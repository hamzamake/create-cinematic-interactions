package dev.createcinematic;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.content.redstone.deskBell.DeskBellBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Hand-worked Create blocks (hand crank, valve handles, analog lever, desk bell, rotation speed controller) have no menu,
 * but the host only keeps a scene alive while a menu of the station's class is open. This slot-less menu is that anchor.
 * Every action is a vanilla menu-button click, which vanilla only delivers after {@link #stillValid} (reach, same block);
 * the server then runs what Create's own use code or value panel would.
 */
public final class HandMenu extends AbstractContainerMenu {
    /** Crank and valve: turn; lever: one notch up or down. */
    public static final int FORWARD = 0, BACKWARD = 1;
    /** Desk bell. */
    public static final int RING = 2;
    /** {@link #valueButton}: valve angle, speed controller RPM, lever signal. Far above the action buttons, so negatives fit. */
    private static final int VALUE = 1 << 20;
    /** Create's held right-click repeats every 4 ticks; one crank turn (and its hunger) per 3 ticks absorbs packet jitter. */
    private static final int MIN_TICKS = 3;

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CreateCinematic.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<HandMenu>> TYPE = MENUS.register("hand",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new HandMenu(id, inventory, buf.readBlockPos())));

    public final BlockPos pos;
    /** The block it opened on: a recoloured valve or a replaced block ends the scene. */
    private final Block block;
    private long lastTurn = -MIN_TICKS;
    private int lastButton = -1;

    private HandMenu(int id, Inventory inventory, BlockPos pos) {
        super(TYPE.get(), id);
        this.pos = pos;
        this.block = inventory.player.level().getBlockState(pos).getBlock();
    }

    /** The button for setting a value (negative = Create's other direction). */
    public static int valueButton(int value) {
        return VALUE + value;
    }

    /** Null when the block entity at pos is not the one {@code kind} works. */
    @Nullable
    static MenuProvider provider(Stations.Kind kind, Level level, BlockPos pos) {
        Class<? extends BlockEntity> type = switch (kind) {
            case HAND_CRANK -> HandCrankBlockEntity.class;
            case VALVE_HANDLE -> ValveHandleBlockEntity.class;
            case ANALOG_LEVER -> AnalogLeverBlockEntity.class;
            case DESK_BELL -> DeskBellBlockEntity.class;
            case SPEED_CONTROLLER -> SpeedControllerBlockEntity.class;
            default -> null;
        };
        if (type == null || !type.isInstance(level.getBlockEntity(pos))) return null;
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return level.getBlockState(pos).getBlock().getName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                // spectators never reach Create's use code; deployers keep Create's own use (NativeUse)
                return player.isSpectator() || player instanceof FakePlayer ? null : new HandMenu(id, inventory, pos);
            }

            @Override
            public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buf) {
                buf.writeBlockPos(pos);
            }
        };
    }

    /** What Create's plain right-click on this block does, as a button; -1 when it has nothing to do (speed controller). */
    public int plainClick(Level level) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DeskBellBlockEntity) return RING;
        return be instanceof HandCrankBlockEntity || be instanceof AnalogLeverBlockEntity ? FORWARD : -1;
    }

    /**
     * The client half of a button, as Create's own use code runs it on the client: the crank's local turn (its cranking
     * sound) and the bell's ding (the server plays it to everyone but the ringer). Call it next to sending the button.
     */
    public void echo(Level level, Player player, int button) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HandCrankBlockEntity crank && !(crank instanceof ValveHandleBlockEntity) && button <= BACKWARD) {
            crank.turn(button == BACKWARD);
        } else if (be instanceof DeskBellBlockEntity && button == RING) {
            ring(level, player);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        Level level = player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (button >= VALUE / 2) {
            setValue(player, be, button - VALUE);
            return false;
        }
        boolean back = button == BACKWARD;
        switch (be) {
            // ValveHandleBlock#onBlockActivated; before the crank case, as a valve is also a crank
            case ValveHandleBlockEntity valve when button <= BACKWARD -> {
                if (player.mayBuild()) valve.activate(back);
            }
            case HandCrankBlockEntity crank when button <= BACKWARD -> crank(player, crank, button);
            case AnalogLeverBlockEntity lever when button <= BACKWARD -> {
                lever.changeState(back);
                leverClick(level, lever);
            }
            case DeskBellBlockEntity bell when button == RING -> ring(level, player);
            case null, default -> {}
        }
        return false; // nothing in the menu changed
    }

    /** HandCrankBlock#useItemOn, with the main-hand item standing in for the item it was used with. */
    private void crank(Player player, HandCrankBlockEntity crank, int button) {
        long now = player.level().getGameTime();
        if (button == lastButton && now - lastTurn < MIN_TICKS) return;
        if (!(crank.getBlockState().getBlock() instanceof HandCrankBlock block)) return;
        lastTurn = now;
        lastButton = button;
        crank.turn(button == BACKWARD);
        if (!AllItems.EXTENDO_GRIP.isIn(player.getMainHandItem()))
            player.causeFoodExhaustion(block.getRotationSpeed() * AllConfigs.server().kinetics.crankHungerMultiplier.getF());
        if (player.getFoodData().getFoodLevel() == 0) AllAdvancements.HAND_CRANK.awardTo(player);
    }

    /** Create's value panel (ValueSettingsPacket), or a straight run of lever notches to {@code value}. */
    private static void setValue(Player player, @Nullable BlockEntity be, int value) {
        switch (be) {
            case ValveHandleBlockEntity valve when valve.showValue() -> panel(player, valve.angleInput, value); // not mid-turn, as Create's panel
            case SpeedControllerBlockEntity controller -> panel(player, controller.targetSpeed, value);
            case AnalogLeverBlockEntity lever when value >= 0 && value <= 15 && value != lever.getState() -> {
                while (lever.getState() != value) lever.changeState(lever.getState() > value);
                leverClick(player.level(), lever);
            }
            case null, default -> {}
        }
    }

    /** ValueSettingsPacket: no adventure players, and Create's panels never set 0 (row 0 is the negative direction). */
    private static void panel(Player player, ScrollValueBehaviour behaviour, int value) {
        if (value == 0 || !player.mayBuild()) return;
        behaviour.setValueSettings(player, new ValueSettings(value < 0 ? 0 : 1, Math.abs(value)), false);
    }

    /** AnalogLeverBlock#useWithoutItem's click, pitched by the new signal. */
    private static void leverClick(Level level, AnalogLeverBlockEntity lever) {
        float pitch = .25f + ((lever.getState() + 5) / 15f) * .5f;
        level.playSound(null, lever.getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.2F, pitch);
    }

    /** DeskBellBlock#useWithoutItem itself, on whichever side this runs. */
    private void ring(Level level, Player player) {
        BlockState state = level.getBlockState(pos);
        Direction face = state.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING).orElse(Direction.UP);
        state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), pos), player, block);
    }
}

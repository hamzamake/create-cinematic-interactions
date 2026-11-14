package dev.createcinematic;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.glue.SuperGlueItem;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlock;
import com.simibubi.create.content.logistics.funnel.FunnelItem;
import com.simibubi.create.content.redstone.displayLink.ClickToLinkBlockItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Right-clicks that belong to Create itself instead of starting a scene. The host asks {@link Stations#sides} from
 * its own RightClickBlock handler (normal priority); while such a click is being dispatched that answers "no sides",
 * so the host leaves the click alone. Decided from the clicker, the held item and the block state only, so the client
 * and the server always agree.
 */
final class NativeUse {
    private NativeUse() {}

    private static final ThreadLocal<Boolean> NATIVE = ThreadLocal.withInitial(() -> false);

    static boolean suppressed() {
        return NATIVE.get();
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayerInteractEvent.RightClickBlock.class,
                event -> NATIVE.set(shouldBeNative(event)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayerInteractEvent.RightClickBlock.class,
                event -> NATIVE.set(false));
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, NativeUse::sneakWindow);
    }

    private static boolean shouldBeNative(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return false;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        Stations.Entry entry = Stations.forState(state);
        if (entry == null) return false;
        // deployers and other fake players: Create's own handling (the host would query their missing network channel)
        if (event.getEntity() instanceof FakePlayer) return true;
        // hand blocks: sneak + use is Create's classic click (crank and valve backwards, lever down, bell); any wrench rotates or picks up
        if (entry.kind().hand()) {
            ItemStack stack = event.getItemStack();
            return event.getEntity().isShiftKeyDown() || stack.is(Tags.Items.TOOLS_WRENCH) || AllItems.WRENCH.isIn(stack)
                    || isNative(stack, state, entry.kind());
        }
        return isNative(event.getItemStack(), state, entry.kind());
    }

    private static boolean isNative(ItemStack stack, BlockState state, Stations.Kind kind) {
        if (stack.isEmpty()) return false;
        // other mods' wrenches on IWrenchable blocks (WrenchEventHandler); Create's own wrench opens the menu like an empty hand
        if (!AllItems.WRENCH.isIn(stack) && stack.is(Tags.Items.TOOLS_WRENCH) && state.getBlock() instanceof IWrenchable) return true;
        // PackagePortBlockEntity#use copies the address onto a held clipboard
        if (kind == Stations.Kind.PACKAGE_PORT && AllBlocks.CLIPBOARD.isIn(stack)) return true;
        // items Create always places instead of using the block (their own handlers set useBlock to FALSE)
        if (stack.getItem() instanceof FunnelItem || stack.getItem() instanceof SuperGlueItem
                || stack.getItem() instanceof ClickToLinkBlockItem) return true;
        // ValveHandleBlock#clicked: a dye of another colour recolours the valve
        if (state.getBlock() instanceof ValveHandleBlock valve) {
            if (AllBlocks.CLIPBOARD.isIn(stack)) return true; // ClipboardValueSettingsHandler copies or pastes its angle
            DyeColor colour = DyeColor.getColor(stack);
            return colour != null && colour != valve.color;
        }
        // SpeedControllerBlock#useItemOn: its placement helper puts a large cogwheel on top; any other block is placed as usual
        if (kind == Stations.Kind.SPEED_CONTROLLER && stack.getItem() instanceof BlockItem) return true;
        // FrogportBlockEntity#use: goggles go on the frog
        if (AllItems.GOGGLES.isIn(stack) && AllBlocks.PACKAGE_FROGPORT.has(state)) return true;
        // ToolboxBlock#useItemOn: a dye of another colour recolours the box
        if (state.getBlock() instanceof ToolboxBlock toolbox) {
            DyeColor colour = DyeColor.getColor(stack);
            return colour != null && colour != toolbox.getColor();
        }
        return false;
    }

    /**
     * Sneak + use with an empty hand opens Create's window, the way to the settings a scene can't edit (address, amounts).
     * Package ports, the requester and the toolbox ignore crouching players themselves, so without this the window is out of reach.
     */
    private static void sneakWindow(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getUseBlock() == TriState.FALSE // a protection mod denied it
                || !player.isSecondaryUseActive() || player.isSpectator()
                || player instanceof FakePlayer || !player.getMainHandItem().isEmpty()) return;
        Stations.Entry entry = Stations.forState(event.getLevel().getBlockState(event.getPos()));
        // one path for every station; the schematic blocks would open on sneak anyway. Hand blocks have no window (see above).
        if (entry == null || entry.kind().hand()) return;
        if (player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = CreateMenuProvider.of(entry, event.getLevel(), event.getPos());
            if (provider != null) serverPlayer.openMenu(provider);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}

package dev.createcinematic;

import com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.table.SchematicTableBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Opens a Create menu through {@code player.openMenu(provider)}: the block entity makes the menu, and {@link #write}
 * (sent by {@code ServerPlayerMixin} through {@code NetworkHooks.openScreen}) appends the extra data that Create's own open call writes (its client menus read it).
 * Create's server-side checks before opening are repeated in {@link #createMenu}.
 */
public final class CreateMenuProvider implements MenuProvider, MenuData {
    private final Stations.Kind kind;
    private final SmartBlockEntity be;
    private final MenuProvider menus;

    private CreateMenuProvider(Stations.Kind kind, SmartBlockEntity be, MenuProvider menus) {
        this.kind = kind;
        this.be = be;
        this.menus = menus;
    }

    /** Null when the block entity at pos is not the one this kind's menu belongs to. */
    @Nullable
    public static MenuProvider of(Stations.Entry entry, Level level, BlockPos pos) {
        if (entry.kind().hand()) return HandMenu.provider(entry.kind(), level, pos); // Create has no menu for it
        Class<? extends SmartBlockEntity> type = switch (entry.kind()) {
            case TOOLBOX -> ToolboxBlockEntity.class;
            case SCHEMATICANNON -> SchematicannonBlockEntity.class;
            case SCHEMATIC_TABLE -> SchematicTableBlockEntity.class;
            case PACKAGE_PORT -> PackagePortBlockEntity.class;
            case REDSTONE_REQUESTER -> RedstoneRequesterBlockEntity.class;
            default -> throw new IllegalStateException("not a menu block: " + entry.kind()); // hand() kinds returned above
        };
        BlockEntity found = level.getBlockEntity(pos);
        return type.isInstance(found) && found instanceof MenuProvider menus
                ? new CreateMenuProvider(entry.kind(), type.cast(found), menus) : null;
    }

    @Override
    public Component getDisplayName() {
        return menus.getDisplayName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        // Create never opens these for a fake player; a spectator never reaches its use code; the block may be gone
        if (player instanceof FakePlayer || player.isSpectator() || be.isRemoved()) return null;
        // RedstoneRequesterBlockEntity#use: a protected logistics network (this tells the player why)
        if (be instanceof RedstoneRequesterBlockEntity requester && !requester.behaviour.mayInteractMessage(player)) return null;
        return menus.createMenu(id, inventory, player);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        switch (kind) {
            case TOOLBOX, SCHEMATICANNON, SCHEMATIC_TABLE -> be.sendToMenu(buf); // NetworkHooks.openScreen(player, be, be::sendToMenu)
            case PACKAGE_PORT, REDSTONE_REQUESTER -> buf.writeBlockPos(be.getBlockPos()); // NetworkHooks.openScreen(player, be, worldPosition)
        }
    }
}

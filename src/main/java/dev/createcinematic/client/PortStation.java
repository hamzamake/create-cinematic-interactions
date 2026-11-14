package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Frogport and postboxes: the 18 package slots as an upright 2 x 9 panel just in front of the block's face (in front of
 * the frog model, so nothing hides it). The address is shown but only edited in Create's own window.
 */
final class PortStation extends CreateStation<PackagePortMenu> {
    private static final double STEP = 0.11, SIZE = 0.047;

    PortStation(BlockPos pos, Direction side, boolean frogport) {
        // an upright panel just in front of the block's face: in front of the frog model too, so nothing hides it
        super(pos, side, PackagePortMenu.class, frogport ? "frogport" : "postbox", 0, 0, 0.56, 0.85, 0.3);
    }

    @Override
    protected List<Spot> layout(PackagePortMenu menu) {
        List<Spot> spots = new ArrayList<>(19);
        for (int i = 0; i < 18; i++) { // PackagePortMenu#addSlots: row * 9 + col
            spots.add(slotSpot(i, (i % 9 - 4) * STEP, 0.13 - i / 9 * STEP, SIZE, null));
        }
        spots.add(buttonSpot("address", 0, -0.13, 0.3, 0.035));
        return spots;
    }

    private Component address() {
        PackagePortBlockEntity be = blockEntity(PackagePortBlockEntity.class);
        return be == null || be.addressFilter == null || be.addressFilter.isBlank() ? tr("port.no_address") : Component.literal(be.addressFilter);
    }

    @Override
    protected List<Component> notes(PackagePortMenu menu, Spot s) {
        return List.of(tr("port.address_note").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected List<Component> buttonTooltip(PackagePortMenu menu, String button) {
        return List.of(tr("port.address", address()), tr("port.address_note").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void drawExtras(PackagePortMenu menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
        for (Spot s : spots) {
            if (s.button() != null) {
                plate(ps, buffers, cam, s, address(), 0xFF3A4A66, true, grow);
            }
        }
    }
}

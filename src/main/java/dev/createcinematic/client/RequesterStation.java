package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Redstone Requester: its 9 ghost slots (menu slots 36..44) in a row on the face. A click sets a slot to what is held or
 * clears it (GhostItemMenu#clicked, on the server); the request is saved when the scene ends. Amounts, address and
 * "allow partial" stay in Create's own window, and nothing here sends its configuration packet.
 */
final class RequesterStation extends CreateStation<RedstoneRequesterMenu> {
    RequesterStation(BlockPos pos, Direction side) {
        super(pos, side, RedstoneRequesterMenu.class, "requester", 0, 0, 0.5, 0.8, 0.35);
    }

    @Override
    protected List<Spot> layout(RedstoneRequesterMenu menu) {
        List<Spot> spots = new ArrayList<>(10);
        for (int i = 0; i < 9; i++) {
            spots.add(ghostSpot(36 + i, (i - 4) * 0.108, 0.1, 0.046));
        }
        spots.add(buttonSpot("window", 0, -0.12, 0.36, 0.035));
        return spots;
    }

    @Override
    protected List<Component> buttonTooltip(RedstoneRequesterMenu menu, String button) {
        return List.of(tr("requester.window"), tr("requester.window_note").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void drawExtras(RedstoneRequesterMenu menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
        for (Spot s : spots) {
            if (s.button() != null) {
                plate(ps, buffers, cam, s, tr("requester.window"), 0xFF3A4A66, true, grow);
            }
        }
    }
}

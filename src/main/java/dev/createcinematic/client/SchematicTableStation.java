package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.table.SchematicTableBlockEntity;
import com.simibubi.create.content.schematics.table.SchematicTableMenu;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.Scene;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Schematic Table, from its front, on its flat top (14 pixels up, 12 deep): the picker strip and refresh at the back;
 * blank schematic in (slot 0), the write button with its progress bar, and the written schematic out (slot 1) in front.
 * Choosing and uploading go through Create's own CreateClient.SCHEMATIC_SENDER, as SchematicTableScreen does.
 */
final class SchematicTableStation extends CreateStation<SchematicTableMenu> {
    private static final double BACK = 0.17, FRONT = -0.15, SLIDE = 0.2, SENT_LATCH = 1.5;

    private double sentAt = -10;
    private int index;
    private double scrolled;
    private double changedAt = -10;
    private int changedDir;
    private boolean refreshed;
    private double progress;

    SchematicTableStation(BlockPos pos, Direction side) {
        super(pos, side, SchematicTableMenu.class, "schematic_table", 90, 14 / 16.0 - 0.5 + 0.03, 0, 0.76, 0.74);
    }

    @Override
    protected boolean takesHeld() {
        return true;
    }

    @Override
    protected List<Spot> layout(SchematicTableMenu menu) {
        return List.of(
                slotSpot(0, -0.38, FRONT, 0.07, "blank"),
                slotSpot(1, 0.38, FRONT, 0.07, "written"),
                buttonSpot("pick", -0.08, BACK, 0.36, 0.05),
                buttonSpot("refresh", 0.4, BACK, 0.075, 0.05),
                buttonSpot("write", -0.19, FRONT, 0.075, 0.05));
    }

    private static List<Component> schematics() {
        return CreateClient.SCHEMATIC_SENDER.getAvailableSchematics();
    }

    /** For the guards: also true just after Write, since the synced flag lags by a round trip (a second upload then eats the blank). */
    private boolean uploading() {
        return Scene.clock() - sentAt < SENT_LATCH || shownUploading();
    }

    /** For display: what the server says. */
    private boolean shownUploading() {
        SchematicTableBlockEntity be = blockEntity(SchematicTableBlockEntity.class);
        return be != null && be.isUploading;
    }

    /** Why writing can't start, or null when it can: the same conditions as SchematicTableScreen's confirm button. */
    private String blocked(SchematicTableMenu menu) {
        if (uploading()) return "table.uploading";
        if (schematics().isEmpty()) return "table.none";
        if (menu.getSlot(1).hasItem()) return "table.take_first";
        if (!menu.canWrite()) return "table.need_blank";
        return null;
    }

    private void step(int by) {
        int size = schematics().size();
        if (size == 0 || uploading()) {
            return;
        }
        index = Math.floorMod(index + by, size);
        changedAt = Scene.clock();
        changedDir = by;
        sound(SoundEvents.BOOK_PAGE_TURN, 0.5f, 1.2f);
    }

    @Override
    public void scroll(double delta) {
        if (menu() == null) {
            return;
        }
        scrolled += delta; // smooth-scrolling mice send fractions
        while (Math.abs(scrolled) >= 1) {
            int dir = scrolled > 0 ? 1 : -1;
            scrolled -= dir;
            step(-dir);
        }
    }

    @Override
    protected boolean press(SchematicTableMenu menu, String button, int mouseButton) {
        switch (button) {
            case "pick" -> {
                if (schematics().isEmpty() || uploading()) {
                    deny();
                    return false;
                }
                step(mouseButton == 1 ? -1 : 1);
            }
            case "refresh" -> {
                CreateClient.SCHEMATIC_SENDER.refresh();
                index = Math.min(index, Math.max(0, schematics().size() - 1));
                changedAt = Scene.clock();
                changedDir = 0;
                sound(SoundEvents.BOOK_PAGE_TURN, 0.5f, 0.9f);
            }
            default -> {
                if (blocked(menu) != null) {
                    deny();
                    return false;
                }
                progress = 0;
                // the server answers on the open SchematicTableMenu; blocked() made sure of the same things the screen checks
                CreateClient.SCHEMATIC_SENDER.startNewUpload(schematics().get(index).getString());
                sentAt = Scene.clock();
                kick(0.3);
                sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.6f, 1.0f);
            }
        }
        return true;
    }

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        SchematicTableMenu menu = menu();
        if (menu == null) {
            return;
        }
        if (!refreshed) {
            refreshed = true;
            CreateClient.SCHEMATIC_SENDER.refresh(); // as the window does when it opens
        }
        index = Math.min(index, Math.max(0, schematics().size() - 1));
        SchematicTableBlockEntity be = blockEntity(SchematicTableBlockEntity.class);
        double target = be != null && be.isUploading ? be.uploadingProgress : menu.getSlot(1).hasItem() ? 1 : 0;
        progress = Anim.approach(progress, target, 8, Scene.frameDt);
    }

    @Override
    public void onEnd() {
        super.onEnd();
        scrolled = 0;
        progress = 0;
        changedAt = -10;
    }

    /** What the strip reads: the upload under way, the chosen file, or that there is none. */
    private Component shownName() {
        SchematicTableBlockEntity be = blockEntity(SchematicTableBlockEntity.class);
        if (be != null && be.isUploading && be.uploadingSchematic != null) {
            return Component.literal(be.uploadingSchematic);
        }
        List<Component> list = schematics();
        return list.isEmpty() ? tr("table.none") : list.get(Math.min(index, list.size() - 1));
    }

    @Override
    protected List<Component> buttonTooltip(SchematicTableMenu menu, String button) {
        List<Component> list = schematics();
        return switch (button) {
            case "pick" -> list.isEmpty()
                    ? List.of(tr("table.none"), tr("table.none_note").withStyle(ChatFormatting.GRAY))
                    : List.of(shownName(), tr("table.position", Math.min(index, list.size() - 1) + 1, list.size()).withStyle(ChatFormatting.DARK_AQUA),
                            tr("table.pick_note").withStyle(ChatFormatting.GRAY));
            case "refresh" -> List.of(tr("button.refresh"), tr("table.refresh_note").withStyle(ChatFormatting.GRAY));
            default -> {
                String why = blocked(menu);
                yield why == null ? List.of(tr("button.write"), shownName().copy().withStyle(ChatFormatting.GRAY))
                        : List.of(tr("button.write").withStyle(ChatFormatting.DARK_GRAY), tr(why).withStyle(ChatFormatting.RED));
            }
        };
    }

    @Override
    protected void drawExtras(SchematicTableMenu menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
        boolean ready = blocked(menu) == null;
        for (Spot s : spots) {
            if (s.button() == null) {
                continue;
            }
            switch (s.button()) {
                case "pick" -> plate(ps, buffers, cam, s, null, 0xFF2A3344, true, grow);
                case "refresh" -> plate(ps, buffers, cam, s, tr("button.refresh"), 0xFF3A4A66, true, grow);
                default -> plate(ps, buffers, cam, s, tr("button.write"), 0xFF3DBE5A, ready, grow);
            }
        }
        if (grow < 0.9) {
            return;
        }
        // the chosen name slides in from the side it was scrolled from
        double k = Anim.outCubic((Scene.clock() - changedAt) / SLIDE);
        Font font = Minecraft.getInstance().font;
        Component name = Component.literal(font.plainSubstrByWidth(shownName().getString(), 185));
        boolean dim = schematics().isEmpty() && !shownUploading();
        text(ps, buffers, cam, name, at(-0.08 + changedDir * 0.05 * (1 - k), BACK, 0.016 - 0.012 * dip("pick")), 0.0036f,
                fade(dim ? 0xFFD3D3D3 : shownUploading() ? 0xFFCCDDFF : 0xFFFFFFFF, 0.15 + 0.85 * k));
        bar(ps, buffers, cam, 0.09, FRONT, 0.17, 0.02, progress, 0xFF5AB8E8);
    }

    @Override
    public void renderScreen(GuiGraphics gui, Font font, float alpha) {
        SchematicTableMenu menu = menu();
        if (menu == null) {
            return;
        }
        if (shownUploading()) {
            statusLine(gui, font, tr("table.status_uploading", shownName(), (int) (Anim.clamp01(progress) * 100)), alpha);
        } else if (menu.getSlot(1).hasItem()) {
            statusLine(gui, font, tr("table.status_done"), alpha);
        }
    }

    @Override
    public SoundEvent touchSound() {
        return SoundEvents.WOOD_STEP;
    }
}

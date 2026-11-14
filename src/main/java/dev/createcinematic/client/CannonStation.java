package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.cannon.ConfigureSchematicannonPacket;
import com.simibubi.create.content.schematics.cannon.ConfigureSchematicannonPacket.Option;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity.State;
import com.simibubi.create.content.schematics.cannon.SchematicannonMenu;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.Scene;
import java.util.List;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Schematicannon, from any side, laid out like Create's window: gunpowder (4), book in (2) and material list out (3) on
 * top; schematic in (0), Play / Pause / Stop and schematic out (1) below (roles per SchematicannonInventory#isItemValid).
 * The buttons send Create's own ConfigureSchematicannonPacket, as SchematicannonScreen#sendOptionUpdate does.
 */
final class CannonStation extends CreateStation<SchematicannonMenu> {
    private static final double TOP = 0.03, BOTTOM = -0.3;
    private double progress, fuel, printing;

    CannonStation(BlockPos pos, Direction side) {
        // AllShapes.SCHEMATICANNON_SHAPE: the base is 11 pixels tall, so the panel keeps to the lower part of the face
        super(pos, side, SchematicannonMenu.class, "schematicannon", 0, 0, 0.5, 0.8, 0.3);
    }

    @Override
    protected Vec3 lookAt() {
        return at(0, -0.12, 0);
    }

    @Override
    protected boolean takesHeld() {
        return true;
    }

    @Override
    protected List<Spot> layout(SchematicannonMenu menu) {
        return List.of(
                slotSpot(4, -0.38, TOP, 0.07, "gunpowder"),
                slotSpot(2, 0.14, TOP, 0.07, "book"),
                slotSpot(3, 0.38, TOP, 0.07, "list"),
                slotSpot(0, -0.38, BOTTOM, 0.07, "schematic"),
                buttonSpot("play", -0.13, BOTTOM, 0.05, 0.05),
                buttonSpot("pause", 0, BOTTOM, 0.05, 0.05),
                buttonSpot("stop", 0.13, BOTTOM, 0.05, 0.05),
                slotSpot(1, 0.38, BOTTOM, 0.07, "schematic_out"));
    }

    private SchematicannonBlockEntity cannon() {
        return blockEntity(SchematicannonBlockEntity.class);
    }

    /** As SchematicannonScreen#containerTick enables its buttons. */
    private static boolean enabled(State state, String button) {
        return switch (button) {
            case "play" -> state != State.RUNNING;
            case "pause" -> state == State.RUNNING;
            default -> state != State.STOPPED;
        };
    }

    @Override
    protected boolean press(SchematicannonMenu menu, String button, int mouseButton) {
        SchematicannonBlockEntity be = cannon();
        if (be == null || be.state == null || !enabled(be.state, button)) {
            deny();
            return false;
        }
        Option option = switch (button) {
            case "play" -> Option.PLAY;
            case "pause" -> Option.PAUSE;
            default -> Option.STOP;
        };
        CatnipServices.NETWORK.sendToServer(new ConfigureSchematicannonPacket(option, true));
        kick(option == Option.PLAY ? 0.3 : 0.15);
        sound(SoundEvents.STONE_BUTTON_CLICK_ON, 0.5f, option == Option.STOP ? 0.9f : 1.2f);
        return true;
    }

    private static Component status(SchematicannonBlockEntity be) {
        // Create's own status text, by its own key (as SchematicannonScreen draws it)
        return Component.translatable("create.schematicannon.status." + be.statusMsg);
    }

    @Override
    protected List<Component> buttonTooltip(SchematicannonMenu menu, String button) {
        SchematicannonBlockEntity be = cannon();
        if (be == null || be.state == null) {
            return List.of(tr("button." + button));
        }
        return List.of(tr("button." + button).withStyle(enabled(be.state, button) ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY),
                tr("cannon.state", status(be)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        SchematicannonBlockEntity be = cannon();
        double dt = Scene.frameDt;
        progress = Anim.approach(progress, be == null ? 0 : Math.min(be.schematicProgress, 1), 6, dt);
        fuel = Anim.approach(fuel, be == null ? 0 : be.hasCreativeCrate ? 1 : be.remainingFuel / (double) Math.max(1, be.getShotsPerGunpowder()), 6, dt);
        printing = Anim.approach(printing, be == null ? 0 : be.bookPrintingProgress, 6, dt);
    }

    @Override
    public void onEnd() {
        super.onEnd();
        progress = fuel = printing = 0;
    }

    @Override
    protected void drawExtras(SchematicannonMenu menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
        SchematicannonBlockEntity be = cannon();
        State state = be == null || be.state == null ? State.STOPPED : be.state;
        for (Spot s : spots) {
            if (s.button() == null) {
                continue;
            }
            boolean lit = switch (s.button()) {
                case "play" -> state == State.RUNNING;
                case "pause" -> state == State.PAUSED;
                default -> state == State.STOPPED;
            };
            int colour = switch (s.button()) {
                case "play" -> lit ? 0xFF3DBE5A : 0xFF2C5A38;
                case "pause" -> lit ? 0xFFE0B83A : 0xFF6A5A26;
                default -> lit ? 0xFFD04A4A : 0xFF6A2C2C;
            };
            plate(ps, buffers, cam, s, null, colour, true, grow);
            if (grow < 0.9) {
                continue;
            }
            // the glyphs are drawn, not typed: no font has to carry them
            Vec3 c = s.point().add(normal.scale(0.002 - 0.012 * dip(s.button())));
            int ink = enabled(state, s.button()) ? 0xFFFFFFFF : 0xFF8C929C;
            double g = 0.024;
            switch (s.button()) {
                case "play" -> {
                    Vec3 tip = c.add(across.scale(g));
                    quad(ps, buffers, cam, c.add(across.scale(-g * 0.8)).add(up.scale(g)), c.add(across.scale(-g * 0.8)).add(up.scale(-g)), tip, tip, ink);
                }
                case "pause" -> {
                    rect(ps, buffers, cam, c.add(across.scale(-g * 0.5)), g * 0.28, g, ink);
                    rect(ps, buffers, cam, c.add(across.scale(g * 0.5)), g * 0.28, g, ink);
                }
                default -> rect(ps, buffers, cam, c, g * 0.85, g * 0.85, ink);
            }
        }
        if (grow < 0.9) {
            return;
        }
        bar(ps, buffers, cam, -0.16, TOP, 0.09, 0.018, fuel, 0xFFB8B8B8);    // gunpowder left in the barrel
        bar(ps, buffers, cam, 0.26, TOP, 0.035, 0.012, printing, 0xFFE8D8A0); // material list being printed
        bar(ps, buffers, cam, 0, -0.145, 0.24, 0.012, progress, 0xFF5AB8E8);  // the schematic's progress
        if (be != null && be.statusMsg != null) {
            text(ps, buffers, cam, status(be), at(0, -0.085, 0.01), 0.0036f, 0xFFDDEEFF);
        }
    }

    @Override
    public void renderScreen(GuiGraphics gui, Font font, float alpha) {
        SchematicannonBlockEntity be = cannon();
        if (menu() != null && be != null && be.statusMsg != null) {
            statusLine(gui, font, status(be), alpha);
        }
    }
}

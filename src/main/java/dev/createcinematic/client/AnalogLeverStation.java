package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import dev.createcinematic.HandMenu;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Analog lever: hold the handle and drag it along its arc, a notch per signal level. The notch under the mouse goes to
 * the server, which steps Create's lever there; the handle, the lit notches and the number show the synced signal.
 * On a wall the swing is seen end-on (up = 0, down = 15); on a floor or ceiling it crosses the screen.
 */
final class AnalogLeverStation extends HandStation {
    // block/analog_lever/handle.json: a 10 px bar on a pivot 1 px off the mount, held 9 px along
    private static final double LENGTH = 9 / 16.0, MOUNT = 7 / 16.0;
    /** AnalogLeverRenderer: 45 degrees off the mount at signal 0, 6 more per level. */
    private static final double START = 45, NOTCH = 6;
    /** Ticks between two notches sent. */
    private static final int GAP = 3;

    private final boolean wall;
    /** The handle's pivot; the handle points along {@code low} at 0 degrees and along {@code off} (out of the mount) at 90. */
    private final Vec3 pivot, low, off;
    private int want, sent, sinceSend = GAP;
    /** The signal as the handle shows it: Create eases it (its own value is not public), so this eases alike. */
    private double shown = Double.NaN;
    private double glow;

    AnalogLeverStation(BlockPos pos, Direction side, BlockState state) {
        super(pos, side, "analog_lever", 0, 0.1 * y(state), 0, 1.0, y(state) == 0 ? 0.2 : -0.3 * y(state));
        // AnalogLeverRenderer#transform turns the model's north onto the opposite of FACING, then tips it onto the wall or ceiling
        Vec3 facing = Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getNormal());
        Vec3 top = new Vec3(0, 1, 0);
        wall = y(state) == 0;
        off = wall ? facing : top.scale(-y(state));
        low = wall ? top : facing.scale(y(state));
        pivot = Vec3.atCenterOf(pos).subtract(off.scale(MOUNT));
    }

    /** -1 on a floor, 0 on a wall, 1 on a ceiling. */
    private static int y(BlockState state) {
        AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
        return face == AttachFace.FLOOR ? -1 : face == AttachFace.WALL ? 0 : 1;
    }

    private Vec3 along(double signal, double length) {
        double a = Math.toRadians(START + NOTCH * signal);
        return pivot.add(low.scale(length * Math.cos(a))).add(off.scale(length * Math.sin(a)));
    }

    private Vec3 grip() {
        return along(shown, LENGTH);
    }

    private int notch(Vec3 p) {
        Vec3 d = p.subtract(pivot);
        // end-on, only the height tells; side-on, the direction from the pivot (never below the mount)
        double a = wall ? Math.acos(Mth.clamp(d.dot(low) / LENGTH, -1, 1)) : Math.atan2(Math.max(d.dot(off), 0), d.dot(low));
        return Mth.clamp((int) Math.round((Math.toDegrees(a) - START) / NOTCH), 0, 15);
    }

    @Override
    public boolean crouches() {
        return off.y >= 0; // not under a ceiling
    }

    @Override
    public void updateCursor(boolean away) {
        aim = null;
        over = false;
        Vec3 p = menu() == null || Double.isNaN(shown) || away && !grabbed ? null : Picking.cursorOnPlane(origin, normal);
        if (p == null) {
            return;
        }
        Vec3 d = p.subtract(grip());
        over = d.subtract(normal.scale(d.dot(normal))).length() < 0.15;
        if (grabbed) {
            want = notch(p);
        } else if (over) {
            aim = grip();
        }
    }

    @Override
    protected void act(int button, boolean shift) {
        AnalogLeverBlockEntity lever = blockEntity(AnalogLeverBlockEntity.class);
        if (button == 0 && over && lever != null) {
            grabbed = true;
            want = sent = lever.getState();
            kick(0.05);
        }
    }

    private void sendNotch() {
        if (want != sent && sendValue(want)) {
            sent = want;
            sinceSend = 0;
        }
    }

    @Override
    public void release(int button) {
        if (button == 0 && grabbed) {
            sendNotch(); // where it was let go is where it stays
        }
        super.release(button);
    }

    @Override
    public void tick() {
        super.tick();
        if (sinceSend < GAP) {
            sinceSend++;
        } else if (grabbed) {
            sendNotch();
        }
    }

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        AnalogLeverBlockEntity lever = blockEntity(AnalogLeverBlockEntity.class);
        if (lever == null) {
            shown = Double.NaN;
            grabbed = false;
            return;
        }
        // AnalogLeverBlockEntity chases the state by a fifth of the gap every tick: -ln(0.8) * 20 a second
        shown = Double.isNaN(shown) ? lever.getState() : Anim.approach(shown, lever.getState(), 4.46, Scene.frameDt);
        glow = Anim.approach(glow, grabbed ? 1 : over ? 0.6 : 0, 10, Scene.frameDt);
    }

    @Override
    public Vec3[] handsOverride() {
        return grabbed && !Double.isNaN(shown) ? new Vec3[] {grip().add(normal.scale(0.08)), rest(false)} : null;
    }

    @Override
    public double handsStiffness() {
        return 40;
    }

    @Override
    public void onEnd() {
        super.onEnd();
        shown = Double.NaN;
        glow = 0;
        sinceSend = GAP;
    }

    /** A dot per signal level along the handle's travel (beside it on a wall), lit up to the signal, and the number. */
    @Override
    protected void drawExtras(HandMenu menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
        AnalogLeverBlockEntity lever = blockEntity(AnalogLeverBlockEntity.class);
        if (lever == null) {
            return;
        }
        double show = grow * (0.5 + 0.5 * glow);
        Vec3 shift = wall ? across.scale(0.2) : Vec3.ZERO;
        for (int n = 0; n <= 15; n++) {
            Vec3 at = along(n, LENGTH + (wall ? 0 : 0.07)).add(shift);
            if (wall) {
                at = at.subtract(normal.scale(at.subtract(origin).dot(normal))); // flat on the plane, as a scale
            }
            boolean aimed = grabbed && n == want;
            double size = aimed ? 0.013 : n <= lever.getState() ? 0.009 : 0.005;
            rect(ps, buffers, cam, at, size, size, fade(aimed ? 0xFFFFFFFF : n <= lever.getState() && n > 0 ? 0xFFFF5A4D : 0xFF8CF2FF, show));
        }
        if (grow > 0.9) {
            Vec3 at = wall ? origin.add(across.scale(0.32)) : pivot.add(off.scale(LENGTH + 0.2));
            text(ps, buffers, cam, Component.literal(String.valueOf(lever.getState())), at, 0.008f, fade(0xFFFFFFFF, show));
        }
    }
}

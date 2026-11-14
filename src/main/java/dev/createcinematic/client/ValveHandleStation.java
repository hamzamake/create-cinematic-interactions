package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import dev.createcinematic.HandMenu;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Valve handle: hold the rim and twist the mouse round the hub. Every {@link #STEP} degrees of twist is one of Create's
 * turns, the way the mouse went; the wheel then turns by the valve's own angle setting. That setting is the mouse wheel's
 * here (this scene takes the click that opens Create's value panel): the lit arc and the label show it.
 */
final class ValveHandleStation extends HandStation {
    // block/valve_handle/valve_handle.obj: the rim is about 5.5 px off the hub and 2 px behind the block centre
    private static final double RADIUS = 5.5 / 16, DEPTH = -2 / 16.0;
    private static final double STEP = 30;
    /** Ticks after the last scroll notch before the angle is sent, and how long it then outranks the synced one. */
    private static final int SETTLE = 8, TRUST = 20;
    /** Ticks the wheel must have stood still: ValveHandleBlockEntity's cooldown (4) is not synced. */
    private static final int FREE = 5;
    private static final int DOTS = 36;

    /** Create's angle is about the positive axis; times this it is anticlockwise as the camera (on the FACING side) sees it. */
    private final int sense;
    /** Where the mouse was round the hub (degrees, NaN = unknown), and how far it has twisted since the last turn. */
    private double mouseAngle = Double.NaN, travel;
    /** Where on the rim the hand took hold, relative to the wheel. */
    private double hold;
    /** The wheel angle last frame, and whether it was turning: to carry {@link #hold} across Create's restart at 0. */
    private double lastWheel;
    private boolean turning;
    /** The angle scrolled to (0 = none: the synced one counts), and ticks since. */
    private int set, setAge;
    private int idle = FREE;
    private double scrolled, glow;

    ValveHandleStation(BlockPos pos, Direction side, BlockState state) {
        super(pos, side, state, "valve_handle", DEPTH);
        sense = state.getValue(BlockStateProperties.FACING).getAxisDirection().getStep();
    }

    private double wheel(ValveHandleBlockEntity valve) {
        return sense * valve.getIndependentAngle(partialTick());
    }

    private int value(ValveHandleBlockEntity valve) {
        return set != 0 ? set : valve.angleInput.getValue();
    }

    private Vec3 rim(double degrees, double out) {
        double a = Math.toRadians(degrees);
        return at(RADIUS * Math.cos(a), RADIUS * Math.sin(a), out);
    }

    @Override
    public void updateCursor(boolean off) {
        aim = null;
        Vec3 p = menu() == null || blockEntity(ValveHandleBlockEntity.class) == null || off && !grabbed ? null
                : Picking.cursorOnPlane(origin, normal);
        Vec3 d = p == null ? Vec3.ZERO : p.subtract(origin);
        // right on the hub the direction means nothing
        double now = d.length() > 0.05 ? Math.toDegrees(Math.atan2(d.dot(up), d.dot(across))) : Double.NaN;
        if (grabbed && !Double.isNaN(now) && !Double.isNaN(mouseAngle)) {
            travel = Mth.clamp(travel + Mth.wrapDegrees(now - mouseAngle), -STEP, STEP);
        }
        mouseAngle = now;
        over = p != null && d.length() < RADIUS + 0.15;
        if (over && !grabbed) {
            aim = rim(Double.isNaN(now) ? 90 : now, 0.1);
        }
    }

    @Override
    public void clearCursor() {
        super.clearCursor();
        mouseAngle = Double.NaN;
    }

    @Override
    protected void act(int button, boolean shift) {
        ValveHandleBlockEntity valve = blockEntity(ValveHandleBlockEntity.class);
        if (button == 0 && over && valve != null) {
            grabbed = true;
            travel = 0;
            hold = (Double.isNaN(mouseAngle) ? 90 : mouseAngle) - wheel(valve);
            kick(0.06);
            sound(SoundEvents.COPPER_HIT, 0.5f, 1.3f);
        }
    }

    @Override
    public void scroll(double delta) {
        ValveHandleBlockEntity valve = blockEntity(ValveHandleBlockEntity.class);
        LocalPlayer player = Minecraft.getInstance().player;
        if (menu() == null || valve == null || player == null) {
            return;
        }
        scrolled += delta; // smooth-scrolling mice send fractions
        int by = (int) scrolled * (Screen.hasShiftDown() ? 1 : 15);
        scrolled -= (int) scrolled;
        if (by == 0) {
            return;
        }
        if (!player.mayBuild()) {
            deny(); // as Create's value panel: not in adventure mode
            return;
        }
        int v = value(valve) + by;
        // Create's panel has no 0: the row below 1 degree is 1 degree the other way
        set = Mth.clamp(v == 0 ? by : v, -180, 180);
        setAge = 0;
    }

    private void sendAngle() {
        if (set != 0 && setAge < SETTLE) {
            sendValue(set);
            setAge = SETTLE;
        }
    }

    @Override
    public void tick() {
        super.tick();
        ValveHandleBlockEntity valve = blockEntity(ValveHandleBlockEntity.class);
        LocalPlayer player = Minecraft.getInstance().player;
        if (valve == null || player == null || menu() == null) {
            return;
        }
        idle = valve.inUse > 0 ? 0 : idle + 1;
        // the server ignores an angle mid-turn (Create's panel is hidden then), so the settle clock waits for the wheel to stop
        if (set != 0 && valve.inUse == 0 && ++setAge == SETTLE) {
            setAge = 0;
            sendAngle();
        } else if (setAge > SETTLE + TRUST) {
            set = 0; // the server's answer has long arrived
        }
        // a twist that carries on while the wheel is busy fires as soon as it is free
        if (!grabbed || idle < FREE || Math.abs(travel) < STEP) {
            return;
        }
        sendAngle(); // the turn must use the angle on show
        // HandCrankBlockEntity#getGeneratedSpeed + convertToDirection: not clockwise() spins anticlockwise seen from the
        // side FACING points to, where the camera is. A valve's clockwise() is (angle < 0) ^ backwards.
        boolean back = travel < 0 ^ value(valve) < 0;
        travel = 0;
        // activate() only answers on the client: false while the wheel is driven by something else
        if (!player.mayBuild() || !valve.activate(back)) {
            deny();
        } else if (send(back ? HandMenu.BACKWARD : HandMenu.FORWARD)) {
            idle = 0;
            kick(0.1);
            sound(SoundEvents.COPPER_STEP, 0.6f, 0.8f);
        }
    }

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        glow = Anim.approach(glow, grabbed ? 1 : over ? 0.6 : 0, 10, Scene.frameDt);
        ValveHandleBlockEntity valve = blockEntity(ValveHandleBlockEntity.class);
        if (valve != null) {
            double w = wheel(valve);
            if (valve.inUse > 0 && !turning) {
                hold += lastWheel - w; // ValveHandleBlockEntity#activate starts every turn from 0 on the server
            }
            turning = valve.inUse > 0;
            lastWheel = w;
        }
    }

    @Override
    public Vec3[] handsOverride() {
        ValveHandleBlockEntity valve = grabbed ? blockEntity(ValveHandleBlockEntity.class) : null;
        // the rim is round, so the hand spring trailing the wheel a little still leaves the fist on it
        return valve == null ? null
                : new Vec3[] {rim(hold + wheel(valve), 0.1).add(across.scale(4 * denyShake())), rest(false)};
    }

    @Override
    public double handsStiffness() {
        return 40;
    }

    @Override
    public void onEnd() {
        sendAngle(); // an angle scrolled to just before leaving still counts
        super.onEnd();
        mouseAngle = Double.NaN;
        travel = scrolled = glow = 0;
        set = setAge = 0;
        idle = FREE;
    }

    /** A ring of dots round the wheel: the lit arc, from the top, is how far and which way one turn of Create's goes. */
    @Override
    protected void drawExtras(HandMenu menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
        ValveHandleBlockEntity valve = blockEntity(ValveHandleBlockEntity.class);
        if (valve == null) {
            return;
        }
        int v = value(valve);
        double show = grow * (0.45 + 0.55 * glow), r = RADIUS + 0.09;
        for (int i = 0; i < DOTS; i++) {
            double a = Math.toRadians(i * 360.0 / DOTS);
            boolean lit = Mth.positiveModulo((i * 360.0 / DOTS - 90) * Mth.sign(v), 360) <= Math.abs(v);
            double size = lit ? 0.01 : 0.005;
            rect(ps, buffers, cam, at(r * Math.cos(a), r * Math.sin(a), 0.1), size, size, fade(0xFF8CF2FF, show * (lit ? 1 : 0.4)));
        }
        if (grow > 0.9) {
            text(ps, buffers, cam, tr("valve.angle", Math.abs(v), v < 0 ? "⟳" : "⟲"), at(0, -r - 0.07, 0.1), 0.005f,
                    fade(0xFFE8F6FF, show));
        }
    }
}

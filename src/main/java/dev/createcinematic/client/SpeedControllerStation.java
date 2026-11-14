package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.createcinematic.HandMenu;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Rotation speed controller as a cutaway, modelled in 3D: an open brass gearbox stands on the near face (its back plate
 * covers the wall and the value panel), and inside it is a belt CVT. The lower pulley sits on the shaft line, a belt runs
 * up to the upper pulley, and a reversing box on that shaft turns the drive up to the large cogwheel. Each pulley is two
 * cones facing each other: slid together the belt rides high, slid apart it sinks, and the two always move opposite ways,
 * so the ratio cogwheel / shaft slides without steps. Power goes either way (see {@link #cogDriven}): the pulleys stay
 * where they are and the IN and OUT tags change sides. The dial floating beside the box is dragged (or the wheel scrolled)
 * to set the RPM.
 * <p>
 * Everything turns about its real axis in the world, right-handed about the positive axis for a positive Create speed, so
 * both sides of the block show the same motion. Solids go through an entity render type, so they sort by depth and take
 * the world's light.
 * <p>
 * Create's controller holds its target whatever the input, so the pulleys re-shape by themselves when the input changes.
 * The client only picks the RPM to ask for ({@link HandMenu#sendValue}); what the block entity has synced is what is shown.
 */
final class SpeedControllerStation extends CreateStation<HandMenu> {
    /** Belt radius at 1:1. It rides between half and one and a half times this, and the two radii add up to twice it. */
    private static final double MID = 0.074, CONE_R = 0.12, HUB = 0.024;
    /** Belt width and thickness, and how far a cone face leans out per unit of radius: the belt sits where the groove is as wide as it is. */
    private static final double BELT_W = 0.08, BELT_T = 0.018, LEAN = 0.42, CONE_T = (CONE_R - HUB) * LEAN, PLATE = 0.012;
    /** Shaft heights on the plane (v = 0 is the block's own shaft), and the window in the frame. */
    private static final double V_IN = 0, V_OUT = 0.29, WIN = 0.44;
    /**
     * Depths off the block's face. The back plate sits clear of Create's value box, which stands a little proud of the
     * wall; the gearbox reaches DEPTH, and both axles lie at AXLE.
     */
    private static final double BACK = 0.03, DEPTH = 0.3, AXLE = 0.165;
    private static final double BOX_U = 0.205, BOX = 0.05, KNOB_U = 0.285, KNOB_W = AXLE + 0.06;
    /** The dial hangs level with the front of the box: further back the box's corner would hide it. */
    private static final double DIAL_V = 0.16, DIAL_R = 0.12, DIAL_W = DEPTH + 0.01, PER_RPM = 0.004, RIDGE = 0.05;
    /** The server rebuilds the kinetic network on every set. */
    private static final int SEND_EVERY = 5, GIVE_UP = 60;

    private static final int STEEL = 0xFF9A9FA8, DARK_STEEL = 0xFF6A6F79, BRASS = 0xFFC9A24B, RUBBER = 0xFF2B2622, RIB = 0xFF5A5046;
    private static final int PLATE_BACK = 0xFF231D15, MARK = 0xFF2E3138;

    private static final ResourceLocation WHITE = new ResourceLocation("textures/misc/white.png");
    /** Round things have this many sides (or a half, or a third of it), and each pulley's share of the belt this many bends. */
    private static final int SEG = 24, ARC = 12;
    private static final double[] COS = new double[SEG], SIN = new double[SEG];
    /** Directions on the plane as (across, up, out): lathe axes, and where the points at angle 0 and 90 about them lie. */
    private static final double[] ALONG = {1, 0, 0}, UPRIGHT = {0, 1, 0}, OUTWARD = {0, 0, 1}, INWARD = {0, 0, -1};
    /** Half a pulley from the groove outwards, as (distance along the axle, radius): cone face, rim, back, boss. And its mirror image. */
    private static final double[] CONE_A = {0, CONE_T, CONE_T + PLATE, CONE_T + PLATE, CONE_T + PLATE + 0.018, CONE_T + PLATE + 0.018};
    private static final double[] CONE_RAD = {HUB, CONE_R, CONE_R, HUB + 0.014, HUB + 0.014, HUB};
    private static final double[] CONE_A_MIRROR = new double[CONE_A.length], CONE_RAD_MIRROR = new double[CONE_A.length];
    /** The handle's ball. */
    private static final double[] BALL_A = new double[7], BALL_RAD = new double[7];

    static {
        for (int i = 0; i < SEG; i++) {
            COS[i] = Math.cos(i * 2 * Math.PI / SEG);
            SIN[i] = Math.sin(i * 2 * Math.PI / SEG);
        }
        for (int i = 0; i < CONE_A.length; i++) {
            CONE_A_MIRROR[i] = -CONE_A[CONE_A.length - 1 - i];
            CONE_RAD_MIRROR[i] = CONE_RAD[CONE_A.length - 1 - i];
        }
        for (int i = 0; i < BALL_A.length; i++) {
            BALL_A[i] = -0.024 * Math.cos(i * Math.PI / (BALL_A.length - 1));
            BALL_RAD[i] = 0.024 * Math.sin(i * Math.PI / (BALL_A.length - 1));
        }
    }

    private final Direction.Axis shaft;
    /** The dial's place across the face: right of the box unless a block is there. */
    private final double dialU;
    /** +1 when the player's right is the shaft's positive direction, else -1: turns a spin in the world into one on the plane. */
    private final double hand;
    /** Where angle 90 about the shaft lies, for a turn that is right-handed about the shaft's positive direction. */
    private final double[] spun;
    private boolean overDial, overKnob, grabbed;
    private double mouseU, grabU, scrolled;
    private int grabRpm;
    /** RPM asked for and not yet synced back (0 = none), the last one sent, and ticks since. */
    private int want, sent, sinceSend = SEND_EVERY;

    /** log(belt radius on the shaft's pulley / on the cogwheel's) as drawn, NaN = not yet known. */
    private double ratio = Double.NaN;
    /** Turn of the shaft's side, of the cogwheel's pulley and of the drive up to the cogwheel; the belt's travel within one rib. */
    private double angleLow, angleUp, angleDrive, belt, needle = Double.NaN, knob = -1;
    /** 0 = the shaft drives, 1 = the cogwheel does (eased, -1 = not yet known); and 0..1 as the box stands out of the wall. */
    private double flow = -1, open;
    private double glowDial, glowKnob, flash, lastClick = -10;
    private float lastIn = Float.NaN;
    /** The belt's middle line round the two pulleys (straight runs join the ends): height, depth and the outward direction. */
    private final double[] beltV = new double[2 * ARC + 2], beltW = new double[2 * ARC + 2], beltNv = new double[2 * ARC + 2],
            beltNw = new double[2 * ARC + 2];
    private final double[] ribA = new double[4], ribB = new double[4];

    SpeedControllerStation(BlockPos pos, Direction side, BlockState state) {
        super(pos, side, HandMenu.class, "speed_controller", 0, 0, 0.5, 0.8, 0.05); // eye 1.45 out: a wall 2 out still allows the scene
        shaft = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        hand = across.x + across.z > 0 ? 1 : -1; // across lies along the shaft: the scene opens on the two sides off its axis
        spun = new double[] {0, 0, hand};
        var level = Minecraft.getInstance().level;
        BlockPos right = pos.relative(side).offset(BlockPos.containing(across.x, 0, across.z));
        BlockPos left = pos.relative(side).offset(BlockPos.containing(-across.x, 0, -across.z));
        boolean rightBlocked = level != null && !level.getBlockState(right).getCollisionShape(level, right).isEmpty();
        boolean leftBlocked = level != null && !level.getBlockState(left).getCollisionShape(level, left).isEmpty();
        dialU = rightBlocked && !leftBlocked ? -0.645 : 0.645; // a wall beside the stand point would bury the dial
    }

    @Override
    protected List<Spot> layout(HandMenu menu) {
        return List.of();
    }

    /** The camera sits closer than the box's depth would like (see the constructor), so it sees wider. */
    @Override
    public double leanFov() {
        return 78;
    }

    /** The camera looks into the middle of the box, not at the wall behind it. */
    @Override
    protected Vec3 lookAt() {
        return at(0, 0.03, DEPTH / 2);
    }

    /** A step further back than at other blocks: the box stands DEPTH out of the face. */
    @Override
    public List<Vec3> approach(LocalPlayer player) {
        Vec3 stand = new Vec3(pos.getX() + 0.5 + side.getStepX() * 1.2, player.getY(), pos.getZ() + 0.5 + side.getStepZ() * 1.2);
        return Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06 ? List.of(stand) : List.of();
    }

    // ---- what the block says ---------------------------------------------------------------------------------------

    private static int max() {
        return Mth.clamp(AllConfigs.server().kinetics.maxRotationSpeed.get(), 1, 1 << 18); // far past any real config; keeps the RPM sane
    }

    private int synced() {
        SpeedControllerBlockEntity be = blockEntity(SpeedControllerBlockEntity.class);
        return be == null ? 0 : be.targetSpeed.getValue();
    }

    /** The target to show: the one asked for until the server answers, then the synced one. */
    private int shown() {
        return want != 0 ? want : synced();
    }

    /**
     * Which way power flows (SpeedControllerBlockEntity#getDesiredOutputSpeed): when the large cog on top is the controller's
     * source, the cog is the input and the shaft line comes out at the target; otherwise the shaft is the input and the cog
     * comes out at the target.
     */
    private boolean cogDriven() {
        SpeedControllerBlockEntity be = blockEntity(SpeedControllerBlockEntity.class);
        return be != null && be.hasSource() && pos.above().equals(be.source);
    }

    /** The large cog on top, if it is a kinetic block entity. */
    @Nullable
    private KineticBlockEntity cog() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos.above()) instanceof KineticBlockEntity cog ? cog : null;
    }

    /** The input side's real speed: the cog's when it drives, else the controller's (its shaft's). */
    private float input() {
        SpeedControllerBlockEntity be = blockEntity(SpeedControllerBlockEntity.class);
        if (be == null) {
            return 0;
        }
        KineticBlockEntity cog = cog();
        return cogDriven() ? (cog == null ? 0 : cog.getSpeed()) : be.getSpeed();
    }

    /** The output side's real speed: the shaft's when the cog drives, else the cog's (0 when there is none). */
    private float output() {
        SpeedControllerBlockEntity be = blockEntity(SpeedControllerBlockEntity.class);
        if (be == null) {
            return 0;
        }
        KineticBlockEntity cog = cog();
        return cogDriven() ? be.getSpeed() : cog == null || !cogOnTop() ? 0 : cog.getSpeed();
    }

    /** RotationPropagator's rule: a large cogwheel on top, its axle level and across the controller's shaft. */
    private boolean cogOnTop() {
        var level = Minecraft.getInstance().level;
        BlockState above = level == null ? null : level.getBlockState(pos.above());
        return above != null && ICogWheel.isLargeCog(above) && above.hasProperty(CogWheelBlock.AXIS)
                && above.getValue(CogWheelBlock.AXIS).isHorizontal() && above.getValue(CogWheelBlock.AXIS) != shaft;
    }

    private void choose(int rpm) {
        int s = shown();
        want = (s < 0 ? -1 : 1) * Mth.clamp(rpm, 1, max());
    }

    /**
     * The output keeps its sign whatever the input's, so the reversing box is in when the two differ. Whichever drives,
     * the two are the shaft's speed and the cogwheel's, so this is also what the box between them is doing.
     */
    private boolean reversed() {
        return (shown() < 0) != (input() < 0);
    }

    // ---- geometry --------------------------------------------------------------------------------------------------

    /**
     * log of r_shaft / r_cog for the shown target, within the cones' travel. That is cogwheel speed / shaft speed whichever
     * drives: target / input with the shaft driving, input / target with the cogwheel driving.
     */
    private double wantedRatio() {
        double in = Math.abs(input());
        // unpowered there is no ratio yet: shape as if for Create's default input, so the dial still shows
        double k = Math.max(1, Math.abs(shown())) / (in > 0 ? in : SpeedControllerBlockEntity.DEFAULT_SPEED);
        return Math.log(Mth.clamp(cogDriven() ? 1 / k : k, 1 / 3.0, 3));
    }

    /** Belt radius on the shaft's (lower) pulley; the cogwheel's (upper) one has the rest of 2 * MID. */
    private double radiusLow() {
        double k = Math.exp(ratio);
        return 2 * MID * k / (1 + k);
    }

    /** Half the gap between a pulley's two cones with the belt riding at {@code r}. */
    private static double gap(double r) {
        return BELT_W / 2 - (r - HUB) * LEAN;
    }

    /** Needle angle: a doubling of the RPM is always the same turn, 1 at lower left round the top to the limit at lower right. */
    private static double dialAngle(int rpm) {
        int max = max();
        double k = max < 2 ? 0 : Math.log(Mth.clamp(Math.abs(rpm), 1, max)) / Math.log(max);
        return Math.toRadians(225 - 270 * k);
    }

    // ---- input -----------------------------------------------------------------------------------------------------

    @Override
    public boolean showsInventory() {
        return false;
    }

    @Override
    public boolean busy() {
        return super.busy() || grabbed;
    }

    @Override
    public void updateCursor(boolean off) {
        aim = null;
        overDial = overKnob = false;
        Vec3 p = menu() == null || blockEntity(SpeedControllerBlockEntity.class) == null || off && !grabbed ? null
                : Picking.cursorOnPlane(at(0, 0, DIAL_W), normal); // the dial's plane
        if (p == null) {
            return;
        }
        double u = mouseU = p.subtract(origin).dot(across), v = p.subtract(origin).dot(up);
        if (grabbed) {
            int rpm = Mth.clamp(grabRpm + (int) Math.round((u - grabU) / PER_RPM), 1, max());
            if (rpm != Math.abs(shown())) {
                choose(rpm);
                if (Scene.clock() - lastClick > 0.05) {
                    lastClick = Scene.clock();
                    sound(SoundEvents.COMPARATOR_CLICK, 0.2f, 1.2f + 0.6f * (float) (Math.log(rpm) / Math.log(Math.max(2, max()))));
                }
            }
            aim = at(dialU + Mth.clamp(u - grabU, -0.12, 0.12), DIAL_V, DIAL_W + 0.03);
            return;
        }
        overDial = Math.hypot(u - dialU, v - DIAL_V) < DIAL_R + 0.02;
        if (overDial) {
            aim = at(dialU, DIAL_V, DIAL_W + 0.03);
            return;
        }
        // the handle sits inside the box, well behind the dial's plane: found on its own
        Vec3 k = Picking.cursorOnPlane(at(0, 0, KNOB_W), normal);
        overKnob = k != null && Math.abs(k.subtract(origin).dot(across) - KNOB_U) < 0.05 && Math.abs(k.subtract(origin).dot(up) - V_OUT) < 0.07;
        if (overKnob) {
            aim = at(KNOB_U, V_OUT, KNOB_W + 0.03);
        }
    }

    @Override
    public void clearCursor() {
        super.clearCursor();
        overDial = overKnob = false;
    }

    @Override
    protected void act(int button, boolean shift) {
        if (button != 0 || menu() == null) {
            return;
        }
        if (overDial) {
            grabbed = true;
            grabU = mouseU;
            grabRpm = Math.max(1, Math.abs(shown()));
            sound(SoundEvents.METAL_HIT, 0.3f, 1.4f);
        } else if (overKnob && shown() != 0) {
            want = -shown();
            sound(SoundEvents.LEVER_CLICK, 0.6f, 0.7f);
            kick(0.06);
        }
    }

    @Override
    public void release(int button) {
        if (button == 0) {
            grabbed = false;
        }
    }

    @Override
    public void scroll(double delta) {
        if (menu() == null) {
            return;
        }
        scrolled += delta; // smooth-scrolling mice send fractions
        while (Math.abs(scrolled) >= 1) {
            int step = scrolled > 0 ? 1 : -1;
            scrolled -= step;
            int before = shown();
            choose(Math.abs(before) + step * (Screen.hasShiftDown() ? 10 : 1));
            if (want != before) {
                sound(SoundEvents.COMPARATOR_CLICK, 0.25f, step > 0 ? 1.6f : 1.3f);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (sinceSend < GIVE_UP) {
            sinceSend++;
        }
        HandMenu menu = menu();
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (menu == null || gameMode == null || blockEntity(SpeedControllerBlockEntity.class) == null) {
            grabbed = false;
            want = sent = 0;
            return;
        }
        if (!leftHeld()) {
            grabbed = false; // the release never arrived (focus lost while holding)
        }
        if (want == 0) {
            return;
        }
        if (want == synced() && (sent == 0 || sent == want)) {
            want = sent = 0; // it arrived (or was scrolled back before anything was sent)
        } else if (want != sent) {
            // not mid-drag: every set rebuilds the kinetic network, so a drag sends once, when the dial is let go
            if (!grabbed && sinceSend >= SEND_EVERY) {
                HandMenu.sendValue(menu, want);
                sent = want;
                sinceSend = 0;
            }
        } else if (!grabbed && sinceSend >= GIVE_UP) {
            want = sent = 0; // refused (adventure mode): back to what the block says
            sound(SoundEvents.LEVER_CLICK, 0.4f, 0.5f);
        }
    }

    @Override
    public List<Component> tooltip(ItemStack carried) {
        if (overDial) {
            return List.of(tr("speed_controller.dial"), tr("speed_controller.trim").withStyle(ChatFormatting.GRAY));
        }
        return overKnob ? List.of(tr("speed_controller.reverse")) : null;
    }

    // ---- motion ----------------------------------------------------------------------------------------------------

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        double dt = Scene.frameDt;
        glowDial = Anim.approach(glowDial, grabbed ? 1 : overDial ? 0.6 : 0, 12, dt);
        glowKnob = Anim.approach(glowKnob, overKnob ? 0.8 : 0, 12, dt);
        flash = Math.max(0, flash - dt / 0.8);
        open = Anim.approach(open, outro() ? 0 : 1, 6, dt);
        // from here on only the block entity is needed, not the menu: the works already run while the camera comes in
        if (blockEntity(SpeedControllerBlockEntity.class) == null) {
            return;
        }
        int shown = shown();
        float in = input();
        if (!Float.isNaN(lastIn) && in != lastIn) {
            flash = 1; // the input moved: IN lights up while the pulleys take it up and OUT stays where it is
        }
        lastIn = in;

        // the cones slide: no steps, no jump when the scene opens, and none when the power turns round
        boolean cogIn = cogDriven();
        flow = flow < 0 ? (cogIn ? 1 : 0) : Anim.approach(flow, cogIn ? 1 : 0, 5, dt);
        ratio = Double.isNaN(ratio) ? wantedRatio() : Anim.approach(ratio, wantedRatio(), 7, dt);
        needle = Double.isNaN(needle) ? dialAngle(shown) : Anim.approach(needle, dialAngle(shown), 18, dt);
        double rev = reversed() ? 1 : 0;
        knob = knob < 0 ? rev : Anim.approach(knob, rev, 14, dt);

        // Life speed, each side at its own real speed, as turns about the world's axes. The cogwheel's pulley turns the
        // way the shaft's does, because the belt joins them, at the cogwheel's rate; the reversing box after it is what
        // gives the drive up to the cogwheel the cogwheel's own direction.
        float shaftRpm = cogIn ? output() : in, cogRpm = cogIn ? in : output();
        double perRpm = Math.PI / 30, rLow = radiusLow(), rUp = 2 * MID - rLow;
        double spinLow = shaftRpm * perRpm;
        // with no cogwheel to turn, the upper pulley just runs with the belt
        double spinUp = Math.signum(shaftRpm) * (cogRpm != 0 ? Math.abs(cogRpm) * perRpm : Math.abs(spinLow) * rLow / rUp);
        angleLow = (angleLow + spinLow * dt) % (2 * Math.PI);
        angleUp = (angleUp + spinUp * dt) % (2 * Math.PI);
        angleDrive = (angleDrive + cogRpm * perRpm * dt) % (2 * Math.PI);
        // the belt runs at the driving pulley's rim speed; faster than this strobes at 60 fps
        double rim = hand * Mth.lerp(Anim.clamp01(flow), spinLow * rLow, spinUp * rUp);
        belt = ((belt + Mth.clamp(rim, -0.6, 0.6) * dt) % RIDGE + RIDGE) % RIDGE;
    }

    @Override
    public void onEnd() {
        HandMenu menu = menu();
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (!grabbed && want != 0 && want != sent && want != synced() && menu != null && gameMode != null) {
            HandMenu.sendValue(menu, want);
        }
        super.onEnd();
        overDial = overKnob = grabbed = false;
        want = sent = grabRpm = 0;
        sinceSend = SEND_EVERY;
        scrolled = 0; // the rest keeps animating through the outro, where the box folds back
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    private static int shade(int argb, double k) {
        return (argb & 0xFF000000) | (int) Math.min(255, (argb >> 16 & 0xFF) * k) << 16 | (int) Math.min(255, (argb >> 8 & 0xFF) * k) << 8
                | (int) Math.min(255, (argb & 0xFF) * k);
    }

    /**
     * Lit solids in the plane's own coordinates: {@code u} across, {@code v} up, {@code w} out of the face. Faces show from
     * both sides, so only the direction each one faces has to be right, not its winding.
     */
    private final class Mesh {
        private final PoseStack.Pose pose;
        private final VertexConsumer vc;
        private final double ox, oy, oz, depth;
        int light;

        Mesh(PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light) {
            pose = ps.last();
            vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            ox = origin.x - cam.x;
            oy = origin.y - cam.y;
            oz = origin.z - cam.z;
            depth = Anim.smoother(Anim.clamp01(open));
            this.light = light;
        }

        void vert(double u, double v, double w, double nu, double nv, double nw, int argb) {
            // everything in front of the back plate folds flat onto it while the box is shut
            double d = w <= BACK ? w : BACK + (w - BACK) * depth;
            vc.vertex(pose.pose(), (float) (ox + across.x * u + up.x * v + normal.x * d), (float) (oy + across.y * u + up.y * v + normal.y * d),
                            (float) (oz + across.z * u + up.z * v + normal.z * d))
                    .color(argb).uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(pose.normal(), (float) (across.x * nu + up.x * nv + normal.x * nw), (float) (across.y * nu + up.y * nv + normal.y * nw),
                            (float) (across.z * nu + up.z * nv + normal.z * nw)).endVertex();
        }

        /** A flat face through four corners, facing (nu, nv, nw). */
        void quad(double u0, double v0, double w0, double u1, double v1, double w1, double u2, double v2, double w2, double u3, double v3,
                double w3, double nu, double nv, double nw, int argb) {
            vert(u0, v0, w0, nu, nv, nw, argb);
            vert(u1, v1, w1, nu, nv, nw, argb);
            vert(u2, v2, w2, nu, nv, nw, argb);
            vert(u3, v3, w3, nu, nv, nw, argb);
        }

        /** A face lying parallel to the plane, facing out. */
        void flat(double w, int argb, double u0, double v0, double u1, double v1, double u2, double v2, double u3, double v3) {
            quad(u0, v0, w, u1, v1, w, u2, v2, w, u3, v3, w, 0, 0, 1, argb);
        }

        void box(double u0, double v0, double w0, double u1, double v1, double w1, int argb) {
            quad(u0, v0, w1, u1, v0, w1, u1, v1, w1, u0, v1, w1, 0, 0, 1, argb);
            quad(u0, v0, w0, u1, v0, w0, u1, v1, w0, u0, v1, w0, 0, 0, -1, argb);
            quad(u1, v0, w0, u1, v1, w0, u1, v1, w1, u1, v0, w1, 1, 0, 0, argb);
            quad(u0, v0, w0, u0, v1, w0, u0, v1, w1, u0, v0, w1, -1, 0, 0, argb);
            quad(u0, v1, w0, u1, v1, w0, u1, v1, w1, u0, v1, w1, 0, 1, 0, argb);
            quad(u0, v0, w0, u1, v0, w0, u1, v0, w1, u0, v0, w1, 0, -1, 0, argb);
        }

        /**
         * A solid turned on a lathe about the line through (cu, cv, cw) along {@code axis}. Its outline is the points
         * ({@code a[i]} along the axis, radius {@code r[i]}), listed so the solid is on the right going from one to the
         * next. Angle 0 lies towards {@code e1} and angle 90 towards {@code e2}. Every {@code every}-th side is painted
         * {@code mark}, which is what shows it turning (0 = none). {@code stride} 1, 2 or 3 gives 24, 12 or 8 sides, and
         * {@code scale} multiplies the radii.
         */
        void lathe(double cu, double cv, double cw, double[] axis, double[] e1, double[] e2, double[] a, double[] r, double scale,
                double angle, int stride, int argb, int every, int mark) {
            double ca = Math.cos(angle), sa = Math.sin(angle);
            for (int j = 0; j + 1 < a.length; j++) {
                double da = a[j + 1] - a[j], dr = (r[j + 1] - r[j]) * scale, len = Math.hypot(da, dr);
                if (len < 1e-9) {
                    continue;
                }
                double na = -dr / len, nr = da / len;
                for (int i = 0, n = 0; i < SEG; i += stride, n++) {
                    int k = (i + stride) % SEG;
                    double c0 = ca * COS[i] - sa * SIN[i], s0 = sa * COS[i] + ca * SIN[i];
                    double c1 = ca * COS[k] - sa * SIN[k], s1 = sa * COS[k] + ca * SIN[k];
                    int colour = every > 0 && n % every == 0 ? mark : argb;
                    turned(cu, cv, cw, axis, e1, e2, a[j], r[j] * scale, c0, s0, na, nr, colour);
                    turned(cu, cv, cw, axis, e1, e2, a[j + 1], r[j + 1] * scale, c0, s0, na, nr, colour);
                    turned(cu, cv, cw, axis, e1, e2, a[j + 1], r[j + 1] * scale, c1, s1, na, nr, colour);
                    turned(cu, cv, cw, axis, e1, e2, a[j], r[j] * scale, c1, s1, na, nr, colour);
                }
            }
        }

        private void turned(double cu, double cv, double cw, double[] axis, double[] e1, double[] e2, double a, double r, double c, double s,
                double na, double nr, int argb) {
            double ru = c * e1[0] + s * e2[0], rv = c * e1[1] + s * e2[1], rw = c * e1[2] + s * e2[2];
            vert(cu + axis[0] * a + r * ru, cv + axis[1] * a + r * rv, cw + axis[2] * a + r * rw, axis[0] * na + nr * ru, axis[1] * na + nr * rv,
                    axis[2] * na + nr * rw, argb);
        }

        /** A square bar, {@code h} to each side, between two points. shortcut: none of its sides show if it points straight out of the face; no bar here does. */
        void rod(double u0, double v0, double w0, double u1, double v1, double w1, double h, int argb) {
            double du = u1 - u0, dv = v1 - v0, dw = w1 - w0, len = Math.sqrt(du * du + dv * dv + dw * dw), pl = Math.hypot(du, dv);
            if (pl < 1e-9) {
                return;
            }
            // two directions square to the bar and to each other
            double pu = dv / pl, pv = -du / pl, qu = -dw * pv / len, qv = dw * pu / len, qw = (du * pv - dv * pu) / len;
            for (int k = 0; k < 4; k++) {
                double sign = k < 2 ? 1 : -1;
                double nu = sign * (k % 2 == 0 ? pu : qu), nv = sign * (k % 2 == 0 ? pv : qv), nw = sign * (k % 2 == 0 ? 0 : qw);
                double tu = k % 2 == 0 ? qu : pu, tv = k % 2 == 0 ? qv : pv, tw = k % 2 == 0 ? qw : 0;
                quad(u0 + h * (nu - tu), v0 + h * (nv - tv), w0 + h * (nw - tw), u0 + h * (nu + tu), v0 + h * (nv + tv), w0 + h * (nw + tw),
                        u1 + h * (nu + tu), v1 + h * (nv + tv), w1 + h * (nw + tw), u1 + h * (nu - tu), v1 + h * (nv - tv), w1 + h * (nw - tw),
                        nu, nv, nw, argb);
            }
        }

        /** A cone pulley on a level shaft at height {@code v}: its two halves start {@code half} either side of the belt's middle. */
        void pulley(double v, double half, double angle) {
            lathe(half, v, AXLE, ALONG, UPRIGHT, spun, CONE_A, CONE_RAD, 1, angle, 1, STEEL, 8, MARK);
            lathe(-half, v, AXLE, ALONG, UPRIGHT, spun, CONE_A_MIRROR, CONE_RAD_MIRROR, 1, angle, 1, STEEL, 8, MARK);
        }

        /** A plain level shaft from {@code u0} to {@code u1}. */
        void spindle(double v, double u0, double u1, double radius, double angle, int argb) {
            lathe(0, v, AXLE, ALONG, UPRIGHT, spun, new double[] {u0, u0, u1, u1}, new double[] {0, radius, radius, 0}, 1, angle, 3, argb, 4, MARK);
        }
    }

    /**
     * The belt's middle line at {@code s} along it, into {@code to} as (height, depth, outward up, outward out). It starts
     * behind the upper pulley and goes over its top, down the front, under the lower pulley and up the back: the way the
     * surface of something turning from up towards out moves.
     */
    private void beltAt(double s, double rLow, double rUp, double beta, double run, double[] to) {
        double over = (Math.PI - 2 * beta) * rUp, under = (Math.PI + 2 * beta) * rLow, total = over + under + 2 * run;
        s = (s % total + total) % total;
        double a, r, v, k = 0;
        if (s < over) {
            a = Math.PI - beta - s / rUp;
            r = rUp;
            v = V_OUT;
        } else if (s < over + run) {
            a = beta;
            k = (s - over) / run;
            r = Mth.lerp(k, rUp, rLow);
            v = Mth.lerp(k, V_OUT, V_IN);
        } else if (s < over + run + under) {
            a = beta - (s - over - run) / rLow;
            r = rLow;
            v = V_IN;
        } else {
            a = Math.PI - beta; // the same direction as -pi - beta, where the lower arc ended
            k = (s - over - run - under) / run;
            r = Mth.lerp(k, rLow, rUp);
            v = Mth.lerp(k, V_IN, V_OUT);
        }
        to[2] = Math.sin(a);
        to[3] = Math.cos(a);
        to[0] = v + r * to[2];
        to[1] = AXLE + r * to[3];
    }

    /** The belt: a loop with a wedge section that fills the grooves, ribbed on the outside so its travel shows. */
    private void belt(Mesh m, double rLow, double rUp) {
        // the straight runs touch both pulleys: they lean by beta when the radii differ
        double span = V_OUT - V_IN, beta = Math.asin((rLow - rUp) / span), run = Math.sqrt(span * span - (rLow - rUp) * (rLow - rUp));
        int n = 0;
        for (int i = 0; i <= ARC; i++, n++) {
            double a = Math.PI - beta - (Math.PI - 2 * beta) * i / ARC;
            beltNv[n] = Math.sin(a);
            beltNw[n] = Math.cos(a);
            beltV[n] = V_OUT + rUp * beltNv[n];
            beltW[n] = AXLE + rUp * beltNw[n];
        }
        for (int i = 0; i <= ARC; i++, n++) {
            double a = beta - (Math.PI + 2 * beta) * i / ARC;
            beltNv[n] = Math.sin(a);
            beltNw[n] = Math.cos(a);
            beltV[n] = V_IN + rLow * beltNv[n];
            beltW[n] = AXLE + rLow * beltNw[n];
        }
        // a hair narrower than the groove, so it never fights the cone faces for depth
        double t = BELT_T / 2, in = BELT_W / 2 - t * LEAN - 0.0015, out = BELT_W / 2 + t * LEAN - 0.0015, lean = 1 / Math.sqrt(1 + LEAN * LEAN);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            for (int side = -1; side <= 1; side += 2) {
                // the outside (side 1) or the inside (side -1)
                double o = side * t, w = side > 0 ? out : in;
                m.vert(-w, beltV[i] + o * beltNv[i], beltW[i] + o * beltNw[i], 0, side * beltNv[i], side * beltNw[i], RUBBER);
                m.vert(w, beltV[i] + o * beltNv[i], beltW[i] + o * beltNw[i], 0, side * beltNv[i], side * beltNw[i], RUBBER);
                m.vert(w, beltV[j] + o * beltNv[j], beltW[j] + o * beltNw[j], 0, side * beltNv[j], side * beltNw[j], RUBBER);
                m.vert(-w, beltV[j] + o * beltNv[j], beltW[j] + o * beltNw[j], 0, side * beltNv[j], side * beltNw[j], RUBBER);
                // and the flank on that hand, leaning with the cones
                double fv = -LEAN * lean;
                m.vert(side * in, beltV[i] - t * beltNv[i], beltW[i] - t * beltNw[i], side * lean, fv * beltNv[i], fv * beltNw[i], RUBBER);
                m.vert(side * out, beltV[i] + t * beltNv[i], beltW[i] + t * beltNw[i], side * lean, fv * beltNv[i], fv * beltNw[i], RUBBER);
                m.vert(side * out, beltV[j] + t * beltNv[j], beltW[j] + t * beltNw[j], side * lean, fv * beltNv[j], fv * beltNw[j], RUBBER);
                m.vert(side * in, beltV[j] - t * beltNv[j], beltW[j] - t * beltNw[j], side * lean, fv * beltNv[j], fv * beltNw[j], RUBBER);
            }
        }
        // ribs, a whole number of them round the loop so there is no seam
        double total = (Math.PI - 2 * beta) * rUp + (Math.PI + 2 * beta) * rLow + 2 * run;
        int ribs = Math.max(1, (int) Math.round(total / RIDGE));
        double pitch = total / ribs, half = 0.005, top = t + 0.004, wide = out * 0.92;
        for (int i = 0; i < ribs; i++) {
            double s = (belt / RIDGE + i) * pitch;
            beltAt(s - half, rLow, rUp, beta, run, ribA);
            beltAt(s + half, rLow, rUp, beta, run, ribB);
            double av = ribA[0], aw = ribA[1], anv = ribA[2], anw = ribA[3], bv = ribB[0], bw = ribB[1], bnv = ribB[2], bnw = ribB[3];
            double tv = bv - av, tw = bw - aw, tl = Math.hypot(tv, tw);
            if (tl < 1e-9) {
                continue;
            }
            tv /= tl;
            tw /= tl;
            m.vert(-wide, av + top * anv, aw + top * anw, 0, anv, anw, RIB);
            m.vert(wide, av + top * anv, aw + top * anw, 0, anv, anw, RIB);
            m.vert(wide, bv + top * bnv, bw + top * bnw, 0, bnv, bnw, RIB);
            m.vert(-wide, bv + top * bnv, bw + top * bnw, 0, bnv, bnw, RIB);
            m.quad(-wide, av + t * anv, aw + t * anw, wide, av + t * anv, aw + t * anw, wide, av + top * anv, aw + top * anw,
                    -wide, av + top * anv, aw + top * anw, 0, -tv, -tw, RIB);
            m.quad(-wide, bv + t * bnv, bw + t * bnw, wide, bv + t * bnv, bw + t * bnw, wide, bv + top * bnv, bw + top * bnw,
                    -wide, bv + top * bnv, bw + top * bnw, 0, tv, tw, RIB);
        }
    }

    @Override
    public void renderScene(PoseStack ps, MultiBufferSource buffers, Camera camera, int light) {
        // Not left to drawExtras, which waits for the menu and stops for the outro: the block's own wall and value panel
        // must stay out of sight from the first frame of the scene to the last.
        machine(new Mesh(ps, buffers, camera.getPosition(), light));
        super.renderScene(ps, buffers, camera, light);
    }

    /** The gearbox and the works in it. */
    private void machine(Mesh m) {
        if (blockEntity(SpeedControllerBlockEntity.class) == null) {
            return;
        }
        // The back plate goes up whole and at once. With the frame's four sides, which reach back to the block, it closes
        // the wall in from every angle; nothing of ours lies in the wall's own plane except the frame's hidden back.
        m.flat(BACK, PLATE_BACK, -WIN, -WIN, WIN, -WIN, WIN, WIN, -WIN, WIN);
        m.box(-0.5, WIN, 0, 0.5, 0.5, DEPTH, BRASS);
        m.box(-0.5, -0.5, 0, 0.5, -WIN, DEPTH, BRASS);
        m.box(-0.5, -WIN, 0, -WIN, WIN, DEPTH, BRASS);
        m.box(WIN, -WIN, 0, 0.5, WIN, DEPTH, BRASS);
        if (m.depth < 0.02) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            double u = (i % 2 == 0 ? -1 : 1) * 0.47, v = (i < 2 ? -1 : 1) * 0.47;
            m.box(u - 0.013, v - 0.013, DEPTH, u + 0.013, v + 0.013, DEPTH + 0.006, shade(BRASS, 0.7));
        }
        if (Double.isNaN(ratio)) {
            return;
        }
        double rLow = radiusLow(), rUp = 2 * MID - rLow;

        // the shaft line, in bearings on the box's sides, with a collar inside each
        m.spindle(V_IN, -WIN, WIN, HUB, angleLow, DARK_STEEL);
        for (int s = -1; s <= 1; s += 2) {
            m.box(Math.min(s * WIN, s * 0.405), V_IN - 0.045, AXLE - 0.045, Math.max(s * WIN, s * 0.405), V_IN + 0.045, AXLE + 0.045, STEEL);
            m.spindle(V_IN, Math.min(s * 0.405, s * 0.365), Math.max(s * 0.405, s * 0.365), 0.04, angleLow, shade(STEEL, 0.8));
        }
        // the upper shaft, from its hanger under the roof to the reversing box
        m.box(-0.225, V_OUT - 0.045, AXLE - 0.035, -0.185, WIN, AXLE + 0.035, shade(BRASS, 0.85));
        m.spindle(V_OUT, -0.2, BOX_U, HUB, angleUp, DARK_STEEL);

        m.pulley(V_IN, gap(rLow), angleLow);
        m.pulley(V_OUT, gap(rUp), angleUp);
        belt(m, rLow, rUp);

        // the reversing box with its handle, and the drive up through the roof to the cogwheel (dull with no cogwheel)
        double lit = cogOnTop() ? 1 : 0.5;
        m.box(BOX_U - BOX, V_OUT - BOX, AXLE - BOX, BOX_U + BOX, V_OUT + BOX, AXLE + BOX, BRASS);
        m.box(BOX_U - 0.035, V_OUT - 0.035, AXLE + BOX, BOX_U + 0.035, V_OUT + 0.035, AXLE + BOX + 0.004, shade(BRASS, 0.7));
        // upright, so a positive cogwheel speed turns it right-handed about straight up, whichever side it is seen from
        m.lathe(BOX_U, 0, AXLE, UPRIGHT, ALONG, INWARD, new double[] {V_OUT + BOX, V_OUT + BOX, 0.4, 0.4, WIN, WIN},
                new double[] {0, 0.02, 0.02, 0.036, 0.036, 0}, 1, angleDrive, 3, shade(DARK_STEEL, lit), 4, shade(MARK, lit));
        double kv = V_OUT + Mth.lerp(Anim.smoother(Anim.clamp01(knob)), 0.04, -0.04);
        m.rod(BOX_U + BOX - 0.01, V_OUT, AXLE + 0.02, KNOB_U, kv, KNOB_W, 0.007, DARK_STEEL);
        if (glowKnob > 0.3) {
            m.light = FULL_BRIGHT;
        }
        m.lathe(KNOB_U, kv, KNOB_W, ALONG, UPRIGHT, OUTWARD, BALL_A, BALL_RAD, 1 + 0.15 * glowKnob, 0, 2,
                glowKnob > 0.3 ? 0xFFE8554A : 0xFFC0392B, 0, 0);
    }

    @Override
    protected void drawExtras(HandMenu menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
        SpeedControllerBlockEntity be = blockEntity(SpeedControllerBlockEntity.class);
        if (be == null || Double.isNaN(ratio)) {
            return;
        }
        int shown = shown();
        float in = input();
        boolean cog = cogOnTop();

        // the dial, floating beside the box: a brass drum with a lit face, a tick per doubling, the needle on the target
        Mesh m = new Mesh(ps, buffers, cam, light);
        double face = DIAL_W, rim = DIAL_R + 0.012 + 0.004 * glowDial;
        m.lathe(dialU, DIAL_V, 0, OUTWARD, ALONG, UPRIGHT, new double[] {face - 0.03, face - 0.03, face + 0.006, face + 0.006, face},
                new double[] {0, rim, rim, DIAL_R, DIAL_R}, grow, 0, 1, glowDial > 0.3 ? shade(BRASS, 1.25) : BRASS, 0, 0);
        m.light = FULL_BRIGHT;
        m.lathe(dialU, DIAL_V, 0, OUTWARD, ALONG, UPRIGHT, new double[] {face, face}, new double[] {DIAL_R, 0}, grow, 0, 1, 0xFF1B1E24, 0, 0);
        if (grow < 0.9) {
            return;
        }
        int max = max();
        for (int n = 1; ; n = Math.min(n * 2, max)) {
            double a = dialAngle(n), c = Math.cos(a), s = Math.sin(a), t = 0.006, r0 = DIAL_R - 0.028, r1 = DIAL_R - 0.006;
            m.flat(face + 0.001, n == Math.abs(shown) ? 0xFF8CF2FF : 0xFF5A6070, dialU + c * r0 + s * t, DIAL_V + s * r0 - c * t,
                    dialU + c * r1 + s * t, DIAL_V + s * r1 - c * t, dialU + c * r1 - s * t, DIAL_V + s * r1 + c * t,
                    dialU + c * r0 - s * t, DIAL_V + s * r0 + c * t);
            if (n >= max) {
                break;
            }
        }
        if (!Double.isNaN(needle)) {
            double c = Math.cos(needle), s = Math.sin(needle), t = 0.008, r = DIAL_R - 0.03;
            m.flat(face + 0.003, glowDial > 0.3 ? 0xFFE8554A : 0xFFC0392B, dialU + s * t, DIAL_V - c * t, dialU + c * r, DIAL_V + s * r,
                    dialU + c * r, DIAL_V + s * r, dialU - s * t, DIAL_V + c * t);
        }
        m.lathe(dialU, DIAL_V, 0, OUTWARD, ALONG, UPRIGHT, new double[] {face, face + 0.008, face + 0.008}, new double[] {0.016, 0.016, 0},
                1, 0, 2, BRASS, 0, 0);

        // Readouts, after every solid so the batch is not broken up. Each side of the belt wears its own tag: the
        // cogwheel's under the upper shaft, the shaft's under the lower. Which is IN and which OUT follows the power, and
        // the two fade through each other when it turns round.
        String signed = (shown > 0 ? "+" : "") + shown;
        boolean cogIn = flow > 0.5;
        double clear = Math.abs(2 * Anim.clamp01(flow) - 1), tags = DEPTH - 0.006;
        Component inTag = tr("speed_controller.in", String.valueOf(Math.round(Math.abs(in))));
        Component outTag = tr("speed_controller.out", signed);
        int inColour = fade(in == 0 ? 0xFFFFB347 : flash > 0 ? 0xFFFFE08A : 0xFFE8F6FF, clear);
        int outColour = fade(want != 0 ? 0xFFFFD27F : 0xFFFFFFFF, clear); // amber until the server has answered
        float inScale = 0.0032f + 0.0006f * (float) flash;
        text(ps, buffers, cam, cogIn ? outTag : inTag, at(-0.29, V_IN - 0.08, tags), cogIn ? 0.0032f : inScale, cogIn ? outColour : inColour);
        text(ps, buffers, cam, cogIn ? inTag : outTag, at(-0.29, V_OUT - 0.09, tags), cogIn ? inScale : 0.0032f, cogIn ? inColour : outColour);
        Component status = !cog ? tr("speed_controller.no_cog")
                : be.isOverStressed() ? tr("crank.overstressed")
                : in == 0 ? tr("speed_controller.no_power", signed)
                : tr("speed_controller.cvt_ratio", String.format(Locale.ROOT, "%.2f", Math.abs(shown / in)));
        text(ps, buffers, cam, status, at(0, -0.3, tags), 0.0028f, !cog || be.isOverStressed() ? 0xFFFF6B6B : in == 0 ? 0xFFFFB347 : 0xFF8CF2FF);
        text(ps, buffers, cam, tr("speed_controller.output", signed), at(dialU, DIAL_V - 0.062, face + 0.006), 0.0036f,
                want != 0 ? 0xFFFFD27F : 0xFFFFFFFF);
    }
}

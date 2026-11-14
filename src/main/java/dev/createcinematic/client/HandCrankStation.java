package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import dev.createcinematic.HandMenu;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hand crank: hold the handle and circle the mouse round the axle. The work plane is the circle the grip travels, seen
 * from the side FACING points to, so the mouse and the handle go round the same way. Create only knows on/off and a
 * direction (a fixed 32 RPM), so the circling is counted in degrees and every {@link #STEP} of it becomes one of Create's
 * own turns, sent as a {@link HandMenu} button. The handle itself is Create's: the hand just holds on to it.
 */
final class HandCrankStation extends CreateStation<HandMenu> {
    // block/hand_crank/handle.json: the grip is 7 px off the axle, and its middle 2 px in front of the block centre
    private static final double RADIUS = 7 / 16.0, GRIP = 2 / 16.0;
    /** Degrees of circling that buy one turn, and Create's own repeat while use is held (ticks). */
    private static final double STEP = 15;
    private static final int REPEAT = 4;
    /** HandCrankBlockEntity#turn keeps the crank driven this many ticks. */
    private static final int DRIVEN = 10;
    private static final double STIFFNESS = 40;
    private static final int DOTS = 24;

    /** The grip's offset from the axle at Create's angle 0, and a quarter turn on. */
    private final Vec3 arm, quarter;
    private boolean over, grabbed;
    /** Where the mouse was round the axle (degrees, NaN = unknown), and how far it has circled since the last turn. */
    private double mouseAngle = Double.NaN, travel;
    private int sinceTurn = DRIVEN, dir = 1;
    private double glow, drive;

    HandCrankStation(BlockPos pos, Direction side, BlockState state) {
        // on a wall: its face; on a floor: looking down at it; on a ceiling: the plane flipped over, looking up
        super(pos, side, HandMenu.class, "hand_crank", 90 * y(state), GRIP * y(state), y(state) == 0 ? GRIP : 0,
                y(state) == 0 ? 1.0 : 0.75, y(state) == 0 ? 0.2 : 0.9 * y(state));
        Direction facing = state.getValue(BlockStateProperties.FACING);
        // both of Create's renderers turn the north-facing model onto FACING, then spin it about the positive axis
        arm = new Vec3(new Quaternionf().rotateTo(0, 0, -1, facing.getStepX(), facing.getStepY(), facing.getStepZ())
                .transform(new Vector3f((float) -RADIUS, 0, 0)));
        quarter = Vec3.atLowerCornerOf(Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis()).getNormal()).cross(arm);
    }

    private static int y(BlockState state) {
        return state.getValue(BlockStateProperties.FACING).getStepY();
    }

    @Override
    protected List<Spot> layout(HandMenu menu) {
        return List.of();
    }

    private static float partialTick() {
        return Minecraft.getInstance().getPartialTick();
    }

    /** The grip where Create draws it, {@code ahead} degrees further on and {@code out} off the plane. */
    private Vec3 grip(HandCrankBlockEntity crank, double ahead, double out) {
        double a = Math.toRadians(crank.getIndependentAngle(partialTick()) + ahead);
        return origin.add(arm.scale(Math.cos(a))).add(quarter.scale(Math.sin(a))).add(normal.scale(out));
    }

    /** Degrees round the axle, anticlockwise as the camera sees it. */
    private double angle(Vec3 p) {
        Vec3 d = p.subtract(origin);
        return Math.toDegrees(Math.atan2(d.dot(up), d.dot(across)));
    }

    @Override
    public boolean showsInventory() {
        return false; // nothing to move, and the mouse needs the room
    }

    /** Wall and ceiling cranks are worked from the front, so the host allows them higher than work on a top face. */
    @Override
    public boolean workOnTop() {
        return normal.y > 0;
    }

    /** While cranking, the host mustn't offer to move over to a station the mouse happens to circle across. */
    @Override
    public boolean busy() {
        return super.busy() || grabbed;
    }

    @Override
    public boolean crouches() {
        return normal.y >= 0;
    }

    @Override
    public float bodyPitch() {
        return normal.y < 0 ? 0 : super.bodyPitch();
    }

    @Override
    public void updateCursor(boolean off) {
        aim = null;
        HandCrankBlockEntity crank = blockEntity(HandCrankBlockEntity.class);
        Vec3 p = menu() == null || crank == null || off && !grabbed ? null : Picking.cursorOnPlane(origin, normal);
        double r = p == null ? 0 : p.distanceTo(origin);
        double now = r > 0.05 ? angle(p) : Double.NaN; // right on the axle the direction means nothing
        if (grabbed && !Double.isNaN(now) && !Double.isNaN(mouseAngle)) {
            travel += Mth.wrapDegrees(now - mouseAngle);
        }
        mouseAngle = now;
        over = p != null && r < RADIUS + 0.15;
        if (over && !grabbed) {
            aim = grip(crank, 0, 0.1);
        }
    }

    @Override
    public void clearCursor() {
        super.clearCursor();
        over = false;
        mouseAngle = Double.NaN;
    }

    @Override
    protected void act(int button, boolean shift) {
        if (button == 0 && over) {
            grabbed = true;
            travel = 0;
            kick(0.06);
            sound(SoundEvents.WOOD_HIT, 0.5f, 1.3f);
        }
    }

    @Override
    public void release(int button) {
        if (button == 0) {
            grabbed = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (sinceTurn < DRIVEN) {
            sinceTurn++;
        }
        HandMenu menu = menu();
        HandCrankBlockEntity crank = blockEntity(HandCrankBlockEntity.class);
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        // also lets go when the release never arrived (focus lost while holding)
        if (menu == null || crank == null || gameMode == null || !leftHeld()) {
            grabbed = false;
            return;
        }
        if (grabbed && sinceTurn >= REPEAT && Math.abs(travel) >= STEP) {
            // HandCrankBlockEntity#getGeneratedSpeed: forward spins anticlockwise seen from the side FACING points to,
            // which is where the camera is, whichever way the crank faces
            boolean back = travel < 0;
            gameMode.handleInventoryButtonClick(menu.containerId, back ? HandMenu.BACKWARD : HandMenu.FORWARD);
            crank.turn(back); // as HandCrankBlock#useItemOn does on the client: Create's cranking sound
            dir = back ? -1 : 1;
            travel = 0;
            sinceTurn = 0;
        }
    }

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        glow = Anim.approach(glow, grabbed ? 1 : over ? 0.6 : 0, 10, Scene.frameDt);
        drive = Anim.approach(drive, grabbed && sinceTurn < DRIVEN ? 1 : 0, 6, Scene.frameDt);
    }

    @Override
    public Vec3[] handsOverride() {
        HandCrankBlockEntity crank = grabbed ? blockEntity(HandCrankBlockEntity.class) : null;
        // the host's hand spring trails a moving target by 2 / stiffness seconds: aim that far round, so the fist stays on the grip
        return crank == null ? null
                : new Vec3[] {grip(crank, crank.chasingAngularVelocity * 20 * 2 / STIFFNESS, 0.1), rest(false)};
    }

    @Override
    public double handsStiffness() {
        return STIFFNESS;
    }

    @Override
    public void onEnd() {
        super.onEnd();
        over = grabbed = false;
        mouseAngle = Double.NaN;
        travel = glow = drive = 0;
        sinceTurn = DRIVEN;
    }

    /** Read-only gauge: the synced speed going out, which way it turns on screen, or that the network is too heavy. */
    @Override
    public void renderScreen(GuiGraphics gui, Font font, float alpha) {
        HandCrankBlockEntity crank = menu() == null ? null : blockEntity(HandCrankBlockEntity.class);
        if (crank == null) {
            return;
        }
        // getSpeed is about the positive axis: seen from the side FACING points to, positive is anticlockwise only there
        float speed = crank.getSpeed() * crank.getBlockState().getValue(BlockStateProperties.FACING).getAxisDirection().getStep();
        if (crank.isOverStressed() && crank.inUse > 0) {
            statusLine(gui, font, tr("crank.overstressed").withStyle(ChatFormatting.RED), alpha);
        } else if (speed != 0) {
            statusLine(gui, font, tr("crank.gauge", Math.round(Math.abs(speed)), speed > 0 ? "⟲" : "⟳"), alpha);
        }
    }

    /** The grip's path as a ring of dots; while cranking, the ones just ahead of the grip light up, showing the way round. */
    @Override
    protected void drawExtras(HandMenu menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
        HandCrankBlockEntity crank = blockEntity(HandCrankBlockEntity.class);
        if (crank == null || glow < 0.02) {
            return;
        }
        double at = angle(grip(crank, 0, 0));
        for (int i = 0; i < DOTS; i++) {
            double a = i * 360.0 / DOTS;
            double ahead = Mth.wrapDegrees(a - at) * dir;
            double lit = ahead > 0 && ahead < 90 ? drive * (1 - ahead / 90) : 0;
            double size = 0.006 + 0.006 * lit;
            rect(ps, buffers, cam, at(RADIUS * Math.cos(Math.toRadians(a)), RADIUS * Math.sin(Math.toRadians(a)), 0), size, size,
                    fade(0xFF8CF2FF, glow * (0.35 + 0.65 * lit)));
        }
    }
}

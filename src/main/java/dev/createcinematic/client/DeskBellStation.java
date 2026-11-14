package dev.createcinematic.client;

import com.simibubi.create.content.redstone.deskBell.DeskBellBlockEntity;
import dev.createcinematic.HandMenu;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Desk bell: the hand hovers over the plunger and a click slaps it down. The ding goes out when the hand lands, and the
 * plunger and the swinging bell are Create's own animation. A click while Create's plunger is still down waits for it.
 */
final class DeskBellStation extends HandStation {
    // block/desk_bell/plunger.json: the cap is 10 px off the mount, 2 px in front of the block centre
    private static final double CAP = 2 / 16.0;
    /** Seconds the hand takes to land, and to come back up. */
    private static final double DOWN = 0.08, UP = 0.25;
    private static final double HOVER = 0.17, TOUCH = 0.09;

    private double pressedAt = -10;
    private boolean rung = true, queued;

    DeskBellStation(BlockPos pos, Direction side, BlockState state) {
        super(pos, side, state, "desk_bell", CAP);
    }

    private boolean pressing() {
        return Scene.clock() - pressedAt < DOWN + UP;
    }

    @Override
    public boolean busy() {
        return super.busy() || queued || pressing();
    }

    @Override
    public void updateCursor(boolean off) {
        aim = null;
        Vec3 p = off || menu() == null ? null : Picking.cursorOnPlane(origin, normal);
        over = p != null && p.distanceTo(origin) < 0.25;
    }

    @Override
    protected void act(int button, boolean shift) {
        if (button == 0 && over) {
            queued = true;
        }
    }

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        DeskBellBlockEntity bell = blockEntity(DeskBellBlockEntity.class);
        if (!rung && Scene.clock() - pressedAt >= DOWN) {
            rung = true;
            if (send(HandMenu.RING)) {
                kick(0.12);
            }
        }
        if (bell == null || menu() == null) {
            queued = false;
        } else if (queued && !pressing() && bell.animation.getValue(partialTick()) <= 0.5f) {
            // DeskBellRenderer: the plunger is down for the first half of the animation
            queued = false;
            rung = false;
            pressedAt = Scene.clock();
        }
    }

    @Override
    public Vec3[] handsOverride() {
        if (!over && !pressing()) {
            return null;
        }
        double t = Scene.clock() - pressedAt;
        double down = t < DOWN ? Anim.inCubic(t / DOWN) : 1 - Anim.smoother((t - DOWN) / UP);
        return new Vec3[] {at(0, 0, HOVER - (HOVER - TOUCH) * Anim.clamp01(down)), rest(false)};
    }

    @Override
    public double handsStiffness() {
        return 40;
    }

    @Override
    public void onEnd() {
        super.onEnd();
        pressedAt = -10;
        rung = true;
        queued = false;
    }
}

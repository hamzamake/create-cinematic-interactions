package dev.createcinematic.client;

import dev.createcinematic.HandMenu;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** What the mouse-worked scenes on {@link HandMenu} share: no slots, a grab that can't get stuck, and the way actions go out. */
abstract class HandStation extends CreateStation<HandMenu> {
    protected boolean over, grabbed;

    protected HandStation(BlockPos pos, Direction side, String name, double tilt, double lift, double out, double eyeBack, double eyeUp) {
        super(pos, side, HandMenu.class, name, tilt, lift, out, eyeBack, eyeUp);
    }

    /**
     * A block on FACING, worked on the plane {@code depth} in front of the block centre. On a wall: its face; on a floor:
     * looking down at it; on a ceiling: the plane flipped over, looking up.
     */
    protected HandStation(BlockPos pos, Direction side, BlockState state, String name, double depth) {
        this(pos, side, name, 90 * y(state), depth * y(state), y(state) == 0 ? depth : 0, y(state) == 0 ? 1.0 : 0.75,
                y(state) == 0 ? 0.2 : 0.9 * y(state));
    }

    private static int y(BlockState state) {
        return state.getValue(BlockStateProperties.FACING).getStepY();
    }

    protected static float partialTick() {
        return Minecraft.getInstance().getPartialTick();
    }

    /** Sends a {@link HandMenu} button and runs its client half; false when there is nothing to send it to. */
    protected boolean send(int button) {
        Minecraft mc = Minecraft.getInstance();
        HandMenu menu = menu();
        if (menu == null || mc.gameMode == null || mc.level == null || mc.player == null) {
            return false;
        }
        mc.gameMode.handleInventoryButtonClick(menu.containerId, button);
        menu.echo(mc.level, mc.player, button);
        return true;
    }

    /** Sends a {@link HandMenu} value (its own message on 1.20.1); false when there is nothing to send it to. */
    protected boolean sendValue(int value) {
        HandMenu menu = menu();
        if (menu == null) {
            return false;
        }
        HandMenu.sendValue(menu, value);
        return true;
    }

    @Override
    protected List<Spot> layout(HandMenu menu) {
        return List.of();
    }

    @Override
    public boolean showsInventory() {
        return false; // nothing to move, and the mouse needs the room
    }

    /** Only a plane lying face up is work on a top; from the front the host allows the block higher. */
    @Override
    public boolean workOnTop() {
        return normal.y > 0;
    }

    @Override
    public boolean crouches() {
        return normal.y >= 0;
    }

    @Override
    public float bodyPitch() {
        return normal.y < 0 ? 0 : super.bodyPitch();
    }

    /** Mid-gesture, the host mustn't offer to move over to a station the mouse happens to cross. */
    @Override
    public boolean busy() {
        return super.busy() || grabbed;
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
        // also lets go when the release never arrived (focus lost while holding)
        if (grabbed && (menu() == null || !leftHeld())) {
            release(0);
        }
    }

    @Override
    public void clearCursor() {
        super.clearCursor();
        over = false;
    }

    @Override
    public void onEnd() {
        super.onEnd();
        over = grabbed = false;
    }
}

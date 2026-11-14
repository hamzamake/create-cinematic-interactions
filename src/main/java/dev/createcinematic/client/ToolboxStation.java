package dev.createcinematic.client;

import com.simibubi.create.content.equipment.toolbox.ToolboxMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Toolbox, worked from its front, looking down into the open box: its 8 compartments in Create's ring. Each compartment
 * is clicked through its representative slot (compartment * 4), exactly as Create's window does; ToolboxMenu#clicked
 * spreads the stacks and frees an emptied compartment on the server.
 */
final class ToolboxStation extends CreateStation<ToolboxMenu> {
    // Create's ring as ToolboxMenu#addSlots lays it out (pixels; 33 = one step), clockwise from the far left corner
    private static final int[] X = {0, 33, 66, 72, 66, 33, 0, -6};
    private static final int[] Y = {0, -6, 0, 33, 66, 72, 66, 33};
    private static final int STACKS = 4; // ToolboxInventory.STACKS_PER_COMPARTMENT

    ToolboxStation(BlockPos pos, Direction side) {
        // AllShapes.TOOLBOX: 14 x 9 x 8 pixels, centred; the tray floats just over its rim
        super(pos, side, ToolboxMenu.class, "toolbox", 90, 9 / 16.0 - 0.5 + 0.035, 0, 0.78, 0.8);
    }

    @Override
    protected List<Spot> layout(ToolboxMenu menu) {
        List<Spot> spots = new ArrayList<>(8);
        for (int c = 0; c < 8; c++) {
            spots.add(slotSpot(c * STACKS, (X[c] - 33) / 33.0 * 0.25, -(Y[c] - 33) / 33.0 * 0.155, 0.056, null));
        }
        return spots;
    }

    private static ItemStack filter(ToolboxMenu menu, Spot s) {
        return menu.contentHolder == null ? ItemStack.EMPTY : menu.getFilter(s.slot() / STACKS);
    }

    @Override
    protected ItemStack display(ToolboxMenu menu, Spot s, Slot slot) {
        return slot.hasItem() ? slot.getItem() : filter(menu, s); // an emptied compartment still shows what it is kept for
    }

    @Override
    protected String count(ToolboxMenu menu, Spot s, Slot slot) {
        int total = menu.totalCountInCompartment(s.slot() / STACKS);
        return total > 1 ? String.valueOf(total) : null; // an emptied, reserved compartment shows its item without a "0"
    }

    @Override
    protected void emptyClick(ToolboxMenu menu, Spot s) {
        if (filter(menu, s).isEmpty()) {
            deny();
            return;
        }
        slotClick(s.slot(), 0, false); // an empty-handed click on an empty compartment frees it (ToolboxMenu#clicked)
        sound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.4f, 0.8f);
    }

    @Override
    protected List<Component> emptyLines(ToolboxMenu menu, Spot s) {
        ItemStack filter = filter(menu, s);
        if (filter.isEmpty()) {
            return List.of(tr("toolbox.free").withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.of(filter.getHoverName(), tr("toolbox.reserved").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected List<Component> notes(ToolboxMenu menu, Spot s) {
        int total = menu.totalCountInCompartment(s.slot() / STACKS);
        return total > 0 ? List.of(tr("toolbox.total", total).withStyle(ChatFormatting.DARK_AQUA)) : List.of();
    }

    @Override
    public SoundEvent touchSound() {
        return SoundEvents.WOOD_STEP;
    }
}

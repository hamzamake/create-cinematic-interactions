package dev.createcinematic;

import com.simibubi.create.content.equipment.toolbox.ToolboxMenu;
import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import com.simibubi.create.content.schematics.cannon.SchematicannonMenu;
import com.simibubi.create.content.schematics.table.SchematicTableMenu;
import dev.dyslexic.cinematic.api.AddonStations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Every Create block that gets a scene. One host definition per block ID (the host allows one scene per block);
 * the scene ID is {@code createcinematic:<create block path>}. Common code: safe on a dedicated server.
 */
public final class Stations {
    private Stations() {}

    /** What the block is; decides the menu, the extra data Create's client menu reads, and the scene. */
    public enum Kind {
        /** 16 colours. Menu: block slots first (8 compartments x 4 stacks), then player slots. Extra data: SmartBlockEntity#sendToMenu. */
        TOOLBOX(ToolboxMenu.class),
        /** Menu: 5 block slots first, then player. Extra data: sendToMenu. */
        SCHEMATICANNON(SchematicannonMenu.class),
        /** Menu: input 0, output 1, then player. Extra data: sendToMenu. */
        SCHEMATIC_TABLE(SchematicTableMenu.class),
        /** Frogport (any side) and the 16 postboxes (front). Menu: 18 block slots first, then player. Extra data: block pos. */
        PACKAGE_PORT(PackagePortMenu.class),
        /** Menu: player slots 0-35 first, then 9 ghost slots 36-44. Extra data: block pos. */
        REDSTONE_REQUESTER(RedstoneRequesterMenu.class),
        // Below: no Create menu; our slot-less HandMenu anchors the scene and carries the actions.
        /** Worked from the side it faces (any, if it faces up or down). */
        HAND_CRANK(HandMenu.class),
        /** Copper and the 16 dyed ones. Worked like the crank. */
        VALVE_HANDLE(HandMenu.class),
        /** Worked from the front on a wall; from either side of its swing on a floor or ceiling. */
        ANALOG_LEVER(HandMenu.class),
        /** Worked like the crank. */
        DESK_BELL(HandMenu.class),
        /** Rotation speed controller. Worked from the two sides off its shaft axis. */
        SPEED_CONTROLLER(HandMenu.class);

        public final Class<? extends AbstractContainerMenu> menu;

        /** Menu-less in Create: worked through {@link HandMenu}, with no window of its own. */
        public boolean hand() {
            return menu == HandMenu.class;
        }

        Kind(Class<? extends AbstractContainerMenu> menu) {
            this.menu = menu;
        }
    }

    /** {@code front}: worked from the block's FACING side only (it points at the player who placed it); else any side. */
    public record Entry(ResourceLocation id, ResourceLocation block, Kind kind, boolean front) {}

    private static final List<Direction> AROUND = List.copyOf(Direction.Plane.HORIZONTAL.stream().toList());

    public static final List<Entry> ALL = build();
    private static final Map<ResourceLocation, Entry> BY_BLOCK = new HashMap<>();
    static {
        for (Entry e : ALL) BY_BLOCK.put(e.block(), e);
    }

    private static List<Entry> build() {
        List<Entry> list = new ArrayList<>();
        for (DyeColor colour : DyeColor.values()) {
            add(list, colour.getSerializedName() + "_toolbox", Kind.TOOLBOX, true);
            add(list, colour.getSerializedName() + "_postbox", Kind.PACKAGE_PORT, true);
        }
        add(list, "schematicannon", Kind.SCHEMATICANNON, false);
        add(list, "schematic_table", Kind.SCHEMATIC_TABLE, true);
        add(list, "package_frogport", Kind.PACKAGE_PORT, false);
        add(list, "redstone_requester", Kind.REDSTONE_REQUESTER, false);
        add(list, "hand_crank", Kind.HAND_CRANK, true);
        add(list, "copper_valve_handle", Kind.VALVE_HANDLE, true);
        for (DyeColor colour : DyeColor.values()) add(list, colour.getSerializedName() + "_valve_handle", Kind.VALVE_HANDLE, true);
        add(list, "analog_lever", Kind.ANALOG_LEVER, true);
        add(list, "desk_bell", Kind.DESK_BELL, true);
        add(list, "rotation_speed_controller", Kind.SPEED_CONTROLLER, true);
        return List.copyOf(list);
    }

    private static void add(List<Entry> list, String path, Kind kind, boolean front) {
        list.add(new Entry(CreateCinematic.id(path), ResourceLocation.fromNamespaceAndPath("create", path), kind, front));
    }

    @Nullable
    public static Entry forBlock(Block block) {
        return BY_BLOCK.get(BuiltInRegistries.BLOCK.getKey(block));
    }

    @Nullable
    public static Entry forState(BlockState state) {
        return forBlock(state.getBlock());
    }

    /** The sides a scene may be worked from; none while this click belongs to Create itself (see {@link NativeUse}). */
    static List<Direction> sides(Entry entry, BlockState state) {
        if (NativeUse.suppressed()) return List.of();
        switch (entry.kind()) {
            case HAND_CRANK, VALVE_HANDLE, DESK_BELL -> {
                // FACING points away from the block it hangs on, so the part you work is on that side
                return state.getOptionalValue(BlockStateProperties.FACING)
                        .map(f -> f.getAxis().isHorizontal() ? List.of(f) : AROUND).orElse(List.of());
            }
            case ANALOG_LEVER -> {
                Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                // on a floor or ceiling it swings along FACING: stand beside the swing so it moves across the screen
                return state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL ? List.of(f)
                        : List.of(f.getClockWise(), f.getCounterClockWise());
            }
            case SPEED_CONTROLLER -> {
                // Create's value box sits on the two faces off the shaft axis
                Direction.Axis shaft = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                return AROUND.stream().filter(d -> d.getAxis() != shaft).toList();
            }
            default -> {}
        }
        if (!entry.front()) return AROUND;
        return state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).map(List::of).orElse(List.of());
    }

    /** Common setup: registers every station with the host. Must run before the client registers its scenes. */
    static void registerAll() {
        for (Entry e : ALL) {
            AddonStations.register(new AddonStations.Definition(e.id(), e.block(), e.kind().menu, state -> sides(e, state)));
        }
    }
}

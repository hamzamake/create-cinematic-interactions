# Coverage

Create 6.0.10, every block ID from `AllBlocks.java`. `*_x` means all 16 dye colours: white, orange, magenta, light_blue, yellow, lime, pink, gray, light_gray, cyan, purple, blue, brown, green, red and black. Palette stone, window, pane and bar families are each one row.

The host API only supports blocks that open a real menu (FEASIBILITY.md section 1), so that is what decides almost every skip.

## Supported: 58 blocks

| Block ID | Worked from | Scene |
|---|---|---|
| `*_toolbox` (16) | front | Lid-open view from above; the 8 compartments in Create's ring order, each showing its item and total count |
| `schematic_table` | front | Blank and written slots on the top; schematic picker (scroll or click), Refresh, Write button with upload progress |
| `schematicannon` | any side | Schematic in/out, book in, material list out, gunpowder with fuel bar; Play/Pause/Stop; status and progress |
| `package_frogport` | any side | 2x9 package panel in front of the block; read-only address plate |
| `*_postbox` (16) | front | 2x9 package panel in front of the block; read-only address plate |
| `redstone_requester` | any side | 9 ghost slots (set with an item, clear with an empty hand) |
| `hand_crank` | the side it faces (any, on a floor or ceiling) | Crank it with the mouse: grab the handle and circle. A gauge shows RPM, direction and overstress |
| `copper_valve_handle`, `*_valve_handle` (17) | the side it faces (any, on a floor or ceiling) | Twist the wheel with the mouse, one step per twist; the mouse wheel sets the step angle |
| `analog_lever` | front on a wall; beside its swing on a floor or ceiling | Drag the lever notch by notch, 0 to 15 |
| `desk_bell` | the side it faces (any, on a floor or ceiling) | Tap the plunger |
| `rotation_speed_controller` | the two sides off its shaft | A 3D cutaway CVT (side panel off, belt between cone pulleys, driven from the shaft or the cog on top): drag the dial or scroll to set the target RPM, instead of Create's value panel |

The last five rows have no Create menu: the add-on's own slot-less `HandMenu` anchors the scene.

On all supported menu blocks, sneak + use with an empty hand opens Create's own window. The hand-worked blocks have no window: sneak + use is Create's classic click (crank or valve backwards, lever down a notch, bell). That is where addresses, amounts and allow-partial are edited.

## Skipped

### Has a menu, but not usable as a scene
| Block ID | Reason |
|---|---|
| `stock_ticker` | Its block menu is a category editor built from screen-only widgets and packets (lists, renaming, ordering), with nothing a slot scene can do. The request menu opens from the seated keeper entity, not the block. |
| `factory_gauge` | Up to 4 panels per block. The menu opens for the clicked panel only, and only with an empty filter and an empty hand. A block-level menu provider has no hit information to pick the panel. |

### Menu-less (no host API for these yet)
`depot`, `weighted_ejector`, `basin`, `blaze_burner`, `lit_blaze_burner`, `mechanical_press`, `mechanical_mixer`, `deployer`,
`mechanical_crafter`, `mechanical_saw`, `millstone`, `crushing_wheel`, `encased_fan`, `spout`, `item_drain`, `belt`, `mechanical_arm`,
`packager`, `repackager`, `item_vault`, `item_hatch`, `fluid_tank`, `creative_crate`, `creative_fluid_tank`, `display_link`,
`sequenced_gearshift`, `track_station`, `elevator_contact`, `clipboard`, `stock_link`, `cart_assembler`, `display_board`,
`lectern_controller`, `controls`, `contraption_controls`, `placard`, `*_seat`,
`steam_whistle`, `haunted_bell`, `peculiar_bell`, `experience_block`

Reason: the API supports only blocks that open a real container menu ("Not yet exposed: menu-less stations"). The hand-worked blocks show the way around it: an add-on-owned slot-less menu (`HandMenu`). REVIEW.md lists the blocks that were weighed for this and left out.

### Not suitable for a scene (no player-facing work)
- **Kinetics:** `shaft`, `powered_shaft`, `cogwheel`, `large_cogwheel`, `andesite_encased_shaft`, `brass_encased_shaft`,
  `andesite_encased_cogwheel`, `brass_encased_cogwheel`, `andesite_encased_large_cogwheel`, `brass_encased_large_cogwheel`, `gearbox`,
  `clutch`, `gearshift`, `adjustable_chain_gearshift`, `encased_chain_drive`, `chain_conveyor`, `speedometer`,
  `stressometer`, `flywheel`, `water_wheel`, `large_water_wheel`, `water_wheel_structure`, `windmill_bearing`, `sail_frame`, `white_sail`,
  `*_sail`, `steam_engine`, `creative_motor`, `turntable`, `gantry_shaft`, `gantry_carriage`, `metal_girder_encased_shaft`
- **Contraptions:** `mechanical_bearing`, `clockwork_bearing`, `mechanical_piston`, `sticky_mechanical_piston`, `mechanical_piston_head`,
  `piston_extension_pole`, `rope_pulley`, `rope`, `pulley_magnet`, `hose_pulley`, `elevator_pulley`, `linear_chassis`,
  `secondary_linear_chassis`, `radial_chassis`, `sticker`, `mechanical_drill`, `mechanical_harvester`, `mechanical_plough`,
  `mechanical_roller`, `portable_storage_interface`, `portable_fluid_interface`, `minecart_anchor`, `controller_rail`
- **Logistics plumbing:** `chute`, `smart_chute`, `andesite_funnel`, `brass_funnel`, `andesite_belt_funnel`, `brass_belt_funnel`,
  `andesite_tunnel`, `brass_tunnel`, `content_observer`, `stockpile_switch`, `fluid_pipe`, `encased_fluid_pipe`, `glass_fluid_pipe`,
  `smart_fluid_pipe`, `fluid_valve`, `mechanical_pump`, `nozzle`
- **Redstone:** `redstone_link`, `redstone_contact`, `pulse_repeater`, `pulse_extender`, `pulse_timer`, `powered_latch`,
  `powered_toggle_latch`, `nixie_tube`, `*_nixie_tube`, `cuckoo_clock`, `mysterious_cuckoo_clock`, `steam_whistle_extension`
- **Trains:** `track`, `fake_track`, `track_signal`, `track_observer`, `small_bogey`, `large_bogey`, `train_door`, `train_trapdoor`,
  `railway_casing`
- **Building, decoration and materials:** `andesite_casing`, `brass_casing`, `copper_casing`, `shadow_steel_casing`,
  `refined_radiance_casing`, `andesite_door`, `brass_door`, `copper_door`, `framed_glass_door`, `framed_glass_trapdoor`, `andesite_ladder`,
  `brass_ladder`, `copper_ladder`, `andesite_scaffolding`, `brass_scaffolding`, `copper_scaffolding`, `andesite_table_cloth`,
  `brass_table_cloth`, `copper_table_cloth`, `*_table_cloth`, `wooden_bracket`, `metal_bracket`, `metal_girder`, `copycat_base`,
  `copycat_step`, `copycat_panel`, `copycat_bars`, `zinc_ore`, `deepslate_zinc_ore`, `raw_zinc_block`, `zinc_block`, `brass_block`,
  `andesite_alloy_block`, `industrial_iron_block`, `weathered_iron_block`, `rose_quartz_block`, `rose_quartz_lamp`, `rose_quartz_tiles`,
  `small_rose_quartz_tiles`, `cardboard_block`, `bound_cardboard_block`, `copper_backtank`, `netherite_backtank`,
  `crushing_wheel_controller`, and every palette stone, window, pane and bar family

## Items
The API is keyed to blocks only. Items that open a menu from the hand (`filter`, `attribute_filter`, `package_filter`,
`linked_controller`, `schedule`) and the `crafting_blueprint` entity cannot attach, and neither can any other item.

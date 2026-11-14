# Create Cinematic Interactions: feasibility check (Step 0)

> **Decision (after this report):** the user chose option 2. The implementation narrows it further: the one mixin targets the
> **vanilla** class `BlockBehaviour.BlockStateBase#getMenuProvider`, not Create's classes, and the lost Create clicks
> (Blocker 3) are handed back to Create through the host's public `sides` callback. See NOTES.md.
> The findings below were re-checked against **Create 6.0.10** sources (the newest public release, the one the add-on builds
> against): apart from line endings, the only change to the files used is a distance check in `SmartBlockEntity.canPlayerUse`.

**Verdict at Step 0: blocked.** The host API accepts the *registration* of a Create block, but at runtime it cannot open
any Create menu. A Create block registered through the API would stop working on a plain right-click.
No feature code was written.

Checked on 2026-11-10 against:

| What | Version | Source |
|---|---|---|
| Cinematic Interactions | **0.18.28** NeoForge 1.21.1 (newest NeoForge 1.21.1 release) | Shipped JAR `Cinematic.Interaction-NeoForge-1.21.1-0.18.28.jar`, disassembled with `javap`, plus `main` source and `docs/addon-api.md` |
| Create | **6.0.11** (`mc1.21.1/dev`, commit `4148fd8`) | GitHub source |
| NeoForge | 1.21.1 branch | `patches/net/minecraft/server/level/ServerPlayer.java.patch` |

Licenses: Cinematic Interactions is MIT. Create is MIT for code (its assets are separately licensed). Nothing was copied or bundled.

---

## 1. Does the API accept Create's blocks?

### Registration: yes
`AddonStations.register` rejects only the `minecraft` namespace, duplicate IDs, and blocks that are already
registered. It does **not** check that the block belongs to the calling mod, so `create:schematic_table` registers
without error. (Confirmed in the 0.18.28 bytecode: its only namespace constant is `"minecraft"`.)

### Runtime: no, for three independent reasons

**Blocker 1: the host cannot find a Create menu.**
Once a block is registered, the host cancels `PlayerInteractEvent.RightClickBlock` on both client and server
(`StationEvents.onRightClickBlock`, `setCanceled(true)`). It then opens the menu itself in `MenuActions.provider`:

```java
return player.level().getBlockState(pos).getMenuProvider(player.level(), pos);   // 0.18.28 bytecode, same as source
```

No Create block overrides `getMenuProvider`, and none extends `BaseEntityBlock`, the vanilla class that supplies
one by default (grep over all of `src/main/java`: zero hits for either). The method therefore returns `null` for every
Create block. Create opens its menus directly from its own `useItemOn`/`useWithoutItem` code instead.

What the player sees: `Sessions.begin` → `MenuActions.open` returns false → `SessionClosePayload`. The window fallback
`Sessions.openWindow` also gets `null` and opens nothing. **Right-clicking the block does nothing.** That makes
registration worse than not registering.

**Blocker 2: Create's client menus need extra data the host never sends.**
Every Create menu opens through NeoForge's extra-data overload:

| Block | Call in Create |
|---|---|
| Schematic Table / Schematicannon / Toolbox | `player.openMenu(be, be::sendToMenu)` |
| Frogport, Postboxes, Redstone Requester, Stock Ticker | `openMenu(provider, blockPos)` |
| Factory Gauge | `openMenu(this, buf -> FactoryPanelPosition.STREAM_CODEC.encode(...))` |

On the client, `MenuBase` builds the menu with `createOnClient(extraData)`, which reads that buffer
(for example, `PackagePortMenu` calls `extraData.readBlockPos()`). The host calls the plain `openMenu(provider)`.
NeoForge then only adds data the provider writes in `IMenuProviderExtension.writeClientSideData`, and Create never
implements that method. The client would build Create's menu from a null or empty buffer and crash.
This only matters if Blocker 1 is solved.

**Blocker 3: the host skips Create's own use logic.**
Because the host cancels the click before the block sees it, logic that runs *before* the menu opens is lost on a
plain right-click:
- Toolbox: dyeing it with a dye in hand.
- Stock ticker: collecting received payments, and the "keeper missing" message.
- Frogport/postbox: copying the address onto a held clipboard, and `onOpenedManually`.
- Factory gauge: panel-connection clicks, and the choice of which of up to 4 panels was hit.
  `getMenuProvider(level, pos)` has no hit information to make that choice.

### Menu-less machines
`docs/addon-api.md` § *Current scope*: "Not yet exposed: menu-less stations". `MenuStation`/`AddonMenuStation` is
the only public base class, and `StationKind.ADDON.usesMenu()` is hard-wired to `true`. A session without an open
menu matching `menuClass` is ended by `Sessions.valid` within 5 ticks. Belts, depots, basins, presses, mixers,
deployers and the like **cannot** be given a scene through this API.

### How this was tested
Statically: the shipped 0.18.28 JAR was disassembled and the code paths traced end to end through the host, Create and
NeoForge. No game client was launched. The failure path needs no judgment calls (a `null` provider leads to a closed
session and no window), so an in-game run would only show the same "nothing happens on right-click".

---

## 2. Sort of every Create block and item

IDs come from `AllBlocks.java` / `AllItems.java` on the commit above. Dyed families are written `*_x` (16 colours).
Palette stone types, windows, panes, bars and the other decorative families generated by loops are grouped as one row.

### (a) Opens a real menu

| Block ID(s) | Menu class | API-attachable today? |
|---|---|---|
| `schematic_table` | `SchematicTableMenu` | No (Blockers 1+2) |
| `schematicannon` | `SchematicannonMenu` | No (1+2) |
| `*_toolbox` (16) | `ToolboxMenu` | No (1+2+3) |
| `package_frogport` | `PackagePortMenu` | No (1+2+3) |
| `*_postbox` (16) | `PackagePortMenu` | No (1+2+3) |
| `redstone_requester` | `RedstoneRequesterMenu` | No (1+2) |
| `stock_ticker` | `StockKeeperCategoryMenu` (needs a seated keeper) | No (1+2+3) |
| `factory_gauge` | `FactoryPanelSetItemMenu` (only with an empty filter and empty hand; per panel) | No (1+2+3, and no single menu per block) |

Items that open a real menu from the hand: `filter`, `attribute_filter`, `package_filter`, `linked_controller`, `schedule`.
`crafting_blueprint` opens a menu from the *entity* it places. The API is keyed to blocks only, so none of these can attach.

Contraption-mounted storage (`MountedItemStorage`) opens chest-like menus from a moving contraption entity, not a block. Not attachable.

### (b) Menu-less but scene-worthy (has a hands-on right-click use or a screen without a menu)

Processing: `depot`, `weighted_ejector`, `basin`, `blaze_burner`, `lit_blaze_burner`, `mechanical_press`, `mechanical_mixer`,
`deployer`, `mechanical_crafter`, `mechanical_saw`, `millstone`, `crushing_wheel`, `encased_fan`, `spout`, `item_drain`, `belt`,
`mechanical_arm`, `packager`, `repackager`, `item_vault`, `item_hatch`, `fluid_tank`, `creative_crate`, `creative_fluid_tank`.

Screen-only (client screen, no container menu): `display_link`, `sequenced_gearshift`, `track_station`, `elevator_contact`,
`clipboard`, `stock_link`, `cart_assembler` (mode via value box), `display_board`.

Hand-operated: `hand_crank`, `copper_valve_handle`, `*_valve_handle`, `desk_bell`, `lectern_controller`, `controls`,
`contraption_controls`, `placard`, `*_seat`, `steam_whistle`, `haunted_bell`, `peculiar_bell`, `experience_block`.

Items with a screen but no menu: `schematic_and_quill`, `schematic`, `wand_of_symmetry`, `handheld_worldshaper`, `shopping_list`.

All of (b): **not attachable** (no menu-less API).

### (c) Not suitable for a scene

Kinetics and transmission: `shaft`, `powered_shaft`, `cogwheel`, `large_cogwheel`, `*_encased_*`, `gearbox`, `vertical_gearbox`, `clutch`,
`gearshift`, `adjustable_chain_gearshift`, `encased_chain_drive`, `chain_conveyor`, `rotation_speed_controller`, `speedometer`, `stressometer`,
`flywheel`, `water_wheel`, `large_water_wheel`, `water_wheel_structure`, `windmill_bearing`, `sail_frame`, `white_sail`, `*_sail`,
`steam_engine`, `creative_motor`, `turntable`, `gantry_shaft`, `gantry_carriage`, `metal_girder_encased_shaft`.

Contraptions: `mechanical_bearing`, `clockwork_bearing`, `mechanical_piston`, `sticky_mechanical_piston`, `mechanical_piston_head`,
`piston_extension_pole`, `rope_pulley`, `rope`, `pulley_magnet`, `hose_pulley`, `elevator_pulley`, `linear_chassis`,
`secondary_linear_chassis`, `radial_chassis`, `sticker`, `mechanical_drill`, `mechanical_harvester`, `mechanical_plough`,
`mechanical_roller`, `portable_storage_interface`, `portable_fluid_interface`, `minecart_anchor`, `controller_rail`.

Logistics plumbing: `chute`, `smart_chute`, `andesite_funnel`, `brass_funnel`, `andesite_belt_funnel`, `brass_belt_funnel`,
`andesite_tunnel`, `brass_tunnel`, `content_observer`, `stockpile_switch`, `fluid_pipe`, `encased_fluid_pipe`, `glass_fluid_pipe`,
`smart_fluid_pipe`, `fluid_valve`, `mechanical_pump`, `nozzle`.

Redstone: `redstone_link`, `redstone_contact`, `analog_lever`, `pulse_repeater`, `pulse_extender`, `pulse_timer`, `powered_latch`,
`powered_toggle_latch`, `nixie_tube`, `*_nixie_tube`, `cuckoo_clock`, `mysterious_cuckoo_clock`, `steam_whistle_extension`.

Trains: `track`, `fake_track`, `track_signal`, `track_observer`, `small_bogey`, `large_bogey`, `train_door`, `train_trapdoor`, `railway_casing`.

Decorative, building and materials: all casings, `*_door`, `framed_glass_*`, ladders, scaffolding, `*_table_cloth`, brackets,
`metal_girder`, `copycat_*`, ores, storage blocks (`zinc_block`, `brass_block`, `andesite_alloy_block`, ...), rose quartz
blocks/lamp/tiles, `cardboard_block`, `bound_cardboard_block`, `copper_backtank`, `netherite_backtank`, `crushing_wheel_controller`, and
every palette stone, window and pane family.

Items: all ingredients, sheets, nuggets, food, armour and tools (`wrench`, `goggles`, `extendo_grip`, `potato_cannon`, ...) and every remaining
item. No block or menu to attach to.

---

## 3. Options

Each option is limited by the rules: public host API only, no mixins into the host, no forking, and neither mod modified or bundled.

1. **Ask upstream for an opener hook (recommended).** File an issue on `dcvke/CinematicInteractions` asking for an
   optional opener on `AddonStations.Definition`, e.g. `(ServerPlayer, BlockPos, BlockHitResult) -> OptionalInt`, used
   in place of `getMenuProvider` when present. Our add-on could then call Create's own path
   (`openMenu(be, be::sendToMenu)`), which fixes Blockers 1 and 2 with no Create changes. A "run the block's own use
   first" flag would fix Blocker 3. Menu-less support would be a second, larger request. This stays within every rule;
   the cost is waiting on the maintainer. I can draft the issue.
2. **Mixin into Create (not the host).** Add `getMenuProvider` to the menu blocks in (a), returning a wrapper that
   implements `writeClientSideData` with the same bytes Create sends. This would work today for 37 blocks:
   schematic table, schematicannon, 16 toolboxes, frogport, 16 postboxes, redstone requester and stock ticker; the factory
   gauge stays out. It conflicts with "don't modify either mod", because it changes Create's behaviour at runtime. It
   breaks on Create updates, still loses the Blocker 3 behaviours on a plain right-click, and covers none of the
   menu-less machines. Only with your explicit approval.
3. **Ship our own Create-themed blocks.** This is the model the API documents (an add-on registers its own block and
   menu), e.g. a "cinematic schematic desk". It is fully supported, but these would not be Create's blocks.
4. **Park it.** Revisit when the host's preview API adds openers or menu-less stations (both are listed as future work).

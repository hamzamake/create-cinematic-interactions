# Create Cinematic Interactions

A Forge 1.20.1 add-on (port of the NeoForge 1.21.1 version) that gives Create's menu-backed workstations first-person scenes in
[Cinematic Interactions](https://github.com/dcvke/CinematicInteractions). Right-click a toolbox, postbox, frogport,
schematic table, schematicannon or redstone requester and the camera leans in. Your hands work the block's real slots.

Mod id `createcinematic`, MIT. Required on **both** client and server.

## Versions built against
| | Version | Declared range |
|---|---|---|
| Minecraft | 1.20.1 | `[1.20.1]` |
| Forge | 47.4.28 | `[47.1.3,)` |
| Create | 6.0.8-291 (newest 1.20.1 build) | `[6.0.8,6.1.0)` |
| Cinematic Interactions | 0.18.30 (Forge 1.20.1) | `[0.18.30,)` |
| Java | 17 | |

## Build
```
./gradlew build          # JDK 17+ on JAVA_HOME; output: build/libs/createcinematic-1.0.0+1.20.1.jar (SRG, for real Forge)
```
Create and its libraries come from `maven.createmod.net`. The Cinematic Interactions jar comes from `libs/local/` (a hand-made maven layout, so it is remapped).
Neither is bundled in the output jar.

To play, put this jar, Create 6.0.8+ and Cinematic Interactions 0.18.30+ in `mods/` on the client and the server.

Dev runs: `./gradlew runServer`, and `./gradlew runClient` (add `-PtestServer` to join that server straight away). Create and
Cinematic Interactions are on the dev classpath. A real (non-dev) server needs `eula=true` in `eula.txt`;
setting that means you accept Minecraft's EULA.

## Supported blocks (58)
| Block | Worked from | What the scene does |
|---|---|---|
| 16 toolboxes | front | 8 compartments in Create's ring order; take and put; empty-hand click on an empty reserved compartment frees it |
| Schematic table | front | Blank and written slots; scroll or click to pick a schematic; Refresh; Write starts Create's own upload, with progress |
| Schematicannon | any side | Schematic, book, list and gunpowder slots; Play / Pause / Stop via Create's own packet; status, fuel and progress |
| Frogport, 16 postboxes | any side / front | 2x9 package panel in front of the block; read-only address plate |
| Redstone requester | any side | 9 ghost slots: click with an item to request it, empty hand to clear |
| Hand crank | the side it faces | Crank it with the mouse: grab the handle and circle; gauge for RPM and overstress |
| 17 valve handles | the side they face | Twist the wheel one step at a time; scroll sets the step angle |
| Analog lever | front, or beside its swing | Drag it notch by notch, 0 to 15 |
| Desk bell | the side it faces | Tap the plunger |
| Rotation speed controller | the sides off its shaft | A cutaway CVT: drag the dial or scroll to set the RPM, reverse with the red handle |

Controls: click takes or puts, right-click handles half, and shift-click sends the stack to your inventory.
**Sneak + use with an empty hand** opens Create's normal window. That is where addresses, amounts and allow-partial are edited.
The hand-worked blocks (crank, valves, lever, bell, speed controller) have no window: sneak + use is Create's classic click.

The full per-block list, including every skipped block and why, is in [COVERAGE.md](COVERAGE.md). How it works and why
is in [FEASIBILITY.md](FEASIBILITY.md) and [NOTES.md](NOTES.md).

## How it works, briefly
- **Opening Create's menus.** The host opens a station's menu through vanilla `getMenuProvider`, which Create's blocks don't
  implement. One mixin, into the vanilla `BlockStateBase#getMenuProvider` (not into Create, not into the host), hands it a
  wrapper around Create's own block entity. The wrapper writes Create's extra menu data through NeoForge's
  `writeClientSideData`.
- **Create's own clicks still work.** These include a dye on a toolbox, goggles on a frogport, a clipboard on a port, funnels,
  glue, display links and other mods' wrenches. The add-on answers the host's public `sides` callback with "none" for that
  one click, so Create handles it.
- **The server stays in charge.** The block's menu decides every item move; the scene only sends real menu clicks or
  Create's own packets.

## Limitations
- **Partly tested in game.** See TEST-RESULTS.md for what was and wasn't covered.
- **No scenes for menu-less machines** (belts, depots, basins, press, mixer, deployer, ...). The host API doesn't support
  menu-less stations yet, and mixing into the host was ruled out. The stock ticker and factory gauge are skipped too
  (see COVERAGE.md).
- **Text and numbers are edited only in Create's window**: addresses, requester amounts, allow-partial.
- **Other players don't see the slot contents.** The host's API does not publish add-on slot contents or effects to other
  players yet.
- **The scenes use public host classes outside the `api` package** (`Scene`, `Picking`, `Anim`, `ItemPlacer`, ...). The
  host's add-on API is a preview, so a future host release could break the add-on.
- **Spectators** right-clicking a station see vanilla's "can't open" message (NOTES.md says why).

## Testing status
Verified:
- **Forge 1.20.1 port:** `./gradlew build` passes against Create 6.0.8-291 and Cinematic Interactions 0.18.30 (Forge). Not yet run in game; the rest of this list is the NeoForge version's.
- The host's add-on API signatures are identical in 0.18.12, 0.18.28 and 0.18.30 (javap diff).
- Byte-level parity of the menu extra data with Create's own open calls, traced in NeoForge 21.1.255 bytecode.
- Dedicated-server startup (`./gradlew runServer`, dev environment): boots to "Done" with Create 6.0.10, Cinematic Interactions
  0.18.12 and this add-on loaded, the required mixin applied and common setup run, with no errors from this mod. Nobody joined it.
- Three review rounds; see "Bugs found and fixed".

In-game run (dev server plus a networked dev client, 2026-11-11): all six scene kinds, item moves, Create's own clicks,
the sneak window, schematic upload (including double-click Write), a hard disconnect mid-scene, block removal and damage
mid-scene, deployers and spectators. Full table: [TEST-RESULTS.md](TEST-RESULTS.md).

Not verified: mod-mismatch connections, two players at once, animation feel at full speed. TEST-RESULTS.md lists the rest.

## Manual in-game test checklist
Use singleplayer, then repeat the starred items on a dedicated server with a second client.

1. ★ Game starts with Create, Cinematic Interactions and this add-on; no errors mentioning `createcinematic` in the log.
2. ★ Toolbox, plain right-click from the front: camera leans in, no snap. Put an item into a compartment, take it back,
   right-click to take half, shift-click to the inventory. Check the counts against Create's window (sneak + use, empty hand).
3. Toolbox: dye of another colour in hand → it recolours (no scene). Click from the side or back → the camera walks you to
   the front.
4. ★ Schematic table: blank schematic in hand walks into the scene in your hand. Put it in, scroll through schematics, press
   Write, watch the progress bar, take the written schematic. Double-click Write quickly: only one upload, blank not lost.
5. ★ Schematicannon: insert a schematic, gunpowder and a book; Play / Pause / Stop change the cannon's state; the status
   text updates.
6. ★ Frogport and a postbox: take and put packages in the 2x9 tray. Sneak + use with an empty hand opens Create's window and
   the address can be edited. Clipboard in hand copies the address (no scene). Goggles on the frogport put them on.
7. ★ Redstone requester: click with an item sets a ghost slot; empty hand clears it. After leaving, sneak + use shows the
   same items in Create's window, with amounts kept.
8. ★ Clean exits during a scene: walk away, take damage (e.g. lava nearby), have a second player break the block, `/kill`,
   and disconnect. In each case the camera returns, controls work, no item is lost or duplicated, and no window is left
   invisible.
9. A deployer using an empty hand or an item on a toolbox, postbox or requester: no crash.
10. ★ A client without the add-on joins a server with it, and the reverse: the connection is refused with a
    missing-channel message, not a broken game.
11. Hover outlines: every slot and button is pickable, and wide buttons don't steal clicks from neighbouring slots.

## Bugs found and fixed
Reviewers were separate agents from the builders. Severity: high = crash/desync/stuck/dupe, medium = wrong behaviour a player hits.

| Round | ID | Severity | Problem | Fix |
|---|---|---|---|---|
| 1 | R1-1 | high | A deployer (fake player) clicking a station crashed the server: the host queries the fake player's nonexistent network channel | Fake players always get Create's own behaviour |
| 1 | R1-2 | medium | Sneak + use did nothing on ports, requester and toolbox (Create ignores crouching players), so addresses and amounts were unreachable | Sneak + use with an empty hand opens Create's window on all 37 blocks |
| 1 | R1-3 | medium | A client/server mod mismatch left stations dead or flashing open and shut | A required network channel makes NeoForge refuse the mismatch |
| 1 | R2-1 | medium | A double-click on Write could restart the upload, cancel it and destroy the blank schematic | 1.5 s client latch on all upload actions |
| 1 | R1-4 | low | Wrench, clipboard and linked-item rules were broader than Create's own, so the window opened instead of the scene | Rules narrowed to what Create actually does |
| 1 | R1-6 | low | NeoForge floor 21.1.255 was stricter than needed | `[21.1.200,)` (the API needed arrived in 21.1.151) |
| 1 | R2-2 | low | Circular hit areas let wide buttons steal clicks from slot corners | Projected-rectangle hit test |
| 1 | R2-3 | low | Shift-click animated a move even when nothing moved | Checks the predicted result and refuses with a shake |
| 1 | R1-5 | low | Spectators see a misleading "can't open" message | Not fixed on purpose (NOTES.md) |
| 2 | S2-1 | (medium) | Reported: sneak + use during a scene's outro opens an invisible menu | False positive, retracted in round 3: the host already blocks every use click while a scene is active. A redundant guard added for it was removed |
| 2 | S2-2 | low | Sneak window ignored protection mods that deny block use | Respects `useBlock == FALSE` |
| 2 | R3-1 | low | Hints didn't say an empty hand is needed | Wording fixed |
| 2 | R3-2 | low | "Uploading" status lingered for 1.5 s | Display uses the server's flag; the latch only guards |
| 2 | S2-3, S2-4 | low | Off-hand use is swallowed by the sneak window; clients without the add-on are refused | Accepted, documented in NOTES.md |

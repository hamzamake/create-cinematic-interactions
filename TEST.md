# In-game test plan

Nobody has run this add-on in a game client yet. Everything below is untested. Tick each box and note anything that looks or feels wrong.
**Bold** marks the tests most likely to find real problems.

## 0. Setup

### Jars you need
| Mod | Version | Where |
|---|---|---|
| Forge | 47.1.3 or newer (47.4.28 used to build) | minecraftforge.net |
| Create | 6.0.8 (1.20.1) | CurseForge / Modrinth |
| Cinematic Interactions | 0.18.30 or newer, Forge 1.20.1 (`libs/local/cinematic/`) | GitHub releases / CurseForge |
| This add-on | `build/libs/createcinematic-1.0.0+1.20.1.jar` | this project, `./gradlew build` |

Put all three mod jars in `mods/` of a Forge 1.20.1 instance, on the client and on the server.

Quick alternative for singleplayer: `./gradlew runClient` from this folder. Create and the host are already on the dev classpath.

### Test world
Use a creative world with cheats on. Give yourself the blocks and items:
```
/give @s create:white_toolbox
/give @s create:red_toolbox
/give @s create:schematic_table
/give @s create:schematicannon
/give @s create:package_frogport
/give @s create:white_postbox
/give @s create:redstone_requester
/give @s create:empty_schematic 4
/give @s create:schematic_and_quill
/give @s create:wrench
/give @s create:clipboard
/give @s create:goggles
/give @s minecraft:blue_dye
/give @s minecraft:gunpowder 16
/give @s minecraft:book 4
/give @s create:deployer
/give @s create:creative_motor
/give @s create:hand_crank 3
/give @s create:speedometer
/give @s create:extendo_grip
/give @s create:copper_valve_handle 2
/give @s create:red_valve_handle
/give @s create:analog_lever 3
/give @s create:desk_bell 2
/give @s create:rotation_speed_controller
/give @s create:large_cogwheel
```
- **Packages** for the port tests come from a Packager. Put a chest behind a `create:packager` and power it with redstone.
- **A schematic file** for the table tests: use the Schematic and Quill to select a small build and save it. It goes into `schematics/` in the game folder.

Keep the log open: `logs/latest.log`, or the console with `runClient`.

---

## 1. Startup
- [ ] **Game reaches the title screen with all three mods listed in Mods.**
- [ ] Log: no `ERROR` or exception mentioning `createcinematic`, `CreateMenuProvider`, `BlockStateBaseMixin` or `Stations`.
- [ ] A world loads without errors.

## 2. Every block, the basic scene
For each block, right-click it with an **empty hand, not sneaking**.

Expected:
- the camera eases in with no snap or jump;
- two hands appear;
- slots or buttons are laid out on the block;
- a hint line shows at the bottom;
- hovering a spot gives an outline and a tooltip;
- walking away or pressing the host's leave key eases the camera back out.

| Block | Worked from | Scene starts | Smooth in/out | Spots sit on the block, not floating oddly or inside it |
|---|---|---|---|---|
| Toolbox (white) | front | [ ] | [ ] | [ ] |
| Toolbox (another colour) | front | [ ] | [ ] | [ ] |
| Schematic table | front | [ ] | [ ] | [ ] |
| Schematicannon | any side | [ ] | [ ] | [ ] |
| **Frogport** (its tray placement is the least certain) | any side | [ ] | [ ] | [ ] |
| Postbox | front | [ ] | [ ] | [ ] |
| Redstone requester | any side | [ ] | [ ] | [ ] |

- [ ] Click a front-only block (toolbox, table, postbox) from its side or back: you're walked round to the front.
- [ ] Hover every single slot and button once. Each can be highlighted, and none is unreachable or highlights its neighbour.
- [ ] Wide buttons (the table's schematic name strip, Write, Refresh) don't steal clicks from the slots next to them.

## 3. Items move correctly (the server decides)
Do these in each block that has normal slots (toolbox, table, cannon, ports):
- [ ] Click a filled slot with an empty cursor: the item comes into your hand.
- [ ] Click an empty slot while holding an item: it goes in.
- [ ] Right-click: takes or puts half.
- [ ] **Shift-click a filled slot: the stack flies to your inventory** with an animation.
- [ ] **Shift-click with a full inventory: nothing moves, the block shakes "no", and no fake animation plays.**
- [ ] Try to put an item a slot doesn't accept (e.g. dirt into a cannon's gunpowder slot): red outline, a "won't fit" tooltip, and nothing moves.
- [ ] Leave the scene, then sneak + right-click with an empty hand to open Create's window. **Its contents match what you saw in the scene.**
- [ ] No item is ever lost or duplicated. Count before and after.

## 4. Per-block details

### Toolbox
- [ ] The 8 compartments are laid out in the same ring order as Create's window.
- [ ] A compartment holding more than one stack shows the total count.
- [ ] An empty compartment that has kept its "reserved" item shows it faded. Clicking it with an empty hand frees it.
- [ ] **Holding blue dye, right-click a white toolbox: it turns blue and no scene starts** (Create's own behaviour).
- [ ] Holding dye of the same colour as the toolbox: the scene starts as normal.

### Schematic table
- [ ] Walk up holding a blank schematic: it starts in your hand, ready to place.
- [ ] Put a blank schematic in the left slot.
- [ ] Scroll, or click the name strip (right-click goes back), to cycle your saved schematics. The name slides in.
- [ ] Refresh: a schematic saved meanwhile appears in the list.
- [ ] Write: the progress bar fills and the status line shows "Uploading ... %". The written schematic appears in the right slot.
- [ ] **Double-click Write fast, ideally on a server with some lag: only one upload, and the blank schematic is not lost.**
- [ ] Write with no blank in place: refused, and the tooltip says why.
- [ ] Write while the output slot is full: refused ("take it out first").
- [ ] With no saved schematics (empty `schematics/` folder): the strip says so, and there's no crash.

### Schematicannon
- [ ] Slots: schematic in, schematic out, book in, material list out, gunpowder.
- [ ] Gunpowder in: the fuel bar fills.
- [ ] With a written schematic in, **Play** starts the cannon, **Pause** pauses it and **Stop** stops it. The status text updates.
- [ ] Buttons that don't apply right now are greyed out and refuse clicks.
- [ ] Book in: material list printing progress shows.

### Frogport and postbox
- [ ] The 2x9 tray is readable and every slot is pickable (frogport especially).
- [ ] The address plate shows the port's address, read-only.
- [ ] **Sneak + right-click, empty hand: Create's window opens and the address can be edited and saved.**
- [ ] **Holding a clipboard: right-click copies the address onto it, with no scene.**
- [ ] **Holding goggles, right-click the frogport: the frog wears goggles, with no scene.**

### Redstone requester
- [ ] Click a ghost slot holding an item: that item appears in it, and nothing leaves your hand.
- [ ] Click a ghost slot with an empty hand: it clears.
- [ ] Leave, then sneak + use with an empty hand: Create's window shows the same items, and **amounts set earlier are kept**.
- [ ] On a protected logistics network (another player's): you get Create's "protected" message, and no scene or stuck state.

## 5. Create's own clicks still work (no scene)
- [ ] Create **wrench**, not sneaking, on any station: the scene starts (Create would open its menu here too).
- [ ] Create wrench, **sneaking**, on a postbox, frogport or requester: the block is picked up.
- [ ] Another mod's wrench (if installed) on a postbox, frogport or requester: it rotates or wrenches the block.
- [ ] Place a funnel, super glue or a display link against a station: it places or links, with no scene.
- [ ] Sneak + right-click while holding a block: the block is placed against the station, as in vanilla.
- [ ] Sneak + right-click with an empty main hand but a torch in the off hand: Create's window opens and the torch is not placed. This is expected (see NOTES.md).

## 6. Clean exits (do each during a scene)
After each: the camera comes back smoothly, mouse and controls work, no window is left open invisibly, and no item is lost or duplicated.
- [ ] Walk or move away.
- [ ] **Take damage** (have a skeleton shoot you, or `/damage @s 2` from a command block).
- [ ] **A second player breaks the block.** Or replace it with `/setblock ~ ~ ~ air` from a command block.
- [ ] `/kill @s`
- [ ] **Disconnect** mid-scene and reconnect. You can move and use blocks normally.
- [ ] A second player dyes the toolbox you're working at.
- [ ] Shift-click something and leave immediately, mid-animation.
- [ ] Holding an item in the cursor (picked up in the scene), leave: it goes back into your inventory.

## 7. Automation and other players
- [ ] **Deployer test (this was a server crash, now fixed).** Place a deployer powered by a creative motor. Put a toolbox, then a postbox, then a requester two blocks in front of it (one gap). Try the deployer empty-handed and holding an item. **The server must not crash.** Create's normal deployer behaviour applies.
- [ ] Spectator mode, right-click a station: vanilla's "can't open" message appears. That's known and accepted; there is no crash.
- [ ] A second player watching you work: you appear at the block. They don't see the slot contents move; that is a host API limit, not a bug.

## 8. Dedicated server
Run a real Forge dedicated server with all three jars. It needs `eula=true` in `eula.txt`, which you accept yourself.
- [ ] **The server starts with no errors mentioning `createcinematic`.**
- [ ] **Repeat sections 2, 3, 6 and 7 with a client connected.** That covers the scenes, item moves, clean exits and the deployer.
- [ ] Two players use the same toolbox one after the other, and at the same time: there is no item duplication.
- [ ] **A client without this add-on joins:** refused with a "missing channel" style message, not a broken game.
- [ ] **A client with this add-on joins a server without it:** refused the same way.

## 8b. Hand crank (mouse cranking)
Place three cranks: one on a wall, one on top of a block (floor), one under a block (ceiling). Put a shaft and a speedometer behind one of them.
- [ ] **Right-click each crank: a scene opens and stays open** (it doesn't close by itself after a moment).
- [ ] **Hold left click on the handle and circle the mouse: the crank turns, the hand stays on the grip**, and the speedometer shows 32 RPM.
- [ ] **Circle the other way: it turns the other way.** The handle's direction on screen matches your mouse, on the wall, floor and ceiling cranks.
- [ ] Stop the mouse while still holding: it coasts to a stop within about half a second.
- [ ] Release the button: the hand lets go. Grab it again: it works again.
- [ ] Survival: cranking costs hunger at about the same rate as holding right-click. With an extendo grip in the main hand it costs none.
- [ ] Sneak + right-click (no scene): Create's own backwards crank. Wrench click: Create rotates it; sneak + wrench: picks it up.
- [ ] A deployer using the crank still turns it.
- [ ] Break the crank, or walk away during a scene: the scene ends cleanly.
- [ ] Ceiling crank: the camera can see it, and you can reach the handle.
- [ ] **A crank too high for a scene** (on a ceiling 3+ blocks up): each right-click turns it once (about half a second), with no blank screen flashing.

- [ ] Crank gauge: while cranking, the line under the hotbar shows 32 RPM and the direction; on an overstressed network it says so in red.

## 8c. Valve handles, analog lever, desk bell, speed controller
**Valve handle** (put a shaft and something to turn behind it, e.g. a mechanical bearing):
- [ ] **Grab the wheel and twist: it turns once per twist, the way your mouse went.** Twist the other way: it turns back.
- [ ] Scroll: the step angle label changes (15° per notch, Shift: 1°). The next twist turns by that angle.
- [ ] Twist again while it is still turning: the hand shakes (busy), nothing breaks.
- [ ] Dye in hand recolours it (no scene); a red valve works the same as copper.
**Analog lever** (on a wall, on a floor, under a ceiling; a redstone lamp or comparator next to it):
- [ ] **Drag the lever: the number follows your mouse 0 to 15, the lever moves with your hand**, and the signal matches when you let go.
- [ ] Sneak + right-click: Create's own one notch down (no scene).
**Desk bell:**
- [ ] Click: the hand presses the plunger, it dings once, a redstone pulse comes out. Clicking fast doesn't double-ring.
**Rotation speed controller** (a powered shaft through it, a large cogwheel on top driving something with a speedometer):
- [ ] **The near side panel is never visible in the scene**, only the back plate, frame, pulleys and belt.
- [ ] **Drag the dial: the RPM follows, the cones slide smoothly (input and output opposite ways), and the speedometer follows after you let go.**
- [ ] Scroll sets it by 1 RPM (Shift: 10); you can reach any value, e.g. 37.
- [ ] Change the input speed: IN changes, OUT and the cog on top hold.
- [ ] **Drive it from the cog on top instead** (shaft unpowered): the IN and OUT tags swap pulleys, IN reads the cog, the shaft comes out at the target.
- [ ] The pulleys, belt and dial look solid 3D from both usable sides, and the parts turn the same way as the real shaft ends.
- [ ] The panel stays hidden during the walk-up and the walk-away too; the box folds back flat on leaving, with no flicker.
- [ ] In a one-block corridor (wall two blocks out from the face) the scene still opens. With a block to your right, the dial moves to the left.
- [ ] Click the red handle: the output reverses.
- [ ] Remove the input power: it still lets you set a target and says it will hold it once powered. Remove the cog on top: it says nothing is driven.
- [ ] Adventure mode: nothing changes (Create's own panel refuses adventure players too).
- [ ] Placing a large cogwheel on top while holding it still works (no scene).

## 9. Versions (optional)
- [ ] Swap in Cinematic Interactions 0.18.30 (from your AE2 folder): everything above still works.

---

## Reporting a problem
For each issue, send:
1. Section and line, e.g. "4. Schematic table, double-click Write".
2. What happened vs what you expected.
3. Singleplayer or dedicated server.
4. Any `ERROR` or exception lines from `logs/latest.log`, and a screenshot for layout problems.

Layout problems are expected on the first run: slots slightly off the block, the camera too close, the tray too high. Every position was set from Create's block shapes but never seen in game. These are quick number tweaks.

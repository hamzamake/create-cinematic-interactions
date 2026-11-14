# Notes: decisions and judgment calls

Unclear points were settled with the options below instead of stopping. Each entry says what was picked and why.

## Approach (user picked option 2 from FEASIBILITY.md)
- **One mixin, into vanilla, not Create.** Option 2 was "mixin into Create". The add-on instead injects at the head of
  `BlockBehaviour.BlockStateBase#getMenuProvider(Level, BlockPos)`. For the 37 supported Create blocks, on the server only, it returns
  `CreateMenuProvider`: a wrapper around Create's own block entity provider. No Create or host bytecode is touched, and
  Create updates that keep the block entity and menu classes keep working.
  - Side effect: any other mod that calls `getMenuProvider` on those blocks now gets Create's real menu. That menu opens with
    the correct extra data and the same gates Create's own use code applies.
- **Forge 1.20.1 port:** plain `ServerPlayer.openMenu(provider)` sends no extra data here, so `ServerPlayerMixin` (vanilla) opens our
  `MenuData` providers through `NetworkHooks.openScreen` with the same bytes. The NeoForge notes below still describe the data itself.
- **Extra data without touching the host.** NeoForge's plain `ServerPlayer.openMenu(provider)` appends
  `provider.writeClientSideData(menu, buf)`. The wrapper writes there exactly what Create's own `openMenu(provider, writer)`
  call writes: `sendToMenu(buf)` or `writeBlockPos(pos)`.
- **Create's own clicks (wrench, clipboard, linked items, toolbox dye, frogport goggles).** The host cancels the right-click on a
  registered block before Create sees it. The add-on's `sides` callback, part of the public `AddonStations.Definition`, returns
  "no sides" while such a click is being dispatched. The host then treats the block as unusable for that one click and leaves
  the event alone, so Create's normal logic runs.
  - The flag is set at `HIGHEST` priority and cleared at `LOWEST` (receiving cancelled events). It is decided only from the held
    item and the block state, so client and server agree.
- **Scenes use public host classes outside the `api` package**: `MenuStation`, `ClientStation`, `Anim`, `CameraRig.Pose`,
  `ItemPlacer`, `Picking`, `Scene`. The API guide points add-ons at the built-in stations, and the user's AE2 add-on uses the same
  classes. They may change without notice; the add-on API is a preview anyway.

## Hand-worked blocks (menu-less): crank, valves, lever, bell, speed controller
- **One `HandMenu` for all five.** Each block's action is a button; the server dispatches on the block entity and runs Create's own code
  (see CLAUDE.md, point 4). The value-panel settings (valve angle, controller RPM) go through the behaviour's own `setValueSettings`, the
  method Create's `ValueSettingsPacket` calls. Its gates are kept as `player.mayBuild()` (no adventure) and vanilla's reach of 4 (Create allows 20).
- **The scene replaces Create's value panel for the valve and the controller.** The host takes the right-click, so the panel can't open;
  the valve's step angle moved into the scene (mouse wheel) and the controller's RPM is set on the cutaway's dial.
- **Analog lever:** a drag sends the target notch; the server steps `changeState` there one at a time (Create's only setter) and plays one click.
- **Desk bell:** the server calls the bell's own `use`, and the client calls it too (`echo`): Create plays the ding to everyone
  but the ringer on the server, and to the ringer on the client.
- **The desk bell is in Create 6.0.10.** It is registered in `AllBlocks` (`DESK_BELL`) and ships its model, sound and loot table (checked 2026-11-11).
- **Speed controller sides:** the two faces off the shaft axis, where Create's own value box is.
- **Mouse button state comes from GLFW.** `MouseHandler#isLeftPressed` only updates while no screen is open, and a scene is a screen,
  so scenes read the window's button (`CreateStation.leftHeld`) to drop a grab whose release was lost.
- **Valve angle is not changed mid-turn**, as Create's panel is hidden then (`showValue`). The scene waits for the wheel to stop before sending it.
- **Spectators** right-clicking a hand block see vanilla's "can't open" message, as on the menu blocks (see Spectators below).
- **Absurd configs:** `maxRotationSpeed` is read clamped to 2^18 in the controller scene, which keeps the sent RPM sane.
- **1.20.1 value message:** `ServerboundContainerButtonClickPacket` writes the button id as one byte here (1.21: VarInt), so values
  travel as `HandMenu.SetValue` on the add-on's channel. Singleplayer hides the difference: memory connections don't serialise packets.
- **Controller scene is a stylised CVT, not Create's real mechanism.** Create's controller has no belt or pulleys; it just sets the cog on top to the target.
  The pulley radii follow target/input, clamped to 1/3..3, so the picture stays sane past that while the readouts stay exact.
  A drag of the dial previews live and sends one value on release, because every set rebuilds the kinetic network.
- **Controller scene works both ways.** It follows Create's own rule (`getDesiredOutputSpeed`): when the large cog on top is the source, the cog is
  the input and the shaft line comes out at the target, so the IN and OUT tags swap pulleys.
- **The controller scene is real 3D.** It uses procedural meshes (`entityCutoutNoCull` on vanilla's white texture, world-lit) in an open brass box standing out of
  the near face, which closes off Create's wall and value box from every angle. It is drawn from the first intro frame through the outro, which
  no other scene does. Create's shaft partial isn't used: it is too thick for the pulleys' spindles.
  - **Camera:** 1.45 out from the block centre with a 78° FOV. Further back, a solid block two cells out made the host refuse the scene.
  - **Dial side:** the dial floats on the right of the box, or on the left when a block stands to the right of where you stand.
  - **Esc mid-drag cancels the drag**; a scrolled or knob value still waiting to be sent goes out as the scene closes.

## Hand crank (menu-less)
- **Our own slot-less menu, not a new packet.** The host ends a session within ticks unless a menu of the station's class is open, so
  `HandMenu` exists only to be that menu. Turns ride on vanilla's `ServerboundContainerButtonClickPacket`. Vanilla already checks
  the container id, spectators and `stillValid` (reach + block) before `clickMenuButton`, so the add-on adds no channel and no validation code of its own.
- **Server does exactly Create's use code:** `HandCrankBlockEntity#turn`, then hunger (skipped with an extendo grip in the main hand,
  where Create checks the used item), then the hunger advancement. The rate limit is one turn per 3 ticks per direction, so a modified
  client can't spend hunger or drive the crank faster than holding right-click does (every 4 ticks).
- **Mouse speed can't change RPM.** Create's crank is a fixed 32 RPM while driven. The mouse decides on/off and direction only.
- **Sneak + use stays Create's backwards crank**, and wrenches stay Create's rotate/pick-up. The crank has no window, so `sneakWindow` skips it.
- **Placeholder screen = Create's plain click.** The host takes the menu from its screen opening, so the menu type needs a screen.
  When the host refuses a scene (a crank above its 1.85 reach height, which includes most ceiling cranks, or a blocked path), it opens "the block's
  window" instead. That screen draws nothing, does Create's plain click (one turn, a notch, a ding) and closes on its first tick.
  Known limit: opening a screen releases the use key, so a held right-click doesn't repeat there; it's one 10-tick turn per click.
  Wall and ceiling cranks report `workOnTop() = false`, so the host allows them up to 1.85 above the feet instead of 1.42.

## Versions
- **Create 6.0.10-280**, not 6.0.11. 6.0.11 exists only as CI builds on Create's Maven, without the documented `slim` artifact.
  6.0.10 is the newest release on CurseForge and Modrinth. Coordinates come from Create's developer docs
  (wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1). Declared range: `[6.0.10,6.1.0)`, the range those docs give.
- **Host compiled against 0.18.12** (the JAR the API guide links on GitHub). The public signatures of every host class used are
  byte-for-byte the same in 0.18.12, 0.18.28 and 0.18.30 (javap diff). Declared range: `[0.18.12,)` as specified.
- **NeoForge 21.1.255**, matching the host's reference build (`stonecutter.properties.toml`).
- Mod id `createcinematic`, MIT licence. The host (MIT) and Create (MIT code) were read for reference only. No code or
  assets were copied, and neither is bundled.

## Scope calls
- **Supported: 58 blocks.** 16 toolboxes, 16 postboxes, frogport, schematicannon, schematic table, redstone requester, hand crank,
  17 valve handles, analog lever, desk bell, rotation speed controller.
- **Stock ticker skipped.** Its block menu (`StockKeeperCategoryMenu`) is a category editor driven by screen-only widgets and
  packets: lists, renaming, ordering. A slot scene would offer nothing real. Its request menu opens from the seated keeper
  entity, not the block.
- **Factory gauge skipped.** One block holds up to four panels, and the menu opens only for the clicked panel when its filter is
  empty and the hand is empty. `getMenuProvider(level, pos)` has no hit information to choose a panel.
- **Menu-less machines skipped** (belts, depots, basins, press, mixer, deployer, ...). The host API has no menu-less stations,
  and building one would mean mixing into the host, which the rules forbid.
- **Text fields stay in Create's window.** Package port address, redstone requester address, amounts and allow-partial.
  The scene never sends Create's configuration packets, because those carry the address and would overwrite it.
  - Sneak + use with an empty hand opens the window.
  - Create's own package ports, requester and toolbox ignore crouching players, so the add-on opens the window itself
    (`NativeUse.sneakWindow`). The same path serves all 37 blocks for consistency.
  - Accepted side effect: with an empty main hand, sneak + use opens the window instead of placing the off-hand item against
    the block. Also requiring an empty off hand would break the window for anyone carrying a shield.
- **Both sides must have the add-on.** This also means a server with the add-on refuses clients that have neither it nor the
  host, though the host on its own lets such clients in. That is intended: a half-installed pair breaks the stations. It registers one required network channel, so NeoForge refuses a mismatched
  connection with a "missing channel" message. Without it, a mismatch leaves the stations dead or flashing open and shut.
- **Fake players (deployers) always get Create's own behaviour.** The host would otherwise ask a fake player's
  nonexistent network connection whether it has the mod.
- **Spectators: known, not fixed.** Right-clicking a station as a spectator shows vanilla's "can't open" message.
  The alternative was letting spectators open Create's menus, which would also let them send Create's packets
  (starting a schematicannon, uploading a schematic). The message is the safer bug.
- **NeoForge floor is `[21.1.200,)`**, the host's own. The add-on needs `IMenuProviderExtension#writeClientSideData`,
  which arrived in 21.1.151 (checked against the Maven release jars).
- **Dedicated-server test.** NeoForge's dev environment skips the EULA check, so `./gradlew runServer` was run without
  writing `eula.txt`. A real server still needs the EULA accepted by its owner.

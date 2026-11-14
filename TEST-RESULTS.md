# Test results

Run on 2026-11-11 by driving a real game. A NeoForge dev **dedicated server** was controlled over RCON, and a dev **client** joined it over the network. The client was driven with synthetic mouse and keyboard input and checked with screenshots. Results were confirmed on the server side wherever possible, by reading block and player data.

Versions: NeoForge 21.1.255, Create 6.0.10, Cinematic Interactions 0.18.12, this add-on 1.0.0.

## Passed
| Test (TEST.md section) | Result | How it was confirmed |
|---|---|---|
| Startup, server and client (1) | Pass | All three mods load; no error from this add-on in either log |
| Matching client joins the server (8) | Pass | The required channel accepts a matching pair |
| Every scene starts, eases in and out (2) | Pass, all 6 kinds | Screenshots of toolbox, schematic table, schematicannon, frogport, postbox, requester |
| Toolbox: put an item in (3, 4) | Pass | 64 iron went in; the server's toolbox data showed it |
| Toolbox: shift-click to inventory (3) | Pass | Server toolbox emptied; the player had 64 iron again |
| Toolbox: reserved compartment kept (4) | Pass | Shows the reserved item faded, with the right tooltip |
| Schematicannon: gunpowder in (4) | Pass | Server shows 15 in the slot plus 1 burned; fuel bar filled |
| Schematicannon: Play with no schematic (4) | Pass | Stays Idle, as in Create's own window |
| Schematic table: walk up holding a blank (4) | Pass | The blank starts in the hand, on client and server |
| Schematic table: picker finds a file (4) | Pass | `tinytest.nbt` listed |
| **Schematic table: double-click Write (4)** | **Pass** | One upload. The server holds the written schematic (`schematic_file: tinytest.nbt`); no blank lost |
| Requester: ghost slot set (4) | Pass | Server `EncodedRequest` holds gold after leaving; the real gold stayed with the player |
| **Sneak + use, empty hand → Create's window (4, 5)** | **Pass** | Create's White Postbox window opened, address field included |
| Dye on toolbox → recolour, no scene (5) | Pass | Server block became `create:blue_toolbox` |
| Goggles on frogport (5) | Pass | Server `Goggles: 1b` |
| Clipboard on postbox → no scene (5) | Pass | No scene; Create's clipboard notice shown |
| **Hard disconnect mid-scene, item on cursor (6)** | **Pass** | Client process killed holding 10 gold; after reconnecting the player had all 10 |
| Block removed mid-scene (6) | Pass | Scene ended, controls back, no errors |
| Damage mid-scene, survival (6) | Pass | Scene ended, controls back, no errors |
| **Deployer, empty hand, on toolbox / postbox / requester (7)** | **Pass** | Deployers cycled for over 15 s with no crash or exception (this was the server crash) |
| Deployer with dye on toolbox (7) | Pass | Recoloured to blue through Create's own logic |
| Spectator right-click (7) | Pass | Nothing happens; no crash |
| Server log after all tests | Clean | Only errors: the expected missing `server.properties` on first boot and "Connection reset" from the forced disconnect |

## Found and fixed during testing
| Problem | Fix |
|---|---|
| **Frogport:** the frog model blocked most of the package tray; the hands were in the way | Ports now use an upright 2x9 panel just in front of the block face, like the schematicannon |
| **Postbox:** the tray floated high and the raised hands covered it | Same upright panel |
| **Toolbox:** camera too close, hands over the bottom compartments, ring hanging over the box edges | Camera pulled back and up; smaller ring that fits the box top |
| **Schematic table:** resting hands covered the Blank and Written slots | Camera pulled back; on top-down scenes the hands now rest wider and nearer the player |
| Requester: the settings text overflowed its plate | Shortened to "Settings: sneak + use, empty hand" |
| Toolbox: empty reserved compartment showed a stray "0" | No count shown when 1 or fewer |

All six scenes were re-checked by screenshot after these fixes.

## Not tested
- **Mod-mismatch connections** (a client without the add-on, or a server without it). The dev client and server always include it. Verified only by code review of NeoForge's channel negotiation.
- **Two players at once**: only one client was run.
- **Shift-click with a full inventory** (the "deny" case).
- **Playing a schematic** with the schematicannon (Play/Pause/Stop on a loaded schematic).
- **Packages in a port**: no packager was set up.
- **Another mod's wrench; placing a funnel, super glue or display link against a station.**
- **Animation feel**: screenshots show positions, not smoothness. No snapping showed between frames, but nobody has watched it at full speed.
- **Cinematic Interactions 0.18.30** in game (its API is identical to 0.18.12).

## Re-running these tests
```
./gradlew runServer                  # dev server (run/); set enable-rcon=true in run/server.properties for RCON
./gradlew runClient -PtestServer     # dev client (run-client/) that joins it straight away
```
The client has its own game folder, `run-client/`, so the two can run side by side.

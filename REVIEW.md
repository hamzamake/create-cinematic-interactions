# Review: more hand-driven blocks like the hand crank

**Decided 2026-11-11: all four approved and being built**, plus a scene for the rotation speed controller (section 5). That scene was a gearbox first, and on 2026-11-13 was changed to a CVT cutaway at your request.

All of them would reuse what the hand crank uses:
- a slot-less menu to keep the scene open;
- vanilla menu-button clicks to the server;
- a public method on Create's block entity.

So none needs a new packet, a mixin into Create, or reflection.

---

## 1. Valve handles: twist with the mouse
- **Blocks:** `copper_valve_handle` and the 16 dyed `*_valve_handle` (17 blocks).
- **What you'd do:** grab the wheel and twist it, like the crank but in short turns. Twist far enough one way and the valve turns once that way. Twist the other way to turn it back.
- **What Create does on the server:** `ValveHandleBlockEntity.activate(back)`. The valve turns by the angle set on it (45° by default). The scene takes the click that opened Create's value panel, so the mouse wheel sets that angle in the scene instead (through the panel's own server code).
- **Limits, from Create:**
  - It turns in that fixed step only, never by a free amount.
  - It refuses while the shaft is already turning or cooling down. The scene would show a "busy" shake.
- **Kept as Create's own clicks:** dye on the valve (recolour) and wrench + sneak (pick up).
- **Effort:** small. It is mostly the crank scene with a step instead of a continuous turn.

**Add it?** `[x] yes   [ ] no`

## 2. Analog lever: drag it notch by notch
- **Block:** `analog_lever`.
- **What you'd do:** grab the lever and drag it along its arc. Every notch you pass sets the signal one higher or lower, 0 to 15. The number is shown beside it, with a click per notch.
- **What Create does on the server:** `AnalogLeverBlockEntity.changeState(back)`, one step per notch. That is the same as one right-click, or one sneak-right-click.
- **As built:** a drag sends the notch you stop on; the server steps Create's `changeState` there one notch at a time (Create's only setter).
- **Effort:** small to medium. It needs a new drag-along-an-arc gesture.

**Add it?** `[x] yes   [ ] no`

## 3. Hand crank: a gauge in the crank scene
- **Block:** `hand_crank` (already has a scene). This adds to it.
- **What you'd see:** while cranking, a small read-only gauge for the speed you're putting out (32 RPM, and which way). It would also show whether the network is overstressed, read from the client's copy of the block, as goggles do.
- **Effort:** small. Nothing new is sent to the server.

**Add it?** `[x] yes   [ ] no`

## 4. Desk bell: tap it
- **Block:** `desk_bell`.
- **What you'd do:** a quick press-down with the hand and a ding. It is one click, not a mouse gesture, so it adds little over a normal right-click.
- **What Create does:** its normal use rings the bell and sends a short redstone pulse.
- **My take:** probably not worth it. It is listed because it is the next closest "hand" block.
- **Still in the game?** Yes: Create 6.0.10 registers `desk_bell` in `AllBlocks` and ships its model, sound and loot table.

**Add it?** `[x] yes   [ ] no`

## 5. Rotation speed controller: a gearbox instead of the value panel
- **Block:** `rotation_speed_controller`.
- **What you'd do:** change a real gear ratio in front of the block to raise or lower the RPM. The gears mesh and spin at the real speeds.
- **What Create does on the server:** the value panel's own `setValueSettings` on `targetSpeed`, sent when a gear clicks in, not while dragging (each set rebuilds the kinetic network).
- **Requested by you** (not one of the original ideas).

**Add it?** `[x] yes   [ ] no`

---

## Looked at and left out
| Block | Why not |
|---|---|
| `steam_whistle` | Redstone plays it. Right-clicking only adds size when you hold another whistle, so there is nothing to do by hand. |
| `controls` (train) | Used while seated on a train, from the seat entity, not the block. The host API is block-only. |
| `lectern_controller` / linked controller | Driven by keyboard keys bound to frequencies, not a hand motion on the block. |
| `contraption_controls` | A filter slot plus a toggle. It is not a gesture, and the filter is set in the world. |
| Wrenching (rotate / pick up) | It belongs to Create's wrench item on every kinetic block. A scene would only get in the way. |

---
name: minecraft
description: Control the current Ardion Minecraft player through native game actions, build executable game skills, and verify tasks against live observations. Use for Minecraft gameplay tasks.
---

# Embodied Minecraft agent

## Ardion modules, settings and diagnostics

Use `ardion_modules` to discover registered modules, change parameters,
and enable or disable them. Do not change module key bindings. `ardion_settings` covers global, HUD,
theme and target values. `ardion_config` loads and saves config and HUD layout,
while preserving module key bindings. `ardion_command` runs the real local client
dispatcher (friends, notebot and other client commands). `ardion_music` controls
the existing music player and its account/playlist/search/playback services.
These tools are fully authorized in both AI Chat and CLI: carry out the user's
requested module/settings/music operations without asking for permission again.
They act on Ardion itself; no Voyager player/bot selection is required.
Discover parameter names, choices and ranges before changing them, and verify
the returned state. Use `persist: true` when the user wants settings saved.

Use `client_inspect`, `client_threads`, `client_memory` and `client_events` for
runtime members, thread/lock stacks, memory and listener timing or errors.

This Minecraft MCP addresses the user's existing player. For gameplay load the
`voyager` skill: its tool explicitly selects target `client` or a separately
connected Mineflayer `bot`. The MC version supplied
in minecraft.md is authoritative. Use the actual world's blocks, entities, recipes,
inventory and command tree rather than assuming another version's mechanics.
Choose relevant observations for the task; there is no mandatory first status call.

## Native operations

- `find_blocks` searches exact block IDs in loaded terrain. `navigate` walks with
  local collision/path checks; it does not teleport, dig or bridge automatically.
- `mine_block` equips a carried tool and keeps breaking across game ticks.
- `interact_block` uses the held item on a visible face: placement, buckets, doors,
  stations and containers. Select/equip an item with `inventory_action` first.
- `interact_entity` attacks/interacts with the given live entity ID.
- `use_item` sustains item use (food, potions, charging). Verify resulting changes.
- `inventory` includes `handler.syncId`, empty/occupied handler slots, player
  inventory indices and cursor. Use `inventory_action` for shift transfer, swap,
  drag, drop, armor/offhand, screen/handler buttons and closing screens. Handler
  slot IDs and player inventory indices differ. Never reuse a stale syncId.
- `recipes` supplies server recipe IDs, ingredient alternatives, output and
  station. `craft_recipe` fills/takes crafting output asynchronously. For furnace
  tasks open it and use its input/fuel/output slots, wait, and verify inventory.
- Long actions return `actionId` with `status: running`. Await `action_status`
  with `waitMs: 10000`. Completion includes before/after observations. A submitted
  click or client prediction is not proof of server acceptance or task completion.
- `cancel_action` stops active movement, mining and item use.
  Death, screen interruption, world change, lost route and timeouts fail explicitly.
  One long action runs at a time. Raw key/mouse/look/bindings/release_inputs tools
  have been removed; do not call them or recreate input simulation in programs.

## Executable programs and persistent skills

Compose many actions into normal async JavaScript, instead of paying for a model
call for every tick. The bundled engine can execute these programs without an
installed Node.js or Bun:

```powershell
& $env:ARDION_ENGINE_EXE minecraft 'C:/path/to/program.mjs'
```

On Linux, use the native shell syntax:

```sh
"$ARDION_ENGINE_EXE" minecraft '/path/to/program.mjs'
```

```js
const mc = await import(process.env.ARDION_MINECRAFT_API);
const targets = await mc.call("find_blocks", { block: "minecraft:oak_log" });
if (!targets.blocks.length) throw new Error("No loaded oak logs found");
const target = targets.blocks[0];
try {
  await mc.run("navigate", { x: target.x, y: target.y, z: target.z, range: 3 });
  await mc.run("mine_block", { x: target.x, y: target.y, z: target.z });
  console.log(JSON.stringify(await mc.observe()));
  // Broken block is not collected inventory. Compare counts, approach drops and
  // wait for pickup when the user's task requires obtaining an item.
} finally {
  await mc.cancel();
}
```

Programs can inspect conditions, loop, branch, retry a different approach and
reuse helper functions. `mc.run` awaits a long action and throws with its game
evidence on failure. Use shell timeout 0 for intentionally long-running programs;
do not hide errors in success messages or retry forever without new evidence.

After a successful reusable behavior, save its code and a SKILL.md under
`$env:ARDION_SKILLS_DIR/<descriptive-name>/`. Include applicability, parameters,
required equipment, verified postconditions and failure recovery. Load and reuse
existing skills on later tasks/worlds, resolve live targets again, and verify the
postcondition rather than persisting old coordinates/entity/recipe IDs as truth.
Keep existing user skills; refine them after observed success. Sessions and skill
files persist across restarts and mod updates. User-directed goals drive work;
do not invent an endless curriculum when the user asked for a specific task.

---
name: voyager
description: Execute Voyager-style Minecraft behaviors and reusable programs on either the current Ardion player or a separately connected Mineflayer bot. Use for gameplay, gathering, crafting, exploration, combat and containers.
---

# Voyager behaviors, two explicit players

Before using this skill's tools, the user must explicitly say whether this task
controls their current Ardion player or creates/uses a separate bot. If the task
does not specify this, ask: “这次要操控你当前的玩家，还是生成独立 bot Alex？”
Wait for the answer before any Voyager operation, including connect, observe or
execute. Ask in a normal reply and end the workflow while waiting; an unavailable
or cancelled question tool is never permission to guess a target.
Do not infer the choice from configuration, an already connected bot,
or a previous unrelated task. Keep that explicit choice within the same workflow;
ask again when a new task is ambiguous. Never ask again if the user already chose
the target for this task, and never switch players without their instruction.

Use the `voyager` MCP tool with `target: "client"` or `target: "bot"`, an `action`,
and a positional `args` array. Never silently switch targets. The client target is
the user's live Ardion player. The bot target is a second Minecraft connection;
call `connect` with `[ {host,port,username,auth,version} ]` or put that object in
`~/.ardion/agent/voyager.json` and pass `args: []`. The generated offline bot is
always named `Alex`, including when an older config specifies another username.
Auth is offline or microsoft; Microsoft login requires the account identity and
the server displays that account's profile name, which cannot be renamed Alex
by a client option. Explain that exception before using Microsoft login.
Never invent credentials or enable cheats. An explicit request to summon Alex
into the current singleplayer world authorizes opening LAN for that bot.

Target `client` and the minecraft MCP work in private singleplayer without LAN.
Only a separate `bot` needs a reachable Minecraft server. To summon Alex into
the current singleplayer world, use `target: "bot", action: "connect", args:
[{"currentWorld":true}]`. This calls the client's native `prepare_bot` operation:
it opens LAN if necessary, preserves game mode and cheats, reuses an existing
LAN port, and permits one loopback Alex login for 30 seconds without changing
server-wide account verification. It then connects and returns the spawned bot's
observation. Do not ask the user to open LAN manually or guess ports. A saved
remote config is ignored with `currentWorld:true`; explicit remote connection
options and the existing config-based connect remain available for remote tasks.
LAN stays published after the bot leaves until the world closes, as with vanilla
Open to LAN. Do not silently switch to `client` if preparation or login fails.
MCP connection/class-loading errors are not evidence that LAN needs enabling.

The bot uses Voyager's vendored JavaScript primitives, backed by real Mineflayer,
pathfinder, collectblock, tool, pvp and HawkEye plugins. The client implements the
same behavioral interface using the current player's native movement, interaction,
inventory and server recipes. It does not inject keyboard or mouse events. Its
walking planner currently covers loaded ground routes, steps and short drops;
it does not gain Mineflayer's digging/bridging/swimming planner by changing names.

## Behaviors (positional args)

- `observe []`: actual player/version/inventory; no mandatory initial call.
- `goto [ {x,y,z,range?} ]`, `waitTicks [ticks]`, `equip [itemName]`, `consume []`.
- `mineBlock [blockName,count?]`: locate, approach, break and attempt collection;
  read resulting inventory instead of assuming ore/block names equal drops.
- `craftItem [itemName,count?]`: count is recipe executions, as in Voyager.
- `smeltItem [itemName,fuelName,count?]`: find furnace, fuel, smelt and collect.
- `placeItem [itemName,{x,y,z}]`: target is the placed block position.
- `killMob [mobName,timeoutSeconds?]`, `shoot [weaponName,mobName]`.
- `exploreUntil [{x,y,z},seconds?]`: direction components are -1/0/1.
- `depositItemIntoChest [{x,y,z},{itemName:count}]` and
  `getItemFromChest [{x,y,z},{itemName:count}]`.
- `checkItemInsideChest [{x,y,z}]`, `cancel []`, `disconnect []` (bot only).
- `execute [absoluteProgramPath]`: run a normal async JS program exporting
  `default async function(api)`. It receives the chosen target's behavioral API;
  callbacks such as `exploreUntil` predicates are available inside programs.

Example tool call: `{"target":"client","action":"mineBlock","args":["oak_log",3]}`.
Both targets report observations, errors and Voyager onChat/onSave feedback.
Submission or a feedback string is not proof of a server outcome: verify inventory,
blocks, entities and goal conditions; use errors to revise the next program.

```js
export default async function(api) {
  await api.mineBlock("oak_log", 3);
  await api.craftItem("oak_planks", 2);
  return await api.observe();
}
```

After verified success, save reusable code plus a descriptive SKILL.md under
`ARDION_SKILLS_DIR/<name>/`; reuse and refine those files across sessions/worlds.
Do not introduce a second model loop or fixed tool-call quota. The existing agent
owns task decomposition, program execution, feedback, correction and verification.
The Ardion module/settings/music and read-only debug MCP remain separate from
player control. Module key bindings must not change. Raw `key`, `mouse`, `look`,
`bindings` and `release_inputs` tools have been retired; use behaviors/cancel.

package io.github.wzhy233.mcp;

import java.util.List;
import java.util.Map;
import static io.github.wzhy233.mcp.GameTools.*;

final class GameActionSpecs {
    static List<GameTools.Spec> specs() {
        var coordinates = Map.of("x", prop("number", "Block x"), "y", prop("number", "Feet/block y"), "z", prop("number", "Block z"),
                "timeoutTicks", prop("number", "Action deadline in game ticks, default 1200, max 12000"));
        var navigate = new java.util.LinkedHashMap<>(coordinates);
        navigate.put("range", prop("number", "Arrival radius 0.5-8, default 1, with 0.05 block tolerance"));
        var mine = new java.util.LinkedHashMap<>(coordinates);
        mine.put("autoTool", prop("boolean", "Select the best carried tool, default true"));
        return List.of(
                spec("find_blocks", "Find matching block IDs in loaded chunks near the player, sorted by distance. Does not load chunks or inspect server-only data.", schema(Map.of(
                        "block", prop("string", "Exact registry ID, e.g. minecraft:oak_log"), "radius", prop("number", "Search radius 1-32, default 16"),
                        "limit", prop("number", "Maximum matches 1-128, default 32")), List.of("block"))),
                spec("navigate", "Walk the current player to coordinates using collision-aware local pathfinding. Walks/jumps one block/drops up to three, avoids fluids/hazards, no teleport or world edits. Search uses loaded chunks within 64 blocks/4096 nodes; use nearer waypoints for longer routes. Returns running actionId; await action_status before claiming arrival.", schema(navigate, List.of("x", "y", "z"))),
                spec("mine_block", "Mine one visible reachable block with vanilla breaking progress across game ticks. Optionally equips best carried tool. Returns actionId; completion includes observations, not a claim of item collection. Navigate closer first if needed.", schema(mine, List.of("x", "y", "z"))),
                spec("use_item", "Use the selected main/offhand item over game ticks (eat, drink, charge bow, etc.). Returns actionId. Inspect after inventory/effects; interaction completion alone does not prove your task succeeded.", schema(Map.of("hand", prop("string", "main or off"), "ticks", prop("number", "Duration, default 40, max 12000")), List.of())),
                spec("wait_ticks", "Wait for real Minecraft ticks and return before/after state and inventory. Returns actionId; await action_status.", schema(Map.of("ticks", prop("number", "1-12000 game ticks")), List.of("ticks"))),
                spec("action_status", "Read an action's progress or terminal result including before/after game evidence. Use waitMs to long-poll without busy loops. Only one long action can run at once.", schema(Map.of("actionId", prop("string", "ID returned by navigate/mine/use/wait/craft"), "waitMs", prop("number", "Wait for completion up to 10000 milliseconds, default 0")), List.of("actionId"))),
                spec("cancel_action", "Cancel the current long action and release its inputs, breaking progress and item use.", schema()),
                spec("aim_entity", "Aim at a visible entity using game coordinates, without mouse events. Used by Voyager ranged interactions. Does not fire or claim a hit.", schema(Map.of("entityId", prop("number", "Live entity ID")), List.of("entityId"))),
                spec("interact_block", "Vanilla right-click a visible block face: open chests/furnaces/doors, place the held block against that face, use tools/buckets. Requires normal player reach and line of sight. Result is submission; wait ticks and inspect state/inventory to verify.", schema(Map.of(
                        "x", prop("number", "Clicked block x"), "y", prop("number", "Clicked block y"), "z", prop("number", "Clicked block z"),
                        "face", prop("string", "Optional visible face: up/down/north/south/east/west"), "hand", prop("string", "main or off")), List.of("x", "y", "z"))),
                spec("interact_entity", "Attack or right-click an entity by the ID from entities (combat, trading, mounting, breeding, etc.). Checks reach and visibility; server remains authoritative.", schema(Map.of(
                        "entityId", prop("number", "Live entity ID"), "action", prop("string", "attack or interact"), "hand", prop("string", "main or off")), List.of("entityId"))),
                spec("inventory_action", "Operate real inventory/container slots. open/close screen, select hotbar, equip player inventory slot, click handler slot, or button (merchant/enchanting/etc.). Read inventory.handler for empty slots, cursor and syncId. Clicks use server synchronization; inspect again after ticks.", schema(Map.of(
                        "action", prop("string", "open, close, select, equip, click, button"), "slot", prop("number", "select: hotbar 0-8; equip: player inventory index; click: handler slot ID (-999 outside)"),
                        "hotbar", prop("number", "equip destination 0-8, default selected"), "syncId", prop("number", "Required for click/button; must match inventory.handler.syncId"),
                        "type", prop("string", "PICKUP, QUICK_MOVE, SWAP, THROW, QUICK_CRAFT, PICKUP_ALL, CLONE (vanilla modes)"), "button", prop("number", "Click mouse button, SWAP hotbar 0-8/offhand 40, or handler button ID")), List.of("action"))),
                spec("recipes", "Inspect the connected server's unlocked recipes, ingredients, outputs and crafting station. Returns connection-specific recipeId and pagination. Does not invent recipes from another MC version.", schema(Map.of("query", prop("string", "Output registry ID substring"), "offset", prop("number", "Pagination offset, default 0")), List.of())),
                spec("craft_recipe", "Fill a recipe in the current player/crafting-table handler, await its output, and quick-move it into inventory. Returns actionId. Requires ingredients and the appropriate open station; furnace/smelting uses inventory_action input/fuel/output slots.", schema(Map.of(
                        "recipeId", prop("number", "ID from recipes"), "syncId", prop("number", "Current inventory.handler.syncId"), "all", prop("boolean", "Fill maximum amount, default false")), List.of("recipeId", "syncId")))
        );
    }
}

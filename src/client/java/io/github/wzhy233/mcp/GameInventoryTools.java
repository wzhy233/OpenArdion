package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

final class GameInventoryTools {
    static int integer(JsonObject args, String key, int min, int max) {
        double number = GameToolSupport.number(args, key, Double.NaN);
        if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max) {
            throw new IllegalArgumentException(key + " must be an integer from " + min + " to " + max);
        }
        return (int) number;
    }

    static JsonObject container() {
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        var handler = player.currentScreenHandler;
        JsonObject result = new JsonObject();
        result.addProperty("syncId", handler.syncId);
        result.addProperty("revision", handler.getRevision());
        result.addProperty("handler", handler.getClass().getSimpleName());
        result.add("cursor", GameToolSupport.stack(handler.getCursorStack(), -1));
        JsonArray slots = new JsonArray();
        for (var slot : handler.slots) {
            JsonObject row = GameToolSupport.stack(slot.getStack(), slot.id);
            row.addProperty("inventorySlot", slot.getIndex());
            row.addProperty("playerInventory", slot.inventory == player.getInventory());
            row.addProperty("canTake", slot.canTakeItems(player));
            slots.add(row);
        }
        result.add("slots", slots);
        return result;
    }

    static String action(JsonObject args) {
        GameActionTools.requireIdle();
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        String operation = GameToolSupport.text(args, "action");
        switch (operation) {
            case "open" -> {
                if (player.currentScreenHandler != player.playerScreenHandler) throw new IllegalStateException("Close the current container first");
                client.setScreen(new InventoryScreen(player));
            }
            case "close" -> player.closeHandledScreen();
            case "select" -> {
                int slot = integer(args, "slot", 0, 8);
                player.getInventory().setSelectedSlot(slot);
                player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
            case "equip" -> {
                int source = integer(args, "slot", 0, player.getInventory().size() - 1);
                int hotbar = args.has("hotbar") ? integer(args, "hotbar", 0, 8) : player.getInventory().getSelectedSlot();
                var handler = player.currentScreenHandler;
                var slot = handler.slots.stream().filter(row -> row.inventory == player.getInventory() && row.getIndex() == source).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Inventory slot not accessible in this handler"));
                if (source != hotbar) client.interactionManager.clickSlot(handler.syncId, slot.id, hotbar, SlotActionType.SWAP, player);
                player.getInventory().setSelectedSlot(hotbar);
                player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbar));
            }
            case "click", "button" -> {
                int syncId = integer(args, "syncId", 0, Integer.MAX_VALUE);
                if (syncId != player.currentScreenHandler.syncId) throw new IllegalStateException("Container changed; read inventory again before clicking");
                if (operation.equals("button")) {
                    client.interactionManager.clickButton(syncId, integer(args, "button", 0, 1024));
                    break;
                }
                String type = GameToolSupport.text(args, "type").toUpperCase(java.util.Locale.ROOT);
                SlotActionType click;
                try { click = SlotActionType.valueOf(type.isEmpty() ? "PICKUP" : type); }
                catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown slot click type"); }
                int slot = integer(args, "slot", -999, player.currentScreenHandler.slots.size() - 1);
                if (slot < 0 && slot != -999) throw new IllegalArgumentException("Use -999 for outside or a real handler slot");
                int max = click == SlotActionType.SWAP ? 40 : click == SlotActionType.QUICK_CRAFT ? 10 : click == SlotActionType.CLONE ? 2 : 1;
                int button = args.has("button") ? integer(args, "button", 0, max) : 0;
                if (click == SlotActionType.SWAP && button > 8 && button != 40) throw new IllegalArgumentException("Swap button must be hotbar 0-8 or offhand 40");
                client.interactionManager.clickSlot(syncId, slot, button, click, player);
            }
            default -> throw new IllegalArgumentException("action must be open, close, select, equip, click, or button");
        }
        JsonObject result = container();
        result.addProperty("submitted", true);
        result.addProperty("note", "Client prediction; wait for server synchronization and inspect inventory to verify the outcome.");
        return result.toString();
    }

    static java.util.List<RecipeDisplayEntry> entries() {
        var player = GameToolSupport.requirePlayer(MinecraftClient.getInstance());
        return player.getRecipeBook().getOrderedResults().stream().flatMap(collection -> collection.getAllRecipes().stream()).toList();
    }

    static String recipes(JsonObject args) {
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        String query = GameToolSupport.text(args, "query").toLowerCase(java.util.Locale.ROOT);
        int offset = args.has("offset") ? integer(args, "offset", 0, 1_000_000) : 0;
        var context = SlotDisplayContexts.createParameters(client.world);
        var finder = new RecipeFinder();
        player.getInventory().populateRecipeFinder(finder);
        if (player.currentScreenHandler instanceof AbstractRecipeScreenHandler handler) handler.populateRecipeFinder(finder);
        JsonArray recipes = new JsonArray();
        int matched = 0;
        for (var entry : entries()) {
            var outputs = entry.getStacks(context);
            if (!query.isBlank() && outputs.stream().noneMatch(stack -> Registries.ITEM.getId(stack.getItem()).toString().contains(query))) continue;
            if (matched++ < offset || recipes.size() >= 80) continue;
            JsonObject row = new JsonObject();
            row.addProperty("recipeId", entry.id().index());
            row.addProperty("display", entry.display().getClass().getSimpleName());
            row.addProperty("hasIngredients", entry.isCraftable(finder));
            JsonArray result = new JsonArray();
            outputs.forEach(stack -> result.add(GameToolSupport.stack(stack, -1)));
            row.add("outputs", result);
            JsonArray station = new JsonArray();
            entry.display().craftingStation().getStacks(context).forEach(stack -> station.add(GameToolSupport.stack(stack, -1)));
            row.add("station", station);
            entry.craftingRequirements().ifPresent(requirements -> {
                JsonArray ingredients = new JsonArray();
                requirements.forEach(ingredient -> {
                    JsonArray alternatives = new JsonArray();
                    ingredient.getMatchingItems().limit(128).forEach(item -> alternatives.add(Registries.ITEM.getId(item.value()).toString()));
                    ingredients.add(alternatives);
                });
                row.add("ingredients", ingredients);
            });
            recipes.add(row);
        }
        JsonObject result = new JsonObject();
        result.add("recipes", recipes);
        result.addProperty("totalMatched", matched);
        result.addProperty("nextOffset", offset + recipes.size() < matched ? offset + recipes.size() : -1);
        result.addProperty("note", "Only recipes sent by this server are available. IDs belong to this connection. hasIngredients does not prove the current crafting station is suitable.");
        return result.toString();
    }
}

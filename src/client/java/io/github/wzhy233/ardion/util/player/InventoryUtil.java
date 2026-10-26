package io.github.wzhy233.ardion.util.player;

import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.Set;

public final class InventoryUtil {
    public static final int ARMOR_START = 5;
    public static final int INVENTORY_START = 9;
    public static final int HOTBAR_START = 36;
    public static final int HOTBAR_END = 44;
    public static final int OFFHAND_SLOT = 45;

    private static final Set<Block> INVALID_BLOCKS = Set.of(
            Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE, Blocks.IRON_BARS, Blocks.SNOW, Blocks.CHEST,
            Blocks.TRAPPED_CHEST, Blocks.TORCH, Blocks.ANVIL, Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX, Blocks.TNT, Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON, Blocks.LEVER, Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL, Blocks.LADDER, Blocks.FURNACE, Blocks.SAND, Blocks.CACTUS,
            Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTING_TABLE, Blocks.COBWEB,
            Blocks.PUMPKIN, Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH, Blocks.FLOWER_POT
    );

    private InventoryUtil() {
    }

    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (isValidBlock(stack)) return true;
        if (isBuffPotion(stack)) return true;
        if (isGoodFood(stack)) return true;
        if (isThrowable(stack)) return true;
        return isGoodItem(stack);
    }

    public static boolean isValidBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 1 || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!isItemValid(stack)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return !(block instanceof FlowerBlock)
                && !(block instanceof BushBlock)
                && !(block instanceof FungusBlock)
                && !(block instanceof CropBlock)
                && !(block instanceof SlabBlock)
                && isValidBlock(block);
    }

    public static boolean isValidBlock(Block block) {
        return block != null
                && !(block instanceof FluidBlock)
                && !(block instanceof AirBlock)
                && !(block instanceof ChestBlock)
                && !(block instanceof FurnaceBlock)
                && !(block instanceof LadderBlock)
                && !(block instanceof TntBlock)
                && !INVALID_BLOCKS.contains(block);
    }

    public static boolean isItemValid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        String name = stack.getName().getString().toLowerCase();
        return !name.contains("click")
                && !name.contains("right")
                && !name.contains("teleport")
                && !name.contains("使用")
                && !name.contains("点击")
                && !name.contains("传送")
                && !name.contains("再来");
    }

    public static boolean isBuffPotion(ItemStack stack) {
        PotionContentsComponent contents = stack == null ? null : stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null || !contents.hasEffects()) {
            return false;
        }
        for (StatusEffectInstance effect : contents.getEffects()) {
            if (!effect.getEffectType().value().isBeneficial()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isHealingPotion(ItemStack stack) {
        PotionContentsComponent contents = stack == null ? null : stack.get(DataComponentTypes.POTION_CONTENTS);
        return contents != null && (contents.matches(Potions.HEALING) || contents.matches(Potions.STRONG_HEALING));
    }

    public static boolean isGoodFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return true;

        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        return food != null && food.nutrition() >= 4 && food.saturation() >= 0.3F;
    }

    public static boolean isGoodItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.FIRE_CHARGE
                || item == Items.ENDER_PEARL
                || item == Items.ARROW
                || item == Items.WATER_BUCKET
                || item == Items.SLIME_BALL
                || item == Items.TNT
                || item == Items.END_CRYSTAL
                || item == Items.FISHING_ROD
                || item == Items.MACE
                || item == Items.TRIDENT;
    }

    public static boolean isThrowable(ItemStack stack) {
        return stack != null && (stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG));
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isIn(ItemTags.SWORDS);
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isIn(ItemTags.PICKAXES);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isIn(ItemTags.AXES);
    }

    public static boolean isShovel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isIn(ItemTags.SHOVELS);
    }

    public static boolean isTool(ItemStack stack) {
        return isPickaxe(stack) || isAxe(stack) || isShovel(stack);
    }

    public static boolean isMace(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(Items.MACE);
    }

    public static boolean isTrident(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(Items.TRIDENT);
    }

    public static boolean isArmor(ItemStack stack) {
        EquipmentSlot slot = getArmorEquipmentSlot(stack);
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    public static EquipmentSlot getArmorEquipmentSlot(ItemStack stack) {
        EquippableComponent equippable = stack == null ? null : stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
    }

    public static int getArmorSlot(EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD) return 5;
        if (slot == EquipmentSlot.CHEST) return 6;
        if (slot == EquipmentSlot.LEGS) return 7;
        if (slot == EquipmentSlot.FEET) return 8;
        return -1;
    }

    public static int getToolType(ItemStack stack) {
        if (isPickaxe(stack)) return 0;
        if (isAxe(stack)) return 1;
        return -1;
    }

    public static double getItemDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0D;
        AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        double damage = modifiers.applyOperations(EntityAttributes.ATTACK_DAMAGE, 0.0D, EquipmentSlot.MAINHAND);
        damage += enchantLevel(stack, Enchantments.SHARPNESS) * 1.25D;
        damage += enchantLevel(stack, Enchantments.FIRE_ASPECT);
        return damage;
    }

    public static float getToolScore(ItemStack stack) {
        if (!isTool(stack)) return 0.0F;
        float score;
        if (isPickaxe(stack)) {
            score = stack.getMiningSpeedMultiplier(Blocks.STONE.getDefaultState());
        } else if (isAxe(stack)) {
            score = stack.getMiningSpeedMultiplier(Blocks.OAK_LOG.getDefaultState());
        } else {
            score = stack.getMiningSpeedMultiplier(Blocks.DIRT.getDefaultState());
        }
        int efficiency = enchantLevel(stack, Enchantments.EFFICIENCY);
        score += efficiency * 0.0075F + efficiency / 100.0F;
        score += enchantLevel(stack, Enchantments.UNBREAKING) / 100.0F;
        return score;
    }

    public static double getArmorScore(ItemStack stack) {
        if (!isArmor(stack)) return 0.0D;
        EquipmentSlot slot = getArmorEquipmentSlot(stack);
        double baseArmor = getVanillaArmorPoints(stack);
        double baseToughness = getVanillaArmorToughness(stack);
        AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        double armor = baseArmor > 0.0D ? baseArmor : modifiers.applyOperations(EntityAttributes.ARMOR, 0.0D, slot);
        double toughness = baseToughness > 0.0D ? baseToughness : modifiers.applyOperations(EntityAttributes.ARMOR_TOUGHNESS, 0.0D, slot);
        double score = armor;
        score += toughness * 0.25D;
        score += enchantLevel(stack, Enchantments.PROTECTION) * 0.8D;
        score += enchantLevel(stack, Enchantments.BLAST_PROTECTION) * 0.2D;
        score += enchantLevel(stack, Enchantments.FIRE_PROTECTION) * 0.2D;
        score += enchantLevel(stack, Enchantments.PROJECTILE_PROTECTION) * 0.2D;
        score += enchantLevel(stack, Enchantments.THORNS) * 0.1D;
        score += enchantLevel(stack, Enchantments.UNBREAKING) * 0.05D;
        score += enchantLevel(stack, Enchantments.MENDING) * 0.05D;
        score += durabilityRatio(stack) * 0.01D;
        return score;
    }

    public static double getBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.BOW)) return 0.0D;
        return 1.0D
                + enchantLevel(stack, Enchantments.POWER)
                + enchantLevel(stack, Enchantments.PUNCH) * 0.2D
                + enchantLevel(stack, Enchantments.FLAME) * 0.5D
                + enchantLevel(stack, Enchantments.INFINITY) * 0.4D
                + enchantLevel(stack, Enchantments.UNBREAKING) * 0.1D
                + enchantLevel(stack, Enchantments.MENDING) * 0.1D
                + durabilityRatio(stack) * 0.01D;
    }

    public static double getCrossbowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.CROSSBOW)) return 0.0D;
        return enchantLevel(stack, Enchantments.QUICK_CHARGE)
                + enchantLevel(stack, Enchantments.MULTISHOT) * 0.5D
                + enchantLevel(stack, Enchantments.PIERCING) * 0.25D
                + enchantLevel(stack, Enchantments.UNBREAKING) * 0.1D
                + enchantLevel(stack, Enchantments.MENDING) * 0.1D
                + durabilityRatio(stack) * 0.01D;
    }

    public static double getMaceScore(ItemStack stack) {
        if (!isMace(stack)) return 0.0D;
        return getItemDamage(stack)
                + enchantLevel(stack, Enchantments.UNBREAKING) * 0.1D
                + enchantLevel(stack, Enchantments.MENDING) * 0.1D
                + durabilityRatio(stack) * 0.01D;
    }

    public static double getTridentScore(ItemStack stack) {
        if (!isTrident(stack)) return 0.0D;
        return getItemDamage(stack)
                + enchantLevel(stack, Enchantments.UNBREAKING) * 0.1D
                + enchantLevel(stack, Enchantments.MENDING) * 0.1D
                + durabilityRatio(stack) * 0.01D;
    }

    private static double getVanillaArmorPoints(ItemStack stack) {
        if (stack.isOf(Items.LEATHER_HELMET)) return 1.0D;
        if (stack.isOf(Items.LEATHER_CHESTPLATE)) return 3.0D;
        if (stack.isOf(Items.LEATHER_LEGGINGS)) return 2.0D;
        if (stack.isOf(Items.LEATHER_BOOTS)) return 1.0D;

        if (stack.isOf(Items.GOLDEN_HELMET) || stack.isOf(Items.CHAINMAIL_HELMET)
                || stack.isOf(Items.IRON_HELMET) || stack.isOf(Items.COPPER_HELMET)
                || stack.isOf(Items.TURTLE_HELMET)) return 2.0D;
        if (stack.isOf(Items.GOLDEN_CHESTPLATE) || stack.isOf(Items.CHAINMAIL_CHESTPLATE)
                || stack.isOf(Items.COPPER_CHESTPLATE)) return 5.0D;
        if (stack.isOf(Items.GOLDEN_LEGGINGS) || stack.isOf(Items.COPPER_LEGGINGS)) return 3.0D;
        if (stack.isOf(Items.CHAINMAIL_LEGGINGS)) return 4.0D;
        if (stack.isOf(Items.GOLDEN_BOOTS) || stack.isOf(Items.CHAINMAIL_BOOTS)
                || stack.isOf(Items.COPPER_BOOTS)) return 1.0D;

        if (stack.isOf(Items.IRON_CHESTPLATE)) return 6.0D;
        if (stack.isOf(Items.IRON_LEGGINGS)) return 5.0D;
        if (stack.isOf(Items.IRON_BOOTS)) return 2.0D;

        if (stack.isOf(Items.DIAMOND_HELMET) || stack.isOf(Items.NETHERITE_HELMET)) return 3.0D;
        if (stack.isOf(Items.DIAMOND_CHESTPLATE) || stack.isOf(Items.NETHERITE_CHESTPLATE)) return 8.0D;
        if (stack.isOf(Items.DIAMOND_LEGGINGS) || stack.isOf(Items.NETHERITE_LEGGINGS)) return 6.0D;
        if (stack.isOf(Items.DIAMOND_BOOTS) || stack.isOf(Items.NETHERITE_BOOTS)) return 3.0D;
        return 0.0D;
    }

    private static double getVanillaArmorToughness(ItemStack stack) {
        if (stack.isOf(Items.NETHERITE_HELMET) || stack.isOf(Items.NETHERITE_CHESTPLATE)
                || stack.isOf(Items.NETHERITE_LEGGINGS) || stack.isOf(Items.NETHERITE_BOOTS)) {
            return 3.0D;
        }
        if (stack.isOf(Items.DIAMOND_HELMET) || stack.isOf(Items.DIAMOND_CHESTPLATE)
                || stack.isOf(Items.DIAMOND_LEGGINGS) || stack.isOf(Items.DIAMOND_BOOTS)) {
            return 2.0D;
        }
        return 0.0D;
    }

    private static double durabilityRatio(ItemStack stack) {
        if (stack == null || !stack.isDamageable() || stack.getMaxDamage() <= 0) return 1.0D;
        return Math.max(0.0D, (stack.getMaxDamage() - stack.getDamage()) / (double) stack.getMaxDamage());
    }

    public static boolean isBestSword(ScreenHandler handler, ItemStack stack) {
        if (handler == null || !isSword(stack)) return false;
        double bestDamage = Double.NEGATIVE_INFINITY;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = INVENTORY_START; i <= HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && isSword(other)) {
                double damage = getItemDamage(other);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestStack = other;
                }
            }
        }

        return bestStack == stack || getItemDamage(stack) > bestDamage;
    }

    public static boolean isBestAxeByDamage(ScreenHandler handler, ItemStack stack) {
        if (handler == null || !isAxe(stack)) return false;
        double bestDamage = Double.NEGATIVE_INFINITY;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = INVENTORY_START; i <= HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && isAxe(other)) {
                double damage = getItemDamage(other);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestStack = other;
                }
            }
        }

        return bestStack == stack || getItemDamage(stack) > bestDamage;
    }

    public static boolean isBestTool(ScreenHandler handler, ItemStack stack) {
        int type = getToolType(stack);
        if (handler == null || type == -1) return false;
        double bestScore = -1.0D;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = INVENTORY_START; i <= HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && isTool(other) && getToolType(other) == type) {
                double score = getToolScore(other);
                if (score > bestScore) {
                    bestScore = score;
                    bestStack = other;
                }
            }
        }

        return bestStack == stack || getToolScore(stack) > bestScore;
    }

    public static boolean isBestArmor(ScreenHandler handler, ItemStack stack) {
        EquipmentSlot slot = getArmorEquipmentSlot(stack);
        if (handler == null || slot == null) return false;
        double bestReduction = Double.NEGATIVE_INFINITY;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = ARMOR_START; i <= HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && isArmor(other) && getArmorEquipmentSlot(other) == slot) {
                double reduction = getArmorScore(other);
                if (reduction > bestReduction) {
                    bestReduction = reduction;
                    bestStack = other;
                }
            }
        }

        return bestStack == stack || getArmorScore(stack) > bestReduction;
    }

    public static boolean isBestBow(ScreenHandler handler, ItemStack stack) {
        if (handler == null || stack == null || !stack.isOf(Items.BOW)) return false;
        double bestDamage = -1.0D;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = INVENTORY_START; i <= HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && other.isOf(Items.BOW)) {
                double damage = getBowScore(other);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestStack = other;
                }
            }
        }

        return bestStack == stack || getBowScore(stack) > bestDamage;
    }

    public static boolean isBestCrossbow(ScreenHandler handler, ItemStack stack) {
        if (handler == null || stack == null || !stack.isOf(Items.CROSSBOW)) return false;
        double bestDamage = -1.0D;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = INVENTORY_START; i <= HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && other.isOf(Items.CROSSBOW)) {
                double damage = getCrossbowScore(other);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestStack = other;
                }
            }
        }

        return bestStack == stack || getCrossbowScore(stack) > bestDamage;
    }

    public static boolean isBestMace(ScreenHandler handler, ItemStack stack) {
        if (handler == null || !isMace(stack)) return false;
        double bestDamage = -1.0D;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = INVENTORY_START; i <= HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && isMace(other)) {
                double damage = getMaceScore(other);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestStack = other;
                }
            }
        }

        return bestStack == stack || getMaceScore(stack) > bestDamage;
    }

    public static boolean isBestTrident(ScreenHandler handler, ItemStack stack) {
        if (handler == null || !isTrident(stack)) return false;
        double bestDamage = -1.0D;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = INVENTORY_START; i <= HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && isTrident(other)) {
                double damage = getTridentScore(other);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestStack = other;
                }
            }
        }

        return bestStack == stack || getTridentScore(stack) > bestDamage;
    }

    public static boolean hasItem(ScreenHandler handler, Item item) {
        if (handler == null) return false;
        for (Slot slot : handler.slots) {
            if (slot.hasStack() && slot.getStack().isOf(item)) {
                return true;
            }
        }
        return false;
    }

    public static int enchantLevel(ItemStack stack, RegistryKey<Enchantment> enchantment) {
        if (stack == null || stack.isEmpty()) return 0;
        MinecraftAccessor accessor = MinecraftAccessor.INSTANCE;
        Registry<Enchantment> registry = accessor.registry();
        if (registry == null) return 0;
        RegistryEntry.Reference<Enchantment> entry = registry.getOptional(enchantment).orElse(null);
        return entry == null ? 0 : EnchantmentHelper.getLevel(entry, stack);
    }

    private static boolean isBest(ScreenHandler handler, ItemStack stack, StackPredicate predicate, StackScorer scorer) {
        if (handler == null || stack == null || stack.isEmpty() || !predicate.test(stack)) {
            return false;
        }

        double score = scorer.score(stack);
        for (int i = ARMOR_START; i <= OFFHAND_SLOT && i < handler.slots.size(); i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (!other.isEmpty() && other != stack && predicate.test(other) && scorer.score(other) > score) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    private interface StackPredicate {
        boolean test(ItemStack stack);
    }

    @FunctionalInterface
    private interface StackScorer {
        double score(ItemStack stack);
    }

    private enum MinecraftAccessor {
        INSTANCE;

        private Registry<Enchantment> registry;

        private Registry<Enchantment> registry() {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.world == null) return null;
            this.registry = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            return this.registry;
        }
    }
}

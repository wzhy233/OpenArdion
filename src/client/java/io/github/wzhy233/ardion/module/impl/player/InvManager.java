package io.github.wzhy233.ardion.module.impl.player;

import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.player.InventoryUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.*;

public final class InvManager extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final int START_DELAY = 0;
    private static final int MERGE_DELAY = 0;
    private static final int CHEST_SLOT_OFFSET = 1000;
    private static InvManager INSTANCE;
    private static int foundBlockCount;
    private static int foundThrowableCount;

    private final BooleanValue handleSword = this.registerValue(new BooleanValue("handle-sword", this, true));
    private final BooleanValue handlePickaxe = this.registerValue(new BooleanValue("handle-pickaxe", this, true));
    private final BooleanValue handleAxe = this.registerValue(new BooleanValue("handle-axe", this, true));
    private final BooleanValue handleGapple = this.registerValue(new BooleanValue("handle-gapple", this, true));
    private final BooleanValue handleWater = this.registerValue(new BooleanValue("handle-water", this, true));
    private final BooleanValue handleBow = this.registerValue(new BooleanValue("handle-bow", this, true));
    private final BooleanValue handleBlock = this.registerValue(new BooleanValue("handle-block", this, true));
    private final BooleanValue handlePearl = this.registerValue(new BooleanValue("handle-pearl", this, true));
    private final BooleanValue handleThrowable = this.registerValue(new BooleanValue("handle-throwable", this, true));
    private final BooleanValue handleArmor = this.registerValue(new BooleanValue("handle-armor", this, true));
    private final BooleanValue handleOffhand = this.registerValue(new BooleanValue("handle-offhand", this, true));

    private final NumberValue slotWeapon = this.registerValue(new NumberValue("weapon-slot", this, 1.0D, 1.0D, 9.0D, true, () -> this.handleSword.getValue()));
    private final NumberValue slotPick = this.registerValue(new NumberValue("pickaxe-slot", this, 2.0D, 1.0D, 9.0D, true, () -> this.handlePickaxe.getValue()));
    private final NumberValue slotAxe = this.registerValue(new NumberValue("axe-slot", this, 3.0D, 1.0D, 9.0D, true, () -> this.handleAxe.getValue()));
    private final NumberValue slotGapple = this.registerValue(new NumberValue("gapple-slot", this, 4.0D, 1.0D, 9.0D, true, () -> this.handleGapple.getValue()));
    private final NumberValue slotWater = this.registerValue(new NumberValue("water-slot", this, 5.0D, 1.0D, 9.0D, true, () -> this.handleWater.getValue()));
    private final NumberValue slotBow = this.registerValue(new NumberValue("bow-slot", this, 6.0D, 1.0D, 9.0D, true, () -> this.handleBow.getValue()));
    private final NumberValue slotBlock = this.registerValue(new NumberValue("block-slot", this, 7.0D, 1.0D, 9.0D, true, () -> this.handleBlock.getValue()));
    private final NumberValue slotPearl = this.registerValue(new NumberValue("pearl-slot", this, 8.0D, 1.0D, 9.0D, true, () -> this.handlePearl.getValue()));
    private final NumberValue slotThrowable = this.registerValue(new NumberValue("throwable-slot", this, 9.0D, 1.0D, 9.0D, true, () -> this.handleThrowable.getValue()));

    private final ModeValue offhandItem = this.registerValue(new ModeValue("offhand-item", this, "none",
            () -> this.handleOffhand.getValue(), "none", "gapple", "potion"));
    private final NumberValue blockCount = this.registerValue(new NumberValue("blocks-count-limit", this, 256.0D, 64.0D, 1024.0D, true));
    private final NumberValue throwableCount = this.registerValue(new NumberValue("throwable-count-limit", this, 32.0D, 0.0D, 64.0D, true));
    private final NumberValue actionDelay = this.registerValue(new NumberValue("action-delay", this, 3.0D, 0.0D, 10.0D, true));
    private final NumberValue dropDelay = this.registerValue(new NumberValue("drop-delay", this, 3.0D, 0.0D, 10.0D, true));
    public final BooleanValue dropFishingRod = this.registerValue(new BooleanValue("drop-fishing-rod", this, true));
    private final BooleanValue shuffle = this.registerValue(new BooleanValue("shuffle", this, true));

    private final int[] bestArmorPieces = new int[4];
    private final int[] bestToolSlots = new int[3];
    private final List<Integer> trash = new ArrayList<>();
    private final List<Integer> gappleStackSlots = new ArrayList<>();
    private final List<Integer> healingPotionSlots = new ArrayList<>();
    private final List<Integer> slots = new ArrayList<>();
    private final Random random = new Random();
    private final Set<String> serverItems = Set.of(
            "选择游戏", "加入游戏", "职业选择菜单", "特效", "选择职业", "离开对局", "再来一局",
            "selector", "tracking compass", "(right click)", "tienda ", "perfil", "salir", "shop",
            "collectibles", "game", "profil", "lobby", "show all", "hub", "friends only", "cofre",
            "(click", "teleport", "play", "exit", "hide all", "jeux", "gadget", "emote",
            "amis", "bountique", "choisir", "choose ", "设置", "道具"
    );

    private int bestSwordSlot;
    private int bestBowSlot;
    private int bestCrossbowSlot;
    private int bestMaceSlot;
    private int bestTridentSlot;
    private int bestWaterSlot;
    private int startDelayTicks;
    private int actionTicks;
    private int dropTicks;
    private int mergeTicks;
    private PendingMerge pendingMerge;
    private InventorySnapshot lastInventorySnapshot;

    public InvManager() {
        super("inv-manager", ModuleCategory.PLAYER);
        INSTANCE = this;
    }

    public static void resetTrashCounters() {
        foundBlockCount = 0;
        foundThrowableCount = 0;
    }

    public static void seedTrashCountersFromInventory(ScreenHandler handler) {
        resetTrashCounters();
        if (handler == null) return;

        for (int i = InventoryUtil.INVENTORY_START; i <= InventoryUtil.HOTBAR_END && i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof BlockItem) {
                foundBlockCount += stack.getCount();
            }
            if (InventoryUtil.isThrowable(stack)) {
                foundThrowableCount += stack.getCount();
            }
        }
    }

    public static boolean isTrash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;

        InvManager manager = INSTANCE;
        if (InventoryUtil.isHealingPotion(stack)) return false;

        if (stack.getItem() instanceof BlockItem) {
            foundBlockCount += stack.getCount();
            int limit = manager == null ? 256 : manager.blockCount.getValue().intValue();
            if (foundBlockCount > limit) return true;
        }

        if (InventoryUtil.isThrowable(stack)) {
            foundThrowableCount += stack.getCount();
            int limit = manager == null ? 32 : manager.throwableCount.getValue().intValue();
            if (foundThrowableCount > limit) return true;
        }

        ScreenHandler handler = mc.player == null ? null : mc.player.playerScreenHandler;
        if (InventoryUtil.isSword(stack) && InventoryUtil.isBestSword(handler, stack)) return false;
        if (InventoryUtil.isAxe(stack) && InventoryUtil.isBestAxeByDamage(handler, stack)) return false;
        if (InventoryUtil.isTool(stack) && !InventoryUtil.isAxe(stack) && InventoryUtil.isBestTool(handler, stack)) return false;
        if (InventoryUtil.isArmor(stack) && InventoryUtil.isBestArmor(handler, stack)) return false;
        if (stack.isOf(Items.BOW) && InventoryUtil.isBestBow(handler, stack)) return false;
        if (stack.isOf(Items.CROSSBOW) && InventoryUtil.isBestCrossbow(handler, stack)) return false;
        if (InventoryUtil.isMace(stack) && InventoryUtil.isBestMace(handler, stack)) return false;
        if (InventoryUtil.isTrident(stack) && InventoryUtil.isBestTrident(handler, stack)) return false;

        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.ENDER_PEARL)) return false;
        if (stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.FISHING_ROD)) {
            return InventoryUtil.hasItem(handler, stack.getItem());
        }
        return !InventoryUtil.isValidStack(stack);
    }

    public static Set<Integer> getTrashChestSlots(ScreenHandler inventoryHandler, ScreenHandler chestHandler, int containerSize) {
        InvManager manager = INSTANCE;
        if (manager == null || inventoryHandler == null || chestHandler == null) {
            return Collections.emptySet();
        }

        List<AnalyzedSlot> slots = new ArrayList<>();
        for (int i = InventoryUtil.ARMOR_START; i <= InventoryUtil.HOTBAR_END && i < inventoryHandler.slots.size(); i++) {
            slots.add(new AnalyzedSlot(i, inventoryHandler.getSlot(i).getStack()));
        }
        for (int i = 0; i < containerSize && i < chestHandler.slots.size(); i++) {
            slots.add(new AnalyzedSlot(CHEST_SLOT_OFFSET + i, chestHandler.getSlot(i).getStack()));
        }

        InventoryAnalysis analysis = manager.analyzeSlots(slots, false);
        Set<Integer> trashSlots = new HashSet<>();
        for (int slot : analysis.trash) {
            if (slot >= CHEST_SLOT_OFFSET) {
                trashSlots.add(slot - CHEST_SLOT_OFFSET);
            }
        }
        return trashSlots;
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null || !mc.player.isAlive()) {
            this.setEnabled(false);
            return;
        }

        if (!this.shouldWork()) {
            this.startDelayTicks = 0;
            this.actionTicks = 0;
            this.dropTicks = 0;
            this.mergeTicks = 0;
            this.pendingMerge = null;
            return;
        }

        this.tickDelays();

        if (this.startDelayTicks < START_DELAY) {
            this.startDelayTicks++;
            this.updateInventorySnapshot();
            return;
        }

        if (this.inventoryChangedExternally()) {
            return;
        }

        if (this.processPendingMerge()) return;

        resetTrashCounters();
        this.clear();
        if (!this.scanInventory()) {
            return;
        }

        if (this.sortOffhand(true)) return;
        if (this.equipArmor(true)) return;
        if (this.mergeInventoryItems(true)) return;

        if (this.sortHotbar(true)) return;
        this.dropItem();
    }

    private boolean shouldWork() {
        return mc.currentScreen instanceof InventoryScreen;
    }

    private boolean scanInventory() {
        ScreenHandler handler = mc.player.playerScreenHandler;

        List<AnalyzedSlot> analyzedSlots = new ArrayList<>();
        for (int i = InventoryUtil.ARMOR_START; i <= InventoryUtil.HOTBAR_END && i < handler.slots.size(); i++) {
            analyzedSlots.add(new AnalyzedSlot(i, handler.getSlot(i).getStack()));
        }
        if (this.shuffle.getValue()) {
            Collections.shuffle(analyzedSlots, this.random);
        }

        InventoryAnalysis analysis = this.analyzeSlots(analyzedSlots, true);
        if (analysis.aborted) return false;
        this.applyAnalysis(analysis);
        return true;
    }

    private InventoryAnalysis analyzeSlots(List<AnalyzedSlot> sourceSlots, boolean abortOnServerItem) {
        InventoryAnalysis analysis = new InventoryAnalysis();
        boolean foundWater = false;
        boolean foundFishingRod = false;
        List<Integer> blockLimitSlots = new ArrayList<>();
        List<Integer> throwableLimitSlots = new ArrayList<>();

        for (AnalyzedSlot sourceSlot : sourceSlots) {
            ItemStack stack = sourceSlot.stack();
            if (stack.isEmpty()) continue;
            analysis.stacks.put(sourceSlot.slot(), stack);
        }

        for (AnalyzedSlot sourceSlot : sourceSlots) {
            int slot = sourceSlot.slot();
            ItemStack stack = sourceSlot.stack();
            if (stack.isEmpty()) continue;
            if (this.isServerItem(stack)) {
                if (abortOnServerItem) {
                    analysis.aborted = true;
                    return analysis;
                }
                analysis.addTrash(slot);
                continue;
            }

            if (InventoryUtil.isHealingPotion(stack)) {
                analysis.healingPotionSlots.add(slot);
                continue;
            }

            if (stack.isOf(Items.FISHING_ROD) && this.dropFishingRod.getValue()) {
                analysis.addTrash(slot);
                continue;
            }
            if (InventoryUtil.isThrowable(stack)) {
                throwableLimitSlots.add(slot);
            }
            if (stack.getItem() instanceof BlockItem && InventoryUtil.isValidBlock(stack)) {
                blockLimitSlots.add(slot);
            }

            if (this.handleSword.getValue() && InventoryUtil.isSword(stack)) {
                analysis.bestSwordSlot = this.selectBestSlot(analysis, analysis.bestSwordSlot, slot, this.hotbarSlot(this.slotWeapon), InventoryUtil::getItemDamage);
            } else if (this.handleAxe.getValue() && InventoryUtil.isAxe(stack)) {
                analysis.bestToolSlots[1] = this.selectBestSlot(analysis, analysis.bestToolSlots[1], slot, this.hotbarSlot(this.slotAxe), InventoryUtil::getItemDamage);
            } else if (this.handlePickaxe.getValue() && InventoryUtil.isTool(stack) && !InventoryUtil.isAxe(stack)) {
                int toolType = InventoryUtil.getToolType(stack);
                if (toolType != -1) {
                    analysis.bestToolSlots[toolType] = this.selectBestSlot(analysis, analysis.bestToolSlots[toolType], slot, this.preferredToolSlot(toolType), InventoryUtil::getToolScore);
                } else {
                    analysis.addTrash(slot);
                }
            } else if (this.handleArmor.getValue() && InventoryUtil.isArmor(stack)) {
                int index = this.armorIndex(InventoryUtil.getArmorEquipmentSlot(stack));
                if (index != -1) {
                    int armorSlot = InventoryUtil.getArmorSlot(InventoryUtil.getArmorEquipmentSlot(stack));
                    analysis.bestArmorPieces[index] = this.selectBestSlot(analysis, analysis.bestArmorPieces[index], slot, armorSlot, InventoryUtil::getArmorScore);
                }
                continue;
            } else if (this.handleBow.getValue() && stack.isOf(Items.BOW)) {
                analysis.bestBowSlot = this.selectBestSlot(analysis, analysis.bestBowSlot, slot, this.hotbarSlot(this.slotBow), InventoryUtil::getBowScore);
            } else if (stack.isOf(Items.CROSSBOW)) {
                analysis.bestCrossbowSlot = this.selectBestSlot(analysis, analysis.bestCrossbowSlot, slot, -1, InventoryUtil::getCrossbowScore);
            } else if (InventoryUtil.isMace(stack)) {
                analysis.bestMaceSlot = this.selectBestSlot(analysis, analysis.bestMaceSlot, slot, -1, InventoryUtil::getMaceScore);
            } else if (InventoryUtil.isTrident(stack)) {
                analysis.bestTridentSlot = this.selectBestSlot(analysis, analysis.bestTridentSlot, slot, -1, InventoryUtil::getTridentScore);
            } else if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                analysis.gappleStackSlots.add(slot);
            } else if (stack.isOf(Items.WATER_BUCKET)) {
                if (!foundWater) {
                    foundWater = true;
                    if (this.handleWater.getValue()) {
                        analysis.bestWaterSlot = slot;
                    }
                } else if (this.handleWater.getValue()
                        && this.isPreferredSlot(analysis.bestWaterSlot, slot, this.hotbarSlot(this.slotWater))) {
                    analysis.addTrash(analysis.bestWaterSlot);
                    analysis.bestWaterSlot = slot;
                } else {
                    analysis.addTrash(slot);
                }
            } else if (stack.isOf(Items.FISHING_ROD)) {
                if (!foundFishingRod) foundFishingRod = true;
                else analysis.addTrash(slot);
            } else if (this.isManagedEquipment(stack)
                    || InventoryUtil.isMace(stack) || InventoryUtil.isTrident(stack)) {
                analysis.addTrash(slot);
            } else if (!InventoryUtil.isValidStack(stack)) {
                analysis.addTrash(slot);
            }
        }

        this.applyStackLimit(analysis, blockLimitSlots, this.blockCount.getValue().intValue());
        this.applyStackLimit(analysis, throwableLimitSlots, this.throwableCount.getValue().intValue());
        return analysis;
    }

    private void applyAnalysis(InventoryAnalysis analysis) {
        this.trash.clear();
        this.trash.addAll(analysis.trash);
        this.gappleStackSlots.clear();
        this.gappleStackSlots.addAll(analysis.gappleStackSlots);
        this.healingPotionSlots.clear();
        this.healingPotionSlots.addAll(analysis.healingPotionSlots);
        this.bestSwordSlot = analysis.bestSwordSlot;
        this.bestBowSlot = analysis.bestBowSlot;
        this.bestCrossbowSlot = analysis.bestCrossbowSlot;
        this.bestMaceSlot = analysis.bestMaceSlot;
        this.bestTridentSlot = analysis.bestTridentSlot;
        this.bestWaterSlot = analysis.bestWaterSlot;
        System.arraycopy(analysis.bestArmorPieces, 0, this.bestArmorPieces, 0, this.bestArmorPieces.length);
        System.arraycopy(analysis.bestToolSlots, 0, this.bestToolSlots, 0, this.bestToolSlots.length);
    }

    private void applyStackLimit(InventoryAnalysis analysis, List<Integer> sourceSlots, int limit) {
        if (sourceSlots.isEmpty()) return;
        if (limit <= 0) {
            sourceSlots.forEach(analysis::addTrash);
            return;
        }

        List<LimitCandidate> candidates = new ArrayList<>();
        for (int slot : sourceSlots) {
            ItemStack stack = analysis.stacks.getOrDefault(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                candidates.add(new LimitCandidate(slot, stack.getCount(), this.isHotbarSlot(slot)));
            }
        }
        if (candidates.isEmpty()) return;

        candidates.sort((first, second) -> {
            int countCompare = Integer.compare(second.count(), first.count());
            if (countCompare != 0) return countCompare;
            if (first.hotbar() != second.hotbar()) return first.hotbar() ? -1 : 1;
            return Integer.compare(first.slot(), second.slot());
        });

        boolean[] reachable = new boolean[limit + 1];
        int[] previousSum = new int[limit + 1];
        int[] previousIndex = new int[limit + 1];
        Arrays.fill(previousSum, -1);
        Arrays.fill(previousIndex, -1);
        reachable[0] = true;

        for (int index = 0; index < candidates.size(); index++) {
            int count = candidates.get(index).count();
            if (count > limit) continue;
            for (int sum = limit - count; sum >= 0; sum--) {
                if (!reachable[sum] || reachable[sum + count]) continue;
                reachable[sum + count] = true;
                previousSum[sum + count] = sum;
                previousIndex[sum + count] = index;
            }
        }

        int bestSum = 0;
        for (int sum = limit; sum > 0; sum--) {
            if (reachable[sum]) {
                bestSum = sum;
                break;
            }
        }

        Set<Integer> keptSlots = new HashSet<>();
        for (int sum = bestSum; sum > 0; sum = previousSum[sum]) {
            int index = previousIndex[sum];
            if (index < 0) break;
            keptSlots.add(candidates.get(index).slot());
        }

        for (LimitCandidate candidate : candidates) {
            if (!keptSlots.contains(candidate.slot())) {
                analysis.addTrash(candidate.slot());
            }
        }
    }

    private boolean dropItem() {
        if (!this.canPerformDropAction() || this.trash.isEmpty()) return false;
        int slot = this.trash.remove(0);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 1, SlotActionType.THROW, mc.player);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        this.markDropAction();
        return true;
    }

    private boolean sortOffhand(boolean moveItems) {
        if (!this.handleOffhand.getValue()) return false;
        ScreenHandler handler = mc.player.playerScreenHandler;
        if (!this.hasOffhandSlot(handler)) return false;
        if (this.mergeOffhandItems(moveItems)) return true;
        if (this.offhandItem.is("gapple")) return this.sortGappleOffhand(moveItems);
        if (this.offhandItem.is("potion")) return this.sortPotionOffhand(moveItems);
        return false;
    }

    private boolean mergeOffhandItems(boolean moveItems) {
        ScreenHandler handler = mc.player.playerScreenHandler;
        if (!this.hasOffhandSlot(handler)) return false;
        ItemStack offhand = handler.getSlot(InventoryUtil.OFFHAND_SLOT).getStack();
        if (offhand.isEmpty() || offhand.getMaxCount() == 1 || offhand.getCount() >= offhand.getMaxCount()) {
            return false;
        }

        int source = this.findMatchingOffhandStack(offhand);
        if (source == -1) return false;
        if (moveItems && !this.startMerge(source, InventoryUtil.OFFHAND_SLOT)) return true;
        return true;
    }

    private boolean sortGappleOffhand(boolean moveItems) {
        if (this.gappleStackSlots.isEmpty()) return false;
        ScreenHandler handler = mc.player.playerScreenHandler;
        if (!this.hasOffhandSlot(handler)) return false;
        ItemStack offhand = handler.getSlot(InventoryUtil.OFFHAND_SLOT).getStack();
        if (offhand.isOf(Items.GOLDEN_APPLE) || offhand.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return false;

        int source = this.getLargestStackSlot(this.gappleStackSlots, InventoryUtil.OFFHAND_SLOT, false);
        if (source == -1 || source == InventoryUtil.OFFHAND_SLOT) return false;
        if (moveItems && !this.moveItemToSlot(InventoryUtil.OFFHAND_SLOT, source)) return true;
        return true;
    }

    private boolean sortHotbar(boolean moveItems) {
        if (this.handleSword.getValue() && this.moveToHotbar(this.bestSwordSlot, this.slotWeapon, moveItems)) return true;
        if (this.handleBow.getValue() && this.moveToHotbar(this.bestBowSlot, this.slotBow, moveItems)) return true;
        if (this.handleWater.getValue() && this.moveToHotbar(this.bestWaterSlot, this.slotWater, moveItems)) return true;

        if (this.handleGapple.getValue() && !this.gappleStackSlots.isEmpty()) {
            int bestGappleSlot = this.getLargestStackSlot(this.gappleStackSlots, this.hotbarSlot(this.slotGapple), false);
            if (this.handleGapple.getValue() && this.moveToHotbar(bestGappleSlot, this.slotGapple, moveItems)) return true;
        }

        int[] toolSlots = {
                this.hotbarSlot(this.slotPick),
                this.hotbarSlot(this.slotAxe)
        };
        for (int toolSlot : this.bestToolSlots) {
            if (toolSlot != -1) {
                int type = InventoryUtil.getToolType(mc.player.playerScreenHandler.getSlot(toolSlot).getStack());
                if (type != -1 && this.shouldSortToolType(type) && toolSlot != toolSlots[type]) {
                    if (moveItems) {
                        if (!this.moveItemToSlot(toolSlots[type], toolSlot)) return true;
                        this.bestToolSlots[type] = toolSlots[type];
                    }
                    return true;
                }
            }
        }

        if (this.handleBlock.getValue() && this.moveStackLargest(this.getMostBlocks(), this.slotBlock, BlockItem.class, moveItems)) return true;
        if (this.handleThrowable.getValue() && this.moveStackLargest(this.getMostThrowable(), this.slotThrowable, null, moveItems)) return true;
        return this.handlePearl.getValue() && this.moveToHotbar(this.getMostPearls(), this.slotPearl, moveItems);
    }

    private boolean sortPotionOffhand(boolean moveItems) {
        ScreenHandler handler = mc.player.playerScreenHandler;
        if (!this.hasOffhandSlot(handler)) return false;
        ItemStack offhand = handler.getSlot(InventoryUtil.OFFHAND_SLOT).getStack();
        if (InventoryUtil.isHealingPotion(offhand)) return false;

        int source = this.getSmallestStackSlot(this.healingPotionSlots);
        if (source == -1 || source == InventoryUtil.OFFHAND_SLOT) return false;
        if (moveItems && !this.moveItemToSlot(InventoryUtil.OFFHAND_SLOT, source)) return true;
        return true;
    }

    private boolean moveToHotbar(int sourceSlot, NumberValue targetValue, boolean moveItems) {
        if (sourceSlot == -1) return false;
        int targetSlot = this.hotbarSlot(targetValue);
        if (sourceSlot == targetSlot) return false;
        ScreenHandler handler = mc.player.playerScreenHandler;
        if (sourceSlot >= 0 && targetSlot >= 0 && sourceSlot < handler.slots.size() && targetSlot < handler.slots.size()) {
            ItemStack source = handler.getSlot(sourceSlot).getStack();
            ItemStack target = handler.getSlot(targetSlot).getStack();
            if (!source.isEmpty() && !target.isEmpty()
                    && source.getMaxCount() == 1
                    && target.getMaxCount() == 1
                    && ItemStack.areItemsAndComponentsEqual(source, target)) {
                return false;
            }
        }
        if (moveItems) {
            if (!this.moveItemToSlot(targetSlot, sourceSlot)) return true;
        }
        return true;
    }

    private boolean moveStackLargest(int sourceSlot, NumberValue targetValue, Class<?> itemClass, boolean moveItems) {
        if (sourceSlot == -1) return false;
        int targetSlot = this.hotbarSlot(targetValue);
        if (sourceSlot == targetSlot) return false;
        ItemStack target = mc.player.playerScreenHandler.getSlot(targetSlot).getStack();
        ItemStack source = mc.player.playerScreenHandler.getSlot(sourceSlot).getStack();
        boolean replace = target.isEmpty()
                || itemClass != null && !itemClass.isInstance(target.getItem())
                || itemClass == null && !InventoryUtil.isThrowable(target)
                || target.getCount() < source.getCount();
        if (!replace) return false;
        if (moveItems) {
            if (!this.moveItemToSlot(targetSlot, sourceSlot)) return true;
        }
        return true;
    }

    private boolean equipArmor(boolean moveItems) {
        if (!this.handleArmor.getValue()) return false;
        ScreenHandler handler = mc.player.playerScreenHandler;

        for (EquipmentSlot equipmentSlot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            int armorSlot = InventoryUtil.getArmorSlot(equipmentSlot);
            if (armorSlot == -1) continue;

            ItemStack equipped = handler.getSlot(armorSlot).getStack();
            if (!equipped.isEmpty() && !InventoryUtil.isBestArmor(handler, equipped)) {
                if (moveItems) {
                    if (!this.canPerformAction()) return true;
                    this.throwSlotAsAction(armorSlot);
                }
                return true;
            }
        }

        for (int piece : this.bestArmorPieces) {
            if (piece == -1) continue;
            EquipmentSlot slot = InventoryUtil.getArmorEquipmentSlot(handler.getSlot(piece).getStack());
            int armorSlot = InventoryUtil.getArmorSlot(slot);
            if (armorSlot == -1) continue;
            ItemStack equipped = handler.getSlot(armorSlot).getStack();
            if (equipped.isEmpty()) {
                if (moveItems) {
                    if (!this.canPerformAction()) return true;
                    mc.interactionManager.clickSlot(handler.syncId, piece, 0, SlotActionType.QUICK_MOVE, mc.player);
                    this.markAction();
                }
                return true;
            }
        }
        return false;
    }

    private boolean mergeInventoryItems(boolean moveItems) {
        MergeChoice choice = this.findBestMergeChoice();
        if (choice == null) return false;
        if (moveItems && !this.startMerge(choice.sourceSlot(), choice.targetSlot())) return true;
        return true;
    }

    private MergeChoice findBestMergeChoice() {
        ScreenHandler handler = mc.player.playerScreenHandler;
        MergeChoice best = null;
        for (int targetSlot = InventoryUtil.INVENTORY_START; targetSlot <= InventoryUtil.HOTBAR_END && targetSlot < handler.slots.size(); targetSlot++) {
            if (this.trash.contains(targetSlot)) continue;
            ItemStack target = handler.getSlot(targetSlot).getStack();
            if (target.isEmpty() || target.getMaxCount() <= 1 || target.getCount() >= target.getMaxCount() || this.isServerItem(target)) {
                continue;
            }

            int available = target.getMaxCount() - target.getCount();
            for (int sourceSlot = InventoryUtil.INVENTORY_START; sourceSlot <= InventoryUtil.HOTBAR_END && sourceSlot < handler.slots.size(); sourceSlot++) {
                if (sourceSlot == targetSlot || this.trash.contains(sourceSlot)) continue;
                ItemStack source = handler.getSlot(sourceSlot).getStack();
                if (source.isEmpty()
                        || source.getCount() >= source.getMaxCount()
                        || this.isServerItem(source)
                        || !ItemStack.areItemsAndComponentsEqual(source, target)) {
                    continue;
                }

                int moved = Math.min(source.getCount(), available);
                if (moved <= 0) continue;
                MergeChoice choice = new MergeChoice(sourceSlot, targetSlot, moved,
                        source.getCount() <= available, moved == available, source.getCount(),
                        Math.abs(sourceSlot - targetSlot));
                if (this.isBetterMergeChoice(choice, best)) {
                    best = choice;
                }
            }
        }
        return best;
    }

    private boolean isBetterMergeChoice(MergeChoice choice, MergeChoice current) {
        if (current == null) return true;
        int choiceExact = choice.sourceEmpties() && choice.targetFills() ? 1 : 0;
        int currentExact = current.sourceEmpties() && current.targetFills() ? 1 : 0;
        if (choiceExact != currentExact) return choiceExact > currentExact;
        if (choice.sourceEmpties() != current.sourceEmpties()) return choice.sourceEmpties();
        if (choice.targetFills() != current.targetFills()) return choice.targetFills();
        if (choice.moved() != current.moved()) return choice.moved() > current.moved();
        if (choice.sourceCount() != current.sourceCount()) return choice.sourceCount() < current.sourceCount();
        if (choice.distance() != current.distance()) return choice.distance() < current.distance();
        if (choice.sourceSlot() != current.sourceSlot()) return choice.sourceSlot() < current.sourceSlot();
        return choice.targetSlot() < current.targetSlot();
    }

    private boolean startMerge(int sourceSlot, int targetSlot) {
        if (this.pendingMerge == null) {
            this.pendingMerge = new PendingMerge(sourceSlot, targetSlot, sourceSlot, 0);
        }
        return this.processPendingMerge();
    }

    private boolean processPendingMerge() {
        if (this.pendingMerge == null) return false;
        if (!this.canPerformMergeClick()) return true;

        ScreenHandler handler = mc.player.playerScreenHandler;
        PendingMerge merge = this.pendingMerge;
        if (!this.validMergeSlot(handler, merge.sourceSlot()) || !this.validMergeSlot(handler, merge.targetSlot())) {
            this.pendingMerge = null;
            return true;
        }

        if (merge.stage() == 0) {
            ItemStack source = handler.getSlot(merge.sourceSlot()).getStack();
            ItemStack target = handler.getSlot(merge.targetSlot()).getStack();
            if (source.isEmpty() || target.isEmpty()
                    || !ItemStack.areItemsAndComponentsEqual(source, target)
                    || target.getCount() >= target.getMaxCount()) {
                this.pendingMerge = null;
                return true;
            }
            this.clickMergeSlot(merge.sourceSlot());
            this.pendingMerge = new PendingMerge(merge.sourceSlot(), merge.targetSlot(), merge.returnSlot(), 1);
            return true;
        }

        if (merge.stage() == 1) {
            if (handler.getCursorStack().isEmpty()) {
                this.pendingMerge = null;
                return true;
            }
            this.clickMergeSlot(merge.targetSlot());
            this.pendingMerge = handler.getCursorStack().isEmpty()
                    ? null
                    : new PendingMerge(merge.sourceSlot(), merge.targetSlot(), merge.returnSlot(), 2);
            return true;
        }

        if (!handler.getCursorStack().isEmpty() && this.validMergeSlot(handler, merge.returnSlot())) {
            this.clickMergeSlot(merge.returnSlot());
        } else {
            this.pendingMerge = null;
        }
        this.pendingMerge = null;
        return true;
    }

    private boolean validMergeSlot(ScreenHandler handler, int slot) {
        return handler != null && slot >= 0 && slot < handler.slots.size();
    }

    private boolean hasOffhandSlot(ScreenHandler handler) {
        return this.validMergeSlot(handler, InventoryUtil.OFFHAND_SLOT);
    }

    private void clickMergeSlot(int slot) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        this.markMergeAction();
    }

    private int getMostBlocks() {
        int count = 0;
        int bestSlot = -1;
        ScreenHandler handler = mc.player.playerScreenHandler;
        for (int i = InventoryUtil.INVENTORY_START; i <= InventoryUtil.HOTBAR_END; i++) {
            if (this.trash.contains(i)) continue;
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem && InventoryUtil.isValidBlock(stack) && stack.getCount() > count && !this.isServerItem(stack)) {
                count = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int getMostThrowable() {
        int count = 0;
        int bestSlot = -1;
        ScreenHandler handler = mc.player.playerScreenHandler;
        for (int i = InventoryUtil.INVENTORY_START; i <= InventoryUtil.HOTBAR_END; i++) {
            if (this.trash.contains(i)) continue;
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && InventoryUtil.isThrowable(stack) && stack.getCount() > count && !this.isServerItem(stack)) {
                count = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int getMostPearls() {
        int count = 0;
        int bestSlot = -1;
        ScreenHandler handler = mc.player.playerScreenHandler;
        for (int i = InventoryUtil.INVENTORY_START; i <= InventoryUtil.HOTBAR_END; i++) {
            if (this.trash.contains(i)) continue;
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.isOf(Items.ENDER_PEARL) && stack.getCount() > count) {
                count = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private boolean moveItemToSlot(int targetSlot, int sourceSlot) {
        ScreenHandler handler = mc.player.playerScreenHandler;
        if (sourceSlot >= 0 && targetSlot >= 0 && sourceSlot < handler.slots.size() && targetSlot < handler.slots.size()) {
            ItemStack target = handler.getSlot(targetSlot).getStack();
            ItemStack source = handler.getSlot(sourceSlot).getStack();
            if (!source.isEmpty() && !target.isEmpty()
                    && source.getCount() < source.getMaxCount()
                    && ItemStack.areItemsAndComponentsEqual(target, source)
                    && target.getCount() < target.getMaxCount()) {
                return this.startMerge(sourceSlot, targetSlot);
            }
        }
        if (!this.canPerformAction()) return false;
        this.putItemInSlot(targetSlot, sourceSlot);
        this.markAction();
        return true;
    }

    private void putItemInSlot(int targetSlot, int sourceSlot) {
        ScreenHandler handler = mc.player.playerScreenHandler;
        if (sourceSlot == targetSlot || sourceSlot < 0 || targetSlot < 0
                || sourceSlot >= handler.slots.size() || targetSlot >= handler.slots.size()) {
            return;
        }
        ItemStack source = handler.getSlot(sourceSlot).getStack();
        if (source.isEmpty()) return;
        int button = targetSlot == InventoryUtil.OFFHAND_SLOT ? 40 : targetSlot - InventoryUtil.HOTBAR_START;
        mc.interactionManager.clickSlot(handler.syncId, sourceSlot, button, SlotActionType.SWAP, mc.player);
    }

    private int findMatchingOffhandStack(ItemStack offhand) {
        ScreenHandler handler = mc.player.playerScreenHandler;
        for (int slot = InventoryUtil.INVENTORY_START; slot <= InventoryUtil.HOTBAR_END && slot < handler.slots.size(); slot++) {
            if (slot == InventoryUtil.OFFHAND_SLOT || this.trash.contains(slot)) continue;
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty() && !this.isServerItem(stack) && ItemStack.areItemsAndComponentsEqual(stack, offhand)) {
                return slot;
            }
        }
        return -1;
    }

    private int getLargestStackSlot(List<Integer> sourceSlots, int preferredSlot, boolean includeOffhand) {
        int bestSlot = -1;
        int bestCount = -1;
        for (int slot : sourceSlots) {
            if (!includeOffhand && slot == InventoryUtil.OFFHAND_SLOT) continue;
            ItemStack stack = mc.player.playerScreenHandler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;
            int count = stack.getCount();
            if (count > bestCount || count == bestCount && this.isPreferredSlot(bestSlot, slot, preferredSlot)) {
                bestCount = count;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int getSmallestStackSlot(List<Integer> sourceSlots) {
        int bestSlot = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int slot : sourceSlots) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;
            int count = stack.getCount();
            if (count < bestCount || count == bestCount && slot < bestSlot) {
                bestCount = count;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int selectBestSlot(int currentSlot, int candidateSlot, int preferredSlot, StackScorer scorer) {
        if (currentSlot == -1) return candidateSlot;
        if (this.shouldPreferSlot(currentSlot, candidateSlot, preferredSlot, scorer)) {
            this.addTrashCandidate(currentSlot);
            return candidateSlot;
        }
        this.addTrashCandidate(candidateSlot);
        return currentSlot;
    }

    private int selectBestSlot(InventoryAnalysis analysis, int currentSlot, int candidateSlot, int preferredSlot, StackScorer scorer) {
        if (currentSlot == -1) return candidateSlot;
        if (this.shouldPreferSlot(analysis, currentSlot, candidateSlot, preferredSlot, scorer)) {
            analysis.addTrashCandidate(currentSlot);
            return candidateSlot;
        }
        analysis.addTrashCandidate(candidateSlot);
        return currentSlot;
    }

    private boolean shouldPreferSlot(int currentSlot, int candidateSlot, int preferredSlot, StackScorer scorer) {
        ItemStack current = mc.player.playerScreenHandler.getSlot(currentSlot).getStack();
        ItemStack candidate = mc.player.playerScreenHandler.getSlot(candidateSlot).getStack();
        double currentScore = scorer.score(current);
        double candidateScore = scorer.score(candidate);
        if (candidateScore != currentScore) return candidateScore > currentScore;
        return this.isPreferredSlot(currentSlot, candidateSlot, preferredSlot);
    }

    private boolean shouldPreferSlot(InventoryAnalysis analysis, int currentSlot, int candidateSlot, int preferredSlot, StackScorer scorer) {
        ItemStack current = analysis.stacks.getOrDefault(currentSlot, ItemStack.EMPTY);
        ItemStack candidate = analysis.stacks.getOrDefault(candidateSlot, ItemStack.EMPTY);
        double currentScore = scorer.score(current);
        double candidateScore = scorer.score(candidate);
        if (candidateScore != currentScore) return candidateScore > currentScore;
        return this.isPreferredSlot(currentSlot, candidateSlot, preferredSlot);
    }

    private void addTrashCandidate(int slot) {
        if (slot >= InventoryUtil.INVENTORY_START && slot <= InventoryUtil.HOTBAR_END) {
            this.addTrash(slot);
        }
    }

    private boolean isPreferredSlot(int currentSlot, int candidateSlot, int preferredSlot) {
        if (currentSlot == -1) return true;
        if (candidateSlot == preferredSlot && currentSlot != preferredSlot) return true;
        if (currentSlot == preferredSlot && candidateSlot != preferredSlot) return false;
        boolean currentHotbar = this.isHotbarSlot(currentSlot);
        boolean candidateHotbar = this.isHotbarSlot(candidateSlot);
        if (candidateHotbar != currentHotbar) return candidateHotbar;
        return candidateSlot < currentSlot;
    }

    private int preferredToolSlot(int toolType) {
        if (toolType == 0) return this.hotbarSlot(this.slotPick);
        if (toolType == 1) return this.hotbarSlot(this.slotAxe);
        return -1;
    }

    private boolean shouldSortToolType(int toolType) {
        if (toolType == 0) return this.handlePickaxe.getValue();
        if (toolType == 1) return this.handleAxe.getValue();
        return false;
    }

    private boolean isManagedEquipment(ItemStack stack) {
        return this.handleSword.getValue() && InventoryUtil.isSword(stack)
                || this.handleAxe.getValue() && InventoryUtil.isAxe(stack)
                || this.handlePickaxe.getValue() && InventoryUtil.isTool(stack) && !InventoryUtil.isAxe(stack)
                || this.handleArmor.getValue() && InventoryUtil.isArmor(stack)
                || this.handleBow.getValue() && (stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW));
    }

    private boolean isHotbarSlot(int slot) {
        return slot >= InventoryUtil.HOTBAR_START && slot <= InventoryUtil.HOTBAR_END;
    }

    private void throwSlotAsAction(int slot) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 1, SlotActionType.THROW, mc.player);
        this.markAction();
    }

    private int hotbarSlot(NumberValue value) {
        return value.getValue().intValue() + 35;
    }

    private int armorIndex(EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD) return 0;
        if (slot == EquipmentSlot.CHEST) return 1;
        if (slot == EquipmentSlot.LEGS) return 2;
        if (slot == EquipmentSlot.FEET) return 3;
        return -1;
    }

    private boolean canPerformAction() {
        return this.actionTicks >= this.actionDelay.getValue().intValue();
    }

    private boolean canPerformMergeClick() {
        return this.mergeTicks >= Math.max(this.actionDelay.getValue().intValue(), MERGE_DELAY);
    }

    private boolean canPerformDropAction() {
        return this.dropTicks >= this.dropDelay.getValue().intValue();
    }

    private void tickDelays() {
        this.actionTicks++;
        this.dropTicks++;
        this.mergeTicks++;
    }

    private void markAction() {
        this.actionTicks = 0;
        this.mergeTicks = 0;
        this.updateInventorySnapshot();
    }

    private void markMergeAction() {
        this.actionTicks = 0;
        this.mergeTicks = 0;
        this.updateInventorySnapshot();
    }

    private void markDropAction() {
        this.dropTicks = 0;
        this.updateInventorySnapshot();
    }

    private boolean inventoryChangedExternally() {
        InventorySnapshot current = this.captureInventorySnapshot();
        if (this.lastInventorySnapshot == null) {
            this.lastInventorySnapshot = current;
            return false;
        }
        if (this.lastInventorySnapshot.equals(current)) {
            return false;
        }

        this.pendingMerge = null;
        this.clear();
        this.actionTicks = 0;
        this.dropTicks = 0;
        this.mergeTicks = 0;
        this.lastInventorySnapshot = current;
        return true;
    }

    private void updateInventorySnapshot() {
        this.lastInventorySnapshot = this.captureInventorySnapshot();
    }

    private InventorySnapshot captureInventorySnapshot() {
        ScreenHandler handler = mc.player == null ? null : mc.player.playerScreenHandler;
        if (handler == null) return InventorySnapshot.empty();

        List<ItemStack> stacks = new ArrayList<>(handler.slots.size());
        for (int i = 0; i < handler.slots.size(); i++) {
            stacks.add(handler.getSlot(i).getStack().copy());
        }
        return new InventorySnapshot(handler.syncId, handler.getCursorStack().copy(), stacks);
    }

    private void clear() {
        this.trash.clear();
        this.bestSwordSlot = -1;
        this.bestBowSlot = -1;
        this.bestCrossbowSlot = -1;
        this.bestMaceSlot = -1;
        this.bestTridentSlot = -1;
        this.bestWaterSlot = -1;
        this.gappleStackSlots.clear();
        this.healingPotionSlots.clear();
        Arrays.fill(this.bestArmorPieces, -1);
        Arrays.fill(this.bestToolSlots, -1);
    }

    private void addTrash(int slot) {
        if (!this.trash.contains(slot)) this.trash.add(slot);
    }

    private boolean isServerItem(ItemStack stack) {
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        for (String serverItem : this.serverItems) {
            if (name.contains(serverItem.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    @Override
    protected void onEnable() {
        resetTrashCounters();
        this.startDelayTicks = 0;
        this.actionTicks = 0;
        this.dropTicks = 0;
        this.mergeTicks = 0;
        this.pendingMerge = null;
        this.lastInventorySnapshot = null;
    }

    @Override
    protected void onDisable() {
        this.clear();
        resetTrashCounters();
        this.startDelayTicks = 0;
        this.actionTicks = 0;
        this.dropTicks = 0;
        this.mergeTicks = 0;
        this.pendingMerge = null;
        this.lastInventorySnapshot = null;
    }

    @Override
    public String getDisplayName() {
        return "Inv Manager";
    }

    private record LimitCandidate(int slot, int count, boolean hotbar) {
    }

    private record AnalyzedSlot(int slot, ItemStack stack) {
    }

    private static final class InventoryAnalysis {
        private final Set<Integer> trash = new LinkedHashSet<>();
        private final Map<Integer, ItemStack> stacks = new HashMap<>();
        private final List<Integer> gappleStackSlots = new ArrayList<>();
        private final List<Integer> healingPotionSlots = new ArrayList<>();
        private final int[] bestArmorPieces = {-1, -1, -1, -1};
        private final int[] bestToolSlots = {-1, -1, -1};
        private int bestSwordSlot = -1;
        private int bestBowSlot = -1;
        private int bestCrossbowSlot = -1;
        private int bestMaceSlot = -1;
        private int bestTridentSlot = -1;
        private int bestWaterSlot = -1;
        private boolean aborted;

        private void addTrash(int slot) {
            if (slot >= InventoryUtil.INVENTORY_START || slot >= CHEST_SLOT_OFFSET) {
                this.trash.add(slot);
            }
        }

        private void addTrashCandidate(int slot) {
            if (slot >= InventoryUtil.INVENTORY_START && slot <= InventoryUtil.HOTBAR_END || slot >= CHEST_SLOT_OFFSET) {
                this.trash.add(slot);
            }
        }
    }

    private record MergeChoice(int sourceSlot, int targetSlot, int moved, boolean sourceEmpties,
                               boolean targetFills, int sourceCount, int distance) {
    }

    private record PendingMerge(int sourceSlot, int targetSlot, int returnSlot, int stage) {
    }

    private record InventorySnapshot(int syncId, ItemStack cursorStack, List<ItemStack> stacks) {
        private static InventorySnapshot empty() {
            return new InventorySnapshot(-1, ItemStack.EMPTY, Collections.emptyList());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof InventorySnapshot snapshot)) return false;
            if (this.syncId != snapshot.syncId || !ItemStack.areEqual(this.cursorStack, snapshot.cursorStack)
                    || this.stacks.size() != snapshot.stacks.size()) {
                return false;
            }
            for (int i = 0; i < this.stacks.size(); i++) {
                if (!ItemStack.areEqual(this.stacks.get(i), snapshot.stacks.get(i))) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.syncId, this.stacks.size());
        }
    }

    @FunctionalInterface
    private interface StackScorer {
        double score(ItemStack stack);
    }

}

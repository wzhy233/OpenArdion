package io.github.wzhy233.ardion.module.impl.player;

import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestStealer extends Module {
    public static final Set<BlockPos> openedByPlayerChests = ConcurrentHashMap.newKeySet();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final Random random = new Random();
    private final BooleanValue ignoreTrash = this.registerValue(new BooleanValue("ignore-trash", this, true));
    private final NumberValue startDelayTicks = this.registerValue(new NumberValue("start-delay", this, 2.0D, 0.0D, 10.0D, true));
    private final NumberValue actionDelayTicks = this.registerValue(new NumberValue("action-delay", this, 2.0D, 0.0D, 10.0D, true));
    private final NumberValue closeDelayTicks = this.registerValue(new NumberValue("close-delay", this, 1.0D, 0.0D, 10.0D, true));

    private final List<StealSlot> targetSlots = new ArrayList<>();
    private boolean started;
    private int startDelayTickCounter;
    private int actionTickCounter;
    private int closeTickCounter;

    public ChestStealer() {
        super("chest-stealer", ModuleCategory.PLAYER);
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE) return;

        if (mc.world == null || mc.player == null || mc.interactionManager == null || !mc.player.isAlive()) {
            this.setEnabled(false);
            return;
        }

        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest)) {
            this.reset();
            return;
        }

        openedByPlayerChests.add(mc.player.getBlockPos());

        if (!this.started) {
            this.targetSlots.clear();
            this.startDelayTickCounter = 0;
            this.actionTickCounter = 0;
            this.closeTickCounter = 0;
            this.calculateTargetSlots(chest);
            this.started = true;
        }

        if (this.startDelayTickCounter < this.startDelayTicks.getValue().intValue()) {
            this.startDelayTickCounter++;
            return;
        }

        if (this.targetSlots.isEmpty()) {
            if (this.closeTickCounter < this.closeDelayTicks.getValue().intValue()) {
                this.closeTickCounter++;
                return;
            }

            mc.player.closeHandledScreen();
            this.reset();
            return;
        }

        if (this.actionTickCounter < this.actionDelayTicks.getValue().intValue()) {
            this.actionTickCounter++;
            return;
        }

        this.performStealAction(chest);
        this.actionTickCounter = 0;
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    private void performStealAction(ScreenHandler chest) {
        if (this.targetSlots.isEmpty() || mc.player == null || mc.interactionManager == null) {
            return;
        }

        StealSlot stealSlot = this.targetSlots.remove(0);
        if (stealSlot.slotIndex() < 0 || stealSlot.slotIndex() >= chest.slots.size()) {
            return;
        }

        Slot sourceSlot = chest.getSlot(stealSlot.slotIndex());
        if (!sourceSlot.hasStack()) {
            return;
        }

        mc.interactionManager.clickSlot(chest.syncId, stealSlot.slotIndex(), 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    private void calculateTargetSlots(ScreenHandler chest) {
        List<StealSlot> candidates = new ArrayList<>();
        int containerSize = this.containerSize(chest);
        Set<Integer> trashSlots = this.ignoreTrash.getValue()
                ? InvManager.getTrashChestSlots(mc.player.playerScreenHandler, chest, containerSize)
                : Collections.emptySet();

        for (int i = 0; i < containerSize; i++) {
            Slot slot = chest.getSlot(i);
            if (!slot.hasStack()) continue;

            ItemStack stack = slot.getStack();
            if (trashSlots.contains(i)) continue;
            if (stack.isOf(Items.FISHING_ROD) && this.shouldDropFishingRod()) continue;

            candidates.add(new StealSlot(i, stack.copy()));
        }

        Collections.shuffle(candidates, this.random);
        this.targetSlots.addAll(candidates);
    }

    private int containerSize(ScreenHandler chest) {
        return Math.max(0, chest.slots.size() - 36);
    }

    private boolean shouldDropFishingRod() {
        InvManager invManager = ModuleManager.getModule(InvManager.class);
        return invManager != null && invManager.dropFishingRod.getValue();
    }

    private void reset() {
        this.started = false;
        this.targetSlots.clear();
        this.startDelayTickCounter = 0;
        this.actionTickCounter = 0;
        this.closeTickCounter = 0;
    }

    private record StealSlot(int slotIndex, ItemStack itemStack) {
    }
}

package io.github.wzhy233.ardion.module.impl.player;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.render.UpdateHeldItemEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoTool extends Module {
    private final NumberValue switchDelay = this.registerValue(new NumberValue("switch-delay", this, 0.0D, 0.0D, 1000.0D, true));
    private final BooleanValue switchback = this.registerValue(new BooleanValue("switchback", this, true));
    private final NumberValue switchbackDelay = this.registerValue(new NumberValue("switchback-delay", this, 250.0D, 0.0D, 2000.0D, true, () -> switchback.getValue()));
    private final BooleanValue randomize = this.registerValue(new BooleanValue("randomize", this, false));
    private final BooleanValue onlyWhileSneaking = this.registerValue(new BooleanValue("only-while-sneaking", this, false));

    private int previousSlot = -1;
    private int switchedSlot = -1;
    private int targetSlot = -1;
    private long targetSince;
    private long switchbackSince;
    private long targetRandomDelay;
    private long switchbackRandomDelay;
    private boolean switched;
    private boolean wasAttackPressed;

    public AutoTool() {
        super("auto-tool", ModuleCategory.PLAYER);
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST) {
            return;
        }

        MinecraftClient client = event.getClient();
        if (client.player == null || client.world == null || client.interactionManager == null) {
            reset(client);
            return;
        }

        if (switched && client.player.getInventory().getSelectedSlot() != switchedSlot) {
            resetState();
        }

        boolean attackPressed = client.options.attackKey.isPressed();
        boolean useBlocked = isUsingItemOrShield(client.player);
        if (!attackPressed || useBlocked) {
            if (wasAttackPressed) {
                switchbackSince = System.currentTimeMillis();
                switchbackRandomDelay = randomDelay();
            }
            wasAttackPressed = false;
            if (switchback.getValue()) switchBack(client);
            return;
        }
        wasAttackPressed = true;

        if (!canSwitch(client)) {
            targetSlot = -1;
            targetSince = 0L;
            return;
        }

        BlockPos pos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        int bestSlot = findBestSlot(client, state);
        if (bestSlot == -1 || bestSlot == client.player.getInventory().getSelectedSlot()) {
            targetSlot = -1;
            targetSince = 0L;
            return;
        }

        switchbackSince = 0L;
        if (bestSlot != targetSlot) {
            targetSlot = bestSlot;
            targetSince = System.currentTimeMillis();
            targetRandomDelay = randomDelay();
        }

        if (System.currentTimeMillis() - targetSince < switchDelay.getValue().longValue() + targetRandomDelay) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        if (!switched) {
            previousSlot = inventory.getSelectedSlot();
        }
        inventory.setSelectedSlot(bestSlot);
        switchedSlot = bestSlot;
        switched = true;
    }

    @Override
    protected void onDisable() {
        if (switchback.getValue()) switchBack(MinecraftClient.getInstance());
    }

    @Listener
    private void onUpdateHeldItem(UpdateHeldItemEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (event.getHand() != Hand.MAIN_HAND
                || client.player == null
                || !switched
                || previousSlot < 0
                || previousSlot > 8
                || client.player.getInventory().getSelectedSlot() != switchedSlot) {
            return;
        }

        event.setItemStack(client.player.getInventory().getStack(previousSlot));
    }

    private boolean canSwitch(MinecraftClient client) {
        if (!(client.crosshairTarget instanceof BlockHitResult blockHitResult)
                || client.crosshairTarget.getType() != HitResult.Type.BLOCK
                || onlyWhileSneaking.getValue() && !client.player.isSneaking()) {
            return false;
        }

        Block block = client.world.getBlockState(blockHitResult.getBlockPos()).getBlock();
        return !isHoldingCurrencyItem(client.player) || !isChest(block);
    }

    private int findBestSlot(MinecraftClient client, BlockState state) {
        PlayerInventory inventory = client.player.getInventory();
        int currentSlot = inventory.getSelectedSlot();
        float bestSpeed = calculateMiningSpeed(inventory.getStack(currentSlot), state);
        int bestSlot = currentSlot;

        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !stack.canMine(state, client.world, ((BlockHitResult) client.crosshairTarget).getBlockPos(), client.player)) {
                continue;
            }

            float speed = calculateMiningSpeed(stack, state);
            if (speed > bestSpeed || speed == bestSpeed && stack.isSuitableFor(state) && !inventory.getStack(bestSlot).isSuitableFor(state)) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        return bestSlot == currentSlot ? -1 : bestSlot;
    }

    private float calculateMiningSpeed(ItemStack stack, BlockState state) {
        float baseSpeed = stack.getMiningSpeedMultiplier(state);
        if (baseSpeed <= 1.0F) {
            return baseSpeed;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            Registry<Enchantment> enchantmentRegistry = client.world
                    .getRegistryManager()
                    .getOrThrow(RegistryKeys.ENCHANTMENT);
            RegistryEntry.Reference<Enchantment> efficiency = enchantmentRegistry
                    .getOrThrow(Enchantments.EFFICIENCY);

            if (efficiency != null) {
                int efficiencyLevel = EnchantmentHelper.getLevel(efficiency, stack);
                if (efficiencyLevel > 0) {
                    baseSpeed += baseSpeed * efficiencyLevel * efficiencyLevel * 0.01F + 1.0F;
                }
            }
        }

        return baseSpeed;
    }

    private void switchBack(MinecraftClient client) {
        targetSlot = -1;
        targetSince = 0L;
        if (!switched || previousSlot == -1 || client.player == null) {
            resetState();
            return;
        }

        if (client.player.getInventory().getSelectedSlot() != switchedSlot) {
            resetState();
            return;
        }

        if (switchbackSince == 0L) {
            switchbackSince = System.currentTimeMillis();
            switchbackRandomDelay = randomDelay();
        }
        if (System.currentTimeMillis() - switchbackSince < switchbackDelay.getValue().longValue() + switchbackRandomDelay) {
            return;
        }

        if (PlayerInventory.isValidHotbarIndex(previousSlot)) {
            client.player.getInventory().setSelectedSlot(previousSlot);
        }
        resetState();
    }

    private void reset(MinecraftClient client) {
        if (client != null && switched && switchback.getValue()) {
            switchBack(client);
            return;
        }
        resetState();
    }

    private void resetState() {
        previousSlot = -1;
        switchedSlot = -1;
        targetSlot = -1;
        targetSince = 0L;
        switchbackSince = 0L;
        targetRandomDelay = 0L;
        switchbackRandomDelay = 0L;
        switched = false;
        wasAttackPressed = false;
    }

    private boolean isUsingItemOrShield(ClientPlayerEntity player) {
        return player != null && (player.isUsingItem() || player.getActiveItem().isOf(Items.SHIELD));
    }

    private boolean isHoldingCurrencyItem(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        return stack.isOf(Items.GOLD_INGOT)
                || stack.isOf(Items.IRON_INGOT)
                || stack.isOf(Items.DIAMOND)
                || stack.isOf(Items.EMERALD);
    }

    private boolean isChest(Block block) {
        return block instanceof AbstractChestBlock || block instanceof EnderChestBlock;
    }

    private long randomDelay() {
        return randomize.getValue() ? ThreadLocalRandom.current().nextLong(25L, 101L) : 0L;
    }
}

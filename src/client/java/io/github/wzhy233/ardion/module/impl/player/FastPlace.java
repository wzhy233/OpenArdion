package io.github.wzhy233.ardion.module.impl.player;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.mixin.client.MinecraftClientAccessor;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

public final class FastPlace extends Module {
    private final NumberValue delay = this.registerValue(new NumberValue("delay", this, 0.0D, 0.0D, 4.0D, true));
    private final BooleanValue blocksOnly = this.registerValue(new BooleanValue("blocks-only", this, true));

    public FastPlace() {
        super("fast-place", ModuleCategory.PLAYER);
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST) {
            return;
        }

        MinecraftClient client = event.getClient();
        if (client.player == null || client.interactionManager == null) {
            return;
        }
        if (this.blocksOnly.getValue() && !this.isHoldingBlock(client)) {
            return;
        }

        ((MinecraftClientAccessor) client).setItemUseCooldown(this.delay.getValue().intValue());
    }

    private boolean isHoldingBlock(MinecraftClient client) {
        ItemStack mainHand = client.player.getMainHandStack();
        ItemStack offHand = client.player.getOffHandStack();
        return this.isBlock(mainHand) || this.isBlock(offHand);
    }

    private boolean isBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return !(block instanceof BedBlock) && !(block instanceof AbstractSkullBlock);
    }
}

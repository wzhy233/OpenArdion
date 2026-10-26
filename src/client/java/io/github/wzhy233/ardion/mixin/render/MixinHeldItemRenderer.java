package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.event.impl.render.UpdateHeldItemEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(HeldItemRenderer.class)
public abstract class MixinHeldItemRenderer {
    @ModifyVariable(method = "updateHeldItems", at = @At(value = "STORE"), ordinal = 0)
    private ItemStack ardion$updateMainHeldItem(ItemStack stack) {
        return this.ardion$getSpoofedHeldItem(Hand.MAIN_HAND, stack);
    }

    @ModifyVariable(method = "updateHeldItems", at = @At(value = "STORE"), ordinal = 1)
    private ItemStack ardion$updateOffHeldItem(ItemStack stack) {
        return this.ardion$getSpoofedHeldItem(Hand.OFF_HAND, stack);
    }

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack ardion$updateHeldItem(
            ItemStack stack,
            AbstractClientPlayerEntity player,
            float tickProgress,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light
    ) {
        return this.ardion$getSpoofedHeldItem(hand, stack);
    }

    private ItemStack ardion$getSpoofedHeldItem(Hand hand, ItemStack stack) {
        UpdateHeldItemEvent event = EventManager.post(new UpdateHeldItemEvent(hand, stack));
        return event.getItemStack();
    }
}

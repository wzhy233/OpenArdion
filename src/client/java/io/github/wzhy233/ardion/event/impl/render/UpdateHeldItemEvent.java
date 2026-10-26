package io.github.wzhy233.ardion.event.impl.render;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class UpdateHeldItemEvent extends Event {
    private final Hand hand;
    private ItemStack itemStack;

    public UpdateHeldItemEvent(Hand hand, ItemStack itemStack) {
        this.hand = hand;
        this.itemStack = itemStack;
    }

    public Hand getHand() {
        return this.hand;
    }

    public ItemStack getItemStack() {
        return this.itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack == null ? ItemStack.EMPTY : itemStack;
    }
}

package io.github.wzhy233.ardion.module.impl.player;

import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoTotem extends Module {

    private enum State { IDLE, EQUIPPING }

    private State state = State.IDLE;
    private int step;
    private long nextAction;

    private int totemSlot = -1;
    private boolean opened = false;

    public AutoTotem() {
        super("auto-totem", ModuleCategory.PLAYER);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        ClientPlayerEntity player = mc.player;
        PlayerInventory inventory = player.getInventory();
        if (inventory.getStack(PlayerInventory.OFF_HAND_SLOT).isOf(Items.TOTEM_OF_UNDYING)) {
            reset();
            return;
        }

        if (player.isUsingItem() || mc.interactionManager.isBreakingBlock() || player.isCreative() || player.isSpectator()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;

        switch (state) {
            case IDLE:
                totemSlot = findTotem(inventory);
                if (totemSlot == -1) return;

                state = State.EQUIPPING;
                step = 0;
                nextAction = System.currentTimeMillis();
                break;

            case EQUIPPING:
                if (System.currentTimeMillis() < nextAction) return;

                equip(mc, player);

                step++;
                nextAction = System.currentTimeMillis() + 50;
                if (step >= 3) {
                    reset();
                }
                break;
        }
    }

    private int findTotem(PlayerInventory inv) {
        var mainStacks = inv.getMainStacks();
        for (int i = 0; i < mainStacks.size(); i++) {
            if (mainStacks.get(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i < 9 ? 36 + i : i;
            }
        }
        return -1;
    }

    private void equip(MinecraftClient mc, ClientPlayerEntity player) {
        int syncId = player.playerScreenHandler.syncId;

        switch (step) {
            case 0:
                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    mc.setScreen(new InventoryScreen(player));
                    opened = true;
                }
                break;
            case 1:
                mc.interactionManager.clickSlot(syncId, totemSlot, 0, SlotActionType.PICKUP, player);
                mc.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, player);
                break;
            case 2:
                if (opened && mc.currentScreen != null) {
                    mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    mc.setScreen(null);
                }
                break;
        }
    }

    private void reset() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (opened && mc.currentScreen != null && mc.player != null) {
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            mc.setScreen(null);
        }

        state = State.IDLE;
        step = 0;
        totemSlot = -1;
        opened = false;
    }
}

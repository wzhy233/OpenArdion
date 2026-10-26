package io.github.wzhy233.ardion.module.impl.movement;

import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.impl.network.PacketSendEvent;
import io.github.wzhy233.ardion.event.impl.player.MotionEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.network.PacketUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class NoSlow extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean shouldSlow;

    public NoSlow() {
        super("no-slow", ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        this.shouldSlow = false;
    }

    @Override
    protected void onDisable() {
        this.shouldSlow = false;
    }

    @Listener(Priority.HIGHEST)
    private void onUpdate(UpdateEvent event) {
        if (mc.player == null || event.getStage() == UpdateEvent.Stage.POST) {
            return;
        }

        if (mc.player.isUsingItem()) {
            this.shouldSlow = false;
        }
    }

    @Listener(Priority.HIGHEST)
    private void onMotion(MotionEvent event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || event.getStage() == MotionEvent.Stage.POST) {
            return;
        }

        if (player.isUsingItem() && (this.isUsable(player.getMainHandStack()) || this.isUsable(player.getOffHandStack()))) {
            this.send();
        }
    }

    @Listener(Priority.HIGHEST)
    private void onPacketReceive(PacketReceiveEvent event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || !this.isHoldingUsable(player) || !player.isUsingItem()) {
            return;
        }

        Block block = null;
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult instanceof BlockHitResult blockHitResult && mc.world != null) {
            block = mc.world.getBlockState(blockHitResult.getBlockPos()).getBlock();
        }

        if (block == Blocks.CHEST) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof InventoryS2CPacket) {
            event.setCancelled(true);
            this.shouldSlow = false;
            return;
        }

        if (packet instanceof ScreenHandlerSlotUpdateS2CPacket) {
            event.setCancelled(true);
        }
    }

    @Listener(Priority.HIGHEST)
    private void onPacketSend(PacketSendEvent event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || !this.isHoldingUsable(player)) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof PlayerInteractItemC2SPacket) {
            this.shouldSlow = true;
            this.send();
            return;
        }

        if (packet instanceof PlayerActionC2SPacket actionPacket
                && actionPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            this.shouldSlow = true;
        }
    }

    public boolean shouldCancelSlow() {
        ClientPlayerEntity player = mc.player;
        return player != null
                && player.isUsingItem()
                && !this.shouldSlow
                && (this.isUsable(player.getMainHandStack()) || this.isUsable(player.getOffHandStack()));
    }

    private boolean isHoldingUsable(ClientPlayerEntity player) {
        return this.isUsable(player.getMainHandStack()) || this.isUsable(player.getOffHandStack());
    }

    private boolean isUsable(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        Item item = itemStack.getItem();
        return itemStack.contains(DataComponentTypes.FOOD)
                || itemStack.contains(DataComponentTypes.CONSUMABLE)
                || item == Items.SHIELD
                || item instanceof BowItem
                || item instanceof CrossbowItem;
    }

    private boolean isBow(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        Item item = itemStack.getItem();
        return item instanceof BowItem || item instanceof CrossbowItem;
    }

    private void send() {
        ClientPlayerEntity player = mc.player;
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (player == null || networkHandler == null) {
            return;
        }

        int interval = this.isBow(player.getMainHandStack()) || this.isBow(player.getOffHandStack()) ? 8 : 6;
        if (player.getItemUseTimeLeft() % interval != 0) {
            return;
        }

        ScreenHandler handler = player.playerScreenHandler;
        ItemStack barrier = Items.BARRIER.getDefaultStack();
        Int2ObjectMap<ItemStackHash> modifiedStacks = new Int2ObjectOpenHashMap<>();
        modifiedStacks.put(36, ItemStackHash.fromItemStack(barrier, networkHandler.getComponentHasher()));
        PacketUtil.sendQueued(new ClickSlotC2SPacket(
                handler.syncId,
                handler.getRevision(),
                (short) 36,
                (byte) 0,
                SlotActionType.SWAP,
                modifiedStacks,
                ItemStackHash.fromItemStack(barrier, networkHandler.getComponentHasher())
        ));
    }
}

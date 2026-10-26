package io.github.wzhy233.ardion.module.impl.misc;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.impl.network.PacketSendEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.util.network.PacketUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.PlayerInput;

import java.util.Iterator;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Disabler extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final double[] PERFECT_PATTERNS = new double[]{0.1, 0.25};

    private final BooleanValue logging = this.registerValue(new BooleanValue("logging", this, false));
    private final BooleanValue acaFastSwitch = this.registerValue(new BooleanValue("aca-fast-switch", this, false));
    private final BooleanValue acaInventoryFrequency = this.registerValue(new BooleanValue("aca-inventory-frequency", this, false));
    private final BooleanValue acaAimStep = this.registerValue(new BooleanValue("aca-aim-step", this, false));
    private final BooleanValue acaPerfectRotation = this.registerValue(new BooleanValue("aca-perfect-rotation", this, false));
    private final BooleanValue grimDuplicateRotPlace = this.registerValue(new BooleanValue("grim-duplicate-rot-place", this, false));
    private final BooleanValue badPacketsA = this.registerValue(new BooleanValue("bad-packets-a", this, false));
    private final BooleanValue badPacketsD = this.registerValue(new BooleanValue("bad-packets-d", this, false));
    private final BooleanValue badPacketsF = this.registerValue(new BooleanValue("bad-packets-f", this, false));
    private final BooleanValue badPacketsG = this.registerValue(new BooleanValue("bad-packets-g", this, false));
    private final BooleanValue badPacketY = this.registerValue(new BooleanValue("bad-packet-y", this, false));
    private final BooleanValue cubecraftPing = this.registerValue(new BooleanValue("cubecraft-ping", this, false));

    private final Random random = new Random();
    private final Queue<DelayedInboundPacket> cubecraftPingQueue = new ConcurrentLinkedQueue<>();

    private float lastMovementYaw;
    private float currentMovementDeltaYaw;
    private float lastPlacedDeltaYaw;
    private float lastYaw;
    private float lastPitch;
    private boolean hasMovementYaw;
    private boolean hasLastPlacedDeltaYaw;
    private boolean hasCurrentMovementDeltaYaw;
    private boolean currentMovementDeltaFresh;
    private int lastAcaFastSwitchSlot = -1;
    private int lastBadPacketsSlot = -1;
    private long inventoryOpenTime;
    private boolean inventoryOpen;
    private CloseHandledScreenC2SPacket storedClosePacket;
    private long storedCloseReleaseTime;
    private boolean lastSprinting;
    private PlayerInput lastInput;

    public Disabler() {
        super("disabler", ModuleCategory.MISC);
    }

    @Override
    protected void onEnable() {
        if (this.cubecraftPing.getValue()) {
            OutputUtil.infoChatOnly("CubeCraft full ping spoof enabled.");
        }

        this.reset();
    }

    @Override
    protected void onDisable() {
        this.flushAllCubecraftPingPackets();
        this.reset();
    }

    @Listener(Priority.HIGH)
    private void onPacket(PacketSendEvent event) {
        if (mc.player == null || event.isCancelled()) {
            return;
        }

        this.releaseStoredClosePacket();
        Packet<?> packet = event.getPacket();

        if (this.handleBadPackets(event, packet)) {
            return;
        }

        packet = event.getPacket();
        if (this.handleAcaFastSwitch(packet)) {
            return;
        }

        packet = event.getPacket();
        if (this.handleAcaInventoryFrequency(event, packet)) {
            return;
        }

        packet = event.getPacket();
        if ((this.acaAimStep.getValue() || this.acaPerfectRotation.getValue()) && packet instanceof PlayerMoveC2SPacket movePacket) {
            Packet<?> beforeRotationPacket = packet;
            this.handleAcaRotation(event, movePacket);
            if (event.getPacket() != beforeRotationPacket) {
                return;
            }
        }

        packet = event.getPacket();
        if (this.grimDuplicateRotPlace.getValue()) {
            this.handleGrimDuplicateRotPlace(event, packet);
        }
    }

    @Listener(Priority.HIGH)
    private void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null || event.isCancelled()) {
            return;
        }

        this.releaseStoredClosePacket();
        this.flushCubecraftPingPackets();
        if (this.acaInventoryFrequency.getValue() && event.getPacket() instanceof OpenScreenS2CPacket) {
            this.inventoryOpenTime = System.currentTimeMillis();
            this.inventoryOpen = true;
            this.log("inventory opened");
        }

        if (this.handleCubecraftPing(event)) {
            return;
        }
    }

    @Listener
    private void onTick(MinecraftTickEvent event) {
        if (event.getStage() == MinecraftTickEvent.Stage.PRE) {
            this.releaseStoredClosePacket();
            this.flushCubecraftPingPackets();
        }
    }

    @Listener
    private void onWorldLoad(WorldLoadEvent event) {
        this.cubecraftPingQueue.clear();
    }

    private boolean handleCubecraftPing(PacketReceiveEvent event) {
        if (!this.cubecraftPing.getValue()) {
            return false;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof KeepAliveS2CPacket || packet instanceof CommonPingS2CPacket) {
            event.setCancelled(true);
            this.cubecraftPingQueue.add(new DelayedInboundPacket(event.getContext(), packet, System.currentTimeMillis()));
            this.log("queued cubecraft ping packet");
            return true;
        }

        if (packet instanceof PlayerPositionLookS2CPacket) {
            this.flushAllCubecraftPingPackets();
            this.log("released cubecraft ping queue on position sync");
        }

        return false;
    }

    private void flushCubecraftPingPackets() {
        if (!this.cubecraftPing.getValue()) {
            this.flushAllCubecraftPingPackets();
            return;
        }

        if (mc.isInSingleplayer()) {
            this.cubecraftPing.setValue(false);
            this.flushAllCubecraftPingPackets();
            OutputUtil.infoChatOnly("cubecraft-ping disabled in singleplayer");
            return;
        }

        if (this.cubecraftPingQueue.isEmpty()) {
            return;
        }

        if (mc.player == null || mc.getNetworkHandler() == null) {
            this.cubecraftPingQueue.clear();
            return;
        }

        long currentTime = System.currentTimeMillis();
        long delay = this.getCubecraftPingDelay();
        Iterator<DelayedInboundPacket> iterator = this.cubecraftPingQueue.iterator();
        while (iterator.hasNext()) {
            DelayedInboundPacket queuedPacket = iterator.next();
            if (currentTime - queuedPacket.timestamp < delay) {
                continue;
            }

            iterator.remove();
            queuedPacket.release();
            this.log("released cubecraft ping packet");
        }
    }

    private void flushAllCubecraftPingPackets() {
        DelayedInboundPacket queuedPacket;
        while ((queuedPacket = this.cubecraftPingQueue.poll()) != null) {
            queuedPacket.release();
        }
    }

    private long getCubecraftPingDelay() {
        int age = mc.player == null ? 0 : mc.player.age;
        if (age < 150) {
            return 5000L;
        }
        if (age < 300) {
            return 10000L;
        }
        return 20000L;
    }

    private boolean handleBadPackets(PacketSendEvent event, Packet<?> packet) {
        if (this.badPacketsA.getValue() && packet instanceof UpdateSelectedSlotC2SPacket slotPacket) {
            int slot = slotPacket.getSelectedSlot();
            if (slot == this.lastBadPacketsSlot) {
                event.setCancelled(true);
                this.log("cancelled duplicate slot packet");
                return true;
            }
            this.lastBadPacketsSlot = slot;
        }

        if (this.badPacketsD.getValue() && packet instanceof PlayerMoveC2SPacket movePacket && movePacket.changesLook()) {
            if (Math.abs(movePacket.getPitch(0.0F)) > 90.0F) {
                event.setCancelled(true);
                this.log("cancelled invalid pitch packet");
                return true;
            }
        }

        if (this.badPacketsF.getValue() && packet instanceof ClientCommandC2SPacket commandPacket) {
            ClientCommandC2SPacket.Mode mode = commandPacket.getMode();
            if (mode == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                if (this.lastSprinting) {
                    event.setCancelled(true);
                    this.log("cancelled duplicate start sprint packet");
                    return true;
                }
                this.lastSprinting = true;
            } else if (mode == ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
                if (!this.lastSprinting) {
                    event.setCancelled(true);
                    this.log("cancelled duplicate stop sprint packet");
                    return true;
                }
                this.lastSprinting = false;
            }
        }

        if (this.badPacketsG.getValue() && packet instanceof PlayerInputC2SPacket inputPacket) {
            PlayerInput input = inputPacket.input();
            if (input.sneak() && input.equals(this.lastInput)) {
                event.setCancelled(true);
                this.log("cancelled duplicate sneak input packet");
                return true;
            }
            this.lastInput = input;
        }

        if (this.badPacketY.getValue() && packet instanceof UpdateSelectedSlotC2SPacket slotPacket) {
            int slot = slotPacket.getSelectedSlot();
            if (slot < 0 || slot > 8) {
                event.setPacket(new UpdateSelectedSlotC2SPacket(Math.max(0, Math.min(8, slot))));
                this.log("clamped selected slot packet");
            }
        }

        return false;
    }

    private boolean handleAcaFastSwitch(Packet<?> packet) {
        if (!this.acaFastSwitch.getValue()) {
            this.lastAcaFastSwitchSlot = -1;
            return false;
        }

        if (!(packet instanceof UpdateSelectedSlotC2SPacket slotPacket)) {
            return false;
        }

        int slot = slotPacket.getSelectedSlot();
        if (this.lastAcaFastSwitchSlot != -1 && slot != this.lastAcaFastSwitchSlot) {
            this.sendIntermediateSlots(this.lastAcaFastSwitchSlot, slot);
        }

        this.lastAcaFastSwitchSlot = slot;
        this.log("processed slot switch to " + slot);
        return false;
    }

    private boolean handleAcaInventoryFrequency(PacketSendEvent event, Packet<?> packet) {
        if (!this.acaInventoryFrequency.getValue() || !(packet instanceof CloseHandledScreenC2SPacket closePacket)) {
            return false;
        }

        if (!this.inventoryOpen) {
            return false;
        }

        long openDuration = System.currentTimeMillis() - this.inventoryOpenTime;
        this.inventoryOpen = false;
        if (openDuration > 150L) {
            this.log("allowed close packet after " + openDuration + "ms");
            return false;
        }

        event.setCancelled(true);
        this.storedClosePacket = closePacket;
        this.storedCloseReleaseTime = System.currentTimeMillis() + 151L - openDuration;
        this.log("delayed close packet for " + (151L - openDuration) + "ms");
        return true;
    }

    private void handleGrimDuplicateRotPlace(PacketSendEvent event, Packet<?> packet) {
        if (packet instanceof PlayerMoveC2SPacket movePacket && movePacket.changesLook()) {
            float yaw = movePacket.getYaw(0.0F);
            float pitch = movePacket.getPitch(0.0F);

            if (this.hasMovementYaw) {
                this.currentMovementDeltaYaw = Math.abs(this.normalizeYaw(yaw - this.lastMovementYaw));
                this.hasCurrentMovementDeltaYaw = true;
                this.currentMovementDeltaFresh = true;
            }

            if (this.shouldAdjustGrimDuplicateDelta()) {
                yaw += this.getDuplicateRotPlaceOffset();
                event.setPacket(this.withRotation(movePacket, yaw, pitch));
                this.log("adjusted block-place rotation");
                return;
            }

            this.lastMovementYaw = yaw;
            this.hasMovementYaw = true;
        } else if (packet instanceof PlayerInteractBlockC2SPacket && this.hasCurrentMovementDeltaYaw && this.currentMovementDeltaFresh) {
            this.lastPlacedDeltaYaw = this.currentMovementDeltaYaw;
            this.hasLastPlacedDeltaYaw = true;
            this.currentMovementDeltaFresh = false;
        }
    }

    private boolean shouldAdjustGrimDuplicateDelta() {
        return this.hasLastPlacedDeltaYaw
                && this.hasCurrentMovementDeltaYaw
                && this.currentMovementDeltaYaw > 2.0F
                && Math.abs(this.currentMovementDeltaYaw - this.lastPlacedDeltaYaw) < 1.0E-4F;
    }

    private float getDuplicateRotPlaceOffset() {
        return this.random.nextBoolean() ? 0.0025F : -0.0025F;
    }

    private void handleAcaRotation(PacketSendEvent event, PlayerMoveC2SPacket movePacket) {
        if (!movePacket.changesLook()) {
            return;
        }

        float yaw = movePacket.getYaw(0.0F);
        float pitch = movePacket.getPitch(0.0F);
        boolean modified = false;

        if (this.acaAimStep.getValue() && this.shouldModifyRotation(yaw, pitch)) {
            float[] rotation = this.getModifiedRotation(yaw, pitch);
            yaw = rotation[0];
            pitch = rotation[1];
            modified = true;
        }

        if (this.acaPerfectRotation.getValue()) {
            float[] rotation = this.getAntiPerfectRotation(yaw, pitch);
            if (rotation[0] != yaw || rotation[1] != pitch) {
                yaw = rotation[0];
                pitch = rotation[1];
                modified = true;
                this.log("adjusted rotation pattern");
            }
        }

        if (modified) {
            event.setPacket(this.withRotation(movePacket, yaw, clampPitch(pitch)));
        }

        this.lastYaw = yaw;
        this.lastPitch = pitch;
    }

    private Packet<?> withRotation(PlayerMoveC2SPacket packet, float yaw, float pitch) {
        if (packet.changesPosition()) {
            return new PlayerMoveC2SPacket.Full(
                    packet.getX(0.0),
                    packet.getY(0.0),
                    packet.getZ(0.0),
                    yaw,
                    pitch,
                    packet.isOnGround(),
                    packet.horizontalCollision()
            );
        }

        return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, packet.isOnGround(), packet.horizontalCollision());
    }

    private float normalizeYaw(float yaw) {
        while (yaw > 180.0F) {
            yaw -= 360.0F;
        }
        while (yaw < -180.0F) {
            yaw += 360.0F;
        }
        return yaw;
    }

    private boolean shouldModifyRotation(float currentYaw, float currentPitch) {
        if (this.lastYaw == 0.0F && this.lastPitch == 0.0F) {
            return false;
        }

        double yawDelta = Math.abs(this.normalizeYaw(currentYaw - this.lastYaw));
        double pitchDelta = Math.abs(currentPitch - this.lastPitch);
        return yawDelta < 1.0E-5 && pitchDelta > 1.0 || pitchDelta < 1.0E-5 && yawDelta > 1.0;
    }

    private float[] getModifiedRotation(float yaw, float pitch) {
        double yawDelta = Math.abs(this.normalizeYaw(yaw - this.lastYaw));
        double pitchDelta = Math.abs(pitch - this.lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;

        if (yawDelta < 1.0E-5 && pitchDelta > 1.0) {
            newYaw = this.lastYaw + (float) (this.random.nextGaussian() * 0.001);
        }
        if (pitchDelta < 1.0E-5 && yawDelta > 1.0) {
            newPitch = this.lastPitch + (float) (this.random.nextGaussian() * 0.001);
        }

        return new float[]{newYaw, newPitch};
    }

    private float[] getAntiPerfectRotation(float yaw, float pitch) {
        if (this.lastYaw == 0.0F && this.lastPitch == 0.0F) {
            return new float[]{yaw, pitch};
        }

        double yawDelta = Math.abs(this.normalizeYaw(yaw - this.lastYaw));
        double pitchDelta = Math.abs(pitch - this.lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;

        if (!this.isNoRotation(yawDelta) && this.isPerfectPattern(yawDelta)) {
            newYaw = yaw + (float) (this.random.nextGaussian() * 0.005);
        }
        if (!this.isNoRotation(pitchDelta) && this.isPerfectPattern(pitchDelta)) {
            newPitch = pitch + (float) (this.random.nextGaussian() * 0.005);
        }

        return new float[]{newYaw, newPitch};
    }

    private boolean isNoRotation(double rotation) {
        return Math.abs(rotation) <= 1.0E-10 || this.isIntegerMultiple(360.0, rotation);
    }

    private boolean isPerfectPattern(double rotation) {
        if (Double.isInfinite(rotation) || Double.isNaN(rotation)) {
            return false;
        }

        for (double pattern : PERFECT_PATTERNS) {
            if (this.isIntegerMultiple(pattern, rotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIntegerMultiple(double reference, double value) {
        if (reference == 0.0) {
            return Math.abs(value) <= 1.0E-10;
        }

        double multiple = value / reference;
        return Math.abs(multiple - Math.round(multiple)) <= 1.0E-10;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    private void reset() {
        this.lastMovementYaw = 0.0F;
        this.currentMovementDeltaYaw = 0.0F;
        this.lastPlacedDeltaYaw = 0.0F;
        this.lastYaw = 0.0F;
        this.lastPitch = 0.0F;
        this.hasMovementYaw = false;
        this.hasLastPlacedDeltaYaw = false;
        this.hasCurrentMovementDeltaYaw = false;
        this.currentMovementDeltaFresh = false;
        this.lastAcaFastSwitchSlot = -1;
        this.lastBadPacketsSlot = -1;
        this.inventoryOpenTime = 0L;
        this.inventoryOpen = false;
        this.storedClosePacket = null;
        this.storedCloseReleaseTime = 0L;
        this.lastSprinting = false;
        this.lastInput = null;
        this.cubecraftPingQueue.clear();
    }

    private void sendIntermediateSlots(int fromSlot, int toSlot) {
        if (mc.getNetworkHandler() == null || this.isWrapAroundSlot(fromSlot, toSlot)) {
            return;
        }

        int distance = Math.abs(fromSlot - toSlot);
        if (distance <= 1) {
            return;
        }

        int step = fromSlot > toSlot ? -1 : 1;
        for (int slot = fromSlot + step; slot != toSlot; slot += step) {
            if (slot < 0 || slot > 8) {
                continue;
            }
            PacketUtil.sendQueued(new UpdateSelectedSlotC2SPacket(slot));
            this.log("sent intermediate slot " + slot);
        }
    }

    private boolean isWrapAroundSlot(int fromSlot, int toSlot) {
        return fromSlot == 0 && toSlot == 8 || fromSlot == 8 && toSlot == 0;
    }

    private void releaseStoredClosePacket() {
        if (this.storedClosePacket == null || mc.getNetworkHandler() == null) {
            return;
        }
        if (System.currentTimeMillis() < this.storedCloseReleaseTime) {
            return;
        }

        CloseHandledScreenC2SPacket packet = this.storedClosePacket;
        this.storedClosePacket = null;
        this.storedCloseReleaseTime = 0L;
        PacketUtil.sendQueued(packet);
        this.log("released delayed close packet");
    }

    private void log(String message) {
        if (this.logging.getValue()) {
            OutputUtil.infoChatOnly(message);
        }
    }

    private record DelayedInboundPacket(ChannelHandlerContext context, Packet<?> packet, long timestamp) {
        private void release() {
            if (this.context != null && this.context.channel().isOpen()) {
                PacketUtil.receiveQueued(this.context, this.packet);
            }
        }
    }
}

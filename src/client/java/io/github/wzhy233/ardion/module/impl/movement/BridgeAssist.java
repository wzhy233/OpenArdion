package io.github.wzhy233.ardion.module.impl.movement;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.player.MovementInputEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public final class BridgeAssist extends Module {
    private final NumberValue minDelay = this.registerValue(new NumberValue("min-delay", this,
            0.0D, 0.0D, 2.0D, true));
    private final NumberValue maxDelay = this.registerValue(new NumberValue("max-delay", this,
            2.0D, 0.0D, 2.0D, true));
    private final BooleanValue directionCheck = this.registerValue(new BooleanValue("direction-check", this, true));
    private final BooleanValue pitchCheck = this.registerValue(new BooleanValue("pitch-check", this, true));
    private final BooleanValue blocksOnly = this.registerValue(new BooleanValue("blocks-only", this, true));
    private final BooleanValue sneakOnly = this.registerValue(new BooleanValue("sneaking-only", this, false));
    private int sneakDelay;

    public BridgeAssist() {
        super("bridge-assist", ModuleCategory.MOVEMENT);
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.PRE) {
            return;
        }

        MinecraftClient client = event.getClient();
        if (client.player == null || client.world == null || client.currentScreen != null) {
            this.sneakDelay = 0;
            return;
        }

        if (this.sneakDelay > 0) {
            this.sneakDelay--;
        }
        if (this.sneakDelay == 0 && this.isAirBelowNextPosition(client.player)) {
            this.sneakDelay = this.nextDelay();
        }
    }

    @Listener
    public void onMovementInput(MovementInputEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.currentScreen != null) {
            return;
        }

        boolean keepSneakingWhileJumping = event.isJump() && event.isSneak();
        if (this.sneakOnly.getValue()
                && client.options.sneakKey.isPressed()
                && !keepSneakingWhileJumping
                && this.passesStaticChecks(player, event)) {
            event.setSneak(false);
        }

        if (!event.isSneak()
                && this.shouldSneak(player, event)
                && (this.sneakDelay > 0 || this.isAirBelowNextPosition(player))) {
            event.setSneak(true);
        }
    }

    @Override
    protected void onDisable() {
        this.sneakDelay = 0;
    }

    private boolean shouldSneak(ClientPlayerEntity player, MovementInputEvent event) {
        return this.passesStaticChecks(player, event) && (player.isOnGround() || event.isJump());
    }

    private boolean passesStaticChecks(ClientPlayerEntity player, MovementInputEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (this.directionCheck.getValue() && event.isPressingForward()) {
            return false;
        }
        if (this.pitchCheck.getValue() && player.getPitch() < 69.0F) {
            return false;
        }
        if (this.sneakOnly.getValue() && !client.options.sneakKey.isPressed()) {
            return false;
        }
        return !this.blocksOnly.getValue() || this.isHoldingBlock(player);
    }

    private boolean isAirBelowNextPosition(ClientPlayerEntity player) {
        if (player.getEntityWorld() == null) {
            return false;
        }

        Vec3d predicted = this.predictMovement(player);
        Vec3d velocity = player.getVelocity();
        Box nextSupportBox = player.getBoundingBox().offset(
                velocity.x + predicted.x,
                -1.0D,
                velocity.z + predicted.z
        );
        return player.getEntityWorld().isSpaceEmpty(player, nextSupportBox);
    }

    private Vec3d predictMovement(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        float strafeInput = getAxis(client.options.leftKey.isPressed(), client.options.rightKey.isPressed()) * 0.98F;
        float forwardInput = getAxis(client.options.forwardKey.isPressed(), client.options.backKey.isPressed()) * 0.98F;
        float inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput;
        if (inputMagnitude < 1.0E-4F) {
            return Vec3d.ZERO;
        }

        inputMagnitude = MathHelper.sqrt(inputMagnitude);
        if (inputMagnitude < 1.0F) {
            inputMagnitude = 1.0F;
        }
        float speed = Math.max(player.getMovementSpeed(), 0.1F) / inputMagnitude;
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        float sinYaw = MathHelper.sin(yaw);
        float cosYaw = MathHelper.cos(yaw);
        strafeInput *= speed;
        forwardInput *= speed;
        return new Vec3d(
                strafeInput * cosYaw - forwardInput * sinYaw,
                0.0D,
                forwardInput * cosYaw + strafeInput * sinYaw
        );
    }

    private boolean isHoldingBlock(ClientPlayerEntity player) {
        return this.isBlockStack(player.getMainHandStack()) || this.isBlockStack(player.getOffHandStack());
    }

    private boolean isBlockStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    private int nextDelay() {
        int min = this.minDelay.getValue().intValue();
        int max = this.maxDelay.getValue().intValue();
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static int getAxis(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0;
        }
        return positive ? 1 : -1;
    }
}

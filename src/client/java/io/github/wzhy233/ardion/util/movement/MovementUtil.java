package io.github.wzhy233.ardion.util.movement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class MovementUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private MovementUtil() {
    }

    public static boolean isMoving() {
        return mc.player != null && mc.player.input != null && mc.player.input.getMovementInput().lengthSquared() > 0.0F;
    }

    public static double direction() {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.input == null) {
            return 0.0D;
        }

        float forward = player.input.getMovementInput().y;
        float strafe = player.input.getMovementInput().x;
        float yaw = player.getYaw();

        if (forward < 0.0F) {
            yaw += 180.0F;
        }

        float factor = 1.0F;
        if (forward < 0.0F) {
            factor = -0.5F;
        } else if (forward > 0.0F) {
            factor = 0.5F;
        }

        if (strafe > 0.0F) {
            yaw -= 90.0F * factor;
        }
        if (strafe < 0.0F) {
            yaw += 90.0F * factor;
        }

        return Math.toRadians(yaw);
    }

    public static void strafe(double speed) {
        ClientPlayerEntity player = mc.player;
        if (player == null || !isMoving()) {
            return;
        }

        double direction = direction();
        Vec3d velocity = player.getVelocity();
        player.setVelocity(-MathHelper.sin((float) direction) * speed, velocity.y, MathHelper.cos((float) direction) * speed);
    }

    public static void stopXZ() {
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return;
        }

        Vec3d velocity = player.getVelocity();
        player.setVelocity(0.0D, velocity.y, 0.0D);
    }

    public static int getAirTicks() {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.isOnGround()) {
            return 0;
        }

        int ticks = 0;
        Vec3d pos = player.getEntityPos();
        Vec3d velocity = player.getVelocity();
        while (ticks < 20 && !player.isOnGround()) {
            ticks++;
            pos = pos.add(velocity);
            velocity = new Vec3d(velocity.x, (velocity.y - 0.08D) * 0.98D, velocity.z);
            if (pos.y <= Math.floor(player.getY())) {
                break;
            }
        }
        return ticks;
    }
}

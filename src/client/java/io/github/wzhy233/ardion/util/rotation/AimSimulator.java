package io.github.wzhy233.ardion.util.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class AimSimulator {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<Box> boxHistory = new ArrayList<>(101);
    private double xRandom;
    private double yRandom;
    private double zRandom;
    private long lastNoiseRandom = System.currentTimeMillis();
    private double lastNoiseDeltaX;
    private double lastNoiseDeltaY;
    private double lastNoiseDeltaZ;
    private boolean nearest;
    private boolean lazy;
    private boolean noise;
    private Vec2f noiseRandom = new Vec2f(0.35F, 0.5F);
    private double noiseSpeed = 1.0D;
    private long noiseDelay = 100L;
    private boolean delay;
    private int delayTicks = 1;
    private boolean scale;
    private double scaleX = 1.0D;
    private double scaleY = 1.0D;
    private boolean offset;
    private double offsetX;
    private double offsetY;
    private boolean offsetPre = true;
    private Vec3d hitPos = Vec3d.ZERO;

    private static float random(double multiple) {
        return (float) ((Math.random() - 0.5D) * 2.0D * multiple);
    }

    private static double normal(double max, double min, double current) {
        if (current >= max) return max;
        return Math.max(current, min);
    }

    public static float rotMove(double target, double current, double diff) {
        return rotMove(target, current, diff, getGCD(), 180.0D);
    }

    public static float rotMove(double target, double current, double diff, Double gcd, double accuracy) {
        double yawDiff = Math.abs(RotationUtil.normalize((float) target) - RotationUtil.normalize((float) current));
        if (yawDiff <= diff) {
            double overMove = yawDiff * (180.0D - accuracy) / 180.0D;
            if (overMove > diff / 10.0D) {
                target += target > current ? overMove : -overMove;
            }
        }

        float delta;
        if ((float) target > (float) current) {
            float dist1 = (float) target - (float) current;
            float dist2 = (float) current + 360.0F - (float) target;
            delta = dist1 > dist2 ? -(float) current - 360.0F + (float) target : dist1;
        } else if ((float) target < (float) current) {
            float dist1 = (float) current - (float) target;
            float dist2 = (float) target + 360.0F - (float) current;
            delta = dist1 > dist2 ? (float) current + 360.0F + (float) target : -dist1;
        } else {
            return (float) current;
        }

        delta = RotationUtil.normalize(delta);
        if (gcd != null) {
            delta = (float) (Math.round(delta / gcd) * gcd);
        }

        if (Math.abs(delta) < 0.1D * Math.random() + 0.1D) {
            return (float) current;
        }
        if (Math.abs(delta) <= (float) diff) {
            return (float) current + delta;
        }
        return delta < 0.0F ? (float) current - (float) diff : (float) current + (float) diff;
    }

    public static boolean yawEquals(float yaw1, float yaw2) {
        return Math.abs(RotationUtil.normalize(yaw1) - RotationUtil.normalize(yaw2)) < getGCD();
    }

    public static boolean equals(Vec2f rot1, Vec2f rot2) {
        return yawEquals(rot1.x, rot2.x) && Math.abs(rot1.y - rot2.y) < getGCD();
    }

    public static double getGCD() {
        double sensitivity = mc.options == null ? 0.5D : mc.options.getMouseSensitivity().getValue();
        double f = sensitivity * 0.6D + 0.2D;
        return f * f * f * 8.0D * 0.15D;
    }

    public void setNearest(boolean nearest) {
        this.nearest = nearest;
    }

    public void setLazy(boolean lazy) {
        this.lazy = lazy;
    }

    public void setNoise(boolean value, Vec2f noiseRandom, double noiseSpeed, long noiseDelay) {
        this.noiseRandom = noiseRandom;
        this.noiseSpeed = noiseSpeed / 100.0D;
        this.noiseDelay = noiseDelay;
        this.noise = value;
    }

    public void setDelay(boolean value, int delayTicks) {
        this.delayTicks = delayTicks;
        this.delay = value;
    }

    public void setScale(boolean value, double scaleX, double scaleY) {
        this.scale = value;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public void setOffset(boolean value, double offsetX, double offsetY, boolean offsetPre) {
        this.offset = value;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetPre = offsetPre;
    }

    public Vec3d getHitPos() {
        return this.hitPos;
    }

    public Rotation getRotation(Entity target) {
        if (mc.player == null) return new Rotation(0.0F, 0.0F);
        Box targetBox = target.getBoundingBox();
        if (this.scale) {
            targetBox = targetBox.expand(
                    (targetBox.getLengthX()) * (this.scaleX - 1.0D),
                    (targetBox.getLengthY()) * (this.scaleY - 1.0D),
                    (targetBox.getLengthZ()) * (this.scaleX - 1.0D)
            );
        }

        if (this.boxHistory.size() >= 101) {
            this.boxHistory.remove(this.boxHistory.size() - 1);
        }
        while (this.boxHistory.size() < 101) {
            this.boxHistory.add(0, targetBox);
        }

        double yDiff = target.getY() - mc.player.getY();
        Box aimBox = this.delay ? this.boxHistory.get(Math.min(this.delayTicks, this.boxHistory.size() - 1)) : targetBox;
        Vec3d targetPosition = this.nearest
                ? RotationUtil.getNearestPoint(aimBox, RotationUtil.getEyePos())
                : new Vec3d((aimBox.maxX + aimBox.minX) / 2.0D,
                aimBox.minY + target.getEyeHeight(target.getPose()) - 0.15D,
                (aimBox.maxZ + aimBox.minZ) / 2.0D);

        if (this.offset && this.offsetPre) {
            targetPosition = targetPosition.add(this.offsetX, this.offsetY, this.offsetX);
        }

        if (yDiff >= 0.0D && this.lazy) {
            if (targetPosition.y - yDiff > target.getY()) {
                targetPosition = new Vec3d(targetPosition.x, targetPosition.y - yDiff, targetPosition.z);
            } else {
                targetPosition = new Vec3d(target.getX(), target.getY() + 0.2D, target.getZ());
            }
        }

        if (this.noise) {
            if (System.currentTimeMillis() - this.lastNoiseRandom >= this.noiseDelay) {
                this.xRandom = random(this.noiseRandom.x);
                this.yRandom = random(this.noiseRandom.y);
                this.zRandom = random(this.noiseRandom.x);
                this.lastNoiseRandom = System.currentTimeMillis();
            }

            this.lastNoiseDeltaX = rotMove(this.xRandom, this.lastNoiseDeltaX, this.noiseSpeed);
            this.lastNoiseDeltaY = rotMove(this.yRandom, this.lastNoiseDeltaY, this.noiseSpeed);
            this.lastNoiseDeltaZ = rotMove(this.zRandom, this.lastNoiseDeltaZ, this.noiseSpeed);

            targetPosition = new Vec3d(
                    normal(targetBox.maxX, targetBox.minX, targetPosition.x + this.lastNoiseDeltaX),
                    normal(targetBox.maxY, targetBox.minY, targetPosition.y + this.lastNoiseDeltaY),
                    normal(targetBox.maxZ, targetBox.minZ, targetPosition.z + this.lastNoiseDeltaZ)
            );
        }

        if (this.offset && !this.offsetPre) {
            targetPosition = targetPosition.add(this.offsetX, this.offsetY, this.offsetX);
        }

        this.hitPos = targetPosition;
        return new Rotation(RotationUtil.getYaw(targetPosition), RotationUtil.getPitch(targetPosition));
    }
}

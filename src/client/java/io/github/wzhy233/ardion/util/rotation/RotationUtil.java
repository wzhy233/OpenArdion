package io.github.wzhy233.ardion.util.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

import java.lang.reflect.Field;
import java.util.Optional;

public final class RotationUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final double TO_DEGREES = 180.0D / Math.PI;

    private RotationUtil() {
    }

    public static Rotation calculate(BlockPos position, Direction direction) {
        Vec3d center = position.toCenterPos();
        double x = center.x + direction.getOffsetX() * 0.5D;
        double y = center.y + direction.getOffsetY() * 0.5D;
        double z = center.z + direction.getOffsetZ() * 0.5D;

        for (double i = -0.3D; i <= 0.3D; i += 0.3D) {
            for (double j = -0.3D; j <= 0.3D; j += 0.3D) {
                if (i == 0.0D && j == 0.0D) continue;

                double testX = x;
                double testY = y;
                double testZ = z;
                if (direction.getAxis() == Direction.Axis.X) {
                    testY += i;
                    testZ += j;
                } else if (direction.getAxis() == Direction.Axis.Y) {
                    testX += i;
                    testZ += j;
                } else {
                    testX += i;
                    testY += j;
                }

                Rotation rotation = calculate(new Vec3d(testX, testY, testZ));
                if (RayTraceUtil.overBlock(rotation, direction, position)) {
                    return rotation;
                }
            }
        }

        return calculate(new Vec3d(x, y, z));
    }

    public static Rotation calculate(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) (Math.atan2(diff.z, diff.x) * TO_DEGREES) - 90.0F;
        float pitch = (float) (-(Math.atan2(diff.y, distance) * TO_DEGREES));
        return new Rotation(yaw, pitch);
    }

    public static Rotation calculate(Vec3d to) {
        if (mc.player == null) return new Rotation(0.0F, 0.0F);
        return calculate(mc.player.getEyePos(), to);
    }

    public static Rotation calculate(BlockPos to) {
        return calculate(to.toCenterPos());
    }

    public static float rotateToYaw(float yawSpeed, float currentYaw, float calcYaw) {
        return updateRotation(currentYaw, calcYaw, yawSpeed);
    }

    public static float updateRotation(float current, float calc, float maxDelta) {
        float delta = MathHelper.wrapDegrees(calc - current);
        if (delta > maxDelta) delta = maxDelta;
        if (delta < -maxDelta) delta = -maxDelta;
        return current + delta;
    }

    public static float updateRotation(float current, float calc, float maxDelta, float minDelta) {
        float delta = MathHelper.wrapDegrees(calc - current);
        if (delta > maxDelta) delta = maxDelta;
        if (delta < -maxDelta) delta = -maxDelta;
        if (Math.abs(delta) < minDelta) delta = 0.0F;
        return current + delta;
    }

    public static Vec3d getNearestPoint(Box box, Vec3d point) {
        double x = MathHelper.clamp(point.x, box.minX, box.maxX);
        double y = MathHelper.clamp(point.y, box.minY, box.maxY);
        double z = MathHelper.clamp(point.z, box.minZ, box.maxZ);
        return new Vec3d(x, y, z);
    }

    public static Vec3d getClosestPoint(Vec3d start, Box box) {
        return getNearestPoint(box, start);
    }

    public static float[] getAngles(Vec3d targetPos) {
        if (mc.player == null) return new float[]{0.0F, 0.0F};
        Vec3d eyePos = mc.player.getEyePos();
        double diffX = targetPos.x - eyePos.x;
        double diffY = targetPos.y - eyePos.y;
        double diffZ = targetPos.z - eyePos.z;
        double dist = MathHelper.sqrt((float) (diffX * diffX + diffZ * diffZ));
        float yaw = (float) (Math.atan2(diffZ, diffX) * TO_DEGREES) - 90.0F;
        float pitch = (float) -(Math.atan2(diffY, dist) * TO_DEGREES);
        return new float[]{
                mc.player.getYaw() + MathHelper.wrapDegrees(yaw - mc.player.getYaw()),
                mc.player.getPitch() + MathHelper.wrapDegrees(pitch - mc.player.getPitch())
        };
    }

    public static float[] getAngles(Box box) {
        if (mc.player == null) return new float[]{0.0F, 0.0F};
        return getAngles(getNearestPoint(box, mc.player.getEyePos()));
    }

    public static Vec3d getEyePos() {
        return mc.player == null ? Vec3d.ZERO : getEyePos(mc.player, mc.player.getEntityPos());
    }

    public static Vec3d getEyePos(Entity entity, Vec3d position) {
        return position.add(0.0D, entity.getEyeHeight(entity.getPose()), 0.0D);
    }

    public static float getYaw(BlockPos pos) {
        return getYaw(pos.toCenterPos());
    }

    public static float getYaw(PlayerEntity from, Vec3d pos) {
        Vec3d fromPos = from.getEntityPos();
        return from.getYaw() + MathHelper.wrapDegrees(
                (float) Math.toDegrees(Math.atan2(pos.z - fromPos.z, pos.x - fromPos.x)) - 90.0F - from.getYaw()
        );
    }

    public static float getYaw(Vec3d pos) {
        if (mc.player == null) return 0.0F;
        return getYaw(mc.player, pos);
    }

    public static float getPitch(BlockPos pos) {
        return getPitch(pos.toCenterPos());
    }

    public static float getPitch(PlayerEntity from, Vec3d pos) {
        Vec3d fromPos = from.getEntityPos();
        double diffX = pos.x - fromPos.x;
        double diffY = pos.y - (fromPos.y + from.getEyeHeight(from.getPose()));
        double diffZ = pos.z - fromPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        return from.getPitch() + MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - from.getPitch());
    }

    public static float getPitch(Vec3d pos) {
        if (mc.player == null) return 0.0F;
        return getPitch(mc.player, pos);
    }

    public static float normalize(float yaw) {
        return normalize(yaw, -180.0F, 180.0F);
    }

    public static float normalize(float yaw, float min, float max) {
        yaw %= 360.0F;
        if (yaw >= max) yaw -= 360.0F;
        if (yaw < min) yaw += 360.0F;
        return yaw;
    }

    public static Optional<Entity> rayCastEntity(PlayerEntity player, float yaw, float pitch, double range) {
        if (mc.world == null || player == null) return Optional.empty();
        Vec3d eyePos = player.getEyePos();
        Vec3d view = RayTraceUtil.getVectorForRotation(yaw, pitch);
        Vec3d rayEnd = eyePos.add(view.multiply(range));

        Entity closestEntity = null;
        double closestDistance = range;
        for (Entity entity : mc.world.getEntities()) {
            if (entity == player || !entity.canHit() || entity.isSpectator()) continue;
            Optional<Vec3d> intersection = entity.getBoundingBox().expand(0.1D).raycast(eyePos, rayEnd);
            if (intersection.isPresent()) {
                double distance = intersection.get().distanceTo(eyePos);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        HitResult blockHit = RayTraceUtil.rayTraceBlocks(eyePos, rayEnd);
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getPos().distanceTo(eyePos) < closestDistance) {
            return Optional.empty();
        }

        return Optional.ofNullable(closestEntity);
    }

    public static Optional<Entity> rayCastEntity(float yaw, float pitch, double range) {
        return mc.player == null ? Optional.empty() : rayCastEntity(mc.player, yaw, pitch, range);
    }

    public static Rotation calculateAim(Vec3d player, Vec3d target) {
        double x = target.x - player.x;
        double z = target.z - player.z;
        double xz = Math.sqrt(x * x + z * z);
        return new Rotation(
                (float) -(Math.atan2(target.y - player.y, xz) * TO_DEGREES),
                (float) -(Math.atan2(target.x - player.x, target.z - player.z) * TO_DEGREES)
        );
    }

    public static Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, float turnSpeed) {
        float yawDifference = getAngleDifference(targetRotation.getYaw(), currentRotation.getYaw());
        float pitchDifference = getAngleDifference(targetRotation.getPitch(), currentRotation.getPitch());
        return new Rotation(
                currentRotation.getYaw() + MathHelper.clamp(yawDifference, -turnSpeed, turnSpeed),
                currentRotation.getPitch() + MathHelper.clamp(pitchDifference, -turnSpeed, turnSpeed)
        );
    }

    public static float getAngleDifference(float a, float b) {
        return ((((a - b) % 360.0F) + 540.0F) % 360.0F) - 180.0F;
    }

    public static Vec3d calculateViewVector(float pitch, float yaw) {
        return RayTraceUtil.calculateViewVector(pitch, yaw);
    }

    public static double getDistanceToEntityBox(Entity target) {
        if (mc.player == null) return Double.MAX_VALUE;
        Vec3d eyes = mc.player.getEyePos();
        Vec3d pos = getNearestPoint(target.getBoundingBox(), eyes);
        return eyes.distanceTo(pos);
    }

    public static double getDistanceToVec3(Vec3d pos) {
        return mc.player == null ? Double.MAX_VALUE : mc.player.getEyePos().distanceTo(pos);
    }

    public static double getDistanceToVec3(Vec3d from, Vec3d pos) {
        return from.distanceTo(pos);
    }

    public static float[] getRotationsNeeded(Entity target) {
        if (mc.player == null) return new float[]{0.0F, 0.0F};
        Vec3d pos;
        double yDist = target.getEntityPos().y - mc.player.getEntityPos().y;
        if (yDist >= 1.7D) {
            pos = target.getEntityPos();
        } else if (yDist <= -1.7D) {
            pos = target.getEntityPos().add(0.0D, target.getEyeHeight(target.getPose()), 0.0D);
        } else {
            pos = target.getEntityPos().add(0.0D, target.getEyeHeight(target.getPose()) / 2.0D, 0.0D);
        }
        Vec3d eye = new Vec3d(mc.player.getX(),
                mc.player.getBoundingBox().minY + mc.player.getEyeHeight(mc.player.getPose()),
                mc.player.getZ());
        return getRotationTo(eye, pos);
    }

    public static float getTrajAngleSolutionLow(float distance, float height, float velocity) {
        float gravity = 0.006F;
        float root = velocity * velocity * velocity * velocity
                - gravity * (gravity * (distance * distance) + 2.0F * height * (velocity * velocity));
        return (float) Math.toDegrees(Math.atan(((velocity * velocity) - Math.sqrt(root)) / (gravity * distance)));
    }

    public static double getDirection(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0.0F) rotationYaw += 180.0F;
        float forward = 1.0F;
        if (moveForward < 0.0F) forward = -0.5F;
        else if (moveForward > 0.0F) forward = 0.5F;
        if (moveStrafing > 0.0F) rotationYaw -= 90.0F * forward;
        if (moveStrafing < 0.0F) rotationYaw += 90.0F * forward;
        return Math.toRadians(rotationYaw);
    }

    public static float[] getRotationTo(Vec3d targetPos) {
        if (mc.player == null) return new float[]{0.0F, 0.0F};
        float[] rotations = getRotationTo(mc.player.getEyePos(), targetPos);
        return new float[]{
                mc.player.getYaw() + MathHelper.wrapDegrees(rotations[0] - mc.player.getYaw()),
                mc.player.getPitch() + MathHelper.wrapDegrees(rotations[1] - mc.player.getPitch())
        };
    }

    public static float[] getRotationTo(Vec3d fromPos, Vec3d targetPos) {
        double diffX = targetPos.x - fromPos.x;
        double diffY = targetPos.y - fromPos.y;
        double diffZ = targetPos.z - fromPos.z;
        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontalDistance));
        return new float[]{yaw, pitch};
    }

    public static float[] getRotationTo(BlockPos blockPos) {
        return getRotationTo(blockPos.toCenterPos());
    }

    public static void setRotations(Vec2f rotations) {
        if (mc.player == null) return;
        mc.player.setYaw(rotations.x);
        mc.player.setPitch(rotations.y);
        try {
            Field lastYaw = Entity.class.getDeclaredField("lastYaw");
            Field lastPitch = Entity.class.getDeclaredField("lastPitch");
            lastYaw.setAccessible(true);
            lastPitch.setAccessible(true);
            lastYaw.setFloat(mc.player, rotations.x);
            lastPitch.setFloat(mc.player, rotations.y);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static void setRotations(Rotation rotation) {
        setRotations(rotation.toVec2f());
    }
}

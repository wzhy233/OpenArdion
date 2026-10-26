package io.github.wzhy233.ardion.util.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

public final class RayTraceUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private RayTraceUtil() {
    }

    public static boolean overBlock(Rotation rotation, Direction direction, BlockPos pos) {
        return overBlock(rotation, direction, pos, false);
    }

    public static boolean overBlock(Rotation rotation, BlockPos pos, boolean strict) {
        return overBlock(rotation, null, pos, strict);
    }

    public static boolean overBlock(Rotation rotation, Direction direction, BlockPos pos, boolean strict) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d cameraPos = mc.player.getEyePos();
        Vec3d rotationVec = getVectorForRotation(rotation.getYaw(), rotation.getPitch());
        Vec3d reachVec = cameraPos.add(rotationVec.multiply(4.5D));
        BlockHitResult hitResult = mc.world.raycast(new RaycastContext(
                cameraPos,
                reachVec,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        boolean samePos = hitResult.getBlockPos().equals(pos);
        boolean sameSide = !strict || direction == null || hitResult.getSide() == direction;
        return samePos && sameSide;
    }

    public static HitResult rayCast(float tickDelta, Vec2f rotations) {
        return rayCast(getReachDistance(), tickDelta, true, rotations);
    }

    public static HitResult rayCast(float tickDelta, Rotation rotations) {
        return rayCast(getReachDistance(), tickDelta, true, rotations.toVec2f());
    }

    public static HitResult rayCast(double range, float tickDelta, boolean hitFluids, Vec2f rotations) {
        if (mc.getCameraEntity() == null || mc.world == null || mc.player == null) return null;
        return pick(range, tickDelta, hitFluids, rotations.x, rotations.y);
    }

    public static HitResult rayCast(double range, float yaw, float pitch) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d from = mc.player.getEyePos();
        Vec3d view = getVectorForRotation(yaw, pitch);
        return rayTraceBlocks(from, from.add(view.multiply(range)));
    }

    public static HitResult rayCast(Vec3d from, float yaw, float pitch, double range) {
        if (mc.world == null) return null;
        Vec3d view = getVectorForRotation(yaw, pitch);
        return rayTraceBlocks(from, from.add(view.multiply(range)));
    }

    public static HitResult rayCast(double range) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d from = mc.player.getEyePos();
        Vec3d to = from.add(mc.player.getRotationVec(1.0F).multiply(range));
        return rayTraceBlocks(from, to);
    }

    public static HitResult pick(double hitDistance, float tickDelta, boolean hitFluids, float yaw, float pitch) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d from = mc.player.getCameraPosVec(tickDelta);
        Vec3d view = getVectorForRotation(yaw, pitch);
        Vec3d to = from.add(view.multiply(hitDistance));
        return mc.world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.OUTLINE,
                hitFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE,
                mc.player
        ));
    }

    public static HitResult rayTraceBlocks(Vec3d from, Vec3d to) {
        if (mc.world == null) return null;
        return mc.world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
    }

    public static HitResult rayTraceBlocks(Vec3d from, Vec3d to, boolean stopOnLiquid,
                                           boolean ignoreBlockWithoutBoundingBox,
                                           boolean returnLastUncollidableBlock,
                                           Entity entity) {
        if (mc.world == null) return null;
        RaycastContext.ShapeType shapeType = ignoreBlockWithoutBoundingBox
                ? RaycastContext.ShapeType.COLLIDER
                : returnLastUncollidableBlock ? RaycastContext.ShapeType.VISUAL : RaycastContext.ShapeType.OUTLINE;
        RaycastContext.FluidHandling fluidHandling = stopOnLiquid
                ? RaycastContext.FluidHandling.ANY
                : RaycastContext.FluidHandling.NONE;
        return mc.world.raycast(new RaycastContext(from, to, shapeType, fluidHandling, entity));
    }

    public static HitResult rayTraceEntity(Entity entity, double range) {
        if (entity == null || entity.getEntityWorld() == null) return null;
        Vec3d from = entity.getEyePos();
        Vec3d to = from.add(entity.getRotationVec(1.0F).multiply(range));
        return entity.getEntityWorld().raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                entity
        ));
    }

    public static BlockPos getNeighborBlockPos(HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult blockHitResult)) return null;
        return blockHitResult.getBlockPos().offset(blockHitResult.getSide());
    }

    public static EntityHitResult calculateIntercept(Box box, Vec3d from, Vec3d to) {
        Optional<Vec3d> clipped = box.raycast(from, to);
        return clipped.map(vec3d -> new EntityHitResult(null, vec3d)).orElse(null);
    }

    public static Vec3d calculateViewVector(float pitch, float yaw) {
        float pitchRad = pitch * ((float) Math.PI / 180.0F);
        float yawRad = -yaw * ((float) Math.PI / 180.0F);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public static Vec3d getVectorForRotation(float yaw, float pitch) {
        return calculateViewVector(pitch, yaw);
    }

    public static boolean isVisible(Vec3d from, Vec3d to) {
        if (mc.world == null) return false;
        HitResult result = mc.world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                (Entity) null
        ));
        return result.getType() == HitResult.Type.MISS;
    }

    public static double getReachDistance() {
        return mc.interactionManager == null ? 4.5D : 4.5D;
    }
}

package io.github.wzhy233.ardion.util.rotation;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;

import java.util.Objects;

public class Rotation {
    private float yaw;
    private float pitch;

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Rotation(Vec2f rotation) {
        this(rotation.x, rotation.y);
    }

    public Rotation(Entity entity) {
        this(entity.getYaw(), entity.getPitch());
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public Rotation offset(Vec2f offset) {
        return new Rotation(this.yaw + offset.x, this.pitch + offset.y);
    }

    public Rotation offset(float yaw, float pitch) {
        return new Rotation(this.yaw + yaw, this.pitch + pitch);
    }

    public Vec2f toVec2f() {
        return new Vec2f(this.yaw, this.pitch);
    }

    public Rotation copy() {
        return new Rotation(this.yaw, this.pitch);
    }

    public double distanceTo(Rotation rotation) {
        return Math.sqrt(this.distanceToSqr(rotation));
    }

    public double distanceToSqr(Rotation rotation) {
        float yawDiff = rotation.yaw - this.yaw;
        float pitchDiff = rotation.pitch - this.pitch;
        return yawDiff * yawDiff + pitchDiff * pitchDiff;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Rotation rotation) {
            return Float.compare(rotation.yaw, this.yaw) == 0
                    && Float.compare(rotation.pitch, this.pitch) == 0;
        }
        if (obj instanceof Vec2f vec) {
            return Float.compare(vec.x, this.yaw) == 0
                    && Float.compare(vec.y, this.pitch) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.yaw, this.pitch);
    }

    @Override
    public String toString() {
        return "yaw:" + this.yaw + " pitch:" + this.pitch;
    }
}

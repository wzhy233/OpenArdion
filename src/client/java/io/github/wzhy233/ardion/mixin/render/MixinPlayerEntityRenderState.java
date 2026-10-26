package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.access.PigGodRenderStateAccess;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public abstract class MixinPlayerEntityRenderState implements PigGodRenderStateAccess {
    @Unique
    private boolean ardion$pigGod;

    @Override
    public boolean ardion$isPigGod() {
        return this.ardion$pigGod;
    }

    @Override
    public void ardion$setPigGod(boolean pigGod) {
        this.ardion$pigGod = pigGod;
    }
}

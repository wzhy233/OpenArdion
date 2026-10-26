package io.github.wzhy233.ardion.mixin.world;

import io.github.wzhy233.ardion.event.impl.world.WorldTickEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.module.impl.world.Scaffold;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class MixinClientWorld {

    @Inject(method = "tickEntities", at = @At("TAIL"))
    private void onTickEntities(CallbackInfo ci) {
        EventManager.post(new WorldTickEvent((ClientWorld) (Object) this));
    }
}

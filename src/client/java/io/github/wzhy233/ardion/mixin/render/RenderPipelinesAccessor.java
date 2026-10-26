package io.github.wzhy233.ardion.mixin.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Invoker("register")
    static RenderPipeline invokeRegister(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}

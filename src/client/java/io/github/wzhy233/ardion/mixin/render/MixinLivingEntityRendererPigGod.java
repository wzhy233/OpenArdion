package io.github.wzhy233.ardion.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.wzhy233.ardion.access.PigGodRenderStateAccess;
import io.github.wzhy233.ardion.module.impl.render.PigGod;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PigEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererPigGod {
    @Unique
    private static final Identifier ARDION$PIG_TEXTURE = Identifier.ofVanilla("textures/entity/pig/temperate_pig.png");

    @Unique
    private PigEntityModel ardion$pigModel;

    @Unique
    private void ardion$ensurePigModel() {
        if (this.ardion$pigModel == null) {
            this.ardion$pigModel = new PigEntityModel(PigEntityModel.getTexturedModelData(Dilation.NONE).createModel());
        }
    }

    @Unique
    private static boolean ardion$isPigGod(LivingEntityRenderState state) {
        return state instanceof PigGodRenderStateAccess access && access.ardion$isPigGod();
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void ardion$markPigGod(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (state instanceof PigGodRenderStateAccess access) {
            access.ardion$setPigGod(entity instanceof PlayerEntity player && PigGod.shouldReplacePlayer(player));
        }
    }

    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void ardion$pigLayer(LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline,
                                 CallbackInfoReturnable<RenderLayer> cir) {
        if (!ardion$isPigGod(state)) return;
        this.ardion$ensurePigModel();

        cir.setReturnValue(translucent ? RenderLayers.itemEntityTranslucentCull(ARDION$PIG_TEXTURE)
                : showBody ? this.ardion$pigModel.getLayer(ARDION$PIG_TEXTURE)
                : showOutline ? RenderLayers.outlineNoCull(ARDION$PIG_TEXTURE) : null);
    }

    @ModifyArgs(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V")
    )
    private void ardion$replacePlayerModel(Args args) {
        if (!(args.get(1) instanceof LivingEntityRenderState state) || !ardion$isPigGod(state)) return;
        this.ardion$ensurePigModel();
        args.set(0, this.ardion$pigModel);
    }

    @ModifyExpressionValue(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;shouldRenderFeatures(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)Z")
    )
    private boolean ardion$hidePlayerFeatures(boolean original, LivingEntityRenderState state) {
        return original && !ardion$isPigGod(state);
    }
}

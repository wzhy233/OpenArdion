package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.module.impl.render.BetterTooltips;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public class MixinItemStack {
    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void appendBetterTooltip(Item.TooltipContext context, net.minecraft.entity.player.PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        BetterTooltips module = ModuleManager.getModule(BetterTooltips.class);
        if (module != null && module.isEnabled()) {
            List<Text> lines = new ArrayList<>(cir.getReturnValue());
            module.appendTooltip((ItemStack) (Object) this, lines);
            cir.setReturnValue(lines);
        }
    }
}

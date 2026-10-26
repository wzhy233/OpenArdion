package io.github.wzhy233.ardion.mixin.render;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InGameHud.class)
public interface InGameHudAccessor {
    @Accessor("title")
    Text ardion$getTitle();

    @Accessor("subtitle")
    Text ardion$getSubtitle();

    @Accessor("titleRemainTicks")
    int ardion$getTitleRemainTicks();

    @Accessor("overlayMessage")
    Text ardion$getOverlayMessage();

    @Accessor("overlayRemaining")
    int ardion$getOverlayRemaining();
}

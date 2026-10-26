package io.github.wzhy233.ardion.gui.screen;

import io.github.wzhy233.ardion.gui.listed.ImplScreen;
import io.github.wzhy233.ardion.util.render.SkiaRenderManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiScreen extends io.github.wzhy233.ardion.gui.ui.AnimatedScreen {
    private final ImplScreen internal = ImplScreen.instance;
    private final SkiaRenderManager skia = SkiaRenderManager.get();

    public ClickGuiScreen() {
        super(Text.literal("Unknown"));
    }

    @Override
    protected void init() {
        internal.initGui();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void renderUi(DrawContext context, int mouseX, int mouseY, float delta) {
        if (skia.renderNow(context, canvas -> internal.drawScreen(mouseX, mouseY, delta))) {
            return;
        }
        context.fill(0, 0, width, height, 0xD80B0E16);
        context.drawTextWithShadow(textRenderer, "Ardion Skija renderer failed", 20, 20, 0xFFFF6B6B);
        context.drawTextWithShadow(textRenderer, skia.failureReason(), 20, 36, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!acceptsInput()) return true;
        internal.mouseClicked((int) click.x(), (int) click.y(), click.button());
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!acceptsInput()) return true;
        internal.mouseReleased((int) click.x(), (int) click.y(), click.button());
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!acceptsInput()) return true;
        internal.update((int) click.x(), (int) click.y());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!acceptsInput()) return true;
        internal.handleMouseInput(verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!acceptsInput()) return true;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(input);
        }
        internal.keyTyped('\0', input.key());
        return true;
    }

    @Override
    public void removed() {
        internal.onGuiClosed();
    }
}

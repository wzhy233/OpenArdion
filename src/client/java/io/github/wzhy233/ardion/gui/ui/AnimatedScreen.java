package io.github.wzhy233.ardion.gui.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class AnimatedScreen extends Screen {
    protected final UiState ui = new UiState();
    private final UiTransition visibility = new UiTransition(0);
    private boolean closing;
    private boolean leaving;
    private Screen destination;

    protected AnimatedScreen(Text title) { super(title); }

    @Override
    public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float opacity = visibility.to(closing ? 0 : 1, System.nanoTime(),
                (closing ? UiManager.EXIT_MS : UiManager.ENTER_MS) * 1_000_000L);
        if (closing && opacity == 0) {
            leaving = true;
            if (client != null && client.currentScreen == this) client.setScreen(destination);
            return;
        }
        float previous = UiManager.screenOpacity();
        UiManager.screenOpacity(opacity);
        try { renderUi(context, closing ? -10000 : mouseX, closing ? -10000 : mouseY, delta); }
        finally { UiManager.screenOpacity(previous); }
    }

    protected abstract void renderUi(DrawContext context, int mouseX, int mouseY, float delta);

    @Override
    public final void close() { closeTo(null); }

    public final void closeTo(Screen next) {
        destination = next;
        if (!closing) {
            closing = true;
            visibility.to(0, System.nanoTime(), UiManager.EXIT_MS * 1_000_000L);
            onCloseRequested();
        }
    }

    protected void onCloseRequested() { }
    public final boolean canLeave() { return leaving; }
    protected final boolean acceptsInput() { return !closing; }
}

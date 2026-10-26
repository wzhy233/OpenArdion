package io.github.wzhy233.ardion.gui.listed.api;

public interface Component {
    void init();

    void update(int mouseX, int mouseY);

    void drawComponent(int mouseX, int mouseY, float partialTicks, Limitation limitation);

    void mouseClicked(float mouseX, float mouseY, int mouseButton);

    void mouseReleased(float mouseX, float mouseY, int state);

    void keyTyped(char typedChar, int keyCode);
}

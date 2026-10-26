package io.github.wzhy233.ardion.gui.shader;

import io.github.humbleui.skija.RuntimeEffect;
import io.github.humbleui.skija.RuntimeEffectBuilder;
import io.github.humbleui.skija.Shader;

public final class RuntimeShader {
    private final RuntimeEffectBuilder builder;

    RuntimeShader(RuntimeEffect effect) {
        this.builder = new RuntimeEffectBuilder(effect);
    }

    public RuntimeShader uniform(String name, float value) {
        builder.setUniform(name, value);
        return this;
    }

    public RuntimeShader uniform(String name, float x, float y) {
        builder.setUniform(name, x, y);
        return this;
    }

    public RuntimeShader child(String name, Shader shader) {
        builder.setChild(name, shader);
        return this;
    }

    public Shader make() {
        try (RuntimeEffectBuilder ignored = builder) {
            return builder.makeShader();
        }
    }
}

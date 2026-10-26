package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.ModeValue;

public final class WeatherChanger extends Module {
    private final ModeValue weather = this.registerValue(new ModeValue("weather", this, "Clear", "Clear", "Rain", "Thunder"));

    public WeatherChanger() {
        super("weather-changer", ModuleCategory.RENDER);
    }

    public float getRainGradient() {
        return weather.is("Clear") ? 0.0F : 1.0F;
    }

    public float getThunderGradient() {
        return weather.is("Thunder") ? 1.0F : 0.0F;
    }
}

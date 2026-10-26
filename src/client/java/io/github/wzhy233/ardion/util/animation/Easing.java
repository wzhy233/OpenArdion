package io.github.wzhy233.ardion.util.animation;

import java.util.function.Function;

import static java.lang.Math.pow;
import static java.lang.Math.sin;

public enum Easing {
    LINEAR(x -> x),
    EASE_OUT_QUAD(x -> x * (2 - x)),
    EASE_OUT_CUBIC(x -> 1 - pow(1 - x, 3)),
    EASE_OUT_EXPO(x -> x == 1 ? 1 : 1 - pow(2, -10 * x)),
    EASE_OUT_BACK(x -> {
        double c1 = 1.70158D;
        double c3 = c1 + 1.0D;
        return 1.0D + c3 * pow(x - 1.0D, 3) + c1 * pow(x - 1.0D, 2);
    });

    private final Function<Double, Double> function;

    Easing(Function<Double, Double> function) {
        this.function = function;
    }

    public Function<Double, Double> getFunction() {
        return function;
    }
}

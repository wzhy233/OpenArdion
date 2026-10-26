package io.github.wzhy233.ardion.util.text;

import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

import java.util.Optional;

public final class SpoofTextUtil {
    private SpoofTextUtil() {
    }

    public static StringVisitable spoof(StringVisitable text) {
        if (text == null) return null;
        if (text instanceof Text vanillaText) {
            return Spoofer.spoof(vanillaText);
        }

        MutableText result = Text.empty();
        text.visit((style, string) -> {
            result.append(Text.literal(string == null ? "" : string).setStyle(style == null ? Style.EMPTY : style));
            return Optional.empty();
        }, Style.EMPTY);
        return Spoofer.spoof(result);
    }
}

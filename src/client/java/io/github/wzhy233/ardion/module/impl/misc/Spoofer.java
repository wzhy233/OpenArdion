package io.github.wzhy233.ardion.module.impl.misc;

import io.github.wzhy233.ardion.event.impl.render.RenderTextEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.irc.IrcNameOverlay;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.StringValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Spoofer extends Module {
    private static final Map<String, String> SERVER_IP_REPLACEMENTS = Map.of(
            "hypixel", "hackpixel",
            "布吉岛", "妖猫岛",
            "cubecraft","cutecraft",
            "mcbi","micb",
            "hoplite","hopheavy"
    );

    private final BooleanValue playerName = this.registerValue(new BooleanValue("player-name", this, true));
    private final BooleanValue serverIp = this.registerValue(new BooleanValue("server-ip", this, true));
    private final BooleanValue clientBrand = this.registerValue(new BooleanValue("client-brand", this, true));
    private final BooleanValue resourcePack = this.registerValue(new BooleanValue("resource-pack", this, true));
    private final BooleanValue translationKey = this.registerValue(new BooleanValue("translation-key", this, true));
    private final BooleanValue localHttp = this.registerValue(new BooleanValue("local-http", this, true));
    private final BooleanValue fingerprinting = this.registerValue(new BooleanValue("finger-printing", this, true));
    private final StringValue brand = this.registerValue(new StringValue("brand", this, "vanilla", () -> this.clientBrand.getValue()));

    public Spoofer() {
        super("spoofer", ModuleCategory.MISC);
    }

    public static OrderedText spoof(OrderedText text) {
        return IrcNameOverlay.rewrite(spoofOrdered(text));
    }

    private static OrderedText spoofOrdered(OrderedText text) {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        if (spoofer == null || !spoofer.isEnabled() || text == null) return text;

        List<StyledChar> chars = spoofer.toStyledChars(text);
        if (chars.isEmpty()) return text;

        String player = spoofer.getPlayerName();
        ReplacementResult result = new ReplacementResult(chars, false);
        if (spoofer.playerName.getValue() && player != null && !player.isEmpty()) {
            result = spoofer.replace(result.chars(), player, "Hidden", result.changed());
        }
        if (spoofer.serverIp.getValue()) {
            for (Map.Entry<String, String> entry : SERVER_IP_REPLACEMENTS.entrySet()) {
                result = spoofer.replace(result.chars(), entry.getKey(), entry.getValue(), result.changed());
            }
        }

        return result.changed() ? spoofer.toOrderedText(result.chars()) : text;
    }

    public static String spoof(String text) {
        return IrcNameOverlay.rewrite(spoofString(text));
    }

    public static String spoofPlain(String text) {
        return IrcNameOverlay.rewritePlain(spoofString(text));
    }

    private static String spoofString(String text) {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        if (spoofer == null || !spoofer.isEnabled()) return text;
        if (text == null || text.isEmpty()) return text;
        String player = spoofer.getPlayerName();
        if (spoofer.playerName.getValue() && player != null && !player.isEmpty()) {
            text = spoofer.replace(text, player, "Hidden");
        }
        text = spoofer.replaceServerIp(text);
        return text;
    }

    public static Text spoof(Text text) {
        return IrcNameOverlay.rewrite(spoofText(text));
    }

    private static Text spoofText(Text text) {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        if (spoofer == null || !spoofer.isEnabled()) return text;
        if (text == null) return null;

        List<StyledChar> chars = spoofer.toStyledChars(text);
        if (chars.isEmpty()) return text;

        String player = spoofer.getPlayerName();
        ReplacementResult result = new ReplacementResult(chars, false);
        if (spoofer.playerName.getValue() && player != null && !player.isEmpty()) {
            result = spoofer.replace(result.chars(), player, "Hidden", result.changed());
        }
        if (spoofer.serverIp.getValue()) {
            for (Map.Entry<String, String> entry : SERVER_IP_REPLACEMENTS.entrySet()) {
                result = spoofer.replace(result.chars(), entry.getKey(), entry.getValue(), result.changed());
            }
        }

        return result.changed() ? spoofer.toText(result.chars()) : text;
    }

    public static boolean spoofsPlayerName() {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        return spoofer != null && spoofer.isEnabled() && spoofer.playerName.getValue();
    }

    public static boolean spoofsServerIp() {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        return spoofer != null && spoofer.isEnabled() && spoofer.serverIp.getValue();
    }

    @Listener
    public void onRenderText(RenderTextEvent event) {
        event.setText(spoof(event.getText()));
    }

    public boolean spoofsClientBrand() {
        return this.clientBrand.getValue();
    }

    public boolean spoofsResourcePack() {
        return this.resourcePack.getValue();
    }

    public boolean protectsTranslationKeys() {
        return this.translationKey.getValue();
    }

    public boolean protectsLocalHttp() {
        return this.localHttp.getValue();
    }

    public boolean protectsFingerprinting() {
        return this.fingerprinting.getValue();
    }

    public String fakeBrand() {
        String value = this.brand.getValue().trim();
        return value.isEmpty() ? "vanilla" : value;
    }

    private String replaceSensitiveText(String text) {
        if (text == null || text.isEmpty()) return text;
        String player = this.getPlayerName();
        if (this.playerName.getValue() && player != null && !player.isEmpty()) {
            text = replace(text, player, "Hidden");
        }
        text = replaceServerIp(text);
        return text;
    }

    private String replaceServerIp(String text) {
        if (!this.serverIp.getValue() || text == null || text.isEmpty()) return text;
        for (Map.Entry<String, String> entry : SERVER_IP_REPLACEMENTS.entrySet()) {
            text = replace(text, entry.getKey(), entry.getValue());
        }
        return text;
    }

    private String replace(String input, String target, String replacement) {
        if (input == null || input.isEmpty() || target == null || target.isEmpty()) return input;

        int[] inputCodePoints = input.codePoints().toArray();
        int[] targetCodePoints = target.codePoints().toArray();
        int[] replacementCodePoints = replacement.codePoints().toArray();
        if (targetCodePoints.length == 0) return input;

        StringBuilder output = new StringBuilder(input.length());
        boolean changed = false;

        for (int i = 0; i < inputCodePoints.length; ) {
            if (matches(inputCodePoints, i, targetCodePoints)) {
                boolean upperCaseReplacement = isAllUpperCase(inputCodePoints, i, targetCodePoints.length);
                for (int replacementIndex = 0; replacementIndex < replacementCodePoints.length; replacementIndex++) {
                    output.appendCodePoint(applyCase(upperCaseReplacement, replacementCodePoints[replacementIndex]));
                }
                i += targetCodePoints.length;
                changed = true;
                continue;
            }

            output.appendCodePoint(inputCodePoints[i]);
            i++;
        }

        return changed ? output.toString() : input;
    }

    private List<StyledChar> toStyledChars(OrderedText text) {
        List<StyledChar> chars = new ArrayList<>();
        text.accept((index, style, codePoint) -> {
            chars.add(new StyledChar(codePoint, style));
            return true;
        });
        return chars;
    }

    private List<StyledChar> toStyledChars(Text text) {
        List<StyledChar> chars = new ArrayList<>();
        text.visit((style, string) -> {
            if (string != null) {
                string.codePoints().forEach(codePoint -> chars.add(new StyledChar(codePoint, style)));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return chars;
    }

    private ReplacementResult replace(List<StyledChar> input, String target, String replacement) {
        return replace(input, target, replacement, false);
    }

    private ReplacementResult replace(List<StyledChar> input, String target, String replacement, boolean alreadyChanged) {
        int[] targetCodePoints = target.codePoints().toArray();
        int[] replacementCodePoints = replacement.codePoints().toArray();
        if (targetCodePoints.length == 0) return new ReplacementResult(input, alreadyChanged);

        List<StyledChar> output = new ArrayList<>();
        boolean changed = alreadyChanged;

        for (int i = 0; i < input.size(); ) {
            if (matches(input, i, targetCodePoints)) {
                boolean upperCaseReplacement = isAllUpperCase(input, i, targetCodePoints.length);
                for (int replacementIndex = 0; replacementIndex < replacementCodePoints.length; replacementIndex++) {
                    int styleIndex = i + Math.min(replacementIndex, targetCodePoints.length - 1);
                    int codePoint = applyCase(upperCaseReplacement, replacementCodePoints[replacementIndex]);
                    output.add(new StyledChar(codePoint, input.get(styleIndex).style));
                }
                i += targetCodePoints.length;
                changed = true;
                continue;
            }

            output.add(input.get(i));
            i++;
        }

        return new ReplacementResult(output, changed);
    }

    private boolean matches(List<StyledChar> chars, int start, int[] targetCodePoints) {
        if (start + targetCodePoints.length > chars.size()) return false;
        for (int i = 0; i < targetCodePoints.length; i++) {
            if (!sameCodePoint(chars.get(start + i).codePoint, targetCodePoints[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(int[] inputCodePoints, int start, int[] targetCodePoints) {
        if (start + targetCodePoints.length > inputCodePoints.length) return false;
        for (int i = 0; i < targetCodePoints.length; i++) {
            if (!sameCodePoint(inputCodePoints[start + i], targetCodePoints[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean sameCodePoint(int left, int right) {
        return left == right || Character.toLowerCase(left) == Character.toLowerCase(right)
                || Character.toUpperCase(left) == Character.toUpperCase(right);
    }

    private boolean isAllUpperCase(List<StyledChar> input, int matchStart, int matchLength) {
        boolean hasCased = false;
        for (int i = 0; i < matchLength; i++) {
            int codePoint = input.get(matchStart + i).codePoint;
            if (!Character.isUpperCase(codePoint) && !Character.isLowerCase(codePoint) && !Character.isTitleCase(codePoint)) {
                continue;
            }

            hasCased = true;
            if (!Character.isUpperCase(codePoint) && !Character.isTitleCase(codePoint)) return false;
        }
        return hasCased;
    }

    private boolean isAllUpperCase(int[] input, int matchStart, int matchLength) {
        boolean hasCased = false;
        for (int i = 0; i < matchLength; i++) {
            int codePoint = input[matchStart + i];
            if (!Character.isUpperCase(codePoint) && !Character.isLowerCase(codePoint) && !Character.isTitleCase(codePoint)) {
                continue;
            }

            hasCased = true;
            if (!Character.isUpperCase(codePoint) && !Character.isTitleCase(codePoint)) return false;
        }
        return hasCased;
    }

    private int applyCase(boolean upperCaseReplacement, int replacementCodePoint) {
        return upperCaseReplacement ? Character.toUpperCase(replacementCodePoint) : replacementCodePoint;
    }

    private OrderedText toOrderedText(List<StyledChar> chars) {
        return visitor -> {
            int index = 0;
            for (StyledChar styledChar : chars) {
                if (!visitor.accept(index, styledChar.style, styledChar.codePoint)) {
                    return false;
                }
                index += Character.charCount(styledChar.codePoint);
            }
            return true;
        };
    }

    private Text toText(List<StyledChar> chars) {
        MutableText result = Text.empty();
        StringBuilder builder = new StringBuilder();
        Style currentStyle = null;

        for (StyledChar styledChar : chars) {
            Style nextStyle = styledChar.style == null ? Style.EMPTY : styledChar.style;
            if (currentStyle != null && !currentStyle.equals(nextStyle)) {
                result.append(Text.literal(builder.toString()).setStyle(currentStyle));
                builder.setLength(0);
            }
            currentStyle = nextStyle;
            builder.appendCodePoint(Character.isValidCodePoint(styledChar.codePoint) ? styledChar.codePoint : '?');
        }

        if (!builder.isEmpty()) {
            result.append(Text.literal(builder.toString()).setStyle(currentStyle == null ? Style.EMPTY : currentStyle));
        }
        return result;
    }

    private String getPlayerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? null : client.player.getGameProfile().name();
    }

    private static class StyledChar {
        final int codePoint;
        final Style style;

        StyledChar(int codePoint, Style style) {
            this.codePoint = codePoint;
            this.style = style;
        }
    }

    private record ReplacementResult(List<StyledChar> chars, boolean changed) {
    }
}

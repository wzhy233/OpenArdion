package io.github.wzhy233.ardion.util.input;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class KeyUtil {
    private KeyUtil() {
    }

    public static int parseKey(String input) {
        String normalized = normalize(input);
        if (normalized.length() == 1) {
            char character = normalized.charAt(0);
            if (character >= 'A' && character <= 'Z') {
                return GLFW.GLFW_KEY_A + (character - 'A');
            }
            if (character >= '0' && character <= '9') {
                return GLFW.GLFW_KEY_0 + (character - '0');
            }
        }

        return switch (normalized) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
            case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
            case "LEFT_SHIFT", "LSHIFT", "SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RIGHT_SHIFT", "RSHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "LEFT_CTRL", "LCTRL", "CTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RIGHT_CTRL", "RCTRL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "LEFT_ALT", "LALT", "ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "RIGHT_ALT", "RALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            default -> parseFunctionKey(normalized);
        };
    }

    public static String getKeyName(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return "NONE";
        }

        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + (keyCode - GLFW.GLFW_KEY_A)));
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (keyCode - GLFW.GLFW_KEY_0)));
        }
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
            return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LEFT_SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RIGHT_SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LEFT_CTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RIGHT_CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LEFT_ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RIGHT_ALT";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> "UNKNOWN(" + keyCode + ")";
        };
    }

    private static int parseFunctionKey(String normalized) {
        if (!normalized.startsWith("F")) {
            throw new IllegalArgumentException("Unknown key: " + normalized);
        }

        try {
            int index = Integer.parseInt(normalized.substring(1));
            if (index < 1 || index > 25) {
                throw new IllegalArgumentException("Unknown key: " + normalized);
            }
            return GLFW.GLFW_KEY_F1 + index - 1;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Unknown key: " + normalized, exception);
        }
    }

    private static String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return normalized.startsWith("KEY_") ? normalized.substring(4) : normalized;
    }
}

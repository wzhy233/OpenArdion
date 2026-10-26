package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

public final class BetterTab extends Module {
    private final NumberValue tabSize = this.registerValue(new NumberValue("tab-size", this, 100.0D, 1.0D, 1000.0D, true));
    private final NumberValue columnHeight = this.registerValue(new NumberValue("column-height", this, 20.0D, 1.0D, 100.0D, true));
    private final BooleanValue gamemode = this.registerValue(new BooleanValue("show-gamemode", this, true));

    public BetterTab() {
        super("better-tab", ModuleCategory.RENDER);
    }

    public int getTabSize() {
        return this.tabSize.getValue().intValue();
    }

    public int getColumnHeight() {
        return this.columnHeight.getValue().intValue();
    }

    public Text getPlayerName(PlayerListEntry entry, Text original) {
        MutableText name = original == null ? Text.literal(entry.getProfile().name()) : original.copy();
        if (this.gamemode.getValue()) {
            return Text.empty().append(name).append(Text.literal(" [" + this.getGameMode(entry.getGameMode()) + "]").formatted(Formatting.GRAY));
        }

        return name;
    }

    private String getGameMode(GameMode gameMode) {
        if (gameMode == null) {
            return "UNKNOWN";
        }
        return switch (gameMode) {
            case SPECTATOR -> "SPECTATOR";
            case SURVIVAL -> "SURVIVAL";
            case CREATIVE -> "CREATIVE";
            case ADVENTURE -> "ADVENTURE";
        };
    }
}

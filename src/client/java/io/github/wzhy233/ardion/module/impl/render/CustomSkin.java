package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.module.impl.misc.IRC;
import io.github.wzhy233.ardion.skin.CustomSkinService;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.StringValue;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

public final class CustomSkin extends Module {
    private final StringValue username = this.registerValue(new StringValue("username", this, ""));
    private final ModeValue model = this.registerValue(new ModeValue("model", this, "auto", "auto", "slim", "wide"));
    private final BooleanValue cape = this.registerValue(new BooleanValue("cape", this, true));
    private String requested;

    public CustomSkin() {
        super("custom-skin", ModuleCategory.RENDER);
    }

    public static CustomSkin get() {
        return ModuleManager.getModule(CustomSkin.class);
    }

    public static SkinTextures textures() {
        CustomSkin module = get();
        if (module == null || !module.isEnabled()) {
            return null;
        }
        return CustomSkinService.textures(module.cape.getValue(), module.modelOverride());
    }

    public boolean capeEnabled() {
        return this.cape.getValue();
    }

    public String sharedSkinName() {
        if (!isEnabled() || textures() == null) return "";
        String configured = username.getValue() == null ? "" : username.getValue().trim();
        String loaded = CustomSkinService.loadedName();
        return configured.equalsIgnoreCase(loaded) ? loaded : "";
    }

    public String modelName() {
        return model.getValue();
    }

    public static SkinTextures textures(UUID uuid, String playerName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(uuid)) return textures();
        IRC irc = ModuleManager.getModule(IRC.class);
        return irc == null ? null : irc.sharedTextures(uuid, playerName);
    }

    @Override
    protected void onEnable() {
        this.requested = null;
    }

    @Override
    protected void onDisable() {
        this.requested = null;
        CustomSkinService.clear();
    }

    @Listener
    private void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST) {
            return;
        }

        String name = this.username.getValue() == null ? "" : this.username.getValue().trim();
        if (name.equals(this.requested)) {
            CustomSkinService.textures(this.cape.getValue(), this.modelOverride());
            return;
        }

        this.requested = name;
        CustomSkinService.request(name);
    }

    private PlayerSkinType modelOverride() {
        return switch (this.model.getValue()) {
            case "slim" -> PlayerSkinType.SLIM;
            case "wide" -> PlayerSkinType.WIDE;
            default -> null;
        };
    }
}

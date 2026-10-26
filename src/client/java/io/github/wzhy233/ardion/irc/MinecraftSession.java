package io.github.wzhy233.ardion.irc;

public final class MinecraftSession {
    private final String name;
    private final String profileId;

    public MinecraftSession(String name, String profileId) {
        this.name = name;
        this.profileId = profileId;
    }

    public String getName() {
        return name;
    }

    public String getProfileId() {
        return profileId;
    }

}

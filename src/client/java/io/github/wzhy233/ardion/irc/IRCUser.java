package io.github.wzhy233.ardion.irc;

public final class IRCUser {
    private final String username;
    private final String permission;
    private final IRCPresence presence;

    public IRCUser(String username, String permission) {
        this(username, permission, "");
    }

    public IRCUser(String username, String permission, String mcName) {
        this(username, permission, new IRCPresence(mcName, null, "", false, true, "auto"));
    }

    public IRCUser(String username, String permission, IRCPresence presence) {
        this.username = username;
        this.permission = permission;
        this.presence = presence;
    }

    public String getUsername() {
        return username;
    }

    public String getPermission() {
        return permission;
    }

    public String getMcName() {
        return presence.mcName();
    }

    public IRCPresence getPresence() {
        return presence;
    }
}

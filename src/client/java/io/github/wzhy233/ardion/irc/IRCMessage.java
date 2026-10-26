package io.github.wzhy233.ardion.irc;

public final class IRCMessage {
    private final IRCUser user;
    private final String content;
    private final long timestamp;

    public IRCMessage(IRCUser user, String content, long timestamp) {
        this.user = user;
        this.content = content;
        this.timestamp = timestamp;
    }

    public IRCUser getUser() {
        return user;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }
}

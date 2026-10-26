package io.github.wzhy233.ardion.music;

public record MusicAccountLayout(float avatarX, float avatarY, float avatarSize,
                                 float textX, float textWidth, float nameY, float detailY,
                                 float actionX, float actionY, float actionRadius) {
    public static MusicAccountLayout fit(float x, float y, float width, float height) {
        float avatar = 28;
        float avatarX = x + 16;
        float centerY = y + height / 2;
        float actionX = x + width - 23;
        float textX = avatarX + avatar + 12;
        float textWidth = Math.max(0, actionX - 12 - 12 - textX);
        return new MusicAccountLayout(avatarX, centerY - avatar / 2, avatar, textX, textWidth,
                centerY - 16, centerY + 3, actionX, centerY, 12);
    }
}

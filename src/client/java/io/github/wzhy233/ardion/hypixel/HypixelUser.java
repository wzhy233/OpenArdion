package io.github.wzhy233.ardion.hypixel;

public record HypixelUser(
        String uuid,
        String networkLevel,
        String bedwarsLevel,
        String winLoseRatio,
        String finalKillDeathRatio,
        String bedsBrokeLostRatio
) {
    public static HypixelUser unknown(String uuid) {
        return new HypixelUser(uuid, "-1", "-1", "-1", "-1", "-1");
    }

    public boolean isUnknown() {
        return "-1".equals(this.networkLevel) || "-1".equals(this.bedwarsLevel);
    }
}

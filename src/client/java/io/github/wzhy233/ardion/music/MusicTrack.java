package io.github.wzhy233.ardion.music;

import io.github.wzhy233.ardion.music.netease.NeteaseSong;

public final class MusicTrack {
    public final long neteaseId;
    public final String title;
    public final String artist;
    public final String album;
    public final String coverUrl;
    public final long durationMs;

    public MusicTrack(long neteaseId, String title, String artist, String album, String coverUrl, long durationMs) {
        this.neteaseId = neteaseId;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.durationMs = durationMs;
    }

    public static MusicTrack from(NeteaseSong song) {
        return new MusicTrack(song.id, song.name, song.artist, song.album, song.coverUrl, song.durationMs);
    }

    public MusicTrack withCoverUrl(String coverUrl) {
        return new MusicTrack(neteaseId, title, artist, album, coverUrl, durationMs);
    }
}

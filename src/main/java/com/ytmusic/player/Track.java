package com.ytmusic.player;

/** Puede ser un video (videoId) o una lista de reproducción (playlistId). */
public record Track(String videoId, String playlistId, String title, String channel) {

    public static Track video(String videoId, String title, String channel) {
        return new Track(videoId, null, title, channel);
    }

    public static Track playlist(String playlistId, String title) {
        return new Track(null, playlistId, title, "");
    }

    public boolean isPlaylist() {
        return playlistId != null;
    }
}

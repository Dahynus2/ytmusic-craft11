package com.ytmusic.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ytmusic.config.ModConfig;
import com.ytmusic.player.PlayerController;
import com.ytmusic.player.Track;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Cliente mínimo de la YouTube Data API v3 usando el token OAuth de tu cuenta. */
public final class YouTubeApi {
    private YouTubeApi() {}

    public static class NotLoggedInException extends Exception {
        public NotLoggedInException() {
            super("No has conectado tu cuenta de Google");
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String BASE = "https://www.googleapis.com/youtube/v3/";

    private static JsonObject get(String endpoint, String query) throws Exception {
        String token = PlayerController.getToken();
        if (token == null) throw new NotLoggedInException();

        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + endpoint + "?" + query))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 401) {
            PlayerController.clearToken();
            throw new NotLoggedInException();
        }
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        if (res.statusCode() >= 400) {
            String msg = "Error " + res.statusCode();
            if (json.has("error") && json.getAsJsonObject("error").has("message")) {
                msg = json.getAsJsonObject("error").get("message").getAsString();
            }
            throw new IOException(msg);
        }
        return json;
    }

    public static List<Track> search(String q) throws Exception {
        String query = "part=snippet&type=video&videoEmbeddable=true&maxResults=20"
                + (ModConfig.get().musicOnly ? "&videoCategoryId=10" : "")
                + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
        JsonObject json = get("search", query);
        List<Track> out = new ArrayList<>();
        for (JsonElement el : array(json, "items")) {
            JsonObject item = el.getAsJsonObject();
            if (!item.has("id") || !item.getAsJsonObject("id").has("videoId")) continue;
            JsonObject sn = item.getAsJsonObject("snippet");
            out.add(Track.video(
                    item.getAsJsonObject("id").get("videoId").getAsString(),
                    clean(str(sn, "title")),
                    clean(str(sn, "channelTitle"))));
        }
        return out;
    }

    public static List<Track> myPlaylists() throws Exception {
        JsonObject json = get("playlists", "part=snippet&mine=true&maxResults=50");
        List<Track> out = new ArrayList<>();
        for (JsonElement el : array(json, "items")) {
            JsonObject item = el.getAsJsonObject();
            out.add(Track.playlist(item.get("id").getAsString(),
                    clean(str(item.getAsJsonObject("snippet"), "title"))));
        }
        return out;
    }

    /** Usa "LL" como playlistId para tus videos que te gustan. */
    public static List<Track> playlistItems(String playlistId) throws Exception {
        JsonObject json = get("playlistItems",
                "part=snippet&maxResults=50&playlistId=" + URLEncoder.encode(playlistId, StandardCharsets.UTF_8));
        List<Track> out = new ArrayList<>();
        for (JsonElement el : array(json, "items")) {
            JsonObject sn = el.getAsJsonObject().getAsJsonObject("snippet");
            String title = clean(str(sn, "title"));
            if (title.equals("Private video") || title.equals("Deleted video")) continue;
            if (!sn.has("resourceId")) continue;
            String channel = sn.has("videoOwnerChannelTitle") ? str(sn, "videoOwnerChannelTitle") : "";
            out.add(Track.video(sn.getAsJsonObject("resourceId").get("videoId").getAsString(),
                    title, clean(channel)));
        }
        return out;
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static String clean(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
    }
}

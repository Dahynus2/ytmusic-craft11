package com.ytmusic.player;

import com.google.gson.JsonObject;
import com.ytmusic.config.ModConfig;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Estado del reproductor + cola de comandos que la página web (reproductor oficial de YouTube)
 * recoge cada ~0,7 s.
 */
public final class PlayerController {
    private PlayerController() {}

    private static final Queue<JsonObject> COMMANDS = new ConcurrentLinkedQueue<>();

    private static volatile long lastPoll = 0;
    private static volatile long lastOpen = 0;
    private static volatile long loadedAt = 0;

    private static volatile String token;
    private static volatile long tokenExpiry = 0;

    private static volatile int ytState = -1; // -1 sin iniciar, 0 terminó, 1 reproduciendo, 2 pausa, 3 buffering
    private static volatile String title = "";
    private static volatile String currentVideoId = "";
    private static volatile double position = 0;
    private static volatile double duration = 0;

    private static List<Track> queue = List.of();
    private static int index = -1;
    private static boolean endedHandled = false;

    // ------------------------------------------------------------ sesión
    public static void setToken(String t, long expiresInSeconds) {
        token = t;
        tokenExpiry = System.currentTimeMillis() + expiresInSeconds * 1000L - 30_000L;
    }

    public static void clearToken() {
        token = null;
        tokenExpiry = 0;
    }

    public static String getToken() {
        return isLoggedIn() ? token : null;
    }

    public static boolean isLoggedIn() {
        return token != null && System.currentTimeMillis() < tokenExpiry;
    }

    // ------------------------------------------------------------ página web
    public static void touchPoll() {
        lastPoll = System.currentTimeMillis();
    }

    public static boolean isPageAlive() {
        return System.currentTimeMillis() - lastPoll < 4000;
    }

    public static JsonObject pollCommand() {
        return COMMANDS.poll();
    }

    public static void openPlayerPage() {
        lastOpen = System.currentTimeMillis();
        try {
            Util.getOperatingSystem().open(URI.create("http://localhost:" + ModConfig.get().port + "/"));
        } catch (Exception ignored) {
        }
    }

    private static void ensurePage() {
        long now = System.currentTimeMillis();
        if (!isPageAlive() && now - lastOpen > 8000) {
            openPlayerPage();
        }
    }

    // ------------------------------------------------------------ eventos desde la página
    public static synchronized void onState(JsonObject s) {
        int st = s.has("state") ? s.get("state").getAsInt() : -1;
        String vid = s.has("videoId") ? s.get("videoId").getAsString() : "";

        if (st == -100) { // error del reproductor (video no incrustable, borrado, etc.)
            if (vid.isEmpty() || vid.equals(currentVideoId)) {
                next();
            }
            return;
        }
        if (!vid.isEmpty() && !vid.equals(currentVideoId)) {
            return; // estado viejo del video anterior
        }
        ytState = st;
        position = s.has("position") ? s.get("position").getAsDouble() : 0;
        duration = s.has("duration") ? s.get("duration").getAsDouble() : 0;
        if (s.has("title") && !s.get("title").getAsString().isEmpty()) {
            title = s.get("title").getAsString();
        }
        if (st == 0 && !endedHandled && System.currentTimeMillis() - loadedAt > 3000) {
            endedHandled = true;
            next();
        }
    }

    // ------------------------------------------------------------ control
    public static synchronized void playQueue(List<Track> tracks, int start) {
        Track chosen = tracks.get(start);
        queue = tracks.stream().filter(t -> !t.isPlaylist()).toList();
        index = queue.indexOf(chosen);
        loadCurrent();
    }

    private static void loadCurrent() {
        if (index < 0 || index >= queue.size()) return;
        Track t = queue.get(index);
        endedHandled = false;
        loadedAt = System.currentTimeMillis();
        currentVideoId = t.videoId();
        title = t.title();
        ytState = -1;
        position = 0;
        JsonObject c = cmd("load");
        c.addProperty("videoId", t.videoId());
        c.addProperty("volume", ModConfig.get().volume);
        COMMANDS.add(c);
        ensurePage();
    }

    public static synchronized void next() {
        if (index + 1 < queue.size()) {
            index++;
            loadCurrent();
        } else {
            ytState = 0;
        }
    }

    public static synchronized void previous() {
        if (position > 5 && currentVideoId != null && !currentVideoId.isEmpty()) {
            JsonObject c = cmd("seek");
            c.addProperty("seconds", 0);
            COMMANDS.add(c);
        } else if (index > 0) {
            index--;
            loadCurrent();
        }
    }

    public static void toggle() {
        if (currentVideoId.isEmpty()) return;
        COMMANDS.add(cmd(ytState == 1 ? "pause" : "resume"));
        ensurePage();
    }

    public static void setVolume(int v) {
        v = Math.max(0, Math.min(100, v));
        ModConfig.get().volume = v;
        ModConfig.get().save();
        JsonObject c = cmd("volume");
        c.addProperty("volume", v);
        COMMANDS.add(c);
    }

    private static JsonObject cmd(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        return o;
    }

    // ------------------------------------------------------------ lectura para GUI / HUD
    public static String getTitle() { return title; }
    public static boolean isPlaying() { return ytState == 1; }
    public static boolean hasTrack() { return !currentVideoId.isEmpty(); }
    public static int getVolume() { return ModConfig.get().volume; }
    public static double getPosition() { return position; }
    public static double getDuration() { return duration; }
}

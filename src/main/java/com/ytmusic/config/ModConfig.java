package com.ytmusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configuración guardada en .minecraft/config/ytmusic.json */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ytmusic.json");
    private static ModConfig instance;

    /** OAuth Client ID de Google Cloud (tipo "Aplicación web"). */
    public String clientId = "";
    /** Puerto del servidor local. Debe coincidir con el origen autorizado en Google Cloud. */
    public int port = 48731;
    public int volume = 60;
    public boolean showHud = true;
    /** Limita la búsqueda a la categoría "Música" de YouTube (videoCategoryId=10). */
    public boolean musicOnly = true;

    public static synchronized ModConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ModConfig load() {
        try {
            if (Files.exists(PATH)) {
                ModConfig c = GSON.fromJson(Files.readString(PATH), ModConfig.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) {
        }
        ModConfig c = new ModConfig();
        c.save();
        return c;
    }

    public synchronized void save() {
        try {
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}

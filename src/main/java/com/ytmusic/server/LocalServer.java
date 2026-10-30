package com.ytmusic.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.ytmusic.YTMusicMod;
import com.ytmusic.config.ModConfig;
import com.ytmusic.player.PlayerController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP local (solo 127.0.0.1) que sirve la página del reproductor y recibe/entrega
 * comandos entre el juego y el navegador.
 *
 * Seguridad: las rutas /api/* exigen la cabecera personalizada X-YTM (un sitio web externo
 * no puede enviarla sin pasar por CORS, que este servidor nunca habilita) y validan el Host.
 */
public final class LocalServer {
    private LocalServer() {}

    private interface Handler {
        void handle(HttpExchange ex) throws Exception;
    }

    private static com.sun.net.httpserver.HttpServer server;

    public static void start() {
        int port = ModConfig.get().port;
        try {
            server = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            server.setExecutor(Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "ytmusic-http");
                t.setDaemon(true);
                return t;
            }));

            route("/", false, ex -> {
                String p = ex.getRequestURI().getPath();
                if (!p.equals("/") && !p.equals("/index.html")) {
                    send(ex, 404, "text/plain", "not found");
                    return;
                }
                try (InputStream in = LocalServer.class.getResourceAsStream("/web/player.html")) {
                    send(ex, 200, "text/html; charset=utf-8", in.readAllBytes());
                }
            });

            route("/api/config", true, ex -> {
                JsonObject o = new JsonObject();
                o.addProperty("clientId", ModConfig.get().clientId);
                o.addProperty("volume", ModConfig.get().volume);
                json(ex, o.toString());
            });

            route("/api/clientid", true, ex -> {
                JsonObject b = body(ex);
                ModConfig.get().clientId = b.get("clientId").getAsString().trim();
                ModConfig.get().save();
                json(ex, "{}");
            });

            route("/api/token", true, ex -> {
                JsonObject b = body(ex);
                PlayerController.setToken(b.get("token").getAsString(),
                        b.has("expiresIn") ? b.get("expiresIn").getAsLong() : 3000);
                json(ex, "{}");
            });

            route("/api/logout", true, ex -> {
                PlayerController.clearToken();
                json(ex, "{}");
            });

            route("/api/poll", true, ex -> {
                PlayerController.touchPoll();
                JsonArray arr = new JsonArray();
                JsonObject c;
                while ((c = PlayerController.pollCommand()) != null) arr.add(c);
                json(ex, arr.toString());
            });

            route("/api/state", true, ex -> {
                PlayerController.onState(body(ex));
                json(ex, "{}");
            });

            server.start();
            YTMusicMod.LOGGER.info("Servidor del reproductor en http://localhost:{}/", port);
        } catch (IOException e) {
            YTMusicMod.LOGGER.error("No se pudo iniciar el servidor local en el puerto {} (¿está en uso?)", port, e);
        }
    }

    public static void stop() {
        if (server != null) server.stop(0);
    }

    // ------------------------------------------------------------ utilidades
    private static void route(String path, boolean api, Handler h) {
        server.createContext(path, ex -> {
            try {
                if (!hostOk(ex) || (api && !"1".equals(ex.getRequestHeaders().getFirst("X-YTM")))) {
                    send(ex, 403, "text/plain", "forbidden");
                    return;
                }
                h.handle(ex);
            } catch (Exception e) {
                try {
                    send(ex, 500, "text/plain", "error");
                } catch (IOException ignored) {
                }
            } finally {
                ex.close();
            }
        });
    }

    private static boolean hostOk(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        return host != null && (host.startsWith("localhost:") || host.startsWith("127.0.0.1:"));
    }

    private static JsonObject body(HttpExchange ex) throws IOException {
        String s = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return JsonParser.parseString(s.isEmpty() ? "{}" : s).getAsJsonObject();
    }

    private static void json(HttpExchange ex, String s) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", s);
    }

    private static void send(HttpExchange ex, int code, String type, String s) throws IOException {
        send(ex, code, type, s.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }
}

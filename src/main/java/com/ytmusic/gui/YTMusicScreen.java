package com.ytmusic.gui;

import com.ytmusic.api.YouTubeApi;
import com.ytmusic.player.PlayerController;
import com.ytmusic.player.Track;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class YTMusicScreen extends Screen {
    private interface Job {
        List<Track> run() throws Exception;
    }

    private static final int PANEL_W = 320;

    // Estado que sobrevive al cerrar/abrir el menú
    private static List<Track> results = List.of();
    private static String listTitle = "";
    private static String lastQuery = "";
    private static String status = "";
    private static int page = 0;

    private TextFieldWidget searchField;
    private int rows = 5;
    private int pagerY, nowY;

    public YTMusicScreen() {
        super(Text.literal("YT Music Craft"));
    }

    @Override
    protected void init() {
        int left = width / 2 - PANEL_W / 2;

        searchField = new TextFieldWidget(textRenderer, left, 22, PANEL_W - 82, 20, Text.literal("Buscar"));
        searchField.setMaxLength(100);
        searchField.setText(lastQuery);
        searchField.setPlaceholder(Text.literal("Buscar música o videos..."));
        addDrawableChild(searchField);
        setInitialFocus(searchField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Buscar"), b -> doSearch())
                .dimensions(left + PANEL_W - 78, 22, 78, 20).build());

        int bw = (PANEL_W - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Mis listas"),
                b -> load(YouTubeApi::myPlaylists, "Mis listas"))
                .dimensions(left, 46, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Me gusta"),
                b -> load(() -> YouTubeApi.playlistItems("LL"), "Videos que me gustan"))
                .dimensions(left + bw + 4, 46, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Conectar / Abrir"),
                b -> PlayerController.openPlayerPage())
                .dimensions(left + 2 * (bw + 4), 46, bw, 20).build());

        // Resultados
        rows = Math.max(3, Math.min(10, (height - 84 - 82) / 21));
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= results.size()) break;
            Track t = results.get(idx);
            String label = (t.isPlaylist() ? "[Lista] " : "") + t.title()
                    + (t.channel().isEmpty() ? "" : "  -  " + t.channel());
            addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label, PANEL_W - 12)),
                    b -> onPick(t, idx)).dimensions(left, 84 + i * 21, PANEL_W, 20).build());
        }

        pagerY = 84 + rows * 21 + 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> changePage(-1))
                .dimensions(left, pagerY, 30, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> changePage(1))
                .dimensions(left + PANEL_W - 30, pagerY, 30, 18).build());

        nowY = pagerY + 24;
        int cy = nowY + 14;
        int x = left;
        x = addCtl(x, cy, 48, "|<", PlayerController::previous);
        x = addCtl(x, cy, 80, "Play/Pausa", PlayerController::toggle);
        x = addCtl(x, cy, 48, ">|", PlayerController::next);
        x = addCtl(x, cy, 60, "Vol -", () -> PlayerController.setVolume(PlayerController.getVolume() - 10));
        addCtl(x, cy, 60, "Vol +", () -> PlayerController.setVolume(PlayerController.getVolume() + 10));
    }

    private int addCtl(int x, int y, int w, String label, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> r.run())
                .dimensions(x, y, w, 20).build());
        return x + w + 4;
    }

    private void doSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) return;
        lastQuery = q;
        load(() -> YouTubeApi.search(q), "Resultados: " + q);
    }

    private void onPick(Track t, int idx) {
        if (t.isPlaylist()) {
            load(() -> YouTubeApi.playlistItems(t.playlistId()), t.title());
        } else {
            PlayerController.playQueue(results, idx);
            status = "Reproduciendo: " + t.title();
        }
    }

    private void changePage(int d) {
        int pages = Math.max(1, (int) Math.ceil(results.size() / (double) rows));
        int np = Math.max(0, Math.min(pages - 1, page + d));
        if (np != page) {
            page = np;
            lastQuery = searchField.getText();
            clearAndInit();
        }
    }

    private void load(Job job, String title) {
        status = "Cargando...";
        CompletableFuture.supplyAsync(() -> {
            try {
                return job.run();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).whenComplete((list, err) -> MinecraftClient.getInstance().execute(() -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                status = c instanceof YouTubeApi.NotLoggedInException
                        ? "Conecta tu cuenta: pulsa 'Conectar / Abrir'"
                        : "Error: " + c.getMessage();
            } else {
                results = list;
                listTitle = title;
                page = 0;
                status = title + " (" + list.size() + ")";
                if (list.isEmpty()) status = title + ": sin resultados";
            }
            if (MinecraftClient.getInstance().currentScreen == this) {
                lastQuery = searchField.getText();
                clearAndInit();
            }
        }));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (searchField != null && searchField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            doSearch();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        String conn = PlayerController.isLoggedIn() ? "Cuenta conectada" : "Cuenta NO conectada";
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("YT Music Craft  -  " + conn), width / 2, 8, 0xFFFFFFFF);

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(textRenderer.trimToWidth(status, PANEL_W)), width / 2, 70, 0xFFAAAAAA);

        int pages = Math.max(1, (int) Math.ceil(results.size() / (double) rows));
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal((page + 1) + " / " + pages), width / 2, pagerY + 5, 0xFFFFFFFF);

        String now;
        if (!PlayerController.hasTrack()) {
            now = "Nada en reproducción";
        } else {
            now = (PlayerController.isPlaying() ? "> " : "|| ") + PlayerController.getTitle();
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(textRenderer.trimToWidth(now + "   [Vol " + PlayerController.getVolume() + "%]", PANEL_W)),
                width / 2, nowY, 0xFF55FF55);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

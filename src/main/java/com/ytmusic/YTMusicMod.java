package com.ytmusic;

import com.ytmusic.config.ModConfig;
import com.ytmusic.gui.YTMusicScreen;
import com.ytmusic.player.PlayerController;
import com.ytmusic.server.LocalServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YTMusicMod implements ClientModInitializer {
    public static final String MOD_ID = "ytmusic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openKey;
    private static KeyBinding toggleKey;
    private static KeyBinding nextKey;
    private static KeyBinding prevKey;

    @Override
    public void onInitializeClient() {
        ModConfig.get();
        LocalServer.start();

        final KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.ytmusic.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, cat));
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.ytmusic.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_5, cat));
        nextKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.ytmusic.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_6, cat));
        prevKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.ytmusic.prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_4, cat));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) client.setScreen(new YTMusicScreen());
            while (toggleKey.wasPressed()) PlayerController.toggle();
            while (nextKey.wasPressed()) PlayerController.next();
            while (prevKey.wasPressed()) PlayerController.previous();
        });

        // Mini HUD "ahora suena" arriba a la izquierda
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(MOD_ID, "now_playing"), (ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!ModConfig.get().showHud || mc.options.hudHidden || !PlayerController.hasTrack()) return;
            String icon = PlayerController.isPlaying() ? "> " : "|| ";
            String text = mc.textRenderer.trimToWidth(icon + PlayerController.getTitle(), 220);
            ctx.drawTextWithShadow(mc.textRenderer, text, 6, 6, 0xFFFFFFFF);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LocalServer.stop();
            ModConfig.get().save();
        });
    }
}

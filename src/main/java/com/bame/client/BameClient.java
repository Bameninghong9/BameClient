package com.bame.client;

import com.bame.client.gui.BameClientScreen;
import com.bame.client.module.*;
import com.bame.client.render.AreaRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BameClient implements ClientModInitializer {
    private static boolean menuWasPressed = false;
    private static boolean keyStrokesWasPressed = false;
    private static boolean fullbrightWasPressed = false;
    private static boolean zoomWasPressed = false;
    private static boolean fpsWasPressed = false;
    private static boolean pingWasPressed = false;
    private static boolean cpsWasPressed = false;

    public static final Logger LOGGER = LoggerFactory.getLogger("bameclient");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing BameClient!");
        BameClientConfig.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("bameclient")
                .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.send(() -> client.setScreen(new BameClientScreen()));
                    return 1;
                }));
        });
        
        // Client Tick Events
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AutoAreaMinerModule.onTick(client);
            FullbrightModule.onTick(client);
            
            if (client.getWindow() != null && client.currentScreen == null) {
                // KeyStrokes bind
                if (KeyStrokesModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), KeyStrokesModule.keyBind);
                    if (down && !keyStrokesWasPressed) {
                        KeyStrokesModule.enabled = !KeyStrokesModule.enabled;
                        BameClientConfig.save();
                    }
                    keyStrokesWasPressed = down;
                }

                // Fullbright bind
                if (FullbrightModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), FullbrightModule.keyBind);
                    if (down && !fullbrightWasPressed) {
                        FullbrightModule.enabled = !FullbrightModule.enabled;
                        BameClientConfig.save();
                    }
                    fullbrightWasPressed = down;
                }

                // Zoom bind
                if (ZoomModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), ZoomModule.keyBind);
                    if (down && !zoomWasPressed) {
                        ZoomModule.enabled = !ZoomModule.enabled;
                        BameClientConfig.save();
                    }
                    zoomWasPressed = down;
                }

                // FPS bind
                if (FpsModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), FpsModule.keyBind);
                    if (down && !fpsWasPressed) {
                        FpsModule.enabled = !FpsModule.enabled;
                        BameClientConfig.save();
                    }
                    fpsWasPressed = down;
                }

                // Ping bind
                if (PingModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), PingModule.keyBind);
                    if (down && !pingWasPressed) {
                        PingModule.enabled = !PingModule.enabled;
                        BameClientConfig.save();
                    }
                    pingWasPressed = down;
                }

                // CPS bind
                if (CpsModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), CpsModule.keyBind);
                    if (down && !cpsWasPressed) {
                        CpsModule.enabled = !CpsModule.enabled;
                        BameClientConfig.save();
                    }
                    cpsWasPressed = down;
                }

                // Menu bind
                if (BameClientConfig.menuBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), BameClientConfig.menuBind);
                    if (down && !menuWasPressed) {
                        client.setScreen(new BameClientScreen());
                    }
                    menuWasPressed = down;
                }
            }
        });
        
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.bame.client.render.KeyStrokesRenderer());
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.bame.client.render.StatusHudRenderer());
        
        WorldRenderEvents.END_MAIN.register(context -> {
            AreaRenderer.render(context);
        });
    }
}

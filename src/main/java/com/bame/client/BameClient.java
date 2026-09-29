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
    private static boolean serverInfoWasPressed = false;
    private static boolean nameProtectWasPressed = false;
    private static boolean showHudWasPressed = false;
    private static boolean fakeScoreboardWasPressed = false;
    private static boolean spotifyHudWasPressed = false;
    private static boolean scoreboardWasPressed = false;
    private static boolean crosshairWasPressed = false;

    public static final Logger LOGGER = LoggerFactory.getLogger("bameclient");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing BameClient!");
        BameClientConfig.load();
        if (SpotifyHudModule.enabled) {
            com.bame.client.spotify.SpotifyService.start();
        }
        HitColorModule.apply();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("caeserclient")
                .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.send(() -> client.setScreen(new BameClientScreen()));
                    return 1;
                }));
        });
        
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && TargetHudModule.enabled && entity instanceof net.minecraft.entity.LivingEntity living) {
                if (!TargetHudModule.playersOnly || living instanceof net.minecraft.entity.player.PlayerEntity) {
                    TargetHudModule.currentTarget = living;
                    TargetHudModule.lastTargetTime = System.currentTimeMillis();
                }
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        // Client Tick Events
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AutoAreaMinerModule.onTick(client);
            FullbrightModule.onTick(client);

            TargetHudModule.onClientTick(client);
            AutoClickerModule.onTick(client);
            HitColorModule.onTick(client);
            ReachDisplayModule.onTick(client);
            LowShieldModule.onTick(client);
            
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

                // ServerInfo bind
                if (ServerInfoModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), ServerInfoModule.keyBind);
                    if (down && !serverInfoWasPressed) {
                        ServerInfoModule.enabled = !ServerInfoModule.enabled;
                        BameClientConfig.save();
                    }
                    serverInfoWasPressed = down;
                }

                // NameProtect bind
                if (NameProtectModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), NameProtectModule.keyBind);
                    if (down && !nameProtectWasPressed) {
                        NameProtectModule.enabled = !NameProtectModule.enabled;
                        BameClientConfig.save();
                    }
                    nameProtectWasPressed = down;
                }

                // Show HUD bind
                if (ShowHudModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), ShowHudModule.keyBind);
                    if (down && !showHudWasPressed) {
                        ShowHudModule.enabled = !ShowHudModule.enabled;
                        BameClientConfig.save();
                    }
                    showHudWasPressed = down;
                }

                // FakeScoreboard bind
                if (com.bame.client.module.FakeScoreboardModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.FakeScoreboardModule.keyBind);
                    if (down && !fakeScoreboardWasPressed) {
                        com.bame.client.module.FakeScoreboardModule.enabled = !com.bame.client.module.FakeScoreboardModule.enabled;
                        BameClientConfig.save();
                    }
                    fakeScoreboardWasPressed = down;
                }

                // Spotify HUD bind
                if (SpotifyHudModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), SpotifyHudModule.keyBind);
                    if (down && !spotifyHudWasPressed) {
                        SpotifyHudModule.enabled = !SpotifyHudModule.enabled;
                        if (SpotifyHudModule.enabled) {
                            com.bame.client.spotify.SpotifyService.start();
                        }
                        BameClientConfig.save();
                    }
                    spotifyHudWasPressed = down;
                }

                // Scoreboard bind
                if (com.bame.client.module.ScoreboardModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.ScoreboardModule.keyBind);
                    if (down && !scoreboardWasPressed) {
                        com.bame.client.module.ScoreboardModule.enabled = !com.bame.client.module.ScoreboardModule.enabled;
                        BameClientConfig.save();
                    }
                    scoreboardWasPressed = down;
                }

                // Custom Crosshair bind
                if (com.bame.client.module.CustomCrosshairModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.CustomCrosshairModule.keyBind);
                    if (down && !crosshairWasPressed) {
                        com.bame.client.module.CustomCrosshairModule.enabled = !com.bame.client.module.CustomCrosshairModule.enabled;
                        BameClientConfig.save();
                    }
                    crosshairWasPressed = down;
                }

                // Menu bind
                if (BameClientConfig.menuBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), BameClientConfig.menuBind);
                    if (down && !menuWasPressed) {
                        client.setScreen(new BameClientScreen());
                    }
                    menuWasPressed = down;
                }

                // InvMove tick (keybind & arrow navigation)
                InvMoveModule.onTick(client);
            }
        });
        
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.bame.client.render.KeyStrokesRenderer());
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.bame.client.render.StatusHudRenderer());
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.bame.client.render.FakeScoreboardRenderer());
        
        WorldRenderEvents.END_MAIN.register(context -> {
            AreaRenderer.render(context);
        });
    }
}

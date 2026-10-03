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
    private static boolean timeChangerWasPressed = false;
    private static boolean skinProtectWasPressed = false;
    private static boolean secretComboWasPressed = false;

    public static final Logger LOGGER = LoggerFactory.getLogger("bameclient");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing BameClient!");
        BameClientConfig.load();
        if (SpotifyHudModule.enabled) {
            com.bame.client.spotify.SpotifyService.start();
        }
        HitColorModule.apply();
        com.bame.client.wallpaper.WallpaperManager.getWallpaperDir();
        try {
            net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.SOUND_EVENT, DurabilityGuardModule.ALARM_ID, DurabilityGuardModule.ALARM_SOUND);
        } catch (Exception ignored) {}

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
            CustomHitboxesModule.onTick(client);
            AutoToolModule.onTick(client);
            NoFogModule.onTick(client);
            BlockOutlineModule.onTick(client);
            FreelookModule.onTick(client);
            ItemSizeModule.onTick(client);
            DurabilityGuardModule.onTick(client);
            ZoomModule.onTick(client);
            com.bame.client.module.PearlPredictionModule.onTick(client);
            com.bame.client.module.AutoCartModule.onTick(client);
            com.bame.client.module.AutoMaceModule.onTick(client);
            com.bame.client.module.DTapModule.onTick(client);
            com.bame.client.module.PearlCatchModule.onTick(client);
            com.bame.client.module.AggroPearlModule.onTick(client);
            
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

                // Time Changer bind
                if (com.bame.client.module.TimeChangerModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.TimeChangerModule.keyBind);
                    if (down && !timeChangerWasPressed) {
                        com.bame.client.module.TimeChangerModule.enabled = !com.bame.client.module.TimeChangerModule.enabled;
                        BameClientConfig.save();
                    }
                    timeChangerWasPressed = down;
                }

                // SkinProtect bind
                if (com.bame.client.module.SkinProtectModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.SkinProtectModule.keyBind);
                    if (down && !skinProtectWasPressed) {
                        com.bame.client.module.SkinProtectModule.enabled = !com.bame.client.module.SkinProtectModule.enabled;
                        BameClientConfig.save();
                    }
                    skinProtectWasPressed = down;
                }

                // Menu bind
                if (BameClientConfig.menuBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), BameClientConfig.menuBind);
                    if (down && !menuWasPressed) {
                        client.setScreen(new BameClientScreen());
                    }
                    menuWasPressed = down;
                }

                // Secret unlock combo bind
                if (BameClientConfig.secretKey != -1 && client.getWindow() != null) {
                    long handle = client.getWindow().getHandle();
                    boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                            || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                    boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                            || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                    boolean alt = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                            || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                    boolean key = InputUtil.isKeyPressed(client.getWindow(), BameClientConfig.secretKey);
                    if (key && !secretComboWasPressed) {
                        if (shift == BameClientConfig.secretRequireShift
                                && ctrl == BameClientConfig.secretRequireCtrl
                                && alt == BameClientConfig.secretRequireAlt) {
                            BameClientConfig.secretUnlocked = !BameClientConfig.secretUnlocked;
                            com.bame.client.sound.ClientSoundManager.playClick();
                        }
                    }
                    secretComboWasPressed = key;
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
            com.bame.client.render.PearlPredictionRenderer.render(context);
        });
    }
}

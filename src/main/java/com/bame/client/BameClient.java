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
    private static boolean emoteWasPressed = false;

    public static final Logger LOGGER = LoggerFactory.getLogger("bameclient");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing BameClient!");
        BameClientConfig.load();
        com.bame.client.cosmetics.CosmeticsConfig.load();
        com.bame.client.emotes.EmoteUnlockManager.init();
        com.bame.client.cosmetics.CaeserPlayerSyncManager.init();
        // Taste „High-Five annehmen“ (Standard F) in den Minecraft-Steuerungen
        com.bame.client.emotes.HighFiveCoordinator.registerKeyBinding();
        if (SpotifyHudModule.enabled) {
            com.bame.client.spotify.SpotifyService.start();
        }
        HitColorModule.apply();
        com.bame.client.wallpaper.WallpaperManager.getWallpaperDir();
        com.bame.client.cosmetics.CaeserSkinManager.reloadFromLauncher();
        MinecraftClient.getInstance().send(() -> {
            try {
                MinecraftClient.getInstance().updateWindowTitle();
            } catch (Throwable ignored) {}
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            com.bame.client.cosmetics.CaeserSkinManager.syncOnServerJoin();
            com.bame.client.emotes.EmoteUnlockManager.forceRefresh();
            com.bame.client.cosmetics.CaeserPlayerSyncManager.onServerJoin();
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(com.bame.client.cosmetics.CaeserPlayerSyncManager::onServerLeave));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> {
                    com.bame.client.killfx.KillDetector.reset();
                    com.bame.client.killfx.KillEffects.clear();
                }));
        // Jeton-Shop: nach dem Client-Start (Session und API-Services sind dann sicher da) Cache laden und anmelden
        // Config: ausstehende Änderungen beim Beenden synchron sichern (nichts geht verloren)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                BameClientConfig.saveNow();
            } catch (Throwable t) {
                LOGGER.warn("Caeser Client config could not be saved on shutdown", t);
            }
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                com.bame.client.shop.Wallet.init();
                com.bame.client.shop.CaeserAuth.login(true).exceptionally(t -> null);
            } catch (Throwable t) {
                LOGGER.warn("Caeser backend login could not be started ({})", t.getClass().getSimpleName());
            }
        });
        // Account-Switcher: Knopf oben links im Multiplayer-Menü (ohne Mixin in MultiplayerScreen)
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen) {
                try {
                    com.bame.client.account.AccountButtonWidget.attach(screen);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to add account button to multiplayer screen", t);
                }
            }
        });
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
            dispatcher.register(ClientCommandManager.literal("cosmetics")
                .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.send(() -> {
                        // Direkt die Cosmetics-Ansicht öffnen (nicht den Hub)
                        BameClientScreen.currentView = BameClientScreen.ViewMode.COSMETICS;
                        com.bame.client.gui.tab.CosmeticsTab.subCategory = "ALL";
                        client.setScreen(new BameClientScreen());
                    });
                    return 1;
                }));
            dispatcher.register(ClientCommandManager.literal("emotes")
                .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.send(() -> client.setScreen(new com.bame.client.gui.EmoteWheelScreen()));
                    return 1;
                }));
            dispatcher.register(ClientCommandManager.literal("caeser_open_screenshot")
                .then(ClientCommandManager.argument("path", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(context -> {
                        String path = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "path");
                        com.bame.client.util.ScreenshotHelper.openScreenshot(path);
                        return 1;
                    })));
            dispatcher.register(ClientCommandManager.literal("caeser_copy_screenshot")
                .then(ClientCommandManager.argument("path", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(context -> {
                        String path = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "path");
                        com.bame.client.util.ScreenshotHelper.copyToClipboard(path);
                        return 1;
                    })));
            dispatcher.register(ClientCommandManager.literal("caeser_del_screenshot")
                .then(ClientCommandManager.argument("path", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(context -> {
                        String path = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "path");
                        com.bame.client.util.ScreenshotHelper.deleteScreenshot(path);
                        return 1;
                    })));
        });

        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, livingEntityRenderer, registrationHelper, context) -> {
            if (livingEntityRenderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer playerRenderer) {
                registrationHelper.register(new com.bame.client.cosmetics.CaeserCosmeticsRenderer(playerRenderer));
                registrationHelper.register(new com.bame.client.emotes.EmotePropFeatureRenderer(playerRenderer));
            }
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
            // Gebündeltes Speichern ~1 s nach der letzten Änderung (Schreiben im Hintergrund)
            BameClientConfig.tick();
            com.bame.client.cosmetics.CaeserSkinManager.checkPeriodicSync();
            com.bame.client.cosmetics.CaeserPlayerSyncManager.tick(client);
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
            com.bame.client.module.HandPositionModule.onTick(client);
            DurabilityGuardModule.onTick(client);
            ZoomModule.onTick(client);
            com.bame.client.module.PearlPredictionModule.onTick(client);
            com.bame.client.module.AutoCartModule.onTick(client);
            com.bame.client.module.AutoMaceModule.onTick(client);
            com.bame.client.module.DTapModule.onTick(client);
            com.bame.client.module.PearlCatchModule.onTick(client);
            com.bame.client.module.AggroPearlModule.onTick(client);
            com.bame.client.module.TriggerbotModule.onTick(client);
            com.bame.client.module.ShieldBreakerModule.onTick(client);
            com.bame.client.module.EmoteModule.onTick(client);
            com.bame.client.emotes.HighFiveCoordinator.tick(client);
            com.bame.client.emotes.CampfireCoordinator.tick(client);
            com.bame.client.emotes.HammockCoordinator.tick(client);
            com.bame.client.emotes.SwingCoordinator.tick(client);
            com.bame.client.emotes.PicnicCoordinator.tick(client);
            com.bame.client.emotes.EmoteWorldEffects.tick(client);
            com.bame.client.killfx.KillDetector.tick(client);
            com.bame.client.killfx.KillEffects.tick(client);

            if (client.getWindow() != null && client.currentScreen == null) {
                // Emote Wheel bind
                if (com.bame.client.module.EmoteModule.enabled && com.bame.client.module.EmoteModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.EmoteModule.keyBind);
                    if (down && !emoteWasPressed) {
                        client.setScreen(new com.bame.client.gui.EmoteWheelScreen());
                    }
                    emoteWasPressed = down;
                }

                // KeyStrokes bind
                if (KeyStrokesModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), KeyStrokesModule.keyBind);
                    if (down && !keyStrokesWasPressed) {
                        KeyStrokesModule.enabled = !KeyStrokesModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Key Strokes", KeyStrokesModule.enabled);
                        BameClientConfig.save();
                    }
                    keyStrokesWasPressed = down;
                }

                // FPS bind
                if (FpsModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), FpsModule.keyBind);
                    if (down && !fpsWasPressed) {
                        FpsModule.enabled = !FpsModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("FPS", FpsModule.enabled);
                        BameClientConfig.save();
                    }
                    fpsWasPressed = down;
                }

                // Ping bind
                if (PingModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), PingModule.keyBind);
                    if (down && !pingWasPressed) {
                        PingModule.enabled = !PingModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Ping", PingModule.enabled);
                        BameClientConfig.save();
                    }
                    pingWasPressed = down;
                }

                // CPS bind
                if (CpsModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), CpsModule.keyBind);
                    if (down && !cpsWasPressed) {
                        CpsModule.enabled = !CpsModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("CPS", CpsModule.enabled);
                        BameClientConfig.save();
                    }
                    cpsWasPressed = down;
                }

                // ServerInfo bind
                if (ServerInfoModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), ServerInfoModule.keyBind);
                    if (down && !serverInfoWasPressed) {
                        ServerInfoModule.enabled = !ServerInfoModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Server Info", ServerInfoModule.enabled);
                        BameClientConfig.save();
                    }
                    serverInfoWasPressed = down;
                }

                // NameProtect bind
                if (NameProtectModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), NameProtectModule.keyBind);
                    if (down && !nameProtectWasPressed) {
                        NameProtectModule.enabled = !NameProtectModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Name Protect", NameProtectModule.enabled);
                        BameClientConfig.save();
                    }
                    nameProtectWasPressed = down;
                }

                // Show HUD bind
                if (ShowHudModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), ShowHudModule.keyBind);
                    if (down && !showHudWasPressed) {
                        ShowHudModule.enabled = !ShowHudModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("HUD", ShowHudModule.enabled);
                        BameClientConfig.save();
                    }
                    showHudWasPressed = down;
                }

                // FakeScoreboard bind
                if (com.bame.client.module.FakeScoreboardModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.FakeScoreboardModule.keyBind);
                    if (down && !fakeScoreboardWasPressed) {
                        com.bame.client.module.FakeScoreboardModule.enabled = !com.bame.client.module.FakeScoreboardModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Fake Scoreboard", com.bame.client.module.FakeScoreboardModule.enabled);
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
                        com.bame.client.notification.NotificationManager.showModuleToggle("Spotify HUD", SpotifyHudModule.enabled);
                        BameClientConfig.save();
                    }
                    spotifyHudWasPressed = down;
                }

                // Scoreboard bind
                if (com.bame.client.module.ScoreboardModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.ScoreboardModule.keyBind);
                    if (down && !scoreboardWasPressed) {
                        com.bame.client.module.ScoreboardModule.enabled = !com.bame.client.module.ScoreboardModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Scoreboard", com.bame.client.module.ScoreboardModule.enabled);
                        BameClientConfig.save();
                    }
                    scoreboardWasPressed = down;
                }

                // Custom Crosshair bind
                if (com.bame.client.module.CustomCrosshairModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.CustomCrosshairModule.keyBind);
                    if (down && !crosshairWasPressed) {
                        com.bame.client.module.CustomCrosshairModule.enabled = !com.bame.client.module.CustomCrosshairModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Crosshair", com.bame.client.module.CustomCrosshairModule.enabled);
                        BameClientConfig.save();
                    }
                    crosshairWasPressed = down;
                }

                // Time Changer bind
                if (com.bame.client.module.TimeChangerModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.TimeChangerModule.keyBind);
                    if (down && !timeChangerWasPressed) {
                        com.bame.client.module.TimeChangerModule.enabled = !com.bame.client.module.TimeChangerModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Time Changer", com.bame.client.module.TimeChangerModule.enabled);
                        BameClientConfig.save();
                    }
                    timeChangerWasPressed = down;
                }

                // SkinProtect bind
                if (com.bame.client.module.SkinProtectModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.SkinProtectModule.keyBind);
                    if (down && !skinProtectWasPressed) {
                        com.bame.client.module.SkinProtectModule.enabled = !com.bame.client.module.SkinProtectModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Skin Protect", com.bame.client.module.SkinProtectModule.enabled);
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
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.bame.client.notification.NotificationManager());
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.bame.client.render.HighFivePromptRenderer());
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.bame.client.killfx.KillEffectHud());

        // Kill-Effekte Sarg und Amboss (mit Opfer-Figur): zusammen mit den Entities zeichnen
        WorldRenderEvents.BEFORE_ENTITIES.register(com.bame.client.killfx.KillEffects::submitWorld);

        WorldRenderEvents.END_MAIN.register(context -> {
            AreaRenderer.render(context);
            com.bame.client.render.PearlPredictionRenderer.render(context);
            com.bame.client.killfx.KillEffects.renderWorld(context);
        });

        // Kill-Effekte: eigene Projektile (Fernkampf-Treffer) und Kill-Nachrichten im Chat
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register(
                com.bame.client.killfx.KillDetector::onEntityLoad);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register(
                com.bame.client.killfx.KillDetector::onEntityUnload);
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                com.bame.client.killfx.KillDetector.onChatMessage(message));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
            // Nur Nachrichten ohne Spieler als Absender (Server-/Plugin-Meldungen); Spieler-Chat könnte Kills vortäuschen
            if (sender == null) com.bame.client.killfx.KillDetector.onChatMessage(message);
        });
    }
}

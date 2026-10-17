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
    private static boolean hideArmorWasPressed = false;

    public static final Logger LOGGER = LoggerFactory.getLogger("bameclient");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing BameClient!");
        BameClientConfig.load();
        // Diagnose: Frames > 25 ms mit Stack-Samples nach config/bameclient/hitches.log (Schalter hitchLogger)
        com.bame.client.util.HitchSampler.start(BameClientConfig.hitchLogger);
        // Klassen des Mod-Menüs/Emote-Rads im Hintergrund vorladen (kein Klassenladen beim ersten Öffnen im Spiel)
        com.bame.client.gui.GuiWarmup.preloadInBackground();
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
            com.bame.client.util.Prof.push("caeser_server_join");
            try {
                com.bame.client.cosmetics.CaeserSkinManager.syncOnServerJoin();
                com.bame.client.emotes.EmoteUnlockManager.forceRefresh();
                com.bame.client.cosmetics.CaeserPlayerSyncManager.onServerJoin();
                // Ladebildschirm: statische Initialisierung der Menü-Klassen jetzt statt beim ersten Öffnen im Spiel
                com.bame.client.gui.GuiWarmup.initializeOnce();
                com.bame.client.wallpaper.WallpaperManager.prefetch();
                // Sprachausgabe an? Einmal pro Spielsitzung ein Hinweis-Toast, sobald man im Spiel ist (siehe tick)
                com.bame.client.util.NarratorGuard.onJoin();
            } finally {
                com.bame.client.util.Prof.pop();
            }
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(com.bame.client.util.Prof.task("caeser_task_sync_server_leave",
                        com.bame.client.cosmetics.CaeserPlayerSyncManager::onServerLeave)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(com.bame.client.util.Prof.task("caeser_task_killfx_reset", () -> {
                    com.bame.client.killfx.KillDetector.reset();
                    com.bame.client.killfx.KillEffects.clear();
                })));
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
        // Läuft nach dem init() jedes Screens (Inventar, Esc-Menü, ...): eigener Abschnitt, damit Messungen ihn benennen
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            com.bame.client.util.Prof.push("caeser_screen_after_init");
            try {
                if (screen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen) {
                    com.bame.client.util.Prof.push("caeser_screen_account_button");
                    try {
                        com.bame.client.account.AccountButtonWidget.attach(screen);
                    } catch (Throwable t) {
                        LOGGER.warn("Failed to add account button to multiplayer screen", t);
                    } finally {
                        com.bame.client.util.Prof.pop();
                    }
                } else if (screen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
                    // Server wechseln: Weltkugel oben rechts im Esc-Menü öffnet die Server-Liste
                    try {
                        com.bame.client.gui.ServerSwitchButtonWidget.attach(screen);
                    } catch (Throwable t) {
                        LOGGER.warn("Failed to add server switch button to game menu", t);
                    }
                }
            } finally {
                com.bame.client.util.Prof.pop();
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
                // Emote-Props mit eigenem Profiler-Abschnitt (Unterklasse nur für push/pop, Verhalten unverändert)
                registrationHelper.register(new com.bame.client.emotes.EmotePropFeatureRenderer(playerRenderer) {
                    @Override
                    public void render(net.minecraft.client.util.math.MatrixStack matrices,
                                       net.minecraft.client.render.command.OrderedRenderCommandQueue queue, int light,
                                       net.minecraft.client.render.entity.state.PlayerEntityRenderState state,
                                       float limbAngle, float limbDistance) {
                        net.minecraft.util.profiler.Profiler p = net.minecraft.util.profiler.Profilers.get();
                        p.push("caeser_emote_props");
                        try {
                            super.render(matrices, queue, light, state, limbAngle, limbDistance);
                        } finally {
                            p.pop();
                        }
                    }
                });
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
            // Profiler-Abschnitte (F3+L): jeder Teil einzeln unter tick > caeser_tick > caeser_<name>.
            // swap() hält die Tiefe konstant; die beiden finally-Blöcke schließen beide Ebenen auch bei Ausnahmen.
            net.minecraft.util.profiler.Profiler prof = net.minecraft.util.profiler.Profilers.get();
            prof.push("caeser_tick");
            try {
            prof.push("caeser_config");
            try {
            // Gebündeltes Speichern ~1 s nach der letzten Änderung (Schreiben im Hintergrund)
            BameClientConfig.tick();
            com.bame.client.cosmetics.GuiPreviewContext.reset();
            prof.swap("caeser_skin_sync");
            com.bame.client.cosmetics.CaeserSkinManager.checkPeriodicSync();
            prof.swap("caeser_player_sync");
            com.bame.client.cosmetics.CaeserPlayerSyncManager.tick(client);
            prof.swap("caeser_auto_area_miner");
            AutoAreaMinerModule.onTick(client);
            prof.swap("caeser_fullbright");
            FullbrightModule.onTick(client);

            prof.swap("caeser_target_hud");
            TargetHudModule.onClientTick(client);
            prof.swap("caeser_auto_clicker");
            AutoClickerModule.onTick(client);
            prof.swap("caeser_hit_color");
            HitColorModule.onTick(client);
            prof.swap("caeser_reach_display");
            ReachDisplayModule.onTick(client);
            prof.swap("caeser_low_shield");
            LowShieldModule.onTick(client);
            prof.swap("caeser_custom_hitboxes");
            CustomHitboxesModule.onTick(client);
            prof.swap("caeser_auto_tool");
            AutoToolModule.onTick(client);
            prof.swap("caeser_no_fog");
            NoFogModule.onTick(client);
            prof.swap("caeser_block_outline");
            BlockOutlineModule.onTick(client);
            prof.swap("caeser_freelook");
            FreelookModule.onTick(client);
            prof.swap("caeser_item_size");
            ItemSizeModule.onTick(client);
            prof.swap("caeser_hand_position");
            com.bame.client.module.HandPositionModule.onTick(client);
            prof.swap("caeser_custom_visuals");
            com.bame.client.module.CustomFireModule.onTick(client);
            com.bame.client.module.ShaderFogModule.onTick(client);
            com.bame.client.module.NametagsModule.onTick(client);
            prof.swap("caeser_durability_guard");
            DurabilityGuardModule.onTick(client);
            prof.swap("caeser_zoom");
            ZoomModule.onTick(client);
            prof.swap("caeser_pearl_prediction");
            com.bame.client.module.PearlPredictionModule.onTick(client);
            prof.swap("caeser_auto_cart");
            com.bame.client.module.AutoCartModule.onTick(client);
            prof.swap("caeser_auto_mace");
            com.bame.client.module.AutoMaceModule.onTick(client);
            prof.swap("caeser_dtap");
            com.bame.client.module.DTapModule.onTick(client);
            prof.swap("caeser_pearl_catch");
            com.bame.client.module.PearlCatchModule.onTick(client);
            prof.swap("caeser_aggro_pearl");
            com.bame.client.module.AggroPearlModule.onTick(client);
            prof.swap("caeser_triggerbot");
            com.bame.client.module.TriggerbotModule.onTick(client);
            prof.swap("caeser_shield_breaker");
            com.bame.client.module.ShieldBreakerModule.onTick(client);
            prof.swap("caeser_emote_module");
            com.bame.client.module.EmoteModule.onTick(client);
            prof.swap("caeser_emote_highfive");
            com.bame.client.emotes.HighFiveCoordinator.tick(client);
            prof.swap("caeser_emote_campfire");
            com.bame.client.emotes.CampfireCoordinator.tick(client);
            prof.swap("caeser_emote_hammock");
            com.bame.client.emotes.HammockCoordinator.tick(client);
            prof.swap("caeser_emote_swing");
            com.bame.client.emotes.SwingCoordinator.tick(client);
            prof.swap("caeser_emote_picnic");
            com.bame.client.emotes.PicnicCoordinator.tick(client);
            prof.swap("caeser_emote_witch_broom");
            com.bame.client.emotes.WitchBroomCoordinator.tick(client);
            prof.swap("caeser_emote_flying_carpet");
            com.bame.client.emotes.FlyingCarpetCoordinator.tick(client);
            prof.swap("caeser_emote_world_effects");
            com.bame.client.emotes.EmoteWorldEffects.tick(client);
            prof.swap("caeser_kill_detector");
            com.bame.client.killfx.KillDetector.tick(client);
            prof.swap("caeser_kill_effects");
            com.bame.client.killfx.KillEffects.tick(client);
            prof.swap("caeser_narrator_hint");
            com.bame.client.util.NarratorGuard.tick(client);
            prof.swap("caeser_container_search");
            com.bame.client.gui.search.ContainerSearch.tick(client);
            prof.swap("caeser_keybinds");

            if (client.getWindow() != null && client.currentScreen == null) {
                // Emote Wheel bind
                if (com.bame.client.module.EmoteModule.enabled && com.bame.client.module.EmoteModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), com.bame.client.module.EmoteModule.keyBind);
                    if (down && !emoteWasPressed) {
                        prof.push("caeser_open_emote_wheel");
                        try {
                            client.setScreen(new com.bame.client.gui.EmoteWheelScreen());
                        } finally {
                            prof.pop();
                        }
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

                // Hide Armor bind
                if (HideArmorModule.keyBind != -1) {
                    boolean down = InputUtil.isKeyPressed(client.getWindow(), HideArmorModule.keyBind);
                    if (down && !hideArmorWasPressed) {
                        HideArmorModule.enabled = !HideArmorModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Hide Armor", HideArmorModule.enabled);
                        BameClientConfig.save();
                    }
                    hideArmorWasPressed = down;
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
                        prof.push("caeser_open_menu");
                        try {
                            client.setScreen(new BameClientScreen());
                        } finally {
                            prof.pop();
                        }
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
            } finally {
                prof.pop();
            }
            } finally {
                prof.pop();
            }
        });

        // HUD: jeder Renderer in einem eigenen Profiler-Abschnitt (Hülle einmal bei der Registrierung)
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                com.bame.client.util.Prof.hud("caeser_hud_keystrokes", new com.bame.client.render.KeyStrokesRenderer()));
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                com.bame.client.util.Prof.hud("caeser_hud_status", new com.bame.client.render.StatusHudRenderer()));
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                com.bame.client.util.Prof.hud("caeser_hud_fake_scoreboard", new com.bame.client.render.FakeScoreboardRenderer()));
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                com.bame.client.util.Prof.hud("caeser_hud_notifications", new com.bame.client.notification.NotificationManager()));
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                com.bame.client.util.Prof.hud("caeser_hud_highfive_prompt", new com.bame.client.render.HighFivePromptRenderer()));
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                com.bame.client.util.Prof.hud("caeser_hud_kill_effects", new com.bame.client.killfx.KillEffectHud()));
        // Vanilla-Hotbar im HUD-Editor verschiebbar: Hotbar-Gruppe nur umhüllt (nichts entfernt, nichts doppelt gezeichnet)
        com.bame.client.render.HotbarHud.register();
        // Suchleiste unter Truhen & Co. (Mod-Menü → Misc → Container Search)
        com.bame.client.gui.search.ContainerSearch.register();

        // Kill-Effekte Sarg und Amboss (mit Opfer-Figur): zusammen mit den Entities zeichnen
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            net.minecraft.util.profiler.Profiler p = net.minecraft.util.profiler.Profilers.get();
            p.push("caeser_world_kill_effects");
            try {
                com.bame.client.killfx.KillEffects.submitWorld(context);
            } finally {
                p.pop();
            }
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            net.minecraft.util.profiler.Profiler p = net.minecraft.util.profiler.Profilers.get();
            p.push("caeser_world_area");
            try {
                AreaRenderer.render(context);
                p.swap("caeser_world_pearl_prediction");
                com.bame.client.render.PearlPredictionRenderer.render(context);
                p.swap("caeser_world_kill_effects");
                com.bame.client.killfx.KillEffects.renderWorld(context);
            } finally {
                p.pop();
            }
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

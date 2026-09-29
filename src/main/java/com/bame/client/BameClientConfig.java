package com.bame.client;

import com.bame.client.module.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class BameClientConfig {
    public static final Path BASE_CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("CaeserClient");

    public static int outlineColor = 0xFF00FFFF;
    public static int renderMode = 0; // 0=Clean, 1=Outline, 2=Corners, 3=Pulse
    public static float outlineWidth = 1.0f;
    public static boolean fullbrightEnabled = false;
    public static float fullbrightIntensity = 1.0f;
    public static String theme = "Ultraviolet";
    public static boolean seeThrough = false, frostedBlur = true, ambientBackground = true;

    public static int guiAnimation = 0; // 0=Zoom/Fade, 1=Slide, 2=None
    public static boolean customMainColor = false, classicToggle = false, ambientAnimation = true;
    public static int mainColor = 0xFFB23CEE, ambientPrimary = 0xFF5675FF, ambientSecondary = 0xFFA647F2;
    public static int ambientMode = 0;
    public static float ambientIntensity = 1, ambientOpacity = 1, ambientRadius = 1, animationSpeed = 1;
    public static int menuBind = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static boolean quickFriend = false;
    public static String selectedCategory = "Visuals";

    public static void load() {
        Path visualsDir = BASE_CONFIG_DIR.resolve("Visuals");
        Path worldDir = BASE_CONFIG_DIR.resolve("World");

        // If new modular structure exists, load from it
        if (Files.exists(visualsDir.resolve("show_hud.properties")) || Files.exists(worldDir.resolve("auto_area_miner.properties"))) {
            loadModular();
            return;
        }

        // Check for legacy single file to migrate
        Path legacyFile = FabricLoader.getInstance().getConfigDir().resolve("bameclient.properties");
        if (Files.exists(legacyFile)) {
            loadLegacy(legacyFile);
            save();
            return;
        }

        // First run: save defaults into modular folders
        save();
    }

    private static void loadModular() {
        try {
            // 1. World - Auto Area Miner
            Path minerFile = BASE_CONFIG_DIR.resolve("World").resolve("auto_area_miner.properties");
            if (Files.exists(minerFile)) {
                Properties props = loadProps(minerFile);
                if (props.containsKey("keyBind")) AutoAreaMinerModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("mode3x3")) AutoAreaMinerModule.mode3x3 = Boolean.parseBoolean(props.getProperty("mode3x3"));
                if (props.containsKey("corner1")) {
                    String[] parts = props.getProperty("corner1").trim().split("\\s+");
                    if (parts.length == 3) {
                        try {
                            AutoAreaMinerModule.corner1 = new net.minecraft.util.math.BlockPos(
                                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
                            );
                        } catch (Exception ignored) {}
                    }
                }
                if (props.containsKey("corner2")) {
                    String[] parts = props.getProperty("corner2").trim().split("\\s+");
                    if (parts.length == 3) {
                        try {
                            AutoAreaMinerModule.corner2 = new net.minecraft.util.math.BlockPos(
                                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
                            );
                        } catch (Exception ignored) {}
                    }
                }
                if (props.containsKey("outlineColor")) outlineColor = (int) Long.parseLong(props.getProperty("outlineColor", "FF00FFFF"), 16);
                if (props.containsKey("renderMode")) renderMode = Integer.parseInt(props.getProperty("renderMode"));
                if (props.containsKey("outlineWidth")) outlineWidth = Float.parseFloat(props.getProperty("outlineWidth"));
            }

            // Movement - InvMove
            Path invMoveFile = BASE_CONFIG_DIR.resolve("Movement").resolve("inv_move.properties");
            if (Files.exists(invMoveFile)) {
                Properties props = loadProps(invMoveFile);
                if (props.containsKey("enabled")) InvMoveModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) InvMoveModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) InvMoveModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("jump")) InvMoveModule.jump = Boolean.parseBoolean(props.getProperty("jump"));
                if (props.containsKey("sprint")) InvMoveModule.sprint = Boolean.parseBoolean(props.getProperty("sprint"));
                if (props.containsKey("sneak")) InvMoveModule.sneak = Boolean.parseBoolean(props.getProperty("sneak"));
                if (props.containsKey("rotateWithArrows")) InvMoveModule.rotateWithArrows = Boolean.parseBoolean(props.getProperty("rotateWithArrows"));
            }

            // 2. Visuals - Show HUD
            Path showHudFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("show_hud.properties");
            if (Files.exists(showHudFile)) {
                Properties props = loadProps(showHudFile);
                if (props.containsKey("showHudEnabled")) ShowHudModule.enabled = Boolean.parseBoolean(props.getProperty("showHudEnabled"));
                if (props.containsKey("showHudKeyBind")) ShowHudModule.keyBind = Integer.parseInt(props.getProperty("showHudKeyBind"));
                if (props.containsKey("showHudExpanded")) ShowHudModule.expanded = Boolean.parseBoolean(props.getProperty("showHudExpanded"));

                // Clock
                if (props.containsKey("clockEnabled")) ClockModule.enabled = Boolean.parseBoolean(props.getProperty("clockEnabled"));
                if (props.containsKey("clockHudX")) ClockModule.hudX = Integer.parseInt(props.getProperty("clockHudX"));
                if (props.containsKey("clockHudY")) ClockModule.hudY = Integer.parseInt(props.getProperty("clockHudY"));
                if (props.containsKey("clockScale")) ClockModule.scale = Float.parseFloat(props.getProperty("clockScale"));
                if (props.containsKey("clockBgMode")) ClockModule.bgMode = Integer.parseInt(props.getProperty("clockBgMode"));
                if (props.containsKey("clockOutlineColor")) ClockModule.outlineColor = (int) Long.parseLong(props.getProperty("clockOutlineColor"), 16);
                if (props.containsKey("clockCustomWidth")) ClockModule.customWidth = Integer.parseInt(props.getProperty("clockCustomWidth"));
                if (props.containsKey("clockCustomHeight")) ClockModule.customHeight = Integer.parseInt(props.getProperty("clockCustomHeight"));

                // Coordinates
                if (props.containsKey("coordinatesEnabled")) CoordinatesModule.enabled = Boolean.parseBoolean(props.getProperty("coordinatesEnabled"));
                if (props.containsKey("coordinatesHudX")) CoordinatesModule.hudX = Integer.parseInt(props.getProperty("coordinatesHudX"));
                if (props.containsKey("coordinatesHudY")) CoordinatesModule.hudY = Integer.parseInt(props.getProperty("coordinatesHudY"));
                if (props.containsKey("coordinatesScale")) CoordinatesModule.scale = Float.parseFloat(props.getProperty("coordinatesScale"));
                if (props.containsKey("coordinatesBgMode")) CoordinatesModule.bgMode = Integer.parseInt(props.getProperty("coordinatesBgMode"));
                if (props.containsKey("coordinatesOutlineColor")) CoordinatesModule.outlineColor = (int) Long.parseLong(props.getProperty("coordinatesOutlineColor"), 16);
                if (props.containsKey("coordinatesCustomWidth")) CoordinatesModule.customWidth = Integer.parseInt(props.getProperty("coordinatesCustomWidth"));
                if (props.containsKey("coordinatesCustomHeight")) CoordinatesModule.customHeight = Integer.parseInt(props.getProperty("coordinatesCustomHeight"));

                // Potions
                if (props.containsKey("potionsEnabled")) PotionsModule.enabled = Boolean.parseBoolean(props.getProperty("potionsEnabled"));
                if (props.containsKey("potionsHudX")) PotionsModule.hudX = Integer.parseInt(props.getProperty("potionsHudX"));
                if (props.containsKey("potionsHudY")) PotionsModule.hudY = Integer.parseInt(props.getProperty("potionsHudY"));
                if (props.containsKey("potionsScale")) PotionsModule.scale = Float.parseFloat(props.getProperty("potionsScale"));
                if (props.containsKey("potionsBgMode")) PotionsModule.bgMode = Integer.parseInt(props.getProperty("potionsBgMode"));
                if (props.containsKey("potionsOutlineColor")) PotionsModule.outlineColor = (int) Long.parseLong(props.getProperty("potionsOutlineColor"), 16);
                if (props.containsKey("potionsCustomWidth")) PotionsModule.customWidth = Integer.parseInt(props.getProperty("potionsCustomWidth"));
                if (props.containsKey("potionsCustomHeight")) PotionsModule.customHeight = Integer.parseInt(props.getProperty("potionsCustomHeight"));

                // Target HUD
                if (props.containsKey("targetHudEnabled")) TargetHudModule.enabled = Boolean.parseBoolean(props.getProperty("targetHudEnabled"));
                if (props.containsKey("targetHudExpanded")) TargetHudModule.expanded = Boolean.parseBoolean(props.getProperty("targetHudExpanded"));
                if (props.containsKey("targetHudShowHearts")) TargetHudModule.showHearts = Boolean.parseBoolean(props.getProperty("targetHudShowHearts"));
                if (props.containsKey("targetHudShowArmor")) TargetHudModule.showArmor = Boolean.parseBoolean(props.getProperty("targetHudShowArmor"));
                if (props.containsKey("targetHudPlayersOnly")) TargetHudModule.playersOnly = Boolean.parseBoolean(props.getProperty("targetHudPlayersOnly"));
                if (props.containsKey("targetHudHudX")) TargetHudModule.hudX = Integer.parseInt(props.getProperty("targetHudHudX"));
                if (props.containsKey("targetHudHudY")) TargetHudModule.hudY = Integer.parseInt(props.getProperty("targetHudHudY"));
                if (props.containsKey("targetHudScale")) TargetHudModule.scale = Float.parseFloat(props.getProperty("targetHudScale"));
                if (props.containsKey("targetHudBgMode")) TargetHudModule.bgMode = Integer.parseInt(props.getProperty("targetHudBgMode"));
                if (props.containsKey("targetHudOutlineColor")) TargetHudModule.outlineColor = (int) Long.parseLong(props.getProperty("targetHudOutlineColor"), 16);
                if (props.containsKey("targetHudCustomWidth")) TargetHudModule.customWidth = Integer.parseInt(props.getProperty("targetHudCustomWidth"));
                if (props.containsKey("targetHudCustomHeight")) TargetHudModule.customHeight = Integer.parseInt(props.getProperty("targetHudCustomHeight"));

                // Armor HUD
                if (props.containsKey("armorHudEnabled")) ArmorHudModule.enabled = Boolean.parseBoolean(props.getProperty("armorHudEnabled"));
                if (props.containsKey("armorHudHudX")) ArmorHudModule.hudX = Integer.parseInt(props.getProperty("armorHudHudX"));
                if (props.containsKey("armorHudHudY")) ArmorHudModule.hudY = Integer.parseInt(props.getProperty("armorHudHudY"));
                if (props.containsKey("armorHudScale")) ArmorHudModule.scale = Float.parseFloat(props.getProperty("armorHudScale"));
                if (props.containsKey("armorHudBgMode")) ArmorHudModule.bgMode = Integer.parseInt(props.getProperty("armorHudBgMode"));
                if (props.containsKey("armorHudOutlineColor")) ArmorHudModule.outlineColor = (int) Long.parseLong(props.getProperty("armorHudOutlineColor"), 16);
                if (props.containsKey("armorHudCustomWidth")) ArmorHudModule.customWidth = Integer.parseInt(props.getProperty("armorHudCustomWidth"));
                if (props.containsKey("armorHudCustomHeight")) ArmorHudModule.customHeight = Integer.parseInt(props.getProperty("armorHudCustomHeight"));

                // KeyStrokes
                if (props.containsKey("keyStrokesEnabled")) KeyStrokesModule.enabled = Boolean.parseBoolean(props.getProperty("keyStrokesEnabled"));
                if (props.containsKey("keyStrokesKeyBind")) KeyStrokesModule.keyBind = Integer.parseInt(props.getProperty("keyStrokesKeyBind"));
                if (props.containsKey("keyStrokesHudX")) KeyStrokesModule.hudX = Integer.parseInt(props.getProperty("keyStrokesHudX"));
                if (props.containsKey("keyStrokesHudY")) KeyStrokesModule.hudY = Integer.parseInt(props.getProperty("keyStrokesHudY"));
                if (props.containsKey("keyStrokesScale")) KeyStrokesModule.scale = Float.parseFloat(props.getProperty("keyStrokesScale"));
                if (props.containsKey("keyStrokesBgMode")) KeyStrokesModule.bgMode = Integer.parseInt(props.getProperty("keyStrokesBgMode"));
                if (props.containsKey("keyStrokesOutlineColor")) KeyStrokesModule.outlineColor = (int) Long.parseLong(props.getProperty("keyStrokesOutlineColor"), 16);
                for (KeyStrokesModule.KeyStroke key : KeyStrokesModule.keys) {
                    if (props.containsKey("ks_" + key.name + "_x")) key.relX = Integer.parseInt(props.getProperty("ks_" + key.name + "_x"));
                    if (props.containsKey("ks_" + key.name + "_y")) key.relY = Integer.parseInt(props.getProperty("ks_" + key.name + "_y"));
                    if (props.containsKey("ks_" + key.name + "_w")) key.width = Integer.parseInt(props.getProperty("ks_" + key.name + "_w"));
                    if (props.containsKey("ks_" + key.name + "_h")) key.height = Integer.parseInt(props.getProperty("ks_" + key.name + "_h"));
                }

                // CPS
                if (props.containsKey("cpsEnabled")) CpsModule.enabled = Boolean.parseBoolean(props.getProperty("cpsEnabled"));
                if (props.containsKey("cpsKeyBind")) CpsModule.keyBind = Integer.parseInt(props.getProperty("cpsKeyBind"));
                if (props.containsKey("cpsHudX")) CpsModule.hudX = Integer.parseInt(props.getProperty("cpsHudX"));
                if (props.containsKey("cpsHudY")) CpsModule.hudY = Integer.parseInt(props.getProperty("cpsHudY"));
                if (props.containsKey("cpsScale")) CpsModule.scale = Float.parseFloat(props.getProperty("cpsScale"));
                if (props.containsKey("cpsBgMode")) CpsModule.bgMode = Integer.parseInt(props.getProperty("cpsBgMode"));
                if (props.containsKey("cpsOutlineColor")) CpsModule.outlineColor = (int) Long.parseLong(props.getProperty("cpsOutlineColor"), 16);
                if (props.containsKey("cpsCustomWidth")) CpsModule.customWidth = Integer.parseInt(props.getProperty("cpsCustomWidth"));
                if (props.containsKey("cpsCustomHeight")) CpsModule.customHeight = Integer.parseInt(props.getProperty("cpsCustomHeight"));

                // FPS
                if (props.containsKey("fpsEnabled")) FpsModule.enabled = Boolean.parseBoolean(props.getProperty("fpsEnabled"));
                if (props.containsKey("fpsKeyBind")) FpsModule.keyBind = Integer.parseInt(props.getProperty("fpsKeyBind"));
                if (props.containsKey("fpsHudX")) FpsModule.hudX = Integer.parseInt(props.getProperty("fpsHudX"));
                if (props.containsKey("fpsHudY")) FpsModule.hudY = Integer.parseInt(props.getProperty("fpsHudY"));
                if (props.containsKey("fpsScale")) FpsModule.scale = Float.parseFloat(props.getProperty("fpsScale"));
                if (props.containsKey("fpsBgMode")) FpsModule.bgMode = Integer.parseInt(props.getProperty("fpsBgMode"));
                if (props.containsKey("fpsOutlineColor")) FpsModule.outlineColor = (int) Long.parseLong(props.getProperty("fpsOutlineColor"), 16);
                if (props.containsKey("fpsCustomWidth")) FpsModule.customWidth = Integer.parseInt(props.getProperty("fpsCustomWidth"));
                if (props.containsKey("fpsCustomHeight")) FpsModule.customHeight = Integer.parseInt(props.getProperty("fpsCustomHeight"));

                // Ping
                if (props.containsKey("pingEnabled")) PingModule.enabled = Boolean.parseBoolean(props.getProperty("pingEnabled"));
                if (props.containsKey("pingKeyBind")) PingModule.keyBind = Integer.parseInt(props.getProperty("pingKeyBind"));
                if (props.containsKey("pingHudX")) PingModule.hudX = Integer.parseInt(props.getProperty("pingHudX"));
                if (props.containsKey("pingHudY")) PingModule.hudY = Integer.parseInt(props.getProperty("pingHudY"));
                if (props.containsKey("pingScale")) PingModule.scale = Float.parseFloat(props.getProperty("pingScale"));
                if (props.containsKey("pingBgMode")) PingModule.bgMode = Integer.parseInt(props.getProperty("pingBgMode"));
                if (props.containsKey("pingOutlineColor")) PingModule.outlineColor = (int) Long.parseLong(props.getProperty("pingOutlineColor"), 16);
                if (props.containsKey("pingCustomWidth")) PingModule.customWidth = Integer.parseInt(props.getProperty("pingCustomWidth"));
                if (props.containsKey("pingCustomHeight")) PingModule.customHeight = Integer.parseInt(props.getProperty("pingCustomHeight"));

                // Server Info
                if (props.containsKey("serverInfoEnabled")) ServerInfoModule.enabled = Boolean.parseBoolean(props.getProperty("serverInfoEnabled"));
                if (props.containsKey("serverInfoKeyBind")) ServerInfoModule.keyBind = Integer.parseInt(props.getProperty("serverInfoKeyBind"));
                if (props.containsKey("serverInfoHudX")) ServerInfoModule.hudX = Integer.parseInt(props.getProperty("serverInfoHudX"));
                if (props.containsKey("serverInfoHudY")) ServerInfoModule.hudY = Integer.parseInt(props.getProperty("serverInfoHudY"));
                if (props.containsKey("serverInfoScale")) ServerInfoModule.scale = Float.parseFloat(props.getProperty("serverInfoScale"));
                if (props.containsKey("serverInfoBgMode")) ServerInfoModule.bgMode = Integer.parseInt(props.getProperty("serverInfoBgMode"));
                if (props.containsKey("serverInfoOutlineColor")) ServerInfoModule.outlineColor = (int) Long.parseLong(props.getProperty("serverInfoOutlineColor"), 16);
                if (props.containsKey("serverInfoCustomWidth")) ServerInfoModule.customWidth = Integer.parseInt(props.getProperty("serverInfoCustomWidth"));
                if (props.containsKey("serverInfoCustomHeight")) ServerInfoModule.customHeight = Integer.parseInt(props.getProperty("serverInfoCustomHeight"));
                if (props.containsKey("serverInfoShowName")) ServerInfoModule.showName = Boolean.parseBoolean(props.getProperty("serverInfoShowName"));
                if (props.containsKey("serverInfoShowServer")) ServerInfoModule.showServer = Boolean.parseBoolean(props.getProperty("serverInfoShowServer"));
                if (props.containsKey("serverInfoShowTime")) ServerInfoModule.showTime = Boolean.parseBoolean(props.getProperty("serverInfoShowTime"));
                if (props.containsKey("serverInfoDockedElements")) {
                    String dockStr = props.getProperty("serverInfoDockedElements");
                    if (!dockStr.trim().isEmpty()) {
                        ServerInfoModule.dockedElements = new java.util.ArrayList<>(java.util.Arrays.asList(dockStr.split(",")));
                    } else {
                        ServerInfoModule.dockedElements = new java.util.ArrayList<>();
                    }
                }
                if (props.containsKey("serverInfoNameX")) ServerInfoModule.nameX = Integer.parseInt(props.getProperty("serverInfoNameX"));
                if (props.containsKey("serverInfoNameY")) ServerInfoModule.nameY = Integer.parseInt(props.getProperty("serverInfoNameY"));
                if (props.containsKey("serverInfoServerX")) ServerInfoModule.serverX = Integer.parseInt(props.getProperty("serverInfoServerX"));
                if (props.containsKey("serverInfoServerY")) ServerInfoModule.serverY = Integer.parseInt(props.getProperty("serverInfoServerY"));
                if (props.containsKey("serverInfoTimeX")) ServerInfoModule.timeX = Integer.parseInt(props.getProperty("serverInfoTimeX"));
                if (props.containsKey("serverInfoTimeY")) ServerInfoModule.timeY = Integer.parseInt(props.getProperty("serverInfoTimeY"));

                // Name Protect
                if (props.containsKey("nameProtectEnabled")) NameProtectModule.enabled = Boolean.parseBoolean(props.getProperty("nameProtectEnabled"));
                if (props.containsKey("nameProtectKeyBind")) NameProtectModule.keyBind = Integer.parseInt(props.getProperty("nameProtectKeyBind"));
                if (props.containsKey("nameProtectAlias")) NameProtectModule.alias = props.getProperty("nameProtectAlias");
            }

            // 3. Visuals - Fullbright
            Path fullbrightFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("fullbright.properties");
            if (Files.exists(fullbrightFile)) {
                Properties props = loadProps(fullbrightFile);
                if (props.containsKey("enabled")) fullbrightEnabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("intensity")) fullbrightIntensity = Float.parseFloat(props.getProperty("intensity"));
                FullbrightModule.enabled = fullbrightEnabled;
                FullbrightModule.intensity = fullbrightIntensity;
                if (props.containsKey("keyBind")) FullbrightModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
            }

            // 4. Visuals - Zoom
            Path zoomFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("zoom.properties");
            if (Files.exists(zoomFile)) {
                Properties props = loadProps(zoomFile);
                if (props.containsKey("enabled")) ZoomModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) ZoomModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("mode")) ZoomModule.mode = Integer.parseInt(props.getProperty("mode"));
            }

            // 5. Visuals - Fake Scoreboard
            Path sbFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("fake_scoreboard.properties");
            if (Files.exists(sbFile)) {
                Properties props = loadProps(sbFile);
                if (props.containsKey("enabled")) FakeScoreboardModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) FakeScoreboardModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) FakeScoreboardModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("hudX")) FakeScoreboardModule.hudX = Integer.parseInt(props.getProperty("hudX"));
                if (props.containsKey("hudY")) FakeScoreboardModule.hudY = Integer.parseInt(props.getProperty("hudY"));
                if (props.containsKey("scale")) FakeScoreboardModule.scale = Float.parseFloat(props.getProperty("scale"));
                if (props.containsKey("bgMode")) FakeScoreboardModule.bgMode = Integer.parseInt(props.getProperty("bgMode"));
                if (props.containsKey("outlineColor")) FakeScoreboardModule.outlineColor = (int) Long.parseLong(props.getProperty("outlineColor"), 16);
                if (props.containsKey("customWidth")) FakeScoreboardModule.customWidth = Integer.parseInt(props.getProperty("customWidth"));
                if (props.containsKey("customHeight")) FakeScoreboardModule.customHeight = Integer.parseInt(props.getProperty("customHeight"));
                if (props.containsKey("money")) FakeScoreboardModule.money = props.getProperty("money");
                if (props.containsKey("stars")) FakeScoreboardModule.stars = props.getProperty("stars");
                if (props.containsKey("kills")) FakeScoreboardModule.kills = props.getProperty("kills");
                if (props.containsKey("deaths")) FakeScoreboardModule.deaths = props.getProperty("deaths");
                if (props.containsKey("time")) FakeScoreboardModule.playtime = props.getProperty("time");
            }

            // Visuals - Spotify HUD
            Path spotifyFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("spotify_hud.properties");
            if (Files.exists(spotifyFile)) {
                Properties props = loadProps(spotifyFile);
                if (props.containsKey("enabled")) SpotifyHudModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) SpotifyHudModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) SpotifyHudModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("hudX")) SpotifyHudModule.hudX = Integer.parseInt(props.getProperty("hudX"));
                if (props.containsKey("hudY")) SpotifyHudModule.hudY = Integer.parseInt(props.getProperty("hudY"));
                if (props.containsKey("scale")) SpotifyHudModule.scale = Float.parseFloat(props.getProperty("scale"));
                if (props.containsKey("bgMode")) SpotifyHudModule.bgMode = Integer.parseInt(props.getProperty("bgMode"));
                if (props.containsKey("outlineColor")) SpotifyHudModule.outlineColor = (int) Long.parseLong(props.getProperty("outlineColor"), 16);
                if (props.containsKey("customWidth")) SpotifyHudModule.customWidth = Integer.parseInt(props.getProperty("customWidth"));
                if (props.containsKey("customHeight")) SpotifyHudModule.customHeight = Integer.parseInt(props.getProperty("customHeight"));
                if (props.containsKey("autoHide")) SpotifyHudModule.autoHide = Boolean.parseBoolean(props.getProperty("autoHide"));
            }

            // Visuals - Scoreboard
            Path scoreboardFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("scoreboard.properties");
            if (Files.exists(scoreboardFile)) {
                Properties props = loadProps(scoreboardFile);
                if (props.containsKey("enabled")) ScoreboardModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) ScoreboardModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) ScoreboardModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("hudX")) ScoreboardModule.hudX = Integer.parseInt(props.getProperty("hudX"));
                if (props.containsKey("hudY")) ScoreboardModule.hudY = Integer.parseInt(props.getProperty("hudY"));
                if (props.containsKey("scale")) ScoreboardModule.scale = Float.parseFloat(props.getProperty("scale"));
                if (props.containsKey("bgMode")) ScoreboardModule.bgMode = Integer.parseInt(props.getProperty("bgMode"));
                if (props.containsKey("outlineColor")) ScoreboardModule.outlineColor = (int) Long.parseLong(props.getProperty("outlineColor"), 16);
                if (props.containsKey("customWidth")) ScoreboardModule.customWidth = Integer.parseInt(props.getProperty("customWidth"));
                if (props.containsKey("customHeight")) ScoreboardModule.customHeight = Integer.parseInt(props.getProperty("customHeight"));
            }

            // Visuals - Custom Crosshair
            Path crosshairFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("custom_crosshair.properties");
            if (Files.exists(crosshairFile)) {
                Properties props = loadProps(crosshairFile);
                if (props.containsKey("enabled")) CustomCrosshairModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) CustomCrosshairModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) CustomCrosshairModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("color")) CustomCrosshairModule.color = (int) Long.parseLong(props.getProperty("color"), 16);
                if (props.containsKey("grid")) CustomCrosshairModule.setEncodedGrid(props.getProperty("grid"));
            }

            // Combat - AutoClicker
            Path acFile = BASE_CONFIG_DIR.resolve("Combat").resolve("auto_clicker.properties");
            if (Files.exists(acFile)) {
                Properties props = loadProps(acFile);
                if (props.containsKey("enabled")) AutoClickerModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) AutoClickerModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) AutoClickerModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("mode")) AutoClickerModule.mode = Integer.parseInt(props.getProperty("mode"));
                if (props.containsKey("cps")) AutoClickerModule.cps = Integer.parseInt(props.getProperty("cps"));
                if (props.containsKey("delaySeconds")) AutoClickerModule.delaySeconds = Float.parseFloat(props.getProperty("delaySeconds"));
                if (props.containsKey("randomJitter")) AutoClickerModule.randomJitter = Boolean.parseBoolean(props.getProperty("randomJitter"));
                if (props.containsKey("weaponOnly")) AutoClickerModule.weaponOnly = Boolean.parseBoolean(props.getProperty("weaponOnly"));
                if (props.containsKey("button")) AutoClickerModule.button = Integer.parseInt(props.getProperty("button"));
            }

            // Combat - HitColor
            Path hcFile = BASE_CONFIG_DIR.resolve("Combat").resolve("hit_color.properties");
            if (Files.exists(hcFile)) {
                Properties props = loadProps(hcFile);
                if (props.containsKey("enabled")) HitColorModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) HitColorModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) HitColorModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("color")) HitColorModule.color = (int) Long.parseLong(props.getProperty("color"), 16);
                if (props.containsKey("alpha")) HitColorModule.alpha = Float.parseFloat(props.getProperty("alpha"));
            }

            // Combat - ReachDisplay
            Path rdFile = BASE_CONFIG_DIR.resolve("Combat").resolve("reach_display.properties");
            if (Files.exists(rdFile)) {
                Properties props = loadProps(rdFile);
                if (props.containsKey("enabled")) ReachDisplayModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) ReachDisplayModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) ReachDisplayModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("mode")) ReachDisplayModule.mode = Integer.parseInt(props.getProperty("mode"));
                if (props.containsKey("hudX")) ReachDisplayModule.hudX = Integer.parseInt(props.getProperty("hudX"));
                if (props.containsKey("hudY")) ReachDisplayModule.hudY = Integer.parseInt(props.getProperty("hudY"));
                if (props.containsKey("scale")) ReachDisplayModule.scale = Float.parseFloat(props.getProperty("scale"));
                if (props.containsKey("bgMode")) ReachDisplayModule.bgMode = Integer.parseInt(props.getProperty("bgMode"));
                if (props.containsKey("outlineColor")) ReachDisplayModule.outlineColor = (int) Long.parseLong(props.getProperty("outlineColor"), 16);
                if (props.containsKey("customWidth")) ReachDisplayModule.customWidth = Integer.parseInt(props.getProperty("customWidth"));
                if (props.containsKey("customHeight")) ReachDisplayModule.customHeight = Integer.parseInt(props.getProperty("customHeight"));
            }

            // Combat - LowShield
            Path lsFile = BASE_CONFIG_DIR.resolve("Combat").resolve("low_shield.properties");
            if (Files.exists(lsFile)) {
                Properties props = loadProps(lsFile);
                if (props.containsKey("enabled")) LowShieldModule.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (props.containsKey("keyBind")) LowShieldModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
                if (props.containsKey("expanded")) LowShieldModule.expanded = Boolean.parseBoolean(props.getProperty("expanded"));
                if (props.containsKey("heightPercent")) LowShieldModule.heightPercent = Integer.parseInt(props.getProperty("heightPercent"));
            }

            // 6. Theme
            Path themeFile = BASE_CONFIG_DIR.resolve("Theme").resolve("theme.properties");
            if (Files.exists(themeFile)) {
                Properties props = loadProps(themeFile);
                if (props.containsKey("theme")) theme = props.getProperty("theme");
                if (props.containsKey("seeThrough")) seeThrough = Boolean.parseBoolean(props.getProperty("seeThrough"));
                if (props.containsKey("frostedBlur")) frostedBlur = Boolean.parseBoolean(props.getProperty("frostedBlur"));
                if (props.containsKey("ambientBackground")) ambientBackground = Boolean.parseBoolean(props.getProperty("ambientBackground"));
                if (props.containsKey("customMainColor")) customMainColor = Boolean.parseBoolean(props.getProperty("customMainColor"));
                if (props.containsKey("classicToggle")) classicToggle = Boolean.parseBoolean(props.getProperty("classicToggle"));
                if (props.containsKey("mainColor")) mainColor = Integer.parseInt(props.getProperty("mainColor"));
                if (props.containsKey("ambientPrimary")) ambientPrimary = Integer.parseInt(props.getProperty("ambientPrimary"));
                if (props.containsKey("ambientSecondary")) ambientSecondary = Integer.parseInt(props.getProperty("ambientSecondary"));
                if (props.containsKey("ambientMode")) ambientMode = Integer.parseInt(props.getProperty("ambientMode"));
                if (props.containsKey("ambientIntensity")) ambientIntensity = Math.clamp(Float.parseFloat(props.getProperty("ambientIntensity")), 0f, 1f);
                if (props.containsKey("ambientOpacity")) ambientOpacity = Math.clamp(Float.parseFloat(props.getProperty("ambientOpacity")), 0f, 1f);
                if (props.containsKey("ambientRadius")) ambientRadius = Math.clamp(Float.parseFloat(props.getProperty("ambientRadius")), 0f, 1f);
                if (props.containsKey("ambientAnimation")) ambientAnimation = Boolean.parseBoolean(props.getProperty("ambientAnimation"));
                if (props.containsKey("animationSpeed")) animationSpeed = Math.clamp(Float.parseFloat(props.getProperty("animationSpeed")), 0f, 1f);
            }

            // 7. Settings
            Path settingsFile = BASE_CONFIG_DIR.resolve("Settings").resolve("settings.properties");
            if (Files.exists(settingsFile)) {
                Properties props = loadProps(settingsFile);
                if (props.containsKey("menuBind")) menuBind = Integer.parseInt(props.getProperty("menuBind"));
                if (props.containsKey("quickFriend")) quickFriend = Boolean.parseBoolean(props.getProperty("quickFriend"));
                if (props.containsKey("guiAnimation")) guiAnimation = Integer.parseInt(props.getProperty("guiAnimation"));
                if (props.containsKey("selectedCategory")) {
                    selectedCategory = props.getProperty("selectedCategory");
                    com.bame.client.gui.BameClientScreen.selected = selectedCategory;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Path combatDir = BASE_CONFIG_DIR.resolve("Combat");
            Path movementDir = BASE_CONFIG_DIR.resolve("Movement");
            Path visualsDir = BASE_CONFIG_DIR.resolve("Visuals");
            Path miscDir = BASE_CONFIG_DIR.resolve("Misc");
            Path worldDir = BASE_CONFIG_DIR.resolve("World");
            Path themeDir = BASE_CONFIG_DIR.resolve("Theme");
            Path settingsDir = BASE_CONFIG_DIR.resolve("Settings");

            Files.createDirectories(combatDir);
            Files.createDirectories(movementDir);
            Files.createDirectories(visualsDir);
            Files.createDirectories(miscDir);
            Files.createDirectories(worldDir);
            Files.createDirectories(themeDir);
            Files.createDirectories(settingsDir);

            // 1. World - Auto Area Miner
            Properties worldProps = new Properties();
            worldProps.setProperty("keyBind", String.valueOf(AutoAreaMinerModule.keyBind));
            worldProps.setProperty("mode3x3", String.valueOf(AutoAreaMinerModule.mode3x3));
            if (AutoAreaMinerModule.corner1 != null) {
                worldProps.setProperty("corner1", AutoAreaMinerModule.corner1.getX() + " " + AutoAreaMinerModule.corner1.getY() + " " + AutoAreaMinerModule.corner1.getZ());
            }
            if (AutoAreaMinerModule.corner2 != null) {
                worldProps.setProperty("corner2", AutoAreaMinerModule.corner2.getX() + " " + AutoAreaMinerModule.corner2.getY() + " " + AutoAreaMinerModule.corner2.getZ());
            }
            worldProps.setProperty("outlineColor", Integer.toHexString(outlineColor));
            worldProps.setProperty("renderMode", String.valueOf(renderMode));
            worldProps.setProperty("outlineWidth", String.valueOf(outlineWidth));
            saveProps(worldDir.resolve("auto_area_miner.properties"), worldProps, "Caeser Client - Auto Area Miner");

            // Movement - InvMove
            Properties invMoveProps = new Properties();
            invMoveProps.setProperty("enabled", String.valueOf(InvMoveModule.enabled));
            invMoveProps.setProperty("keyBind", String.valueOf(InvMoveModule.keyBind));
            invMoveProps.setProperty("expanded", String.valueOf(InvMoveModule.expanded));
            invMoveProps.setProperty("jump", String.valueOf(InvMoveModule.jump));
            invMoveProps.setProperty("sprint", String.valueOf(InvMoveModule.sprint));
            invMoveProps.setProperty("sneak", String.valueOf(InvMoveModule.sneak));
            invMoveProps.setProperty("rotateWithArrows", String.valueOf(InvMoveModule.rotateWithArrows));
            saveProps(movementDir.resolve("inv_move.properties"), invMoveProps, "Caeser Client - InvMove");

            // 2. Visuals - Show HUD
            Properties showHudProps = new Properties();
            showHudProps.setProperty("showHudEnabled", String.valueOf(ShowHudModule.enabled));
            showHudProps.setProperty("showHudKeyBind", String.valueOf(ShowHudModule.keyBind));
            showHudProps.setProperty("showHudExpanded", String.valueOf(ShowHudModule.expanded));

            // Clock
            showHudProps.setProperty("clockEnabled", String.valueOf(ClockModule.enabled));
            showHudProps.setProperty("clockHudX", String.valueOf(ClockModule.hudX));
            showHudProps.setProperty("clockHudY", String.valueOf(ClockModule.hudY));
            showHudProps.setProperty("clockScale", String.valueOf(ClockModule.scale));
            showHudProps.setProperty("clockBgMode", String.valueOf(ClockModule.bgMode));
            showHudProps.setProperty("clockOutlineColor", Integer.toHexString(ClockModule.outlineColor));
            showHudProps.setProperty("clockCustomWidth", String.valueOf(ClockModule.customWidth));
            showHudProps.setProperty("clockCustomHeight", String.valueOf(ClockModule.customHeight));

            // Coordinates
            showHudProps.setProperty("coordinatesEnabled", String.valueOf(CoordinatesModule.enabled));
            showHudProps.setProperty("coordinatesHudX", String.valueOf(CoordinatesModule.hudX));
            showHudProps.setProperty("coordinatesHudY", String.valueOf(CoordinatesModule.hudY));
            showHudProps.setProperty("coordinatesScale", String.valueOf(CoordinatesModule.scale));
            showHudProps.setProperty("coordinatesBgMode", String.valueOf(CoordinatesModule.bgMode));
            showHudProps.setProperty("coordinatesOutlineColor", Integer.toHexString(CoordinatesModule.outlineColor));
            showHudProps.setProperty("coordinatesCustomWidth", String.valueOf(CoordinatesModule.customWidth));
            showHudProps.setProperty("coordinatesCustomHeight", String.valueOf(CoordinatesModule.customHeight));

            // Potions
            showHudProps.setProperty("potionsEnabled", String.valueOf(PotionsModule.enabled));
            showHudProps.setProperty("potionsHudX", String.valueOf(PotionsModule.hudX));
            showHudProps.setProperty("potionsHudY", String.valueOf(PotionsModule.hudY));
            showHudProps.setProperty("potionsScale", String.valueOf(PotionsModule.scale));
            showHudProps.setProperty("potionsBgMode", String.valueOf(PotionsModule.bgMode));
            showHudProps.setProperty("potionsOutlineColor", Integer.toHexString(PotionsModule.outlineColor));
            showHudProps.setProperty("potionsCustomWidth", String.valueOf(PotionsModule.customWidth));
            showHudProps.setProperty("potionsCustomHeight", String.valueOf(PotionsModule.customHeight));

            // Target HUD
            showHudProps.setProperty("targetHudEnabled", String.valueOf(TargetHudModule.enabled));
            showHudProps.setProperty("targetHudExpanded", String.valueOf(TargetHudModule.expanded));
            showHudProps.setProperty("targetHudShowHearts", String.valueOf(TargetHudModule.showHearts));
            showHudProps.setProperty("targetHudShowArmor", String.valueOf(TargetHudModule.showArmor));
            showHudProps.setProperty("targetHudPlayersOnly", String.valueOf(TargetHudModule.playersOnly));
            showHudProps.setProperty("targetHudHudX", String.valueOf(TargetHudModule.hudX));
            showHudProps.setProperty("targetHudHudY", String.valueOf(TargetHudModule.hudY));
            showHudProps.setProperty("targetHudScale", String.valueOf(TargetHudModule.scale));
            showHudProps.setProperty("targetHudBgMode", String.valueOf(TargetHudModule.bgMode));
            showHudProps.setProperty("targetHudOutlineColor", Integer.toHexString(TargetHudModule.outlineColor));
            showHudProps.setProperty("targetHudCustomWidth", String.valueOf(TargetHudModule.customWidth));
            showHudProps.setProperty("targetHudCustomHeight", String.valueOf(TargetHudModule.customHeight));

            // Armor HUD
            showHudProps.setProperty("armorHudEnabled", String.valueOf(ArmorHudModule.enabled));
            showHudProps.setProperty("armorHudHudX", String.valueOf(ArmorHudModule.hudX));
            showHudProps.setProperty("armorHudHudY", String.valueOf(ArmorHudModule.hudY));
            showHudProps.setProperty("armorHudScale", String.valueOf(ArmorHudModule.scale));
            showHudProps.setProperty("armorHudBgMode", String.valueOf(ArmorHudModule.bgMode));
            showHudProps.setProperty("armorHudOutlineColor", Integer.toHexString(ArmorHudModule.outlineColor));
            showHudProps.setProperty("armorHudCustomWidth", String.valueOf(ArmorHudModule.customWidth));
            showHudProps.setProperty("armorHudCustomHeight", String.valueOf(ArmorHudModule.customHeight));

            // KeyStrokes
            showHudProps.setProperty("keyStrokesEnabled", String.valueOf(KeyStrokesModule.enabled));
            showHudProps.setProperty("keyStrokesKeyBind", String.valueOf(KeyStrokesModule.keyBind));
            showHudProps.setProperty("keyStrokesHudX", String.valueOf(KeyStrokesModule.hudX));
            showHudProps.setProperty("keyStrokesHudY", String.valueOf(KeyStrokesModule.hudY));
            showHudProps.setProperty("keyStrokesScale", String.valueOf(KeyStrokesModule.scale));
            showHudProps.setProperty("keyStrokesBgMode", String.valueOf(KeyStrokesModule.bgMode));
            showHudProps.setProperty("keyStrokesOutlineColor", Integer.toHexString(KeyStrokesModule.outlineColor));
            for (KeyStrokesModule.KeyStroke key : KeyStrokesModule.keys) {
                showHudProps.setProperty("ks_" + key.name + "_x", String.valueOf(key.relX));
                showHudProps.setProperty("ks_" + key.name + "_y", String.valueOf(key.relY));
                showHudProps.setProperty("ks_" + key.name + "_w", String.valueOf(key.width));
                showHudProps.setProperty("ks_" + key.name + "_h", String.valueOf(key.height));
            }

            // CPS
            showHudProps.setProperty("cpsEnabled", String.valueOf(CpsModule.enabled));
            showHudProps.setProperty("cpsKeyBind", String.valueOf(CpsModule.keyBind));
            showHudProps.setProperty("cpsHudX", String.valueOf(CpsModule.hudX));
            showHudProps.setProperty("cpsHudY", String.valueOf(CpsModule.hudY));
            showHudProps.setProperty("cpsScale", String.valueOf(CpsModule.scale));
            showHudProps.setProperty("cpsBgMode", String.valueOf(CpsModule.bgMode));
            showHudProps.setProperty("cpsOutlineColor", Integer.toHexString(CpsModule.outlineColor));
            showHudProps.setProperty("cpsCustomWidth", String.valueOf(CpsModule.customWidth));
            showHudProps.setProperty("cpsCustomHeight", String.valueOf(CpsModule.customHeight));

            // FPS
            showHudProps.setProperty("fpsEnabled", String.valueOf(FpsModule.enabled));
            showHudProps.setProperty("fpsKeyBind", String.valueOf(FpsModule.keyBind));
            showHudProps.setProperty("fpsHudX", String.valueOf(FpsModule.hudX));
            showHudProps.setProperty("fpsHudY", String.valueOf(FpsModule.hudY));
            showHudProps.setProperty("fpsScale", String.valueOf(FpsModule.scale));
            showHudProps.setProperty("fpsBgMode", String.valueOf(FpsModule.bgMode));
            showHudProps.setProperty("fpsOutlineColor", Integer.toHexString(FpsModule.outlineColor));
            showHudProps.setProperty("fpsCustomWidth", String.valueOf(FpsModule.customWidth));
            showHudProps.setProperty("fpsCustomHeight", String.valueOf(FpsModule.customHeight));

            // Ping
            showHudProps.setProperty("pingEnabled", String.valueOf(PingModule.enabled));
            showHudProps.setProperty("pingKeyBind", String.valueOf(PingModule.keyBind));
            showHudProps.setProperty("pingHudX", String.valueOf(PingModule.hudX));
            showHudProps.setProperty("pingHudY", String.valueOf(PingModule.hudY));
            showHudProps.setProperty("pingScale", String.valueOf(PingModule.scale));
            showHudProps.setProperty("pingBgMode", String.valueOf(PingModule.bgMode));
            showHudProps.setProperty("pingOutlineColor", Integer.toHexString(PingModule.outlineColor));
            showHudProps.setProperty("pingCustomWidth", String.valueOf(PingModule.customWidth));
            showHudProps.setProperty("pingCustomHeight", String.valueOf(PingModule.customHeight));

            // Server Info
            showHudProps.setProperty("serverInfoEnabled", String.valueOf(ServerInfoModule.enabled));
            showHudProps.setProperty("serverInfoKeyBind", String.valueOf(ServerInfoModule.keyBind));
            showHudProps.setProperty("serverInfoHudX", String.valueOf(ServerInfoModule.hudX));
            showHudProps.setProperty("serverInfoHudY", String.valueOf(ServerInfoModule.hudY));
            showHudProps.setProperty("serverInfoScale", String.valueOf(ServerInfoModule.scale));
            showHudProps.setProperty("serverInfoBgMode", String.valueOf(ServerInfoModule.bgMode));
            showHudProps.setProperty("serverInfoOutlineColor", Integer.toHexString(ServerInfoModule.outlineColor));
            showHudProps.setProperty("serverInfoCustomWidth", String.valueOf(ServerInfoModule.customWidth));
            showHudProps.setProperty("serverInfoCustomHeight", String.valueOf(ServerInfoModule.customHeight));
            showHudProps.setProperty("serverInfoShowName", String.valueOf(ServerInfoModule.showName));
            showHudProps.setProperty("serverInfoShowServer", String.valueOf(ServerInfoModule.showServer));
            showHudProps.setProperty("serverInfoShowTime", String.valueOf(ServerInfoModule.showTime));
            showHudProps.setProperty("serverInfoDockedElements", String.join(",", ServerInfoModule.dockedElements));
            showHudProps.setProperty("serverInfoNameX", String.valueOf(ServerInfoModule.nameX));
            showHudProps.setProperty("serverInfoNameY", String.valueOf(ServerInfoModule.nameY));
            showHudProps.setProperty("serverInfoServerX", String.valueOf(ServerInfoModule.serverX));
            showHudProps.setProperty("serverInfoServerY", String.valueOf(ServerInfoModule.serverY));
            showHudProps.setProperty("serverInfoTimeX", String.valueOf(ServerInfoModule.timeX));
            showHudProps.setProperty("serverInfoTimeY", String.valueOf(ServerInfoModule.timeY));

            // Name Protect
            showHudProps.setProperty("nameProtectEnabled", String.valueOf(NameProtectModule.enabled));
            showHudProps.setProperty("nameProtectKeyBind", String.valueOf(NameProtectModule.keyBind));
            if (NameProtectModule.alias != null) {
                showHudProps.setProperty("nameProtectAlias", NameProtectModule.alias);
            }
            saveProps(visualsDir.resolve("show_hud.properties"), showHudProps, "Caeser Client - Show HUD");

            // 3. Visuals - Fullbright
            Properties fullbrightProps = new Properties();
            fullbrightProps.setProperty("enabled", String.valueOf(FullbrightModule.enabled));
            fullbrightProps.setProperty("intensity", String.valueOf(FullbrightModule.intensity));
            fullbrightProps.setProperty("keyBind", String.valueOf(FullbrightModule.keyBind));
            saveProps(visualsDir.resolve("fullbright.properties"), fullbrightProps, "Caeser Client - Fullbright");

            // 4. Visuals - Zoom
            Properties zoomProps = new Properties();
            zoomProps.setProperty("enabled", String.valueOf(ZoomModule.enabled));
            zoomProps.setProperty("keyBind", String.valueOf(ZoomModule.keyBind));
            zoomProps.setProperty("mode", String.valueOf(ZoomModule.mode));
            saveProps(visualsDir.resolve("zoom.properties"), zoomProps, "Caeser Client - Zoom");

            // 5. Visuals - Fake Scoreboard
            Properties sbProps = new Properties();
            sbProps.setProperty("enabled", String.valueOf(FakeScoreboardModule.enabled));
            sbProps.setProperty("keyBind", String.valueOf(FakeScoreboardModule.keyBind));
            sbProps.setProperty("expanded", String.valueOf(FakeScoreboardModule.expanded));
            sbProps.setProperty("hudX", String.valueOf(FakeScoreboardModule.hudX));
            sbProps.setProperty("hudY", String.valueOf(FakeScoreboardModule.hudY));
            sbProps.setProperty("scale", String.valueOf(FakeScoreboardModule.scale));
            sbProps.setProperty("bgMode", String.valueOf(FakeScoreboardModule.bgMode));
            sbProps.setProperty("outlineColor", Integer.toHexString(FakeScoreboardModule.outlineColor));
            sbProps.setProperty("customWidth", String.valueOf(FakeScoreboardModule.customWidth));
            sbProps.setProperty("customHeight", String.valueOf(FakeScoreboardModule.customHeight));
            if (FakeScoreboardModule.money != null) sbProps.setProperty("money", FakeScoreboardModule.money);
            if (FakeScoreboardModule.stars != null) sbProps.setProperty("stars", FakeScoreboardModule.stars);
            if (FakeScoreboardModule.kills != null) sbProps.setProperty("kills", FakeScoreboardModule.kills);
            if (FakeScoreboardModule.deaths != null) sbProps.setProperty("deaths", FakeScoreboardModule.deaths);
            if (FakeScoreboardModule.playtime != null) sbProps.setProperty("time", FakeScoreboardModule.playtime);
            saveProps(visualsDir.resolve("fake_scoreboard.properties"), sbProps, "Caeser Client - Fake Scoreboard");

            // Visuals - Spotify HUD
            Properties spotifyProps = new Properties();
            spotifyProps.setProperty("enabled", String.valueOf(SpotifyHudModule.enabled));
            spotifyProps.setProperty("keyBind", String.valueOf(SpotifyHudModule.keyBind));
            spotifyProps.setProperty("expanded", String.valueOf(SpotifyHudModule.expanded));
            spotifyProps.setProperty("hudX", String.valueOf(SpotifyHudModule.hudX));
            spotifyProps.setProperty("hudY", String.valueOf(SpotifyHudModule.hudY));
            spotifyProps.setProperty("scale", String.valueOf(SpotifyHudModule.scale));
            spotifyProps.setProperty("bgMode", String.valueOf(SpotifyHudModule.bgMode));
            spotifyProps.setProperty("outlineColor", Integer.toHexString(SpotifyHudModule.outlineColor));
            spotifyProps.setProperty("customWidth", String.valueOf(SpotifyHudModule.customWidth));
            spotifyProps.setProperty("customHeight", String.valueOf(SpotifyHudModule.customHeight));
            spotifyProps.setProperty("autoHide", String.valueOf(SpotifyHudModule.autoHide));
            saveProps(visualsDir.resolve("spotify_hud.properties"), spotifyProps, "Caeser Client - Spotify HUD");

            // Visuals - Scoreboard
            Properties realScoreboardProps = new Properties();
            realScoreboardProps.setProperty("enabled", String.valueOf(ScoreboardModule.enabled));
            realScoreboardProps.setProperty("keyBind", String.valueOf(ScoreboardModule.keyBind));
            realScoreboardProps.setProperty("expanded", String.valueOf(ScoreboardModule.expanded));
            realScoreboardProps.setProperty("hudX", String.valueOf(ScoreboardModule.hudX));
            realScoreboardProps.setProperty("hudY", String.valueOf(ScoreboardModule.hudY));
            realScoreboardProps.setProperty("scale", String.valueOf(ScoreboardModule.scale));
            realScoreboardProps.setProperty("bgMode", String.valueOf(ScoreboardModule.bgMode));
            realScoreboardProps.setProperty("outlineColor", Integer.toHexString(ScoreboardModule.outlineColor));
            realScoreboardProps.setProperty("customWidth", String.valueOf(ScoreboardModule.customWidth));
            realScoreboardProps.setProperty("customHeight", String.valueOf(ScoreboardModule.customHeight));
            saveProps(visualsDir.resolve("scoreboard.properties"), realScoreboardProps, "Caeser Client - Scoreboard");

            // Visuals - Custom Crosshair
            Properties crosshairProps = new Properties();
            crosshairProps.setProperty("enabled", String.valueOf(CustomCrosshairModule.enabled));
            crosshairProps.setProperty("keyBind", String.valueOf(CustomCrosshairModule.keyBind));
            crosshairProps.setProperty("expanded", String.valueOf(CustomCrosshairModule.expanded));
            crosshairProps.setProperty("color", Integer.toHexString(CustomCrosshairModule.color));
            crosshairProps.setProperty("grid", CustomCrosshairModule.getEncodedGrid());
            saveProps(visualsDir.resolve("custom_crosshair.properties"), crosshairProps, "Caeser Client - Custom Crosshair");

            // Combat - AutoClicker
            Properties acProps = new Properties();
            acProps.setProperty("enabled", String.valueOf(AutoClickerModule.enabled));
            acProps.setProperty("keyBind", String.valueOf(AutoClickerModule.keyBind));
            acProps.setProperty("expanded", String.valueOf(AutoClickerModule.expanded));
            acProps.setProperty("mode", String.valueOf(AutoClickerModule.mode));
            acProps.setProperty("cps", String.valueOf(AutoClickerModule.cps));
            acProps.setProperty("delaySeconds", String.valueOf(AutoClickerModule.delaySeconds));
            acProps.setProperty("randomJitter", String.valueOf(AutoClickerModule.randomJitter));
            acProps.setProperty("weaponOnly", String.valueOf(AutoClickerModule.weaponOnly));
            acProps.setProperty("button", String.valueOf(AutoClickerModule.button));
            saveProps(combatDir.resolve("auto_clicker.properties"), acProps, "Caeser Client - AutoClicker");

            // Combat - HitColor
            Properties hcProps = new Properties();
            hcProps.setProperty("enabled", String.valueOf(HitColorModule.enabled));
            hcProps.setProperty("keyBind", String.valueOf(HitColorModule.keyBind));
            hcProps.setProperty("expanded", String.valueOf(HitColorModule.expanded));
            hcProps.setProperty("color", Integer.toHexString(HitColorModule.color));
            hcProps.setProperty("alpha", String.valueOf(HitColorModule.alpha));
            saveProps(combatDir.resolve("hit_color.properties"), hcProps, "Caeser Client - HitColor");

            // Combat - ReachDisplay
            Properties rdProps = new Properties();
            rdProps.setProperty("enabled", String.valueOf(ReachDisplayModule.enabled));
            rdProps.setProperty("keyBind", String.valueOf(ReachDisplayModule.keyBind));
            rdProps.setProperty("expanded", String.valueOf(ReachDisplayModule.expanded));
            rdProps.setProperty("mode", String.valueOf(ReachDisplayModule.mode));
            rdProps.setProperty("hudX", String.valueOf(ReachDisplayModule.hudX));
            rdProps.setProperty("hudY", String.valueOf(ReachDisplayModule.hudY));
            rdProps.setProperty("scale", String.valueOf(ReachDisplayModule.scale));
            rdProps.setProperty("bgMode", String.valueOf(ReachDisplayModule.bgMode));
            rdProps.setProperty("outlineColor", Integer.toHexString(ReachDisplayModule.outlineColor));
            rdProps.setProperty("customWidth", String.valueOf(ReachDisplayModule.customWidth));
            rdProps.setProperty("customHeight", String.valueOf(ReachDisplayModule.customHeight));
            saveProps(combatDir.resolve("reach_display.properties"), rdProps, "Caeser Client - ReachDisplay");

            // Combat - LowShield
            Properties lsProps = new Properties();
            lsProps.setProperty("enabled", String.valueOf(LowShieldModule.enabled));
            lsProps.setProperty("keyBind", String.valueOf(LowShieldModule.keyBind));
            lsProps.setProperty("expanded", String.valueOf(LowShieldModule.expanded));
            lsProps.setProperty("heightPercent", String.valueOf(LowShieldModule.heightPercent));
            saveProps(combatDir.resolve("low_shield.properties"), lsProps, "Caeser Client - LowShield");

            // 6. Theme
            Properties themeProps = new Properties();
            themeProps.setProperty("theme", theme);
            themeProps.setProperty("seeThrough", String.valueOf(seeThrough));
            themeProps.setProperty("frostedBlur", String.valueOf(frostedBlur));
            themeProps.setProperty("ambientBackground", String.valueOf(ambientBackground));
            themeProps.setProperty("customMainColor", String.valueOf(customMainColor));
            themeProps.setProperty("classicToggle", String.valueOf(classicToggle));
            themeProps.setProperty("mainColor", String.valueOf(mainColor));
            themeProps.setProperty("ambientPrimary", String.valueOf(ambientPrimary));
            themeProps.setProperty("ambientSecondary", String.valueOf(ambientSecondary));
            themeProps.setProperty("ambientMode", String.valueOf(ambientMode));
            themeProps.setProperty("ambientIntensity", String.valueOf(ambientIntensity));
            themeProps.setProperty("ambientOpacity", String.valueOf(ambientOpacity));
            themeProps.setProperty("ambientRadius", String.valueOf(ambientRadius));
            themeProps.setProperty("ambientAnimation", String.valueOf(ambientAnimation));
            themeProps.setProperty("animationSpeed", String.valueOf(animationSpeed));
            saveProps(themeDir.resolve("theme.properties"), themeProps, "Caeser Client - Theme");

            // 7. Settings
            Properties settingsProps = new Properties();
            settingsProps.setProperty("menuBind", String.valueOf(menuBind));
            settingsProps.setProperty("quickFriend", String.valueOf(quickFriend));
            settingsProps.setProperty("guiAnimation", String.valueOf(guiAnimation));
            settingsProps.setProperty("selectedCategory", selectedCategory);
            saveProps(settingsDir.resolve("settings.properties"), settingsProps, "Caeser Client - General Settings");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Properties loadProps(Path path) {
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return props;
    }

    private static void saveProps(Path path, Properties props, String comments) {
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, comments);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadLegacy(Path legacyFile) {
        try (InputStream in = Files.newInputStream(legacyFile)) {
            Properties props = new Properties();
            props.load(in);

            if (props.containsKey("keyBind")) AutoAreaMinerModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
            if (props.containsKey("mode3x3")) AutoAreaMinerModule.mode3x3 = Boolean.parseBoolean(props.getProperty("mode3x3"));
            if (props.containsKey("corner1")) {
                String[] parts = props.getProperty("corner1").trim().split("\\s+");
                if (parts.length == 3) {
                    try {
                        AutoAreaMinerModule.corner1 = new net.minecraft.util.math.BlockPos(
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
                        );
                    } catch (Exception ignored) {}
                }
            }
            if (props.containsKey("corner2")) {
                String[] parts = props.getProperty("corner2").trim().split("\\s+");
                if (parts.length == 3) {
                    try {
                        AutoAreaMinerModule.corner2 = new net.minecraft.util.math.BlockPos(
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
                        );
                    } catch (Exception ignored) {}
                }
            }
            if (props.containsKey("outlineColor")) outlineColor = (int) Long.parseLong(props.getProperty("outlineColor", "FF00FFFF"), 16);
            if (props.containsKey("renderMode")) renderMode = Integer.parseInt(props.getProperty("renderMode"));
            if (props.containsKey("outlineWidth")) outlineWidth = Float.parseFloat(props.getProperty("outlineWidth"));

            if (props.containsKey("fullbrightEnabled")) fullbrightEnabled = Boolean.parseBoolean(props.getProperty("fullbrightEnabled"));
            if (props.containsKey("fullbrightIntensity")) fullbrightIntensity = Float.parseFloat(props.getProperty("fullbrightIntensity"));
            FullbrightModule.enabled = fullbrightEnabled;
            FullbrightModule.intensity = fullbrightIntensity;
            if (props.containsKey("fullbrightKeyBind")) FullbrightModule.keyBind = Integer.parseInt(props.getProperty("fullbrightKeyBind"));

            if (props.containsKey("zoomEnabled")) ZoomModule.enabled = Boolean.parseBoolean(props.getProperty("zoomEnabled"));
            if (props.containsKey("zoomKeyBind")) ZoomModule.keyBind = Integer.parseInt(props.getProperty("zoomKeyBind"));
            if (props.containsKey("zoomMode")) ZoomModule.mode = Integer.parseInt(props.getProperty("zoomMode"));

            if (props.containsKey("fakeScoreboardEnabled")) FakeScoreboardModule.enabled = Boolean.parseBoolean(props.getProperty("fakeScoreboardEnabled"));
            if (props.containsKey("fakeScoreboardKeyBind")) FakeScoreboardModule.keyBind = Integer.parseInt(props.getProperty("fakeScoreboardKeyBind"));
            if (props.containsKey("fakeScoreboardExpanded")) FakeScoreboardModule.expanded = Boolean.parseBoolean(props.getProperty("fakeScoreboardExpanded"));
            if (props.containsKey("fakeScoreboardHudX")) FakeScoreboardModule.hudX = Integer.parseInt(props.getProperty("fakeScoreboardHudX"));
            if (props.containsKey("fakeScoreboardHudY")) FakeScoreboardModule.hudY = Integer.parseInt(props.getProperty("fakeScoreboardHudY"));
            if (props.containsKey("fakeScoreboardScale")) FakeScoreboardModule.scale = Float.parseFloat(props.getProperty("fakeScoreboardScale"));
            if (props.containsKey("fakeScoreboardBgMode")) FakeScoreboardModule.bgMode = Integer.parseInt(props.getProperty("fakeScoreboardBgMode"));
            if (props.containsKey("fakeScoreboardMoney")) FakeScoreboardModule.money = props.getProperty("fakeScoreboardMoney");
            if (props.containsKey("fakeScoreboardStars")) FakeScoreboardModule.stars = props.getProperty("fakeScoreboardStars");
            if (props.containsKey("fakeScoreboardKills")) FakeScoreboardModule.kills = props.getProperty("fakeScoreboardKills");
            if (props.containsKey("fakeScoreboardDeaths")) FakeScoreboardModule.deaths = props.getProperty("fakeScoreboardDeaths");
            if (props.containsKey("fakeScoreboardTime")) FakeScoreboardModule.playtime = props.getProperty("fakeScoreboardTime");

            if (props.containsKey("showHudEnabled")) ShowHudModule.enabled = Boolean.parseBoolean(props.getProperty("showHudEnabled"));
            if (props.containsKey("showHudKeyBind")) ShowHudModule.keyBind = Integer.parseInt(props.getProperty("showHudKeyBind"));
            if (props.containsKey("showHudExpanded")) ShowHudModule.expanded = Boolean.parseBoolean(props.getProperty("showHudExpanded"));

            if (props.containsKey("clockEnabled")) ClockModule.enabled = Boolean.parseBoolean(props.getProperty("clockEnabled"));
            if (props.containsKey("clockHudX")) ClockModule.hudX = Integer.parseInt(props.getProperty("clockHudX"));
            if (props.containsKey("clockHudY")) ClockModule.hudY = Integer.parseInt(props.getProperty("clockHudY"));
            if (props.containsKey("clockScale")) ClockModule.scale = Float.parseFloat(props.getProperty("clockScale"));
            if (props.containsKey("clockBgMode")) ClockModule.bgMode = Integer.parseInt(props.getProperty("clockBgMode"));

            if (props.containsKey("coordinatesEnabled")) CoordinatesModule.enabled = Boolean.parseBoolean(props.getProperty("coordinatesEnabled"));
            if (props.containsKey("coordinatesHudX")) CoordinatesModule.hudX = Integer.parseInt(props.getProperty("coordinatesHudX"));
            if (props.containsKey("coordinatesHudY")) CoordinatesModule.hudY = Integer.parseInt(props.getProperty("coordinatesHudY"));
            if (props.containsKey("coordinatesScale")) CoordinatesModule.scale = Float.parseFloat(props.getProperty("coordinatesScale"));
            if (props.containsKey("coordinatesBgMode")) CoordinatesModule.bgMode = Integer.parseInt(props.getProperty("coordinatesBgMode"));

            if (props.containsKey("potionsEnabled")) PotionsModule.enabled = Boolean.parseBoolean(props.getProperty("potionsEnabled"));
            if (props.containsKey("potionsHudX")) PotionsModule.hudX = Integer.parseInt(props.getProperty("potionsHudX"));
            if (props.containsKey("potionsHudY")) PotionsModule.hudY = Integer.parseInt(props.getProperty("potionsHudY"));
            if (props.containsKey("potionsScale")) PotionsModule.scale = Float.parseFloat(props.getProperty("potionsScale"));
            if (props.containsKey("potionsBgMode")) PotionsModule.bgMode = Integer.parseInt(props.getProperty("potionsBgMode"));

            if (props.containsKey("targetHudEnabled")) TargetHudModule.enabled = Boolean.parseBoolean(props.getProperty("targetHudEnabled"));
            if (props.containsKey("targetHudExpanded")) TargetHudModule.expanded = Boolean.parseBoolean(props.getProperty("targetHudExpanded"));
            if (props.containsKey("targetHudShowHearts")) TargetHudModule.showHearts = Boolean.parseBoolean(props.getProperty("targetHudShowHearts"));
            if (props.containsKey("targetHudShowArmor")) TargetHudModule.showArmor = Boolean.parseBoolean(props.getProperty("targetHudShowArmor"));
            if (props.containsKey("targetHudPlayersOnly")) TargetHudModule.playersOnly = Boolean.parseBoolean(props.getProperty("targetHudPlayersOnly"));
            if (props.containsKey("targetHudHudX")) TargetHudModule.hudX = Integer.parseInt(props.getProperty("targetHudHudX"));
            if (props.containsKey("targetHudHudY")) TargetHudModule.hudY = Integer.parseInt(props.getProperty("targetHudHudY"));
            if (props.containsKey("targetHudScale")) TargetHudModule.scale = Float.parseFloat(props.getProperty("targetHudScale"));
            if (props.containsKey("targetHudBgMode")) TargetHudModule.bgMode = Integer.parseInt(props.getProperty("targetHudBgMode"));

            if (props.containsKey("armorHudEnabled")) ArmorHudModule.enabled = Boolean.parseBoolean(props.getProperty("armorHudEnabled"));
            if (props.containsKey("armorHudHudX")) ArmorHudModule.hudX = Integer.parseInt(props.getProperty("armorHudHudX"));
            if (props.containsKey("armorHudHudY")) ArmorHudModule.hudY = Integer.parseInt(props.getProperty("armorHudHudY"));
            if (props.containsKey("armorHudScale")) ArmorHudModule.scale = Float.parseFloat(props.getProperty("armorHudScale"));
            if (props.containsKey("armorHudBgMode")) ArmorHudModule.bgMode = Integer.parseInt(props.getProperty("armorHudBgMode"));

            if (props.containsKey("keyStrokesEnabled")) KeyStrokesModule.enabled = Boolean.parseBoolean(props.getProperty("keyStrokesEnabled"));
            if (props.containsKey("keyStrokesKeyBind")) KeyStrokesModule.keyBind = Integer.parseInt(props.getProperty("keyStrokesKeyBind"));
            if (props.containsKey("keyStrokesHudX")) KeyStrokesModule.hudX = Integer.parseInt(props.getProperty("keyStrokesHudX"));
            if (props.containsKey("keyStrokesHudY")) KeyStrokesModule.hudY = Integer.parseInt(props.getProperty("keyStrokesHudY"));
            if (props.containsKey("keyStrokesScale")) KeyStrokesModule.scale = Float.parseFloat(props.getProperty("keyStrokesScale"));
            for (KeyStrokesModule.KeyStroke key : KeyStrokesModule.keys) {
                if (props.containsKey("ks_" + key.name + "_x")) key.relX = Integer.parseInt(props.getProperty("ks_" + key.name + "_x"));
                if (props.containsKey("ks_" + key.name + "_y")) key.relY = Integer.parseInt(props.getProperty("ks_" + key.name + "_y"));
                if (props.containsKey("ks_" + key.name + "_w")) key.width = Integer.parseInt(props.getProperty("ks_" + key.name + "_w"));
                if (props.containsKey("ks_" + key.name + "_h")) key.height = Integer.parseInt(props.getProperty("ks_" + key.name + "_h"));
            }

            if (props.containsKey("cpsEnabled")) CpsModule.enabled = Boolean.parseBoolean(props.getProperty("cpsEnabled"));
            if (props.containsKey("cpsKeyBind")) CpsModule.keyBind = Integer.parseInt(props.getProperty("cpsKeyBind"));
            if (props.containsKey("cpsHudX")) CpsModule.hudX = Integer.parseInt(props.getProperty("cpsHudX"));
            if (props.containsKey("cpsHudY")) CpsModule.hudY = Integer.parseInt(props.getProperty("cpsHudY"));
            if (props.containsKey("cpsScale")) CpsModule.scale = Float.parseFloat(props.getProperty("cpsScale"));
            if (props.containsKey("cpsBgMode")) CpsModule.bgMode = Integer.parseInt(props.getProperty("cpsBgMode"));

            if (props.containsKey("fpsEnabled")) FpsModule.enabled = Boolean.parseBoolean(props.getProperty("fpsEnabled"));
            if (props.containsKey("fpsKeyBind")) FpsModule.keyBind = Integer.parseInt(props.getProperty("fpsKeyBind"));
            if (props.containsKey("fpsHudX")) FpsModule.hudX = Integer.parseInt(props.getProperty("fpsHudX"));
            if (props.containsKey("fpsHudY")) FpsModule.hudY = Integer.parseInt(props.getProperty("fpsHudY"));
            if (props.containsKey("fpsScale")) FpsModule.scale = Float.parseFloat(props.getProperty("fpsScale"));
            if (props.containsKey("fpsBgMode")) FpsModule.bgMode = Integer.parseInt(props.getProperty("fpsBgMode"));

            if (props.containsKey("pingEnabled")) PingModule.enabled = Boolean.parseBoolean(props.getProperty("pingEnabled"));
            if (props.containsKey("pingKeyBind")) PingModule.keyBind = Integer.parseInt(props.getProperty("pingKeyBind"));
            if (props.containsKey("pingHudX")) PingModule.hudX = Integer.parseInt(props.getProperty("pingHudX"));
            if (props.containsKey("pingHudY")) PingModule.hudY = Integer.parseInt(props.getProperty("pingHudY"));
            if (props.containsKey("pingScale")) PingModule.scale = Float.parseFloat(props.getProperty("pingScale"));
            if (props.containsKey("pingBgMode")) PingModule.bgMode = Integer.parseInt(props.getProperty("pingBgMode"));

            if (props.containsKey("serverInfoEnabled")) ServerInfoModule.enabled = Boolean.parseBoolean(props.getProperty("serverInfoEnabled"));
            if (props.containsKey("serverInfoKeyBind")) ServerInfoModule.keyBind = Integer.parseInt(props.getProperty("serverInfoKeyBind"));
            if (props.containsKey("serverInfoHudX")) ServerInfoModule.hudX = Integer.parseInt(props.getProperty("serverInfoHudX"));
            if (props.containsKey("serverInfoHudY")) ServerInfoModule.hudY = Integer.parseInt(props.getProperty("serverInfoHudY"));
            if (props.containsKey("serverInfoScale")) ServerInfoModule.scale = Float.parseFloat(props.getProperty("serverInfoScale"));
            if (props.containsKey("serverInfoBgMode")) ServerInfoModule.bgMode = Integer.parseInt(props.getProperty("serverInfoBgMode"));
            if (props.containsKey("serverInfoShowName")) ServerInfoModule.showName = Boolean.parseBoolean(props.getProperty("serverInfoShowName"));
            if (props.containsKey("serverInfoShowServer")) ServerInfoModule.showServer = Boolean.parseBoolean(props.getProperty("serverInfoShowServer"));
            if (props.containsKey("serverInfoShowTime")) ServerInfoModule.showTime = Boolean.parseBoolean(props.getProperty("serverInfoShowTime"));

            if (props.containsKey("nameProtectEnabled")) NameProtectModule.enabled = Boolean.parseBoolean(props.getProperty("nameProtectEnabled"));
            if (props.containsKey("nameProtectKeyBind")) NameProtectModule.keyBind = Integer.parseInt(props.getProperty("nameProtectKeyBind"));
            if (props.containsKey("nameProtectAlias")) NameProtectModule.alias = props.getProperty("nameProtectAlias");

            if (props.containsKey("theme")) theme = props.getProperty("theme");
            if (props.containsKey("seeThrough")) seeThrough = Boolean.parseBoolean(props.getProperty("seeThrough"));
            if (props.containsKey("frostedBlur")) frostedBlur = Boolean.parseBoolean(props.getProperty("frostedBlur"));
            if (props.containsKey("ambientBackground")) ambientBackground = Boolean.parseBoolean(props.getProperty("ambientBackground"));
            if (props.containsKey("customMainColor")) customMainColor = Boolean.parseBoolean(props.getProperty("customMainColor"));
            if (props.containsKey("classicToggle")) classicToggle = Boolean.parseBoolean(props.getProperty("classicToggle"));
            if (props.containsKey("mainColor")) mainColor = Integer.parseInt(props.getProperty("mainColor"));
            if (props.containsKey("ambientPrimary")) ambientPrimary = Integer.parseInt(props.getProperty("ambientPrimary"));
            if (props.containsKey("ambientSecondary")) ambientSecondary = Integer.parseInt(props.getProperty("ambientSecondary"));
            if (props.containsKey("ambientMode")) ambientMode = Integer.parseInt(props.getProperty("ambientMode"));
            if (props.containsKey("ambientIntensity")) ambientIntensity = Math.clamp(Float.parseFloat(props.getProperty("ambientIntensity")), 0f, 1f);
            if (props.containsKey("ambientOpacity")) ambientOpacity = Math.clamp(Float.parseFloat(props.getProperty("ambientOpacity")), 0f, 1f);
            if (props.containsKey("ambientRadius")) ambientRadius = Math.clamp(Float.parseFloat(props.getProperty("ambientRadius")), 0f, 1f);
            if (props.containsKey("ambientAnimation")) ambientAnimation = Boolean.parseBoolean(props.getProperty("ambientAnimation"));
            if (props.containsKey("animationSpeed")) animationSpeed = Math.clamp(Float.parseFloat(props.getProperty("animationSpeed")), 0f, 1f);

            if (props.containsKey("menuBind")) menuBind = Integer.parseInt(props.getProperty("menuBind"));
            if (props.containsKey("quickFriend")) quickFriend = Boolean.parseBoolean(props.getProperty("quickFriend"));
            if (props.containsKey("guiAnimation")) guiAnimation = Integer.parseInt(props.getProperty("guiAnimation"));
            if (props.containsKey("selectedCategory")) {
                selectedCategory = props.getProperty("selectedCategory");
                com.bame.client.gui.BameClientScreen.selected = selectedCategory;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

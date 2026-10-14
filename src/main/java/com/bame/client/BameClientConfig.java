package com.bame.client;

import com.bame.client.module.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BameClientConfig {
    public static final Path BASE_CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("CaeserClient");

    public static int outlineColor = 0xFF00FFFF;
    public static int renderMode = 0; // 0=Clean, 1=Outline, 2=Corners, 3=Pulse
    public static float outlineWidth = 1.0f;
    public static boolean fullbrightEnabled = false;
    public static float fullbrightIntensity = 1.0f;
    public static String theme = "Ultraviolet";
    public static boolean seeThrough = false, frostedBlur = true, ambientBackground = false;

    // Custom Wallpaper
    public static boolean customWallpaper = false;
    public static String selectedWallpaper = "";
    public static float wallpaperBrightness = 0.85f;
    public static float wallpaperBlur = 0.0f;

    public static int guiAnimation = 0; // 0=Fade In, 1=Slide In, 2=Elastic Bounce, 3=Swirl Twist, 4=None
    public static boolean customMainColor = false, classicToggle = false, ambientAnimation = true;
    public static int mainColor = 0xFFB23CEE, ambientPrimary = 0xFF5675FF, ambientSecondary = 0xFFA647F2;
    public static int ambientMode = 0;
    public static float ambientIntensity = 1, ambientOpacity = 1, ambientRadius = 1, animationSpeed = 1;
    public static int menuBind = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static boolean quickFriend = false;
    /** Zuletzt gewählter Tab im Mod-Menü (einzige Quelle für den aktiven Tab, wird gespeichert). */
    public static String selectedCategory = "Combat";

    // Secret Key Combo Settings (Hidden GUI unlock)
    // Standard: Strg + Umschalt + K (K ist weder in Vanilla noch bei einem Modul standardmäßig belegt)
    public static final int DEFAULT_SECRET_KEY = org.lwjgl.glfw.GLFW.GLFW_KEY_K;
    public static final boolean DEFAULT_SECRET_CTRL = true;
    public static final boolean DEFAULT_SECRET_SHIFT = true;
    public static final boolean DEFAULT_SECRET_ALT = false;
    public static boolean secretRequireShift = DEFAULT_SECRET_SHIFT;
    public static boolean secretRequireCtrl = DEFAULT_SECRET_CTRL;
    public static boolean secretRequireAlt = DEFAULT_SECRET_ALT;
    public static int secretKey = DEFAULT_SECRET_KEY;
    public static boolean secretUnlocked = false; // Always starts locked on launch

    // Client Sounds
    public static boolean moduleSound = true;
    public static int soundPack = 0; // 0=Default, 1=Mechanical, 2=Pop, 3=Metallic, 4=Modern
    public static float soundVolume = 0.70f;
    public static boolean hoverSound = true;
    public static int hoverStyle = 0; // 0=Soft Tick, 1=Pop, 2=Subtle, 3=Breeze
    public static float hoverVolume = 1.00f;
    public static boolean notifications = true;
    public static boolean threeDGui = true;

    public static void load() {
        Path visualsDir = BASE_CONFIG_DIR.resolve("Visuals");
        Path worldDir = BASE_CONFIG_DIR.resolve("World");

        // If new modular structure exists, load from it
        if (Files.exists(visualsDir.resolve("show_hud.properties")) || Files.exists(worldDir.resolve("auto_area_miner.properties"))) {
            loadModular();
            afterLoad();
            if (dirty) saveNow(); // Migrationen (z. B. geheime Tastenkombination) sofort sichern
            return;
        }

        // Check for legacy single file to migrate
        Path legacyFile = FabricLoader.getInstance().getConfigDir().resolve("bameclient.properties");
        if (Files.exists(legacyFile)) {
            loadLegacy(legacyFile);
            afterLoad();
            saveNow();
            return;
        }

        // First run: save defaults into modular folders
        afterLoad();
        saveNow();
    }

    /** Gemeinsame Nachbearbeitung nach jedem Laden. */
    private static void afterLoad() {
        selectedCategory = sanitizeCategory(selectedCategory);
        // Versteckte Kampf-Module laufen nach einem Neustart nie weiter (Einstellungswerte bleiben erhalten)
        disableHiddenCombatModules();
        secretUnlocked = false;
    }

    private static void loadModular() {
        try {
            // 1. World - Auto Area Miner
            Path minerFile = BASE_CONFIG_DIR.resolve("World").resolve("auto_area_miner.properties");
            if (Files.exists(minerFile)) { try {
                Properties props = loadProps(minerFile);
                AutoAreaMinerModule.keyBind = getInt(props, "keyBind", AutoAreaMinerModule.keyBind);
                AutoAreaMinerModule.mode3x3 = getBool(props, "mode3x3", AutoAreaMinerModule.mode3x3);
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
                outlineColor = getHex(props, "outlineColor", outlineColor);
                renderMode = getInt(props, "renderMode", renderMode);
                outlineWidth = getFloat(props, "outlineWidth", outlineWidth);
            } catch (Exception e) { logLoadError(minerFile, e); } }

            // Movement - InvMove
            Path invMoveFile = BASE_CONFIG_DIR.resolve("Movement").resolve("inv_move.properties");
            if (Files.exists(invMoveFile)) { try {
                Properties props = loadProps(invMoveFile);
                InvMoveModule.enabled = getBool(props, "enabled", InvMoveModule.enabled);
                InvMoveModule.keyBind = getInt(props, "keyBind", InvMoveModule.keyBind);
                InvMoveModule.expanded = getBool(props, "expanded", InvMoveModule.expanded);
                InvMoveModule.jump = getBool(props, "jump", InvMoveModule.jump);
                InvMoveModule.sprint = getBool(props, "sprint", InvMoveModule.sprint);
                InvMoveModule.sneak = getBool(props, "sneak", InvMoveModule.sneak);
                InvMoveModule.rotateWithArrows = getBool(props, "rotateWithArrows", InvMoveModule.rotateWithArrows);
            } catch (Exception e) { logLoadError(invMoveFile, e); } }

            // 2. Visuals - Show HUD
            Path showHudFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("show_hud.properties");
            if (Files.exists(showHudFile)) { try {
                Properties props = loadProps(showHudFile);
                ShowHudModule.enabled = getBool(props, "showHudEnabled", ShowHudModule.enabled);
                ShowHudModule.keyBind = getInt(props, "showHudKeyBind", ShowHudModule.keyBind);
                ShowHudModule.expanded = getBool(props, "showHudExpanded", ShowHudModule.expanded);

                // Clock
                ClockModule.enabled = getBool(props, "clockEnabled", ClockModule.enabled);
                ClockModule.hudX = getInt(props, "clockHudX", ClockModule.hudX);
                ClockModule.hudY = getInt(props, "clockHudY", ClockModule.hudY);
                ClockModule.scale = getFloat(props, "clockScale", ClockModule.scale);
                ClockModule.bgMode = getInt(props, "clockBgMode", ClockModule.bgMode);
                ClockModule.outlineColor = getHex(props, "clockOutlineColor", ClockModule.outlineColor);
                ClockModule.customWidth = getInt(props, "clockCustomWidth", ClockModule.customWidth);
                ClockModule.customHeight = getInt(props, "clockCustomHeight", ClockModule.customHeight);

                // Coordinates
                CoordinatesModule.enabled = getBool(props, "coordinatesEnabled", CoordinatesModule.enabled);
                CoordinatesModule.hudX = getInt(props, "coordinatesHudX", CoordinatesModule.hudX);
                CoordinatesModule.hudY = getInt(props, "coordinatesHudY", CoordinatesModule.hudY);
                CoordinatesModule.scale = getFloat(props, "coordinatesScale", CoordinatesModule.scale);
                CoordinatesModule.bgMode = getInt(props, "coordinatesBgMode", CoordinatesModule.bgMode);
                CoordinatesModule.outlineColor = getHex(props, "coordinatesOutlineColor", CoordinatesModule.outlineColor);
                CoordinatesModule.customWidth = getInt(props, "coordinatesCustomWidth", CoordinatesModule.customWidth);
                CoordinatesModule.customHeight = getInt(props, "coordinatesCustomHeight", CoordinatesModule.customHeight);
                CoordinatesModule.expanded = getBool(props, "coordinatesExpanded", CoordinatesModule.expanded);
                CoordinatesModule.layoutMode = getInt(props, "coordinatesLayoutMode", CoordinatesModule.layoutMode);
                CoordinatesModule.style = getInt(props, "coordinatesStyle", CoordinatesModule.style);
                CoordinatesModule.showX = getBool(props, "coordinatesShowX", CoordinatesModule.showX);
                CoordinatesModule.showY = getBool(props, "coordinatesShowY", CoordinatesModule.showY);
                CoordinatesModule.showZ = getBool(props, "coordinatesShowZ", CoordinatesModule.showZ);
                CoordinatesModule.decimals = getBool(props, "coordinatesDecimals", CoordinatesModule.decimals);
                CoordinatesModule.showNether = getBool(props, "coordinatesShowNether", CoordinatesModule.showNether);
                CoordinatesModule.showDirection = getBool(props, "coordinatesShowDirection", CoordinatesModule.showDirection);

                // Potions
                PotionsModule.enabled = getBool(props, "potionsEnabled", PotionsModule.enabled);
                PotionsModule.hudX = getInt(props, "potionsHudX", PotionsModule.hudX);
                PotionsModule.hudY = getInt(props, "potionsHudY", PotionsModule.hudY);
                PotionsModule.scale = getFloat(props, "potionsScale", PotionsModule.scale);
                PotionsModule.bgMode = getInt(props, "potionsBgMode", PotionsModule.bgMode);
                PotionsModule.outlineColor = getHex(props, "potionsOutlineColor", PotionsModule.outlineColor);
                PotionsModule.customWidth = getInt(props, "potionsCustomWidth", PotionsModule.customWidth);
                PotionsModule.customHeight = getInt(props, "potionsCustomHeight", PotionsModule.customHeight);

                // Target HUD
                TargetHudModule.enabled = getBool(props, "targetHudEnabled", TargetHudModule.enabled);
                TargetHudModule.expanded = getBool(props, "targetHudExpanded", TargetHudModule.expanded);
                TargetHudModule.showHearts = getBool(props, "targetHudShowHearts", TargetHudModule.showHearts);
                TargetHudModule.showArmor = getBool(props, "targetHudShowArmor", TargetHudModule.showArmor);
                TargetHudModule.playersOnly = getBool(props, "targetHudPlayersOnly", TargetHudModule.playersOnly);
                TargetHudModule.hudX = getInt(props, "targetHudHudX", TargetHudModule.hudX);
                TargetHudModule.hudY = getInt(props, "targetHudHudY", TargetHudModule.hudY);
                TargetHudModule.scale = getFloat(props, "targetHudScale", TargetHudModule.scale);
                TargetHudModule.bgMode = getInt(props, "targetHudBgMode", TargetHudModule.bgMode);
                TargetHudModule.outlineColor = getHex(props, "targetHudOutlineColor", TargetHudModule.outlineColor);
                TargetHudModule.customWidth = getInt(props, "targetHudCustomWidth", TargetHudModule.customWidth);
                TargetHudModule.customHeight = getInt(props, "targetHudCustomHeight", TargetHudModule.customHeight);

                // Armor HUD
                ArmorHudModule.enabled = getBool(props, "armorHudEnabled", ArmorHudModule.enabled);
                ArmorHudModule.hudX = getInt(props, "armorHudHudX", ArmorHudModule.hudX);
                ArmorHudModule.hudY = getInt(props, "armorHudHudY", ArmorHudModule.hudY);
                ArmorHudModule.scale = getFloat(props, "armorHudScale", ArmorHudModule.scale);
                ArmorHudModule.bgMode = getInt(props, "armorHudBgMode", ArmorHudModule.bgMode);
                ArmorHudModule.outlineColor = getHex(props, "armorHudOutlineColor", ArmorHudModule.outlineColor);
                ArmorHudModule.customWidth = getInt(props, "armorHudCustomWidth", ArmorHudModule.customWidth);
                ArmorHudModule.customHeight = getInt(props, "armorHudCustomHeight", ArmorHudModule.customHeight);

                // KeyStrokes
                KeyStrokesModule.enabled = getBool(props, "keyStrokesEnabled", KeyStrokesModule.enabled);
                KeyStrokesModule.keyBind = getInt(props, "keyStrokesKeyBind", KeyStrokesModule.keyBind);
                KeyStrokesModule.hudX = getInt(props, "keyStrokesHudX", KeyStrokesModule.hudX);
                KeyStrokesModule.hudY = getInt(props, "keyStrokesHudY", KeyStrokesModule.hudY);
                KeyStrokesModule.scale = getFloat(props, "keyStrokesScale", KeyStrokesModule.scale);
                KeyStrokesModule.bgMode = getInt(props, "keyStrokesBgMode", KeyStrokesModule.bgMode);
                KeyStrokesModule.outlineColor = getHex(props, "keyStrokesOutlineColor", KeyStrokesModule.outlineColor);
                for (KeyStrokesModule.KeyStroke key : KeyStrokesModule.keys) {
                    key.relX = getInt(props, "ks_" + key.name + "_x", key.relX);
                    key.relY = getInt(props, "ks_" + key.name + "_y", key.relY);
                    key.width = getInt(props, "ks_" + key.name + "_w", key.width);
                    key.height = getInt(props, "ks_" + key.name + "_h", key.height);
                }

                // CPS
                CpsModule.enabled = getBool(props, "cpsEnabled", CpsModule.enabled);
                CpsModule.keyBind = getInt(props, "cpsKeyBind", CpsModule.keyBind);
                CpsModule.hudX = getInt(props, "cpsHudX", CpsModule.hudX);
                CpsModule.hudY = getInt(props, "cpsHudY", CpsModule.hudY);
                CpsModule.scale = getFloat(props, "cpsScale", CpsModule.scale);
                CpsModule.bgMode = getInt(props, "cpsBgMode", CpsModule.bgMode);
                CpsModule.outlineColor = getHex(props, "cpsOutlineColor", CpsModule.outlineColor);
                CpsModule.customWidth = getInt(props, "cpsCustomWidth", CpsModule.customWidth);
                CpsModule.customHeight = getInt(props, "cpsCustomHeight", CpsModule.customHeight);

                // FPS
                FpsModule.enabled = getBool(props, "fpsEnabled", FpsModule.enabled);
                FpsModule.keyBind = getInt(props, "fpsKeyBind", FpsModule.keyBind);
                FpsModule.hudX = getInt(props, "fpsHudX", FpsModule.hudX);
                FpsModule.hudY = getInt(props, "fpsHudY", FpsModule.hudY);
                FpsModule.scale = getFloat(props, "fpsScale", FpsModule.scale);
                FpsModule.bgMode = getInt(props, "fpsBgMode", FpsModule.bgMode);
                FpsModule.outlineColor = getHex(props, "fpsOutlineColor", FpsModule.outlineColor);
                FpsModule.customWidth = getInt(props, "fpsCustomWidth", FpsModule.customWidth);
                FpsModule.customHeight = getInt(props, "fpsCustomHeight", FpsModule.customHeight);

                // Ping
                PingModule.enabled = getBool(props, "pingEnabled", PingModule.enabled);
                PingModule.keyBind = getInt(props, "pingKeyBind", PingModule.keyBind);
                PingModule.hudX = getInt(props, "pingHudX", PingModule.hudX);
                PingModule.hudY = getInt(props, "pingHudY", PingModule.hudY);
                PingModule.scale = getFloat(props, "pingScale", PingModule.scale);
                PingModule.bgMode = getInt(props, "pingBgMode", PingModule.bgMode);
                PingModule.outlineColor = getHex(props, "pingOutlineColor", PingModule.outlineColor);
                PingModule.customWidth = getInt(props, "pingCustomWidth", PingModule.customWidth);
                PingModule.customHeight = getInt(props, "pingCustomHeight", PingModule.customHeight);

                // Server Info
                ServerInfoModule.enabled = getBool(props, "serverInfoEnabled", ServerInfoModule.enabled);
                ServerInfoModule.keyBind = getInt(props, "serverInfoKeyBind", ServerInfoModule.keyBind);
                ServerInfoModule.hudX = getInt(props, "serverInfoHudX", ServerInfoModule.hudX);
                ServerInfoModule.hudY = getInt(props, "serverInfoHudY", ServerInfoModule.hudY);
                ServerInfoModule.scale = getFloat(props, "serverInfoScale", ServerInfoModule.scale);
                ServerInfoModule.bgMode = getInt(props, "serverInfoBgMode", ServerInfoModule.bgMode);
                ServerInfoModule.outlineColor = getHex(props, "serverInfoOutlineColor", ServerInfoModule.outlineColor);
                ServerInfoModule.customWidth = getInt(props, "serverInfoCustomWidth", ServerInfoModule.customWidth);
                ServerInfoModule.customHeight = getInt(props, "serverInfoCustomHeight", ServerInfoModule.customHeight);
                ServerInfoModule.showName = getBool(props, "serverInfoShowName", ServerInfoModule.showName);
                ServerInfoModule.showServer = getBool(props, "serverInfoShowServer", ServerInfoModule.showServer);
                ServerInfoModule.showTime = getBool(props, "serverInfoShowTime", ServerInfoModule.showTime);
                if (props.containsKey("serverInfoDockedElements")) {
                    String dockStr = props.getProperty("serverInfoDockedElements");
                    if (!dockStr.trim().isEmpty()) {
                        ServerInfoModule.dockedElements = new java.util.ArrayList<>(java.util.Arrays.asList(dockStr.split(",")));
                    } else {
                        ServerInfoModule.dockedElements = new java.util.ArrayList<>();
                    }
                }
                ServerInfoModule.nameX = getInt(props, "serverInfoNameX", ServerInfoModule.nameX);
                ServerInfoModule.nameY = getInt(props, "serverInfoNameY", ServerInfoModule.nameY);
                ServerInfoModule.serverX = getInt(props, "serverInfoServerX", ServerInfoModule.serverX);
                ServerInfoModule.serverY = getInt(props, "serverInfoServerY", ServerInfoModule.serverY);
                ServerInfoModule.timeX = getInt(props, "serverInfoTimeX", ServerInfoModule.timeX);
                ServerInfoModule.timeY = getInt(props, "serverInfoTimeY", ServerInfoModule.timeY);

                // Name Protect
                NameProtectModule.enabled = getBool(props, "nameProtectEnabled", NameProtectModule.enabled);
                NameProtectModule.keyBind = getInt(props, "nameProtectKeyBind", NameProtectModule.keyBind);
                NameProtectModule.alias = getString(props, "nameProtectAlias", NameProtectModule.alias);
                NameProtectModule.expanded = getBool(props, "nameProtectExpanded", NameProtectModule.expanded);
            } catch (Exception e) { logLoadError(showHudFile, e); } }

            // 3. Visuals - Fullbright
            Path fullbrightFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("fullbright.properties");
            if (Files.exists(fullbrightFile)) { try {
                Properties props = loadProps(fullbrightFile);
                fullbrightEnabled = getBool(props, "enabled", fullbrightEnabled);
                fullbrightIntensity = getFloat(props, "intensity", fullbrightIntensity);
                FullbrightModule.enabled = fullbrightEnabled;
                FullbrightModule.intensity = fullbrightIntensity;
                FullbrightModule.keyBind = getInt(props, "keyBind", FullbrightModule.keyBind);
            } catch (Exception e) { logLoadError(fullbrightFile, e); } }

            // 4. Visuals - Zoom
            Path zoomFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("zoom.properties");
            if (Files.exists(zoomFile)) { try {
                Properties props = loadProps(zoomFile);
                ZoomModule.enabled = getBool(props, "enabled", ZoomModule.enabled);
                ZoomModule.keyBind = getInt(props, "keyBind", ZoomModule.keyBind);
                ZoomModule.mode = getInt(props, "mode", ZoomModule.mode);
                ZoomModule.type = getInt(props, "type", ZoomModule.type);
                ZoomModule.scrollZoom = getBool(props, "scrollZoom", ZoomModule.scrollZoom);
                ZoomModule.defaultLevel = getInt(props, "defaultLevel", ZoomModule.defaultLevel);
            } catch (Exception e) { logLoadError(zoomFile, e); } }

            // 5. Visuals - Fake Scoreboard
            Path sbFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("fake_scoreboard.properties");
            if (Files.exists(sbFile)) { try {
                Properties props = loadProps(sbFile);
                FakeScoreboardModule.enabled = getBool(props, "enabled", FakeScoreboardModule.enabled);
                FakeScoreboardModule.keyBind = getInt(props, "keyBind", FakeScoreboardModule.keyBind);
                FakeScoreboardModule.expanded = getBool(props, "expanded", FakeScoreboardModule.expanded);
                FakeScoreboardModule.hudX = getInt(props, "hudX", FakeScoreboardModule.hudX);
                FakeScoreboardModule.hudY = getInt(props, "hudY", FakeScoreboardModule.hudY);
                FakeScoreboardModule.scale = getFloat(props, "scale", FakeScoreboardModule.scale);
                FakeScoreboardModule.bgMode = getInt(props, "bgMode", FakeScoreboardModule.bgMode);
                FakeScoreboardModule.outlineColor = getHex(props, "outlineColor", FakeScoreboardModule.outlineColor);
                FakeScoreboardModule.customWidth = getInt(props, "customWidth", FakeScoreboardModule.customWidth);
                FakeScoreboardModule.customHeight = getInt(props, "customHeight", FakeScoreboardModule.customHeight);
                FakeScoreboardModule.money = getString(props, "money", FakeScoreboardModule.money);
                FakeScoreboardModule.stars = getString(props, "stars", FakeScoreboardModule.stars);
                FakeScoreboardModule.kills = getString(props, "kills", FakeScoreboardModule.kills);
                FakeScoreboardModule.deaths = getString(props, "deaths", FakeScoreboardModule.deaths);
                FakeScoreboardModule.playtime = getString(props, "time", FakeScoreboardModule.playtime);
            } catch (Exception e) { logLoadError(sbFile, e); } }

            // Visuals - Spotify HUD
            Path spotifyFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("spotify_hud.properties");
            if (Files.exists(spotifyFile)) { try {
                Properties props = loadProps(spotifyFile);
                SpotifyHudModule.enabled = getBool(props, "enabled", SpotifyHudModule.enabled);
                SpotifyHudModule.keyBind = getInt(props, "keyBind", SpotifyHudModule.keyBind);
                SpotifyHudModule.expanded = getBool(props, "expanded", SpotifyHudModule.expanded);
                SpotifyHudModule.hudX = getInt(props, "hudX", SpotifyHudModule.hudX);
                SpotifyHudModule.hudY = getInt(props, "hudY", SpotifyHudModule.hudY);
                SpotifyHudModule.scale = getFloat(props, "scale", SpotifyHudModule.scale);
                SpotifyHudModule.bgMode = getInt(props, "bgMode", SpotifyHudModule.bgMode);
                SpotifyHudModule.outlineColor = getHex(props, "outlineColor", SpotifyHudModule.outlineColor);
                SpotifyHudModule.customWidth = getInt(props, "customWidth", SpotifyHudModule.customWidth);
                SpotifyHudModule.customHeight = getInt(props, "customHeight", SpotifyHudModule.customHeight);
                SpotifyHudModule.autoHide = getBool(props, "autoHide", SpotifyHudModule.autoHide);
                // Compact/Minimal gibt es nicht mehr: alte Werte werden auf Classic abgebildet
                SpotifyHudModule.style = SpotifyHudModule.normalizeStyle(getInt(props, "style", SpotifyHudModule.style));
                SpotifyHudModule.accentMode = getInt(props, "accentMode", SpotifyHudModule.accentMode);
                // "controlsMode" (Steuerknöpfe-Option) gibt es nicht mehr und wird ignoriert. Steht der Schlüssel noch in
                // der Datei, stammt sie aus der Zeit, in der Card seinen Hintergrund immer selbst gezeichnet hat:
                // Card + "Transparent" einmalig auf "Dark" (= Cover-Tönung) heben, damit die Karte gleich aussieht.
                if (props.containsKey("controlsMode") && SpotifyHudModule.style == SpotifyHudModule.STYLE_CARD
                        && SpotifyHudModule.bgMode == 0) {
                    SpotifyHudModule.bgMode = 2;
                }
            } catch (Exception e) { logLoadError(spotifyFile, e); } }

            // Visuals - Scoreboard
            Path scoreboardFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("scoreboard.properties");
            if (Files.exists(scoreboardFile)) { try {
                Properties props = loadProps(scoreboardFile);
                ScoreboardModule.enabled = getBool(props, "enabled", ScoreboardModule.enabled);
                ScoreboardModule.keyBind = getInt(props, "keyBind", ScoreboardModule.keyBind);
                ScoreboardModule.expanded = getBool(props, "expanded", ScoreboardModule.expanded);
                ScoreboardModule.hudX = getInt(props, "hudX", ScoreboardModule.hudX);
                ScoreboardModule.hudY = getInt(props, "hudY", ScoreboardModule.hudY);
                ScoreboardModule.scale = getFloat(props, "scale", ScoreboardModule.scale);
                ScoreboardModule.bgMode = getInt(props, "bgMode", ScoreboardModule.bgMode);
                ScoreboardModule.outlineColor = getHex(props, "outlineColor", ScoreboardModule.outlineColor);
                ScoreboardModule.customWidth = getInt(props, "customWidth", ScoreboardModule.customWidth);
                ScoreboardModule.customHeight = getInt(props, "customHeight", ScoreboardModule.customHeight);
            } catch (Exception e) { logLoadError(scoreboardFile, e); } }

            // Visuals - Custom Crosshair
            Path crosshairFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("custom_crosshair.properties");
            if (Files.exists(crosshairFile)) { try {
                Properties props = loadProps(crosshairFile);
                CustomCrosshairModule.enabled = getBool(props, "enabled", CustomCrosshairModule.enabled);
                CustomCrosshairModule.keyBind = getInt(props, "keyBind", CustomCrosshairModule.keyBind);
                CustomCrosshairModule.expanded = getBool(props, "expanded", CustomCrosshairModule.expanded);
                CustomCrosshairModule.color = getHex(props, "color", CustomCrosshairModule.color);
                if (props.containsKey("grid")) CustomCrosshairModule.setEncodedGrid(props.getProperty("grid"));
            } catch (Exception e) { logLoadError(crosshairFile, e); } }

            // Visuals - Tab Badge
            Path tabBadgeFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("tab_badge.properties");
            if (Files.exists(tabBadgeFile)) { try {
                Properties props = loadProps(tabBadgeFile);
                TabBadgeModule.enabled = getBool(props, "enabled", TabBadgeModule.enabled);
                // "badgeType" wird ignoriert: es gibt nur noch Caeser Bold (c_bold), alte Werte werden beim Speichern ersetzt
                TabBadgeModule.keyBind = getInt(props, "keyBind", TabBadgeModule.keyBind);
                TabBadgeModule.expanded = getBool(props, "expanded", TabBadgeModule.expanded);
            } catch (Exception e) { logLoadError(tabBadgeFile, e); } }


            // Combat - AutoClicker
            Path acFile = BASE_CONFIG_DIR.resolve("Combat").resolve("auto_clicker.properties");
            if (Files.exists(acFile)) { try {
                Properties props = loadProps(acFile);
                AutoClickerModule.enabled = getBool(props, "enabled", AutoClickerModule.enabled);
                AutoClickerModule.keyBind = getInt(props, "keyBind", AutoClickerModule.keyBind);
                AutoClickerModule.expanded = getBool(props, "expanded", AutoClickerModule.expanded);
                AutoClickerModule.mode = getInt(props, "mode", AutoClickerModule.mode);
                AutoClickerModule.cps = getInt(props, "cps", AutoClickerModule.cps);
                AutoClickerModule.delaySeconds = getFloat(props, "delaySeconds", AutoClickerModule.delaySeconds);
                AutoClickerModule.holdMouse = getBool(props, "holdMouse", AutoClickerModule.holdMouse);
                AutoClickerModule.onlyOnTarget = getBool(props, "onlyOnTarget", AutoClickerModule.onlyOnTarget);
                AutoClickerModule.randomJitter = getBool(props, "randomJitter", AutoClickerModule.randomJitter);
                AutoClickerModule.weaponOnly = getBool(props, "weaponOnly", AutoClickerModule.weaponOnly);
                AutoClickerModule.button = getInt(props, "button", AutoClickerModule.button);
            } catch (Exception e) { logLoadError(acFile, e); } }

            // Combat - HitColor
            Path hcFile = BASE_CONFIG_DIR.resolve("Combat").resolve("hit_color.properties");
            if (Files.exists(hcFile)) { try {
                Properties props = loadProps(hcFile);
                HitColorModule.enabled = getBool(props, "enabled", HitColorModule.enabled);
                HitColorModule.keyBind = getInt(props, "keyBind", HitColorModule.keyBind);
                HitColorModule.expanded = getBool(props, "expanded", HitColorModule.expanded);
                HitColorModule.color = getHex(props, "color", HitColorModule.color);
                HitColorModule.alpha = getFloat(props, "alpha", HitColorModule.alpha);
            } catch (Exception e) { logLoadError(hcFile, e); } }

            // Combat - ReachDisplay
            Path rdFile = BASE_CONFIG_DIR.resolve("Combat").resolve("reach_display.properties");
            if (Files.exists(rdFile)) { try {
                Properties props = loadProps(rdFile);
                ReachDisplayModule.enabled = getBool(props, "enabled", ReachDisplayModule.enabled);
                ReachDisplayModule.keyBind = getInt(props, "keyBind", ReachDisplayModule.keyBind);
                ReachDisplayModule.expanded = getBool(props, "expanded", ReachDisplayModule.expanded);
                ReachDisplayModule.mode = getInt(props, "mode", ReachDisplayModule.mode);
                ReachDisplayModule.hudX = getInt(props, "hudX", ReachDisplayModule.hudX);
                ReachDisplayModule.hudY = getInt(props, "hudY", ReachDisplayModule.hudY);
                ReachDisplayModule.scale = getFloat(props, "scale", ReachDisplayModule.scale);
                ReachDisplayModule.bgMode = getInt(props, "bgMode", ReachDisplayModule.bgMode);
                ReachDisplayModule.outlineColor = getHex(props, "outlineColor", ReachDisplayModule.outlineColor);
                ReachDisplayModule.customWidth = getInt(props, "customWidth", ReachDisplayModule.customWidth);
                ReachDisplayModule.customHeight = getInt(props, "customHeight", ReachDisplayModule.customHeight);
            } catch (Exception e) { logLoadError(rdFile, e); } }

            // Combat - LowShield
            Path lsFile = BASE_CONFIG_DIR.resolve("Combat").resolve("low_shield.properties");
            if (Files.exists(lsFile)) { try {
                Properties props = loadProps(lsFile);
                LowShieldModule.enabled = getBool(props, "enabled", LowShieldModule.enabled);
                LowShieldModule.keyBind = getInt(props, "keyBind", LowShieldModule.keyBind);
                LowShieldModule.expanded = getBool(props, "expanded", LowShieldModule.expanded);
                LowShieldModule.heightPercent = getInt(props, "heightPercent", LowShieldModule.heightPercent);
                LowShieldModule.totemSizePercent = getInt(props, "totemSizePercent", LowShieldModule.totemSizePercent);
            } catch (Exception e) { logLoadError(lsFile, e); } }

            // Combat - CustomHitboxes
            Path hbFile = BASE_CONFIG_DIR.resolve("Combat").resolve("custom_hitboxes.properties");
            if (Files.exists(hbFile)) { try {
                Properties props = loadProps(hbFile);
                CustomHitboxesModule.enabled = getBool(props, "enabled", CustomHitboxesModule.enabled);
                CustomHitboxesModule.keyBind = getInt(props, "keyBind", CustomHitboxesModule.keyBind);
                CustomHitboxesModule.expanded = getBool(props, "expanded", CustomHitboxesModule.expanded);
                CustomHitboxesModule.color = getHex(props, "color", CustomHitboxesModule.color);
                CustomHitboxesModule.fillOpacity = getFloat(props, "fillOpacity", CustomHitboxesModule.fillOpacity);
                CustomHitboxesModule.lineWidth = getFloat(props, "lineWidth", CustomHitboxesModule.lineWidth);
                CustomHitboxesModule.targetMode = getInt(props, "targetMode", CustomHitboxesModule.targetMode);
                CustomHitboxesModule.showEyeHeight = getBool(props, "showEyeHeight", CustomHitboxesModule.showEyeHeight);
                CustomHitboxesModule.showViewVector = getBool(props, "showViewVector", CustomHitboxesModule.showViewVector);
            } catch (Exception e) { logLoadError(hbFile, e); } }

            // Combat - PearlPrediction
            Path pearlFile = BASE_CONFIG_DIR.resolve("Combat").resolve("pearl_prediction.properties");
            if (Files.exists(pearlFile)) { try {
                Properties props = loadProps(pearlFile);
                PearlPredictionModule.enabled = getBool(props, "enabled", PearlPredictionModule.enabled);
                PearlPredictionModule.keyBind = getInt(props, "keyBind", PearlPredictionModule.keyBind);
                PearlPredictionModule.expanded = getBool(props, "expanded", PearlPredictionModule.expanded);
                PearlPredictionModule.enemyOnly = getBool(props, "enemyOnly", PearlPredictionModule.enemyOnly);
                PearlPredictionModule.landingBox = getBool(props, "landingBox", PearlPredictionModule.landingBox);
                PearlPredictionModule.throwPreview = getBool(props, "throwPreview", PearlPredictionModule.throwPreview);
            } catch (Exception e) { logLoadError(pearlFile, e); } }

            // Combat - AutoCart
            Path autoCartFile = BASE_CONFIG_DIR.resolve("Combat").resolve("auto_cart.properties");
            if (Files.exists(autoCartFile)) { try {
                Properties props = loadProps(autoCartFile);
                AutoCartModule.enabled = getBool(props, "enabled", AutoCartModule.enabled);
                AutoCartModule.keyBind = getInt(props, "keyBind", AutoCartModule.keyBind);
                AutoCartModule.expanded = getBool(props, "expanded", AutoCartModule.expanded);
                AutoCartModule.switchBack = getBool(props, "switchBack", AutoCartModule.switchBack);
                AutoCartModule.switchDelay = getInt(props, "switchDelay", AutoCartModule.switchDelay);
                if (props.containsKey("mode")) {
                    try {
                        AutoCartModule.mode = AutoCartModule.CartMode.valueOf(props.getProperty("mode"));
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) { logLoadError(autoCartFile, e); } }

            // Combat - AutoMace
            Path autoMaceFile = BASE_CONFIG_DIR.resolve("Combat").resolve("auto_mace.properties");
            if (Files.exists(autoMaceFile)) { try {
                Properties props = loadProps(autoMaceFile);
                AutoMaceModule.enabled = getBool(props, "enabled", AutoMaceModule.enabled);
                AutoMaceModule.keyBind = getInt(props, "keyBind", AutoMaceModule.keyBind);
                AutoMaceModule.expanded = getBool(props, "expanded", AutoMaceModule.expanded);
                AutoMaceModule.switchBack = getBool(props, "switchBack", AutoMaceModule.switchBack);
                AutoMaceModule.switchDelay = getInt(props, "switchDelay", AutoMaceModule.switchDelay);
                AutoMaceModule.cooldownCheck = getBool(props, "cooldownCheck", AutoMaceModule.cooldownCheck);
                AutoMaceModule.onlyPlayers = getBool(props, "onlyPlayers", AutoMaceModule.onlyPlayers);
                AutoMaceModule.minFallDistance = getDouble(props, "minFallDistance", AutoMaceModule.minFallDistance);
                if (props.containsKey("mode")) {
                    try {
                        AutoMaceModule.mode = AutoMaceModule.MaceMode.valueOf(props.getProperty("mode"));
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) { logLoadError(autoMaceFile, e); } }

            // Combat - DTap
            Path dTapFile = BASE_CONFIG_DIR.resolve("Combat").resolve("d_tap.properties");
            if (Files.exists(dTapFile)) { try {
                Properties props = loadProps(dTapFile);
                DTapModule.enabled = getBool(props, "enabled", DTapModule.enabled);
                DTapModule.keyBind = getInt(props, "keyBind", DTapModule.keyBind);
                DTapModule.expanded = getBool(props, "expanded", DTapModule.expanded);
                DTapModule.tapDelay = getInt(props, "tapDelay", DTapModule.tapDelay);
                DTapModule.switchBack = getBool(props, "switchBack", DTapModule.switchBack);
                if (props.containsKey("mode")) {
                    try {
                        DTapModule.mode = DTapModule.DTapMode.valueOf(props.getProperty("mode"));
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) { logLoadError(dTapFile, e); } }

            // Combat - PearlCatch
            Path pearlCatchFile = BASE_CONFIG_DIR.resolve("Combat").resolve("pearl_catch.properties");
            if (Files.exists(pearlCatchFile)) { try {
                Properties props = loadProps(pearlCatchFile);
                PearlCatchModule.enabled = getBool(props, "enabled", PearlCatchModule.enabled);
                PearlCatchModule.keyBind = getInt(props, "keyBind", PearlCatchModule.keyBind);
                PearlCatchModule.expanded = getBool(props, "expanded", PearlCatchModule.expanded);
                PearlCatchModule.timingDelay = getInt(props, "timingDelay", PearlCatchModule.timingDelay);
                PearlCatchModule.autoAim = getBool(props, "autoAim", PearlCatchModule.autoAim);
                PearlCatchModule.switchToMace = getBool(props, "switchToMace", PearlCatchModule.switchToMace);
                PearlCatchModule.switchBack = getBool(props, "switchBack", PearlCatchModule.switchBack);
            } catch (Exception e) { logLoadError(pearlCatchFile, e); } }

            // Combat - AggroPearl
            Path aggroPearlFile = BASE_CONFIG_DIR.resolve("Combat").resolve("aggro_pearl.properties");
            if (Files.exists(aggroPearlFile)) { try {
                Properties props = loadProps(aggroPearlFile);
                AggroPearlModule.enabled = getBool(props, "enabled", AggroPearlModule.enabled);
                AggroPearlModule.keyBind = getInt(props, "keyBind", AggroPearlModule.keyBind);
                AggroPearlModule.expanded = getBool(props, "expanded", AggroPearlModule.expanded);
                AggroPearlModule.throwDelay = getInt(props, "throwDelay", AggroPearlModule.throwDelay);
                AggroPearlModule.autoAim = getBool(props, "autoAim", AggroPearlModule.autoAim);
                AggroPearlModule.switchBack = getBool(props, "switchBack", AggroPearlModule.switchBack);
            } catch (Exception e) { logLoadError(aggroPearlFile, e); } }

            // Combat - Triggerbot
            Path triggerbotFile = BASE_CONFIG_DIR.resolve("Combat").resolve("triggerbot.properties");
            if (Files.exists(triggerbotFile)) { try {
                Properties props = loadProps(triggerbotFile);
                TriggerbotModule.enabled = getBool(props, "enabled", TriggerbotModule.enabled);
                TriggerbotModule.keyBind = getInt(props, "keyBind", TriggerbotModule.keyBind);
                TriggerbotModule.expanded = getBool(props, "expanded", TriggerbotModule.expanded);
                TriggerbotModule.showDetails = getBool(props, "showDetails", TriggerbotModule.showDetails);
                TriggerbotModule.mode = getInt(props, "mode", TriggerbotModule.mode);
                TriggerbotModule.cooldownThreshold = getFloat(props, "cooldownThreshold", TriggerbotModule.cooldownThreshold);
                TriggerbotModule.cps = getInt(props, "cps", TriggerbotModule.cps);
                TriggerbotModule.playersOnly = getBool(props, "playersOnly", TriggerbotModule.playersOnly);
                TriggerbotModule.weaponOnly = getBool(props, "weaponOnly", TriggerbotModule.weaponOnly);
                TriggerbotModule.holdAttackKey = getBool(props, "holdAttackKey", TriggerbotModule.holdAttackKey);
                TriggerbotModule.range = getDouble(props, "range", TriggerbotModule.range);
                TriggerbotModule.randomJitter = getBool(props, "randomJitter", TriggerbotModule.randomJitter);
            } catch (Exception e) { logLoadError(triggerbotFile, e); } }

            // Combat - ShieldBreaker
            Path shieldBreakerFile = BASE_CONFIG_DIR.resolve("Combat").resolve("shield_breaker.properties");
            if (Files.exists(shieldBreakerFile)) { try {
                Properties props = loadProps(shieldBreakerFile);
                ShieldBreakerModule.enabled = getBool(props, "enabled", ShieldBreakerModule.enabled);
                ShieldBreakerModule.keyBind = getInt(props, "keyBind", ShieldBreakerModule.keyBind);
                ShieldBreakerModule.expanded = getBool(props, "expanded", ShieldBreakerModule.expanded);
                ShieldBreakerModule.mode = getInt(props, "mode", ShieldBreakerModule.mode);
                ShieldBreakerModule.switchBack = getBool(props, "switchBack", ShieldBreakerModule.switchBack);
                ShieldBreakerModule.switchDelay = getInt(props, "switchDelay", ShieldBreakerModule.switchDelay);
                ShieldBreakerModule.reach = getDouble(props, "reach", ShieldBreakerModule.reach);
            } catch (Exception e) { logLoadError(shieldBreakerFile, e); } }

            // World - NoFog
            Path noFogFile = BASE_CONFIG_DIR.resolve("World").resolve("no_fog.properties");
            if (Files.exists(noFogFile)) { try {
                Properties props = loadProps(noFogFile);
                NoFogModule.enabled = getBool(props, "enabled", NoFogModule.enabled);
                NoFogModule.keyBind = getInt(props, "keyBind", NoFogModule.keyBind);
                NoFogModule.expanded = getBool(props, "expanded", NoFogModule.expanded);
                NoFogModule.allFog = getBool(props, "allFog", NoFogModule.allFog);
                NoFogModule.netherFog = getBool(props, "netherFog", NoFogModule.netherFog);
                NoFogModule.waterFog = getBool(props, "waterFog", NoFogModule.waterFog);
                NoFogModule.lavaFog = getBool(props, "lavaFog", NoFogModule.lavaFog);
            } catch (Exception e) { logLoadError(noFogFile, e); } }

            // Misc - AutoTool
            Path autoToolFile = BASE_CONFIG_DIR.resolve("Misc").resolve("auto_tool.properties");
            if (Files.exists(autoToolFile)) { try {
                Properties props = loadProps(autoToolFile);
                AutoToolModule.enabled = getBool(props, "enabled", AutoToolModule.enabled);
                AutoToolModule.keyBind = getInt(props, "keyBind", AutoToolModule.keyBind);
                AutoToolModule.expanded = getBool(props, "expanded", AutoToolModule.expanded);
                AutoToolModule.switchBack = getBool(props, "switchBack", AutoToolModule.switchBack);
            } catch (Exception e) { logLoadError(autoToolFile, e); } }

            // Misc - DurabilityGuard
            Path durabilityGuardFile = BASE_CONFIG_DIR.resolve("Misc").resolve("durability_guard.properties");
            if (Files.exists(durabilityGuardFile)) { try {
                Properties props = loadProps(durabilityGuardFile);
                DurabilityGuardModule.enabled = getBool(props, "enabled", DurabilityGuardModule.enabled);
                DurabilityGuardModule.keyBind = getInt(props, "keyBind", DurabilityGuardModule.keyBind);
                DurabilityGuardModule.expanded = getBool(props, "expanded", DurabilityGuardModule.expanded);
                DurabilityGuardModule.alertMode = getInt(props, "alertMode", DurabilityGuardModule.alertMode);
                if (props.containsKey("toolThresholds")) {
                    DurabilityGuardModule.toolThresholds.clear();
                    String val = props.getProperty("toolThresholds");
                    if (val != null && !val.trim().isEmpty()) {
                        for (String entry : val.split(",")) {
                            entry = entry.trim();
                            if (entry.isEmpty()) continue;
                            int sep = entry.lastIndexOf('=');
                            if (sep == -1) sep = entry.lastIndexOf(':');
                            if (sep != -1) {
                                String id = entry.substring(0, sep).trim();
                                try {
                                    int threshold = Integer.parseInt(entry.substring(sep + 1).trim());
                                    DurabilityGuardModule.toolThresholds.put(id, threshold);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            } catch (Exception e) { logLoadError(durabilityGuardFile, e); } } else {
                DurabilityGuardModule.resetToDefault();
            }

            // Visuals - BlockOutline
            Path blockOutlineFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("block_outline.properties");
            if (Files.exists(blockOutlineFile)) { try {
                Properties props = loadProps(blockOutlineFile);
                BlockOutlineModule.enabled = getBool(props, "enabled", BlockOutlineModule.enabled);
                BlockOutlineModule.keyBind = getInt(props, "keyBind", BlockOutlineModule.keyBind);
                BlockOutlineModule.expanded = getBool(props, "expanded", BlockOutlineModule.expanded);
                BlockOutlineModule.color = getHex(props, "color", BlockOutlineModule.color);
                BlockOutlineModule.lineWidth = getFloat(props, "lineWidth", BlockOutlineModule.lineWidth);
                BlockOutlineModule.chroma = getBool(props, "chroma", BlockOutlineModule.chroma);
                BlockOutlineModule.opacity = getFloat(props, "opacity", BlockOutlineModule.opacity);
            } catch (Exception e) { logLoadError(blockOutlineFile, e); } }

            // Movement - Freelook
            Path freelookFile = BASE_CONFIG_DIR.resolve("Movement").resolve("freelook.properties");
            if (Files.exists(freelookFile)) { try {
                Properties props = loadProps(freelookFile);
                FreelookModule.enabled = getBool(props, "enabled", FreelookModule.enabled);
                FreelookModule.keyBind = getInt(props, "keyBind", FreelookModule.keyBind);
                FreelookModule.expanded = getBool(props, "expanded", FreelookModule.expanded);
                FreelookModule.toggleMode = getBool(props, "toggleMode", FreelookModule.toggleMode);
                FreelookModule.invertPitch = getBool(props, "invertPitch", FreelookModule.invertPitch);
                FreelookModule.sensitivity = getFloat(props, "sensitivity", FreelookModule.sensitivity);
            } catch (Exception e) { logLoadError(freelookFile, e); } }

            // Movement - Emotes
            Path emoteFile = BASE_CONFIG_DIR.resolve("Movement").resolve("emotes.properties");
            if (Files.exists(emoteFile)) { try {
                Properties props = loadProps(emoteFile);
                com.bame.client.module.EmoteModule.enabled = getBool(props, "enabled", com.bame.client.module.EmoteModule.enabled);
                com.bame.client.module.EmoteModule.keyBind = getInt(props, "keyBind", com.bame.client.module.EmoteModule.keyBind);
                com.bame.client.module.EmoteModule.expanded = getBool(props, "expanded", com.bame.client.module.EmoteModule.expanded);
                com.bame.client.module.EmoteModule.smoothAnimations = getBool(props, "smoothAnimations", com.bame.client.module.EmoteModule.smoothAnimations);
            } catch (Exception e) { logLoadError(emoteFile, e); } }

            // Visuals - ItemSize
            Path itemSizeFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("item_size.properties");
            if (Files.exists(itemSizeFile)) { try {
                Properties props = loadProps(itemSizeFile);
                ItemSizeModule.enabled = getBool(props, "enabled", ItemSizeModule.enabled);
                ItemSizeModule.keyBind = getInt(props, "keyBind", ItemSizeModule.keyBind);
                ItemSizeModule.expanded = getBool(props, "expanded", ItemSizeModule.expanded);
                ItemSizeModule.scale = getFloat(props, "scale", ItemSizeModule.scale);
                ItemSizeModule.yOffset = getFloat(props, "yOffset", ItemSizeModule.yOffset);
                if (props.containsKey("selectedItems")) {
                    ItemSizeModule.selectedItems.clear();
                    String val = props.getProperty("selectedItems");
                    if (val != null && !val.trim().isEmpty()) {
                        for (String id : val.split(",")) {
                            if (!id.trim().isEmpty()) {
                                ItemSizeModule.selectedItems.add(id.trim());
                            }
                        }
                    }
                }
            } catch (Exception e) { logLoadError(itemSizeFile, e); } }

            // Visuals - HandPosition
            Path handPosFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("hand_position.properties");
            if (Files.exists(handPosFile)) { try {
                Properties props = loadProps(handPosFile);
                HandPositionModule.enabled = getBool(props, "enabled", HandPositionModule.enabled);
                HandPositionModule.keyBind = getInt(props, "keyBind", HandPositionModule.keyBind);
                HandPositionModule.expanded = getBool(props, "expanded", HandPositionModule.expanded);
                HandPositionModule.posX = getFloat(props, "posX", HandPositionModule.posX);
                HandPositionModule.posY = getFloat(props, "posY", HandPositionModule.posY);
                HandPositionModule.posZ = getFloat(props, "posZ", HandPositionModule.posZ);
                HandPositionModule.scale = getFloat(props, "scale", HandPositionModule.scale);
                HandPositionModule.pitch = getFloat(props, "pitch", HandPositionModule.pitch);
                HandPositionModule.yaw = getFloat(props, "yaw", HandPositionModule.yaw);
                HandPositionModule.roll = getFloat(props, "roll", HandPositionModule.roll);
                HandPositionModule.swingStyle = getInt(props, "swingStyle", HandPositionModule.swingStyle);
                HandPositionModule.applyToOffhand = getBool(props, "applyToOffhand", HandPositionModule.applyToOffhand);
                HandPositionModule.weaponsOnly = getBool(props, "weaponsOnly", HandPositionModule.weaponsOnly);
                HandPositionModule.miningAnimation = getBool(props, "miningAnimation", HandPositionModule.miningAnimation);
            } catch (Exception e) { logLoadError(handPosFile, e); } }

            // World - TimeChanger
            Path timeChangerFile = BASE_CONFIG_DIR.resolve("World").resolve("time_changer.properties");
            if (Files.exists(timeChangerFile)) { try {
                Properties props = loadProps(timeChangerFile);
                TimeChangerModule.enabled = getBool(props, "enabled", TimeChangerModule.enabled);
                TimeChangerModule.expanded = getBool(props, "expanded", TimeChangerModule.expanded);
                TimeChangerModule.keyBind = getInt(props, "keyBind", TimeChangerModule.keyBind);
                TimeChangerModule.timeMode = getInt(props, "timeMode", TimeChangerModule.timeMode);
            } catch (Exception e) { logLoadError(timeChangerFile, e); } }

            // Visuals - SkinProtect
            Path skinProtectFile = BASE_CONFIG_DIR.resolve("Visuals").resolve("skin_protect.properties");
            if (Files.exists(skinProtectFile)) { try {
                Properties props = loadProps(skinProtectFile);
                SkinProtectModule.enabled = getBool(props, "enabled", SkinProtectModule.enabled);
                SkinProtectModule.expanded = getBool(props, "expanded", SkinProtectModule.expanded);
                SkinProtectModule.keyBind = getInt(props, "keyBind", SkinProtectModule.keyBind);
                SkinProtectModule.currentSkinIndex = getInt(props, "skinIndex", SkinProtectModule.currentSkinIndex);
                SkinProtectModule.targetInitialSkinName = getString(props, "currentSkinName", SkinProtectModule.targetInitialSkinName);
                if (props.containsKey("customSkins")) {
                    String val = props.getProperty("customSkins");
                    if (!val.trim().isEmpty()) {
                        for (String part : val.split(";")) {
                            String[] s = part.split(":");
                            if (s.length == 2) {
                                try {
                                    SkinProtectModule.addCustomSkin(s[0], UUID.fromString(s[1]));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            } catch (Exception e) { logLoadError(skinProtectFile, e); } }

            // 6. Theme
            Path themeFile = BASE_CONFIG_DIR.resolve("Theme").resolve("theme.properties");
            if (Files.exists(themeFile)) { try {
                Properties props = loadProps(themeFile);
                theme = getString(props, "theme", theme);
                seeThrough = getBool(props, "seeThrough", seeThrough);
                frostedBlur = getBool(props, "frostedBlur", frostedBlur);
                ambientBackground = getBool(props, "ambientBackground", ambientBackground);
                customMainColor = getBool(props, "customMainColor", customMainColor);
                classicToggle = getBool(props, "classicToggle", classicToggle);
                mainColor = getInt(props, "mainColor", mainColor);
                ambientPrimary = getInt(props, "ambientPrimary", ambientPrimary);
                ambientSecondary = getInt(props, "ambientSecondary", ambientSecondary);
                ambientMode = getInt(props, "ambientMode", ambientMode);
                ambientIntensity = Math.clamp(getFloat(props, "ambientIntensity", ambientIntensity), 0f, 1f);
                ambientOpacity = Math.clamp(getFloat(props, "ambientOpacity", ambientOpacity), 0f, 1f);
                ambientRadius = Math.clamp(getFloat(props, "ambientRadius", ambientRadius), 0f, 1f);
                ambientAnimation = getBool(props, "ambientAnimation", ambientAnimation);
                animationSpeed = Math.clamp(getFloat(props, "animationSpeed", animationSpeed), 0f, 1f);
                customWallpaper = getBool(props, "customWallpaper", customWallpaper);
                selectedWallpaper = getString(props, "selectedWallpaper", selectedWallpaper);
                wallpaperBrightness = Math.clamp(getFloat(props, "wallpaperBrightness", wallpaperBrightness), 0.1f, 1f);
                wallpaperBlur = Math.clamp(getFloat(props, "wallpaperBlur", wallpaperBlur), 0f, 1f);
            } catch (Exception e) { logLoadError(themeFile, e); } }

            // 7. Settings
            Path settingsFile = BASE_CONFIG_DIR.resolve("Settings").resolve("settings.properties");
            if (Files.exists(settingsFile)) { try {
                Properties props = loadProps(settingsFile);
                menuBind = getInt(props, "menuBind", menuBind);
                quickFriend = getBool(props, "quickFriend", quickFriend);
                guiAnimation = getInt(props, "guiAnimation", guiAnimation);
                selectedCategory = sanitizeCategory(getString(props, "selectedCategory", selectedCategory));
                moduleSound = getBool(props, "moduleSound", moduleSound);
                soundPack = getInt(props, "soundPack", soundPack);
                soundVolume = getFloat(props, "soundVolume", soundVolume);
                hoverSound = getBool(props, "hoverSound", hoverSound);
                hoverStyle = getInt(props, "hoverStyle", hoverStyle);
                hoverVolume = getFloat(props, "hoverVolume", hoverVolume);
                notifications = getBool(props, "notifications", notifications);
                threeDGui = getBool(props, "threeDGui", threeDGui);
                secretRequireShift = getBool(props, "secretRequireShift", secretRequireShift);
                secretRequireCtrl = getBool(props, "secretRequireCtrl", secretRequireCtrl);
                secretRequireAlt = getBool(props, "secretRequireAlt", secretRequireAlt);
                secretKey = getInt(props, "secretKey", secretKey);
                if (migrateSecretCombo()) markDirty();
            } catch (Exception e) { logLoadError(settingsFile, e); } }
        } catch (Exception e) {
            BameClient.LOGGER.error("Caeser Client config could not be loaded completely", e);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Sicheres Parsen: ein kaputter Wert betrifft nur diesen Schlüssel, alle anderen werden normal geladen
    // ------------------------------------------------------------------------------------------------

    private static void logLoadError(Path file, Exception e) {
        BameClient.LOGGER.warn("Caeser Client: could not read {} completely, keeping defaults for the rest of this file", file.getFileName(), e);
    }

    private static void logBadValue(Properties props, String key, Object fallback) {
        BameClient.LOGGER.warn("Caeser Client: invalid value '{}' for key '{}', using {}", props.getProperty(key), key, fallback);
    }

    static int getInt(Properties props, String key, int fallback) {
        String v = props.getProperty(key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            logBadValue(props, key, fallback);
            return fallback;
        }
    }

    static boolean getBool(Properties props, String key, boolean fallback) {
        String v = props.getProperty(key);
        if (v == null) return fallback;
        String t = v.trim();
        if (t.equalsIgnoreCase("true")) return true;
        if (t.equalsIgnoreCase("false")) return false;
        logBadValue(props, key, fallback);
        return fallback;
    }

    static float getFloat(Properties props, String key, float fallback) {
        String v = props.getProperty(key);
        if (v == null) return fallback;
        try {
            float f = Float.parseFloat(v.trim());
            if (Float.isFinite(f)) return f;
        } catch (NumberFormatException ignored) {}
        logBadValue(props, key, fallback);
        return fallback;
    }

    static double getDouble(Properties props, String key, double fallback) {
        String v = props.getProperty(key);
        if (v == null) return fallback;
        try {
            double d = Double.parseDouble(v.trim());
            if (Double.isFinite(d)) return d;
        } catch (NumberFormatException ignored) {}
        logBadValue(props, key, fallback);
        return fallback;
    }

    /** Farbwerte werden als Hex (ohne 0x) gespeichert, z. B. "ff00ffff". */
    static int getHex(Properties props, String key, int fallback) {
        String v = props.getProperty(key);
        if (v == null) return fallback;
        try {
            String t = v.trim();
            if (t.startsWith("#")) t = t.substring(1);
            if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
            return (int) Long.parseLong(t, 16);
        } catch (NumberFormatException e) {
            logBadValue(props, key, Integer.toHexString(fallback));
            return fallback;
        }
    }

    static String getString(Properties props, String key, String fallback) {
        String v = props.getProperty(key);
        return v != null ? v : fallback;
    }

    /** Nur gültige Kategorien des Mod-Menüs zulassen (sonst Combat). */
    public static String sanitizeCategory(String category) {
        if (category != null) {
            for (String c : com.bame.client.gui.BameClientScreen.CATEGORIES) {
                if (c.equalsIgnoreCase(category.trim())) return c;
            }
        }
        return com.bame.client.gui.BameClientScreen.CATEGORIES[0];
    }

    // ------------------------------------------------------------------------------------------------
    // Geheime Tastenkombination
    // ------------------------------------------------------------------------------------------------

    /**
     * Alte Standard-Kombination Strg+C kollidierte mit Sprinten (Strg) + Zoom (C) und mit Kopieren in Textfeldern.
     * Gespeicherte Strg+C-Werte werden auf die neue Standard-Kombination Strg+Umschalt+K migriert.
     * @return true, wenn migriert wurde
     */
    static boolean migrateSecretCombo() {
        if (secretKey == org.lwjgl.glfw.GLFW.GLFW_KEY_C && secretRequireCtrl && !secretRequireShift && !secretRequireAlt) {
            secretKey = DEFAULT_SECRET_KEY;
            secretRequireCtrl = DEFAULT_SECRET_CTRL;
            secretRequireShift = DEFAULT_SECRET_SHIFT;
            secretRequireAlt = DEFAULT_SECRET_ALT;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------
    // Versteckte Kampf-Module (nur im Geheim-Menü sichtbar): starten immer deaktiviert
    // ------------------------------------------------------------------------------------------------

    /** Schaltet alle nur im Geheim-Menü sichtbaren Kampf-Module aus. Einstellungen (Delay, Modus, Taste) bleiben. */
    public static void disableHiddenCombatModules() {
        TriggerbotModule.enabled = false;
        AutoMaceModule.enabled = false;
        DTapModule.enabled = false;
        AggroPearlModule.enabled = false;
        PearlCatchModule.enabled = false;
        ShieldBreakerModule.enabled = false;
        AutoCartModule.enabled = false;
        PearlPredictionModule.enabled = false;
    }

    /**
     * Darf die Taste eines versteckten Kampf-Moduls etwas auslösen? Nur wenn das Geheim-Menü entsperrt ist
     * (oder das Modul schon an ist, damit man es per Taste wieder ausschalten kann).
     */
    public static boolean hiddenModuleHotkeyAllowed(boolean moduleEnabled) {
        return secretUnlocked || moduleEnabled;
    }

    // ------------------------------------------------------------------------------------------------
    // Speichern: gebündelt, im Hintergrund, atomar
    // ------------------------------------------------------------------------------------------------

    /** Wartezeit nach der letzten Änderung, bevor gespeichert wird. */
    private static final long SAVE_DELAY_MS = 1000L;
    private static volatile boolean dirty = false;
    private static volatile long lastChangeMs = 0L;
    /** Zähler pro Snapshot: ein älterer Snapshot überschreibt nie einen neueren. */
    private static final java.util.concurrent.atomic.AtomicLong snapshotGeneration = new java.util.concurrent.atomic.AtomicLong();
    private static long writtenGeneration = 0L; // nur unter WRITE_LOCK
    private static final Object WRITE_LOCK = new Object();
    /** Zuletzt geschriebener Inhalt pro Datei (ohne Datumszeile): unveränderte Dateien werden nicht neu geschrieben. */
    private static final Map<Path, String> lastWritten = new HashMap<>(); // nur unter WRITE_LOCK
    private static final java.util.concurrent.ExecutorService SAVE_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "CaeserClient-ConfigSaver");
                t.setDaemon(true);
                return t;
            });
    private static boolean shutdownHookInstalled = false;

    private record PendingFile(Path path, Properties props, String comment) {}

    /**
     * Änderung merken. Gespeichert wird gebündelt ~1 s nach der letzten Änderung (siehe {@link #tick()}),
     * beim Schließen des Menüs ({@link #flush()}) und beim Beenden des Spiels ({@link #saveNow()}).
     */
    public static void save() {
        markDirty();
    }

    public static void markDirty() {
        lastChangeMs = System.currentTimeMillis();
        dirty = true;
        installShutdownHook();
    }

    public static boolean isDirty() {
        return dirty;
    }

    /** Pro Client-Tick aufrufen (Haupt-Thread): speichert gedrosselt nach der letzten Änderung. */
    public static void tick() {
        if (dirty && System.currentTimeMillis() - lastChangeMs >= SAVE_DELAY_MS) {
            flush();
        }
    }

    /** Ausstehende Änderungen jetzt im Hintergrund schreiben (Snapshot auf dem aufrufenden Thread). */
    public static void flush() {
        if (!dirty) return;
        dirty = false;
        final List<PendingFile> snapshot;
        final long gen;
        try {
            snapshot = buildSnapshot();
            gen = snapshotGeneration.incrementAndGet();
        } catch (Exception e) {
            BameClient.LOGGER.error("Caeser Client: could not collect config for saving", e);
            return;
        }
        try {
            SAVE_EXECUTOR.execute(() -> writeSnapshot(snapshot, gen));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            writeSnapshot(snapshot, gen);
        }
    }

    /** Synchron speichern (Spielende, erster Start, Migration). */
    public static void saveNow() {
        dirty = false;
        try {
            List<PendingFile> snapshot = buildSnapshot();
            writeSnapshot(snapshot, snapshotGeneration.incrementAndGet());
        } catch (Exception e) {
            BameClient.LOGGER.error("Caeser Client: could not save config", e);
        }
    }

    private static synchronized void installShutdownHook() {
        if (shutdownHookInstalled) return;
        shutdownHookInstalled = true;
        try {
            // Rückfallebene, falls das Spiel ohne CLIENT_STOPPING beendet wird
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (dirty) saveNow();
            }, "CaeserClient-ConfigShutdownSave"));
        } catch (Exception ignored) {}
    }

    private static void writeSnapshot(List<PendingFile> files, long gen) {
        synchronized (WRITE_LOCK) {
            if (gen < writtenGeneration) return; // ein neuerer Stand wurde schon geschrieben
            writtenGeneration = gen;
            for (PendingFile f : files) {
                try {
                    writePropsAtomic(f.path(), f.props(), f.comment());
                } catch (Exception e) {
                    BameClient.LOGGER.error("Caeser Client: could not write {}", f.path().getFileName(), e);
                }
            }
        }
    }

    private static void writePropsAtomic(Path path, Properties props, String comment) throws IOException {
        // store(OutputStream) schreibt ISO-8859-1 mit Unicode-Escapes, passend zu load(InputStream)
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        props.store(bos, comment);
        byte[] bytes = bos.toByteArray();
        String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        // Vergleich ohne Kommentar-/Datumszeilen
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (!line.startsWith("#")) body.append(line).append('\n');
        }
        String key = body.toString();
        if (key.equals(lastWritten.get(path)) && Files.exists(path)) return;

        Path dir = path.getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmp = (dir != null ? dir : Path.of(".")).resolve(path.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        lastWritten.put(path, key);
    }

    private static void queue(List<PendingFile> out, Path path, Properties props, String comment) {
        out.add(new PendingFile(path, props, comment));
    }

    /** Erfasst alle Werte als Snapshot (Haupt-Thread); das Schreiben passiert danach getrennt. */
    private static List<PendingFile> buildSnapshot() {
        List<PendingFile> out = new ArrayList<>();
        {
            Path combatDir = BASE_CONFIG_DIR.resolve("Combat");
            Path movementDir = BASE_CONFIG_DIR.resolve("Movement");
            Path visualsDir = BASE_CONFIG_DIR.resolve("Visuals");
            Path miscDir = BASE_CONFIG_DIR.resolve("Misc");
            Path worldDir = BASE_CONFIG_DIR.resolve("World");
            Path themeDir = BASE_CONFIG_DIR.resolve("Theme");
            Path settingsDir = BASE_CONFIG_DIR.resolve("Settings");
            Path wallpapersDir = BASE_CONFIG_DIR.resolve("wallpapers");


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
            queue(out, worldDir.resolve("auto_area_miner.properties"), worldProps, "Caeser Client - Auto Area Miner");

            // Movement - InvMove
            Properties invMoveProps = new Properties();
            invMoveProps.setProperty("enabled", String.valueOf(InvMoveModule.enabled));
            invMoveProps.setProperty("keyBind", String.valueOf(InvMoveModule.keyBind));
            invMoveProps.setProperty("expanded", String.valueOf(InvMoveModule.expanded));
            invMoveProps.setProperty("jump", String.valueOf(InvMoveModule.jump));
            invMoveProps.setProperty("sprint", String.valueOf(InvMoveModule.sprint));
            invMoveProps.setProperty("sneak", String.valueOf(InvMoveModule.sneak));
            invMoveProps.setProperty("rotateWithArrows", String.valueOf(InvMoveModule.rotateWithArrows));
            queue(out, movementDir.resolve("inv_move.properties"), invMoveProps, "Caeser Client - InvMove");

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
            showHudProps.setProperty("coordinatesExpanded", String.valueOf(CoordinatesModule.expanded));
            showHudProps.setProperty("coordinatesLayoutMode", String.valueOf(CoordinatesModule.layoutMode));
            showHudProps.setProperty("coordinatesStyle", String.valueOf(CoordinatesModule.style));
            showHudProps.setProperty("coordinatesShowX", String.valueOf(CoordinatesModule.showX));
            showHudProps.setProperty("coordinatesShowY", String.valueOf(CoordinatesModule.showY));
            showHudProps.setProperty("coordinatesShowZ", String.valueOf(CoordinatesModule.showZ));
            showHudProps.setProperty("coordinatesDecimals", String.valueOf(CoordinatesModule.decimals));
            showHudProps.setProperty("coordinatesShowNether", String.valueOf(CoordinatesModule.showNether));
            showHudProps.setProperty("coordinatesShowDirection", String.valueOf(CoordinatesModule.showDirection));

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
            showHudProps.setProperty("nameProtectExpanded", String.valueOf(NameProtectModule.expanded));
            if (NameProtectModule.alias != null) {
                showHudProps.setProperty("nameProtectAlias", NameProtectModule.alias);
            }
            queue(out, visualsDir.resolve("show_hud.properties"), showHudProps, "Caeser Client - Show HUD");

            // 3. Visuals - Fullbright
            Properties fullbrightProps = new Properties();
            fullbrightProps.setProperty("enabled", String.valueOf(FullbrightModule.enabled));
            fullbrightProps.setProperty("intensity", String.valueOf(FullbrightModule.intensity));
            fullbrightProps.setProperty("keyBind", String.valueOf(FullbrightModule.keyBind));
            queue(out, visualsDir.resolve("fullbright.properties"), fullbrightProps, "Caeser Client - Fullbright");

            // 4. Visuals - Zoom
            Properties zoomProps = new Properties();
            zoomProps.setProperty("enabled", String.valueOf(ZoomModule.enabled));
            zoomProps.setProperty("keyBind", String.valueOf(ZoomModule.keyBind));
            zoomProps.setProperty("mode", String.valueOf(ZoomModule.mode));
            zoomProps.setProperty("type", String.valueOf(ZoomModule.type));
            zoomProps.setProperty("scrollZoom", String.valueOf(ZoomModule.scrollZoom));
            zoomProps.setProperty("defaultLevel", String.valueOf(ZoomModule.defaultLevel));
            queue(out, visualsDir.resolve("zoom.properties"), zoomProps, "Caeser Client - Zoom");

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
            queue(out, visualsDir.resolve("fake_scoreboard.properties"), sbProps, "Caeser Client - Fake Scoreboard");

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
            spotifyProps.setProperty("style", String.valueOf(SpotifyHudModule.style));
            spotifyProps.setProperty("accentMode", String.valueOf(SpotifyHudModule.accentMode));
            queue(out, visualsDir.resolve("spotify_hud.properties"), spotifyProps, "Caeser Client - Spotify HUD");

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
            queue(out, visualsDir.resolve("scoreboard.properties"), realScoreboardProps, "Caeser Client - Scoreboard");

            // Visuals - Custom Crosshair
            Properties crosshairProps = new Properties();
            crosshairProps.setProperty("enabled", String.valueOf(CustomCrosshairModule.enabled));
            crosshairProps.setProperty("keyBind", String.valueOf(CustomCrosshairModule.keyBind));
            crosshairProps.setProperty("expanded", String.valueOf(CustomCrosshairModule.expanded));
            crosshairProps.setProperty("color", Integer.toHexString(CustomCrosshairModule.color));
            crosshairProps.setProperty("grid", CustomCrosshairModule.getEncodedGrid());
            queue(out, visualsDir.resolve("custom_crosshair.properties"), crosshairProps, "Caeser Client - Custom Crosshair");

            // Visuals - Tab Badge
            Properties tabBadgeProps = new Properties();
            tabBadgeProps.setProperty("enabled", String.valueOf(TabBadgeModule.enabled));
            tabBadgeProps.setProperty("badgeType", TabBadgeModule.BADGE_TYPE);
            tabBadgeProps.setProperty("keyBind", String.valueOf(TabBadgeModule.keyBind));
            tabBadgeProps.setProperty("expanded", String.valueOf(TabBadgeModule.expanded));
            queue(out, visualsDir.resolve("tab_badge.properties"), tabBadgeProps, "Caeser Client - Tab Badge");


            // Combat - AutoClicker
            Properties acProps = new Properties();
            acProps.setProperty("enabled", String.valueOf(AutoClickerModule.enabled));
            acProps.setProperty("keyBind", String.valueOf(AutoClickerModule.keyBind));
            acProps.setProperty("expanded", String.valueOf(AutoClickerModule.expanded));
            acProps.setProperty("mode", String.valueOf(AutoClickerModule.mode));
            acProps.setProperty("cps", String.valueOf(AutoClickerModule.cps));
            acProps.setProperty("delaySeconds", String.valueOf(AutoClickerModule.delaySeconds));
            acProps.setProperty("holdMouse", String.valueOf(AutoClickerModule.holdMouse));
            acProps.setProperty("onlyOnTarget", String.valueOf(AutoClickerModule.onlyOnTarget));
            acProps.setProperty("randomJitter", String.valueOf(AutoClickerModule.randomJitter));
            acProps.setProperty("weaponOnly", String.valueOf(AutoClickerModule.weaponOnly));
            acProps.setProperty("button", String.valueOf(AutoClickerModule.button));
            queue(out, combatDir.resolve("auto_clicker.properties"), acProps, "Caeser Client - AutoClicker");

            // Combat - HitColor
            Properties hcProps = new Properties();
            hcProps.setProperty("enabled", String.valueOf(HitColorModule.enabled));
            hcProps.setProperty("keyBind", String.valueOf(HitColorModule.keyBind));
            hcProps.setProperty("expanded", String.valueOf(HitColorModule.expanded));
            hcProps.setProperty("color", Integer.toHexString(HitColorModule.color));
            hcProps.setProperty("alpha", String.valueOf(HitColorModule.alpha));
            queue(out, combatDir.resolve("hit_color.properties"), hcProps, "Caeser Client - HitColor");

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
            queue(out, combatDir.resolve("reach_display.properties"), rdProps, "Caeser Client - ReachDisplay");

            // Combat - LowShield
            Properties lsProps = new Properties();
            lsProps.setProperty("enabled", String.valueOf(LowShieldModule.enabled));
            lsProps.setProperty("keyBind", String.valueOf(LowShieldModule.keyBind));
            lsProps.setProperty("expanded", String.valueOf(LowShieldModule.expanded));
            lsProps.setProperty("heightPercent", String.valueOf(LowShieldModule.heightPercent));
            lsProps.setProperty("totemSizePercent", String.valueOf(LowShieldModule.totemSizePercent));
            queue(out, combatDir.resolve("low_shield.properties"), lsProps, "Caeser Client - LowShield");

            // Combat - CustomHitboxes
            Properties hbProps = new Properties();
            hbProps.setProperty("enabled", String.valueOf(CustomHitboxesModule.enabled));
            hbProps.setProperty("keyBind", String.valueOf(CustomHitboxesModule.keyBind));
            hbProps.setProperty("expanded", String.valueOf(CustomHitboxesModule.expanded));
            hbProps.setProperty("color", Integer.toHexString(CustomHitboxesModule.color));
            hbProps.setProperty("fillOpacity", String.valueOf(CustomHitboxesModule.fillOpacity));
            hbProps.setProperty("lineWidth", String.valueOf(CustomHitboxesModule.lineWidth));
            hbProps.setProperty("targetMode", String.valueOf(CustomHitboxesModule.targetMode));
            hbProps.setProperty("showEyeHeight", String.valueOf(CustomHitboxesModule.showEyeHeight));
            hbProps.setProperty("showViewVector", String.valueOf(CustomHitboxesModule.showViewVector));
            queue(out, combatDir.resolve("custom_hitboxes.properties"), hbProps, "Caeser Client - Custom Hitboxes");

            // Combat - PearlPrediction
            Properties pearlProps = new Properties();
            pearlProps.setProperty("enabled", String.valueOf(PearlPredictionModule.enabled));
            pearlProps.setProperty("keyBind", String.valueOf(PearlPredictionModule.keyBind));
            pearlProps.setProperty("expanded", String.valueOf(PearlPredictionModule.expanded));
            pearlProps.setProperty("enemyOnly", String.valueOf(PearlPredictionModule.enemyOnly));
            pearlProps.setProperty("landingBox", String.valueOf(PearlPredictionModule.landingBox));
            pearlProps.setProperty("throwPreview", String.valueOf(PearlPredictionModule.throwPreview));
            queue(out, combatDir.resolve("pearl_prediction.properties"), pearlProps, "Caeser Client - Pearl Prediction");

            // Combat - AutoCart
            Properties autoCartProps = new Properties();
            autoCartProps.setProperty("enabled", String.valueOf(AutoCartModule.enabled));
            autoCartProps.setProperty("keyBind", String.valueOf(AutoCartModule.keyBind));
            autoCartProps.setProperty("expanded", String.valueOf(AutoCartModule.expanded));
            autoCartProps.setProperty("switchBack", String.valueOf(AutoCartModule.switchBack));
            autoCartProps.setProperty("switchDelay", String.valueOf(AutoCartModule.switchDelay));
            autoCartProps.setProperty("mode", AutoCartModule.mode.name());
            queue(out, combatDir.resolve("auto_cart.properties"), autoCartProps, "Caeser Client - AutoCart");

            // Combat - AutoMace
            Properties autoMaceProps = new Properties();
            autoMaceProps.setProperty("enabled", String.valueOf(AutoMaceModule.enabled));
            autoMaceProps.setProperty("keyBind", String.valueOf(AutoMaceModule.keyBind));
            autoMaceProps.setProperty("expanded", String.valueOf(AutoMaceModule.expanded));
            autoMaceProps.setProperty("switchBack", String.valueOf(AutoMaceModule.switchBack));
            autoMaceProps.setProperty("switchDelay", String.valueOf(AutoMaceModule.switchDelay));
            autoMaceProps.setProperty("cooldownCheck", String.valueOf(AutoMaceModule.cooldownCheck));
            autoMaceProps.setProperty("onlyPlayers", String.valueOf(AutoMaceModule.onlyPlayers));
            autoMaceProps.setProperty("minFallDistance", String.valueOf(AutoMaceModule.minFallDistance));
            autoMaceProps.setProperty("mode", AutoMaceModule.mode.name());
            queue(out, combatDir.resolve("auto_mace.properties"), autoMaceProps, "Caeser Client - AutoMace");

            // Combat - DTap
            Properties dTapProps = new Properties();
            dTapProps.setProperty("enabled", String.valueOf(DTapModule.enabled));
            dTapProps.setProperty("keyBind", String.valueOf(DTapModule.keyBind));
            dTapProps.setProperty("expanded", String.valueOf(DTapModule.expanded));
            dTapProps.setProperty("tapDelay", String.valueOf(DTapModule.tapDelay));
            dTapProps.setProperty("switchBack", String.valueOf(DTapModule.switchBack));
            dTapProps.setProperty("mode", DTapModule.mode.name());
            queue(out, combatDir.resolve("d_tap.properties"), dTapProps, "Caeser Client - DTap");

            // Combat - PearlCatch
            Properties pearlCatchProps = new Properties();
            pearlCatchProps.setProperty("enabled", String.valueOf(PearlCatchModule.enabled));
            pearlCatchProps.setProperty("keyBind", String.valueOf(PearlCatchModule.keyBind));
            pearlCatchProps.setProperty("expanded", String.valueOf(PearlCatchModule.expanded));
            pearlCatchProps.setProperty("timingDelay", String.valueOf(PearlCatchModule.timingDelay));
            pearlCatchProps.setProperty("autoAim", String.valueOf(PearlCatchModule.autoAim));
            pearlCatchProps.setProperty("switchToMace", String.valueOf(PearlCatchModule.switchToMace));
            pearlCatchProps.setProperty("switchBack", String.valueOf(PearlCatchModule.switchBack));
            queue(out, combatDir.resolve("pearl_catch.properties"), pearlCatchProps, "Caeser Client - PearlCatch");

            // Combat - AggroPearl
            Properties aggroPearlProps = new Properties();
            aggroPearlProps.setProperty("enabled", String.valueOf(AggroPearlModule.enabled));
            aggroPearlProps.setProperty("keyBind", String.valueOf(AggroPearlModule.keyBind));
            aggroPearlProps.setProperty("expanded", String.valueOf(AggroPearlModule.expanded));
            aggroPearlProps.setProperty("throwDelay", String.valueOf(AggroPearlModule.throwDelay));
            aggroPearlProps.setProperty("autoAim", String.valueOf(AggroPearlModule.autoAim));
            aggroPearlProps.setProperty("switchBack", String.valueOf(AggroPearlModule.switchBack));
            queue(out, combatDir.resolve("aggro_pearl.properties"), aggroPearlProps, "Caeser Client - AggroPearl");

            // Combat - Triggerbot
            Properties triggerbotProps = new Properties();
            triggerbotProps.setProperty("enabled", String.valueOf(TriggerbotModule.enabled));
            triggerbotProps.setProperty("keyBind", String.valueOf(TriggerbotModule.keyBind));
            triggerbotProps.setProperty("expanded", String.valueOf(TriggerbotModule.expanded));
            triggerbotProps.setProperty("showDetails", String.valueOf(TriggerbotModule.showDetails));
            triggerbotProps.setProperty("mode", String.valueOf(TriggerbotModule.mode));
            triggerbotProps.setProperty("cooldownThreshold", String.valueOf(TriggerbotModule.cooldownThreshold));
            triggerbotProps.setProperty("cps", String.valueOf(TriggerbotModule.cps));
            triggerbotProps.setProperty("playersOnly", String.valueOf(TriggerbotModule.playersOnly));
            triggerbotProps.setProperty("weaponOnly", String.valueOf(TriggerbotModule.weaponOnly));
            triggerbotProps.setProperty("holdAttackKey", String.valueOf(TriggerbotModule.holdAttackKey));
            triggerbotProps.setProperty("range", String.valueOf(TriggerbotModule.range));
            triggerbotProps.setProperty("randomJitter", String.valueOf(TriggerbotModule.randomJitter));
            queue(out, combatDir.resolve("triggerbot.properties"), triggerbotProps, "Caeser Client - Triggerbot");

            // Combat - ShieldBreaker
            Properties shieldBreakerProps = new Properties();
            shieldBreakerProps.setProperty("enabled", String.valueOf(ShieldBreakerModule.enabled));
            shieldBreakerProps.setProperty("keyBind", String.valueOf(ShieldBreakerModule.keyBind));
            shieldBreakerProps.setProperty("expanded", String.valueOf(ShieldBreakerModule.expanded));
            shieldBreakerProps.setProperty("mode", String.valueOf(ShieldBreakerModule.mode));
            shieldBreakerProps.setProperty("switchBack", String.valueOf(ShieldBreakerModule.switchBack));
            shieldBreakerProps.setProperty("switchDelay", String.valueOf(ShieldBreakerModule.switchDelay));
            shieldBreakerProps.setProperty("reach", String.valueOf(ShieldBreakerModule.reach));
            queue(out, combatDir.resolve("shield_breaker.properties"), shieldBreakerProps, "Caeser Client - ShieldBreaker");

            // World - NoFog
            Properties noFogProps = new Properties();
            noFogProps.setProperty("enabled", String.valueOf(NoFogModule.enabled));
            noFogProps.setProperty("keyBind", String.valueOf(NoFogModule.keyBind));
            noFogProps.setProperty("expanded", String.valueOf(NoFogModule.expanded));
            noFogProps.setProperty("allFog", String.valueOf(NoFogModule.allFog));
            noFogProps.setProperty("netherFog", String.valueOf(NoFogModule.netherFog));
            noFogProps.setProperty("waterFog", String.valueOf(NoFogModule.waterFog));
            noFogProps.setProperty("lavaFog", String.valueOf(NoFogModule.lavaFog));
            queue(out, worldDir.resolve("no_fog.properties"), noFogProps, "Caeser Client - NoFog");

            // Misc - AutoTool
            Properties autoToolProps = new Properties();
            autoToolProps.setProperty("enabled", String.valueOf(AutoToolModule.enabled));
            autoToolProps.setProperty("keyBind", String.valueOf(AutoToolModule.keyBind));
            autoToolProps.setProperty("expanded", String.valueOf(AutoToolModule.expanded));
            autoToolProps.setProperty("switchBack", String.valueOf(AutoToolModule.switchBack));
            queue(out, miscDir.resolve("auto_tool.properties"), autoToolProps, "Caeser Client - AutoTool");

            // Misc - DurabilityGuard
            Properties durabilityGuardProps = new Properties();
            durabilityGuardProps.setProperty("enabled", String.valueOf(DurabilityGuardModule.enabled));
            durabilityGuardProps.setProperty("keyBind", String.valueOf(DurabilityGuardModule.keyBind));
            durabilityGuardProps.setProperty("expanded", String.valueOf(DurabilityGuardModule.expanded));
            durabilityGuardProps.setProperty("alertMode", String.valueOf(DurabilityGuardModule.alertMode));
            StringBuilder dguardSb = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> e : DurabilityGuardModule.toolThresholds.entrySet()) {
                if (dguardSb.length() > 0) dguardSb.append(",");
                dguardSb.append(e.getKey()).append("=").append(e.getValue());
            }
            durabilityGuardProps.setProperty("toolThresholds", dguardSb.toString());
            queue(out, miscDir.resolve("durability_guard.properties"), durabilityGuardProps, "Caeser Client - DurabilityGuard");

            // Visuals - BlockOutline
            Properties blockOutlineProps = new Properties();
            blockOutlineProps.setProperty("enabled", String.valueOf(BlockOutlineModule.enabled));
            blockOutlineProps.setProperty("keyBind", String.valueOf(BlockOutlineModule.keyBind));
            blockOutlineProps.setProperty("expanded", String.valueOf(BlockOutlineModule.expanded));
            blockOutlineProps.setProperty("color", Integer.toHexString(BlockOutlineModule.color));
            blockOutlineProps.setProperty("lineWidth", String.valueOf(BlockOutlineModule.lineWidth));
            blockOutlineProps.setProperty("chroma", String.valueOf(BlockOutlineModule.chroma));
            blockOutlineProps.setProperty("opacity", String.valueOf(BlockOutlineModule.opacity));
            queue(out, visualsDir.resolve("block_outline.properties"), blockOutlineProps, "Caeser Client - BlockOutline");

            // Movement - Freelook
            Properties freelookProps = new Properties();
            freelookProps.setProperty("enabled", String.valueOf(FreelookModule.enabled));
            freelookProps.setProperty("keyBind", String.valueOf(FreelookModule.keyBind));
            freelookProps.setProperty("expanded", String.valueOf(FreelookModule.expanded));
            freelookProps.setProperty("toggleMode", String.valueOf(FreelookModule.toggleMode));
            freelookProps.setProperty("invertPitch", String.valueOf(FreelookModule.invertPitch));
            freelookProps.setProperty("sensitivity", String.valueOf(FreelookModule.sensitivity));
            queue(out, movementDir.resolve("freelook.properties"), freelookProps, "Caeser Client - Freelook");

            // Movement - Emotes
            Properties emoteProps = new Properties();
            emoteProps.setProperty("enabled", String.valueOf(com.bame.client.module.EmoteModule.enabled));
            emoteProps.setProperty("keyBind", String.valueOf(com.bame.client.module.EmoteModule.keyBind));
            emoteProps.setProperty("expanded", String.valueOf(com.bame.client.module.EmoteModule.expanded));
            emoteProps.setProperty("smoothAnimations", String.valueOf(com.bame.client.module.EmoteModule.smoothAnimations));
            queue(out, movementDir.resolve("emotes.properties"), emoteProps, "Caeser Client - Emotes");

            // Visuals - ItemSize
            Properties itemSizeProps = new Properties();
            itemSizeProps.setProperty("enabled", String.valueOf(ItemSizeModule.enabled));
            itemSizeProps.setProperty("keyBind", String.valueOf(ItemSizeModule.keyBind));
            itemSizeProps.setProperty("expanded", String.valueOf(ItemSizeModule.expanded));
            itemSizeProps.setProperty("scale", String.valueOf(ItemSizeModule.scale));
            itemSizeProps.setProperty("yOffset", String.valueOf(ItemSizeModule.yOffset));
            itemSizeProps.setProperty("selectedItems", String.join(",", ItemSizeModule.selectedItems));
            queue(out, visualsDir.resolve("item_size.properties"), itemSizeProps, "Caeser Client - ItemSize");

            // Visuals - HandPosition
            Properties handPosProps = new Properties();
            handPosProps.setProperty("enabled", String.valueOf(HandPositionModule.enabled));
            handPosProps.setProperty("keyBind", String.valueOf(HandPositionModule.keyBind));
            handPosProps.setProperty("expanded", String.valueOf(HandPositionModule.expanded));
            handPosProps.setProperty("posX", String.valueOf(HandPositionModule.posX));
            handPosProps.setProperty("posY", String.valueOf(HandPositionModule.posY));
            handPosProps.setProperty("posZ", String.valueOf(HandPositionModule.posZ));
            handPosProps.setProperty("scale", String.valueOf(HandPositionModule.scale));
            handPosProps.setProperty("pitch", String.valueOf(HandPositionModule.pitch));
            handPosProps.setProperty("yaw", String.valueOf(HandPositionModule.yaw));
            handPosProps.setProperty("roll", String.valueOf(HandPositionModule.roll));
            handPosProps.setProperty("swingStyle", String.valueOf(HandPositionModule.swingStyle));
            handPosProps.setProperty("applyToOffhand", String.valueOf(HandPositionModule.applyToOffhand));
            handPosProps.setProperty("weaponsOnly", String.valueOf(HandPositionModule.weaponsOnly));
            handPosProps.setProperty("miningAnimation", String.valueOf(HandPositionModule.miningAnimation));
            queue(out, visualsDir.resolve("hand_position.properties"), handPosProps, "Caeser Client - HandPosition");
            // World - TimeChanger
            Properties timeChangerProps = new Properties();
            timeChangerProps.setProperty("enabled", String.valueOf(TimeChangerModule.enabled));
            timeChangerProps.setProperty("expanded", String.valueOf(TimeChangerModule.expanded));
            timeChangerProps.setProperty("keyBind", String.valueOf(TimeChangerModule.keyBind));
            timeChangerProps.setProperty("timeMode", String.valueOf(TimeChangerModule.timeMode));
            queue(out, worldDir.resolve("time_changer.properties"), timeChangerProps, "Caeser Client - TimeChanger");

            // Visuals - SkinProtect
            Properties skinProtectProps = new Properties();
            skinProtectProps.setProperty("enabled", String.valueOf(SkinProtectModule.enabled));
            skinProtectProps.setProperty("expanded", String.valueOf(SkinProtectModule.expanded));
            skinProtectProps.setProperty("keyBind", String.valueOf(SkinProtectModule.keyBind));
            skinProtectProps.setProperty("skinIndex", String.valueOf(SkinProtectModule.currentSkinIndex));
            skinProtectProps.setProperty("currentSkinName", SkinProtectModule.getCurrentSkinName());
            List<String> customSkinList = new ArrayList<>();
            for (SkinProtectModule.RealSkinEntry e : SkinProtectModule.CUSTOM_SEARCHED_SKINS) {
                customSkinList.add(e.name + ":" + e.uuid.toString());
            }
            skinProtectProps.setProperty("customSkins", String.join(";", customSkinList));
            queue(out, visualsDir.resolve("skin_protect.properties"), skinProtectProps, "Caeser Client - SkinProtect");

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
            themeProps.setProperty("customWallpaper", String.valueOf(customWallpaper));
            themeProps.setProperty("selectedWallpaper", selectedWallpaper != null ? selectedWallpaper : "");
            themeProps.setProperty("wallpaperBrightness", String.valueOf(wallpaperBrightness));
            themeProps.setProperty("wallpaperBlur", String.valueOf(wallpaperBlur));
            queue(out, themeDir.resolve("theme.properties"), themeProps, "Caeser Client - Theme");

            // 7. Settings
            Properties settingsProps = new Properties();
            settingsProps.setProperty("menuBind", String.valueOf(menuBind));
            settingsProps.setProperty("quickFriend", String.valueOf(quickFriend));
            settingsProps.setProperty("guiAnimation", String.valueOf(guiAnimation));
            settingsProps.setProperty("selectedCategory", selectedCategory);
            settingsProps.setProperty("moduleSound", String.valueOf(moduleSound));
            settingsProps.setProperty("soundPack", String.valueOf(soundPack));
            settingsProps.setProperty("soundVolume", String.valueOf(soundVolume));
            settingsProps.setProperty("hoverSound", String.valueOf(hoverSound));
            settingsProps.setProperty("hoverStyle", String.valueOf(hoverStyle));
            settingsProps.setProperty("hoverVolume", String.valueOf(hoverVolume));
            settingsProps.setProperty("notifications", String.valueOf(notifications));
            settingsProps.setProperty("threeDGui", String.valueOf(threeDGui));
            settingsProps.setProperty("secretRequireShift", String.valueOf(secretRequireShift));
            settingsProps.setProperty("secretRequireCtrl", String.valueOf(secretRequireCtrl));
            settingsProps.setProperty("secretRequireAlt", String.valueOf(secretRequireAlt));
            settingsProps.setProperty("secretKey", String.valueOf(secretKey));
            queue(out, settingsDir.resolve("settings.properties"), settingsProps, "Caeser Client - General Settings");
        }
        return out;
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

    private static void loadLegacy(Path legacyFile) {
        try (InputStream in = Files.newInputStream(legacyFile)) {
            Properties props = new Properties();
            props.load(in);

            AutoAreaMinerModule.keyBind = getInt(props, "keyBind", AutoAreaMinerModule.keyBind);
            AutoAreaMinerModule.mode3x3 = getBool(props, "mode3x3", AutoAreaMinerModule.mode3x3);
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
            outlineColor = getHex(props, "outlineColor", outlineColor);
            renderMode = getInt(props, "renderMode", renderMode);
            outlineWidth = getFloat(props, "outlineWidth", outlineWidth);

            fullbrightEnabled = getBool(props, "fullbrightEnabled", fullbrightEnabled);
            fullbrightIntensity = getFloat(props, "fullbrightIntensity", fullbrightIntensity);
            FullbrightModule.enabled = fullbrightEnabled;
            FullbrightModule.intensity = fullbrightIntensity;
            FullbrightModule.keyBind = getInt(props, "fullbrightKeyBind", FullbrightModule.keyBind);

            ZoomModule.enabled = getBool(props, "zoomEnabled", ZoomModule.enabled);
            ZoomModule.keyBind = getInt(props, "zoomKeyBind", ZoomModule.keyBind);
            ZoomModule.mode = getInt(props, "zoomMode", ZoomModule.mode);
            ZoomModule.type = getInt(props, "zoomType", ZoomModule.type);
            ZoomModule.scrollZoom = getBool(props, "zoomScrollZoom", ZoomModule.scrollZoom);
            ZoomModule.defaultLevel = getInt(props, "zoomDefaultLevel", ZoomModule.defaultLevel);

            FakeScoreboardModule.enabled = getBool(props, "fakeScoreboardEnabled", FakeScoreboardModule.enabled);
            FakeScoreboardModule.keyBind = getInt(props, "fakeScoreboardKeyBind", FakeScoreboardModule.keyBind);
            FakeScoreboardModule.expanded = getBool(props, "fakeScoreboardExpanded", FakeScoreboardModule.expanded);
            FakeScoreboardModule.hudX = getInt(props, "fakeScoreboardHudX", FakeScoreboardModule.hudX);
            FakeScoreboardModule.hudY = getInt(props, "fakeScoreboardHudY", FakeScoreboardModule.hudY);
            FakeScoreboardModule.scale = getFloat(props, "fakeScoreboardScale", FakeScoreboardModule.scale);
            FakeScoreboardModule.bgMode = getInt(props, "fakeScoreboardBgMode", FakeScoreboardModule.bgMode);
            FakeScoreboardModule.money = getString(props, "fakeScoreboardMoney", FakeScoreboardModule.money);
            FakeScoreboardModule.stars = getString(props, "fakeScoreboardStars", FakeScoreboardModule.stars);
            FakeScoreboardModule.kills = getString(props, "fakeScoreboardKills", FakeScoreboardModule.kills);
            FakeScoreboardModule.deaths = getString(props, "fakeScoreboardDeaths", FakeScoreboardModule.deaths);
            FakeScoreboardModule.playtime = getString(props, "fakeScoreboardTime", FakeScoreboardModule.playtime);

            ShowHudModule.enabled = getBool(props, "showHudEnabled", ShowHudModule.enabled);
            ShowHudModule.keyBind = getInt(props, "showHudKeyBind", ShowHudModule.keyBind);
            ShowHudModule.expanded = getBool(props, "showHudExpanded", ShowHudModule.expanded);

            ClockModule.enabled = getBool(props, "clockEnabled", ClockModule.enabled);
            ClockModule.hudX = getInt(props, "clockHudX", ClockModule.hudX);
            ClockModule.hudY = getInt(props, "clockHudY", ClockModule.hudY);
            ClockModule.scale = getFloat(props, "clockScale", ClockModule.scale);
            ClockModule.bgMode = getInt(props, "clockBgMode", ClockModule.bgMode);

            CoordinatesModule.enabled = getBool(props, "coordinatesEnabled", CoordinatesModule.enabled);
            CoordinatesModule.hudX = getInt(props, "coordinatesHudX", CoordinatesModule.hudX);
            CoordinatesModule.hudY = getInt(props, "coordinatesHudY", CoordinatesModule.hudY);
            CoordinatesModule.scale = getFloat(props, "coordinatesScale", CoordinatesModule.scale);
            CoordinatesModule.bgMode = getInt(props, "coordinatesBgMode", CoordinatesModule.bgMode);
            CoordinatesModule.expanded = getBool(props, "coordinatesExpanded", CoordinatesModule.expanded);
            CoordinatesModule.layoutMode = getInt(props, "coordinatesLayoutMode", CoordinatesModule.layoutMode);
            CoordinatesModule.style = getInt(props, "coordinatesStyle", CoordinatesModule.style);
            CoordinatesModule.showX = getBool(props, "coordinatesShowX", CoordinatesModule.showX);
            CoordinatesModule.showY = getBool(props, "coordinatesShowY", CoordinatesModule.showY);
            CoordinatesModule.showZ = getBool(props, "coordinatesShowZ", CoordinatesModule.showZ);
            CoordinatesModule.decimals = getBool(props, "coordinatesDecimals", CoordinatesModule.decimals);
            CoordinatesModule.showNether = getBool(props, "coordinatesShowNether", CoordinatesModule.showNether);
            CoordinatesModule.showDirection = getBool(props, "coordinatesShowDirection", CoordinatesModule.showDirection);

            PotionsModule.enabled = getBool(props, "potionsEnabled", PotionsModule.enabled);
            PotionsModule.hudX = getInt(props, "potionsHudX", PotionsModule.hudX);
            PotionsModule.hudY = getInt(props, "potionsHudY", PotionsModule.hudY);
            PotionsModule.scale = getFloat(props, "potionsScale", PotionsModule.scale);
            PotionsModule.bgMode = getInt(props, "potionsBgMode", PotionsModule.bgMode);

            TargetHudModule.enabled = getBool(props, "targetHudEnabled", TargetHudModule.enabled);
            TargetHudModule.expanded = getBool(props, "targetHudExpanded", TargetHudModule.expanded);
            TargetHudModule.showHearts = getBool(props, "targetHudShowHearts", TargetHudModule.showHearts);
            TargetHudModule.showArmor = getBool(props, "targetHudShowArmor", TargetHudModule.showArmor);
            TargetHudModule.playersOnly = getBool(props, "targetHudPlayersOnly", TargetHudModule.playersOnly);
            TargetHudModule.hudX = getInt(props, "targetHudHudX", TargetHudModule.hudX);
            TargetHudModule.hudY = getInt(props, "targetHudHudY", TargetHudModule.hudY);
            TargetHudModule.scale = getFloat(props, "targetHudScale", TargetHudModule.scale);
            TargetHudModule.bgMode = getInt(props, "targetHudBgMode", TargetHudModule.bgMode);

            ArmorHudModule.enabled = getBool(props, "armorHudEnabled", ArmorHudModule.enabled);
            ArmorHudModule.hudX = getInt(props, "armorHudHudX", ArmorHudModule.hudX);
            ArmorHudModule.hudY = getInt(props, "armorHudHudY", ArmorHudModule.hudY);
            ArmorHudModule.scale = getFloat(props, "armorHudScale", ArmorHudModule.scale);
            ArmorHudModule.bgMode = getInt(props, "armorHudBgMode", ArmorHudModule.bgMode);

            KeyStrokesModule.enabled = getBool(props, "keyStrokesEnabled", KeyStrokesModule.enabled);
            KeyStrokesModule.keyBind = getInt(props, "keyStrokesKeyBind", KeyStrokesModule.keyBind);
            KeyStrokesModule.hudX = getInt(props, "keyStrokesHudX", KeyStrokesModule.hudX);
            KeyStrokesModule.hudY = getInt(props, "keyStrokesHudY", KeyStrokesModule.hudY);
            KeyStrokesModule.scale = getFloat(props, "keyStrokesScale", KeyStrokesModule.scale);
            for (KeyStrokesModule.KeyStroke key : KeyStrokesModule.keys) {
                key.relX = getInt(props, "ks_" + key.name + "_x", key.relX);
                key.relY = getInt(props, "ks_" + key.name + "_y", key.relY);
                key.width = getInt(props, "ks_" + key.name + "_w", key.width);
                key.height = getInt(props, "ks_" + key.name + "_h", key.height);
            }

            CpsModule.enabled = getBool(props, "cpsEnabled", CpsModule.enabled);
            CpsModule.keyBind = getInt(props, "cpsKeyBind", CpsModule.keyBind);
            CpsModule.hudX = getInt(props, "cpsHudX", CpsModule.hudX);
            CpsModule.hudY = getInt(props, "cpsHudY", CpsModule.hudY);
            CpsModule.scale = getFloat(props, "cpsScale", CpsModule.scale);
            CpsModule.bgMode = getInt(props, "cpsBgMode", CpsModule.bgMode);

            FpsModule.enabled = getBool(props, "fpsEnabled", FpsModule.enabled);
            FpsModule.keyBind = getInt(props, "fpsKeyBind", FpsModule.keyBind);
            FpsModule.hudX = getInt(props, "fpsHudX", FpsModule.hudX);
            FpsModule.hudY = getInt(props, "fpsHudY", FpsModule.hudY);
            FpsModule.scale = getFloat(props, "fpsScale", FpsModule.scale);
            FpsModule.bgMode = getInt(props, "fpsBgMode", FpsModule.bgMode);

            PingModule.enabled = getBool(props, "pingEnabled", PingModule.enabled);
            PingModule.keyBind = getInt(props, "pingKeyBind", PingModule.keyBind);
            PingModule.hudX = getInt(props, "pingHudX", PingModule.hudX);
            PingModule.hudY = getInt(props, "pingHudY", PingModule.hudY);
            PingModule.scale = getFloat(props, "pingScale", PingModule.scale);
            PingModule.bgMode = getInt(props, "pingBgMode", PingModule.bgMode);

            ServerInfoModule.enabled = getBool(props, "serverInfoEnabled", ServerInfoModule.enabled);
            ServerInfoModule.keyBind = getInt(props, "serverInfoKeyBind", ServerInfoModule.keyBind);
            ServerInfoModule.hudX = getInt(props, "serverInfoHudX", ServerInfoModule.hudX);
            ServerInfoModule.hudY = getInt(props, "serverInfoHudY", ServerInfoModule.hudY);
            ServerInfoModule.scale = getFloat(props, "serverInfoScale", ServerInfoModule.scale);
            ServerInfoModule.bgMode = getInt(props, "serverInfoBgMode", ServerInfoModule.bgMode);
            ServerInfoModule.showName = getBool(props, "serverInfoShowName", ServerInfoModule.showName);
            ServerInfoModule.showServer = getBool(props, "serverInfoShowServer", ServerInfoModule.showServer);
            ServerInfoModule.showTime = getBool(props, "serverInfoShowTime", ServerInfoModule.showTime);

            NameProtectModule.enabled = getBool(props, "nameProtectEnabled", NameProtectModule.enabled);
            NameProtectModule.keyBind = getInt(props, "nameProtectKeyBind", NameProtectModule.keyBind);
            NameProtectModule.alias = getString(props, "nameProtectAlias", NameProtectModule.alias);
            NameProtectModule.expanded = getBool(props, "nameProtectExpanded", NameProtectModule.expanded);

            TabBadgeModule.enabled = getBool(props, "tabBadgeEnabled", TabBadgeModule.enabled);
            TabBadgeModule.keyBind = getInt(props, "tabBadgeKeyBind", TabBadgeModule.keyBind);
            TabBadgeModule.expanded = getBool(props, "tabBadgeExpanded", TabBadgeModule.expanded);

            AutoCartModule.enabled = getBool(props, "autoCartEnabled", AutoCartModule.enabled);
            AutoCartModule.keyBind = getInt(props, "autoCartKeyBind", AutoCartModule.keyBind);
            AutoCartModule.expanded = getBool(props, "autoCartExpanded", AutoCartModule.expanded);
            AutoCartModule.switchBack = getBool(props, "autoCartSwitchBack", AutoCartModule.switchBack);
            AutoCartModule.switchDelay = getInt(props, "autoCartSwitchDelay", AutoCartModule.switchDelay);
            if (props.containsKey("autoCartMode")) {
                try {
                    AutoCartModule.mode = AutoCartModule.CartMode.valueOf(props.getProperty("autoCartMode"));
                } catch (Exception ignored) {}
            }

            AutoMaceModule.enabled = getBool(props, "autoMaceEnabled", AutoMaceModule.enabled);
            AutoMaceModule.keyBind = getInt(props, "autoMaceKeyBind", AutoMaceModule.keyBind);
            AutoMaceModule.expanded = getBool(props, "autoMaceExpanded", AutoMaceModule.expanded);
            AutoMaceModule.switchBack = getBool(props, "autoMaceSwitchBack", AutoMaceModule.switchBack);
            AutoMaceModule.switchDelay = getInt(props, "autoMaceSwitchDelay", AutoMaceModule.switchDelay);
            AutoMaceModule.cooldownCheck = getBool(props, "autoMaceCooldownCheck", AutoMaceModule.cooldownCheck);
            AutoMaceModule.onlyPlayers = getBool(props, "autoMaceOnlyPlayers", AutoMaceModule.onlyPlayers);
            AutoMaceModule.minFallDistance = getDouble(props, "autoMaceMinFallDistance", AutoMaceModule.minFallDistance);
            if (props.containsKey("autoMaceMode")) {
                try {
                    AutoMaceModule.mode = AutoMaceModule.MaceMode.valueOf(props.getProperty("autoMaceMode"));
                } catch (Exception ignored) {}
            }

            DTapModule.enabled = getBool(props, "dTapEnabled", DTapModule.enabled);
            DTapModule.keyBind = getInt(props, "dTapKeyBind", DTapModule.keyBind);
            DTapModule.expanded = getBool(props, "dTapExpanded", DTapModule.expanded);
            DTapModule.tapDelay = getInt(props, "dTapTapDelay", DTapModule.tapDelay);
            DTapModule.switchBack = getBool(props, "dTapSwitchBack", DTapModule.switchBack);
            if (props.containsKey("dTapMode")) {
                try {
                    DTapModule.mode = DTapModule.DTapMode.valueOf(props.getProperty("dTapMode"));
                } catch (Exception ignored) {}
            }

            PearlCatchModule.enabled = getBool(props, "pearlCatchEnabled", PearlCatchModule.enabled);
            PearlCatchModule.keyBind = getInt(props, "pearlCatchKeyBind", PearlCatchModule.keyBind);
            PearlCatchModule.expanded = getBool(props, "pearlCatchExpanded", PearlCatchModule.expanded);
            PearlCatchModule.timingDelay = getInt(props, "pearlCatchTimingDelay", PearlCatchModule.timingDelay);
            PearlCatchModule.autoAim = getBool(props, "pearlCatchAutoAim", PearlCatchModule.autoAim);
            PearlCatchModule.switchToMace = getBool(props, "pearlCatchSwitchToMace", PearlCatchModule.switchToMace);
            PearlCatchModule.switchBack = getBool(props, "pearlCatchSwitchBack", PearlCatchModule.switchBack);

            theme = getString(props, "theme", theme);
            seeThrough = getBool(props, "seeThrough", seeThrough);
            frostedBlur = getBool(props, "frostedBlur", frostedBlur);
            ambientBackground = getBool(props, "ambientBackground", ambientBackground);
            customMainColor = getBool(props, "customMainColor", customMainColor);
            classicToggle = getBool(props, "classicToggle", classicToggle);
            mainColor = getInt(props, "mainColor", mainColor);
            ambientPrimary = getInt(props, "ambientPrimary", ambientPrimary);
            ambientSecondary = getInt(props, "ambientSecondary", ambientSecondary);
            ambientMode = getInt(props, "ambientMode", ambientMode);
            ambientIntensity = Math.clamp(getFloat(props, "ambientIntensity", ambientIntensity), 0f, 1f);
            ambientOpacity = Math.clamp(getFloat(props, "ambientOpacity", ambientOpacity), 0f, 1f);
            ambientRadius = Math.clamp(getFloat(props, "ambientRadius", ambientRadius), 0f, 1f);
            ambientAnimation = getBool(props, "ambientAnimation", ambientAnimation);
            animationSpeed = Math.clamp(getFloat(props, "animationSpeed", animationSpeed), 0f, 1f);

            menuBind = getInt(props, "menuBind", menuBind);
            quickFriend = getBool(props, "quickFriend", quickFriend);
            guiAnimation = getInt(props, "guiAnimation", guiAnimation);
            threeDGui = getBool(props, "threeDGui", threeDGui);
            selectedCategory = sanitizeCategory(getString(props, "selectedCategory", selectedCategory));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

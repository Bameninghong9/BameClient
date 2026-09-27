package com.bame.client;

import com.bame.client.module.AutoAreaMinerModule;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class BameClientConfig {
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bameclient.properties");

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
        if (!Files.exists(CONFIG_FILE)) return;
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(in);

            // AutoAreaMiner
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
            if (props.containsKey("outlineColor")) outlineColor = (int)Long.parseLong(props.getProperty("outlineColor", "FF00FFFF"), 16);
            if (props.containsKey("renderMode")) renderMode = Integer.parseInt(props.getProperty("renderMode"));
            if (props.containsKey("outlineWidth")) outlineWidth = Float.parseFloat(props.getProperty("outlineWidth"));

            // Fullbright
            if (props.containsKey("fullbrightEnabled")) fullbrightEnabled = Boolean.parseBoolean(props.getProperty("fullbrightEnabled"));
            if (props.containsKey("fullbrightIntensity")) fullbrightIntensity = Float.parseFloat(props.getProperty("fullbrightIntensity"));
            com.bame.client.module.FullbrightModule.enabled = fullbrightEnabled;
            com.bame.client.module.FullbrightModule.intensity = fullbrightIntensity;
            if (props.containsKey("fullbrightKeyBind")) com.bame.client.module.FullbrightModule.keyBind = Integer.parseInt(props.getProperty("fullbrightKeyBind"));

            // KeyStrokes
            if (props.containsKey("keyStrokesEnabled")) com.bame.client.module.KeyStrokesModule.enabled = Boolean.parseBoolean(props.getProperty("keyStrokesEnabled"));
            if (props.containsKey("keyStrokesKeyBind")) com.bame.client.module.KeyStrokesModule.keyBind = Integer.parseInt(props.getProperty("keyStrokesKeyBind"));
            if (props.containsKey("keyStrokesHudX")) com.bame.client.module.KeyStrokesModule.hudX = Integer.parseInt(props.getProperty("keyStrokesHudX"));
            if (props.containsKey("keyStrokesHudY")) com.bame.client.module.KeyStrokesModule.hudY = Integer.parseInt(props.getProperty("keyStrokesHudY"));
            if (props.containsKey("keyStrokesScale")) com.bame.client.module.KeyStrokesModule.scale = Float.parseFloat(props.getProperty("keyStrokesScale"));
            for (com.bame.client.module.KeyStrokesModule.KeyStroke key : com.bame.client.module.KeyStrokesModule.keys) {
                if (props.containsKey("ks_" + key.name + "_x")) key.relX = Integer.parseInt(props.getProperty("ks_" + key.name + "_x"));
                if (props.containsKey("ks_" + key.name + "_y")) key.relY = Integer.parseInt(props.getProperty("ks_" + key.name + "_y"));
                if (props.containsKey("ks_" + key.name + "_w")) key.width = Integer.parseInt(props.getProperty("ks_" + key.name + "_w"));
                if (props.containsKey("ks_" + key.name + "_h")) key.height = Integer.parseInt(props.getProperty("ks_" + key.name + "_h"));
            }

            // Zoom
            if (props.containsKey("zoomEnabled")) com.bame.client.module.ZoomModule.enabled = Boolean.parseBoolean(props.getProperty("zoomEnabled"));
            if (props.containsKey("zoomKeyBind")) com.bame.client.module.ZoomModule.keyBind = Integer.parseInt(props.getProperty("zoomKeyBind"));
            if (props.containsKey("zoomMode")) com.bame.client.module.ZoomModule.mode = Integer.parseInt(props.getProperty("zoomMode"));

            // FPS
            if (props.containsKey("fpsEnabled")) com.bame.client.module.FpsModule.enabled = Boolean.parseBoolean(props.getProperty("fpsEnabled"));
            if (props.containsKey("fpsKeyBind")) com.bame.client.module.FpsModule.keyBind = Integer.parseInt(props.getProperty("fpsKeyBind"));
            if (props.containsKey("fpsHudX")) com.bame.client.module.FpsModule.hudX = Integer.parseInt(props.getProperty("fpsHudX"));
            if (props.containsKey("fpsHudY")) com.bame.client.module.FpsModule.hudY = Integer.parseInt(props.getProperty("fpsHudY"));
            if (props.containsKey("fpsScale")) com.bame.client.module.FpsModule.scale = Float.parseFloat(props.getProperty("fpsScale"));
            if (props.containsKey("fpsBgMode")) com.bame.client.module.FpsModule.bgMode = Integer.parseInt(props.getProperty("fpsBgMode"));

            // Ping
            if (props.containsKey("pingEnabled")) com.bame.client.module.PingModule.enabled = Boolean.parseBoolean(props.getProperty("pingEnabled"));
            if (props.containsKey("pingKeyBind")) com.bame.client.module.PingModule.keyBind = Integer.parseInt(props.getProperty("pingKeyBind"));
            if (props.containsKey("pingHudX")) com.bame.client.module.PingModule.hudX = Integer.parseInt(props.getProperty("pingHudX"));
            if (props.containsKey("pingHudY")) com.bame.client.module.PingModule.hudY = Integer.parseInt(props.getProperty("pingHudY"));
            if (props.containsKey("pingScale")) com.bame.client.module.PingModule.scale = Float.parseFloat(props.getProperty("pingScale"));
            if (props.containsKey("pingBgMode")) com.bame.client.module.PingModule.bgMode = Integer.parseInt(props.getProperty("pingBgMode"));

            // CPS
            if (props.containsKey("cpsEnabled")) com.bame.client.module.CpsModule.enabled = Boolean.parseBoolean(props.getProperty("cpsEnabled"));
            if (props.containsKey("cpsKeyBind")) com.bame.client.module.CpsModule.keyBind = Integer.parseInt(props.getProperty("cpsKeyBind"));
            if (props.containsKey("cpsHudX")) com.bame.client.module.CpsModule.hudX = Integer.parseInt(props.getProperty("cpsHudX"));
            if (props.containsKey("cpsHudY")) com.bame.client.module.CpsModule.hudY = Integer.parseInt(props.getProperty("cpsHudY"));
            if (props.containsKey("cpsScale")) com.bame.client.module.CpsModule.scale = Float.parseFloat(props.getProperty("cpsScale"));
            if (props.containsKey("cpsBgMode")) com.bame.client.module.CpsModule.bgMode = Integer.parseInt(props.getProperty("cpsBgMode"));

            // Server Info
            if (props.containsKey("serverInfoEnabled")) com.bame.client.module.ServerInfoModule.enabled = Boolean.parseBoolean(props.getProperty("serverInfoEnabled"));
            if (props.containsKey("serverInfoKeyBind")) com.bame.client.module.ServerInfoModule.keyBind = Integer.parseInt(props.getProperty("serverInfoKeyBind"));
            if (props.containsKey("serverInfoHudX")) com.bame.client.module.ServerInfoModule.hudX = Integer.parseInt(props.getProperty("serverInfoHudX"));
            if (props.containsKey("serverInfoHudY")) com.bame.client.module.ServerInfoModule.hudY = Integer.parseInt(props.getProperty("serverInfoHudY"));
            if (props.containsKey("serverInfoScale")) com.bame.client.module.ServerInfoModule.scale = Float.parseFloat(props.getProperty("serverInfoScale"));
            if (props.containsKey("serverInfoBgMode")) com.bame.client.module.ServerInfoModule.bgMode = Integer.parseInt(props.getProperty("serverInfoBgMode"));
            if (props.containsKey("serverInfoShowName")) com.bame.client.module.ServerInfoModule.showName = Boolean.parseBoolean(props.getProperty("serverInfoShowName"));
            if (props.containsKey("serverInfoShowServer")) com.bame.client.module.ServerInfoModule.showServer = Boolean.parseBoolean(props.getProperty("serverInfoShowServer"));
            if (props.containsKey("serverInfoShowTime")) com.bame.client.module.ServerInfoModule.showTime = Boolean.parseBoolean(props.getProperty("serverInfoShowTime"));

            // Name Protect
            if (props.containsKey("nameProtectEnabled")) com.bame.client.module.NameProtectModule.enabled = Boolean.parseBoolean(props.getProperty("nameProtectEnabled"));
            if (props.containsKey("nameProtectKeyBind")) com.bame.client.module.NameProtectModule.keyBind = Integer.parseInt(props.getProperty("nameProtectKeyBind"));
            if (props.containsKey("nameProtectAlias")) com.bame.client.module.NameProtectModule.alias = props.getProperty("nameProtectAlias");

            // Show HUD
            if (props.containsKey("showHudEnabled")) com.bame.client.module.ShowHudModule.enabled = Boolean.parseBoolean(props.getProperty("showHudEnabled"));
            if (props.containsKey("showHudKeyBind")) com.bame.client.module.ShowHudModule.keyBind = Integer.parseInt(props.getProperty("showHudKeyBind"));
            if (props.containsKey("showHudExpanded")) com.bame.client.module.ShowHudModule.expanded = Boolean.parseBoolean(props.getProperty("showHudExpanded"));

            // Fake Scoreboard
            if (props.containsKey("fakeScoreboardEnabled")) com.bame.client.module.FakeScoreboardModule.enabled = Boolean.parseBoolean(props.getProperty("fakeScoreboardEnabled"));
            if (props.containsKey("fakeScoreboardKeyBind")) com.bame.client.module.FakeScoreboardModule.keyBind = Integer.parseInt(props.getProperty("fakeScoreboardKeyBind"));
            if (props.containsKey("fakeScoreboardExpanded")) com.bame.client.module.FakeScoreboardModule.expanded = Boolean.parseBoolean(props.getProperty("fakeScoreboardExpanded"));
            if (props.containsKey("fakeScoreboardHudX")) com.bame.client.module.FakeScoreboardModule.hudX = Integer.parseInt(props.getProperty("fakeScoreboardHudX"));
            if (props.containsKey("fakeScoreboardHudY")) com.bame.client.module.FakeScoreboardModule.hudY = Integer.parseInt(props.getProperty("fakeScoreboardHudY"));
            if (props.containsKey("fakeScoreboardScale")) com.bame.client.module.FakeScoreboardModule.scale = Float.parseFloat(props.getProperty("fakeScoreboardScale"));
            if (props.containsKey("fakeScoreboardBgMode")) com.bame.client.module.FakeScoreboardModule.bgMode = Integer.parseInt(props.getProperty("fakeScoreboardBgMode"));
            if (props.containsKey("fakeScoreboardMoney")) com.bame.client.module.FakeScoreboardModule.money = props.getProperty("fakeScoreboardMoney");
            if (props.containsKey("fakeScoreboardStars")) com.bame.client.module.FakeScoreboardModule.stars = props.getProperty("fakeScoreboardStars");
            if (props.containsKey("fakeScoreboardKills")) com.bame.client.module.FakeScoreboardModule.kills = props.getProperty("fakeScoreboardKills");
            if (props.containsKey("fakeScoreboardDeaths")) com.bame.client.module.FakeScoreboardModule.deaths = props.getProperty("fakeScoreboardDeaths");
            if (props.containsKey("fakeScoreboardTime")) com.bame.client.module.FakeScoreboardModule.playtime = props.getProperty("fakeScoreboardTime");

            // Theme, Effects & Appearance
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

            // General & Settings
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

    public static void save() {
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            Properties props = new Properties();

            // AutoAreaMiner
            props.setProperty("keyBind", String.valueOf(AutoAreaMinerModule.keyBind));
            props.setProperty("mode3x3", String.valueOf(AutoAreaMinerModule.mode3x3));
            if (AutoAreaMinerModule.corner1 != null) {
                props.setProperty("corner1", AutoAreaMinerModule.corner1.getX() + " " + AutoAreaMinerModule.corner1.getY() + " " + AutoAreaMinerModule.corner1.getZ());
            }
            if (AutoAreaMinerModule.corner2 != null) {
                props.setProperty("corner2", AutoAreaMinerModule.corner2.getX() + " " + AutoAreaMinerModule.corner2.getY() + " " + AutoAreaMinerModule.corner2.getZ());
            }
            props.setProperty("outlineColor", Integer.toHexString(outlineColor));
            props.setProperty("renderMode", String.valueOf(renderMode));
            props.setProperty("outlineWidth", String.valueOf(outlineWidth));

            // Fullbright
            props.setProperty("fullbrightEnabled", String.valueOf(com.bame.client.module.FullbrightModule.enabled));
            props.setProperty("fullbrightIntensity", String.valueOf(com.bame.client.module.FullbrightModule.intensity));
            props.setProperty("fullbrightKeyBind", String.valueOf(com.bame.client.module.FullbrightModule.keyBind));

            // KeyStrokes
            props.setProperty("keyStrokesEnabled", String.valueOf(com.bame.client.module.KeyStrokesModule.enabled));
            props.setProperty("keyStrokesKeyBind", String.valueOf(com.bame.client.module.KeyStrokesModule.keyBind));
            props.setProperty("keyStrokesHudX", String.valueOf(com.bame.client.module.KeyStrokesModule.hudX));
            props.setProperty("keyStrokesHudY", String.valueOf(com.bame.client.module.KeyStrokesModule.hudY));
            props.setProperty("keyStrokesScale", String.valueOf(com.bame.client.module.KeyStrokesModule.scale));
            for (com.bame.client.module.KeyStrokesModule.KeyStroke key : com.bame.client.module.KeyStrokesModule.keys) {
                props.setProperty("ks_" + key.name + "_x", String.valueOf(key.relX));
                props.setProperty("ks_" + key.name + "_y", String.valueOf(key.relY));
                props.setProperty("ks_" + key.name + "_w", String.valueOf(key.width));
                props.setProperty("ks_" + key.name + "_h", String.valueOf(key.height));
            }

            // Zoom
            props.setProperty("zoomEnabled", String.valueOf(com.bame.client.module.ZoomModule.enabled));
            props.setProperty("zoomKeyBind", String.valueOf(com.bame.client.module.ZoomModule.keyBind));
            props.setProperty("zoomMode", String.valueOf(com.bame.client.module.ZoomModule.mode));

            // FPS
            props.setProperty("fpsEnabled", String.valueOf(com.bame.client.module.FpsModule.enabled));
            props.setProperty("fpsKeyBind", String.valueOf(com.bame.client.module.FpsModule.keyBind));
            props.setProperty("fpsHudX", String.valueOf(com.bame.client.module.FpsModule.hudX));
            props.setProperty("fpsHudY", String.valueOf(com.bame.client.module.FpsModule.hudY));
            props.setProperty("fpsScale", String.valueOf(com.bame.client.module.FpsModule.scale));
            props.setProperty("fpsBgMode", String.valueOf(com.bame.client.module.FpsModule.bgMode));

            // Ping
            props.setProperty("pingEnabled", String.valueOf(com.bame.client.module.PingModule.enabled));
            props.setProperty("pingKeyBind", String.valueOf(com.bame.client.module.PingModule.keyBind));
            props.setProperty("pingHudX", String.valueOf(com.bame.client.module.PingModule.hudX));
            props.setProperty("pingHudY", String.valueOf(com.bame.client.module.PingModule.hudY));
            props.setProperty("pingScale", String.valueOf(com.bame.client.module.PingModule.scale));
            props.setProperty("pingBgMode", String.valueOf(com.bame.client.module.PingModule.bgMode));

            // CPS
            props.setProperty("cpsEnabled", String.valueOf(com.bame.client.module.CpsModule.enabled));
            props.setProperty("cpsKeyBind", String.valueOf(com.bame.client.module.CpsModule.keyBind));
            props.setProperty("cpsHudX", String.valueOf(com.bame.client.module.CpsModule.hudX));
            props.setProperty("cpsHudY", String.valueOf(com.bame.client.module.CpsModule.hudY));
            props.setProperty("cpsScale", String.valueOf(com.bame.client.module.CpsModule.scale));
            props.setProperty("cpsBgMode", String.valueOf(com.bame.client.module.CpsModule.bgMode));

            // Server Info
            props.setProperty("serverInfoEnabled", String.valueOf(com.bame.client.module.ServerInfoModule.enabled));
            props.setProperty("serverInfoKeyBind", String.valueOf(com.bame.client.module.ServerInfoModule.keyBind));
            props.setProperty("serverInfoHudX", String.valueOf(com.bame.client.module.ServerInfoModule.hudX));
            props.setProperty("serverInfoHudY", String.valueOf(com.bame.client.module.ServerInfoModule.hudY));
            props.setProperty("serverInfoScale", String.valueOf(com.bame.client.module.ServerInfoModule.scale));
            props.setProperty("serverInfoBgMode", String.valueOf(com.bame.client.module.ServerInfoModule.bgMode));
            props.setProperty("serverInfoShowName", String.valueOf(com.bame.client.module.ServerInfoModule.showName));
            props.setProperty("serverInfoShowServer", String.valueOf(com.bame.client.module.ServerInfoModule.showServer));
            props.setProperty("serverInfoShowTime", String.valueOf(com.bame.client.module.ServerInfoModule.showTime));

            // Name Protect
            props.setProperty("nameProtectEnabled", String.valueOf(com.bame.client.module.NameProtectModule.enabled));
            props.setProperty("nameProtectKeyBind", String.valueOf(com.bame.client.module.NameProtectModule.keyBind));
            if (com.bame.client.module.NameProtectModule.alias != null) {
                props.setProperty("nameProtectAlias", com.bame.client.module.NameProtectModule.alias);
            }

            // Show HUD
            props.setProperty("showHudEnabled", String.valueOf(com.bame.client.module.ShowHudModule.enabled));
            props.setProperty("showHudKeyBind", String.valueOf(com.bame.client.module.ShowHudModule.keyBind));
            props.setProperty("showHudExpanded", String.valueOf(com.bame.client.module.ShowHudModule.expanded));

            // Fake Scoreboard
            props.setProperty("fakeScoreboardEnabled", String.valueOf(com.bame.client.module.FakeScoreboardModule.enabled));
            props.setProperty("fakeScoreboardKeyBind", String.valueOf(com.bame.client.module.FakeScoreboardModule.keyBind));
            props.setProperty("fakeScoreboardExpanded", String.valueOf(com.bame.client.module.FakeScoreboardModule.expanded));
            props.setProperty("fakeScoreboardHudX", String.valueOf(com.bame.client.module.FakeScoreboardModule.hudX));
            props.setProperty("fakeScoreboardHudY", String.valueOf(com.bame.client.module.FakeScoreboardModule.hudY));
            props.setProperty("fakeScoreboardScale", String.valueOf(com.bame.client.module.FakeScoreboardModule.scale));
            props.setProperty("fakeScoreboardBgMode", String.valueOf(com.bame.client.module.FakeScoreboardModule.bgMode));
            if (com.bame.client.module.FakeScoreboardModule.money != null) props.setProperty("fakeScoreboardMoney", com.bame.client.module.FakeScoreboardModule.money);
            if (com.bame.client.module.FakeScoreboardModule.stars != null) props.setProperty("fakeScoreboardStars", com.bame.client.module.FakeScoreboardModule.stars);
            if (com.bame.client.module.FakeScoreboardModule.kills != null) props.setProperty("fakeScoreboardKills", com.bame.client.module.FakeScoreboardModule.kills);
            if (com.bame.client.module.FakeScoreboardModule.deaths != null) props.setProperty("fakeScoreboardDeaths", com.bame.client.module.FakeScoreboardModule.deaths);
            if (com.bame.client.module.FakeScoreboardModule.playtime != null) props.setProperty("fakeScoreboardTime", com.bame.client.module.FakeScoreboardModule.playtime);

            // Theme, Effects & Appearance
            props.setProperty("theme", theme);
            props.setProperty("seeThrough", String.valueOf(seeThrough));
            props.setProperty("frostedBlur", String.valueOf(frostedBlur));
            props.setProperty("ambientBackground", String.valueOf(ambientBackground));
            props.setProperty("customMainColor", String.valueOf(customMainColor));
            props.setProperty("classicToggle", String.valueOf(classicToggle));
            props.setProperty("mainColor", String.valueOf(mainColor));
            props.setProperty("ambientPrimary", String.valueOf(ambientPrimary));
            props.setProperty("ambientSecondary", String.valueOf(ambientSecondary));
            props.setProperty("ambientMode", String.valueOf(ambientMode));
            props.setProperty("ambientIntensity", String.valueOf(ambientIntensity));
            props.setProperty("ambientOpacity", String.valueOf(ambientOpacity));
            props.setProperty("ambientRadius", String.valueOf(ambientRadius));
            props.setProperty("ambientAnimation", String.valueOf(ambientAnimation));
            props.setProperty("animationSpeed", String.valueOf(animationSpeed));

            // General & Settings
            props.setProperty("menuBind", String.valueOf(menuBind));
            props.setProperty("quickFriend", String.valueOf(quickFriend));
            props.setProperty("guiAnimation", String.valueOf(guiAnimation));
            if (com.bame.client.gui.BameClientScreen.selected != null) {
                selectedCategory = com.bame.client.gui.BameClientScreen.selected;
            }
            props.setProperty("selectedCategory", selectedCategory);

            props.store(out, "BameClient Configuration");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

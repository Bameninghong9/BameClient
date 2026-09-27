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

    public static int guiAnimation=0; // 0=Zoom/Fade, 1=Slide, 2=None
    public static boolean customMainColor=false, classicToggle=false, ambientAnimation=true;
    public static int mainColor=0xFFB23CEE, ambientPrimary=0xFF5675FF, ambientSecondary=0xFFA647F2;
    public static int ambientMode=0;
    public static float ambientIntensity=1, ambientOpacity=1, ambientRadius=1, animationSpeed=1;
    public static int menuBind = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static boolean quickFriend = false;


    public static void load() {
        if (!Files.exists(CONFIG_FILE)) return;
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
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
            customMainColor = Boolean.parseBoolean(props.getProperty("customMainColor", String.valueOf(customMainColor)));
            classicToggle = Boolean.parseBoolean(props.getProperty("classicToggle", String.valueOf(classicToggle)));
            ambientAnimation = Boolean.parseBoolean(props.getProperty("ambientAnimation", String.valueOf(ambientAnimation)));
            mainColor = Integer.parseInt(props.getProperty("mainColor", String.valueOf(mainColor)));
            ambientPrimary = Integer.parseInt(props.getProperty("ambientPrimary", String.valueOf(ambientPrimary)));
            ambientSecondary = Integer.parseInt(props.getProperty("ambientSecondary", String.valueOf(ambientSecondary)));
            ambientMode = Integer.parseInt(props.getProperty("ambientMode", String.valueOf(ambientMode)));
            ambientIntensity = Math.clamp(Float.parseFloat(props.getProperty("ambientIntensity", String.valueOf(ambientIntensity))),0f,1f);
            ambientOpacity = Math.clamp(Float.parseFloat(props.getProperty("ambientOpacity", String.valueOf(ambientOpacity))),0f,1f);
            ambientRadius = Math.clamp(Float.parseFloat(props.getProperty("ambientRadius", String.valueOf(ambientRadius))),0f,1f);
            animationSpeed = Math.clamp(Float.parseFloat(props.getProperty("animationSpeed", String.valueOf(animationSpeed))),0f,1f);
            outlineColor = (int)Long.parseLong(props.getProperty("outlineColor", "FF00FFFF"),16);
            if (props.containsKey("renderMode")) renderMode = Integer.parseInt(props.getProperty("renderMode"));
            if (props.containsKey("outlineWidth")) outlineWidth = Float.parseFloat(props.getProperty("outlineWidth"));
            if (props.containsKey("fullbrightEnabled")) fullbrightEnabled = Boolean.parseBoolean(props.getProperty("fullbrightEnabled"));
            if (props.containsKey("fullbrightIntensity")) fullbrightIntensity = Float.parseFloat(props.getProperty("fullbrightIntensity"));
            com.bame.client.module.FullbrightModule.enabled = fullbrightEnabled;
            com.bame.client.module.FullbrightModule.intensity = fullbrightIntensity;
            if (props.containsKey("fullbrightKeyBind")) com.bame.client.module.FullbrightModule.keyBind = Integer.parseInt(props.getProperty("fullbrightKeyBind"));
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            Properties props = new Properties();
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
            props.setProperty("fullbrightEnabled", String.valueOf(com.bame.client.module.FullbrightModule.enabled));
            props.setProperty("fullbrightIntensity", String.valueOf(com.bame.client.module.FullbrightModule.intensity));
            props.setProperty("fullbrightKeyBind", String.valueOf(com.bame.client.module.FullbrightModule.keyBind));
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


            props.setProperty("theme", theme);
            props.setProperty("seeThrough", String.valueOf(seeThrough));
            props.setProperty("frostedBlur", String.valueOf(frostedBlur));
            props.setProperty("ambientBackground", String.valueOf(ambientBackground));
                        props.setProperty("customMainColor", String.valueOf(customMainColor));
            props.setProperty("classicToggle", String.valueOf(classicToggle));
            props.setProperty("ambientAnimation", String.valueOf(ambientAnimation));
            props.setProperty("mainColor", String.valueOf(mainColor));
            props.setProperty("ambientPrimary", String.valueOf(ambientPrimary));
            props.setProperty("ambientSecondary", String.valueOf(ambientSecondary));
            props.setProperty("ambientMode", String.valueOf(ambientMode));
            props.setProperty("ambientIntensity", String.valueOf(ambientIntensity));
            props.setProperty("ambientOpacity", String.valueOf(ambientOpacity));
            props.setProperty("ambientRadius", String.valueOf(ambientRadius));
            props.setProperty("animationSpeed", String.valueOf(animationSpeed));
            props.setProperty("menuBind", String.valueOf(menuBind));
            props.setProperty("quickFriend", String.valueOf(quickFriend));
            props.setProperty("guiAnimation", String.valueOf(guiAnimation));

            props.store(out, "BameClient Configuration");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

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
    public static String theme = "Ultraviolet";
    public static boolean seeThrough = false, frostedBlur = true, ambientBackground = true;

    public static boolean customMainColor=false, classicToggle=false, ambientAnimation=true;
    public static int mainColor=0xFFB23CEE, ambientPrimary=0xFF5675FF, ambientSecondary=0xFFA647F2;
    public static int ambientMode=0;
    public static float ambientIntensity=1, ambientOpacity=1, ambientRadius=1, animationSpeed=1;

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) return;
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(in);
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
            theme = props.getProperty("theme", "Ultraviolet");
            seeThrough = Boolean.parseBoolean(props.getProperty("seeThrough", "false"));
            frostedBlur = Boolean.parseBoolean(props.getProperty("frostedBlur", "true"));
            ambientBackground = Boolean.parseBoolean(props.getProperty("ambientBackground", "true"));
            if (props.containsKey("keyBind")) AutoAreaMinerModule.keyBind = Integer.parseInt(props.getProperty("keyBind"));
            if (props.containsKey("mode3x3")) AutoAreaMinerModule.mode3x3 = Boolean.parseBoolean(props.getProperty("mode3x3"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.setProperty("keyBind", String.valueOf(AutoAreaMinerModule.keyBind));
            props.setProperty("mode3x3", String.valueOf(AutoAreaMinerModule.mode3x3));
            props.setProperty("outlineColor", Integer.toHexString(outlineColor));
            props.setProperty("renderMode", String.valueOf(renderMode));
            props.setProperty("outlineWidth", String.valueOf(outlineWidth));
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
            props.store(out, "BameClient Configuration");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

/**
 * Eigener Himmel (nur Oberwelt-Himmel, nicht Nether/End): Himmelsfarbe oder RGB, Tag-Nacht-Wechsel, Horizont
 * (Nebel) in derselben Farbe, eigene Wolkenfarbe und Sterne auch am Tag. Unter Wasser und in Lava bleibt der Nebel
 * unverändert.
 */
public class CustomSkyModule {
    public static boolean enabled = false;
    public static boolean expanded = false;
    public static int keyBind = -1;

    public static int skyColor = 0xFFB23CEE;
    public static boolean chroma = false;
    /** Nachts dunkler werden wie der Vanilla-Himmel; aus = immer volle Farbe. */
    public static boolean dayNightCycle = true;
    /** Horizont/Nebel in der Himmelsfarbe einfärben (sonst bleibt am Horizont ein Vanilla-Streifen). */
    public static boolean tintFog = true;
    public static boolean customClouds = false;
    public static int cloudColor = 0xFFFFC2F2;
    /** Sterne auch tagsüber (Helligkeit 0 bis 1). 0 = Vanilla. */
    public static float stars = 0.0f;

    private static boolean wasKeyBindPressed = false;

    public static int skyRgb() {
        if (chroma) return java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 12000L) / 12000.0f, 0.75f, 1.0f) & 0xFFFFFF;
        return skyColor & 0xFFFFFF;
    }

    /**
     * Eigene Farbe mit der Helligkeit der Vanilla-Farbe (hellster Kanal), damit Nacht, Gewitter und Dämmerung
     * erhalten bleiben. Ohne Tag-Nacht-Wechsel die volle Farbe.
     */
    public static int shade(int customRgb, int vanillaArgb, int dayReference) {
        if (!dayNightCycle) return (vanillaArgb & 0xFF000000) | customRgb;
        int vMax = Math.max(Math.max((vanillaArgb >> 16) & 255, (vanillaArgb >> 8) & 255), vanillaArgb & 255);
        float f = Math.clamp(vMax / (float) dayReference, 0.0f, 1.0f);
        int r = Math.round(((customRgb >> 16) & 255) * f);
        int g = Math.round(((customRgb >> 8) & 255) * f);
        int b = Math.round((customRgb & 255) * f);
        return (vanillaArgb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            com.bame.client.notification.NotificationManager.showModuleToggle("Custom Sky", enabled);
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }

    public static void resetToDefault() {
        skyColor = 0xFFB23CEE;
        chroma = false;
        dayNightCycle = true;
        tintFog = true;
        customClouds = false;
        cloudColor = 0xFFFFC2F2;
        stars = 0.0f;
    }
}

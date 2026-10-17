package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

/**
 * Shader Fog (früher Custom Sky): eigener Oberwelt-Himmel und farbiger Dunst über der fernen Landschaft.
 * Stile: Weltraum (Nebel, viele Sterne, Ringplanet), Tiefsee (dunkles Türkis) oder eine eigene Farbe. Nether und End
 * bleiben unverändert, unter Wasser und in Lava bleibt der Nebel Vanilla.
 */
public class ShaderFogModule {
    public enum Style {
        SPACE("Space", 0xFF04050B, true),
        DEEP_SEA("Deep Sea", 0xFF0A2431, true),
        COLOR("Color", 0, false);

        public final String label;
        /** Grundfarbe der Himmelsscheibe (bei COLOR die eigene Farbe). */
        public final int baseSky;
        /** Mit eigener Himmelstextur (Skybox) statt nur einer Farbe. */
        public final boolean skybox;

        Style(String label, int baseSky, boolean skybox) {
            this.label = label;
            this.baseSky = baseSky;
            this.skybox = skybox;
        }

        public Style next() { return values()[(ordinal() + 1) % values().length]; }

        public static Style parse(String name, Style fallback) {
            for (Style s : values()) if (s.name().equalsIgnoreCase(name)) return s;
            return fallback;
        }
    }

    public static boolean enabled = false;
    public static boolean expanded = false;
    public static int keyBind = -1;

    public static Style style = Style.SPACE;
    /** Eigene Himmelsfarbe (Stil COLOR). */
    public static int skyColor = 0xFFB23CEE;
    public static boolean chroma = false;
    /** Stil COLOR: nachts dunkler werden wie der Vanilla-Himmel. */
    public static boolean dayNightCycle = true;
    /** Ringplanet am Himmel. */
    public static boolean planet = true;
    public static float planetSize = 1.0f;
    /** Sonne, Mond und Abendrot ausblenden. */
    public static boolean hideSunMoon = true;
    /** Ferne Landschaft im Dunst dieser Farbe. */
    public static boolean colorFog = true;
    public static int fogColor = 0xFF9DB7DA;
    /** Wie früh der Dunst beginnt: 0 = Vanilla, 1 = dichter Dunst. */
    public static float fogDensity = 0.5f;
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
     * erhalten bleiben. Mit {@code keepBrightness == false} die volle Farbe.
     */
    public static int shade(int customRgb, int vanillaArgb, int dayReference, boolean keepBrightness) {
        if (!keepBrightness) return (vanillaArgb & 0xFF000000) | customRgb;
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
            com.bame.client.notification.NotificationManager.showModuleToggle("Shader Fog", enabled);
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }

    public static void resetToDefault() {
        style = Style.SPACE;
        skyColor = 0xFFB23CEE;
        chroma = false;
        dayNightCycle = true;
        planet = true;
        planetSize = 1.0f;
        hideSunMoon = true;
        colorFog = true;
        fogColor = 0xFF9DB7DA;
        fogDensity = 0.5f;
        customClouds = false;
        cloudColor = 0xFFFFC2F2;
        stars = 0.0f;
    }
}

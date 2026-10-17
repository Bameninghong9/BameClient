package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

/**
 * Shader Fog: animierter Oberwelt-Himmel aus einem eigenen Shader ({@code ShaderFogRenderer}) und farbiger Dunst über
 * der fernen Landschaft. Modi: Nebula (helle, wirbelnde Wolken), Bloom (leuchtende Lichtbahnen), Saturn (Weltraum mit
 * Ringplanet, Sternen und Sternschnuppen), Drain (dunkler, langsam kreisender Strudel) und Color (eigene Farbe ohne
 * Shader). Nether und End bleiben unverändert, unter Wasser und in Lava bleibt der Nebel Vanilla.
 */
public class ShaderFogModule {
    public enum Mode {
        NEBULA("Nebula", true),
        BLOOM("Bloom", true),
        SATURN("Saturn", true),
        DRAIN("Drain", true),
        COLOR("Color", false);

        public final String label;
        /** Wird mit dem eigenen Himmels-Shader gezeichnet. */
        public final boolean shader;

        Mode(String label, boolean shader) {
            this.label = label;
            this.shader = shader;
        }

        public Mode next() { return values()[(ordinal() + 1) % values().length]; }

        /** Auch alte Stil-Namen (bis 0.3.37) werden erkannt. */
        public static Mode parse(String name, Mode fallback) {
            if (name == null) return fallback;
            if (name.equalsIgnoreCase("SPACE")) return SATURN;
            if (name.equalsIgnoreCase("DEEP_SEA")) return DRAIN;
            for (Mode m : values()) if (m.name().equalsIgnoreCase(name)) return m;
            return fallback;
        }

        /** Dunkler Himmel: Wolken werden abgedunkelt. */
        public boolean dark() { return this == BLOOM || this == SATURN || this == DRAIN; }
    }

    public static boolean enabled = false;
    public static boolean expanded = false;
    public static int keyBind = -1;

    public static Mode mode = Mode.NEBULA;
    /** Farbe des Effekts (Shader-Modi) bzw. des Himmels (Color). */
    public static int color = 0xFF2F8CFF;
    public static boolean chroma = false;
    public static float speed = 1.0f;
    public static float scale = 5.0f;
    public static float intensity = 0.5f;
    /** Deckkraft des Shader-Himmels über dem Vanilla-Himmel. */
    public static float alpha = 1.0f;
    /** Nachts dunkler werden wie der Vanilla-Himmel. */
    public static boolean dayNightCycle = true;
    /** Größe des Planeten im Modus Saturn. */
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
    /** Vanilla-Sterne auch tagsüber (Helligkeit 0 bis 1). 0 = Vanilla. */
    public static float stars = 0.0f;

    private static boolean wasKeyBindPressed = false;

    /** Effektfarbe als 0xRRGGBB (RGB-Modus läuft in 12 Sekunden einmal durch). */
    public static int rgb() {
        if (chroma) return java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 12000L) / 12000.0f, 0.75f, 1.0f) & 0xFFFFFF;
        return color & 0xFFFFFF;
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
        mode = Mode.NEBULA;
        color = 0xFF2F8CFF;
        chroma = false;
        speed = 1.0f;
        scale = 5.0f;
        intensity = 0.5f;
        alpha = 1.0f;
        dayNightCycle = true;
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

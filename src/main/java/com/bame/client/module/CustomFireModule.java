package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Custom Fire („Custom Flame“): eigene, prozedural erzeugte Flammen statt der Vanilla-Feuertextur. Das Feuer am
 * Bildschirm wird selbst gezeichnet ({@code CustomFireOverlay}); mit „Restyle world fire“ bekommen auch Feuer-Blöcke
 * und brennende Spieler/Mobs die eigenen Flammen ({@code FireRestyle}), auch aus Resourcepacks.
 */
public class CustomFireModule {
    public enum Style {
        /** Weiche, flackernde Flammen wie Vanilla. */
        NATIVE("Native"),
        /** Wenige breite, ruhige Flammen. */
        MINIMAL("Minimal"),
        /** Spitze, gezackte Flammenzungen. */
        SHARP("Sharp");

        public final String label;

        Style(String label) { this.label = label; }

        public Style next() { return values()[(ordinal() + 1) % values().length]; }

        public static Style parse(String name, Style fallback) {
            for (Style s : values()) if (s.name().equalsIgnoreCase(name)) return s;
            return fallback;
        }
    }

    public static boolean enabled = false;
    public static boolean expanded = false;
    public static int keyBind = -1;

    /** Hauptfarbe (unten, Kern der Flamme). */
    public static int color = 0xFFFF6A1A;
    /** Zweitfarbe (Spitzen). */
    public static int secondaryColor = 0xFFFFC531;
    /** Zweitfarbe automatisch aus der Hauptfarbe ableiten (heller, Farbton leicht verschoben). */
    public static boolean autoSecondary = true;
    /** Höhe des Feuers am Bildschirm: 0 = ganz unten, 1 = Vanilla. */
    public static float height = 0.36f;
    /** Deckkraft des Feuers am Bildschirm. */
    public static float opacity = 0.9f;
    /** Wie schnell die Flammen flackern und aufsteigen. */
    public static float speed = 1.0f;
    /** Leuchtkraft und Dichte der Flammen. */
    public static float intensity = 1.0f;
    /** Heller Rand entlang der Flammenkontur. */
    public static float edgeGlow = 0.3f;
    /** Innen durchsichtiger (nur Bildschirm-Feuer; Blöcke können nur ganz oder gar nicht durchsichtig sein). */
    public static float centerClear = 0.55f;
    /** Unterer Rand blendet aus (Bildschirm-Feuer). */
    public static float bottomFade = 0.35f;
    public static Style style = Style.NATIVE;
    /** Vanilla-Feuer am Bildschirm ausblenden (sonst liegen beide übereinander). */
    public static boolean hideVanillaFire = true;
    /** Auch Feuer-Blöcke und brennende Spieler/Mobs bekommen die eigenen Flammen. */
    public static boolean restyleWorldFire = true;
    /** Höhe der Flammen auf Feuer-Blöcken (Anteil des Blocks). */
    public static float worldFlameHeight = 1.0f;
    /** Auch Seelenfeuer umgestalten. */
    public static boolean soulFire = false;
    /** Bildschirm-Feuer ausblenden, solange Feuerresistenz wirkt. */
    public static boolean hideWithFireResistance = true;

    private static boolean wasKeyBindPressed = false;

    /** Verschiebung des Bildschirm-Feuers nach unten (Vanilla: -0.3). */
    public static float overlayYOffset() {
        return -0.3f - (1.0f - Math.clamp(height, 0.0f, 1.0f)) * 0.45f;
    }

    public static boolean hideOverlay(MinecraftClient client) {
        return hideWithFireResistance && client.player != null && client.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
    }

    /** Zweitfarbe, die tatsächlich benutzt wird (bei „Auto“ aus der Hauptfarbe abgeleitet). */
    public static int effectiveSecondary() {
        if (!autoSecondary) return secondaryColor;
        int rgb = color & 0xFFFFFF;
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        float hue = hsb[0] + 0.07f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue - (float) Math.floor(hue), hsb[1] * 0.55f, Math.min(1.0f, hsb[2] + 0.35f)) & 0xFFFFFF);
    }

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            com.bame.client.notification.NotificationManager.showModuleToggle("Custom Fire", enabled);
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
        com.bame.client.render.FireRestyle.tick();
    }

    public static void resetToDefault() {
        color = 0xFFFF6A1A;
        secondaryColor = 0xFFFFC531;
        autoSecondary = true;
        height = 0.36f;
        opacity = 0.9f;
        speed = 1.0f;
        intensity = 1.0f;
        edgeGlow = 0.3f;
        centerClear = 0.55f;
        bottomFade = 0.35f;
        style = Style.NATIVE;
        hideVanillaFire = true;
        restyleWorldFire = true;
        worldFlameHeight = 1.0f;
        soulFire = false;
        hideWithFireResistance = true;
    }
}

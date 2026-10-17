package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Eigenes Feuer: Farbe (oder RGB), Höhe und Deckkraft des Feuers am Bildschirmrand, optional auch die Farbe des Feuers
 * auf brennenden Spielern und Mobs. Gezeichnet wird eine graue Kopie der Feuer-Textur ({@code CustomFireRenderer}),
 * damit jede Farbe kräftig wirkt (Einfärben der orangen Vanilla-Textur würde z. B. Blau fast schwarz machen).
 */
public class CustomFireModule {
    public static boolean enabled = false;
    public static boolean expanded = false;
    public static int keyBind = -1;

    public static int color = 0xFF2F8CFF;
    public static boolean chroma = false;
    /** Höhe des Feuers am Bildschirm: 0.0 = ganz unten, 1.0 = Vanilla. */
    public static float height = 0.55f;
    /** Deckkraft 0.1 bis 1.0 (Vanilla 0.9). */
    public static float opacity = 0.75f;
    /** Feuer auf brennenden Entities (auch dem eigenen Spieler in F5) in derselben Farbe. */
    public static boolean entityFire = true;
    /** Bildschirm-Feuer ausblenden, solange Feuerresistenz wirkt. */
    public static boolean hideWithFireResistance = true;

    private static boolean wasKeyBindPressed = false;

    /** Aktuelle Feuerfarbe als 0xRRGGBB (RGB-Modus läuft in 3 Sekunden einmal durch). */
    public static int rgb() {
        if (chroma) return java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 3000L) / 3000.0f, 0.85f, 1.0f) & 0xFFFFFF;
        return color & 0xFFFFFF;
    }

    /** Verschiebung des Bildschirm-Feuers nach unten (Vanilla: -0.3). */
    public static float overlayYOffset() {
        return -0.3f - (1.0f - Math.clamp(height, 0.0f, 1.0f)) * 0.45f;
    }

    public static boolean hideOverlay(MinecraftClient client) {
        return hideWithFireResistance && client.player != null && client.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
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
    }

    public static void resetToDefault() {
        color = 0xFF2F8CFF;
        chroma = false;
        height = 0.55f;
        opacity = 0.75f;
        entityFire = true;
        hideWithFireResistance = true;
    }
}

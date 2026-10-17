package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Eigenes Feuer: färbt die Feuer-Animation selbst um ({@code FireRecolor}), dadurch ist jedes Feuer in der eigenen
 * Farbe: Feuer-Blöcke in der Welt, das Feuer am Bildschirmrand und brennende Spieler/Mobs. Optional Farbverlauf von
 * unten nach oben, RGB und Seelenfeuer. Höhe und Deckkraft gelten für das Feuer am Bildschirmrand.
 */
public class CustomFireModule {
    public static boolean enabled = false;
    public static boolean expanded = false;
    public static int keyBind = -1;

    /** Farbe des Feuers (beim Verlauf: unten). */
    public static int color = 0xFFFFFFFF;
    /** Verlauf von {@link #color} (unten) nach {@link #topColor} (oben). */
    public static boolean gradient = false;
    public static int topColor = 0xFFB23CEE;
    public static boolean chroma = false;
    /** Auch das blaue Seelenfeuer umfärben. */
    public static boolean soulFire = false;
    /** Höhe des Feuers am Bildschirm: 0.0 = ganz unten, 1.0 = Vanilla. */
    public static float height = 0.55f;
    /** Deckkraft des Feuers am Bildschirm, 0.1 bis 1.0 (Vanilla 0.9). */
    public static float opacity = 0.75f;
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

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            com.bame.client.notification.NotificationManager.showModuleToggle("Custom Fire", enabled);
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
        com.bame.client.render.FireRecolor.tick();
    }

    public static void resetToDefault() {
        color = 0xFFFFFFFF;
        gradient = false;
        topColor = 0xFFB23CEE;
        chroma = false;
        soulFire = false;
        height = 0.55f;
        opacity = 0.75f;
        hideWithFireResistance = true;
    }
}

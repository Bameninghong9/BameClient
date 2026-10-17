package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

/**
 * Nametags über Spielern und Mobs: Größe, Hintergrund (Farbe und Deckkraft), Textschatten, eigener Nametag in der
 * Third-Person-Ansicht und das Caeser-Badge vor dem Namen anderer Caeser-Spieler (wie in der Tab-Liste).
 */
public class NametagsModule {
    public static boolean enabled = false;
    public static boolean expanded = false;
    public static int keyBind = -1;

    /** Größe 0.5 bis 2.0 (1.0 = Vanilla). */
    public static float scale = 1.0f;
    /** Hintergrund-Deckkraft 0.0 bis 1.0 (Vanilla nach Minecraft-Option, meist 0.25). */
    public static float backgroundOpacity = 0.25f;
    public static int backgroundColor = 0xFF000000;
    public static boolean textShadow = false;
    public static boolean showOwn = true;
    public static boolean showBadge = true;

    private static boolean wasKeyBindPressed = false;

    /** Hintergrund als ARGB; 0 bleibt 0 (Vanilla zeichnet die vordere Textschicht ohne Hintergrund). */
    public static int background(int vanillaArgb) {
        if (vanillaArgb == 0) return 0;
        int a = Math.clamp(Math.round(backgroundOpacity * 255.0f), 0, 255);
        return (a << 24) | (backgroundColor & 0xFFFFFF);
    }

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            com.bame.client.notification.NotificationManager.showModuleToggle("Nametags", enabled);
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }

    public static void resetToDefault() {
        scale = 1.0f;
        backgroundOpacity = 0.25f;
        backgroundColor = 0xFF000000;
        textShadow = false;
        showOwn = true;
        showBadge = true;
    }
}

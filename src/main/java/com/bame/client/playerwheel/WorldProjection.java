package com.bame.client.playerwheel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;

/**
 * Projektionsmatrix, mit der die Welt in diesem Bild wirklich gezeichnet wurde (FOV inklusive Zoom und
 * Sprint-/Effekt-Änderung, Seitenverhältnis, Wackeln beim Laufen). Gemerkt von {@code GameRendererMixin} beim
 * Hochladen in {@code renderWorld}; gelesen vom Spieler-Picking im selben Bild (nur Render-Thread, ohne Allokation).
 *
 * <p>Die FOV-Methode des GameRenderers wird bewusst nicht erneut aufgerufen: der Zoom-Mixin rechnet dort seine
 * Glättung weiter und würde dadurch schneller zoomen.
 */
public final class WorldProjection {
    private WorldProjection() {}

    private static final float[] CAPTURED = new float[16];
    private static boolean valid;
    private static int capturedFrameWidth;
    private static int capturedFrameHeight;

    /** Aus {@code GameRendererMixin} (Render-Thread). */
    public static void capture(Matrix4f projection) {
        projection.get(CAPTURED);
        Window window = MinecraftClient.getInstance().getWindow();
        capturedFrameWidth = window.getFramebufferWidth();
        capturedFrameHeight = window.getFramebufferHeight();
        valid = true;
    }

    /**
     * Projektionsmatrix (spaltenweise) in {@code out}. Ohne gemerkte Matrix (Mixin nicht aktiv, Fenstergröße geändert)
     * eine einfache Perspektive aus FOV-Einstellung und Seitenverhältnis.
     */
    public static void get(double[] out) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        int fbW = Math.max(1, window.getFramebufferWidth());
        int fbH = Math.max(1, window.getFramebufferHeight());
        if (valid && capturedFrameWidth == fbW && capturedFrameHeight == fbH) {
            for (int i = 0; i < 16; i++) out[i] = CAPTURED[i];
            return;
        }
        double fov = Math.toRadians(client.options.getFov().getValue());
        MouseRay.perspective(fov, fbW / (double) fbH, 0.05, 512.0, out);
    }
}

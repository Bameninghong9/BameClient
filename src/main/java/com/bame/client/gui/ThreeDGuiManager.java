package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public class ThreeDGuiManager {
    private static final Matrix3x2f transform = new Matrix3x2f();
    private static final Matrix3x2f invTransform = new Matrix3x2f();
    private static final Vector2f tempVec = new Vector2f();

    // Natural floating 3D angle matching CORZ cleanly without distortion
    private static final float SHEAR_Y = -0.040f;
    private static final float SHEAR_X = 0.005f;
    private static final float SCALE_X = 0.95f;
    private static final float ROT = 0.0f;
    private static boolean active = false;

    public static void setupFrame(float px, float py, float pw, float ph, float screenWidth, float screenHeight, double mx, double my, float delta) {
        if (!BameClientConfig.threeDGui) {
            active = false;
            transform.identity();
            invTransform.identity();
            return;
        }
        active = true;

        float cx = px + pw * 0.5f;
        float cy = py + ph * 0.5f;

        transform.identity();
        transform.translate(cx, cy);
        if (ROT != 0.0f) transform.rotate(ROT);
        transform.shearY(SHEAR_Y);
        transform.shearX(SHEAR_X);
        transform.scale(SCALE_X, 1.0f);
        transform.translate(-cx, -cy);

        invTransform.set(transform).invert();
    }

    public static double[] screenToLocal(double screenX, double screenY) {
        if (!active || !BameClientConfig.threeDGui) {
            return new double[]{screenX, screenY};
        }
        synchronized (tempVec) {
            invTransform.transformPosition((float) screenX, (float) screenY, tempVec);
            return new double[]{tempVec.x, tempVec.y};
        }
    }

    public static void apply3D(DrawContext c) {
        if (!BameClientConfig.threeDGui) return;
        c.getMatrices().pushMatrix();
        c.getMatrices().mul(transform);
    }

    public static void pop3D(DrawContext c) {
        if (!BameClientConfig.threeDGui) return;
        c.getMatrices().popMatrix();
    }

    public static void render3DTopBezel(DrawContext c, int px, int py, int pw) {
        // Removed: CORZ design has no separate grey top bezel
    }

    public static void renderBackGlow(DrawContext c, int px, int py, int pw, int ph, int accentColor) {
        // Outer halo glow removed per user request
    }

    public static boolean isActive() {
        return active && BameClientConfig.threeDGui;
    }

    // ------------------------------------------------------------------------------------------------
    // Schwebendes Haupt-Mod-Menü (BameClientScreen, GalleryScreen). Unabhängig vom obigen Zustand der
    // Secret-Ansicht, damit sich die beiden Bildschirme nicht gegenseitig die Mausumrechnung verstellen.
    // ------------------------------------------------------------------------------------------------

    /** Panel, dessen Transformation gerade auf dem Matrix-Stapel liegt (für Modell-Vorschauen), sonst null. */
    private static Panel3D pushedPanel = null;
    private static final Matrix3x2f panelMatrix = new Matrix3x2f();

    /** Reduzierte Bewegung: GUI-Animation "None" schaltet Neigung, Schweben und Hereinfliegen ab. */
    public static boolean motionEnabled() {
        return BameClientConfig.guiAnimation != 4;
    }

    /** Legt die Panel-Transformation auf den Matrix-Stapel (nur wenn das Panel aktiv ist). */
    public static void pushPanel(DrawContext c, Panel3D panel) {
        if (!panel.isActive()) return;
        float[] m = panel.matrix();
        panelMatrix.set(m[0], m[1], m[2], m[3], m[4], m[5]);
        c.getMatrices().pushMatrix();
        c.getMatrices().mul(panelMatrix);
        pushedPanel = panel;
    }

    public static void popPanel(DrawContext c, Panel3D panel) {
        if (pushedPanel != panel) return;
        c.getMatrices().popMatrix();
        pushedPanel = null;
    }

    /** Panel-Transformation, die gerade gezeichnet wird, oder null (siehe DrawContextPanel3DMixin). */
    public static Panel3D pushedPanel() {
        return pushedPanel;
    }

    /**
     * Tiefe hinter dem Panel (Bildschirm-Raum, vor {@link #pushPanel}): weicher Akzent-Schein, der sich gegenläufig zur
     * Neigung bewegt (Parallax). Wenige abgerundete Flächen pro Frame, keine Texturen.
     */
    public static void renderBackDepth(DrawContext c, Panel3D panel, int px, int py, int pw, int ph, float open) {
        int accent = GuiTheme.accent() & 0xFFFFFF;
        int ox = Math.round(-panel.tiltX() * 9f);
        int oy = Math.round(-panel.tiltY() * 6f);
        float strength = Math.clamp(open, 0f, 1f);
        for (int i = 5; i >= 1; i--) {
            int grow = i * 7;
            int alpha = Math.round((0x07 + (5 - i) * 2) * strength);
            if (alpha <= 0) continue;
            CustomGuiUtils.fillUltraRounded(c, px - grow + ox, py - grow + oy, pw + 2 * grow, ph + 2 * grow,
                    (alpha << 24) | accent, 8f + grow);
        }
    }

    /** Schatten unter dem Panel (innerhalb der Panel-Transformation, vor dem Panel-Hintergrund). */
    public static void renderPanelShadow(DrawContext c, Panel3D panel, int px, int py, int pw, int ph) {
        int ox = Math.round(-panel.tiltX() * 4f);
        int oy = 7 + Math.round(-panel.tiltY() * 3f);
        for (int i = 4; i >= 1; i--) {
            int grow = i * 3;
            int alpha = 0x12 + (4 - i) * 0x0A;
            CustomGuiUtils.fillUltraRounded(c, px - grow + ox, py - grow + oy, pw + 2 * grow, ph + 2 * grow,
                    alpha << 24, 8f + grow);
        }
    }

    /**
     * Glas-Kante über dem Panel-Inhalt: Rim-Light in der Theme-Akzentfarbe (heller auf der Seite des Mauszeigers),
     * feiner Glanz an der Oberkante. Klicks gehen nicht verloren, es wird nur gezeichnet.
     */
    public static void renderPanelRim(DrawContext c, Panel3D panel, int px, int py, int pw, int ph) {
        int accent = GuiTheme.accent() & 0xFFFFFF;
        float tx = panel.tiltX(), ty = panel.tiltY();
        CustomGuiUtils.drawUltraRoundedOutline(c, px, py, pw, ph, (0x46 << 24) | accent, 8f);

        int r = 8;
        // Seitenkanten: die zur Maus gewandte Seite leuchtet stärker
        int left = Math.round(0x18 + 0x50 * Math.max(0f, -tx));
        int right = Math.round(0x18 + 0x50 * Math.max(0f, tx));
        int top = Math.round(0x30 + 0x50 * Math.max(0f, -ty));
        int bottom = Math.round(0x10 + 0x40 * Math.max(0f, ty));
        c.fill(px, py + r, px + 1, py + ph - r, (left << 24) | accent);
        c.fill(px + pw - 1, py + r, px + pw, py + ph - r, (right << 24) | accent);
        c.fill(px + r, py, px + pw - r, py + 1, (top << 24) | GuiTheme.blend(0xFFFFFF, accent, 0.55f) & 0xFFFFFF);
        c.fill(px + r, py + ph - 1, px + pw - r, py + ph, (bottom << 24) | accent);

        // Glanz: weicher heller Verlauf unter der Oberkante, stärker wenn die Maus oben ist
        int sheen = Math.round(0x0A + 0x0C * Math.max(0f, -ty));
        c.fillGradient(px + r, py + 1, px + pw - r, py + 22, (sheen << 24) | 0xFFFFFF, 0x00FFFFFF);
    }
}

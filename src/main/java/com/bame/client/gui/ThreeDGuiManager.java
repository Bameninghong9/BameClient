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
}

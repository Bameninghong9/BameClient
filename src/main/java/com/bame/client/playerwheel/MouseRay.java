package com.bame.client.playerwheel;

/**
 * Reine Rechnung für das Spieler-Picking mit der Maus (keine Minecraft-/JOML-Klassen, getestet in
 * {@code src/plannerTest}, Aufgabe {@code mouseRayTest}).
 *
 * <p>Ablauf: Mausposition → normalisierte Gerätekoordinaten (NDC, -1..1) → über die Inverse der Projektionsmatrix der
 * Welt (inklusive FOV, Zoom und Seitenverhältnis, siehe {@code WorldProjection}) ein Strahl im Kamera-Raum → mit der
 * Kameradrehung (Gier/Neigung wie {@code Camera.setRotation}) in die Welt gedreht. Umgekehrt projiziert
 * {@link #projectRelative} Weltpunkte auf den Bildschirm (Hervorhebung des Spielers).
 *
 * <p>Matrizen sind 4x4, spaltenweise gespeichert wie bei JOML/OpenGL: {@code m[spalte * 4 + zeile]}.
 */
public final class MouseRay {
    private MouseRay() {}

    private static final double EPS = 1e-12;

    // ------------------------------------------------------------------------------------------------------------
    // Bildschirm ↔ NDC
    // ------------------------------------------------------------------------------------------------------------

    /** NDC-x aus einer Position und der Breite derselben Einheit (Fensterpixel oder GUI-Pixel mit GUI-Breite). */
    public static double ndcX(double x, double width) {
        return width <= 0 ? 0.0 : 2.0 * x / width - 1.0;
    }

    /** NDC-y (oben = +1) aus einer Position von oben und der Höhe derselben Einheit. */
    public static double ndcY(double y, double height) {
        return height <= 0 ? 0.0 : 1.0 - 2.0 * y / height;
    }

    /** GUI-Breite wie {@code Window}: Framebuffer durch GUI-Skalierung, aufgerundet. */
    public static int scaledSize(int framebufferSize, int guiScale) {
        int scale = Math.max(1, guiScale);
        int i = framebufferSize / scale;
        return framebufferSize % scale != 0 ? i + 1 : i;
    }

    /** Mausposition in GUI-Pixeln wie {@code Mouse.scaleX}: Fensterpixel * GUI-Breite / Fensterbreite. */
    public static double guiFromWindow(double windowPos, int windowSize, int scaledSize) {
        return windowSize <= 0 ? 0.0 : windowPos * scaledSize / windowSize;
    }

    /**
     * GUI-Koordinate, an der ein NDC-Punkt gezeichnet erscheint. Die GUI wird mit Framebuffer / GUI-Skalierung
     * (nicht aufgerundet) abgebildet, deshalb hier diese Breite.
     */
    public static double guiFromNdcX(double ndcX, int framebufferWidth, int guiScale) {
        return (ndcX + 1.0) * 0.5 * framebufferWidth / Math.max(1, guiScale);
    }

    public static double guiFromNdcY(double ndcY, int framebufferHeight, int guiScale) {
        return (1.0 - ndcY) * 0.5 * framebufferHeight / Math.max(1, guiScale);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Matrizen
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Perspektive wie JOML {@code Matrix4f.perspective(fovy, aspect, near, far)} (OpenGL, z -1..1). Für Tests und als
     * Rückfall, solange noch keine Projektionsmatrix der Welt gemerkt wurde.
     */
    public static void perspective(double fovYRadians, double aspect, double near, double far, double[] out) {
        double h = Math.tan(fovYRadians * 0.5);
        java.util.Arrays.fill(out, 0, 16, 0.0);
        out[0] = 1.0 / (h * aspect);
        out[5] = 1.0 / h;
        out[10] = (far + near) / (near - far);
        out[11] = -1.0;
        out[14] = (far + far) * near / (near - far);
    }

    /** {@code out = a * b} (beide spaltenweise). {@code out} darf weder {@code a} noch {@code b} sein. */
    public static void multiply(double[] a, double[] b, double[] out) {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                double s = 0.0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[col * 4 + k];
                out[col * 4 + row] = s;
            }
        }
    }

    /**
     * Inverse einer 4x4-Matrix (Kofaktoren). {@code false}, wenn die Matrix (fast) singulär ist; {@code out} ist dann
     * unverändert. {@code out} darf {@code m} sein.
     */
    public static boolean invert(double[] m, double[] out) {
        double a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
        double a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
        double a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
        double a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];
        double b00 = a00 * a11 - a01 * a10;
        double b01 = a00 * a12 - a02 * a10;
        double b02 = a00 * a13 - a03 * a10;
        double b03 = a01 * a12 - a02 * a11;
        double b04 = a01 * a13 - a03 * a11;
        double b05 = a02 * a13 - a03 * a12;
        double b06 = a20 * a31 - a21 * a30;
        double b07 = a20 * a32 - a22 * a30;
        double b08 = a20 * a33 - a23 * a30;
        double b09 = a21 * a32 - a22 * a31;
        double b10 = a21 * a33 - a23 * a31;
        double b11 = a22 * a33 - a23 * a32;
        double det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (!Double.isFinite(det) || Math.abs(det) < EPS) return false;
        double inv = 1.0 / det;
        double r0 = (a11 * b11 - a12 * b10 + a13 * b09) * inv;
        double r1 = (a02 * b10 - a01 * b11 - a03 * b09) * inv;
        double r2 = (a31 * b05 - a32 * b04 + a33 * b03) * inv;
        double r3 = (a22 * b04 - a21 * b05 - a23 * b03) * inv;
        double r4 = (a12 * b08 - a10 * b11 - a13 * b07) * inv;
        double r5 = (a00 * b11 - a02 * b08 + a03 * b07) * inv;
        double r6 = (a32 * b02 - a30 * b05 - a33 * b01) * inv;
        double r7 = (a20 * b05 - a22 * b02 + a23 * b01) * inv;
        double r8 = (a10 * b10 - a11 * b08 + a13 * b06) * inv;
        double r9 = (a01 * b08 - a00 * b10 - a03 * b06) * inv;
        double r10 = (a30 * b04 - a31 * b02 + a33 * b00) * inv;
        double r11 = (a21 * b02 - a20 * b04 - a23 * b00) * inv;
        double r12 = (a11 * b07 - a10 * b09 - a12 * b06) * inv;
        double r13 = (a00 * b09 - a01 * b07 + a02 * b06) * inv;
        double r14 = (a31 * b01 - a30 * b03 - a32 * b00) * inv;
        double r15 = (a20 * b03 - a21 * b01 + a22 * b00) * inv;
        out[0] = r0; out[1] = r1; out[2] = r2; out[3] = r3;
        out[4] = r4; out[5] = r5; out[6] = r6; out[7] = r7;
        out[8] = r8; out[9] = r9; out[10] = r10; out[11] = r11;
        out[12] = r12; out[13] = r13; out[14] = r14; out[15] = r15;
        return true;
    }

    /** Punkt (x, y, z, 1) mit der Matrix abbilden und durch w teilen. {@code false}, wenn w (fast) 0 ist. */
    public static boolean transformProject(double[] m, double x, double y, double z, double[] out3) {
        double rx = m[0] * x + m[4] * y + m[8] * z + m[12];
        double ry = m[1] * x + m[5] * y + m[9] * z + m[13];
        double rz = m[2] * x + m[6] * y + m[10] * z + m[14];
        double rw = m[3] * x + m[7] * y + m[11] * z + m[15];
        if (!Double.isFinite(rw) || Math.abs(rw) < EPS) return false;
        out3[0] = rx / rw;
        out3[1] = ry / rw;
        out3[2] = rz / rw;
        return true;
    }

    /**
     * Richtung des Strahls durch einen NDC-Punkt im Kamera-Raum (Blick entlang -z), normiert. Zwei Tiefen (0 und 0,5)
     * statt Nah-/Fernebene: funktioniert für z -1..1 und 0..1 gleich, beide liegen auf derselben Sichtlinie.
     *
     * @param inverseProjection Inverse der Projektionsmatrix (siehe {@link #invert})
     * @return {@code false}, wenn kein Strahl bestimmt werden kann
     */
    public static boolean viewDirection(double[] inverseProjection, double ndcX, double ndcY, double[] out3, double[] scratch3) {
        if (!transformProject(inverseProjection, ndcX, ndcY, 0.0, scratch3)) return false;
        double ax = scratch3[0], ay = scratch3[1], az = scratch3[2];
        if (!transformProject(inverseProjection, ndcX, ndcY, 0.5, scratch3)) return false;
        double dx = scratch3[0] - ax, dy = scratch3[1] - ay, dz = scratch3[2] - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(len) || len < EPS) return false;
        if (dz > 0) { // Tiefe wächst Richtung -z; sonst zeigte der Strahl nach hinten
            dx = -dx;
            dy = -dy;
            dz = -dz;
        }
        out3[0] = dx / len;
        out3[1] = dy / len;
        out3[2] = dz / len;
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Kameradrehung (Camera.setRotation: rotationYXZ(PI - gier, -neigung, 0))
    // ------------------------------------------------------------------------------------------------------------

    /** Vektor aus dem Kamera-Raum in die Welt drehen: {@code R = rotY(PI - gier) * rotX(-neigung)}. */
    public static void rotateToWorld(double yawDeg, double pitchDeg, double vx, double vy, double vz, double[] out3) {
        double a = -Math.toRadians(pitchDeg);
        double ca = Math.cos(a), sa = Math.sin(a);
        double x1 = vx;
        double y1 = vy * ca - vz * sa;
        double z1 = vy * sa + vz * ca;
        double b = Math.PI - Math.toRadians(yawDeg);
        double cb = Math.cos(b), sb = Math.sin(b);
        out3[0] = x1 * cb + z1 * sb;
        out3[1] = y1;
        out3[2] = -x1 * sb + z1 * cb;
    }

    /** Umkehrung von {@link #rotateToWorld} (Welt → Kamera-Raum): {@code R^T}. */
    public static void rotateToView(double yawDeg, double pitchDeg, double wx, double wy, double wz, double[] out3) {
        double b = Math.PI - Math.toRadians(yawDeg);
        double cb = Math.cos(b), sb = Math.sin(b);
        double x1 = wx * cb - wz * sb;
        double y1 = wy;
        double z1 = wx * sb + wz * cb;
        double a = -Math.toRadians(pitchDeg);
        double ca = Math.cos(a), sa = Math.sin(a);
        out3[0] = x1;
        out3[1] = y1 * ca + z1 * sa;
        out3[2] = -y1 * sa + z1 * ca;
    }

    /**
     * Strahl in Weltrichtung für einen NDC-Punkt (normiert).
     *
     * @return {@code false}, wenn kein Strahl bestimmt werden kann
     */
    public static boolean worldDirection(double[] inverseProjection, double yawDeg, double pitchDeg, double ndcX, double ndcY,
                                         double[] out3, double[] scratch3) {
        if (!viewDirection(inverseProjection, ndcX, ndcY, out3, scratch3)) return false;
        rotateToWorld(yawDeg, pitchDeg, out3[0], out3[1], out3[2], out3);
        return true;
    }

    /**
     * Weltpunkt relativ zur Kamera ({@code punkt - kamera}) auf NDC projizieren.
     *
     * @return {@code false}, wenn der Punkt hinter der Kamera liegt (nicht sinnvoll auf dem Bildschirm)
     */
    public static boolean projectRelative(double[] projection, double yawDeg, double pitchDeg, double rx, double ry, double rz,
                                          double[] ndcOut3) {
        rotateToView(yawDeg, pitchDeg, rx, ry, rz, ndcOut3);
        double vx = ndcOut3[0], vy = ndcOut3[1], vz = ndcOut3[2];
        double w = projection[3] * vx + projection[7] * vy + projection[11] * vz + projection[15];
        if (!Double.isFinite(w) || w <= EPS) return false;
        return transformProject(projection, vx, vy, vz, ndcOut3);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Strahl gegen Quader (Slab-Test)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Abstand vom Ursprung bis zum Eintritt in den Quader entlang der (normierten) Richtung, oder -1 ohne Treffer bis
     * {@code maxT}. Liegt der Ursprung im Quader, ist der Abstand 0.
     */
    public static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz,
                                double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double maxT) {
        double tMin = 0.0;
        double tMax = maxT;
        // x
        if (Math.abs(dx) < EPS) {
            if (ox < minX || ox > maxX) return -1;
        } else {
            double inv = 1.0 / dx;
            double t1 = (minX - ox) * inv, t2 = (maxX - ox) * inv;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            if (t1 > tMin) tMin = t1;
            if (t2 < tMax) tMax = t2;
            if (tMin > tMax) return -1;
        }
        // y
        if (Math.abs(dy) < EPS) {
            if (oy < minY || oy > maxY) return -1;
        } else {
            double inv = 1.0 / dy;
            double t1 = (minY - oy) * inv, t2 = (maxY - oy) * inv;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            if (t1 > tMin) tMin = t1;
            if (t2 < tMax) tMax = t2;
            if (tMin > tMax) return -1;
        }
        // z
        if (Math.abs(dz) < EPS) {
            if (oz < minZ || oz > maxZ) return -1;
        } else {
            double inv = 1.0 / dz;
            double t1 = (minZ - oz) * inv, t2 = (maxZ - oz) * inv;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            if (t1 > tMin) tMin = t1;
            if (t2 < tMax) tMax = t2;
            if (tMin > tMax) return -1;
        }
        return tMin;
    }
}

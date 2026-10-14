package com.bame.client.gui;

/**
 * Pseudo-3D-Lage des schwebenden Mod-Menü-Panels ("3D GUI"), ohne Minecraft-Abhängigkeiten (testbar im
 * guiGeometryTest).
 *
 * <p>Die GUI von 1.21.11 zeichnet ausschließlich mit 2D-affinen Matrizen ({@code Matrix3x2f}); echte Perspektive ist dort
 * nicht möglich. Die Neigung wird deshalb als sehr kleine Scherung um die Panel-Mitte nachgebildet (der Rand auf der
 * Seite des Mauszeigers kippt leicht nach vorn), dazu kommen Öffnen-Animation (Kippen/Hereinfliegen mit Ease-out) und
 * ein langsames Schweben. Tiefe entsteht vor allem über Schatten, Rim-Light und Parallax, die der Bildschirm zeichnet.
 *
 * <p>Die Matrix ist affin und invertierbar: {@link #toLocal} rechnet Mauskoordinaten exakt in Panel-Koordinaten um.
 * Benutzt wird immer die Matrix des zuletzt gezeichneten Frames, also genau das, was auf dem Bildschirm zu sehen ist.
 *
 * <p>Matrix-Layout (wie JOML {@code Matrix3x2f}): {@code x' = m00*x + m10*y + m20}, {@code y' = m01*x + m11*y + m21}.
 */
public final class Panel3D {
    /** Scherung (y aus x) bei voller horizontaler Neigung: auf 600 px Breite höchstens ±3 px. */
    public static final float SHEAR_Y_MAX = 0.010f;
    /** Scherung (x aus y) bei voller vertikaler Neigung. */
    public static final float SHEAR_X_MAX = 0.006f;
    /** Dämpfung der Neigung (1/s): größer = folgt schneller. */
    public static final float TILT_DAMPING = 5.5f;
    /** Dämpfung, wenn ein Dialog offen ist (1/s). */
    public static final float CALM_DAMPING = 16f;
    /** Öffnen-Animation. */
    public static final float OPEN_SECONDS = 0.42f;
    public static final float OPEN_SCALE_FROM = 0.93f;
    public static final float OPEN_DROP_PX = 22f;
    public static final float OPEN_TIP_SHEAR = 0.035f;
    /** Schweben: Amplitude in GUI-Pixeln und Periode. */
    public static final float FLOAT_AMPLITUDE = 0.6f;
    public static final float FLOAT_PERIOD_SECONDS = 7.0f;

    // Geglätteter Zustand
    private float tiltX, tiltY;       // -1..1, x: Maus rechts > 0, y: Maus unten > 0
    private float floatWeight;        // 0..1, blendet das Schweben weich ein/aus
    private boolean active;

    // Matrix des letzten Frames und ihre Inverse
    private float m00 = 1, m01 = 0, m10 = 0, m11 = 1, m20 = 0, m21 = 0;
    private float i00 = 1, i01 = 0, i10 = 0, i11 = 1, i20 = 0, i21 = 0;

    /** Eingaben eines Frames. */
    public static final class Input {
        public float px, py, pw, ph;    // Panel-Rechteck (lokal = ungeneigt)
        public double mouseX, mouseY;   // Mauszeiger in Bildschirm-Koordinaten
        public float dtSeconds;         // Zeit seit dem letzten Frame
        public float openSeconds;       // Zeit seit dem Öffnen des Panels
        public double timeSeconds;      // laufende Zeit (Schweben)
        public boolean motion = true;   // false = reduzierte Bewegung (keine Animation, keine Neigung)
        public boolean calm;            // true = Neigung/Schweben ausblenden (z. B. Dialog offen)
        public boolean hold;            // true = Neigung festhalten (Maustaste gedrückt: Ziehen bleibt ruhig)
    }

    /** Ease-out (kubisch), 0..1. */
    public static float easeOut(float t) {
        float c = Math.clamp(t, 0f, 1f);
        float inv = 1f - c;
        return 1f - inv * inv * inv;
    }

    /** Fortschritt der Öffnen-Animation (0..1, bereits mit Ease-out), 1 bei reduzierter Bewegung. */
    public static float openProgress(float openSeconds, boolean motion) {
        if (!motion) return 1f;
        return easeOut(openSeconds / OPEN_SECONDS);
    }

    /** Schaltet die Transformation ab (Identität, z. B. 3D GUI aus oder Hub-Ansicht). */
    public void deactivate() {
        active = false;
        lastNanos = 0;
        tiltX = tiltY = 0;
        floatWeight = 0;
        setIdentity();
    }

    public boolean isActive() {
        return active;
    }

    public float tiltX() {
        return tiltX;
    }

    public float tiltY() {
        return tiltY;
    }


    // Zeitmessung für frame()
    private long lastNanos;
    private final Input scratch = new Input();

    /**
     * Bequeme Variante von {@link #update(Input)} für den Render-Thread: misst die Zeit selbst.
     * {@code openStartNanos} ist {@link System#nanoTime()} beim Öffnen des Panels.
     */
    public void frame(float px, float py, float pw, float ph, double mouseX, double mouseY, long openStartNanos,
                      boolean motion, boolean calm, boolean hold) {
        long now = System.nanoTime();
        Input in = scratch;
        in.px = px;
        in.py = py;
        in.pw = pw;
        in.ph = ph;
        in.mouseX = mouseX;
        in.mouseY = mouseY;
        in.dtSeconds = lastNanos == 0 ? 0f : (now - lastNanos) / 1.0e9f;
        in.openSeconds = (now - openStartNanos) / 1.0e9f;
        in.timeSeconds = (now % 3_600_000_000_000L) / 1.0e9;
        in.motion = motion;
        in.calm = calm;
        in.hold = hold;
        lastNanos = now;
        update(in);
    }

    /** Berechnet Neigung (gedämpft) und Matrix für diesen Frame. */
    public void update(Input in) {
        active = true;
        float cx = in.px + in.pw * 0.5f;
        float cy = in.py + in.ph * 0.5f;
        float dt = Float.isFinite(in.dtSeconds) ? Math.clamp(in.dtSeconds, 0f, 0.25f) : 0f;

        float targetX = 0, targetY = 0;
        if (in.motion && !in.calm && in.pw > 0 && in.ph > 0
                && Double.isFinite(in.mouseX) && Double.isFinite(in.mouseY)) {
            targetX = (float) Math.clamp((in.mouseX - cx) / (in.pw * 0.5f), -1.0, 1.0);
            targetY = (float) Math.clamp((in.mouseY - cy) / (in.ph * 0.5f), -1.0, 1.0);
        }
        if (!in.hold || in.calm) {
            // Dialog offen: schnell gerade stellen, damit sein Abdunkeln den ganzen Bildschirm deckt
            float k = 1f - (float) Math.exp(-(in.calm ? CALM_DAMPING : TILT_DAMPING) * dt);
            tiltX += (targetX - tiltX) * k;
            tiltY += (targetY - tiltY) * k;
        }
        float floatTarget = in.motion && !in.calm ? 1f : 0f;
        floatWeight += (floatTarget - floatWeight) * (1f - (float) Math.exp(-(in.calm ? CALM_DAMPING : 2.5f) * dt));
        if (!in.motion) {
            tiltX = tiltY = 0;
            floatWeight = 0;
        }

        float open = openProgress(in.openSeconds, in.motion);
        float floatY = (float) (Math.sin(in.timeSeconds * (2.0 * Math.PI / FLOAT_PERIOD_SECONDS)) * FLOAT_AMPLITUDE * floatWeight);
        compose(cx, cy, tiltX, tiltY, open, floatY);
    }

    /**
     * Setzt die Matrix aus Neigung, Öffnen-Fortschritt und Schwebe-Versatz zusammen:
     * {@code T(cx, cy + drop + floatY) * Scherung * Skalierung * T(-cx, -cy)}.
     */
    public void compose(float cx, float cy, float tx, float ty, float open, float floatY) {
        float closed = 1f - Math.clamp(open, 0f, 1f);
        float scale = OPEN_SCALE_FROM + (1f - OPEN_SCALE_FROM) * (1f - closed);
        // Rechter Rand kippt bei Maus rechts nach oben, unterer Rand bei Maus unten nach rechts (Panel "schaut" zur Maus);
        // beim Öffnen kippt das Panel zusätzlich von unten herein
        float shY = -tx * SHEAR_Y_MAX + closed * OPEN_TIP_SHEAR;
        float shX = -ty * SHEAR_X_MAX;
        float drop = closed * OPEN_DROP_PX + floatY;

        // [1 shX; shY 1] * [scale 0; 0 scale]
        m00 = scale;
        m10 = shX * scale;
        m01 = shY * scale;
        m11 = scale;
        m20 = cx - (m00 * cx + m10 * cy);
        m21 = cy + drop - (m01 * cx + m11 * cy);
        invert();
        active = true;
    }

    private void setIdentity() {
        m00 = 1; m01 = 0; m10 = 0; m11 = 1; m20 = 0; m21 = 0;
        i00 = 1; i01 = 0; i10 = 0; i11 = 1; i20 = 0; i21 = 0;
    }

    private void invert() {
        float det = m00 * m11 - m10 * m01;
        if (Math.abs(det) < 1e-6f) {
            setIdentity();
            return;
        }
        float inv = 1f / det;
        i00 = m11 * inv;
        i01 = -m01 * inv;
        i10 = -m10 * inv;
        i11 = m00 * inv;
        i20 = -(i00 * m20 + i10 * m21);
        i21 = -(i01 * m20 + i11 * m21);
    }

    /** Bildschirm -> Panel-Koordinaten (für Klicks, Hover, Ziehen, Scrollen). Inaktiv: unverändert. */
    public double[] toLocal(double x, double y) {
        if (!active) return new double[]{x, y};
        return new double[]{i00 * x + i10 * y + i20, i01 * x + i11 * y + i21};
    }

    /** Panel -> Bildschirm-Koordinaten. Inaktiv: unverändert. */
    public double[] toScreen(double x, double y) {
        if (!active) return new double[]{x, y};
        return new double[]{m00 * x + m10 * y + m20, m01 * x + m11 * y + m21};
    }

    /** Matrix als {m00, m01, m10, m11, m20, m21}. */
    public float[] matrix() {
        return new float[]{m00, m01, m10, m11, m20, m21};
    }

    /** Mittlerer Skalierungsfaktor (Wurzel der Determinante), z. B. für Modell-Vorschauen. */
    public float uniformScale() {
        return (float) Math.sqrt(Math.abs(m00 * m11 - m10 * m01));
    }

    /** Achsenparalleles Hüllrechteck eines lokalen Rechtecks auf dem Bildschirm: {x1, y1, x2, y2}. */
    public int[] boundsOnScreen(int x1, int y1, int x2, int y2) {
        if (!active) return new int[]{x1, y1, x2, y2};
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        int[][] corners = {{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}};
        for (int[] p : corners) {
            double[] s = toScreen(p[0], p[1]);
            minX = Math.min(minX, s[0]);
            minY = Math.min(minY, s[1]);
            maxX = Math.max(maxX, s[0]);
            maxY = Math.max(maxY, s[1]);
        }
        return new int[]{(int) Math.round(minX), (int) Math.round(minY), (int) Math.round(maxX), (int) Math.round(maxY)};
    }
}

package com.bame.client.killfx;

import java.util.Arrays;

/**
 * Pixel-genaues Spielermodell für den Thanos-Snap (reines Java, ohne Minecraft, testbar): Jeder sichtbare Texel des Skins
 * und der Rüstung wird zu einem eigenen kleinen Viereck, genau so, wie das Vanilla-Modell ihn zeichnen würde (gleiche
 * Quader, gleiche UV-Belegung nach {@code ModelPart.Cuboid}, gleiche Pivot-Punkte und Skalierung). So kann die Figur
 * Pixel für Pixel zerfallen, und jede Flocke trägt die Farbe "ihres" Texels (UV in die Original-Textur, keine
 * Textur-Erzeugung).
 * <p>
 * Koordinaten: Teil-lokal im Modellraum (Blöcke, y nach unten, wie {@code ModelPart}); {@link #partMatrix} rechnet in den
 * Figurenraum (Blöcke, y nach oben, Füße bei 0, um die Körperdrehung gedreht) um.
 */
public final class KillFigureMesh {

    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, PARTS = 6;
    /** Pivot-Punkte der Teile (Pixel), wie in {@code BipedEntityModel.getModelData}. */
    static final float[][] PIVOT = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9F, 12, 0}, {1.9F, 12, 0}};
    /** Skalierung des Spieler-Renderers. */
    public static final float MODEL_SCALE = 0.9375F;
    /** Versatz im {@code LivingEntityRenderer} (Modell-Ursprung über den Füßen). */
    public static final float MODEL_OFFSET_Y = 1.501F;

    /** Sichtbarkeit eines Texels (Alpha > 0). {@code null} = alle sichtbar. */
    public interface Mask {
        boolean opaque(int x, int y);
    }

    // Texel (Struktur aus Feldern)
    int count;
    int[] part = new int[0];
    int[] slot = new int[0];
    /** 4 Ecken x (x, y, z), Teil-lokal (Blöcke). */
    float[] pos = new float[0];
    /** 4 Ecken x (u, v), normiert. */
    float[] uv = new float[0];
    /** Normale (Teil-lokal). */
    float[] normal = new float[0];
    /** Mitte (Teil-lokal). */
    float[] center = new float[0];
    long[] seed = new long[0];
    /** Schwelle der Zerfallsgrenze (Blöcke) und Zeitpunkt des Zerfalls (ms). */
    float[] threshold = new float[0];
    long[] birthMs = new long[0];
    boolean[] flake = new boolean[0];
    /** Höhe der Mitte über dem Boden in der Ausgangspose (Blöcke). */
    float[] height = new float[0];

    private int face;

    public int size() {
        return count;
    }

    public int part(int i) {
        return part[i];
    }

    public int slot(int i) {
        return slot[i];
    }

    public float threshold(int i) {
        return threshold[i];
    }

    public long birthMs(int i) {
        return birthMs[i];
    }

    public boolean flake(int i) {
        return flake[i];
    }

    public float height(int i) {
        return height[i];
    }

    public long seed(int i) {
        return seed[i];
    }

    private void ensure(int n) {
        if (n <= part.length) return;
        int cap = Math.max(n, part.length * 2 + 64);
        part = Arrays.copyOf(part, cap);
        slot = Arrays.copyOf(slot, cap);
        pos = Arrays.copyOf(pos, cap * 12);
        uv = Arrays.copyOf(uv, cap * 8);
        normal = Arrays.copyOf(normal, cap * 3);
        center = Arrays.copyOf(center, cap * 3);
        seed = Arrays.copyOf(seed, cap);
        threshold = Arrays.copyOf(threshold, cap);
        birthMs = Arrays.copyOf(birthMs, cap);
        flake = Arrays.copyOf(flake, cap);
        height = Arrays.copyOf(height, cap);
    }

    // ==========================================
    // Aufbau
    // ==========================================

    /** Skin (64 x 64) wie {@code PlayerEntityModel.getTexturedModelData}: Grundschicht und zweite Schicht. */
    public void addSkin(int slotIndex, boolean slim, Mask mask) {
        int armW = slim ? 3 : 4;
        addBox(HEAD, slotIndex, 0, 0, -4, -8, -4, 8, 8, 8, 0.0F, false, 64, 64, mask);
        addBox(HEAD, slotIndex, 32, 0, -4, -8, -4, 8, 8, 8, 0.5F, false, 64, 64, mask);
        addBox(BODY, slotIndex, 16, 16, -4, 0, -2, 8, 12, 4, 0.0F, false, 64, 64, mask);
        addBox(BODY, slotIndex, 16, 32, -4, 0, -2, 8, 12, 4, 0.25F, false, 64, 64, mask);
        float rx = slim ? -2 : -3;
        addBox(RIGHT_ARM, slotIndex, 40, 16, rx, -2, -2, armW, 12, 4, 0.0F, false, 64, 64, mask);
        addBox(RIGHT_ARM, slotIndex, 40, 32, rx, -2, -2, armW, 12, 4, 0.25F, false, 64, 64, mask);
        addBox(LEFT_ARM, slotIndex, 32, 48, -1, -2, -2, armW, 12, 4, 0.0F, false, 64, 64, mask);
        addBox(LEFT_ARM, slotIndex, 48, 48, -1, -2, -2, armW, 12, 4, 0.25F, false, 64, 64, mask);
        addBox(RIGHT_LEG, slotIndex, 0, 16, -2, 0, -2, 4, 12, 4, 0.0F, false, 64, 64, mask);
        addBox(RIGHT_LEG, slotIndex, 0, 32, -2, 0, -2, 4, 12, 4, 0.25F, false, 64, 64, mask);
        addBox(LEFT_LEG, slotIndex, 16, 48, -2, 0, -2, 4, 12, 4, 0.0F, false, 64, 64, mask);
        addBox(LEFT_LEG, slotIndex, 0, 48, -2, 0, -2, 4, 12, 4, 0.25F, false, 64, 64, mask);
    }

    /**
     * Eine Rüstungsschicht (64 x 32) wie {@code BipedEntityModel.createEquipmentModelData} mit den Spieler-Dilatationen
     * (Rüstung 1,0, Hose 0,5; Beine jeweils 0,1 schmaler).
     *
     * @param armorSlot 0 = Kopf, 1 = Brust, 2 = Beine, 3 = Füße
     */
    public void addArmor(int slotIndex, int armorSlot, Mask mask) {
        switch (armorSlot) {
            case 0 -> {
                addBox(HEAD, slotIndex, 0, 0, -4, -8, -4, 8, 8, 8, 1.0F, false, 64, 32, mask);
                addBox(HEAD, slotIndex, 32, 0, -4, -8, -4, 8, 8, 8, 1.5F, false, 64, 32, mask);
            }
            case 1 -> {
                addBox(BODY, slotIndex, 16, 16, -4, 0, -2, 8, 12, 4, 1.0F, false, 64, 32, mask);
                addBox(RIGHT_ARM, slotIndex, 40, 16, -3, -2, -2, 4, 12, 4, 1.0F, false, 64, 32, mask);
                addBox(LEFT_ARM, slotIndex, 40, 16, -1, -2, -2, 4, 12, 4, 1.0F, true, 64, 32, mask);
            }
            case 2 -> {
                addBox(BODY, slotIndex, 16, 16, -4, 0, -2, 8, 12, 4, 0.5F, false, 64, 32, mask);
                addBox(RIGHT_LEG, slotIndex, 0, 16, -2, 0, -2, 4, 12, 4, 0.4F, false, 64, 32, mask);
                addBox(LEFT_LEG, slotIndex, 0, 16, -2, 0, -2, 4, 12, 4, 0.4F, true, 64, 32, mask);
            }
            case 3 -> {
                addBox(RIGHT_LEG, slotIndex, 0, 16, -2, 0, -2, 4, 12, 4, 0.9F, false, 64, 32, mask);
                addBox(LEFT_LEG, slotIndex, 0, 16, -2, 0, -2, 4, 12, 4, 0.9F, true, 64, 32, mask);
            }
            default -> { }
        }
    }

    /** Ein Quader, nachgebaut nach {@code ModelPart.Cuboid}, in Texel-Vierecke zerlegt (unsichtbare Texel entfallen). */
    void addBox(int partIndex, int slotIndex, int u, int v, float x, float y, float z, float sx, float sy, float sz,
                float dil, boolean mirror, int texW, int texH, Mask mask) {
        float f = x + sx, g = y + sy, h = z + sz;
        x -= dil;
        y -= dil;
        z -= dil;
        f += dil;
        g += dil;
        h += dil;
        if (mirror) {
            float t = f;
            f = x;
            x = t;
        }
        float[] v1 = {x, y, z}, v2 = {f, y, z}, v3 = {f, g, z}, v4 = {x, g, z};
        float[] v5 = {x, y, h}, v6 = {f, y, h}, v7 = {f, g, h}, v8 = {x, g, h};
        float j = u, k = u + sz, l = u + sz + sx, m = u + sz + sx + sx, n = u + sz + sx + sz, o = u + sz + sx + sz + sx;
        float p = v, q = v + sz, r = v + sz + sy;
        float nx = mirror ? -1.0F : 1.0F;
        face(partIndex, slotIndex, new float[][]{v6, v5, v1, v2}, k, p, l, q, 0, -1, 0, texW, texH, mask);   // DOWN
        face(partIndex, slotIndex, new float[][]{v3, v4, v8, v7}, l, q, m, p, 0, 1, 0, texW, texH, mask);    // UP
        face(partIndex, slotIndex, new float[][]{v1, v5, v8, v4}, j, q, k, r, -nx, 0, 0, texW, texH, mask);  // WEST
        face(partIndex, slotIndex, new float[][]{v2, v1, v4, v3}, k, q, l, r, 0, 0, -1, texW, texH, mask);   // NORTH
        face(partIndex, slotIndex, new float[][]{v6, v2, v3, v7}, l, q, n, r, nx, 0, 0, texW, texH, mask);   // EAST
        face(partIndex, slotIndex, new float[][]{v5, v6, v7, v8}, n, q, o, r, 0, 0, 1, texW, texH, mask);    // SOUTH
    }

    /**
     * Eine Seite: Vanilla ordnet den Ecken die UVs (u2, v1), (u1, v1), (u1, v2), (u2, v2) zu. Die Seite wird bilinear in
     * |u2-u1| x |v2-v1| Texel zerlegt.
     */
    private void face(int partIndex, int slotIndex, float[][] c, float u1, float v1, float u2, float v2,
                      float nx, float ny, float nz, int texW, int texH, Mask mask) {
        int cols = Math.round(Math.abs(u2 - u1));
        int rows = Math.round(Math.abs(v2 - v1));
        face++;
        if (cols <= 0 || rows <= 0) return;
        // P(a, b): a entlang u (u1 -> u2), b entlang v (v1 -> v2); Ecke (0,0) = c[1], (1,0) = c[0], (0,1) = c[2], (1,1) = c[3]
        for (int jy = 0; jy < rows; jy++) {
            for (int ix = 0; ix < cols; ix++) {
                float a0 = ix / (float) cols, a1 = (ix + 1) / (float) cols;
                float b0 = jy / (float) rows, b1 = (jy + 1) / (float) rows;
                float uc = u1 + (a0 + a1) * 0.5F * (u2 - u1);
                float vc = v1 + (b0 + b1) * 0.5F * (v2 - v1);
                int tx = (int) Math.floor(uc), ty = (int) Math.floor(vc);
                if (tx < 0 || ty < 0 || tx >= texW || ty >= texH) continue;
                if (mask != null && !mask.opaque(tx, ty)) continue;
                int i = count;
                ensure(i + 1);
                part[i] = partIndex;
                slot[i] = slotIndex;
                float[][] corners = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
                float cx = 0, cy = 0, cz = 0;
                for (int e = 0; e < 4; e++) {
                    float a = corners[e][0], b = corners[e][1];
                    float w00 = (1 - a) * (1 - b), w10 = a * (1 - b), w01 = (1 - a) * b, w11 = a * b;
                    for (int d = 0; d < 3; d++) {
                        float val = (w00 * c[1][d] + w10 * c[0][d] + w01 * c[2][d] + w11 * c[3][d]) / 16.0F;
                        pos[i * 12 + e * 3 + d] = val;
                    }
                    cx += pos[i * 12 + e * 3];
                    cy += pos[i * 12 + e * 3 + 1];
                    cz += pos[i * 12 + e * 3 + 2];
                    uv[i * 8 + e * 2] = (u1 + a * (u2 - u1)) / texW;
                    uv[i * 8 + e * 2 + 1] = (v1 + b * (v2 - v1)) / texH;
                }
                center[i * 3] = cx / 4.0F;
                center[i * 3 + 1] = cy / 4.0F;
                center[i * 3 + 2] = cz / 4.0F;
                normal[i * 3] = nx;
                normal[i * 3 + 1] = ny;
                normal[i * 3 + 2] = nz;
                seed[i] = mix(((long) slotIndex << 40) ^ ((long) partIndex << 32) ^ ((long) face << 16) ^ (jy * 64L + ix));
                count++;
            }
        }
    }

    // ==========================================
    // Zerfall
    // ==========================================

    /**
     * Berechnet Schwellen, Zerfallszeiten und Flocken aus der Ausgangspose.
     *
     * @param pose     Ausgangspose (pitch, yaw, roll je Teil, Bogenmaß)
     * @param bodyYaw  Körperdrehung (Grad, wie {@code bodyYaw} im Render-Zustand)
     * @param windX    Windrichtung (Welt, waagerecht, normiert)
     * @param flakeFraction Anteil der Texel, die zu Flocken werden (0..1); höchstens {@link KillFxTimeline#SNAP_MAX_FLAKES}
     */
    public void prepare(float[][] pose, float bodyYaw, float windX, float windZ, float flakeFraction) {
        float[][] mats = new float[PARTS][12];
        for (int p = 0; p < PARTS; p++) partMatrix(p, pose[p], bodyYaw, mats[p]);
        float[] w = new float[3];
        int flakes = 0;
        for (int i = 0; i < count; i++) {
            transform(mats[part[i]], center[i * 3], center[i * 3 + 1], center[i * 3 + 2], w);
            height[i] = w[1];
            // Ausgefranste Kante: räumlich zusammenhängendes Rauschen plus etwas Körnung pro Texel
            float fray = (valueNoise3(w[0] * 7.0F, w[1] * 5.5F, w[2] * 7.0F) - 0.5F) * 0.8F
                    + (KillFxTimeline.unit(seed[i], 7) - 0.5F) * 0.2F;
            // windabgewandte Seite (Wind weht von dort weg) zerfällt etwas früher
            float lee = (w[0] * windX + w[2] * windZ) / 0.3F;
            threshold[i] = KillFxTimeline.snapThreshold(height[i], fray, lee);
            birthMs[i] = KillFxTimeline.snapBirthMs(threshold[i]);
            flake[i] = KillFxTimeline.unit(seed[i], 8) < flakeFraction;
            if (flake[i]) flakes++;
        }
        // Obergrenze für Flocken: gleichmäßig ausdünnen
        if (flakes > KillFxTimeline.SNAP_MAX_FLAKES) {
            float keep = KillFxTimeline.SNAP_MAX_FLAKES / (float) flakes;
            int kept = 0;
            for (int i = 0; i < count; i++) {
                if (!flake[i]) continue;
                if (kept >= KillFxTimeline.SNAP_MAX_FLAKES || KillFxTimeline.unit(seed[i], 9) >= keep) {
                    flake[i] = false;
                } else {
                    kept++;
                }
            }
        }
    }

    public int flakeCount() {
        int n = 0;
        for (int i = 0; i < count; i++) if (flake[i]) n++;
        return n;
    }

    /** Ist Texel {@code i} zum Zeitpunkt {@code ms} schon zerfallen? */
    public boolean dissolved(int i, long ms) {
        return KillFxTimeline.snapBoundary(ms) >= threshold[i];
    }

    // ==========================================
    // Pose und Transformation
    // ==========================================

    /** Ausgangspose des Snaps: ruhig stehend, Arme leicht abgespreizt (wie die Vanilla-Ruhepose). */
    public static float[][] restPose() {
        float[][] p = new float[PARTS][3];
        p[RIGHT_ARM][2] = 0.07F;
        p[LEFT_ARM][2] = -0.07F;
        return p;
    }

    /** Pose beim Zusammensacken ({@code slump} 0..1): Kopf sinkt nach vorne, Arme hängen schlaff und etwas nach vorne. */
    public static float[][] slumpPose(float slump) {
        float[][] p = restPose();
        p[HEAD][0] = 0.42F * slump;
        p[RIGHT_ARM][0] = -0.08F * slump;
        p[LEFT_ARM][0] = -0.06F * slump;
        p[RIGHT_ARM][2] = 0.07F - 0.05F * slump;
        p[LEFT_ARM][2] = -0.07F + 0.05F * slump;
        return p;
    }

    /**
     * Matrix (3 x 3 zeilenweise, dann Verschiebung) vom Teil-lokalen Modellraum in den Figurenraum: Teil-Drehung
     * ({@code rotationZYX}), Pivot, Versatz 1,501, Spiegelung (-1, -1, 1), Skalierung 0,9375, Drehung um 180 - bodyYaw.
     */
    public static void partMatrix(int partIndex, float[] rot, float bodyYaw, float[] out) {
        float pitch = rot[0], yaw = rot[1], roll = rot[2];
        float cx = (float) Math.cos(pitch), sx = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cz = (float) Math.cos(roll), sz = (float) Math.sin(roll);
        // R = Rz * Ry * Rx
        float r00 = cz * cy, r01 = cz * sy * sx - sz * cx, r02 = cz * sy * cx + sz * sx;
        float r10 = sz * cy, r11 = sz * sy * sx + cz * cx, r12 = sz * sy * cx - cz * sx;
        float r20 = -sy, r21 = cy * sx, r22 = cy * cx;
        float s = MODEL_SCALE;
        // D = diag(-s, -s, s)
        float d00 = -s * r00, d01 = -s * r01, d02 = -s * r02;
        float d10 = -s * r10, d11 = -s * r11, d12 = -s * r12;
        float d20 = s * r20, d21 = s * r21, d22 = s * r22;
        float px = PIVOT[partIndex][0] / 16.0F, py = PIVOT[partIndex][1] / 16.0F - MODEL_OFFSET_Y, pz = PIVOT[partIndex][2] / 16.0F;
        float tx = -s * px, ty = -s * py, tz = s * pz;
        double th = Math.toRadians(180.0F - bodyYaw);
        float c = (float) Math.cos(th), sn = (float) Math.sin(th);
        // Y-Drehung: x' = x c + z s, z' = -x s + z c
        out[0] = c * d00 + sn * d20;
        out[1] = c * d01 + sn * d21;
        out[2] = c * d02 + sn * d22;
        out[3] = d10;
        out[4] = d11;
        out[5] = d12;
        out[6] = -sn * d00 + c * d20;
        out[7] = -sn * d01 + c * d21;
        out[8] = -sn * d02 + c * d22;
        out[9] = c * tx + sn * tz;
        out[10] = ty;
        out[11] = -sn * tx + c * tz;
    }

    public static void transform(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z + m[9];
        out[1] = m[3] * x + m[4] * y + m[5] * z + m[10];
        out[2] = m[6] * x + m[7] * y + m[8] * z + m[11];
    }

    /** Richtung (ohne Verschiebung), normiert. */
    public static void rotate(float[] m, float x, float y, float z, float[] out) {
        float ox = m[0] * x + m[1] * y + m[2] * z;
        float oy = m[3] * x + m[4] * y + m[5] * z;
        float oz = m[6] * x + m[7] * y + m[8] * z;
        float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (len < 1.0E-6F) len = 1.0F;
        out[0] = ox / len;
        out[1] = oy / len;
        out[2] = oz / len;
    }

    /** Ecke {@code corner} (0..3) von Texel {@code i} im Figurenraum. */
    public void corner(int i, int corner, float[] m, float[] out) {
        int b = i * 12 + corner * 3;
        transform(m, pos[b], pos[b + 1], pos[b + 2], out);
    }

    public void centerOf(int i, float[] m, float[] out) {
        transform(m, center[i * 3], center[i * 3 + 1], center[i * 3 + 2], out);
    }

    public void normalOf(int i, float[] m, float[] out) {
        rotate(m, normal[i * 3], normal[i * 3 + 1], normal[i * 3 + 2], out);
    }

    public float u(int i, int corner) {
        return uv[i * 8 + corner * 2];
    }

    public float v(int i, int corner) {
        return uv[i * 8 + corner * 2 + 1];
    }

    // ==========================================
    // Rauschen
    // ==========================================

    static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    private static float lattice(int x, int y, int z) {
        long h = mix(x * 73856093L ^ y * 19349663L ^ z * 83492791L);
        return ((h >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Glattes Wertrauschen 0..1. */
    static float valueNoise3(float x, float y, float z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float fx = x - x0, fy = y - y0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float c000 = lattice(x0, y0, z0), c100 = lattice(x0 + 1, y0, z0);
        float c010 = lattice(x0, y0 + 1, z0), c110 = lattice(x0 + 1, y0 + 1, z0);
        float c001 = lattice(x0, y0, z0 + 1), c101 = lattice(x0 + 1, y0, z0 + 1);
        float c011 = lattice(x0, y0 + 1, z0 + 1), c111 = lattice(x0 + 1, y0 + 1, z0 + 1);
        float x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx;
        float x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
        float y0v = x00 + (x10 - x00) * fy, y1v = x01 + (x11 - x01) * fy;
        return y0v + (y1v - y0v) * fz;
    }
}

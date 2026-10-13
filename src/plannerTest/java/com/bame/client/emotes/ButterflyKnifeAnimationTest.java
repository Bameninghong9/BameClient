package com.bame.client.emotes;

/**
 * Numerische Pruefung der Butterfly-Routine ohne Minecraft: rechnet dieselbe Transformationskette wie der Renderer
 * (Wurfversatz -> rechter Arm -> ButterflyKnifeAnimation.applyKnifeTransform -> Skalierung -> Teilrotation) und prueft
 * 1) Stetigkeit (keine Spruenge pro Zeitschritt, an Segmentgrenzen und an der Schleifennaht),
 * 2) dass das Messer weder Kopf noch Koerper schneidet (breite und schmale Arme).
 */
public class ButterflyKnifeAnimationTest {

    private static final float P = ButterflyKnifeAnimation.PERIOD;

    public static void main(String[] args) {
        checkStepContinuity(false);
        checkBoundaries();
        checkClearance(false);
        checkClearance(true);
        System.out.println("BUTTERFLY KNIFE ANIMATION TESTS PASSED");
    }

    // ---------------- Kette ----------------

    /** 4x4-Matrix (Spaltenvektoren, row-major gespeichert), Nachmultiplikation wie MatrixStack. */
    static final class Mat implements ButterflyKnifeAnimation.Xform {
        double[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

        Mat copy() { Mat c = new Mat(); c.m = m.clone(); return c; }

        void mul(double[] b) {
            double[] r = new double[16];
            for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += m[i * 4 + k] * b[k * 4 + j];
                r[i * 4 + j] = s;
            }
            m = r;
        }

        @Override public void translate(float x, float y, float z) { mul(new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1}); }
        void scale(double s) { mul(new double[]{s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, 0, 0, 0, 1}); }
        @Override public void rotateX(float deg) {
            double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
            mul(new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1});
        }
        @Override public void rotateY(float deg) {
            double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
            mul(new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1});
        }
        @Override public void rotateZ(float deg) {
            double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
            mul(new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        }

        double[] apply(double x, double y, double z) {
            return new double[]{
                    m[0] * x + m[1] * y + m[2] * z + m[3],
                    m[4] * x + m[5] * y + m[6] * z + m[7],
                    m[8] * x + m[9] * y + m[10] * z + m[11]};
        }
    }

    /** Messer-Wurzel in Modell-Pixeln (wie pushKnifeRoot + Skalierung im Renderer, nur ohne /16). */
    static Mat knifeRoot(ButterflyKnifeAnimation.Pose p, boolean slim) {
        Mat mat = new Mat();
        mat.translate(p.tossX, p.tossY, p.tossZ);
        mat.translate(-5.0F, slim ? 2.5F : 2.0F, 0.0F);
        mat.rotateZ((float) Math.toDegrees(p.rightArmRoll));
        mat.rotateY((float) Math.toDegrees(p.rightArmYaw));
        mat.rotateX((float) Math.toDegrees(p.rightArmPitch));
        ButterflyKnifeAnimation.applyKnifeTransform(p, slim, mat);
        mat.scale(ButterflyKnifeAnimation.KNIFE_SCALE / ButterflyKnifeAnimation.UNITS_PER_PIXEL);
        return mat;
    }

    // Stuetzpunkte (Modell-Einheiten) entlang Klinge und Griffen
    static final double[][] BLADE_PTS = {
            {0, 0, -8}, {0, -4, -16}, {0, 4, -16}, {0, -4, -30}, {0, 4, -30}, {0, -2, -37}, {0, 4, -37},
            {0, 0, -41}, {0, 4, -41}, {0, 2.5, -45}};
    static final double[][] HANDLE_PTS = {
            {2.75, -5, -6}, {2.75, 5, -6}, {2.75, -5, 15}, {2.75, 5, 15}, {2.75, -5, 30}, {2.75, 5, 30},
            {2.75, -5.5, 47}, {2.75, 5.5, 47}};

    /** Alle Stuetzpunkte des Messers in Modell-Pixeln. */
    static double[][] knifePoints(ButterflyKnifeAnimation.Pose p, boolean slim) {
        Mat root = knifeRoot(p, slim);
        double[][] out = new double[BLADE_PTS.length + 2 * HANDLE_PTS.length][];
        int n = 0;
        Mat blade = root.copy(); blade.rotateX(p.bladeAngle);
        for (double[] q : BLADE_PTS) out[n++] = blade.apply(q[0], q[1], q[2]);
        Mat safe = root.copy(); safe.rotateX(p.safeAngle);
        for (double[] q : HANDLE_PTS) out[n++] = safe.apply(q[0], q[1], q[2]);
        Mat bite = root.copy(); bite.rotateX(p.biteAngle);
        for (double[] q : HANDLE_PTS) out[n++] = bite.apply(-q[0], q[1], q[2]);
        return out;
    }

    static double[] angles(ButterflyKnifeAnimation.Pose p) {
        return new double[]{p.rightArmPitch, p.rightArmYaw, p.rightArmRoll, p.leftArmPitch, p.leftArmYaw, p.leftArmRoll,
                p.headPitch, p.headYaw, p.headRoll, p.bodyPitch, p.bodyYaw, p.bodyRoll};
    }

    static ButterflyKnifeAnimation.Pose sample(double t) {
        ButterflyKnifeAnimation.Pose p = new ButterflyKnifeAnimation.Pose();
        ButterflyKnifeAnimation.sample((float) t, p);
        return p;
    }

    /** Groesster Abstand zweier Abtastungen: {Punkte in px, Winkel in Grad}. */
    static double[] diff(ButterflyKnifeAnimation.Pose a, ButterflyKnifeAnimation.Pose b) {
        double[][] pa = knifePoints(a, false), pb = knifePoints(b, false);
        double dp = 0;
        for (int i = 0; i < pa.length; i++) {
            double dx = pa[i][0] - pb[i][0], dy = pa[i][1] - pb[i][1], dz = pa[i][2] - pb[i][2];
            dp = Math.max(dp, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        double[] aa = angles(a), ab = angles(b);
        double da = 0;
        for (int i = 0; i < aa.length; i++) da = Math.max(da, Math.toDegrees(Math.abs(aa[i] - ab[i])));
        return new double[]{dp, da};
    }

    // ---------------- Pruefungen ----------------

    static void checkStepContinuity(boolean verbose) {
        double dt = 0.0002;
        double maxP = 0, maxA = 0, atP = 0, atA = 0;
        ButterflyKnifeAnimation.Pose prev = sample(1.0);
        for (double t = 1.0 + dt; t <= 1.0 + P + 0.5; t += dt) {
            ButterflyKnifeAnimation.Pose cur = sample(t);
            double[] d = diff(prev, cur);
            if (d[0] > maxP) { maxP = d[0]; atP = t % P; }
            if (d[1] > maxA) { maxA = d[1]; atA = t % P; }
            prev = cur;
        }
        System.out.printf("step continuity (dt=%.4f s): max knife point step %.4f px at u=%.3f, max angle step %.4f deg at u=%.3f%n",
                dt, maxP, atP, maxA, atA);
        // 0.15 px pro 0.2 ms entspricht 750 px/s - deutlich ueber der schnellsten echten Bewegung (~250 px/s)
        check(maxP < 0.15, "knife jumps by " + maxP + " px at u=" + atP);
        check(maxA < 0.3, "pose angle jumps by " + maxA + " deg at u=" + atA);
    }

    static void checkBoundaries() {
        double eps = 2e-5;
        double worstP = 0, worstA = 0;
        for (float b : ButterflyKnifeAnimation.BOUNDARIES) {
            double t = P + b; // nach dem Einblenden; b = 0 prueft die Schleifennaht (P - eps vs. P + eps)
            double[] d = diff(sample(t - eps), sample(t + eps));
            worstP = Math.max(worstP, d[0]);
            worstA = Math.max(worstA, d[1]);
            check(d[0] < 0.02 && d[1] < 0.05, String.format("discontinuity at u=%.3f: %.4f px / %.4f deg", b, d[0], d[1]));
        }
        System.out.printf("segment boundaries + loop seam (%d checks, +-%.0e s): max %.5f px / %.5f deg%n",
                ButterflyKnifeAnimation.BOUNDARIES.length, eps, worstP, worstA);
    }

    static void checkClearance(boolean slim) {
        double minHead = Double.MAX_VALUE, minBody = Double.MAX_VALUE, atHead = 0, atBody = 0;
        for (double t = ButterflyKnifeAnimation.BLEND_IN; t < ButterflyKnifeAnimation.BLEND_IN + P; t += 0.002) {
            ButterflyKnifeAnimation.Pose p = sample(t);
            for (double[] q : knifePoints(p, slim)) {
                double h = boxDistance(toLocal(q, p.headPitch, p.headYaw, p.headRoll), -4, -8, -4, 4, 0, 4);
                double b = boxDistance(toLocal(q, p.bodyPitch, p.bodyYaw, p.bodyRoll), -4, 0, -2, 4, 12, 2);
                if (h < minHead) { minHead = h; atHead = t % P; }
                if (b < minBody) { minBody = b; atBody = t % P; }
            }
        }
        System.out.printf("clearance (%s arms): head %.2f px (u=%.2f), body %.2f px (u=%.2f)%n",
                slim ? "slim" : "wide", minHead, atHead, minBody, atBody);
        check(minHead > 0.3, "knife clips the head at u=" + atHead + " (" + minHead + ")");
        check(minBody > 0.3, "knife clips the body at u=" + atBody + " (" + minBody + ")");
    }

    /** Modellpunkt in das Lokalsystem eines Teils mit Pivot im Ursprung und Rotation Z*Y*X (inverse Rotation). */
    static double[] toLocal(double[] q, float pitch, float yaw, float roll) {
        double x = q[0], y = q[1], z = q[2];
        double c = Math.cos(-roll), s = Math.sin(-roll);
        double x1 = x * c - y * s, y1 = x * s + y * c;
        c = Math.cos(-yaw); s = Math.sin(-yaw);
        double x2 = x1 * c + z * s, z2 = -x1 * s + z * c;
        c = Math.cos(-pitch); s = Math.sin(-pitch);
        double y3 = y1 * c - z2 * s, z3 = y1 * s + z2 * c;
        return new double[]{x2, y3, z3};
    }

    /** Abstand eines Punktes zu einer Box (negativ = innen). */
    static double boxDistance(double[] p, double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = Math.max(x0 - p[0], p[0] - x1), dy = Math.max(y0 - p[1], p[1] - y1), dz = Math.max(z0 - p[2], p[2] - z1);
        double ox = Math.max(dx, 0), oy = Math.max(dy, 0), oz = Math.max(dz, 0);
        double outside = Math.sqrt(ox * ox + oy * oy + oz * oz);
        return outside > 0 ? outside : Math.max(dx, Math.max(dy, dz));
    }

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}

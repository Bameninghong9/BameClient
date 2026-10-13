package com.bame.client.emotes;

/**
 * Geometrie, Platzprüfung und Ameisen des Picknick-Emotes (ohne Minecraft-Klassen, im swingPicnicTest geprüft).
 * <p>
 * Koordinaten im <b>Picknick-Rahmen</b> P (Blöcke, gleiche Achsen wie {@link HammockLogic}): Ursprung an den Füßen des
 * Spielers beim Start (Boden), +Y oben, +X nach rechts, +Z nach hinten bezogen auf seinen Körper-Yaw beim Start. Der
 * Spieler sitzt im Schneidersitz in der Mitte der Decke, der Korb steht rechts vor ihm, die Flasche links vor ihm,
 * Serviette mit Apfel links neben ihm.
 * <p>
 * Ameisen: Alle {@link #ANT_SLOT} s ein Zeitfenster, in dem genau eine Ameise (Start im Fenster zufällig, Abstand also
 * 10–20 s) auf einer Kurve quer über den vorderen Teil der Decke krabbelt und am Rand verschwindet. Fällt die Mitte ihres
 * Wegs in die ruhige Phase der Schleife, schnippt der Spieler sie weg. Alles deterministisch aus UUID und gemeinsamer
 * Zeitbasis ({@link CampfireLogic#seed}), daher auf allen Clients gleich.
 */
public final class PicnicLogic {

    private PicnicLogic() {}

    public static final double PX = 1.0 / 16.0;

    // ------------------------------------------------------------------
    // Decke und Sachen (Blöcke)
    // ------------------------------------------------------------------

    /** Mitte der Decke (z etwas vor dem Spieler, damit die gekreuzten Beine darauf liegen) und halbe Kantenlänge. */
    public static final double BLANKET_CX = 0.0, BLANKET_CZ = -0.12, BLANKET_HALF = 13.0 * PX;
    /** Oberseite der Decke über dem Boden. */
    public static final double BLANKET_TOP = 0.25 * PX;

    /** Korb: Mitte (Boden), halbe Breite (x), halbe Länge (z), Höhe des Korbs ohne Henkel. */
    public static final double BASKET_X = 0.53, BASKET_Z = -0.28;
    public static final double BASKET_HALF_X = 2.6 * PX, BASKET_HALF_Z = 3.0 * PX, BASKET_H = 4.2 * PX;
    /** Henkel: Oberkante über dem Boden. */
    public static final double BASKET_HANDLE_TOP = 6.4 * PX;

    /** Flasche: Standplatz (Boden) und Griffhöhe (Mitte des Flaschenkörpers über dem Boden). */
    public static final double BOTTLE_X = -0.30, BOTTLE_Z = -0.28, BOTTLE_GRIP_Y = 1.6 * PX;
    /** Serviette mit Apfel. */
    public static final double NAPKIN_X = -0.52, NAPKIN_Z = 0.22;

    // ------------------------------------------------------------------
    // Platzprüfung
    // ------------------------------------------------------------------

    private static final double[] GRID = {-0.75, 0.0, 0.75};

    /**
     * Prüfpunkte im Rahmen {x, y, z, needsGround}: ein 3×3-Raster über die Decke auf Fußhöhe (Luft, darunter ein fester
     * Block = flach, keine Wand) und Korb/Flasche auf halber Höhe.
     */
    public static double[][] checkPointsFrame() {
        double[][] out = new double[GRID.length * GRID.length + 2][];
        int k = 0;
        for (double gx : GRID) {
            for (double gz : GRID) out[k++] = new double[]{BLANKET_CX + gx, 0.05, BLANKET_CZ + gz, 1.0};
        }
        out[k++] = new double[]{BASKET_X, 0.3, BASKET_Z, 0.0};
        out[k] = new double[]{BOTTLE_X, 0.3, BOTTLE_Z, 0.0};
        return out;
    }

    public static double[][] checkPoints(double x, double y, double z, float yaw) {
        double[][] f = checkPointsFrame();
        double[][] out = new double[f.length][];
        for (int i = 0; i < f.length; i++) {
            double[] w = HammockLogic.frameToWorld(f[i][0], f[i][1], f[i][2], x, y, z, yaw);
            out[i] = new double[]{w[0], w[1], w[2], f[i][3]};
        }
        return out;
    }

    /** Liegt (x, z) auf der Decke (mit Rand {@code inset})? */
    public static boolean onBlanket(double x, double z, double inset) {
        return Math.abs(x - BLANKET_CX) <= BLANKET_HALF - inset && Math.abs(z - BLANKET_CZ) <= BLANKET_HALF - inset;
    }

    // ------------------------------------------------------------------
    // Ameisen
    // ------------------------------------------------------------------

    /** Erste Ameise frühestens ab dieser Emote-Zeit; je Zeitfenster eine Ameise, Start im Fenster zufällig (s). */
    public static final float ANT_FIRST = 8.0F, ANT_SLOT = 15.0F, ANT_JITTER = 5.0F;
    /** Dauer des Wegs (s). */
    public static final float ANT_DUR_MIN = 4.5F, ANT_DUR_SPREAD = 1.2F;
    /** Weg im vorderen Streifen der Decke: x von Rand zu Rand, z in diesem Bereich. */
    public static final double ANT_X = 0.78, ANT_Z_NEAR = -0.6, ANT_Z_FAR = -0.9;
    /** Weggeschnippt: Flug bis zum Verschwinden (s), Weite und Höhe (Blöcke). */
    public static final float FLICK_FLY = 0.45F;
    public static final double FLICK_DIST = 0.45, FLICK_HEIGHT = 0.28;
    /** Der Spieler schnippt bei diesem Anteil des Wegs. */
    public static final float FLICK_AT = 0.55F;

    /** Eine Ameise: Startzeit, Dauer, Schnipp-Zeit (oder NaN) und quadratische Bézier-Kurve (x0,z0)-(cx,cz)-(x1,z1). */
    public record Ant(int index, float start, float duration, float flickAt,
                      double x0, double z0, double cx, double cz, double x1, double z1) {

        public boolean flicked() {
            return !Float.isNaN(flickAt);
        }

        /** Ende der Sichtbarkeit (Rand erreicht bzw. Flug zu Ende). */
        public float end() {
            return flicked() ? flickAt + FLICK_FLY : start + duration;
        }
    }

    /** Ameise im Zeitfenster {@code k} (k ≥ 0). */
    public static Ant ant(String id, long seedMs, int k) {
        double u0 = rnd(id, seedMs, k, 0), u1 = rnd(id, seedMs, k, 1), u2 = rnd(id, seedMs, k, 2);
        double u3 = rnd(id, seedMs, k, 3), u4 = rnd(id, seedMs, k, 4), u5 = rnd(id, seedMs, k, 5), u6 = rnd(id, seedMs, k, 6);
        float start = ANT_FIRST + k * ANT_SLOT + (float) (ANT_JITTER * u0);
        float dur = ANT_DUR_MIN + (float) (ANT_DUR_SPREAD * u1);
        double dir = u2 < 0.5 ? 1.0 : -1.0;
        double x0 = -dir * ANT_X, x1 = dir * ANT_X;
        double z0 = ANT_Z_NEAR + (ANT_Z_FAR - ANT_Z_NEAR) * u3;
        double z1 = ANT_Z_NEAR + (ANT_Z_FAR - ANT_Z_NEAR) * u4;
        double cx = (u5 - 0.5) * 0.6;
        double cz = ANT_Z_NEAR + (ANT_Z_FAR - ANT_Z_NEAR) * u6;
        float flick = start + FLICK_AT * dur;
        float lt = PicnicMotion.loopTime(flick);
        boolean doFlick = flick > PicnicMotion.LOOP_START && lt >= PicnicMotion.FLICK_FROM && lt <= PicnicMotion.FLICK_TO;
        return new Ant(k, start, dur, doFlick ? flick : Float.NaN, x0, z0, cx, cz, x1, z1);
    }

    private static double rnd(String id, long seedMs, int k, int j) {
        return CampfireLogic.unit(CampfireLogic.seed(id, seedMs, 7_000 + k * 8 + j));
    }

    /** Zeitfenster, in dem {@code t} liegt (Ameise darin kann zu {@code t} unterwegs sein), oder -1. */
    public static int antSlot(float t) {
        if (t < ANT_FIRST) return -1;
        return (int) Math.floor((t - ANT_FIRST) / ANT_SLOT);
    }

    /**
     * Zustand einer Ameise zur Emote-Zeit {@code t}: {x, y, z, Blickrichtung (Bogenmaß, Rahmen-Yaw: 0 = -Z, + = nach
     * rechts gedreht), sichtbar (1/0), fliegt (1/0)}.
     */
    public static double[] antState(Ant a, float t, double[] out) {
        out[4] = 0.0;
        out[5] = 0.0;
        if (t < a.start() || t > a.end()) return out;
        float walkEnd = a.flicked() ? a.flickAt() : a.start() + a.duration();
        float tw = Math.min(t, walkEnd);
        double s = (tw - a.start()) / a.duration();
        // leicht ungleichmäßiges Tempo (Ameisen halten kurz an)
        s = s + 0.03 * Math.sin(s * Math.PI * 6.0) * Math.sin(Math.PI * s);
        s = Math.max(0.0, Math.min(1.0, s));
        double om = 1.0 - s;
        double x = om * om * a.x0() + 2 * om * s * a.cx() + s * s * a.x1();
        double z = om * om * a.z0() + 2 * om * s * a.cz() + s * s * a.z1();
        double dx = 2 * om * (a.cx() - a.x0()) + 2 * s * (a.x1() - a.cx());
        double dz = 2 * om * (a.cz() - a.z0()) + 2 * s * (a.z1() - a.cz());
        double heading = Math.atan2(dx, -dz);
        double y = BLANKET_TOP;
        if (a.flicked() && t > a.flickAt()) {
            double f = (t - a.flickAt()) / FLICK_FLY;
            // weggeschnippt: nach vorne (vom Spieler weg) und hoch, dabei überschlägt sie sich
            z -= FLICK_DIST * f;
            x += 0.15 * f * Math.signum(x == 0.0 ? 1.0 : x);
            y += FLICK_HEIGHT * 4.0 * f * (1.0 - f) + 0.02 * f;
            heading += f * 9.0;
            out[5] = 1.0;
        } else {
            // Krabbeln: leichtes Wackeln
            heading += 0.12 * Math.sin(t * 2.0 * Math.PI * 5.0);
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        out[3] = heading;
        out[4] = 1.0;
        return out;
    }
}

package com.bame.client.emotes;

/**
 * Geometrie und Flugbahn des Hexenbesen-Emotes (ohne Minecraft-Klassen, im witchBroomTest geprüft).
 * <p>
 * <b>Rein optisch:</b> Die echte Spielerposition, die Hitbox und alles, was an den Server geht, bleiben unverändert.
 * Bewegt wird nur das gerenderte Modell (Transformation in {@code setupTransforms}) und ggf. die Emote-Kamera.
 * <p>
 * Koordinaten im <b>Besen-Rahmen</b> F (gleiche Achsen wie {@link HammockLogic}): Ursprung an den Füßen des Spielers
 * beim Start (Boden), +Y oben, +X rechts, +Z hinten bezogen auf den Körper-Yaw beim Start. Längen in Einheiten des
 * Raums von {@code setupTransforms} (Blöcke geteilt durch die Spieler-Skalierung; Tests: Skalierung 1).
 * <p>
 * Der Besen selbst hat einen eigenen Raum B: Ursprung auf der Stielachse unter dem Schritt des Reiters (Sitzpunkt),
 * +Z entlang des Stiels nach hinten (Reisig), -Z nach vorne (Stielende), +Y oben, +X rechts. Der Besen liegt im Rahmen
 * bei {@code W = Tr(P) · Ry(heading) · Rz(bank) · Rx(nose)}.
 * <p>
 * Flugbahn: Nach dem Aufsitzen schwebt der Besen vom Startpunkt in einer weichen Spirale auf einen Kreis mit Radius
 * {@link #RADIUS} um den Startpunkt (im Uhrzeigersinn von oben, also eine Rechtskurve). Der Besen zeigt immer in
 * Flugrichtung (Ableitung der Bahn), neigt sich in die Kurve und wippt leicht auf und ab.
 */
public final class WitchBroomLogic {

    private WitchBroomLogic() {}

    public static final double PX = 1.0 / 16.0;

    // ------------------------------------------------------------------
    // Besen (Raum B; 1 Modell-px = 1/16 Block)
    // ------------------------------------------------------------------

    /** Radius des Stiels (flache Seiten) und größter Abstand der Kanten des achteckigen Querschnitts von der Achse. */
    public static final double STICK_R = 0.8 * PX, STICK_R_MAX = 1.05 * PX;
    /** Vorderes Stielende (inkl. leicht nach oben gebogener Spitze) und Beginn der Reisig-Bindung (z). */
    public static final double TIP_Z = -0.93, BIND_Z = 0.42;
    /** Ende des Reisigs (z) und Radius des Reisig-Bündels an der Bindung bzw. am Ende. */
    public static final double BRISTLE_END_Z = 0.80, BRISTLE_R0 = 2.2 * PX, BRISTLE_R1 = 4.0 * PX;
    /** Höchstens so weit weicht der krumme Stiel von der geraden Achse ab (Modell; Test). */
    public static final double STICK_CROOK_MAX = 0.45 * PX;

    // ------------------------------------------------------------------
    // Flug (Emote-Zeit, s)
    // ------------------------------------------------------------------

    /** Beginn des Fluges (nach dem Aufsitzen). */
    public static final float FLY_START = 0.9F;
    /** In dieser Zeit wächst der Abstand zum Startpunkt von 0 auf {@link #RADIUS} (Spirale). */
    public static final float SPIRAL = 2.4F;
    /** Radius des Kreises um den Startpunkt (Blöcke) und Dauer einer Runde (s). */
    public static final double RADIUS = 0.8;
    public static final float PERIOD = 7.0F;
    /** Höhe der Stielachse am Sitzpunkt über dem Boden und Auf-und-Ab (Amplitude, Periode). */
    public static final double HOVER_Y = 0.70, BOB = 0.045;
    public static final float BOB_PERIOD = PERIOD / 3.0F;
    /** Neigung in die Kurve (Grad) bei voller Fahrt. */
    public static final float BANK_DEG = 13.0F;
    /** Nase des Besens nach oben (Grad) und leichtes Nicken mit dem Auf-und-Ab. */
    public static final float NOSE_DEG = 28.0F, NOSE_OSC_DEG = 1.5F;

    /** Fortschritt des Fluges 0..1 (weich, C²) ab {@link #FLY_START} über {@link #SPIRAL}. */
    public static double flightEnv(float t) {
        return smoother((t - FLY_START) / SPIRAL);
    }

    /** Winkel auf dem Kreis (Bogenmaß, 0 = vorne, wächst im Uhrzeigersinn von oben). */
    public static double phase(float t) {
        return t <= FLY_START ? 0.0 : 2.0 * Math.PI * (t - FLY_START) / PERIOD;
    }

    /** Abstand vom Startpunkt (Blöcke) und seine Ableitung nach der Zeit. */
    public static double radius(float t) {
        return RADIUS * flightEnv(t);
    }

    public static double radiusRate(float t) {
        double x = (t - FLY_START) / SPIRAL;
        if (x <= 0.0 || x >= 1.0) return 0.0;
        return RADIUS * 30.0 * x * x * (1.0 - x) * (1.0 - x) / SPIRAL;
    }

    /**
     * Flugrichtung als Yaw im Rahmen (Grad, Drehung um +Y; 0 = nach vorne -Z, negativ = nach rechts). Stetig (nicht
     * auf ±180° gefaltet): Aus der Spirale heraus dreht sich der Besen von „radial nach außen“ weich auf die Tangente.
     */
    public static double headingDeg(float t) {
        if (t <= FLY_START) return 0.0;
        double w = 2.0 * Math.PI / PERIOD;
        double delta = Math.atan2(radius(t) * w, radiusRate(t));
        return -Math.toDegrees(phase(t) + delta);
    }

    /** Position des Sitzpunkts im Rahmen (Blöcke, ohne Skalierung): {x, y, z}. */
    public static double[] position(float t, double[] out) {
        double r = radius(t), phi = phase(t);
        out[0] = r * Math.sin(phi);
        out[1] = altitude(t);
        out[2] = -r * Math.cos(phi);
        return out;
    }

    /** Höhe der Stielachse am Sitzpunkt (Blöcke über dem Boden). */
    public static double altitude(float t) {
        if (t <= FLY_START) return HOVER_Y;
        return HOVER_Y + BOB * flightEnv(t) * Math.sin(2.0 * Math.PI * (t - FLY_START) / BOB_PERIOD);
    }

    /** Neigung in die Kurve als Drehung um die Stielachse (Grad, Rz; negativ = rechte Seite nach unten). */
    public static double bankDeg(float t) {
        return -BANK_DEG * flightEnv(t);
    }

    /** Nase nach oben (Grad, Rx; positiv = Stielende hoch), nickt mit dem Steigen. */
    public static double noseDeg(float t) {
        if (t <= FLY_START) return NOSE_DEG;
        return NOSE_DEG + NOSE_OSC_DEG * flightEnv(t) * Math.cos(2.0 * Math.PI * (t - FLY_START) / BOB_PERIOD);
    }

    // ------------------------------------------------------------------
    // Platzprüfung
    // ------------------------------------------------------------------

    /** Prüfradien (Blöcke): Kreis der Sitzpunkte und äußerster Punkt der Besenspitzen. */
    public static final double[] CHECK_RADII = {RADIUS, 1.15};
    /** Prüfhöhen über den Füßen: Reisig, Rumpf, Kopf (Blöcke). */
    public static final double[] CHECK_HEIGHTS = {0.25, 1.0, 1.75};
    public static final int CHECK_DIRECTIONS = 8;

    /** Prüfpunkte im Rahmen {x, y, z}: Mitte und zwei Ringe in acht Richtungen, je auf drei Höhen (alle in Luft). */
    public static double[][] checkPointsFrame() {
        int n = (1 + CHECK_RADII.length * CHECK_DIRECTIONS) * CHECK_HEIGHTS.length;
        double[][] out = new double[n][];
        int k = 0;
        for (double h : CHECK_HEIGHTS) {
            out[k++] = new double[]{0.0, h, 0.0};
            for (double r : CHECK_RADII) {
                for (int d = 0; d < CHECK_DIRECTIONS; d++) {
                    double a = 2.0 * Math.PI * d / CHECK_DIRECTIONS;
                    out[k++] = new double[]{r * Math.sin(a), h, -r * Math.cos(a)};
                }
            }
        }
        return out;
    }

    /** Wie {@link #checkPointsFrame()} in Weltkoordinaten. */
    public static double[][] checkPoints(double x, double y, double z, float yaw) {
        double[][] f = checkPointsFrame();
        double[][] out = new double[f.length][];
        for (int i = 0; i < f.length; i++) out[i] = HammockLogic.frameToWorld(f[i][0], f[i][1], f[i][2], x, y, z, yaw);
        return out;
    }

    // ------------------------------------------------------------------
    // Drehungen (rechtshändig, wie RotationAxis.POSITIVE_*), Grad
    // ------------------------------------------------------------------

    public static void rotX(double[] v, double deg) {
        if (deg == 0.0) return;
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        double y = v[1] * c - v[2] * s, z = v[1] * s + v[2] * c;
        v[1] = y;
        v[2] = z;
    }

    public static void rotY(double[] v, double deg) {
        if (deg == 0.0) return;
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        double x = v[0] * c + v[2] * s, z = -v[0] * s + v[2] * c;
        v[0] = x;
        v[2] = z;
    }

    public static void rotZ(double[] v, double deg) {
        if (deg == 0.0) return;
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        double x = v[0] * c - v[1] * s, y = v[0] * s + v[1] * c;
        v[0] = x;
        v[1] = y;
    }

    /** C²-weicher Übergang 0..1 (smootherstep). */
    public static double smoother(double x) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        return x * x * x * (x * (6.0 * x - 15.0) + 10.0);
    }
}

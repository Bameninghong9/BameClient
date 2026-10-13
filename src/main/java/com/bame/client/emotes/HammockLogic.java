package com.bame.client.emotes;

/**
 * Geometrie der Hängematte (ohne Minecraft-Klassen, im hammockTest geprüft): Pfosten, Aufhängung, Form des Tuchs und die
 * Platzprüfung beim Start.
 * <p>
 * Koordinaten im <b>Hängematten-Rahmen</b> H (Blöcke): Ursprung an den Füßen des Spielers beim Start (Boden), +Y oben,
 * +X nach rechts und +Z nach hinten bezogen auf den Körper-Yaw beim Start. Das entspricht dem Raum in
 * {@code setupTransforms} nach der Gegendrehung auf den Start-Yaw. Die Hängematte spannt sich entlang X (quer zur
 * Blickrichtung beim Start): In der 3rd-Person-Ansicht von hinten liegt der Spieler quer im Bild, Kopf links.
 */
public final class HammockLogic {

    private HammockLogic() {}

    /** Blöcke pro Modell-Pixel (Spieler-Skalierung 0.9375) und Modell-Ursprung über den Füßen (px). */
    public static final double BLOCKS_PER_PX = 0.9375 / 16.0;
    public static final double MODEL_ORIGIN_UP_PX = 24.016;

    // ------------------------------------------------------------------
    // Pfosten
    // ------------------------------------------------------------------

    /** Pfosten-Fuß (Mitte) so weit links/rechts von der Mitte (Blöcke). */
    public static final double POST_X = 1.6;
    /** Pfosten leicht nach außen geneigt (Grad). */
    public static final double POST_TILT_DEG = 6.0;
    /** Länge des Pfostens entlang seiner Achse ohne Spitze (Blöcke) und halbe Dicke (Blöcke, Modell 3,2 px breit). */
    public static final double POST_LENGTH = 1.95;
    public static final double POST_HALF = 0.1;
    /** Seilwicklung: so weit entlang der Pfostenachse über dem Boden (Blöcke). */
    public static final double ATTACH_ALONG = 1.78;

    private static final double TILT = Math.toRadians(POST_TILT_DEG);
    /** Höhe der Schaukelachse = Mitte der Seilwicklung (Blöcke über dem Boden). */
    public static final double AXIS_Y = ATTACH_ALONG * Math.cos(TILT);
    /** Aufhängepunkt (Innenseite des Pfostens auf Achsenhöhe), Abstand von der Mitte (Blöcke). */
    public static final double ATTACH_X = POST_X + ATTACH_ALONG * Math.sin(TILT) - POST_HALF / Math.cos(TILT);

    // ------------------------------------------------------------------
    // Tuch (Liegefläche)
    // ------------------------------------------------------------------

    /** Spreizstäbe an den Enden des Tuchs (Abstand von der Mitte, Blöcke). */
    public static final double SPREADER_X = 1.12;
    /** Halbe Breite des Tuchs (Blöcke) und flacher Mittelstreifen (halbe Breite). */
    public static final double SLING_HALF_W = 0.42;
    public static final double FLAT_HALF_W = 0.2;
    /** Seitliche Wölbung: die Ränder liegen so viel höher als die Mitte (Blöcke). */
    public static final double CUP = 0.09;
    /** Oberseite des Tuchs in der Mitte (tiefster Punkt) und Durchhang bis zu den Enden (Blöcke). */
    public static final double LOW_Y = 0.56;
    public static final double SAG = 0.30;
    /** Form der Kettenlinie (Blöcke): kleiner = stärker gekrümmt. */
    public static final double CATENARY_A = 0.8;
    /** Stoffdicke (Blöcke, 0,6 px). */
    public static final double FABRIC = 0.6 / 16.0;
    /** Anzahl Segmente entlang der Länge (Modell-Knochen s0..s7, von -X nach +X). */
    public static final int SEGMENTS = 8;
    /** Seile laufen von der Wicklung zu den Enden des Spreizstabs (halbe Länge des Stabs, Blöcke). */
    public static final double SPREADER_HALF = 0.43;

    private static final double CAT_NORM = Math.cosh(SPREADER_X / CATENARY_A) - 1.0;

    /** Oberseite des Tuchs auf der Mittellinie (z = 0) an der Stelle x (Kettenlinie, Blöcke). */
    public static double slingY(double x) {
        double cx = Math.max(-SPREADER_X, Math.min(SPREADER_X, x));
        return LOW_Y + SAG * (Math.cosh(cx / CATENARY_A) - 1.0) / CAT_NORM;
    }

    /** Steigung dy/dx der Mittellinie. */
    public static double slingSlope(double x) {
        double cx = Math.max(-SPREADER_X, Math.min(SPREADER_X, x));
        return SAG * Math.sinh(cx / CATENARY_A) / (CATENARY_A * CAT_NORM);
    }

    /** Anhebung der Oberseite quer zur Länge (Wölbung): 0 im Mittelstreifen, {@link #CUP} am Rand. */
    public static double cupLift(double z) {
        double a = Math.abs(z);
        if (a <= FLAT_HALF_W) return 0.0;
        return CUP * Math.min(1.0, (a - FLAT_HALF_W) / (SLING_HALF_W - FLAT_HALF_W));
    }

    /** Oberseite des (nicht geschaukelten) Tuchs an (x, z). */
    public static double surfaceY(double x, double z) {
        return slingY(x) + cupLift(z);
    }

    /** Höhe der Enden (Oberseite am Spreizstab). */
    public static double endY() {
        return slingY(SPREADER_X);
    }

    /** x-Positionen der Segmentgrenzen (SEGMENTS + 1 Werte, gleichmäßig von -SPREADER_X bis +SPREADER_X). */
    public static double segmentX(int i) {
        return -SPREADER_X + 2.0 * SPREADER_X * i / SEGMENTS;
    }

    // ------------------------------------------------------------------
    // Schaukeln: Drehung um die Achse durch beide Aufhängepunkte (entlang X auf Höhe AXIS_Y, z = 0)
    // ------------------------------------------------------------------

    /** Punkt (x, y, z) im Rahmen um die Schaukelachse drehen (Grad, positiv = Rechtsdrehung um +X). */
    public static double[] swing(double x, double y, double z, double deg, double[] out) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a), s = Math.sin(a);
        double dy = y - AXIS_Y;
        out[0] = x;
        out[1] = AXIS_Y + dy * c - z * s;
        out[2] = dy * s + z * c;
        return out;
    }

    // ------------------------------------------------------------------
    // Platzprüfung
    // ------------------------------------------------------------------

    /** Prüfpunkte entlang der Hängematte (x im Rahmen): beide Pfosten, Tuch-Enden und Mitte. */
    public static final double[] CHECK_X = {-POST_X, -0.8, 0.0, 0.8, POST_X};
    /** Prüfhöhen über den Füßen: Fuß- und Kopfhöhe (Blöcke). */
    public static final double[] CHECK_HEIGHTS = {0.05, 1.5};

    /**
     * Prüfpunkte für die Platzprüfung in Weltkoordinaten: {x, y, z, needsGround}. An jedem Punkt muss Luft sein (keine
     * Kollision, keine Flüssigkeit); bei {@code needsGround = 1} (Fußhöhe an den Pfosten) muss darunter ein fester Block
     * liegen.
     *
     * @param yaw Körper-Yaw beim Start (Minecraft-Grad)
     */
    public static double[][] checkPoints(double x, double y, double z, float yaw) {
        double[][] out = new double[CHECK_X.length * CHECK_HEIGHTS.length][];
        int k = 0;
        for (double cx : CHECK_X) {
            boolean post = Math.abs(Math.abs(cx) - POST_X) < 1.0E-9;
            for (int h = 0; h < CHECK_HEIGHTS.length; h++) {
                double[] w = frameToWorld(cx, CHECK_HEIGHTS[h], 0.0, x, y, z, yaw);
                out[k++] = new double[]{w[0], w[1], w[2], post && h == 0 ? 1.0 : 0.0};
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Umrechnung Rahmen <-> Welt
    // ------------------------------------------------------------------

    /** Rahmenpunkt (Blöcke) in Weltkoordinaten; Ursprung (ox, oy, oz), Ausrichtung {@code yaw}. */
    public static double[] frameToWorld(double fx, double fy, double fz, double ox, double oy, double oz, float yaw) {
        double r = Math.toRadians(yaw);
        // rechts = (-cos, 0, -sin), hinten = (sin, 0, -cos)
        double wx = ox - Math.cos(r) * fx + Math.sin(r) * fz;
        double wz = oz - Math.sin(r) * fx - Math.cos(r) * fz;
        return new double[]{wx, oy + fy, wz};
    }

    /** Weltpunkt in Rahmenkoordinaten (Umkehrung von {@link #frameToWorld}). */
    public static double[] worldToFrame(double wx, double wy, double wz, double ox, double oy, double oz, float yaw,
                                        double[] out) {
        double r = Math.toRadians(yaw);
        double dx = wx - ox, dz = wz - oz;
        out[0] = -Math.cos(r) * dx - Math.sin(r) * dz;
        out[1] = wy - oy;
        out[2] = Math.sin(r) * dx - Math.cos(r) * dz;
        return out;
    }

    /** Winkel auf (-180, 180] Grad. */
    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d <= -180.0) d += 360.0;
        return d;
    }
}

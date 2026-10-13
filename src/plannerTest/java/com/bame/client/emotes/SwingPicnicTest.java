package com.bame.client.emotes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft ohne Minecraft Schaukel und Picknick:
 * <ul>
 *   <li>Schaukel: Gerüst-Maße und Platzprüfung ({@link SwingLogic}), Zeitleiste/Schaukeln/Pumpen ({@link SwingMotion})
 *       flüssig in 1-ms-Schritten inkl. Erscheinen, deterministisch, Hüfte bleibt über den ganzen Bogen auf dem Sitz,
 *       Hände an den Seilen, nichts im Boden/Balken/Gerüst, Sitzplatz-Regeln (zweiter Sitz bis 2,0 Blöcke, weiter weg
 *       abgelehnt, besetzt → eigenes Gerüst bzw. abgelehnt, Besitzer geht → Gerüst bleibt, unabhängig von der
 *       Reihenfolge).</li>
 *   <li>Picknick: Zeitleiste und Schleife ({@link PicnicMotion}) flüssig und nahtlos, deterministisch, Schneidersitz
 *       nicht unter der Decke, Hände erreichen Korb/Flasche/Mund, Ameisen ({@link PicnicLogic}) alle 10–20 s auf der
 *       Decke, Wegschnippen nur in der ruhigen Phase.</li>
 *   <li>Modelle beider Emotes passen zu den Maßen.</li>
 * </ul>
 */
public class SwingPicnicTest {

    private static int checks = 0;
    private static final double PX = 1.0 / 16.0;
    private static final double KPX = HammockLogic.BLOCKS_PER_PX;

    public static void main(String[] args) {
        emoteTypes();
        // Schaukel
        swingGeometry();
        swingCheckPoints();
        swingTimeline();
        swingDeterministic();
        swingContinuity();
        swingHipOnSeat();
        swingHandsOnRopes();
        swingClearance();
        swingJoinerSlidesToSeat();
        swingHeadLook();
        swingArmsClearHead();
        hammockPicnicHeadWrap();
        seatJoinFree();
        seatRejectFar();
        seatOccupied();
        seatOwnerLeaves();
        seatOrderIndependent();
        seatTieBreakAndHeight();
        swingEvents();
        // Picknick
        picnicCheckPoints();
        picnicTimeline();
        picnicLoopSeam();
        picnicDeterministic();
        picnicContinuity();
        picnicSitsOnBlanket();
        picnicReach();
        antsSchedule();
        antsOnBlanket();
        antsFlick();
        picnicEvents();
        // Modelle
        swingModels();
        picnicModels();
        System.out.println("SWING/PICNIC TESTS PASSED (" + checks + " checks)");
    }

    // ---------------- Hilfen ----------------

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void near(double a, double b, double eps, String what) {
        check(Math.abs(a - b) <= eps, what + ": " + a + " != " + b);
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private interface PartMap {
        double[] map(HammockMotion.Part part, double lx, double ly, double lz);
    }

    /** Ecken und Kantenmitten aller Körperteile (27 Punkte pro Teil). */
    private static List<double[]> bodyPoints(PartMap f) {
        List<double[]> out = new ArrayList<>();
        for (HammockMotion.Part part : HammockMotion.Part.values()) {
            for (int i = 0; i < 27; i++) {
                double fx = (i % 3) / 2.0, fy = ((i / 3) % 3) / 2.0, fz = (i / 9) / 2.0;
                double[] w = f.map(part, part.x0 + part.sx * fx, part.y0 + part.sy * fy, part.z0 + part.sz * fz);
                out.add(new double[]{w[0], w[1], w[2], part.ordinal(), i});
            }
        }
        return out;
    }

    private static List<double[]> swingPoints(SwingMotion.Pose p) {
        return bodyPoints((part, x, y, z) -> SwingMotion.partToFrame(p, part, x, y, z, new double[3]));
    }

    private static List<double[]> picnicPoints(PicnicMotion.Pose p) {
        return bodyPoints((part, x, y, z) -> PicnicMotion.partToFrame(p, part, x, y, z, new double[3]));
    }

    // ---------------- Emote-Typen ----------------

    private static void emoteTypes() {
        check(EmoteType.fromId("SWING") == EmoteType.SWING, "fromId SWING");
        check(EmoteType.fromId("swing") == EmoteType.SWING, "fromId swing (Karten-ID)");
        check("Swing".equals(EmoteType.SWING.getDisplayName()), "Anzeigename Swing");
        check(EmoteType.fromId("PICNIC") == EmoteType.PICNIC, "fromId PICNIC");
        check(EmoteType.fromId("picnic") == EmoteType.PICNIC, "fromId picnic (Karten-ID)");
        check("Picnic".equals(EmoteType.PICNIC.getDisplayName()), "Anzeigename Picnic");
        for (EmoteType e : new EmoteType[]{EmoteType.SWING, EmoteType.PICNIC}) {
            check(!e.getDescription().isBlank(), "Beschreibung " + e);
            check(!e.allowsWalking(), "stationär " + e);
            check(!e.isHidden(), "auswählbar " + e);
        }
    }

    // ---------------- Schaukel: Geometrie ----------------

    private static void swingGeometry() {
        double width = 2.0 * SwingLogic.LEG_FOOT_X + 2.0 * SwingLogic.LEG_HALF;
        check(width >= 2.8 && width <= 3.4, "Gerüst ca. 3 Blöcke breit (" + width + ")");
        double height = SwingLogic.BEAM_Y + SwingLogic.BEAM_HALF;
        check(height >= 2.6 && height <= 3.0, "Gerüst 2,6–3 Blöcke hoch (" + height + ")");
        check(SwingLogic.LEG_FOOT_X > SwingLogic.LEG_TOP_X, "Beine leicht gespreizt");
        near(SwingLogic.seatX(0), 0.0, 1e-12, "Sitz 0 genau am Standpunkt");
        near(SwingLogic.seatX(1) - SwingLogic.seatX(0), SwingLogic.SEAT_SPACING, 1e-12, "zweiter Sitz daneben");
        near(SwingLogic.CENTER_X, (SwingLogic.seatX(0) + SwingLogic.seatX(1)) / 2.0, 1e-12, "Gerüst mittig über beiden Sitzen");
        // Beide Sitze (inkl. Seile) liegen zwischen den Beinen
        for (int s = 0; s < 2; s++) {
            double outer = Math.abs(SwingLogic.seatX(s) - SwingLogic.CENTER_X) + SwingLogic.SEAT_HALF_W;
            check(outer < SwingLogic.LEG_TOP_X - SwingLogic.LEG_HALF - 0.15, "Sitz " + s + " zwischen den Beinen");
        }
        double period = 2.0 * Math.PI * Math.sqrt(SwingLogic.ROPE_LENGTH / 9.81);
        check(Math.abs(period - SwingMotion.PERIOD) < 0.35, "Periode passt zur Seillänge (" + period + ")");
        double[] o = new double[3];
        SwingLogic.swing(0.3, SwingLogic.PIVOT_Y, 0.0, 30.0, o);
        near(o[1], SwingLogic.PIVOT_Y, 1e-12, "Achse bleibt fest");
        SwingLogic.swing(0.0, SwingLogic.SEAT_TOP_Y, 0.0, 30.0, o);
        check(o[2] < 0.0, "positiver Winkel schwingt nach vorne (-Z)");
    }

    private static void swingCheckPoints() {
        double[][] f = SwingLogic.checkPointsFrame();
        int ground = 0;
        double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9, maxY = 0;
        for (double[] p : f) {
            if (p[3] > 0.5) {
                ground++;
                near(Math.abs(p[0] - SwingLogic.CENTER_X), SwingLogic.LEG_FOOT_X, 1e-9, "Boden unter den Bein-Füßen");
                near(Math.abs(p[2]), SwingLogic.LEG_FOOT_Z, 1e-9, "Boden unter den Bein-Füßen (z)");
            }
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[2]);
            maxZ = Math.max(maxZ, p[2]);
            maxY = Math.max(maxY, p[1]);
        }
        check(ground == 4, "vier Bein-Füße brauchen festen Boden (" + ground + ")");
        check(maxX - minX >= 2.9, "Grundfläche ca. 3 breit");
        check(maxZ - minZ >= 2.9, "Schwungbereich ±1,5 vorne/hinten");
        check(maxY >= 2.6, "Höhe bis zum Balken geprüft");
        // Weltpunkte: Rundweg über den Rahmen
        for (float yaw : new float[]{0.0F, 33.0F, -120.0F}) {
            double[][] w = SwingLogic.checkPoints(4.5, 70.0, -3.0, yaw);
            double[] tmp = new double[3];
            for (int i = 0; i < w.length; i++) {
                HammockLogic.worldToFrame(w[i][0], w[i][1], w[i][2], 4.5, 70.0, -3.0, yaw, tmp);
                near(tmp[0], f[i][0], 1e-9, "Prüfpunkt x");
                near(tmp[2], f[i][2], 1e-9, "Prüfpunkt z");
            }
        }
    }

    // ---------------- Schaukel: Zeitleiste ----------------

    private static void swingTimeline() {
        SwingMotion.Pose p = new SwingMotion.Pose();
        SwingMotion.sample(0.0F, p);
        near(p.sit, 0.0, 1e-9, "steht am Anfang");
        near(p.swingDeg, 0.0, 1e-9, "schaukelt noch nicht");
        SwingMotion.Appear ap = new SwingMotion.Appear();
        SwingMotion.appear(0.0F, ap);
        near(ap.legs, 0.0, 1e-6, "Beine beginnen bei 0");
        near(ap.ropes, 0.0, 1e-6, "Seile noch nicht da");
        SwingMotion.appear(1.0F, ap);
        near(ap.legs, 1.0, 1e-6, "Beine voll da");
        near(ap.beam, 1.0, 1e-6, "Balken da");
        near(ap.seat, 1.0, 1e-6, "Sitz da");
        near(ap.ropes, 1.0, 1e-6, "Seile da");
        near(SwingMotion.beamOffset(1.0F), 0.0, 1e-9, "Balken in Ruhelage");
        SwingMotion.sample(SwingMotion.SIT_END, p);
        near(p.sit, 1.0, 1e-6, "sitzt nach dem Hinsetzen");
        check(SwingMotion.AMP_DEG >= 35.0F && SwingMotion.AMP_DEG <= 40.0F, "Amplitude 35–40°");
        check(SwingMotion.PERIOD >= 2.6F && SwingMotion.PERIOD <= 3.0F, "Periode 2,6–3 s");
        check(SwingMotion.SWING_START + SwingMotion.RAMP <= 4.5F, "Amplitude baut sich in ca. 3 s auf");
        double max = 0.0, maxEarly = 0.0;
        for (int ms = 0; ms < 30000; ms++) {
            SwingMotion.sample(ms / 1000.0F, p);
            if (ms > 4500) max = Math.max(max, Math.abs(p.swingDeg));
            if (ms < 2000) maxEarly = Math.max(maxEarly, Math.abs(p.swingDeg));
            check(Math.abs(p.swingDeg) <= SwingMotion.AMP_DEG + 1e-3, "innerhalb der Amplitude");
        }
        near(max, SwingMotion.AMP_DEG, 0.05, "volle Amplitude erreicht");
        check(maxEarly < SwingMotion.AMP_DEG * 0.35, "Amplitude wächst langsam (" + maxEarly + ")");
        for (float t = 6.0F; t < 20.0F; t += 0.37F) {
            near(SwingMotion.shape(t), SwingMotion.shape(t + SwingMotion.PERIOD), 1e-4, "periodisch");
        }
        // Pumpen phasenrichtig: vorne Beine ausgestreckt + zurückgelehnt, hinten angezogen + vorgelehnt
        for (int n = 4; n < 10; n++) {
            SwingMotion.sample(SwingMotion.extremeTime(n), p);
            boolean front = p.swingDeg > 0;
            check(Math.abs(p.swingDeg) > 30.0F, "Umkehrpunkt " + n);
            if (front) {
                check(p.pump > 0.8F, "vorne gepumpt (" + p.pump + ")");
                check(p.leanDeg > 8.0F, "vorne zurückgelehnt");
                double legSeat = Math.toDegrees(p.rLegPitch) - p.leanDeg;
                check(legSeat < -80.0, "vorne Beine gestreckt (zum Sitz " + legSeat + ")");
            } else {
                check(p.pump < -0.8F, "hinten angezogen (" + p.pump + ")");
                check(p.leanDeg < -8.0F, "hinten vorgelehnt");
                check(Math.toDegrees(p.rLegPitch) - p.leanDeg > -45.0, "hinten Beine unter den Sitz gezogen");
            }
        }
    }

    private static void swingDeterministic() {
        SwingMotion.Pose a = new SwingMotion.Pose(), b = new SwingMotion.Pose();
        double[] feet = {0.8, 0.0, 0.6};
        for (int ms = 0; ms < 30000; ms += 7) {
            float t = ms / 1000.0F;
            SwingMotion.sample(t, 1, feet, 25.0F, 10.0F, 5.0F, true, 1.0F, a);
            SwingMotion.sample(t, 1, feet, 25.0F, 10.0F, 5.0F, true, 1.0F, b);
            check(a.swingDeg == b.swingDeg && a.hipX == b.hipX && a.rArmPitch == b.rArmPitch && a.rLegPitch == b.rLegPitch
                    && a.headYaw == b.headYaw, "gleiche Zeit, gleiche Pose (" + t + ")");
        }
    }

    private static final double MAX_POINT_STEP = 0.008;

    private static void swingContinuity() {
        double[] feet = {SwingLogic.SEAT_SPACING + 1.2, 0.0, 0.9};
        for (int variant = 0; variant < 2; variant++) {
            int seat = variant;
            double[] f = variant == 0 ? null : feet;
            float turn0 = variant == 0 ? 0.0F : 70.0F;
            SwingMotion.Pose prev = new SwingMotion.Pose(), cur = new SwingMotion.Pose();
            SwingMotion.sample(0.0F, seat, f, turn0, 0, 0, false, 1.0F, prev);
            List<double[]> pp = swingPoints(prev);
            double worst = 0.0;
            float worstAt = 0;
            SwingMotion.Appear a0 = new SwingMotion.Appear(), a1 = new SwingMotion.Appear();
            for (int ms = 1; ms <= 30_000; ms++) {
                float t = ms / 1000.0F;
                SwingMotion.sample(t, seat, f, turn0, 0, 0, false, 1.0F, cur);
                List<double[]> cp = swingPoints(cur);
                for (int i = 0; i < cp.size(); i++) {
                    double d = dist(pp.get(i), cp.get(i));
                    if (d > worst) { worst = d; worstAt = t; }
                }
                SwingMotion.appear(t - 0.001F, a0);
                SwingMotion.appear(t, a1);
                check(Math.abs(a1.legs - a0.legs) < 0.05 && Math.abs(a1.beam - a0.beam) < 0.05
                        && Math.abs(a1.seat - a0.seat) < 0.05 && Math.abs(a1.ropes - a0.ropes) < 0.05, "Erscheinen flüssig " + t);
                pp = cp;
                SwingMotion.Pose sw = prev; prev = cur; cur = sw;
            }
            check(worst <= MAX_POINT_STEP, "Schaukel flüssig (Variante " + variant + ", " + worst + " Blöcke/ms bei " + worstAt + ")");
        }
        // lange Laufzeit
        SwingMotion.Pose a = new SwingMotion.Pose(), b = new SwingMotion.Pose();
        SwingMotion.sample(3600.0F, a);
        List<double[]> pa = swingPoints(a);
        for (int ms = 1; ms < 3000; ms++) {
            SwingMotion.sample(3600.0F + ms / 1000.0F, b);
            List<double[]> pb = swingPoints(b);
            for (int i = 0; i < pa.size(); i += 4) check(dist(pa.get(i), pb.get(i)) <= 3 * MAX_POINT_STEP, "flüssig nach 1 h");
            pa = pb;
        }
    }

    /** Unterseite der Hüfte (Mitte zwischen den Oberschenkeln, 2 px unter dem Hüftgelenk) im Rahmen. */
    private static double[] swingHipBottom(SwingMotion.Pose p) {
        return SwingMotion.modelToFrame(p, 0.0, 12.0 + 2.0, 0.0, new double[3]);
    }

    private static void swingHipOnSeat() {
        SwingMotion.Pose p = new SwingMotion.Pose();
        double worst = 0.0;
        for (int seat = 0; seat < 2; seat++) {
            double[] feet = seat == 0 ? null : new double[]{SwingLogic.SEAT_SPACING - 0.7, 0.0, 1.1};
            for (int ms = (int) (SwingMotion.SIT_END * 1000); ms < 40_000; ms += 3) {
                SwingMotion.sample(ms / 1000.0F, seat, feet, seat == 0 ? 0.0F : -40.0F, 0, 0, false, 1.0F, p);
                // Hüftgelenk (Drehpunkt des Lehnens) bleibt auf dem Sitz; Sitzpunkt darunter mit gleichem Winkel geschaukelt
                double[] hip = SwingMotion.modelToFrame(p, 0.0, 12.0, 0.0, new double[3]);
                double[] seatPt = SwingLogic.swing(SwingLogic.seatX(seat), SwingLogic.SEAT_TOP_Y + SwingMotion.HIP_ABOVE_SEAT,
                        SwingMotion.HIP_Z, p.swingDeg, new double[3]);
                worst = Math.max(worst, dist(hip, seatPt));
                // Unterseite der Hüfte im Sitz-Raum (ohne Schaukeln) auf der Sitzfläche, innerhalb des Bretts
                double[] b = SwingLogic.swing(0, 0, 0, 0, new double[3]);
                double[] hb = swingHipBottom(p);
                SwingLogic.swing(hb[0], hb[1], hb[2], -p.swingDeg, b);
                near(b[1], SwingLogic.SEAT_TOP_Y, 1.0 * PX + Math.abs(Math.sin(Math.toRadians(p.leanDeg))) * 2 * KPX,
                        "Hüfte auf der Sitzfläche bei " + ms);
                check(Math.abs(b[0] - SwingLogic.seatX(seat)) < 0.05, "Hüfte mittig auf dem Brett (x)");
                check(Math.abs(b[2]) < SwingLogic.SEAT_HALF_D, "Hüfte auf dem Brett (z " + b[2] + ")");
            }
        }
        check(worst <= 1.0 * PX, "Hüfte bleibt über den ganzen Bogen auf dem Sitz (" + worst / PX + " px)");
    }

    private static void swingHandsOnRopes() {
        SwingMotion.Pose p = new SwingMotion.Pose();
        double worst = 0.0;
        double[] g = new double[3], w = new double[3], u = new double[3];
        for (int seat = 0; seat < 2; seat++) {
            for (int ms = (int) (SwingMotion.SIT_END * 1000); ms < 40_000; ms += 3) {
                SwingMotion.sample(ms / 1000.0F, seat, null, 0, 0, 0, false, 1.0F, p);
                for (boolean right : new boolean[]{true, false}) {
                    SwingMotion.gripPoint(right, right ? p.rArmPitch : p.lArmPitch, 0.0F, right ? p.rArmRoll : p.lArmRoll, g);
                    SwingMotion.modelToFrame(p, g[0], g[1], g[2], w);
                    SwingLogic.swing(w[0], w[1], w[2], -p.swingDeg, u);
                    double ropeX = SwingLogic.seatX(seat) + (right ? 1 : -1) * SwingLogic.ROPE_X;
                    double d = Math.hypot(u[0] - ropeX, u[2]);
                    worst = Math.max(worst, d);
                    check(u[1] > SwingLogic.SEAT_TOP_Y + 0.8 && u[1] < SwingLogic.PIVOT_Y, "Hand am Seil zwischen Sitz und Haken");
                }
            }
        }
        check(worst <= 1.5 * PX, "Hände an den Seilen (" + worst / PX + " px)");
    }

    private static void swingClearance() {
        SwingMotion.Pose p = new SwingMotion.Pose();
        double lowest = 1e9, highest = -1e9, legGap = 1e9;
        for (int seat = 0; seat < 2; seat++) {
            for (int ms = 0; ms < 40_000; ms += 4) {
                SwingMotion.sample(ms / 1000.0F, seat, null, 0, 0, 0, false, 1.0F, p);
                boolean seated = ms >= SwingMotion.SIT_END * 1000;
                for (double[] q : swingPoints(p)) {
                    if (seated) lowest = Math.min(lowest, q[1]);
                    else check(q[1] >= 0.0, "beim Hinsetzen nichts im Boden (" + q[1] + " bei " + ms + ")");
                    highest = Math.max(highest, q[1]);
                    // A-Bein auf dieser Höhe (x linear zwischen Fuß und Balken)
                    double f = Math.max(0.0, Math.min(1.0, q[1] / SwingLogic.BEAM_Y));
                    double legX = SwingLogic.LEG_FOOT_X + (SwingLogic.LEG_TOP_X - SwingLogic.LEG_FOOT_X) * f;
                    legGap = Math.min(legGap, legX - SwingLogic.LEG_HALF - Math.abs(q[0] - SwingLogic.CENTER_X));
                }
            }
        }
        check(lowest >= 0.03, "nichts im Boden (tiefster Punkt " + lowest + ")");
        check(highest < SwingLogic.BEAM_Y - SwingLogic.BEAM_HALF - 0.1, "nichts im Balken (" + highest + ")");
        // Arme stehen leicht nach außen gespreizt (Kopf frei), bleiben aber deutlich vor den A-Beinen
        check(legGap >= 0.15, "Abstand zu den Beinen (" + legGap + ")");
    }

    private static void swingJoinerSlidesToSeat() {
        double[] feet = {SwingLogic.SEAT_SPACING + 1.3, 0.0, 0.8};
        SwingMotion.Pose p = new SwingMotion.Pose();
        SwingMotion.sample(0.0F, 1, feet, 40.0F, 0, 0, false, 1.0F, p);
        near(p.hipX, feet[0], 1e-9, "zweiter Sitz: startet am eigenen Standpunkt (x)");
        near(p.hipZ, feet[2], 1e-9, "zweiter Sitz: startet am eigenen Standpunkt (z)");
        near(p.turnDeg, 40.0, 1e-6, "noch in eigener Blickrichtung");
        SwingMotion.sample(SwingMotion.SIT_END, 1, feet, 40.0F, 0, 0, false, 1.0F, p);
        near(p.hipX, SwingLogic.seatX(1), 1e-6, "sitzt auf Sitz 1");
        near(p.turnDeg, 0.0, 1e-6, "schaut in Schaukelrichtung");
    }

    // ---------------- Schaukel: Kopf ----------------

    private static final double RAD = 180.0 / Math.PI;

    /**
     * Kopf folgt dem Blick nur in Grenzen (Yaw ±60°, Blick-Pitch -30..+25°), für beliebige Blickrichtungen (auch
     * unnormierte Winkel und über ±180° hinweg) stetig, nie über die Schulter nach hinten; Gesamt-Pitch gegenüber dem
     * Rumpf bleibt in ±{@link SwingMotion#HEAD_PITCH_LIMIT}.
     */
    private static void swingHeadLook() {
        SwingMotion.Pose p = new SwingMotion.Pose(), q = new SwingMotion.Pose();
        float yawMax = SwingMotion.HEAD_YAW_MAX, pitchLimit = SwingMotion.HEAD_PITCH_LIMIT;
        float[] times = {0.9F, 2.3F, SwingMotion.extremeTime(4), SwingMotion.extremeTime(5), SwingMotion.bottomTime(8), 17.77F};
        float[] pitches = {-90.0F, -45.0F, -30.0F, -12.0F, 0.0F, 14.0F, 25.0F, 45.0F, 90.0F};
        double worstYawStep = 0.0, worstPitchStep = 0.0;
        for (int seat = 0; seat < 2; seat++) {
            for (float t : times) {
                SwingMotion.sample(t, seat, null, 0.0F, 0.0F, 0.0F, false, 1.0F, q);
                double idleYaw = q.headYaw * RAD;
                for (float lp : pitches) {
                    double prevYaw = Double.NaN, prevPitch = Double.NaN;
                    for (int k = -2880; k <= 2880; k++) {
                        float ly = k * 0.25F;
                        SwingMotion.sample(t, seat, null, 0.0F, ly, lp, true, 1.0F, p);
                        double hy = p.headYaw * RAD, hp = p.headPitch * RAD;
                        check(Math.abs(hy) <= yawMax + 1e-3, "Kopf-Yaw ≤ ±" + yawMax + " (" + hy + " bei Blick " + ly + ")");
                        check(Math.abs(hp) <= pitchLimit + 1e-3, "Kopf-Pitch ≤ ±" + pitchLimit + " (" + hp + ")");
                        if (!Double.isNaN(prevYaw)) {
                            worstYawStep = Math.max(worstYawStep, Math.abs(hy - prevYaw));
                            worstPitchStep = Math.max(worstPitchStep, Math.abs(hp - prevPitch));
                        }
                        prevYaw = hy;
                        prevPitch = hp;
                        // Blick nach hinten: Kopf schaut nach vorne (wie ohne Blick-Folgen), kein Verdrehen
                        float wrapped = (float) HammockLogic.wrapDegrees(ly);
                        if (Math.abs(wrapped) >= HammockMotion.LOOK_BACK_TO) {
                            near(hy, idleYaw, 1e-3, "Blick nach hinten (" + ly + "): Kopf nach vorne");
                        }
                        // gleicher Blick, anders normiert: gleiche Pose
                        if (k % 37 == 0) {
                            SwingMotion.sample(t, seat, null, 0.0F, wrapped, lp, true, 1.0F, q);
                            near(p.headYaw * RAD, q.headYaw * RAD, 1e-3, "Yaw unabhängig von ±360° (" + ly + ")");
                            near(p.headPitch * RAD, q.headPitch * RAD, 1e-3, "Pitch unabhängig von ±360° (" + ly + ")");
                        }
                    }
                }
                // Blick-Pitch wird weich begrenzt (stetig über den ganzen Bereich)
                double prev = Double.NaN;
                for (int k = -360; k <= 360; k++) {
                    SwingMotion.sample(t, seat, null, 0.0F, 30.0F, k * 0.25F, true, 1.0F, p);
                    double hp = p.headPitch * RAD;
                    if (!Double.isNaN(prev)) worstPitchStep = Math.max(worstPitchStep, Math.abs(hp - prev));
                    prev = hp;
                }
            }
        }
        check(worstYawStep <= 1.0, "Kopf-Yaw stetig (" + worstYawStep + "° pro 0,25° Blick)");
        check(worstPitchStep <= 1.0, "Kopf-Pitch stetig (" + worstPitchStep + "° pro 0,25° Blick)");

        // Ohne Schaukeln (sitzt, noch kein Schwung): Blick-Pitch in -30..+25
        for (float lp = -90.0F; lp <= 90.0F; lp += 0.5F) {
            SwingMotion.sample(0.9F, 0, null, 0.0F, 35.0F, lp, true, 1.0F, p);
            double hp = p.headPitch * RAD;
            check(hp >= SwingMotion.HEAD_LOOK_PITCH_MIN - 1e-3 && hp <= SwingMotion.HEAD_LOOK_PITCH_MAX + 1e-3,
                    "Blick-Pitch begrenzt (" + hp + " bei " + lp + ")");
        }
        // Folgt deutlichem Umschauen zur Seite (rechts +, links -), weich begrenzt
        SwingMotion.sample(6.0F, 0, null, 0.0F, 90.0F, 0.0F, true, 1.0F, p);
        check(p.headYaw * RAD > yawMax - 3.0, "schaut nach rechts (" + p.headYaw * RAD + ")");
        SwingMotion.sample(6.0F, 0, null, 0.0F, -90.0F, 0.0F, true, 1.0F, p);
        check(p.headYaw * RAD < -yawMax + 3.0, "schaut nach links (" + p.headYaw * RAD + ")");

        // Kopf gleicht die Kippung (Schaukeln + Lehnen) teilweise aus: Blick in der Welt bleibt ruhig
        double worstWorld = 0.0;
        for (int ms = 5000; ms < 20000; ms += 5) {
            SwingMotion.sample(ms / 1000.0F, p);
            double world = p.headPitch * RAD - (p.swingDeg + p.leanDeg);
            worstWorld = Math.max(worstWorld, Math.abs(world));
        }
        check(worstWorld <= 30.0, "Kopf schaut in der Welt ungefähr nach vorne (höchstens " + worstWorld + "° geneigt)");
    }

    /** Erhobene Arme an den Seilen lassen den Kopf frei (Innenseite der Arme neben dem Kopf, nicht davor). */
    private static void swingArmsClearHead() {
        SwingMotion.Pose p = new SwingMotion.Pose();
        double minInner = 1e9;
        double[] o = new double[3];
        for (int ms = (int) (SwingMotion.SIT_END * 1000); ms < 20_000; ms += 7) {
            SwingMotion.sample(ms / 1000.0F, p);
            for (boolean right : new boolean[]{true, false}) {
                float pitch = right ? p.rArmPitch : p.lArmPitch, roll = right ? p.rArmRoll : p.lArmRoll;
                for (double ly = -2.0; ly <= 10.0; ly += 0.5) {
                    for (double lz = -2.0; lz <= 2.0; lz += 1.0) {
                        // Innenseite des Arms (zum Kopf hin)
                        CampfireMotion.rotZYX(right ? 1.0 : -1.0, ly, lz, pitch, 0.0F, roll, o);
                        double x = o[0] + (right ? -SwingMotion.SHOULDER_X : SwingMotion.SHOULDER_X);
                        double y = o[1] + SwingMotion.SHOULDER_Y;
                        if (y < -1.0 && y > -8.5) minInner = Math.min(minInner, Math.abs(x));
                    }
                }
            }
        }
        check(minInner >= 4.4, "Arme neben dem Kopf, nicht im Gesicht (Innenseite " + minInner + " px, Kopf 4 px)");
    }

    /** Hängematte und Picknick: Kopf springt beim Umschauen über ±180° nicht von einer Seite auf die andere. */
    private static void hammockPicnicHeadWrap() {
        HammockMotion.Pose h = new HammockMotion.Pose(), h2 = new HammockMotion.Pose();
        PicnicMotion.Pose c = new PicnicMotion.Pose(), c2 = new PicnicMotion.Pose();
        double worstH = 0.0, worstC = 0.0;
        for (float t : new float[]{3.0F, 9.5F, PicnicMotion.LOOP_START + 12.5F}) {
            double prevH = Double.NaN, prevC = Double.NaN;
            for (int k = -1440; k <= 1440; k++) {
                float ly = k * 0.25F;
                HammockMotion.sample(t, ly, 10.0F, true, h);
                PicnicMotion.sample(t, null, 0L, false, ly, 10.0F, true, c);
                double hy = h.headYaw * RAD, cy = c.headYaw * RAD;
                if (!Double.isNaN(prevH)) {
                    worstH = Math.max(worstH, Math.abs(hy - prevH));
                    worstC = Math.max(worstC, Math.abs(cy - prevC));
                }
                prevH = hy;
                prevC = cy;
                if (k % 41 == 0) {
                    float w = (float) HammockLogic.wrapDegrees(ly);
                    HammockMotion.sample(t, w, 10.0F, true, h2);
                    PicnicMotion.sample(t, null, 0L, false, w, 10.0F, true, c2);
                    near(hy, h2.headYaw * RAD, 1e-3, "Hängematte: Yaw unabhängig von ±360°");
                    near(cy, c2.headYaw * RAD, 1e-3, "Picknick: Yaw unabhängig von ±360°");
                }
            }
        }
        check(worstH <= 1.0, "Hängematte: Kopf-Yaw stetig über ±180° (" + worstH + ")");
        check(worstC <= 1.0, "Picknick: Kopf-Yaw stetig über ±180° (" + worstC + ")");
    }

    // ---------------- Schaukel: Sitzplätze ----------------

    private static SwingLogic.Participant part(String id, long start, double x, double z, float yaw) {
        return new SwingLogic.Participant(id, start, x, 64.0, z, yaw);
    }

    private static double[] seat1World(float yaw) {
        return HammockLogic.frameToWorld(SwingLogic.seatX(1), 0, 0, 0, 64, 0, yaw);
    }

    private static void seatJoinFree() {
        List<SwingLogic.SwingSet> sets = new ArrayList<>();
        List<SwingLogic.Participant> act = new ArrayList<>();
        act.add(part("aaaa", 1000, 0, 0, 0.0F));
        SwingLogic.Changes c = SwingLogic.update(sets, act);
        check(c.created.size() == 1 && sets.size() == 1, "erstes Gerüst entsteht");
        SwingLogic.SwingSet s = sets.get(0);
        check(s.seats[0] != null && s.seats[0].id.equals("aaaa"), "Aufbauer auf Sitz 0");
        // Sitz 1 bei Yaw 0 liegt rechts = -X
        double[] s1 = seat1World(0.0F);
        near(s1[0], -SwingLogic.SEAT_SPACING, 1e-9, "Sitz 1 rechts vom Aufbauer");
        // B steht 1,9 Blöcke vom freien Sitz entfernt (schräg), schaut woanders hin
        double bx = s1[0] - 1.2, bz = s1[2] + Math.sqrt(1.9 * 1.9 - 1.2 * 1.2);
        check(SwingLogic.checkStart(sets, "bbbb", bx, 64, bz, 90.0F) == SwingLogic.StartResult.JOIN, "freier Sitz in 1,9 → Hinsetzen");
        act.add(part("bbbb", 3000, bx, bz, 90.0F));
        c = SwingLogic.update(sets, act);
        check(c.created.isEmpty() && sets.size() == 1, "kein neues Gerüst");
        check(s.seats[1] != null && s.seats[1].id.equals("bbbb"), "B auf Sitz 1");
        check(s.owner().id.equals("aaaa"), "Besitzer = frühester Start");
        // Versatz (Spieler → Sitz) höchstens 2 Blöcke
        double[] sw = s.seatWorld(1);
        check(Math.hypot(sw[0] - bx, sw[2] - bz) <= 2.0 + 1e-9, "Versatz ≤ 2 Blöcke");
        // Ein dritter in der Nähe: kein freier Sitz mehr
        check(SwingLogic.nearestFreeSeat(sets, bx, 64, bz, 5.0, null) == null, "beide Sitze belegt");
        check(SwingLogic.setOf(sets, "bbbb", 3000) == s, "setOf findet das Gerüst");
        check(SwingLogic.setOf(sets, "bbbb", 3001) == null, "anderes Emote (Startzeit) nicht");
    }

    private static void seatRejectFar() {
        List<SwingLogic.SwingSet> sets = new ArrayList<>();
        SwingLogic.update(sets, List.of(part("aaaa", 1000, 0, 0, 0.0F)));
        double[] s1 = seat1World(0.0F);
        check(SwingLogic.checkStart(sets, "cccc", s1[0], 64, s1[2] + 2.05, 0.0F) == SwingLogic.StartResult.TOO_FAR,
                "freier Sitz in 2,05 → „Geh näher“");
        check(SwingLogic.checkStart(sets, "cccc", s1[0], 64, s1[2] + 2.0, 0.0F) == SwingLogic.StartResult.JOIN, "genau 2,0 → Hinsetzen");
        check(SwingLogic.checkStart(sets, "cccc", s1[0] - 8.0, 64, s1[2], 0.0F) == SwingLogic.StartResult.NEW, "weit weg → eigenes Gerüst");
        // eigenes altes Gerüst zählt nicht
        check(SwingLogic.checkStart(sets, "aaaa", 0.3, 64, 0.2, 0.0F) == SwingLogic.StartResult.NEW, "eigenes altes Gerüst ignoriert");
    }

    private static void seatOccupied() {
        List<SwingLogic.SwingSet> sets = new ArrayList<>();
        double[] s1 = seat1World(0.0F);
        List<SwingLogic.Participant> act = new ArrayList<>(List.of(part("aaaa", 1000, 0, 0, 0.0F),
                part("bbbb", 2000, s1[0] + 0.3, s1[2] + 0.5, 0.0F)));
        SwingLogic.update(sets, act);
        check(sets.size() == 1 && sets.get(0).memberCount() == 2, "Gerüst voll");
        // direkt daneben: besetzt (neues Gerüst würde überschneiden)
        check(SwingLogic.checkStart(sets, "dddd", s1[0], 64, s1[2] + 1.0, 0.0F) == SwingLogic.StartResult.OCCUPIED, "besetzt → abgelehnt");
        // weit genug weg (aber nahe am vollen Gerüst): eigenes Gerüst
        double[] far = HammockLogic.frameToWorld(SwingLogic.CENTER_X, 0, -3.4, 0, 64, 0, 0.0F);
        check(SwingLogic.checkStart(sets, "dddd", far[0] - (-SwingLogic.CENTER_X), 64, far[2], 0.0F) == SwingLogic.StartResult.NEW,
                "besetzt, aber Platz → eigenes Gerüst");
        act.add(part("dddd", 3000, s1[0], s1[2] + 1.0, 0.0F));
        SwingLogic.Changes c = SwingLogic.update(sets, act);
        check(c.created.size() == 1 && sets.size() == 2, "andere Clients: dritter bekommt ein eigenes Gerüst");
    }

    private static void seatOwnerLeaves() {
        List<SwingLogic.SwingSet> sets = new ArrayList<>();
        double[] s1 = seat1World(30.0F);
        HammockLogic.frameToWorld(0, 0, 0, 0, 64, 0, 30.0F);
        List<SwingLogic.Participant> act = new ArrayList<>(List.of(part("aaaa", 1000, 0, 0, 30.0F),
                part("bbbb", 2000, s1[0] + 0.4, s1[2], 100.0F)));
        SwingLogic.update(sets, act);
        SwingLogic.SwingSet s = sets.get(0);
        double x = s.x, z = s.z;
        float yaw = s.yaw;
        act.remove(0);
        SwingLogic.Changes c = SwingLogic.update(sets, act);
        check(c.removed.isEmpty() && sets.size() == 1 && sets.get(0) == s, "Gerüst bleibt, solange einer schaukelt");
        check(c.left.size() == 1 && c.left.get(0).id.equals("aaaa"), "Besitzer ist aufgestanden");
        near(s.x, x, 0, "Position fest");
        near(s.z, z, 0, "Position fest");
        check(s.yaw == yaw, "Ausrichtung fest");
        check(s.seats[0] == null && s.seats[1] != null, "Sitz 0 frei, B bleibt auf Sitz 1");
        check(s.owner().id.equals("bbbb"), "B ist jetzt Besitzer");
        // freier Sitz 0 kann wieder genommen werden
        act.add(part("eeee", 5000, 0.5, 0.5, 0.0F));
        SwingLogic.update(sets, act);
        check(s.seats[0] != null && s.seats[0].id.equals("eeee"), "neuer Spieler auf freiem Sitz 0");
        act.clear();
        c = SwingLogic.update(sets, act);
        check(sets.isEmpty() && c.removed.size() == 1, "alle weg → Gerüst verschwindet");
    }

    private static void seatOrderIndependent() {
        Random rnd = new Random(42);
        for (int trial = 0; trial < 60; trial++) {
            List<SwingLogic.Participant> ps = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                ps.add(part(String.format("%08x", rnd.nextInt()), 1000 + rnd.nextInt(5) * 100L,
                        rnd.nextDouble() * 6.0 - 3.0, rnd.nextDouble() * 6.0 - 3.0, rnd.nextFloat() * 360.0F - 180.0F));
            }
            String ref = null;
            for (int perm = 0; perm < 5; perm++) {
                List<SwingLogic.Participant> sh = new ArrayList<>(ps);
                Collections.shuffle(sh, rnd);
                List<SwingLogic.SwingSet> sets = new ArrayList<>();
                SwingLogic.update(sets, sh);
                String desc = describe(sets);
                if (ref == null) ref = desc;
                check(ref.equals(desc), "Zuordnung unabhängig von der Reihenfolge");
            }
            // schrittweise (in Startreihenfolge gesehen) ergibt dasselbe wie alle auf einmal
            List<SwingLogic.Participant> sorted = new ArrayList<>(ps);
            sorted.sort((a, b) -> a.startMs() != b.startMs() ? Long.compare(a.startMs(), b.startMs()) : a.id().compareTo(b.id()));
            List<SwingLogic.SwingSet> sets = new ArrayList<>();
            List<SwingLogic.Participant> seen = new ArrayList<>();
            for (SwingLogic.Participant p : sorted) {
                seen.add(p);
                SwingLogic.update(sets, seen);
            }
            check(ref.equals(describe(sets)), "schrittweise = auf einmal");
        }
    }

    private static String describe(List<SwingLogic.SwingSet> sets) {
        List<String> parts = new ArrayList<>();
        for (SwingLogic.SwingSet s : sets) {
            parts.add(s.createdStartMs + "@" + Math.round(s.x * 1000) + "," + Math.round(s.z * 1000) + ":"
                    + (s.seats[0] != null ? s.seats[0].id : "-") + "/" + (s.seats[1] != null ? s.seats[1].id : "-"));
        }
        Collections.sort(parts);
        return String.join(";", parts);
    }

    private static void seatTieBreakAndHeight() {
        List<SwingLogic.SwingSet> sets = new ArrayList<>();
        // gleiche Startzeit: kleinere UUID baut auf, die andere setzt sich dazu
        double[] s1 = seat1World(0.0F);
        SwingLogic.update(sets, List.of(part("bbbb", 1000, s1[0] + 0.2, s1[2], 0.0F), part("aaaa", 1000, 0, 0, 0.0F)));
        check(sets.size() == 1, "ein Gerüst");
        check(sets.get(0).seats[0].id.equals("aaaa") && sets.get(0).owner().id.equals("aaaa"), "UUID entscheidet bei Gleichstand");
        // Höhenunterschied > 0,8: kein Hinsetzen
        List<SwingLogic.SwingSet> sets2 = new ArrayList<>();
        SwingLogic.update(sets2, List.of(part("aaaa", 1000, 0, 0, 0.0F)));
        check(SwingLogic.nearestFreeSeat(sets2, s1[0], 64.0 + 0.9, s1[2], 2.0, null) == null, "Höhe > 0,8 → kein Sitz");
        check(SwingLogic.nearestFreeSeat(sets2, s1[0], 64.0 + 0.7, s1[2], 2.0, null) != null, "Höhe 0,7 → Sitz");
    }

    private static void swingEvents() {
        for (int n = 0; n < 20; n++) {
            near(Math.abs(SwingMotion.shape(SwingMotion.extremeTime(n))), 1.0, 1e-4, "Umkehrpunkt " + n);
            near(SwingMotion.shape(SwingMotion.bottomTime(n + 1)), 0.0, 1e-4, "Tiefpunkt " + n);
        }
        check(SwingMotion.extremeTime(0) > SwingMotion.SWING_START, "erster Umkehrpunkt nach dem Start");
        int creaks = 0;
        for (int n = 0; n < 30; n++) if (SwingMotion.creaksAt(n)) creaks++;
        check(creaks == 10, "jeder dritte Umkehrpunkt knarzt (" + creaks + ")");
        check(!SwingMotion.creaksAt(0) && !SwingMotion.whooshAt(1), "nicht gleich am Anfang");
    }

    // ---------------- Picknick ----------------

    private static void picnicCheckPoints() {
        double[][] f = PicnicLogic.checkPointsFrame();
        int ground = 0;
        for (double[] p : f) {
            if (p[3] > 0.5) {
                ground++;
                check(PicnicLogic.onBlanket(p[0], p[2], -0.05), "Bodenpunkt auf der Decke");
            }
        }
        check(ground == 9, "3×3 Bodenpunkte (flach)");
        double span = PicnicLogic.BLANKET_HALF * 2;
        check(span >= 1.5 && span <= 1.7, "Decke ca. 1,6 Blöcke (" + span + ")");
        check(PicnicLogic.onBlanket(PicnicLogic.BASKET_X, PicnicLogic.BASKET_Z, PicnicLogic.BASKET_HALF_Z), "Korb auf der Decke");
        check(PicnicLogic.onBlanket(PicnicLogic.BOTTLE_X, PicnicLogic.BOTTLE_Z, 0.1), "Flasche auf der Decke");
        check(PicnicLogic.onBlanket(PicnicLogic.NAPKIN_X, PicnicLogic.NAPKIN_Z, 0.12), "Serviette auf der Decke");
        check(PicnicLogic.onBlanket(0.0, 0.0, 0.3), "Spieler sitzt auf der Decke");
    }

    private static void picnicTimeline() {
        PicnicMotion.Pose p = new PicnicMotion.Pose();
        PicnicMotion.sample(0.0F, p);
        near(p.blanketX, 0.0, 1e-6, "Decke zusammengefaltet");
        near(p.basket, 0.0, 1e-6, "Korb noch nicht da");
        near(p.sit, 0.0, 1e-6, "steht");
        PicnicMotion.sample(PicnicMotion.LOOP_START, p);
        near(p.blanketX, 1.0, 1e-6, "Decke entfaltet (x)");
        near(p.blanketZ, 1.0, 1e-6, "Decke entfaltet (z)");
        near(p.basket, 1.0, 1e-6, "Korb da");
        near(p.items, 1.0, 1e-6, "Sachen da");
        near(p.sit, 1.0, 1e-6, "sitzt");
        near(p.drop, PicnicMotion.DROP, 1e-9, "abgesenkt");
        float L = PicnicMotion.LOOP_START;
        PicnicMotion.sample(L + 1.0F, p);
        check(!p.sandwichHeld && !p.bottleHeld, "am Anfang nichts in der Hand");
        PicnicMotion.sample(L + 1.4F, p);
        check(p.lid > 0.3F, "Deckel geht auf");
        PicnicMotion.sample(L + 2.6F, p);
        check(p.lid < 0.01F, "Deckel wieder zu");
        check(p.sandwichHeld && p.sandwichParts[0] == 1.0F && p.sandwichParts[2] == 1.0F, "ganzes Sandwich in der Hand");
        PicnicMotion.sample(L + PicnicMotion.BITES[0] + 0.2F, p);
        check(p.sandwichParts[2] == 0.0F && p.sandwichParts[1] == 1.0F, "erster Bissen: vorderes Teil weg");
        PicnicMotion.sample(L + PicnicMotion.BITES[1] + 0.2F, p);
        check(p.sandwichParts[1] == 0.0F && p.sandwichParts[0] == 1.0F, "zweiter Bissen");
        PicnicMotion.sample(L + PicnicMotion.BITES[2] + 0.2F, p);
        check(!p.sandwichHeld, "aufgegessen");
        PicnicMotion.sample(L + 9.3F, p);
        check(p.bottleHeld && p.bottleTilt < -90.0F, "trinkt (Flasche gekippt)");
        check(p.headPitch < -15.0F * Math.PI / 180.0, "Kopf beim Trinken zurück");
        PicnicMotion.sample(L + 12.0F, p);
        check(!p.bottleHeld && !p.sandwichHeld, "Ruhephase: Hände frei");
        check(PicnicMotion.LOOP >= 16.0F && PicnicMotion.LOOP <= 20.0F, "Schleife 16–20 s");
        // Ereignis-Hilfe
        check(PicnicMotion.crossed(L + 3.0F, L + 3.4F, PicnicMotion.BITES[0]), "Bissen erkannt");
        check(PicnicMotion.crossed(L + PicnicMotion.LOOP + 3.0F, L + PicnicMotion.LOOP + 3.4F, PicnicMotion.BITES[0]), "Bissen in der 2. Schleife");
        check(!PicnicMotion.crossed(L + 3.4F, L + 4.0F, PicnicMotion.BITES[0]), "kein doppelter Bissen");
        check(!PicnicMotion.crossed(0.0F, L, PicnicMotion.BITES[0]), "kein Bissen vor der Schleife");
    }

    private static void picnicLoopSeam() {
        PicnicMotion.Pose a = new PicnicMotion.Pose(), b = new PicnicMotion.Pose();
        for (int k = 1; k < 4; k++) {
            float seam = PicnicMotion.LOOP_START + k * PicnicMotion.LOOP;
            PicnicMotion.sample(seam - 0.0005F, a);
            PicnicMotion.sample(seam + 0.0005F, b);
            List<double[]> pa = picnicPoints(a), pb = picnicPoints(b);
            for (int i = 0; i < pa.size(); i++) check(dist(pa.get(i), pb.get(i)) < 0.004, "Schleifennaht " + k);
        }
    }

    private static void picnicDeterministic() {
        PicnicMotion.Pose a = new PicnicMotion.Pose(), b = new PicnicMotion.Pose();
        for (int ms = 0; ms < 60000; ms += 11) {
            float t = ms / 1000.0F;
            PicnicMotion.sample(t, "0123abcd", 1_700_000_000_000L, true, 0, 0, false, a);
            PicnicMotion.sample(t, "0123abcd", 1_700_000_000_000L, true, 0, 0, false, b);
            check(a.rArmPitch == b.rArmPitch && a.headYaw == b.headYaw && a.lean == b.lean && a.flickAnt == b.flickAnt,
                    "gleiche Zeit, gleiche Pose (" + t + ")");
        }
    }

    private static void picnicContinuity() {
        String id = "feedbeef";
        long seed = 1_700_000_123_456L;
        PicnicMotion.Pose prev = new PicnicMotion.Pose(), cur = new PicnicMotion.Pose();
        PicnicMotion.sample(0.0F, id, seed, true, 0, 0, false, prev);
        List<double[]> pp = picnicPoints(prev);
        double worst = 0.0, worstProp = 0.0;
        float worstAt = 0;
        double[] g0 = new double[3], g1 = new double[3];
        int flicks = 0;
        for (int ms = 1; ms <= 80_000; ms++) {
            float t = ms / 1000.0F;
            PicnicMotion.sample(t, id, seed, true, 0, 0, false, cur);
            if (cur.flickAnt >= 0 && prev.flickAnt < 0) flicks++;
            List<double[]> cp = picnicPoints(cur);
            for (int i = 0; i < cp.size(); i++) {
                double d = dist(pp.get(i), cp.get(i));
                if (d > worst) { worst = d; worstAt = t; }
            }
            worstProp = Math.max(worstProp, Math.abs(cur.blanketX - prev.blanketX));
            worstProp = Math.max(worstProp, Math.abs(cur.blanketZ - prev.blanketZ));
            worstProp = Math.max(worstProp, Math.abs(cur.basket - prev.basket));
            worstProp = Math.max(worstProp, Math.abs(cur.items - prev.items));
            worstProp = Math.max(worstProp, Math.abs(cur.lid - prev.lid));
            for (int k = 0; k < 3; k++) worstProp = Math.max(worstProp, Math.abs(cur.sandwichParts[k] - prev.sandwichParts[k]));
            check(Math.abs(cur.bottleTilt - prev.bottleTilt) < 1.0F, "Flasche kippt flüssig " + t);
            // Sandwich und Flasche folgen flüssig der Hand
            if (cur.bottleHeld && prev.bottleHeld) {
                PicnicMotion.gripPoint(prev, false, g0);
                PicnicMotion.gripPoint(cur, false, g1);
                check(dist(g0, g1) * KPX <= MAX_POINT_STEP, "Flasche flüssig " + t);
            }
            pp = cp;
            PicnicMotion.Pose sw = prev; prev = cur; cur = sw;
        }
        check(worst <= MAX_POINT_STEP, "Picknick flüssig (" + worst + " Blöcke/ms bei " + worstAt + ")");
        check(worstProp <= 0.05, "Requisiten flüssig (" + worstProp + ")");
        check(flicks >= 1, "mindestens einmal weggeschnippt in 80 s (" + flicks + ")");
    }

    private static void picnicSitsOnBlanket() {
        PicnicMotion.Pose p = new PicnicMotion.Pose();
        double lowest = 1e9, lowestSitting = 1e9;
        String what = "";
        for (int ms = 0; ms < 60_000; ms += 3) {
            float t = ms / 1000.0F;
            PicnicMotion.sample(t, "cafe0001", 1_700_000_000_000L, true, 0, 0, false, p);
            for (double[] q : picnicPoints(p)) {
                if (q[1] < lowest) {
                    lowest = q[1];
                    what = HammockMotion.Part.values()[(int) q[3]] + " #" + (int) q[4] + " bei " + t;
                }
                if (t >= PicnicMotion.SIT_END) lowestSitting = Math.min(lowestSitting, q[1]);
            }
        }
        check(lowest >= PicnicLogic.BLANKET_TOP - 0.3 * PX, "nichts unter der Decke (" + lowest / PX + " px, " + what + ")");
        check(lowestSitting <= PicnicLogic.BLANKET_TOP + 1.5 * PX, "sitzt auf der Decke (" + lowestSitting / PX + " px)");
        // Schneidersitz: Beine gekreuzt (Füße auf der anderen Seite)
        PicnicMotion.sample(5.0F, p);
        double[] rFoot = PicnicMotion.partToFrame(p, HammockMotion.Part.RIGHT_LEG, 0, 12, 0, new double[3]);
        double[] lFoot = PicnicMotion.partToFrame(p, HammockMotion.Part.LEFT_LEG, 0, 12, 0, new double[3]);
        check(rFoot[0] < 0.0 && lFoot[0] > 0.0, "Beine gekreuzt (rechter Fuß links, linker Fuß rechts)");
        check(rFoot[2] < -0.3 && lFoot[2] < -0.3, "Füße vor dem Körper");
        check(PicnicLogic.onBlanket(rFoot[0], rFoot[2], 0.05) && PicnicLogic.onBlanket(lFoot[0], lFoot[2], 0.05), "Füße auf der Decke");
        // Füße nicht im Korb
        for (double[] f : new double[][]{rFoot, lFoot}) {
            check(Math.abs(f[0] - PicnicLogic.BASKET_X) > PicnicLogic.BASKET_HALF_X + 0.05
                    || Math.abs(f[2] - PicnicLogic.BASKET_Z) > PicnicLogic.BASKET_HALF_Z + 0.05, "Fuß nicht im Korb");
        }
    }

    private static void picnicReach() {
        PicnicMotion.Pose p = new PicnicMotion.Pose();
        double[] g = new double[3], m = new double[3];
        float L = PicnicMotion.LOOP_START;
        // Flasche: Hand am Griffpunkt der stehenden Flasche beim Aufnehmen und Abstellen
        double[] bottle = PicnicMotion.frameToModel(PicnicLogic.BOTTLE_X, PicnicLogic.BOTTLE_GRIP_Y, PicnicLogic.BOTTLE_Z, new double[3]);
        for (float u : new float[]{PicnicMotion.BOTTLE_PICK, PicnicMotion.BOTTLE_PUT}) {
            PicnicMotion.sample(L + u, p);
            PicnicMotion.gripPoint(p, false, g);
            check(dist(g, bottle) <= 1.5, "Hand an der Flasche bei " + u + " (" + dist(g, bottle) + " px)");
        }
        // Korb: Hand über/im Korb
        PicnicMotion.sample(L + 1.7F, p);
        PicnicMotion.gripPoint(p, true, g);
        double[] gw = PicnicMotion.modelToFrame(p, g[0], g[1], g[2], new double[3]);
        check(Math.abs(gw[0] - PicnicLogic.BASKET_X) < PicnicLogic.BASKET_HALF_X + 0.06
                && Math.abs(gw[2] - PicnicLogic.BASKET_Z) < PicnicLogic.BASKET_HALF_Z + 0.06, "Hand am Korb (" + gw[0] + ", " + gw[2] + ")");
        check(gw[1] < PicnicLogic.BASKET_H + 0.12, "Hand greift in den Korb (" + gw[1] + ")");
        // Bissen: Sandwich (Griff) vor dem Mund
        for (float b : PicnicMotion.BITES) {
            PicnicMotion.sample(L + b, p);
            PicnicMotion.gripPoint(p, true, g);
            PicnicMotion.mouthPoint(p, m);
            check(dist(g, m) <= 4.5, "Sandwich am Mund (" + dist(g, m) + " px)");
            check(g[2] < m[2], "Sandwich vor dem Gesicht");
        }
        // Trinken: Flasche am Mund
        PicnicMotion.sample(L + 9.3F, p);
        PicnicMotion.gripPoint(p, false, g);
        PicnicMotion.mouthPoint(p, m);
        check(dist(g, m) <= 6.0, "Flasche am Mund (" + dist(g, m) + " px)");
    }

    private static void antsSchedule() {
        for (String id : new String[]{"aaaa0000", "bbbb1111", null}) {
            for (long seed : new long[]{0L, 1_700_000_000_000L, 1_700_000_777_000L}) {
                float prevStart = -1;
                for (int k = 0; k < 40; k++) {
                    PicnicLogic.Ant a = PicnicLogic.ant(id, seed, k);
                    PicnicLogic.Ant b = PicnicLogic.ant(id, seed, k);
                    check(a.equals(b), "Ameise deterministisch");
                    if (prevStart >= 0) {
                        float gap = a.start() - prevStart;
                        check(gap >= 10.0F - 1e-3 && gap <= 20.0F + 1e-3, "Abstand 10–20 s (" + gap + ")");
                    }
                    prevStart = a.start();
                    check(a.end() < PicnicLogic.ANT_FIRST + (k + 1) * PicnicLogic.ANT_SLOT, "Ameise vor dem nächsten Fenster weg");
                    check(PicnicLogic.antSlot(a.start()) == k && PicnicLogic.antSlot(a.end()) == k, "Fenster");
                }
            }
        }
        // anderer Zeitbasis-Wert → andere Ameisen; gerundete Zeitbasis (±400 ms) → gleiche
        PicnicLogic.Ant a = PicnicLogic.ant("aaaa0000", 1_700_000_000_000L, 3);
        PicnicLogic.Ant b = PicnicLogic.ant("aaaa0000", 1_700_000_000_400L, 3);
        PicnicLogic.Ant c = PicnicLogic.ant("aaaa0000", 1_700_000_050_000L, 3);
        check(a.equals(b), "Rundung der Zeitbasis");
        check(!a.equals(c), "andere Zeitbasis, andere Ameise");
    }

    private static void antsOnBlanket() {
        double[] st = new double[6];
        for (int k = 0; k < 60; k++) {
            PicnicLogic.Ant a = PicnicLogic.ant("abcdef01", 1_700_000_000_000L + k * 977L, k);
            for (float t = a.start(); t <= a.end(); t += 0.01F) {
                PicnicLogic.antState(a, t, st);
                if (st[4] < 0.5) continue;
                if (st[5] > 0.5) continue; // fliegt (weggeschnippt)
                check(PicnicLogic.onBlanket(st[0], st[2], 0.02), "Ameise auf der Decke (" + st[0] + ", " + st[2] + ")");
                near(st[1], PicnicLogic.BLANKET_TOP, 1e-9, "krabbelt auf der Oberseite");
                // nicht unter Korb oder Flasche
                check(Math.abs(st[0] - PicnicLogic.BASKET_X) > PicnicLogic.BASKET_HALF_X || Math.abs(st[2] - PicnicLogic.BASKET_Z) > PicnicLogic.BASKET_HALF_Z + 0.03,
                        "nicht durch den Korb");
                check(Math.hypot(st[0] - PicnicLogic.BOTTLE_X, st[2] - PicnicLogic.BOTTLE_Z) > 0.15, "nicht durch die Flasche");
            }
            // endet am Rand (oder weggeschnippt)
            if (!a.flicked()) {
                PicnicLogic.antState(a, a.end(), st);
                check(Math.abs(Math.abs(st[0]) - PicnicLogic.ANT_X) < 2e-3, "verschwindet am Rand (" + st[0] + ")");
            }
            PicnicLogic.antState(a, a.start() - 0.01F, st);
            check(st[4] < 0.5, "vorher unsichtbar");
            PicnicLogic.antState(a, a.end() + 0.01F, st);
            check(st[4] < 0.5, "nachher unsichtbar");
        }
    }

    private static void antsFlick() {
        int flicked = 0, total = 0;
        for (int k = 0; k < 200; k++) {
            PicnicLogic.Ant a = PicnicLogic.ant("77777777", 1_700_000_000_000L, k);
            total++;
            if (!a.flicked()) continue;
            flicked++;
            float lt = PicnicMotion.loopTime(a.flickAt());
            check(lt >= PicnicMotion.FLICK_FROM && lt <= PicnicMotion.FLICK_TO, "Schnippen nur in der Ruhephase");
            // Pose: schnippt diese Ameise
            PicnicMotion.Pose p = new PicnicMotion.Pose();
            PicnicMotion.sample(a.flickAt(), "77777777", 1_700_000_000_000L, true, 0, 0, false, p);
            check(p.flickAnt == k, "Pose schnippt Ameise " + k);
            check(!p.sandwichHeld && !p.bottleHeld, "Hände frei beim Schnippen");
        }
        check(flicked > 0 && flicked < total, "manchmal geschnippt (" + flicked + "/" + total + ")");
    }

    private static void picnicEvents() {
        // Bissen und Schlucke liegen in den richtigen Phasen
        PicnicMotion.Pose p = new PicnicMotion.Pose();
        for (float g : PicnicMotion.GULPS) {
            PicnicMotion.sample(PicnicMotion.LOOP_START + g, p);
            check(p.bottleHeld && p.bottleTilt < -80.0F, "Schluck mit gekippter Flasche");
        }
        for (float b : PicnicMotion.BITES) {
            PicnicMotion.sample(PicnicMotion.LOOP_START + b - 0.01F, p);
            check(p.sandwichHeld, "Bissen mit Sandwich");
        }
    }

    // ---------------- Modelle ----------------

    private static final String ROOT = "src/main/resources/assets/bameclient/emotes/";

    private record Cube(String bone, double[] origin, double[] size, double[] pivot, double[] rotation) {}

    private static List<Cube> loadCubes(String file) {
        String text;
        try {
            text = Files.readString(Path.of(ROOT + file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Modell fehlt: " + file + " (" + e.getMessage() + ")");
        }
        List<Cube> out = new ArrayList<>();
        Pattern bone = Pattern.compile("\"name\":\\s*\"([^\"]+)\"");
        Pattern cube = Pattern.compile("\\{\"origin\":\\s*\\[([^\\]]*)\\],\\s*\"size\":\\s*\\[([^\\]]*)\\]"
                + "(?:,\\s*\"pivot\":\\s*\\[([^\\]]*)\\],\\s*\"rotation\":\\s*\\[([^\\]]*)\\])?");
        String currentBone = null;
        for (String line : text.split("\n")) {
            Matcher b = bone.matcher(line);
            if (b.find()) currentBone = b.group(1);
            Matcher c = cube.matcher(line);
            if (c.find()) {
                out.add(new Cube(currentBone, nums(c.group(1)), nums(c.group(2)),
                        c.group(3) != null ? nums(c.group(3)) : null, c.group(4) != null ? nums(c.group(4)) : null));
            }
        }
        check(!out.isEmpty(), "Würfel gelesen: " + file);
        return out;
    }

    private static double[] nums(String s) {
        String[] p = s.split(",");
        double[] v = new double[p.length];
        for (int i = 0; i < p.length; i++) v[i] = Double.parseDouble(p[i].trim());
        return v;
    }

    /** Punkt (lokal im Würfel) in den Knochenraum (wie BedrockCosmeticModel mit blockbenchAxisFix). */
    private static double[] xf(Cube c, double[] p) {
        if (c.rotation() == null) return p.clone();
        double[] pv = c.pivot();
        double x = p[0] - pv[0], y = p[1] - pv[1], z = p[2] - pv[2];
        double a = Math.toRadians(-c.rotation()[0]);
        double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
        double b = Math.toRadians(c.rotation()[1]);
        double x2 = x * Math.cos(b) + z1 * Math.sin(b), z2 = -x * Math.sin(b) + z1 * Math.cos(b);
        double g = Math.toRadians(-c.rotation()[2]);
        double x3 = x2 * Math.cos(g) - y1 * Math.sin(g), y3 = x2 * Math.sin(g) + y1 * Math.cos(g);
        return new double[]{x3 + pv[0], y3 + pv[1], z2 + pv[2]};
    }

    private static List<double[]> corners(Cube c) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            out.add(xf(c, new double[]{c.origin()[0] + ((i & 1) != 0 ? c.size()[0] : 0),
                    c.origin()[1] + ((i & 2) != 0 ? c.size()[1] : 0), c.origin()[2] + ((i & 4) != 0 ? c.size()[2] : 0)}));
        }
        return out;
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} (Bedrock-px) aller Würfel eines Knochens (null = alle). */
    private static double[] bounds(List<Cube> cubes, String bone) {
        double[] b = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
        for (Cube c : cubes) {
            if (bone != null && !bone.equals(c.bone())) continue;
            for (double[] p : corners(c)) {
                for (int k = 0; k < 3; k++) {
                    b[k] = Math.min(b[k], p[k]);
                    b[k + 3] = Math.max(b[k + 3], p[k]);
                }
            }
        }
        return b;
    }

    private static void swingModels() {
        // Gerüst: im Modell ist Bedrock-x = -Rahmen-x; Ursprung = Gerüstmitte (Rahmen-x CENTER_X) am Boden
        List<Cube> frame = loadCubes("swing/swing_frame.geo.json");
        double[] legs = bounds(frame, "legs");
        near(legs[1], 0.0, 1e-6, "Beine stehen auf dem Boden");
        near(legs[3] * PX, SwingLogic.LEG_FOOT_X + SwingLogic.LEG_HALF, 0.06, "Bein-Füße außen (x)");
        near(legs[5] * PX, SwingLogic.LEG_FOOT_Z + SwingLogic.LEG_HALF, 0.08, "Bein-Füße vorne/hinten (z)");
        double[] beam = bounds(frame, "beam");
        near((beam[1] + beam[4]) / 2.0 * PX, SwingLogic.BEAM_Y, 0.02, "Balken auf Höhe BEAM_Y");
        near(beam[3] * PX, SwingLogic.BEAM_HALF_LEN, 0.03, "Balkenlänge");
        check(legs[4] <= beam[4] + 0.5, "Beine enden am Balken");
        // Sitz + Seile: Ursprung = Sitzmitte am Boden, Seile vom Haken (PIVOT_Y) bis zum Brett
        List<Cube> seat = loadCubes("swing/swing_seat.geo.json");
        double[] plank = bounds(seat, "seat");
        near(plank[4] * PX, SwingLogic.SEAT_TOP_Y, 0.01, "Sitzfläche auf SEAT_TOP_Y");
        near(plank[3] * PX, SwingLogic.SEAT_HALF_W, 0.02, "Brettbreite");
        near(plank[5] * PX, SwingLogic.SEAT_HALF_D, 0.02, "Bretttiefe");
        double[] ropes = bounds(seat, "ropes");
        near(ropes[4] * PX, SwingLogic.PIVOT_Y + 0.05, 0.06, "Seile bis zum Haken");
        near(ropes[3] * PX, SwingLogic.ROPE_X + 0.03, 0.04, "Seile bei ROPE_X");
        check(ropes[1] * PX < SwingLogic.SEAT_TOP_Y, "Seile bis ins Brett");
    }

    private static void picnicModels() {
        List<Cube> blanket = loadCubes("picnic/picnic_blanket.geo.json");
        double[] b = bounds(blanket, null);
        near(b[4] * PX, PicnicLogic.BLANKET_TOP + 0.3 * PX, 0.4 * PX, "Decke liegt flach (Oberseite)");
        check(b[1] >= -1e-6, "Decke nicht im Boden");
        near((b[3] - b[0]) * PX, 2 * PicnicLogic.BLANKET_HALF, 0.07, "Deckengröße x");
        near((b[5] - b[2]) * PX, 2 * PicnicLogic.BLANKET_HALF, 0.07, "Deckengröße z");
        List<Cube> basket = loadCubes("picnic/picnic_basket.geo.json");
        double[] k = bounds(basket, "body");
        near(k[1], 0.0, 1e-6, "Korb steht auf dem Boden");
        near(k[3] * PX, PicnicLogic.BASKET_HALF_X, 0.4 * PX, "Korbbreite");
        near(k[5] * PX, PicnicLogic.BASKET_HALF_Z, 0.4 * PX, "Korblänge");
        double[] h = bounds(basket, "handle");
        near(h[4] * PX, PicnicLogic.BASKET_HANDLE_TOP, 0.4 * PX, "Henkelhöhe");
        check(bounds(basket, "lid")[4] * PX <= PicnicLogic.BASKET_H + 0.6 * PX, "Deckel liegt auf");
        List<Cube> items = loadCubes("picnic/picnic_items.geo.json");
        for (String bone : new String[]{"bottle", "s0", "s1", "s2", "apple", "napkin", "ant"}) {
            check(bounds(items, bone)[0] < 1e8, "Teil " + bone + " vorhanden");
        }
        double[] bottle = bounds(items, "bottle");
        near(bottle[1], -PicnicLogic.BOTTLE_GRIP_Y / PX, 0.01, "Flaschenboden unter dem Griff");
        double[] ant = bounds(items, "ant");
        double len = Math.max(ant[3] - ant[0], ant[5] - ant[2]);
        check(len >= 2.0 && len <= 3.2, "Ameise 2–3 px (" + len + ")");
        near(ant[1], 0.0, 0.05, "Ameise steht auf dem Boden");
        near(bounds(items, "napkin")[1], 0.0, 1e-6, "Serviette liegt auf");
    }
}

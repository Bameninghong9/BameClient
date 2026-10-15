package com.bame.client.emotes;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Prüft ohne Minecraft den Hexenbesen:
 * <ul>
 *   <li>Flugbahn ({@link WitchBroomLogic}): Kreis um den Startpunkt bleibt im Radius 0,6–1, flüssig in 1-ms-Schritten
 *       (keine Sprünge in Position, Richtung, Neigung, Höhe), Besen zeigt in Flugrichtung, Neigung in die Kurve
 *       plausibel, Höhe 0,6–0,8, deterministisch.</li>
 *   <li>Sitz-Pose ({@link WitchBroomMotion}): Schritt liegt auf dem Stiel, Hände am Stiel (vor dem Reiter, mit Abstand
 *       zueinander), kein Körperteil im Besen (gegen die echten Würfel des Modells, auch beim Hereingleiten), nichts im
 *       Boden, Kopf unter 2 Blöcken, leichte Vorlage, kein Herausfallen.</li>
 *   <li>Rein optisch: Bahn und Pose sind reine Funktionen der Emote-Zeit; der Emote-Code ändert nie Position,
 *       Bewegung oder Hitbox des Spielers und sendet nichts an den Server (Quelltext-Prüfung).</li>
 *   <li>Modell und Textur passen zu den Maßen.</li>
 * </ul>
 */
public class WitchBroomTest {

    private static int checks = 0;
    private static final double PX = 1.0 / 16.0;
    private static final double KPX = HammockLogic.BLOCKS_PER_PX;
    private static final String ROOT = "src/main/resources/assets/bameclient/";
    private static final String SRC = "src/main/java/com/bame/client/";

    public static void main(String[] args) {
        emoteType();
        pathStaysInRadius();
        pathSmooth();
        headingFollowsFlight();
        bankPlausible();
        hoverHeight();
        deterministic();
        poseContinuity();
        seatOnStick();
        handsOnStick();
        noPenetration();
        aboveGround();
        headroomAndLean();
        armsClearBody();
        neverFallsOff();
        camera();
        effects();
        checkPoints();
        purelyVisual();
        model();
        System.out.println("WITCH BROOM TESTS PASSED (" + checks + " checks)");
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

    /** Punkte auf allen Flächen eines Körperteils (Raster etwa alle 1 px), lokal im Quader-Raum: {x, y, z}. */
    private static List<double[]> surface(HammockMotion.Part part, double step) {
        List<double[]> out = new ArrayList<>();
        int nx = Math.max(1, (int) Math.ceil(part.sx / step)), ny = Math.max(1, (int) Math.ceil(part.sy / step));
        int nz = Math.max(1, (int) Math.ceil(part.sz / step));
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= ny; j++) {
                for (int k = 0; k <= nz; k++) {
                    if (i != 0 && i != nx && j != 0 && j != ny && k != 0 && k != nz) continue;
                    out.add(new double[]{part.x0 + part.sx * i / nx, part.y0 + part.sy * j / ny, part.z0 + part.sz * k / nz});
                }
            }
        }
        return out;
    }

    private static final java.util.Map<HammockMotion.Part, List<double[]>> SURF = new java.util.EnumMap<>(HammockMotion.Part.class);

    private static List<double[]> surf(HammockMotion.Part part) {
        return SURF.computeIfAbsent(part, p -> surface(p, 1.0));
    }

    /** Hand-Bereich eines Arms (umschließt den Stiel beim Greifen): lokal y ab 5,5 px. */
    private static boolean isHand(HammockMotion.Part part, double ly) {
        return (part == HammockMotion.Part.RIGHT_ARM || part == HammockMotion.Part.LEFT_ARM) && ly > 5.5;
    }

    // ---------------- Emote-Typ ----------------

    private static void emoteType() {
        check(EmoteType.fromId("WITCH_BROOM") == EmoteType.WITCH_BROOM, "fromId WITCH_BROOM");
        check(EmoteType.fromId("witch_broom") == EmoteType.WITCH_BROOM, "fromId witch_broom (Karten-ID)");
        check(EmoteType.fromId("witch-broom") == EmoteType.WITCH_BROOM, "fromId witch-broom");
        check(EmoteType.fromId("WITCHBROOM") == EmoteType.WITCH_BROOM, "fromId WITCHBROOM");
        check("Witch Broom".equals(EmoteType.WITCH_BROOM.getDisplayName()), "Anzeigename Witch Broom");
        check(!EmoteType.WITCH_BROOM.getDescription().isBlank(), "Beschreibung");
        check(!EmoteType.WITCH_BROOM.allowsWalking(), "stationär (Abbruch bei Bewegung wie die anderen)");
        check(!EmoteType.WITCH_BROOM.isHidden(), "auswählbar");
    }

    // ---------------- Flugbahn ----------------

    private static void pathStaysInRadius() {
        check(WitchBroomLogic.RADIUS >= 0.6 && WitchBroomLogic.RADIUS <= 1.0, "Radius 0,6–1 Block");
        double[] p = new double[3];
        WitchBroomLogic.position(0.0F, p);
        near(Math.hypot(p[0], p[2]), 0.0, 1e-12, "beginnt am Startpunkt");
        double maxR = 0.0, minRSteady = 1e9;
        double cx = 0, cz = 0;
        int n = 0;
        float steady = WitchBroomLogic.FLY_START + WitchBroomLogic.SPIRAL;
        for (int ms = 0; ms <= 60_000; ms++) {
            float t = ms / 1000.0F;
            WitchBroomLogic.position(t, p);
            double r = Math.hypot(p[0], p[2]);
            maxR = Math.max(maxR, r);
            if (t >= steady) {
                minRSteady = Math.min(minRSteady, r);
                cx += p[0];
                cz += p[2];
                n++;
            }
        }
        check(maxR <= WitchBroomLogic.RADIUS + 1e-9, "Kreis bleibt im Radius (" + maxR + ")");
        near(minRSteady, WitchBroomLogic.RADIUS, 1e-6, "nach der Spirale genau auf dem Kreis");
        // Mittelpunkt der Bahn = Startpunkt (über ganze Runden gemittelt)
        check(Math.hypot(cx / n, cz / n) < 0.05, "Kreis um den Startpunkt (" + cx / n + ", " + cz / n + ")");
        // Eine Runde dauert PERIOD
        float t0 = 10.0F;
        double[] a = WitchBroomLogic.position(t0, new double[3]), b = WitchBroomLogic.position(t0 + WitchBroomLogic.PERIOD, new double[3]);
        near(a[0], b[0], 1e-5, "periodisch x");
        near(a[2], b[2], 1e-5, "periodisch z");
        check(WitchBroomLogic.PERIOD >= 5.0F && WitchBroomLogic.PERIOD <= 10.0F, "ruhige Runde (5–10 s)");
        double speed = 2.0 * Math.PI * WitchBroomLogic.RADIUS / WitchBroomLogic.PERIOD;
        check(speed < 1.2, "sanftes Tempo (" + speed + " Blöcke/s)");
    }

    private static void pathSmooth() {
        double[] prev = WitchBroomLogic.position(0.0F, new double[3]), cur = new double[3];
        double prevH = WitchBroomLogic.headingDeg(0.0F), prevB = WitchBroomLogic.bankDeg(0.0F), prevN = WitchBroomLogic.noseDeg(0.0F);
        double maxStep = 0, maxDh = 0, maxDb = 0, maxDn = 0;
        for (int ms = 1; ms <= 40_000; ms++) {
            float t = ms / 1000.0F;
            WitchBroomLogic.position(t, cur);
            double[] step = {cur[0] - prev[0], cur[1] - prev[1], cur[2] - prev[2]};
            double s = Math.sqrt(step[0] * step[0] + step[1] * step[1] + step[2] * step[2]);
            maxStep = Math.max(maxStep, s);
            double h = WitchBroomLogic.headingDeg(t), b = WitchBroomLogic.bankDeg(t), nd = WitchBroomLogic.noseDeg(t);
            maxDh = Math.max(maxDh, Math.abs(h - prevH));
            maxDb = Math.max(maxDb, Math.abs(b - prevB));
            maxDn = Math.max(maxDn, Math.abs(nd - prevN));
            prev = cur.clone();
            prevH = h;
            prevB = b;
            prevN = nd;
        }
        check(maxStep < 0.0012, "keine Sprünge in der Position (max " + maxStep + " Blöcke/ms)");
        // Beschleunigung über 5-ms-Schritte (unempfindlich gegen float-Rundung der Zeit): höchstens ca. 2 Blöcke/s²
        double maxA = 0;
        double[] p0 = WitchBroomLogic.position(0.0F, new double[3]), p1 = WitchBroomLogic.position(0.005F, new double[3]);
        for (int ms = 10; ms <= 40_000; ms += 5) {
            double[] p2 = WitchBroomLogic.position(ms / 1000.0F, new double[3]);
            double ax = p2[0] - 2 * p1[0] + p0[0], ay = p2[1] - 2 * p1[1] + p0[1], az = p2[2] - 2 * p1[2] + p0[2];
            maxA = Math.max(maxA, Math.sqrt(ax * ax + ay * ay + az * az) / (0.005 * 0.005));
            p0 = p1;
            p1 = p2;
        }
        check(maxA < 2.5, "stetige Geschwindigkeit, kein Ruck (max " + maxA + " Blöcke/s²)");
        check(maxDh < 0.12, "Flugrichtung dreht stetig (max " + maxDh + "°/ms)");
        check(maxDb < 0.02, "Neigung ändert sich stetig (max " + maxDb + "°/ms)");
        check(maxDn < 0.01, "Nase nickt stetig (max " + maxDn + "°/ms)");
    }

    private static void headingFollowsFlight() {
        near(WitchBroomLogic.headingDeg(0.0F), 0.0, 1e-12, "beim Start nach vorne");
        double worst = 0.0;
        for (int ms = (int) (WitchBroomLogic.FLY_START * 1000) + 50; ms <= 30_000; ms += 7) {
            float t = ms / 1000.0F;
            double[] a = WitchBroomLogic.position(t - 0.0005F, new double[3]), b = WitchBroomLogic.position(t + 0.0005F, new double[3]);
            double vx = b[0] - a[0], vz = b[2] - a[2];
            if (Math.hypot(vx, vz) < 1e-6 * 0.05) continue;
            double h = Math.toRadians(WitchBroomLogic.headingDeg(t));
            // Ry(h) dreht vorne (0, 0, -1) auf (-sin h, 0, -cos h)
            double fx = -Math.sin(h), fz = -Math.cos(h);
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (fx * vx + fz * vz) / Math.hypot(vx, vz)))));
            worst = Math.max(worst, ang);
        }
        check(worst < 1.5, "Besen zeigt in Flugrichtung (max Abweichung " + worst + "°)");
    }

    private static void bankPlausible() {
        near(WitchBroomLogic.bankDeg(0.5F), 0.0, 1e-12, "keine Neigung vor dem Flug");
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        double maxBank = 0;
        for (int ms = 0; ms <= 30_000; ms += 5) {
            float t = ms / 1000.0F;
            maxBank = Math.max(maxBank, Math.abs(WitchBroomLogic.bankDeg(t)));
            if (t < WitchBroomLogic.FLY_START + WitchBroomLogic.SPIRAL) continue;
            WitchBroomMotion.sample(t, p);
            // Oben-Vektor des Besens neigt sich zur Kreismitte (Rechtskurve: Mitte rechts)
            double[] up = {0, 1, 0};
            WitchBroomMotion.broomRotate(p, up);
            double toCx = -p.bx, toCz = -p.bz;
            check(up[0] * toCx + up[2] * toCz > 0.0, "Neigung in die Kurve bei " + ms);
            double tilt = Math.toDegrees(Math.acos(up[1]));
            check(tilt > 10.0 && tilt < 35.0, "Neigung plausibel (" + tilt + "° inkl. Nase)");
        }
        check(maxBank <= 20.0 && maxBank >= 8.0, "Kurvenlage 8–20° (" + maxBank + ")");
    }

    private static void hoverHeight() {
        for (int ms = 0; ms <= 40_000; ms += 3) {
            float t = ms / 1000.0F;
            double y = WitchBroomLogic.altitude(t);
            check(y >= 0.6 && y <= 0.8, "Besen 0,6–0,8 über dem Boden (" + y + " bei " + ms + ")");
        }
        // leichtes Auf-und-Ab im Flug
        double lo = 9, hi = -9;
        for (int ms = 6000; ms <= 12_000; ms += 5) {
            double y = WitchBroomLogic.altitude(ms / 1000.0F);
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        check(hi - lo > 0.04 && hi - lo < 0.15, "leichtes Auf-und-Ab (" + (hi - lo) + ")");
    }

    private static void deterministic() {
        WitchBroomMotion.Pose a = new WitchBroomMotion.Pose(), b = new WitchBroomMotion.Pose();
        for (int ms = 0; ms < 30_000; ms += 11) {
            float t = ms / 1000.0F;
            WitchBroomMotion.sample(t, 20.0F, 5.0F, true, false, false, 1.0F, a);
            WitchBroomMotion.sample(t, 20.0F, 5.0F, true, false, false, 1.0F, b);
            check(a.bx == b.bx && a.bz == b.bz && a.qy == b.qy && a.rArmPitch == b.rArmPitch && a.headYaw == b.headYaw
                    && a.broomHeading == b.broomHeading, "gleiche Zeit, gleiche Pose (" + t + ")");
        }
    }

    // ---------------- Pose ----------------

    private static void poseContinuity() {
        WitchBroomMotion.Pose prev = new WitchBroomMotion.Pose(), cur = new WitchBroomMotion.Pose();
        WitchBroomMotion.sample(0.0F, prev);
        double worst = 0.0, worstBroom = 0.0;
        String where = "";
        for (int ms = 1; ms <= 16_000; ms++) {
            WitchBroomMotion.sample(ms / 1000.0F, cur);
            for (HammockMotion.Part part : HammockMotion.Part.values()) {
                for (double[] l : new double[][]{{part.x0, part.y0, part.z0}, {part.x0 + part.sx, part.y0 + part.sy, part.z0 + part.sz},
                        {part.x0, part.y0 + part.sy, part.z0 + part.sz}}) {
                    double d = dist(WitchBroomMotion.partToFrame(prev, part, l[0], l[1], l[2], new double[3]),
                            WitchBroomMotion.partToFrame(cur, part, l[0], l[1], l[2], new double[3]));
                    if (d > worst) {
                        worst = d;
                        where = part + " bei " + ms;
                    }
                }
            }
            if (cur.broomScale > 0.0F && prev.broomScale > 0.0F) {
                for (double z : new double[]{WitchBroomLogic.TIP_Z, 0.0, WitchBroomLogic.BRISTLE_END_Z}) {
                    worstBroom = Math.max(worstBroom, dist(WitchBroomMotion.drawnBroomToFrame(prev, 0, 0, z, new double[3]),
                            WitchBroomMotion.drawnBroomToFrame(cur, 0, 0, z, new double[3])));
                }
            }
            WitchBroomMotion.Pose t = prev;
            prev = cur;
            cur = t;
        }
        check(worst < 0.012, "Körper bewegt sich flüssig (max " + worst + " Blöcke/ms, " + where + ")");
        check(worstBroom < 0.015, "Besen bewegt sich flüssig (max " + worstBroom + ")");
    }

    private static void seatOnStick() {
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        double worst = 0.0;
        for (int ms = (int) (WitchBroomMotion.MOUNT_END * 1000); ms <= 40_000; ms += 3) {
            WitchBroomMotion.sample(ms / 1000.0F, p);
            // Schritt (vordere Unterkante des Rumpfs, Mitte) im Raum des Besens: genau über der Achse am Sitzpunkt
            double[] f = WitchBroomMotion.modelToFrame(p, 0.0, 12.0, -2.0, new double[3]);
            double[] b = WitchBroomMotion.frameToBroom(p, f[0], f[1], f[2], new double[3]);
            worst = Math.max(worst, Math.abs(b[1] - WitchBroomMotion.SEAT_Y) + Math.abs(b[0]) + Math.abs(b[2]));
            // Gesäß-Kanten links/rechts liegen auch auf dem Stiel (nicht seitlich daneben)
            for (double mx : new double[]{-4.0, 4.0}) {
                double[] e = WitchBroomMotion.frameToBroom(p, WitchBroomMotion.modelToFrame(p, mx, 12.0, -2.0, new double[3])[0],
                        WitchBroomMotion.modelToFrame(p, mx, 12.0, -2.0, new double[3])[1],
                        WitchBroomMotion.modelToFrame(p, mx, 12.0, -2.0, new double[3])[2], new double[3]);
                near(e[1], WitchBroomMotion.SEAT_Y, 0.3 * PX, "Unterkante waagerecht auf dem Stiel");
            }
            // hintere Unterkante liegt höher (Vorlage), nicht im Stiel
            double[] back = WitchBroomMotion.modelToFrame(p, 0.0, 12.0, 2.0, new double[3]);
            double[] bb = WitchBroomMotion.frameToBroom(p, back[0], back[1], back[2], new double[3]);
            check(bb[1] > WitchBroomMotion.SEAT_Y, "Gesäß hinten über dem Stiel");
        }
        check(worst <= 0.3 * PX, "Schritt sitzt auf dem Stiel (Abweichung " + worst / PX + " px)");
        check(WitchBroomMotion.SEAT_Y - WitchBroomLogic.STICK_R_MAX <= 0.1 * PX, "kein Spalt unter dem Schritt");
    }

    private static void handsOnStick() {
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        double worstAxis = 0.0, worstErr = 0.0, minGap = 1e9;
        double[] g = new double[3], h = new double[3];
        for (boolean slim : new boolean[]{false, true}) {
            for (int ms = (int) (WitchBroomMotion.MOUNT_END * 1000); ms <= 40_000; ms += 3) {
                WitchBroomMotion.sample(ms / 1000.0F, 0.0F, 0.0F, false, false, slim, 1.0F, p);
                worstErr = Math.max(worstErr, Math.max(p.rGripErr, p.lGripErr));
                double[][] grips = new double[2][];
                for (int k = 0; k < 2; k++) {
                    boolean right = k == 0;
                    WitchBroomMotion.gripPoint(p, right, right ? p.rArmPitch : p.lArmPitch, right ? p.rArmYaw : p.lArmYaw,
                            right ? p.rGripY : p.lGripY, g);
                    check(p.rArmRoll == 0.0F && p.lArmRoll == 0.0F, "Arme ohne Rollen (Griff-Rechnung)");
                    double[] f = WitchBroomMotion.modelToFrame(p, g[0], g[1], g[2], new double[3]);
                    double[] b = WitchBroomMotion.frameToBroom(p, f[0], f[1], f[2], new double[3]);
                    worstAxis = Math.max(worstAxis, Math.hypot(b[0], b[1]));
                    check(b[2] < -0.2 && b[2] > WitchBroomLogic.TIP_Z + 0.08, "Hand vorne am Stiel (z " + b[2] + ")");
                    grips[k] = g.clone();
                    float gy = right ? p.rGripY : p.lGripY;
                    check(gy >= WitchBroomMotion.GRIP_Y_MIN && gy <= WitchBroomMotion.GRIP_Y_MAX, "Stiel in der Faust");
                }
                minGap = Math.min(minGap, dist(grips[0], grips[1]));
            }
        }
        check(worstErr <= 0.3, "Arme erreichen den Stiel (Rest " + worstErr + " px)");
        check(worstAxis <= 0.5 * KPX, "Hände am Stiel (Abweichung " + worstAxis / KPX + " px)");
        check(minGap >= 4.2, "Fäuste nebeneinander, nicht ineinander (" + minGap + " px)");
        // rechte Hand vorne, linke dahinter
        check(WitchBroomMotion.GRIP_R_Z < WitchBroomMotion.GRIP_L_Z, "rechte Hand vorne");
    }

    /** Besen-Würfel (echtes Modell) im Besen-Raum. */
    private record BCube(String bone, double[] o, double[] s, double[] p, double[] r) {
        /** Eindringtiefe (px, positiv = innen) eines Punkts im Besen-Raum (Blöcke). */
        double depth(double[] q) {
            // Besen-Raum -> Bedrock-px, dann in den unrotierten Würfel (Umkehrung von Rz(-rz)·Ry(ry)·Rx(-rx))
            double[] v = {-q[0] * 16.0 - p[0], q[1] * 16.0 - p[1], q[2] * 16.0 - p[2]};
            v = rotZ(v, r[2]);
            v = rotY(v, -r[1]);
            v = rotX(v, r[0]);
            double x = v[0] + p[0], y = v[1] + p[1], z = v[2] + p[2];
            double d = Math.min(Math.min(x - o[0], o[0] + s[0] - x), Math.min(Math.min(y - o[1], o[1] + s[1] - y),
                    Math.min(z - o[2], o[2] + s[2] - z)));
            return d;
        }

        /** Ecken im Besen-Raum (Blöcke), wie BedrockCosmeticModel (blockbenchAxisFix) + bedrockSpace. */
        List<double[]> corners() {
            List<double[]> out = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double x = o[0] + ((i & 1) != 0 ? s[0] : 0), y = o[1] + ((i & 2) != 0 ? s[1] : 0), z = o[2] + ((i & 4) != 0 ? s[2] : 0);
                double[] v = {x - p[0], y - p[1], z - p[2]};
                v = rotX(v, -r[0]);
                v = rotY(v, r[1]);
                v = rotZ(v, -r[2]);
                out.add(new double[]{-(v[0] + p[0]) / 16.0, (v[1] + p[1]) / 16.0, (v[2] + p[2]) / 16.0});
            }
            return out;
        }
    }

    private static List<BCube> broomCubes;

    private static List<BCube> broomCubes() {
        if (broomCubes != null) return broomCubes;
        String text = read(ROOT + "emotes/witch_broom/witch_broom.geo.json");
        List<BCube> out = new ArrayList<>();
        Pattern bone = Pattern.compile("\"name\":\\s*\"([^\"]+)\"");
        Pattern cube = Pattern.compile("\\{\"origin\":\\s*\\[([^\\]]*)\\],\\s*\"size\":\\s*\\[([^\\]]*)\\]"
                + "(?:,\\s*\"pivot\":\\s*\\[([^\\]]*)\\],\\s*\"rotation\":\\s*\\[([^\\]]*)\\])?");
        String current = null;
        for (String line : text.split("\n")) {
            Matcher b = bone.matcher(line);
            if (b.find()) current = b.group(1);
            Matcher c = cube.matcher(line);
            if (c.find()) {
                out.add(new BCube(current, nums(c.group(1)), nums(c.group(2)),
                        c.group(3) != null ? nums(c.group(3)) : new double[3], c.group(4) != null ? nums(c.group(4)) : new double[3]));
            }
        }
        check(out.size() > 30, "Besen-Würfel gelesen (" + out.size() + ")");
        broomCubes = out;
        return out;
    }

    private static void noPenetration() {
        List<BCube> cubes = broomCubes();
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        double worst = -1e9;
        String where = "";
        double[] f = new double[3], b = new double[3];
        for (int ms = 0; ms <= 12_000; ms += (ms < 1500 ? 5 : 25)) {
            WitchBroomMotion.sample(ms / 1000.0F, p);
            if (p.broomScale <= 0.0F) continue;
            for (HammockMotion.Part part : HammockMotion.Part.values()) {
                for (double[] l : surf(part)) {
                    if (isHand(part, l[1])) continue;
                    WitchBroomMotion.partToFrame(p, part, l[0], l[1], l[2], f);
                    WitchBroomMotion.frameToDrawnBroom(p, f[0], f[1], f[2], b);
                    for (BCube c : cubes) {
                        double d = c.depth(b) * p.broomScale;
                        if (d > worst) {
                            worst = d;
                            where = part + " " + java.util.Arrays.toString(l) + " in " + c.bone() + " bei " + ms + " ms";
                        }
                    }
                }
            }
        }
        check(worst <= 0.15, "kein Körperteil im Besen (tiefstens " + worst + " px, " + where + ")");
    }

    private static void aboveGround() {
        List<BCube> cubes = broomCubes();
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        double lowBody = 1e9, lowBodyFlying = 1e9, lowBroom = 1e9;
        double[] f = new double[3];
        for (int ms = 0; ms <= 20_000; ms += 4) {
            float t = ms / 1000.0F;
            WitchBroomMotion.sample(t, p);
            for (HammockMotion.Part part : HammockMotion.Part.values()) {
                for (double[] l : new double[][]{{part.x0, part.y0 + part.sy, part.z0}, {part.x0 + part.sx, part.y0 + part.sy, part.z0},
                        {part.x0, part.y0 + part.sy, part.z0 + part.sz}, {part.x0 + part.sx, part.y0 + part.sy, part.z0 + part.sz},
                        {part.x0, part.y0, part.z0}, {part.x0 + part.sx, part.y0, part.z0 + part.sz}}) {
                    WitchBroomMotion.partToFrame(p, part, l[0], l[1], l[2], f);
                    lowBody = Math.min(lowBody, f[1]);
                    if (t >= WitchBroomMotion.POSE_END) lowBodyFlying = Math.min(lowBodyFlying, f[1]);
                }
            }
            if (p.broomScale > 0.0F && ms % 20 == 0) {
                for (BCube c : cubes) {
                    for (double[] q : c.corners()) {
                        lowBroom = Math.min(lowBroom, WitchBroomMotion.drawnBroomToFrame(p, q[0], q[1], q[2], f)[1]);
                    }
                }
            }
        }
        check(lowBody >= -1e-6, "nichts im Boden (" + lowBody + ")");
        check(lowBodyFlying >= 0.03, "Füße schweben über dem Boden (" + lowBodyFlying + ")");
        check(lowBroom >= 0.01, "Besen nie im Boden, auch beim Hereingleiten (" + lowBroom + ")");
    }

    private static void headroomAndLean() {
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        double high = 0;
        for (int ms = (int) (WitchBroomMotion.MOUNT_END * 1000); ms <= 30_000; ms += 5) {
            WitchBroomMotion.sample(ms / 1000.0F, p);
            for (double[] l : new double[][]{{-4, -8, -4}, {4, -8, -4}, {-4, -8, 4}, {4, -8, 4}}) {
                high = Math.max(high, WitchBroomMotion.partToFrame(p, HammockMotion.Part.HEAD, l[0], l[1], l[2], new double[3])[1]);
            }
            // Vorlage im Raum: Rumpf-Achse gegen die Senkrechte
            double[] top = WitchBroomMotion.modelToFrame(p, 0, 0, 0, new double[3]), bot = WitchBroomMotion.modelToFrame(p, 0, 12, 0, new double[3]);
            double lean = Math.toDegrees(Math.acos((top[1] - bot[1]) / dist(top, bot)));
            check(lean >= 12.0 && lean <= 40.0, "leichte Vorlage (" + lean + "°)");
            // Kopf schaut ungefähr geradeaus (Gesicht nicht zum Boden)
            double[] eye = WitchBroomMotion.partToFrame(p, HammockMotion.Part.HEAD, 0, -4, -4, new double[3]);
            double[] back = WitchBroomMotion.partToFrame(p, HammockMotion.Part.HEAD, 0, -4, 4, new double[3]);
            double gaze = Math.toDegrees(Math.atan2(eye[1] - back[1], Math.hypot(eye[0] - back[0], eye[2] - back[2])));
            check(gaze > -25.0 && gaze < 20.0, "Blick nach vorne (" + gaze + "°)");
        }
        check(high <= 1.97, "Kopf bleibt unter 2 Blöcken (" + high + ")");
    }

    private static double[] local(WitchBroomMotion.Pose p, HammockMotion.Part part, double[] m) {
        float[] a = WitchBroomMotion.partAngles(p, part);
        double[] pv = WitchBroomMotion.partPivot(p, part, new double[3]);
        double[] o = new double[3];
        CampfireMotion.rotXYZInverse(m[0] - pv[0], m[1] - pv[1], m[2] - pv[2], a[0], a[1], a[2], o);
        return o;
    }

    private static double[] model(WitchBroomMotion.Pose p, HammockMotion.Part part, double[] l) {
        float[] a = WitchBroomMotion.partAngles(p, part);
        double[] pv = WitchBroomMotion.partPivot(p, part, new double[3]);
        double[] o = new double[3];
        CampfireMotion.rotZYX(l[0], l[1], l[2], a[0], a[1], a[2], o);
        o[0] += pv[0];
        o[1] += pv[1];
        o[2] += pv[2];
        return o;
    }

    private static double depthIn(HammockMotion.Part q, double[] l) {
        double dx = Math.min(l[0] - q.x0, q.x0 + q.sx - l[0]), dy = Math.min(l[1] - q.y0, q.y0 + q.sy - l[1]);
        double dz = Math.min(l[2] - q.z0, q.z0 + q.sz - l[2]);
        return Math.min(dx, Math.min(dy, dz));
    }

    /** Arme bleiben (bis auf die Schulter wie bei Vanilla) vor dem Rumpf, Beine nicht ineinander. */
    private static void armsClearBody() {
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        double armBody = -1e9, armArm = -1e9, legLeg = -1e9, armLeg = -1e9;
        for (int ms = (int) (WitchBroomMotion.ARM_END * 1000); ms <= 20_000; ms += 20) {
            WitchBroomMotion.sample(ms / 1000.0F, p);
            for (HammockMotion.Part a : HammockMotion.Part.values()) {
                for (double[] l : surf(a)) {
                    boolean arm = a == HammockMotion.Part.RIGHT_ARM || a == HammockMotion.Part.LEFT_ARM;
                    if (arm && l[1] < 0.5) continue; // Schulterkappe
                    double[] m = model(p, a, l);
                    for (HammockMotion.Part b : HammockMotion.Part.values()) {
                        if (a == b) continue;
                        double d = depthIn(b, local(p, b, m));
                        if (arm && b == HammockMotion.Part.BODY) armBody = Math.max(armBody, d);
                        else if (arm && (b == HammockMotion.Part.RIGHT_ARM || b == HammockMotion.Part.LEFT_ARM)) armArm = Math.max(armArm, d);
                        else if (arm && (b == HammockMotion.Part.RIGHT_LEG || b == HammockMotion.Part.LEFT_LEG)) armLeg = Math.max(armLeg, d);
                        else if (a == HammockMotion.Part.RIGHT_LEG && b == HammockMotion.Part.LEFT_LEG) legLeg = Math.max(legLeg, d);
                    }
                }
            }
        }
        check(armBody <= 0.6, "Arme kaum im Rumpf (" + armBody + " px)");
        check(armArm <= 0.3, "Arme nicht ineinander (" + armArm + " px)");
        check(armLeg <= 0.0, "Arme nicht in den Beinen (" + armLeg + " px)");
        check(legLeg <= 0.0, "Beine nicht ineinander (" + legLeg + " px)");
    }

    /** Reiter ist starr mit dem Besen verbunden: im Raum des Besens ändert sich der Körper im Flug kaum. */
    private static void neverFallsOff() {
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        double[] ref = null;
        double worst = 0;
        for (int ms = (int) (WitchBroomMotion.MOUNT_END * 1000); ms <= 60_000; ms += 9) {
            WitchBroomMotion.sample(ms / 1000.0F, p);
            double[] f = WitchBroomMotion.modelToFrame(p, 0, 0, 0, new double[3]); // Hals
            double[] b = WitchBroomMotion.frameToBroom(p, f[0], f[1], f[2], new double[3]);
            if (ref == null) ref = b;
            worst = Math.max(worst, dist(ref, b));
        }
        check(worst < 0.03, "kein Herausfallen: Reiter bleibt auf dem Besen (" + worst + ")");
    }

    private static void camera() {
        double[] c = WitchBroomMotion.cameraPivot();
        near(c[0], 0.0, 1e-12, "Kamera über der Kreismitte (x)");
        near(c[2], 0.0, 1e-12, "Kamera über der Kreismitte (z)");
        check(c[1] > 0.9 && c[1] < 1.8, "Kamera-Drehpunkt auf Oberkörperhöhe");
    }

    private static void effects() {
        check(WitchBroomMotion.PUFF_AT >= WitchBroomMotion.SLIDE_START && WitchBroomMotion.PUFF_AT < WitchBroomMotion.SLIDE_END,
                "Puff beim Erscheinen des Besens");
        check(WitchBroomMotion.STAR_EVERY >= 4, "Sterne nur gelegentlich (Partikelzahl begrenzt)");
        WitchBroomMotion.Pose p = new WitchBroomMotion.Pose();
        WitchBroomMotion.sample(WitchBroomLogic.FLY_START, p);
        check(p.flight < WitchBroomMotion.TRAIL_FROM, "kein Schweif vor dem Flug");
        WitchBroomMotion.sample(6.0F, p);
        double[] tip = WitchBroomMotion.bristleTip(p, new double[3]);
        double[] seat = WitchBroomMotion.broomToFrame(p, 0, 0, 0, new double[3]);
        // Schweif-Quelle hinten am Reisig: entgegen der Flugrichtung
        double h = Math.toRadians(p.broomHeading);
        double fx = -Math.sin(h), fz = -Math.cos(h);
        check((tip[0] - seat[0]) * fx + (tip[2] - seat[2]) * fz < -0.5, "Funken am Reisig-Ende (hinten)");
        check(tip[1] > 0.1, "Funken über dem Boden");
    }

    private static void checkPoints() {
        double[][] f = WitchBroomLogic.checkPointsFrame();
        double maxR = 0, maxY = 0;
        for (double[] q : f) {
            maxR = Math.max(maxR, Math.hypot(q[0], q[2]));
            maxY = Math.max(maxY, q[1]);
        }
        check(maxR >= WitchBroomLogic.RADIUS + 0.3, "Platzprüfung deckt Besenspitzen ab");
        check(maxY >= 1.7, "Platzprüfung bis Kopfhöhe");
        for (float yaw : new float[]{0.0F, 77.0F, -140.0F}) {
            double[][] w = WitchBroomLogic.checkPoints(10.5, 64.0, -7.25, yaw);
            double[] tmp = new double[3];
            for (int i = 0; i < w.length; i++) {
                HammockLogic.worldToFrame(w[i][0], w[i][1], w[i][2], 10.5, 64.0, -7.25, yaw, tmp);
                near(tmp[0], f[i][0], 1e-9, "Prüfpunkt x");
                near(tmp[1], f[i][1], 1e-9, "Prüfpunkt y");
                near(tmp[2], f[i][2], 1e-9, "Prüfpunkt z");
            }
        }
    }

    // ---------------- Rein optisch ----------------

    private static final String[] FORBIDDEN = {
            "setPos", "setPosition", "updatePosition", "refreshPositionAndAngles", "teleport", "setVelocity", "addVelocity",
            ".move(", "setBoundingBox", "setOnGround", "flying =", "allowFlying", "networkHandler", "sendPacket",
            "PlayerMoveC2SPacket", "setYaw(", "setBodyYaw", "setHeadYaw", "setPitch(", "fallDistance", "noClip"};

    private static void purelyVisual() {
        // 1. Bahn und Pose hängen nur von der Zeit ab und verändern ihre Eingaben nicht
        double[] buf = {1, 2, 3};
        WitchBroomLogic.position(5.0F, buf);
        double[] again = WitchBroomLogic.position(5.0F, new double[3]);
        check(buf[0] == again[0] && buf[1] == again[1] && buf[2] == again[2], "Bahn ist eine reine Funktion der Zeit");
        // 2. Der Emote-Code fasst Position, Bewegung und Hitbox nie an und sendet nichts an den Server
        List<String> sources = new ArrayList<>();
        sources.add(read(SRC + "emotes/WitchBroomCoordinator.java"));
        sources.add(read(SRC + "emotes/WitchBroomMotion.java"));
        sources.add(read(SRC + "emotes/WitchBroomLogic.java"));
        sources.add(section(read(SRC + "emotes/EmoteAnimator.java"), "// Hexenbesen", "// High-Five"));
        sources.add(section(read(SRC + "emotes/EmotePropFeatureRenderer.java"), "// Hexenbesen", "Griffpunkt (Modell-px)"));
        sources.add(section(read(SRC + "module/EmoteModule.java"), "// Hexenbesen", "currentEmote = type;"));
        for (String s : sources) {
            String code = stripComments(s);
            for (String bad : FORBIDDEN) check(!code.contains(bad), "Emote-Code ändert nichts am Spieler/Server: " + bad);
        }
        // 3. Die Transformation wirkt nur auf die Render-Matrix (MatrixStack) des gezeichneten Modells
        String anim = section(read(SRC + "emotes/EmoteAnimator.java"), "// Hexenbesen", "// High-Five");
        check(anim.contains("MatrixStack matrices") && anim.contains("matrices.translate"), "nur Render-Matrix");
        check(!stripComments(read(SRC + "emotes/WitchBroomCoordinator.java")).contains("client.player."),
                "Koordinator liest den Spieler nur (keine Schreibzugriffe über client.player)");
    }

    private static String section(String text, String from, String to) {
        int a = text.indexOf(from);
        check(a >= 0, "Abschnitt gefunden: " + from);
        int b = text.indexOf(to, a + from.length());
        check(b > a, "Abschnittsende gefunden: " + to);
        return text.substring(a, b);
    }

    private static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    // ---------------- Modell ----------------

    private static void model() {
        List<BCube> cubes = broomCubes();
        for (String b : new String[]{"stick", "binding", "bristles"}) {
            check(cubes.stream().anyMatch(c -> b.equals(c.bone())), "Knochen " + b + " vorhanden");
        }
        double minZ = 1e9, maxZ = -1e9, stickR = 0, bristleR = 0, minBristleZ = 1e9;
        for (BCube c : cubes) {
            for (double[] q : c.corners()) {
                minZ = Math.min(minZ, q[2]);
                maxZ = Math.max(maxZ, q[2]);
                double r = Math.hypot(q[0], q[1]);
                if ("bristles".equals(c.bone())) {
                    bristleR = Math.max(bristleR, r);
                    minBristleZ = Math.min(minBristleZ, q[2]);
                }
                // Stiel (ohne Knoten/Zweig/Knauf an der Spitze): nahe an der Achse
                if ("stick".equals(c.bone()) && q[2] > -0.75 && q[2] < WitchBroomLogic.BIND_Z && c.s()[0] > 1.2) {
                    stickR = Math.max(stickR, r);
                }
            }
        }
        near(minZ, WitchBroomLogic.TIP_Z, 0.6 * PX, "Stielende bei TIP_Z");
        near(maxZ, WitchBroomLogic.BRISTLE_END_Z, 0.8 * PX, "Reisig-Ende bei BRISTLE_END_Z");
        check(stickR <= WitchBroomLogic.STICK_R_MAX + WitchBroomLogic.STICK_CROOK_MAX, "Stiel nur leicht krumm (" + stickR / PX + " px)");
        check(bristleR <= WitchBroomLogic.BRISTLE_R1 + 0.4 * PX, "Reisig-Bündel nicht zu breit (" + bristleR / PX + " px)");
        check(bristleR >= WitchBroomLogic.BRISTLE_R1 - 0.6 * PX, "Reisig fächert auf (" + bristleR / PX + " px)");
        check(minBristleZ > WitchBroomLogic.BIND_Z - 0.2, "Reisig beginnt an der Bindung");
        double len = maxZ - minZ;
        check(len >= 1.5 && len <= 1.9, "Besen ca. 1,5–1,9 Blöcke lang (" + len + ")");
        // Stiel am Sitzpunkt: oberste Kante unter dem Schritt
        double top = -1e9;
        for (BCube c : cubes) {
            if (!"stick".equals(c.bone())) continue;
            for (double z = -0.12; z <= 0.12; z += 0.01) {
                for (double y = 0.0; y <= 0.1; y += 0.0025) {
                    if (c.depth(new double[]{0, y, z}) > 0) top = Math.max(top, y);
                }
            }
        }
        check(top <= WitchBroomMotion.SEAT_Y, "Stiel unter dem Schritt (" + top / PX + " px)");
        // Textur: Größe wie im Modell, alle UV-Flächen innerhalb und deckend
        String text = read(ROOT + "emotes/witch_broom/witch_broom.geo.json");
        Matcher tw = Pattern.compile("\"texture_width\":\\s*(\\d+),\\s*\"texture_height\":\\s*(\\d+)").matcher(text);
        check(tw.find(), "Texturgröße im Modell");
        int w = Integer.parseInt(tw.group(1)), h = Integer.parseInt(tw.group(2));
        BufferedImage img;
        try {
            img = ImageIO.read(new File(ROOT + "textures/entity/witch_broom.png"));
        } catch (IOException e) {
            throw new AssertionError("Textur fehlt: " + e.getMessage());
        }
        check(img != null && img.getWidth() == w && img.getHeight() == h, "Textur " + w + "x" + h);
        Matcher uv = Pattern.compile("\"uv\":\\s*\\[(\\d+), (\\d+)\\],\\s*\"uv_size\":\\s*\\[(\\d+), (\\d+)\\]").matcher(text);
        int faces = 0;
        while (uv.find()) {
            int u = Integer.parseInt(uv.group(1)), v = Integer.parseInt(uv.group(2));
            int us = Integer.parseInt(uv.group(3)), vs = Integer.parseInt(uv.group(4));
            check(u + us <= w && v + vs <= h, "UV innerhalb der Textur");
            for (int x = u; x < u + us; x++) {
                for (int y = v; y < v + vs; y++) {
                    if ((img.getRGB(x, y) >>> 24) < 255) throw new AssertionError("Textur-Loch bei " + x + "," + y);
                }
            }
            faces++;
        }
        check(faces == cubes.size() * 6, "alle Flächen mit UV (" + faces + ")");
    }

    // ---------------- Kleinkram ----------------

    private static String read(String file) {
        try {
            return Files.readString(Path.of(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Datei fehlt: " + file + " (" + e.getMessage() + ")");
        }
    }

    private static double[] nums(String s) {
        String[] parts = s.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i].trim());
        return out;
    }

    private static double[] rotX(double[] v, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
    }

    private static double[] rotY(double[] v, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
    }

    private static double[] rotZ(double[] v, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2]};
    }
}

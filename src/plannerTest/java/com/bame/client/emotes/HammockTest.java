package com.bame.client.emotes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft ohne Minecraft die Hängematte: Geometrie und Platzprüfung aus {@link HammockLogic}, Zeitleiste und Pose aus
 * {@link HammockMotion} (flüssig in 1-ms-Schritten inkl. Erscheinen und Schleife, deterministisch), die Hüfte liegt beim
 * Schaukeln auf dem Tuch, nichts unter dem Boden, kein Körperteil in den Pfosten, kein Herausfallen, und die Modelle
 * (Pfosten auf dem Boden, Tuch auf der Kettenlinie, Seile an Achse und Stäben).
 */
public class HammockTest {

    private static int checks = 0;
    private static final double PX = 1.0 / 16.0;

    public static void main(String[] args) {
        emoteType();
        frameRoundTrip();
        checkPointsGeometry();
        slingCurve();
        swingAxis();
        timeline();
        deterministic();
        continuity();
        hipOnSling();
        aboveGroundAndClearOfPosts();
        noFallingOut();
        restsOnFabric();
        creakTiming();
        camera();
        propModels();
        System.out.println("HAMMOCK TESTS PASSED (" + checks + " checks)");
    }

    // ---------------- Hilfen ----------------

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void near(double a, double b, double eps, String what) {
        check(Math.abs(a - b) <= eps, what + ": " + a + " != " + b);
    }

    /** Ecken und Kantenmitten aller Körperteile im Rahmen (27 Punkte pro Teil). */
    private static List<double[]> bodyPoints(HammockMotion.Pose p) {
        List<double[]> out = new ArrayList<>();
        for (HammockMotion.Part part : HammockMotion.Part.values()) {
            for (int i = 0; i < 27; i++) {
                double fx = (i % 3) / 2.0, fy = ((i / 3) % 3) / 2.0, fz = (i / 9) / 2.0;
                double[] w = HammockMotion.partToFrame(p, part, part.x0 + part.sx * fx, part.y0 + part.sy * fy,
                        part.z0 + part.sz * fz, new double[3]);
                out.add(new double[]{w[0], w[1], w[2], part.ordinal(), i});
            }
        }
        return out;
    }

    /** Punkt zurück in die Ruhelage des Tuchs (Schaukeln herausrechnen). */
    private static double[] unswing(double[] w, double swayDeg) {
        return HammockLogic.swing(w[0], w[1], w[2], -swayDeg, new double[3]);
    }

    // ---------------- Emote-Typ ----------------

    private static void emoteType() {
        check(EmoteType.fromId("HAMMOCK") == EmoteType.HAMMOCK, "fromId HAMMOCK");
        check(EmoteType.fromId("hammock") == EmoteType.HAMMOCK, "fromId hammock (Karten-ID)");
        check("Hammock".equals(EmoteType.HAMMOCK.getDisplayName()), "Anzeigename Hammock");
        check(!EmoteType.HAMMOCK.getDescription().isBlank(), "Beschreibung vorhanden");
        check(!EmoteType.HAMMOCK.allowsWalking(), "stationär (bricht bei Bewegung ab)");
        check(!EmoteType.HAMMOCK.isHidden(), "auswählbar");
    }

    // ---------------- Geometrie ----------------

    private static void frameRoundTrip() {
        double[] tmp = new double[3];
        for (float yaw : new float[]{0.0F, 37.0F, 90.0F, -135.0F, 180.0F, 271.5F}) {
            double[] w = HammockLogic.frameToWorld(1.3, 0.7, -0.4, 10.5, 64.0, -3.25, yaw);
            HammockLogic.worldToFrame(w[0], w[1], w[2], 10.5, 64.0, -3.25, yaw, tmp);
            near(tmp[0], 1.3, 1e-9, "Rundweg x (yaw " + yaw + ")");
            near(tmp[1], 0.7, 1e-9, "Rundweg y");
            near(tmp[2], -0.4, 1e-9, "Rundweg z");
        }
        // Yaw 0 = Blick nach Süden (+Z): rechts ist Westen (-X), hinten ist Norden (-Z)
        double[] right = HammockLogic.frameToWorld(1.0, 0.0, 0.0, 0, 0, 0, 0.0F);
        near(right[0], -1.0, 1e-9, "rechts = -X bei Yaw 0");
        double[] back = HammockLogic.frameToWorld(0.0, 0.0, 1.0, 0, 0, 0, 0.0F);
        near(back[2], -1.0, 1e-9, "hinten = -Z bei Yaw 0");
    }

    private static void checkPointsGeometry() {
        for (float yaw : new float[]{0.0F, 45.0F, 123.0F, -90.0F}) {
            double ox = 5.5, oy = 70.0, oz = -12.5;
            double[][] pts = HammockLogic.checkPoints(ox, oy, oz, yaw);
            check(pts.length == HammockLogic.CHECK_X.length * HammockLogic.CHECK_HEIGHTS.length, "Anzahl Prüfpunkte");
            int ground = 0;
            double r = Math.toRadians(yaw);
            double fx = -Math.sin(r), fz = Math.cos(r);
            double maxSide = 0.0;
            for (double[] p : pts) {
                // quer zur Blickrichtung: kein Versatz nach vorne
                near((p[0] - ox) * fx + (p[2] - oz) * fz, 0.0, 1e-9, "Prüfpunkt quer zur Blickrichtung");
                maxSide = Math.max(maxSide, Math.hypot(p[0] - ox, p[2] - oz));
                check(p[1] >= oy && p[1] <= oy + 1.6, "Prüfhöhe Fuß/Kopf");
                if (p[3] > 0.5) {
                    ground++;
                    near(p[1], oy + 0.05, 1e-9, "Bodenprüfung auf Fußhöhe");
                    near(Math.hypot(p[0] - ox, p[2] - oz), HammockLogic.POST_X, 1e-9, "Bodenprüfung an den Pfosten");
                }
            }
            check(ground == 2, "fester Boden unter beiden Pfosten gefordert");
            near(maxSide, HammockLogic.POST_X, 1e-9, "Platz bis zu den Pfosten geprüft");
        }
        // Pfosten ca. 1,6 Blöcke links und rechts
        check(HammockLogic.POST_X >= 1.4 && HammockLogic.POST_X <= 1.8, "Pfostenabstand ~1,6");
    }

    private static void slingCurve() {
        near(HammockLogic.slingY(0.0), HammockLogic.LOW_Y, 1e-12, "tiefster Punkt in der Mitte");
        near(HammockLogic.endY(), HammockLogic.LOW_Y + HammockLogic.SAG, 1e-12, "Enden um SAG höher");
        double prev = HammockLogic.slingY(0.0);
        for (int i = 1; i <= 100; i++) {
            double x = HammockLogic.SPREADER_X * i / 100.0;
            double y = HammockLogic.slingY(x);
            check(y > prev, "Kettenlinie steigt nach außen");
            near(y, HammockLogic.slingY(-x), 1e-12, "symmetrisch");
            double num = (HammockLogic.slingY(x + 1e-6) - HammockLogic.slingY(x - 1e-6)) / 2e-6;
            if (i < 100) near(HammockLogic.slingSlope(x), num, 1e-5, "Steigung");
            prev = y;
        }
        near(HammockLogic.cupLift(0.0), 0.0, 1e-12, "Mitte flach");
        near(HammockLogic.cupLift(HammockLogic.SLING_HALF_W), HammockLogic.CUP, 1e-12, "Rand gewölbt");
        // Seile hängen von der Achse nach unten zu den Enden
        check(HammockLogic.AXIS_Y > HammockLogic.endY() + 0.5, "Aufhängung deutlich über den Enden");
        check(HammockLogic.ATTACH_X > HammockLogic.SPREADER_X + 0.3, "Seile laufen schräg nach außen");
        check(HammockLogic.ATTACH_X < HammockLogic.POST_X + HammockLogic.ATTACH_ALONG * Math.sin(Math.toRadians(HammockLogic.POST_TILT_DEG)),
                "Aufhängepunkt auf der Innenseite des Pfostens");
    }

    private static void swingAxis() {
        double[] o = new double[3];
        for (double deg : new double[]{-12.0, -3.0, 5.0, 11.0}) {
            HammockLogic.swing(HammockLogic.ATTACH_X, HammockLogic.AXIS_Y, 0.0, deg, o);
            near(o[1], HammockLogic.AXIS_Y, 1e-12, "Aufhängepunkt bleibt fest (y)");
            near(o[2], 0.0, 1e-12, "Aufhängepunkt bleibt fest (z)");
            HammockLogic.swing(0.3, 0.6, 0.1, deg, o);
            double[] back = HammockLogic.swing(o[0], o[1], o[2], -deg, new double[3]);
            near(back[1], 0.6, 1e-12, "Rückdrehung y");
            near(back[2], 0.1, 1e-12, "Rückdrehung z");
        }
    }

    // ---------------- Zeitleiste ----------------

    private static void timeline() {
        HammockMotion.Pose p = new HammockMotion.Pose();
        HammockMotion.sample(0.0F, p);
        near(p.postScale, 0.0, 1e-6, "Pfosten beginnen bei 0");
        near(p.ropeExtend, 0.0, 1e-6, "Seile noch nicht da");
        near(p.lie, 0.0, 1e-6, "steht noch");
        near(p.swayDeg, 0.0, 1e-6, "kein Schaukeln am Anfang");
        for (int i = 0; i < HammockLogic.SEGMENTS; i++) near(HammockMotion.segmentScale(0.0F, i), 0.0, 1e-6, "Tuch noch eingerollt");
        HammockMotion.sample(HammockMotion.INTRO, p);
        near(p.postScale, 1.0, 1e-6, "Pfosten voll da");
        near(p.ropeExtend, 1.0, 1e-6, "Seile voll da");
        near(p.lie, 1.0, 1e-6, "liegt nach der Einleitung (0,8 s Hinlegen)");
        for (int i = 0; i < HammockLogic.SEGMENTS; i++) near(HammockMotion.segmentScale(HammockMotion.UNROLL_END, i), 1.0, 1e-6, "Tuch ausgerollt");
        check(HammockMotion.LIE_END - HammockMotion.LIE_START >= 0.7F && HammockMotion.LIE_END - HammockMotion.LIE_START <= 0.9F,
                "Hinlegen dauert ca. 0,8 s");
        // Ausrollen vom Kopfende zum Fußende
        float mid = (HammockMotion.UNROLL_START + HammockMotion.UNROLL_END) / 2.0F;
        check(HammockMotion.segmentScale(mid, 0) > HammockMotion.segmentScale(mid, HammockLogic.SEGMENTS - 1), "rollt vom Kopfende aus");
        // Schaukeln: Amplitude ±SWAY_AMP_DEG, Periode SWAY_PERIOD
        double max = 0.0;
        for (int ms = 3000; ms < 30000; ms++) {
            HammockMotion.sample(ms / 1000.0F, p);
            max = Math.max(max, Math.abs(p.swayDeg));
            check(Math.abs(p.swayDeg) <= HammockMotion.SWAY_AMP_DEG + 1e-3, "Schaukeln innerhalb der Amplitude");
        }
        near(max, HammockMotion.SWAY_AMP_DEG, 0.05, "volle Amplitude erreicht");
        check(HammockMotion.SWAY_AMP_DEG >= 8.0F && HammockMotion.SWAY_AMP_DEG <= 12.0F, "Amplitude 8–12°");
        check(HammockMotion.SWAY_PERIOD >= 3.5F && HammockMotion.SWAY_PERIOD <= 4.0F, "Periode 3,5–4 s");
        for (float t = 5.0F; t < 20.0F; t += 0.37F) {
            near(HammockMotion.swayShape(t), HammockMotion.swayShape(t + HammockMotion.SWAY_PERIOD), 1e-4, "Schaukeln periodisch");
        }
        // Strecken alle 12 s, sonst Ruhe
        near(HammockMotion.yawn(HammockMotion.YAWN_FIRST - 0.01F), 0.0, 1e-9, "kein Strecken vor dem ersten");
        near(HammockMotion.yawn(HammockMotion.YAWN_FIRST + HammockMotion.YAWN_LENGTH / 2), 1.0, 1e-3, "Strecken");
        near(HammockMotion.yawn(HammockMotion.YAWN_FIRST + HammockMotion.YAWN_EVERY + HammockMotion.YAWN_LENGTH / 2), 1.0, 1e-3,
                "Strecken wiederholt sich");
        near(HammockMotion.yawn(HammockMotion.YAWN_FIRST + HammockMotion.YAWN_LENGTH + 1.0F), 0.0, 1e-9, "dazwischen Ruhe");
    }

    private static void deterministic() {
        HammockMotion.Pose a = new HammockMotion.Pose(), b = new HammockMotion.Pose();
        for (int ms = 0; ms < 40000; ms += 7) {
            float t = ms / 1000.0F;
            HammockMotion.sample(t, a);
            HammockMotion.sample(t, b);
            check(a.swayDeg == b.swayDeg && a.hipY == b.hipY && a.headYaw == b.headYaw && a.lArmPitch == b.lArmPitch
                    && a.rLegPitch == b.rLegPitch && a.postScale == b.postScale, "gleiche Zeit, gleiche Pose (" + t + ")");
        }
        // Reihenfolge der Aufrufe spielt keine Rolle (kein Zustand)
        HammockMotion.sample(12.345F, a);
        HammockMotion.sample(3.0F, b);
        HammockMotion.sample(12.345F, b);
        check(a.swayDeg == b.swayDeg && a.headPitch == b.headPitch, "zustandslos");
    }

    /** Größter Schritt pro ms: Körperpunkte (Blöcke; 8 Blöcke/s beim Hineinfallen, ein Sprung von 0,5 px fiele auf) und Kenngrößen. */
    private static final double MAX_POINT_STEP = 0.008;
    private static final double MAX_SCALE_STEP = 0.05;

    private static void continuity() {
        HammockMotion.Pose prev = new HammockMotion.Pose(), cur = new HammockMotion.Pose();
        HammockMotion.sample(0.0F, prev);
        List<double[]> prevPts = bodyPoints(prev);
        double worstPt = 0.0, worstScale = 0.0;
        float worstAt = 0;
        // Erscheinen + mehrere Schaukel- und Streck-Zyklen in 1-ms-Schritten
        for (int ms = 1; ms <= 45_000; ms++) {
            float t = ms / 1000.0F;
            HammockMotion.sample(t, cur);
            List<double[]> pts = bodyPoints(cur);
            for (int i = 0; i < pts.size(); i++) {
                double[] a = prevPts.get(i), b = pts.get(i);
                double d = Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
                if (d > worstPt) { worstPt = d; worstAt = t; }
            }
            worstScale = Math.max(worstScale, Math.abs(cur.postScale - prev.postScale));
            worstScale = Math.max(worstScale, Math.abs(cur.ropeExtend - prev.ropeExtend));
            for (int s = 0; s < HammockLogic.SEGMENTS; s++) {
                worstScale = Math.max(worstScale, Math.abs(HammockMotion.segmentScale(t, s) - HammockMotion.segmentScale(t - 0.001F, s)));
            }
            check(Math.abs(cur.swayDeg - prev.swayDeg) < 0.05, "Schaukeln flüssig bei " + t);
            prevPts = pts;
            HammockMotion.Pose sw = prev; prev = cur; cur = sw;
        }
        check(worstPt <= MAX_POINT_STEP, "Körper flüssig (größter Schritt " + worstPt + " Blöcke/ms bei " + worstAt + " s)");
        check(worstScale <= MAX_SCALE_STEP, "Erscheinen flüssig (größter Schritt " + worstScale + ")");
        // Große Emote-Zeiten (langes Liegen): weiterhin flüssig
        HammockMotion.Pose a = new HammockMotion.Pose(), b = new HammockMotion.Pose();
        for (float base : new float[]{600.0F, 3599.0F}) {
            HammockMotion.sample(base, a);
            List<double[]> pa = bodyPoints(a);
            for (int ms = 1; ms < 4000; ms += 1) {
                HammockMotion.sample(base + ms / 1000.0F, b);
                List<double[]> pb = bodyPoints(b);
                for (int i = 0; i < pa.size(); i += 5) {
                    double[] p = pa.get(i), q = pb.get(i);
                    check(Math.abs(p[0] - q[0]) + Math.abs(p[1] - q[1]) + Math.abs(p[2] - q[2]) <= 3 * MAX_POINT_STEP,
                            "flüssig nach langer Zeit (" + base + ")");
                }
                pa = pb;
            }
        }
    }

    // ---------------- Liegen ----------------

    /** Rückseite der Hüfte (Modell-px): Mitte des Rumpfs unten, hintere Fläche. */
    private static double[] hipBack(HammockMotion.Pose p) {
        double[] m = HammockMotion.modelToBody(0.0, 12.0, HammockMotion.BACK_PX, new double[3]);
        return HammockMotion.bodyToFrame(p, m[0], m[1], m[2], new double[3]);
    }

    private static void hipOnSling() {
        HammockMotion.Pose p = new HammockMotion.Pose();
        double worst = 0.0;
        for (int ms = (int) (HammockMotion.LIE_END * 1000); ms < 60_000; ms += 5) {
            HammockMotion.sample(ms / 1000.0F, p);
            double[] h = unswing(hipBack(p), p.swayDeg);
            double d = h[1] - HammockLogic.surfaceY(h[0], h[2]);
            worst = Math.max(worst, Math.abs(d));
            check(Math.abs(h[2]) < 1e-6, "Hüfte mittig im Tuch");
            check(Math.abs(h[0]) < HammockLogic.SPREADER_X * 0.5, "Hüfte im tiefen Teil");
        }
        check(worst <= PX, "Hüfte liegt auf dem Tuch (Abweichung " + worst / PX + " px)");
        // Ruhelage: genau SINK eingesunken
        HammockMotion.sample(HammockMotion.SWAY_START, p);
        double[] h = hipBack(p);
        near(h[1], HammockLogic.slingY(h[0]) - HammockMotion.SINK, 1e-6, "Hüfte sinkt SINK ins Tuch");
    }

    private static void aboveGroundAndClearOfPosts() {
        HammockMotion.Pose p = new HammockMotion.Pose();
        double lowest = 1e9, closest = 1e9;
        double tan = Math.tan(Math.toRadians(HammockLogic.POST_TILT_DEG));
        for (int ms = 0; ms < 40_000; ms += 3) {
            HammockMotion.sample(ms / 1000.0F, p);
            for (double[] w : bodyPoints(p)) {
                lowest = Math.min(lowest, w[1]);
                // Innenfläche der (nach außen geneigten) Pfosten auf dieser Höhe
                double inner = HammockLogic.POST_X - HammockLogic.POST_HALF + Math.max(0.0, w[1]) * tan;
                closest = Math.min(closest, inner - Math.abs(w[0]));
            }
        }
        check(lowest >= -1e-6, "nichts unter dem Boden (tiefster Punkt " + lowest + ")");
        check(closest >= 0.1, "kein Körperteil in den Pfosten (Abstand " + closest + ")");
        // im Liegen deutlich über dem Boden (auch die herabhängenden Teile)
        double lowLying = 1e9;
        for (int ms = (int) (HammockMotion.LIE_END * 1000); ms < 40_000; ms += 3) {
            HammockMotion.sample(ms / 1000.0F, p);
            for (double[] w : bodyPoints(p)) lowLying = Math.min(lowLying, w[1]);
        }
        check(lowLying >= 0.2, "liegend frei über dem Boden (" + lowLying + ")");
    }

    private static void noFallingOut() {
        HammockMotion.Pose p = new HammockMotion.Pose();
        for (int ms = (int) (HammockMotion.LIE_END * 1000); ms < 60_000; ms += 4) {
            HammockMotion.sample(ms / 1000.0F, p);
            // Rumpfmitte bleibt im flachen Mittelstreifen, kein Teil weit über den Rand hinaus
            double[] mid = HammockMotion.modelToBody(0.0, 6.0, 0.0, new double[3]);
            double[] c = unswing(HammockMotion.bodyToFrame(p, mid[0], mid[1], mid[2], new double[3]), p.swayDeg);
            check(Math.abs(c[2]) < HammockLogic.FLAT_HALF_W, "Rumpf mittig");
            check(c[1] > HammockLogic.surfaceY(c[0], c[2]), "Rumpf über dem Tuch");
            for (double[] w : bodyPoints(p)) {
                double[] u = unswing(w, p.swayDeg);
                check(Math.abs(u[2]) <= HammockLogic.SLING_HALF_W + 0.12, "Körper innerhalb der Hängematte (z " + u[2] + ")");
                check(Math.abs(u[0]) <= HammockLogic.SPREADER_X + 0.15, "Körper zwischen den Spreizstäben (x " + u[0] + ")");
            }
        }
    }

    /** Kein Körperteil sinkt sichtbar ins Tuch (höchstens {@link #MAX_SINK_PX}; Hinterkopf-Ecken liegen wie auf einem Kissen etwas tiefer). */
    private static final double MAX_SINK_PX = 2.5;

    private static void restsOnFabric() {
        HammockMotion.Pose p = new HammockMotion.Pose();
        double worst = 0.0;
        String worstWhat = "";
        for (int ms = (int) (HammockMotion.LIE_END * 1000); ms < 40_000; ms += 9) {
            HammockMotion.sample(ms / 1000.0F, p);
            for (double[] w : bodyPoints(p)) {
                double[] u = unswing(w, p.swayDeg);
                if (Math.abs(u[0]) > HammockLogic.SPREADER_X || Math.abs(u[2]) > HammockLogic.SLING_HALF_W) continue;
                double below = HammockLogic.surfaceY(u[0], u[2]) - u[1];
                if (below > worst) {
                    worst = below;
                    worstWhat = HammockMotion.Part.values()[(int) w[3]] + " #" + (int) w[4] + " bei " + ms / 1000.0 + " s";
                }
            }
        }
        check(worst <= MAX_SINK_PX * PX, "liegt auf dem Tuch statt hindurch (" + worst / PX + " px, " + worstWhat + ")");
    }

    private static void creakTiming() {
        for (int n = 0; n < 20; n++) {
            float t = HammockMotion.extremeTime(n);
            near(Math.abs(HammockMotion.swayShape(t)), 1.0, 1e-4, "Umkehrpunkt " + n);
        }
        int creaks = 0;
        for (int n = 0; n < 30; n++) if (HammockMotion.creaksAt(n)) creaks++;
        check(creaks == 10, "jeder dritte Umkehrpunkt knarzt: " + creaks);
        check(!HammockMotion.creaksAt(0), "nicht gleich beim ersten Ausschlag");
    }

    private static void camera() {
        double[] c = HammockMotion.cameraPivot();
        check(c[1] > HammockLogic.slingY(c[0]) + 0.3, "Kamera-Drehpunkt über dem liegenden Körper");
        near(HammockMotion.cameraBlend(0.0F), 0.0, 1e-9, "Kamera-Übergang beginnt bei 0");
        near(HammockMotion.cameraBlend(HammockMotion.CAMERA_BLEND_TIME), 1.0, 1e-9, "Kamera-Übergang endet bei 1");
    }

    // ---------------- Modelle ----------------

    private static final String MODEL_DIR = "src/main/resources/assets/bameclient/emotes/hammock/";

    private record Cube(String bone, double[] origin, double[] size, double[] pivot, double[] rotation) {}

    private static List<Cube> loadCubes(String file) {
        String text;
        try {
            text = Files.readString(Path.of(MODEL_DIR + file), StandardCharsets.UTF_8);
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

    /** Punkt (lokal im Würfel) in den Knochenraum (gleiche Drehung wie BedrockCosmeticModel mit blockbenchAxisFix). */
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

    private static void propModels() {
        // Pfosten: Fuß genau auf dem Boden, Seilwicklung auf Achsenhöhe, Höhe ca. 2 Blöcke
        double bottom = 1e9, top = -1e9, wrapTop = -1e9, wrapBottom = 1e9;
        for (Cube c : loadCubes("hammock_post.geo.json")) {
            check("post".equals(c.bone()), "Pfosten-Knochen");
            for (double[] p : corners(c)) {
                bottom = Math.min(bottom, p[1]);
                top = Math.max(top, p[1]);
            }
            // Wicklung: Quader breiter als der Schaft um die Wickelhöhe
            if (c.size()[0] > 2 * HammockLogic.POST_HALF * 16 + 0.5 && c.origin()[1] > 20) {
                wrapBottom = Math.min(wrapBottom, c.origin()[1]);
                wrapTop = Math.max(wrapTop, c.origin()[1] + c.size()[1]);
            }
        }
        near(bottom, 0.0, 1e-6, "Pfosten steht auf dem Boden");
        check(top * PX > 1.9 && top * PX < 2.2, "Pfosten ca. 2 Blöcke hoch (" + top * PX + ")");
        near((wrapTop + wrapBottom) / 2.0 * PX, HammockLogic.ATTACH_ALONG, 0.02, "Seilwicklung auf Achsenhöhe");

        // Tuch: Oberseite der Mittelstreifen liegt auf der Kettenlinie (Rahmen-x = -Bedrock-x)
        List<Cube> sling = loadCubes("hammock_sling.geo.json");
        int strips = 0;
        double worstCurve = 0.0;
        for (Cube c : sling) {
            if (c.bone() == null || !c.bone().matches("s\\d")) continue;
            boolean center = Math.abs(c.origin()[2] + c.size()[2] / 2.0) < 1e-3 && c.size()[2] > 2.0;
            if (!center) continue;
            strips++;
            // Oberseite: Mittellinie (z = 0) an beiden Enden ohne Überlappung (0,3 px)
            double y1 = c.origin()[1] + c.size()[1];
            for (double fx : new double[]{0.3, c.size()[0] / 2.0, c.size()[0] - 0.3}) {
                double[] p = xf(c, new double[]{c.origin()[0] + fx, y1, 0.0});
                double hx = -p[0] * PX;
                worstCurve = Math.max(worstCurve, Math.abs(p[1] * PX - HammockLogic.slingY(hx)));
            }
            int idx = Integer.parseInt(c.bone().substring(1));
            double[] mid = xf(c, new double[]{c.origin()[0] + c.size()[0] / 2.0, y1, 0.0});
            double hx = -mid[0] * PX;
            check(hx > HammockLogic.segmentX(idx) && hx < HammockLogic.segmentX(idx + 1), "Segment s" + idx + " an seinem Platz");
            near(c.size()[1] * PX, HammockLogic.FABRIC, 1e-3, "Stoffdicke");
        }
        check(strips == HammockLogic.SEGMENTS, "ein Mittelstreifen pro Segment (" + strips + ")");
        check(worstCurve <= 0.15 * PX, "Tuch folgt der Kettenlinie (" + worstCurve / PX + " px)");
        // Seitenstreifen: Außenkante um CUP höher, Breite bis SLING_HALF_W
        double maxZ = 0.0, edgeLift = 0.0;
        for (Cube c : sling) {
            if (c.bone() == null || !c.bone().matches("s\\d") || c.rotation() == null || Math.abs(c.rotation()[0]) < 1.0) continue;
            for (double[] p : corners(c)) maxZ = Math.max(maxZ, Math.abs(p[2]));
            boolean plus = c.origin()[2] > 0;
            double zEdge = plus ? c.origin()[2] + c.size()[2] : c.origin()[2];
            double zIn = plus ? c.origin()[2] : c.origin()[2] + c.size()[2];
            double xm = c.pivot()[0], ytop = c.origin()[1] + c.size()[1];
            double[] e = xf(c, new double[]{xm, ytop, zEdge}), in = xf(c, new double[]{xm, ytop, zIn});
            edgeLift = Math.max(edgeLift, (e[1] - in[1]) * PX);
        }
        near(maxZ * PX, HammockLogic.SLING_HALF_W, 0.03, "Tuchbreite");
        near(edgeLift, HammockLogic.CUP, 0.01, "seitliche Wölbung");
        // Seile: beginnen an der Achse (Aufhängepunkt), enden an den Spreizstäben
        int ropes = 0;
        for (Cube c : sling) {
            if (!"rope_l".equals(c.bone()) && !"rope_r".equals(c.bone())) continue;
            ropes++;
            double[] a = c.pivot();
            double sx = "rope_l".equals(c.bone()) ? -1 : 1; // Rahmen-x: links negativ
            near(-a[0] * PX, sx * HammockLogic.ATTACH_X, 1e-3, "Seil an der Wicklung (x)");
            near(a[1] * PX, HammockLogic.AXIS_Y, 1e-3, "Seil auf Achsenhöhe");
            double[] end = xf(c, new double[]{a[0], c.origin()[1], a[2]});
            near(-end[0] * PX, sx * HammockLogic.SPREADER_X, 1e-3, "Seil endet am Spreizstab (x)");
            near(Math.abs(end[2]) * PX, HammockLogic.SPREADER_HALF, 1e-3, "Seil endet am Stab-Ende (z)");
        }
        check(ropes == 4, "vier Seilstränge (" + ropes + ")");
        int bars = 0;
        for (Cube c : sling) {
            if (!"bars".equals(c.bone())) continue;
            bars++;
            near(Math.abs(c.origin()[0] + c.size()[0] / 2.0) * PX, HammockLogic.SPREADER_X, 1e-3, "Spreizstab an den Enden");
        }
        check(bars == 2, "zwei Spreizstäbe");
    }
}

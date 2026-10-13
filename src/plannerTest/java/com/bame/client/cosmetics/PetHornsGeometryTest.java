package com.bame.client.cosmetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueft ohne Minecraft die Geometrie der Demon Horns v2 und des Baby-Drachen: Hoerner sitzen auf der Stirn (Basisring
 * auf der Kopfoberseite, nichts im Kopf ausser der Wurzel, ca. 6-7 px hoch, nicht zu breit), Drache steht mit den
 * Fuessen genau auf der Schulter (ueber die ganze Wach-Animation), schlaeft aufliegend ohne in die Schulter zu ragen,
 * und die Animation ist stetig. Die Knochen-Mathematik entspricht {@link BedrockCosmeticModel} mit
 * {@code blockbenchAxisFix}.
 */
public class PetHornsGeometryTest {

    private static int checks = 0;
    private static final String RES = "src/main/resources/assets/";

    public static void main(String[] args) {
        namespacesIdentical();
        horns();
        dragonBones();
        dragonStanding();
        dragonSleeping();
        dragonFlyingAndContinuity();
        seat();
        System.out.println("PET/HORNS GEOMETRY TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    // ---------------- Modell lesen ----------------

    private record Cube(String bone, double[] origin, double[] size, double[] pivot, double[] rotation) {}

    private record Bone(String name, String parent, double[] pivot) {}

    private record Geo(List<Cube> cubes, Map<String, Bone> bones) {}

    private static String read(String rel) {
        try {
            return Files.readString(Path.of(RES + rel), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Datei fehlt: " + rel + " (" + e.getMessage() + ")");
        }
    }

    private static Geo load(String rel) {
        String text = read(rel);
        Pattern name = Pattern.compile("^\\s*\"name\":\\s*\"([^\"]+)\"");
        Pattern bonePivot = Pattern.compile("^\\s*\"pivot\":\\s*\\[([^\\]]*)\\]");
        Pattern parent = Pattern.compile("^\\s*\"parent\":\\s*\"([^\"]+)\"");
        Pattern cube = Pattern.compile("\\{\"origin\":\\s*\\[([^\\]]*)\\],\\s*\"size\":\\s*\\[([^\\]]*)\\]"
                + "(?:,\\s*\"pivot\":\\s*\\[([^\\]]*)\\],\\s*\"rotation\":\\s*\\[([^\\]]*)\\])?");
        List<Cube> cubes = new ArrayList<>();
        Map<String, Bone> bones = new HashMap<>();
        String cur = null;
        double[] piv = null;
        String par = null;
        for (String line : text.split("\n")) {
            Matcher m = name.matcher(line);
            if (m.find()) {
                if (cur != null) bones.put(cur, new Bone(cur, par, piv));
                cur = m.group(1);
                piv = new double[3];
                par = null;
                continue;
            }
            m = bonePivot.matcher(line);
            if (m.find()) piv = nums(m.group(1));
            m = parent.matcher(line);
            if (m.find()) par = m.group(1);
            m = cube.matcher(line);
            if (m.find()) {
                cubes.add(new Cube(cur, nums(m.group(1)), nums(m.group(2)),
                        m.group(3) != null ? nums(m.group(3)) : null, m.group(4) != null ? nums(m.group(4)) : null));
            }
        }
        if (cur != null) bones.put(cur, new Bone(cur, par, piv));
        check(!cubes.isEmpty(), "Wuerfel gelesen: " + rel);
        return new Geo(cubes, bones);
    }

    private static double[] nums(String s) {
        String[] p = s.split(",");
        double[] v = new double[p.length];
        for (int i = 0; i < p.length; i++) v[i] = Double.parseDouble(p[i].trim());
        return v;
    }

    // ---------------- Mathematik wie BedrockCosmeticModel (blockbenchAxisFix) ----------------

    /** Drehung (Grad, Datei-/Animations-Konvention) um pivot: zuerst X, dann Y, dann Z, X und Z negiert. */
    private static double[] rotate(double[] p, double[] pivot, double rx, double ry, double rz) {
        double x = p[0] - pivot[0], y = p[1] - pivot[1], z = p[2] - pivot[2];
        double a = Math.toRadians(-rx);
        double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
        double b = Math.toRadians(ry);
        double x2 = x * Math.cos(b) + z1 * Math.sin(b), z2 = -x * Math.sin(b) + z1 * Math.cos(b);
        double g = Math.toRadians(-rz);
        double x3 = x2 * Math.cos(g) - y1 * Math.sin(g), y3 = x2 * Math.sin(g) + y1 * Math.cos(g);
        return new double[]{x3 + pivot[0], y3 + pivot[1], z2 + pivot[2]};
    }

    private static List<double[]> corners(Cube c) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double[] p = {c.origin()[0] + ((i & 1) != 0 ? c.size()[0] : 0), c.origin()[1] + ((i & 2) != 0 ? c.size()[1] : 0),
                    c.origin()[2] + ((i & 4) != 0 ? c.size()[2] : 0)};
            out.add(c.rotation() == null ? p : rotate(p, c.pivot(), c.rotation()[0], c.rotation()[1], c.rotation()[2]));
        }
        return out;
    }

    /** Punkt im Knochen (Ruhelage) mit Pose in Modellkoordinaten (Bedrock-Pixel). */
    private static double[] posed(Geo g, String bone, double[] p, BabyDragonPose pose) {
        double[] q = p.clone();
        for (Bone b = g.bones().get(bone); b != null; b = b.parent() != null ? g.bones().get(b.parent()) : null) {
            float[] r = pose != null ? rotOf(pose, b.name()) : new float[3];
            float[] t = pose != null ? transOf(pose, b.name()) : new float[3];
            q = rotate(q, b.pivot(), r[0], r[1], r[2]);
            q[0] += t[0];
            q[1] += t[1];
            q[2] += t[2];
        }
        return q;
    }

    private static float[] rotOf(BabyDragonPose p, String bone) {
        for (String b : BabyDragonPose.BONES) if (b.equals(bone)) return p.rotation(bone);
        return new float[3];
    }

    private static float[] transOf(BabyDragonPose p, String bone) {
        for (String b : BabyDragonPose.BONES) if (b.equals(bone)) return p.translation(bone);
        return new float[3];
    }

    private static List<double[]> posedCorners(Geo g, BabyDragonPose pose, java.util.function.Predicate<Cube> filter) {
        List<double[]> out = new ArrayList<>();
        for (Cube c : g.cubes()) {
            if (!filter.test(c)) continue;
            for (double[] p : corners(c)) out.add(posed(g, c.bone(), p, pose));
        }
        return out;
    }

    // ---------------- Tests ----------------

    private static void namespacesIdentical() {
        for (String f : new String[]{"models/cosmetics/hat/demon_horns.geo.json", "models/cosmetics/pets/baby_dragon.geo.json"}) {
            check(read("bameclient/" + f).equals(read("caeserclient/" + f)), "beide Namespaces gleich: " + f);
        }
    }

    private static void horns() {
        Geo g = load("bameclient/models/cosmetics/hat/demon_horns.geo.json");
        // Kopf: x -4..4, y 24..32, z -4..4; Hut-Layer bis 32.5
        double top = -1e9, maxX = 0, minY = 1e9;
        int segs = 0;
        for (Cube c : g.cubes()) {
            List<double[]> cs = corners(c);
            double cy = 0, cx = 0, cz = 0;
            for (double[] p : cs) {
                top = Math.max(top, p[1]);
                maxX = Math.max(maxX, Math.abs(p[0]));
                minY = Math.min(minY, p[1]);
                cx += p[0] / 8; cy += p[1] / 8; cz += p[2] / 8;
            }
            boolean ring = c.size()[0] > 2.5;
            if (ring) {
                // Basisring umschliesst die Fuge Kopf/Hut-Layer
                double lo = 1e9, hi = -1e9;
                for (double[] p : cs) { lo = Math.min(lo, p[1]); hi = Math.max(hi, p[1]); }
                check(lo < 32.2 && hi > 32.4 && lo > 31.0 && hi < 33.5, "Basisring sitzt auf der Kopfoberseite: " + lo + ".." + hi);
                check(Math.abs(cz) < 3.0 && Math.abs(cx) > 1.5 && Math.abs(cx) < 3.8, "Basisring auf der Stirn/Schlaefe");
            } else {
                segs++;
                // Segmentmitten ausserhalb des Kopfes, ausser der eingesteckten Wurzel
                boolean inside = Math.abs(cx) < 4 && cy < 32 && Math.abs(cz) < 4;
                check(!inside || cy > 31.4, "Hornsegment steckt nicht im Kopf (Mitte y=" + cy + ")");
            }
        }
        check(segs >= 16, "mindestens 8 Segmente je Horn (" + segs + ")");
        check(top - 32.0 >= 5.5 && top - 32.0 <= 7.5, "Hoerner ca. 6-7 px ueber dem Kopf (" + (top - 32.0) + ")");
        check(maxX <= 7.0, "nicht zu breit (" + maxX + ")");
        check(minY >= 30.5, "Wurzel nur knapp (max. 1,5 px) im Kopf (" + minY + ")");
        // Symmetrie
        double sum = 0;
        for (Cube c : g.cubes()) for (double[] p : corners(c)) sum += p[0];
        check(Math.abs(sum) < 1e-3 * g.cubes().size() * 8, "links/rechts symmetrisch");
    }

    private static void dragonBones() {
        Geo g = load("bameclient/models/cosmetics/pets/baby_dragon.geo.json");
        for (String b : BabyDragonPose.BONES) check(g.bones().containsKey(b), "Knochen vorhanden: " + b);
        for (Bone b : g.bones().values()) {
            check(b.parent() == null || g.bones().containsKey(b.parent()), "Elternknochen existiert: " + b.name());
        }
        // Nuestern-Punkt liegt an der Schnauzenspitze
        double front = 1e9;
        for (Cube c : g.cubes()) if ("head".equals(c.bone())) for (double[] p : corners(c)) front = Math.min(front, p[2]);
        check(Math.abs(BabyDragonPose.NOSTRIL_Z - front) < 0.15, "Nuester an der Schnauzenspitze (" + front + ")");
    }

    private static boolean isFoot(Cube c) {
        return c.bone() != null && c.bone().startsWith("leg_") && c.bone().endsWith("2");
    }

    /** Teil, das auf der Schulter liegen muss (Schwanz hinter der Schulterkante darf herabhaengen). */
    private static final double SHOULDER_BACK_Z = (2.0 / 16.0 + 0.02) / BabyDragonPose.MODEL_SCALE * 16.0;

    private static void dragonStanding() {
        Geo g = load("bameclient/models/cosmetics/pets/baby_dragon.geo.json");
        BabyDragonPose pose = new BabyDragonPose();
        for (int i = 0; i <= 2000; i++) {
            float t = i * 0.01f;
            pose.compute(t, false, false);
            double footLow = 1e9;
            for (double[] p : posedCorners(g, pose, PetHornsGeometryTest::isFoot)) footLow = Math.min(footLow, p[1]);
            check(Math.abs(footLow - 24.0) < 0.02, "Fuesse auf der Schulter bei t=" + t + " (" + footLow + ")");
            for (double[] p : posedCorners(g, pose, c -> !isFoot(c))) {
                if (p[2] > SHOULDER_BACK_Z) continue;
                check(p[1] > 24.0 - 0.02, "nichts ragt in die Schulter (t=" + t + ", y=" + p[1] + ", z=" + p[2] + ")");
            }
        }
    }

    private static void dragonSleeping() {
        Geo g = load("bameclient/models/cosmetics/pets/baby_dragon.geo.json");
        BabyDragonPose pose = new BabyDragonPose();
        for (int i = 0; i <= 700; i++) {
            float t = i * 0.01f;
            pose.compute(t, true, false);
            double low = 1e9, bellyLow = 1e9;
            for (double[] p : posedCorners(g, pose, c -> true)) {
                if (p[2] > SHOULDER_BACK_Z) continue;
                low = Math.min(low, p[1]);
            }
            for (double[] p : posedCorners(g, pose, c -> "body".equals(c.bone()))) bellyLow = Math.min(bellyLow, p[1]);
            check(low > 24.0 - 0.05, "schlafend nicht in der Schulter (t=" + t + ", " + low + ")");
            check(bellyLow - 24.0 < 0.6, "Bauch liegt auf (t=" + t + ", " + bellyLow + ")");
            // Lider geschlossen: nicht in den Schaedel geschoben
            check(pose.translation("lid_l")[0] == 0.0f && pose.translation("lid_r")[0] == 0.0f, "Augen zu im Schlaf");
        }
        pose.compute(1.0f, false, false);
        check(pose.translation("lid_l")[0] < -0.3f, "wach: Lider eingefahren");
    }

    private static void dragonFlyingAndContinuity() {
        BabyDragonPose a = new BabyDragonPose(), b = new BabyDragonPose();
        for (boolean fly : new boolean[]{false, true}) {
            for (int i = 0; i < 3000; i++) {
                float t = i * 0.005f;
                a.compute(t, false, fly);
                b.compute(t + 0.005f, false, fly);
                for (int k = 0; k < BabyDragonPose.BONES.length; k++) {
                    for (int j = 0; j < 3; j++) {
                        if (BabyDragonPose.BONES[k].startsWith("lid_")) continue; // Blinzeln springt absichtlich
                        check(Math.abs(a.rot[k][j] - b.rot[k][j]) < 5.0f, // Nies-Ruck: bis ~700 Grad/s
                                "stetig: " + BabyDragonPose.BONES[k] + " bei t=" + t + (fly ? " (fliegend)" : ""));
                    }
                }
            }
        }
        // Niesen: Rauch genau waehrend des Rucks, Kopf geht erst hoch, dann runter
        int smoke = 0;
        for (int i = 0; i < 500; i++) if (BabyDragonPose.isSmokeMoment(i * 0.01f)) smoke++;
        check(smoke > 10 && smoke < 40, "Rauch kurz einmal pro Zyklus (" + smoke + ")");
        BabyDragonPose p = new BabyDragonPose();
        p.compute(BabyDragonPose.SNEEZE_START + 0.5f * BabyDragonPose.SNEEZE_LEN, false, false);
        float up = p.rotation("head")[0];
        p.compute(BabyDragonPose.SNEEZE_START + 0.66f * BabyDragonPose.SNEEZE_LEN, false, false);
        float down = p.rotation("head")[0];
        check(up < 0 && down > up + 20, "Niesen: Kopf hoch (" + up + ") dann Ruck nach unten (" + down + ")");
    }

    private static void seat() {
        // Schulter: Arm oben bei Java-y 0, Aermel-Layer 0.25 px hoeher -> Fuesse (Modell-y 0) knapp darueber
        double shoulderFeet = -0.22 + BabyDragonPose.seatOffsetY("right_shoulder");
        check(shoulderFeet < -0.010 && shoulderFeet > -0.025, "Fuesse auf der Schulter (" + shoulderFeet + ")");
        check(BabyDragonPose.seatOffsetY("left_shoulder") == BabyDragonPose.seatOffsetY("right_shoulder"), "beide Schultern gleich");
        double headFeet = -0.70 + BabyDragonPose.seatOffsetY("head");
        check(headFeet < -0.5 && headFeet > -0.56, "Fuesse auf dem Kopf/Hut-Layer (" + headFeet + ")");
        check(BabyDragonPose.isFlying("floating") && !BabyDragonPose.isFlying("right_shoulder"), "schwebend = fliegen");
    }
}

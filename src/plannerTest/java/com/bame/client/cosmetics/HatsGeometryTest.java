package com.bame.client.cosmetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueft ohne Minecraft die neuen Huete (Krone, Kopfhoerer, Wikingerhelm, Propellermuetze, Katzenohren): Modelle
 * vorhanden und in beiden Namespaces gleich, sitzen ohne Luecke auf dem Kopf (Basis zwischen innerer Kopfoberseite 32 und
 * Hut-Layer 32.5, Baender/Waende umschliessen die Kopfseiten, nichts tief im Kopf), verdecken das Gesicht nicht (Augen/Mund), plus Propeller-Drehzahl, Katzenohr-Zucken/Anlegen und
 * Kronen-Glanzlichter (stetig, deterministisch). Knochen-Mathematik wie {@link BedrockCosmeticModel} mit
 * {@code blockbenchAxisFix}.
 */
public class HatsGeometryTest {

    private static int checks = 0;
    private static final String RES = "src/main/resources/assets/";
    private static final String[] HATS = {"crown", "headphones", "viking_helmet", "propeller_cap", "cat_ears"};

    public static void main(String[] args) {
        namespacesIdentical();
        for (String h : HATS) common(h);
        crown();
        headphones();
        vikingHelmet();
        propellerCap();
        catEars();
        glintAnimation();
        propellerAnimation();
        catEarAnimation();
        stateCleanup();
        System.out.println("HATS GEOMETRY TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    // ---------------- Modell lesen (Format der Generator-Ausgabe: ein Wuerfel pro Zeile) ----------------

    private record Cube(String bone, double[] origin, double[] size, double[] pivot, double[] rotation) {}

    private record Bone(String name, String parent, double[] pivot) {}

    private record Geo(List<Cube> cubes, Map<String, Bone> bones) {}

    private static String read(String rel) {
        try {
            return Files.readString(Path.of(RES + rel), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new AssertionError("Datei fehlt: " + rel + " (" + e.getMessage() + ")");
        }
    }

    private static Geo load(String hat) {
        String text = read("bameclient/models/cosmetics/hat/" + hat + ".geo.json");
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
        check(!cubes.isEmpty(), "Wuerfel gelesen: " + hat);
        check(text.contains("\"texture_width\"") && text.contains("geometry." + hat), "Geometrie-Kopf: " + hat);
        return new Geo(cubes, bones);
    }

    private static double[] nums(String s) {
        String[] p = s.split(",");
        double[] v = new double[p.length];
        for (int i = 0; i < p.length; i++) v[i] = Double.parseDouble(p[i].trim());
        return v;
    }

    // ---------------- Mathematik wie BedrockCosmeticModel (blockbenchAxisFix) ----------------

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

    /** Stichproben im Wuerfel (Ecken, Kanten- und Flaechenmitten, Mitte), in Knochen-Ruhelage. */
    private static List<double[]> samples(Cube c) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i <= 2; i++) for (int j = 0; j <= 2; j++) for (int k = 0; k <= 2; k++) {
            double[] p = {c.origin()[0] + c.size()[0] * i / 2.0, c.origin()[1] + c.size()[1] * j / 2.0, c.origin()[2] + c.size()[2] * k / 2.0};
            out.add(c.rotation() == null ? p : rotate(p, c.pivot(), c.rotation()[0], c.rotation()[1], c.rotation()[2]));
        }
        return out;
    }

    /** Punkt mit Knochen-Animation (Grad je Knochen) in Modellkoordinaten. */
    private static double[] posed(Geo g, String bone, double[] p, Map<String, double[]> anim) {
        double[] q = p.clone();
        for (Bone b = g.bones().get(bone); b != null; b = b.parent() != null ? g.bones().get(b.parent()) : null) {
            double[] r = anim != null ? anim.get(b.name()) : null;
            if (r != null) q = rotate(q, b.pivot(), r[0], r[1], r[2]);
        }
        return q;
    }

    private static List<double[]> points(Geo g, Map<String, double[]> anim, java.util.function.Predicate<Cube> filter) {
        List<double[]> out = new ArrayList<>();
        for (Cube c : g.cubes()) {
            if (!filter.test(c)) continue;
            for (double[] p : samples(c)) out.add(posed(g, c.bone(), p, anim));
        }
        return out;
    }

    /** Wie tief liegt p unter der Oberflaeche des Quaders |x|,|z| < half, y < top (0 = aussen). */
    private static double depthIn(double[] p, double half, double top) {
        double d = Math.min(top - p[1], Math.min(half - Math.abs(p[0]), half - Math.abs(p[2])));
        return Math.max(0.0, d);
    }

    // ---------------- Gemeinsame Pruefungen ----------------

    private static void namespacesIdentical() {
        for (String h : HATS) {
            for (String f : new String[]{"models/cosmetics/hat/" + h + ".geo.json", "textures/cosmetics/hat/" + h + ".png"}) {
                check(read("bameclient/" + f).equals(read("caeserclient/" + f)), "beide Namespaces gleich: " + f);
            }
        }
    }

    private static void faceClear(String hat, List<double[]> pts) {
        for (double[] p : pts) {
            if (p[2] >= -4.0) continue;
            double ax = Math.abs(p[0]);
            // Mund/Kinn: unterhalb der Augen nichts vor dem Gesicht
            check(!(ax < 3.5 && p[1] < 27.0), hat + ": verdeckt den Mund (" + Arrays.toString(p) + ")");
            // Augen: Zeilen 27..29, x 1..3.5 frei (ein schmaler Nasenschutz in der Mitte ist erlaubt)
            check(!(ax > 0.75 && ax < 3.5 && p[1] >= 27.0 && p[1] <= 29.0), hat + ": verdeckt die Augen (" + Arrays.toString(p) + ")");
        }
    }

    private static void common(String hat) {
        Geo g = load(hat);
        for (Bone b : g.bones().values()) {
            check(b.parent() == null || g.bones().containsKey(b.parent()), hat + ": Elternknochen existiert: " + b.name());
        }
        List<double[]> pts = points(g, null, c -> true);
        double minY = 1e9, maxY = -1e9, maxDepth = 0;
        for (double[] p : pts) {
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
            check(Math.abs(p[0]) <= 10.0 && Math.abs(p[2]) <= 7.5, hat + ": nicht zu ausladend " + Arrays.toString(p));
            maxDepth = Math.max(maxDepth, depthIn(p, 4.5, 32.5));
        }
        check(minY >= 24.5, hat + ": nichts unterhalb des Kopfes (" + minY + ")");
        check(maxY <= 40.0, hat + ": nicht zu hoch (" + maxY + ")");
        check(maxDepth <= 1.0, hat + ": nichts tief im Kopf/Hut-Layer (" + maxDepth + ")");
        faceClear(hat, pts);
        // Symmetrie (ausser Kopfhoerer mit Mikrofon links)
        if (!"headphones".equals(hat)) {
            double sum = 0;
            for (double[] p : pts) sum += p[0];
            check(Math.abs(sum) < 1e-2 * pts.size(), hat + ": links/rechts symmetrisch (" + sum / pts.size() + ")");
        }
    }

    /** Schalen-Huete: unterhalb der Kopfoberseite kein Punkt im Hut-Layer (kein Clipping in die Haut-Overlay-Pixel). */
    private static void noClip(String hat, Geo g, java.util.function.Predicate<Cube> filter, double tol) {
        for (double[] p : points(g, null, filter)) {
            check(depthIn(p, 4.5, 32.5) <= tol, hat + ": clippt in den Hut-Layer " + Arrays.toString(p));
        }
    }

    /** Kopf: innere Oberseite 32, Hut-Layer (0.5 px) bis 32.5; Seiten +-4 bzw. +-4.5. */
    private static final double LAYER_TOP = 32.5, HEAD_TOP = 32.0, LAYER_HALF = 4.5, HEAD_HALF = 4.0;

    /**
     * Keine Luecke: der tiefste Punkt ueber der Kopfoberseite (|x|,|z| <= 4) liegt zwischen minY (innere
     * Kopfoberseite bzw. leicht darunter) und der Hut-Layer-Oberseite (32.5); nichts ragt tiefer in den inneren Kopf.
     */
    private static void restsOnHead(String hat, List<double[]> pts, double minY) {
        double low = 1e9;
        for (double[] p : pts) {
            double r = Math.max(Math.abs(p[0]), Math.abs(p[2]));
            if (r <= HEAD_HALF && p[1] > 29.0) low = Math.min(low, p[1]);
            if (r < HEAD_HALF - 0.05) check(p[1] >= minY - 1e-6, hat + ": nichts tief im inneren Kopf " + Arrays.toString(p));
        }
        check(low <= LAYER_TOP + 1e-6, hat + ": liegt auf dem Kopf auf, keine Luecke (" + low + " > " + LAYER_TOP + ")");
        check(low >= minY - 1e-6, hat + ": nicht tief im Kopf (" + low + ")");
    }

    /** Ein Ring/Band um den Kopf deckt die Ecken des Hut-Layers ab (sie stechen nicht durch). */
    private static void coversLayerCorners(String hat, List<double[]> pts, double y0, double y1) {
        for (int q = 0; q < 4; q++) {
            double sx = (q & 1) == 0 ? 1 : -1, sz = (q & 2) == 0 ? 1 : -1;
            double best = 0;
            for (double[] p : pts) {
                if (p[1] < y0 || p[1] > y1 || p[0] * sx < 0 || p[2] * sz < 0) continue;
                best = Math.max(best, (Math.abs(p[0]) + Math.abs(p[2])) / Math.sqrt(2));
            }
            check(best > LAYER_HALF * Math.sqrt(2) + 0.05, hat + ": deckt die Hut-Layer-Ecke ab (Quadrant " + q + ", " + best + ")");
        }
    }

    // ---------------- Hut-spezifisch ----------------

    private static void crown() {
        Geo g = load("crown");
        check(g.bones().containsKey("base"), "Krone: Knochen base");
        // Sitz wie getragen: Band umschliesst den Kopf, Unterkante auf den Kopfseiten, Samt liegt auf dem Kopf
        List<double[]> all = points(g, null, c -> true);
        double rim = 1e9, rimInner = 1e9;
        for (double[] p : all) {
            rim = Math.min(rim, p[1]);
            if (p[1] < HEAD_TOP) rimInner = Math.min(rimInner, Math.max(Math.abs(p[0]), Math.abs(p[2])));
        }
        check(rim >= 30.5 && rim <= 31.75, "Krone: Bandunterkante ueberlappt die Kopfseiten (" + rim + ")");
        check(rimInner >= 4.2 && rimInner <= 4.6, "Krone: Band liegt eng am Kopf/Hut-Layer an (" + rimInner + ")");
        restsOnHead("crown", all, HEAD_TOP);
        coversLayerCorners("crown", all, 31.6, 32.4);
        for (int i = 0; i < HatAnimations.CROWN_GLINTS; i++) {
            String b = "glint" + i;
            check(g.bones().containsKey(b), "Krone: Glanzlicht-Knochen " + b);
            check("crown".equals(g.bones().get(b).parent()), "Glanzlicht haengt an der Wurzel: " + b);
            double[] c = new double[3];
            int n = 0;
            for (double[] p : points(g, null, cb -> b.equals(cb.bone()))) { c[0] += p[0]; c[1] += p[1]; c[2] += p[2]; n++; }
            check(n > 0, "Glanzlicht hat Geometrie: " + b);
            float[] want = HatAnimations.CROWN_GLINT_POS[i];
            double d = Math.hypot(Math.hypot(c[0] / n - want[0], c[1] / n - want[1]), c[2] / n - want[2]);
            check(d < 0.08, "Funken-Position passt zum Glanzlicht " + b + " (" + d + ")");
        }
        check(HatAnimations.CROWN_GLINT_POS.length == HatAnimations.CROWN_GLINTS, "Anzahl Funken-Positionen");
    }

    private static void headphones() {
        Geo g = load("headphones");
        check(g.bones().containsKey("base") && g.bones().containsKey("glow"), "Kopfhoerer: base + glow");
        // Polster duerfen leicht in den Hut-Layer einsinken (liegen auf), aber nicht tief
        noClip("headphones", g, c -> true, 0.35);
        double band = 1e9, cup = 1e9;
        for (double[] p : points(g, null, c -> true)) {
            if (Math.abs(p[0]) < 3.0 && p[1] > 30.0) band = Math.min(band, p[1]);
            if (p[1] > 26.0 && p[1] < 29.0) cup = Math.min(cup, Math.abs(p[0]));
        }
        check(band >= HEAD_TOP && band <= LAYER_TOP, "Kopfhoerer: Buegel liegt auf dem Kopf (" + band + ")");
        check(cup >= HEAD_HALF && cup <= LAYER_HALF, "Kopfhoerer: Muscheln liegen an den Kopfseiten an (" + cup + ")");
        // Muscheln auf beiden Seiten, Buegel ueber dem Kopf
        double left = 0, right = 0, top = -1e9;
        for (double[] p : points(g, null, c -> true)) {
            if (p[1] < 30 && p[1] > 26) { left = Math.max(left, p[0]); right = Math.min(right, p[0]); }
            if (Math.abs(p[0]) < 1) top = Math.max(top, p[1]);
        }
        check(left > 6.3 && right < -6.3, "Ohrmuscheln an beiden Seiten (" + left + ", " + right + ")");
        check(top > 32.8 && top < 34.5, "Buegel knapp ueber dem Kopf (" + top + ")");
        for (double[] p : points(g, null, c -> "glow".equals(c.bone()))) {
            check(Math.abs(p[0]) > 6.4, "Logo/LED aussen auf der Muschel " + Arrays.toString(p));
        }
    }

    private static void vikingHelmet() {
        Geo g = load("viking_helmet");
        noClip("viking_helmet", g, c -> true, 0.0);
        // eng anliegend: Wand umschliesst die Kopfseiten (bis unter 30), innen max. 0.25 px Abstand zum Hut-Layer,
        // Kuppel-Innenseite hoechstens 0.5 px ueber dem Hut-Layer
        List<double[]> all = points(g, null, c -> true);
        double rim = 1e9, wall = 1e9, dome = 1e9;
        for (double[] p : all) {
            double r = Math.max(Math.abs(p[0]), Math.abs(p[2]));
            if (Math.abs(p[0]) > 4.0 && Math.abs(p[0]) < 6.0) rim = Math.min(rim, p[1]);
            if (p[1] > 30.5 && p[1] < 32.0) wall = Math.min(wall, r);
            if (r <= LAYER_HALF) dome = Math.min(dome, p[1]);
        }
        check(rim <= 30.0, "Wikingerhelm: Rand ueberlappt die Kopfseiten (" + rim + ")");
        check(wall >= LAYER_HALF && wall <= LAYER_HALF + 0.25, "Wikingerhelm: Wand eng am Kopf (" + wall + ")");
        check(dome >= LAYER_TOP && dome <= LAYER_TOP + 0.5, "Wikingerhelm: Kuppel eng ueber dem Kopf (" + dome + ")");
        coversLayerCorners("viking_helmet", all, 30.0, 32.4);
        double hornTop = -1e9, hornX = 0;
        for (double[] p : points(g, null, c -> true)) {
            if (Math.abs(p[0]) > 6.0) { hornTop = Math.max(hornTop, p[1]); hornX = Math.max(hornX, Math.abs(p[0])); }
        }
        check(hornTop > 35.5 && hornTop < 38.5, "Hoerner nach oben (" + hornTop + ")");
        check(hornX > 8.0 && hornX < 10.0, "Hoerner nach aussen, kuerzer als breit (" + hornX + ")");
        // Nasenschutz vorhanden, schmal und vor dem Gesicht
        boolean nasal = false;
        for (double[] p : points(g, null, c -> true)) if (p[2] < -5.0 && p[1] < 28.5 && Math.abs(p[0]) <= 0.55) nasal = true;
        check(nasal, "Nasenschutz vorhanden");
    }

    private static void propellerCap() {
        Geo g = load("propeller_cap");
        check(g.bones().containsKey("propeller"), "Propeller-Knochen");
        // Kuppel-Innenseite darf minimal in den Hut-Layer einsinken (liegt auf)
        noClip("propeller_cap", g, c -> !"propeller".equals(c.bone()), 0.15);
        List<double[]> capPts = points(g, null, c -> !"propeller".equals(c.bone()));
        // geschlossene Kappe: sichtbarer Kontakt ueber die Wand an den Kopfseiten; die Kuppel liegt flach auf
        // (Innenseite ueber der Kopfmitte hoechstens 0.6 px ueber dem Hut-Layer, nichts im inneren Kopf)
        double inner = 1e9;
        for (double[] p : capPts) {
            if (Math.max(Math.abs(p[0]), Math.abs(p[2])) <= 3.0 && p[1] > 29.0) inner = Math.min(inner, p[1]);
            if (Math.max(Math.abs(p[0]), Math.abs(p[2])) < HEAD_HALF - 0.05) check(p[1] >= HEAD_TOP, "Muetze: nichts im inneren Kopf");
        }
        check(inner >= LAYER_TOP - 0.15 && inner <= LAYER_TOP + 0.6, "Muetze: Kuppel liegt eng auf dem Kopf (" + inner + ")");
        coversLayerCorners("propeller_cap", capPts, 30.0, 32.4);
        double rim = 1e9, crownTop = -1e9, visorZ = 0;
        for (double[] p : capPts) {
            if (Math.abs(p[0]) > 4.0 && Math.abs(p[0]) < 6.0) rim = Math.min(rim, p[1]);
            if (Math.abs(p[0]) < 2.5 && Math.abs(p[2]) < 2.5 && Math.max(Math.abs(p[0]), Math.abs(p[2])) > 1.0) crownTop = Math.max(crownTop, p[1]);
            visorZ = Math.min(visorZ, p[2]);
        }
        check(rim <= 30.0 && rim >= 29.0, "Muetze bedeckt die oberen Kopfseiten (" + rim + ")");
        check(crownTop - LAYER_TOP < 2.0, "Muetze flach und rund, nicht spitz (Scheitel " + crownTop + ")");
        check(visorZ < -6.5, "Schirm vorn (" + visorZ + ")");
        double capTop = -1e9;
        for (double[] p : points(g, null, c -> "base".equals(c.bone()) && Math.abs(c.size()[0]) > 1.0)) capTop = Math.max(capTop, p[1]);
        double[] piv = g.bones().get("propeller").pivot();
        check(Math.abs(piv[0]) < 1e-6 && Math.abs(piv[2]) < 1e-6, "Propeller dreht um die Kopfachse");
        for (int a = 0; a < 360; a += 15) {
            Map<String, double[]> anim = Map.of("propeller", new double[]{0, a, 0});
            for (double[] p : points(g, anim, c -> "propeller".equals(c.bone()))) {
                check(p[1] > capTop + 0.6, "Propellerblatt ueber der Muetze (Winkel " + a + ")");
                check(Math.hypot(p[0], p[2]) < 4.8, "Propeller nicht breiter als der Kopf (Winkel " + a + ")");
            }
        }
    }

    private static void catEars() {
        Geo g = load("cat_ears");
        check(g.bones().containsKey("ear_l") && g.bones().containsKey("ear_r"), "Ohr-Knochen");
        // Ueber die Animation (Zucken + Anlegen): nichts tief im Kopf, Gesicht frei
        double restTipZ = tip(g, null)[2];
        restsOnHead("cat_ears", points(g, null, c -> true), 31.5);
        for (String ear : new String[]{"ear_l", "ear_r"}) {
            double low = 1e9;
            for (double[] p : points(g, null, c -> ear.equals(c.bone()))) low = Math.min(low, p[1]);
            check(low >= 31.5 && low <= HEAD_TOP, "Ohransatz steckt im Kopf (" + ear + ", " + low + ")");
        }
        for (int i = 0; i <= 600; i++) {
            float t = i * 0.05f;
            for (float sneak : new float[]{0f, 0.5f, 1f}) {
                float[] p = HatAnimations.catEarPose(t, 12345L, sneak);
                Map<String, double[]> anim = Map.of("ear_l", new double[]{p[0], 0, p[1]}, "ear_r", new double[]{p[2], 0, p[3]});
                for (double[] q : points(g, anim, c -> true)) {
                    // beim Anlegen taucht der Ansatz hinten tiefer ein (verdeckt), aber nie durch den Kopf
                    check(depthIn(q, 4.5, 32.5) <= 1.8, "Ohr steckt nicht tief im Kopf (t=" + t + ", sneak=" + sneak + ", " + Arrays.toString(q) + ")");
                }
                // keine Luecke unter dem Ohr: Unterseite des Fellansatzes bleibt unter der Hut-Layer-Oberseite
                for (String ear : new String[]{"ear_l", "ear_r"}) {
                    double maxBottom = -1e9;
                    for (Cube c : g.cubes()) {
                        if (!ear.equals(c.bone()) || c.origin()[1] >= HEAD_TOP) continue; // nur der Ansatz
                        for (int k = 0; k < 4; k++) {
                            double[] corner = {c.origin()[0] + ((k & 1) == 0 ? 0 : c.size()[0]), c.origin()[1], c.origin()[2] + ((k & 2) == 0 ? 0 : c.size()[2])};
                            maxBottom = Math.max(maxBottom, posed(g, ear, corner, anim)[1]);
                        }
                    }
                    check(maxBottom > -1e8 && maxBottom <= LAYER_TOP, "Ohransatz liegt auf (t=" + t + ", sneak=" + sneak + ", " + ear + ", " + maxBottom + ")");
                }
                faceClear("cat_ears", points(g, anim, c -> true));
            }
        }
        float[] flat = HatAnimations.catEarPose(0.0f, 1L, 1.0f);
        double[] ft = tip(g, Map.of("ear_l", new double[]{flat[0], 0, flat[1]}, "ear_r", new double[]{flat[2], 0, flat[3]}));
        double[] rt = tip(g, null);
        check(ft[2] > restTipZ + 1.5, "angelegt: Ohrspitze nach hinten (" + ft[2] + " vs " + restTipZ + ")");
        check(ft[1] < rt[1] - 1.0, "angelegt: Ohrspitze tiefer (" + ft[1] + " vs " + rt[1] + ")");
    }

    /** Hoechster Punkt des linken Ohrs. */
    private static double[] tip(Geo g, Map<String, double[]> anim) {
        double[] best = null;
        for (double[] p : points(g, anim, c -> "ear_l".equals(c.bone()))) if (best == null || p[1] > best[1]) best = p;
        return best;
    }

    // ---------------- Animationen ----------------

    private static void glintAnimation() {
        int[] flashes = new int[HatAnimations.CROWN_GLINTS];
        float[] prev = new float[HatAnimations.CROWN_GLINTS];
        int maxTogether = 0;
        for (int ms = 0; ms <= 8000; ms++) {
            float t = 100.0f + ms / 1000.0f;
            int together = 0;
            for (int i = 0; i < HatAnimations.CROWN_GLINTS; i++) {
                float k = HatAnimations.crownGlint(i, t);
                check(k >= 0 && k <= 1, "Glanzlicht 0..1");
                if (ms > 0) check(Math.abs(k - prev[i]) < 0.05f, "Glanzlicht stetig (i=" + i + ", t=" + t + ")");
                if (k > 0.9f && prev[i] <= 0.9f) flashes[i]++;
                if (k > 0.5f) together++;
                prev[i] = k;
                check(k == HatAnimations.crownGlint(i, t), "Glanzlicht deterministisch");
            }
            maxTogether = Math.max(maxTogether, together);
        }
        for (int i = 0; i < flashes.length; i++) check(flashes[i] >= 2, "Glanzlicht " + i + " blitzt auf (" + flashes[i] + ")");
        check(maxTogether <= 4, "gestaffelt, nicht alle gleichzeitig (" + maxTogether + ")");
        int sparks = 0;
        for (long s = 0; s < 1000; s++) {
            int gi = HatAnimations.crownSparkle(s, 42L);
            check(gi >= -1 && gi < HatAnimations.CROWN_GLINTS, "Funken-Index gueltig");
            if (gi >= 0) sparks++;
        }
        check(sparks > 120 && sparks < 420, "Funken selten (" + sparks + " / 1000 Slots)");
        float p0 = HatAnimations.headphonePulse(0f), p1 = HatAnimations.headphonePulse(1.3f);
        check(p0 >= 0.78f && p0 <= 1f && p1 >= 0.78f && p1 <= 1f, "Logo-Puls sanft");
        check(HatAnimations.scaleRgb(0xB23CEE, 1f) == 0xFFB23CEE && HatAnimations.scaleRgb(0xFFFFFF, 0f) == 0xFF000000, "Farbskalierung");
    }

    private static void propellerAnimation() {
        check(HatAnimations.propellerTargetSpeed(0f) == HatAnimations.PROPELLER_IDLE_DPS, "Propeller dreht im Stand langsam");
        check(Math.abs(HatAnimations.propellerTargetSpeed(1f) - HatAnimations.PROPELLER_MAX_DPS) < 1e-3, "Propeller beim Sprinten schnell");
        float last = -1;
        for (int i = 0; i <= 100; i++) {
            float v = HatAnimations.propellerTargetSpeed(i / 100f);
            check(v >= last, "Zieldrehzahl monoton");
            if (i > 0) check(v - last < 60f, "Zieldrehzahl stetig");
            last = v;
        }
        HatAnimations.Spinner a = new HatAnimations.Spinner(), b = new HatAnimations.Spinner();
        float dt = 1 / 60f;
        float prevSpeed = a.speed;
        for (int i = 0; i < 60 * 12; i++) {
            float t = i * dt;
            float amp = t < 2 ? 0 : (t < 6 ? 1 : 0); // stehen, laufen, stehen
            float target = HatAnimations.propellerTargetSpeed(amp);
            float before = a.angle;
            a.advance(target, dt);
            b.advance(target, dt);
            check(a.angle == b.angle && a.speed == b.speed, "Propeller deterministisch");
            check(Math.abs(a.speed - prevSpeed) < 80f, "Propeller beschleunigt weich (t=" + t + ")");
            float step = ((a.angle - before) % 360f + 360f) % 360f;
            check(step < 40f, "kein Sprung im Winkel (t=" + t + ", " + step + ")");
            prevSpeed = a.speed;
            if (Math.abs(t - 5.9f) < dt / 2) check(a.speed > 1700f, "nach Anlaufen fast volle Drehzahl (" + a.speed + ")");
            if (Math.abs(t - 6.5f) < dt / 2) check(a.speed > 500f, "laeuft nach (" + a.speed + ")");
            if (Math.abs(t - 11.9f) < dt / 2) check(a.speed < 200f, "bremst auf Leerlauf ab (" + a.speed + ")");
        }
        HatAnimations.Spinner c = new HatAnimations.Spinner();
        c.advance(1800f, 5.0f); // riesiger Frame-Sprung wird begrenzt
        check(c.speed < 600f, "Frame-Spruenge begrenzt (" + c.speed + ")");
    }

    private static void catEarAnimation() {
        float[] prev = HatAnimations.catEarPose(50f, 99L, 0f);
        int twitchesL = 0, twitchesR = 0;
        boolean inL = false, inR = false;
        for (int ms = 1; ms <= 600_000; ms++) {
            float t = 50f + ms / 1000f;
            float[] p = HatAnimations.catEarPose(t, 99L, 0f);
            for (int k = 0; k < 4; k++) check(Math.abs(p[k] - prev[k]) < 0.5f, "Ohren stetig (t=" + t + ")");
            float tl = HatAnimations.earTwitch(t, 99L, 0), tr = HatAnimations.earTwitch(t, 99L, 1);
            check(tl == 0f || tr == 0f, "nur ein Ohr zuckt gleichzeitig");
            if (tl > 0.5f && !inL) twitchesL++;
            if (tr > 0.5f && !inR) twitchesR++;
            inL = tl > 0.5f;
            inR = tr > 0.5f;
            prev = p;
        }
        int total = twitchesL + twitchesR;
        check(total > 70 && total < 220, "Zucken alle paar Sekunden (" + total + " in 600 s)");
        check(twitchesL > 20 && twitchesR > 20, "beide Ohren zucken mal (" + twitchesL + "/" + twitchesR + ")");
        // deterministisch je Spieler, verschieden zwischen Spielern
        boolean differs = false;
        for (int i = 0; i < 2000; i++) {
            float t = i * 0.05f;
            check(Arrays.equals(HatAnimations.catEarPose(t, 7L, 0.3f), HatAnimations.catEarPose(t, 7L, 0.3f)), "Ohren deterministisch");
            if (HatAnimations.earTwitch(t, 7L, 0) != HatAnimations.earTwitch(t, 8L, 0)) differs = true;
        }
        check(differs, "verschiedene Spieler zucken verschieden");
        // Schleichen: weiches Anlegen und Aufrichten
        float s = 0f;
        float last = 0f;
        for (int i = 0; i < 36; i++) {
            s = HatAnimations.sneakStep(s, true, 1 / 60f);
            check(s >= last && s - last < 0.2f, "Anlegen weich");
            last = s;
        }
        check(s > 0.95f, "nach 0.6 s angelegt (" + s + ")");
        float[] p = HatAnimations.catEarPose(3.0f, 1L, s);
        check(p[0] < -40f && p[2] < -40f, "angelegt nach hinten (" + p[0] + ")");
        check(p[1] > 0f && p[3] < 0f, "angelegt leicht nach aussen");
        for (int i = 0; i < 60; i++) s = HatAnimations.sneakStep(s, false, 1 / 60f);
        check(s < 0.01f, "richtet sich wieder auf (" + s + ")");
    }

    private static void stateCleanup() {
        int before = HatAnimations.trackedStates();
        check(before == 0, "Start ohne Zustand");
        HatAnimations.state(1, 1000L);
        HatAnimations.state(2, 1000L);
        HatAnimations.PlayerHatState a = HatAnimations.state(1, 2000L);
        check(a == HatAnimations.state(1, 2500L), "Zustand bleibt je Spieler erhalten");
        check(HatAnimations.trackedStates() == 2, "zwei Spieler verfolgt");
        HatAnimations.state(3, 30_000L);
        check(HatAnimations.trackedStates() == 1, "alte Spieler entfernt (" + HatAnimations.trackedStates() + ")");
        float dt0 = a.tick(1_000_000_000L), dt1 = a.tick(1_016_000_000L);
        check(dt0 == 0f && Math.abs(dt1 - 0.016f) < 1e-4, "Zeitschritt pro Spieler");
    }
}

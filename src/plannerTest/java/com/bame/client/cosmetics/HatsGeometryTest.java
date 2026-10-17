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
        // Helme (Samurai, Ritter)
        helmetsFiles();
        samuraiHelmet();
        knightHelmet();
        helmetAnimations();
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

    // ---------------- Helme (Samurai, Ritter) ----------------

    private static final String[] HELMETS = {HelmetAnimations.SAMURAI_HELMET, HelmetAnimations.KNIGHT_HELMET};

    /** Beide Namespaces gleich, UVs innerhalb der Textur (Groesse aus dem PNG), Texturgroesse passt zum Modell. */
    private static void helmetsFiles() {
        Pattern uvp = Pattern.compile("\"uv\":\\s*\\[([-0-9.]+),\\s*([-0-9.]+)\\],\\s*\"uv_size\":\\s*\\[([-0-9.]+),\\s*([-0-9.]+)\\]");
        Pattern tw = Pattern.compile("\"texture_width\":\\s*(\\d+),\\s*\"texture_height\":\\s*(\\d+)");
        for (String h : HELMETS) {
            for (String f : new String[]{"models/cosmetics/hat/" + h + ".geo.json", "textures/cosmetics/hat/" + h + ".png"}) {
                check(read("bameclient/" + f).equals(read("caeserclient/" + f)), "beide Namespaces gleich: " + f);
            }
            String geo = read("bameclient/models/cosmetics/hat/" + h + ".geo.json");
            Matcher mt = tw.matcher(geo);
            check(mt.find(), h + ": Texturgroesse im Modell");
            int w = Integer.parseInt(mt.group(1)), hh = Integer.parseInt(mt.group(2));
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(Path.of(RES + "bameclient/textures/cosmetics/hat/" + h + ".png").toFile());
                check(img != null && img.getWidth() == w && img.getHeight() == hh, h + ": PNG " + (img == null ? "?" : img.getWidth() + "x" + img.getHeight()) + " passt zu " + w + "x" + hh);
            } catch (IOException e) {
                throw new AssertionError(h + ": PNG unlesbar");
            }
            Matcher mu = uvp.matcher(geo);
            int n = 0;
            while (mu.find()) {
                double u = Double.parseDouble(mu.group(1)), v = Double.parseDouble(mu.group(2));
                double us = Double.parseDouble(mu.group(3)), vs = Double.parseDouble(mu.group(4));
                check(u >= 0 && v >= 0 && us > 0 && vs > 0 && u + us <= w + 1e-6 && v + vs <= hh + 1e-6, h + ": UV in der Textur (" + u + "," + v + " " + us + "x" + vs + ")");
                n++;
            }
            check(n > 100, h + ": Flaechen mit UV (" + n + ")");
        }
        check(HelmetAnimations.isHelmet("SAMURAI_HELMET") && HelmetAnimations.isHelmet("knight_helmet") && !HelmetAnimations.isHelmet("viking_helmet")
                && !HelmetAnimations.isHelmet(null), "Helm-IDs");
        check("SAMURAI_HELMET".equals(HelmetAnimations.SAMURAI_HELMET.toUpperCase()) && "KNIGHT_HELMET".equals(HelmetAnimations.KNIGHT_HELMET.toUpperCase()), "Shop-IDs = Mod-IDs in GROSS");
    }

    /** Punkt (Knochenraum) im Wuerfel (mit Wuerfel-Rotation), tol = Mindesttiefe. */
    private static boolean inCube(Cube c, double[] p, double tol) {
        double[] q = p;
        if (c.rotation() != null) {
            // Umkehrung von rotate(): Rx(rx) * Ry(-ry) * Rz(rz) um den Pivot
            double x = p[0] - c.pivot()[0], y = p[1] - c.pivot()[1], z = p[2] - c.pivot()[2];
            double g = Math.toRadians(c.rotation()[2]);
            double x1 = x * Math.cos(g) - y * Math.sin(g), y1 = x * Math.sin(g) + y * Math.cos(g);
            double b = Math.toRadians(-c.rotation()[1]);
            double x2 = x1 * Math.cos(b) + z * Math.sin(b), z2 = -x1 * Math.sin(b) + z * Math.cos(b);
            double a = Math.toRadians(c.rotation()[0]);
            double y3 = y1 * Math.cos(a) - z2 * Math.sin(a), z3 = y1 * Math.sin(a) + z2 * Math.cos(a);
            q = new double[]{x2 + c.pivot()[0], y3 + c.pivot()[1], z3 + c.pivot()[2]};
        }
        return q[0] > c.origin()[0] + tol && q[0] < c.origin()[0] + c.size()[0] - tol
                && q[1] > c.origin()[1] + tol && q[1] < c.origin()[1] + c.size()[1] - tol
                && q[2] > c.origin()[2] + tol && q[2] < c.origin()[2] + c.size()[2] - tol;
    }

    /** Dichtere Stichproben (n x n x n) eines Wuerfels in Knochen-Ruhelage. */
    private static List<double[]> dense(Cube c, int n) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) for (int j = 0; j <= n; j++) for (int k = 0; k <= n; k++) {
            double[] p = {c.origin()[0] + c.size()[0] * i / n, c.origin()[1] + c.size()[1] * j / n, c.origin()[2] + c.size()[2] * k / n};
            out.add(c.rotation() == null ? p : rotate(p, c.pivot(), c.rotation()[0], c.rotation()[1], c.rotation()[2]));
        }
        return out;
    }

    /** Trifft ein Strahl von (x, y, -4.5) nach vorn (-z) einen Wuerfel der Knochen (mit Animation)? */
    private static boolean rayHitsFront(Geo g, Map<String, double[]> anim, java.util.function.Predicate<Cube> filter, double x, double y) {
        for (double z = -4.55; z > -9.0; z -= 0.04) {
            double[] p = {x, y, z};
            for (Cube c : g.cubes()) {
                if (!filter.test(c)) continue;
                // Punkt in den Knochenraum zurueck: Knochen-Animation umkehren (nur Rotation um Pivots)
                double[] q = unposed(g, c.bone(), p, anim);
                if (inCube(c, q, -0.01)) return true;
            }
        }
        return false;
    }

    /** Umkehrung von posed(): Rotationen der Kette von der Wurzel zum Knochen mit negativen Winkeln, umgekehrte Reihenfolge. */
    private static double[] unposed(Geo g, String bone, double[] p, Map<String, double[]> anim) {
        List<Bone> chain = new ArrayList<>();
        for (Bone b = g.bones().get(bone); b != null; b = b.parent() != null ? g.bones().get(b.parent()) : null) chain.add(0, b);
        double[] q = p.clone();
        for (Bone b : chain) {
            double[] r = anim != null ? anim.get(b.name()) : null;
            if (r == null) continue;
            // inverse von Rz(-rz) Ry(ry) Rx(-rx): Rx(rx) Ry(-ry) Rz(rz)
            double x = q[0] - b.pivot()[0], y = q[1] - b.pivot()[1], z = q[2] - b.pivot()[2];
            double gz = Math.toRadians(r[2]);
            double x1 = x * Math.cos(gz) - y * Math.sin(gz), y1 = x * Math.sin(gz) + y * Math.cos(gz);
            double by = Math.toRadians(-r[1]);
            double x2 = x1 * Math.cos(by) + z * Math.sin(by), z2 = -x1 * Math.sin(by) + z * Math.cos(by);
            double ax = Math.toRadians(r[0]);
            double y3 = y1 * Math.cos(ax) - z2 * Math.sin(ax), z3 = y1 * Math.sin(ax) + z2 * Math.cos(ax);
            q = new double[]{x2 + b.pivot()[0], y3 + b.pivot()[1], z3 + b.pivot()[2]};
        }
        return q;
    }

    /** Gemeinsame Pruefungen der Helme in einer Pose: Grenzen, nichts im Kopf/Hut-Layer. */
    private static void helmetBounds(String hat, List<double[]> pts, double maxAbsZ) {
        for (double[] p : pts) {
            check(Math.abs(p[0]) <= 10.0 && Math.abs(p[2]) <= maxAbsZ, hat + ": nicht zu ausladend " + Arrays.toString(p));
            check(p[1] >= 24.5 && p[1] <= 40.0, hat + ": Hoehe im Rahmen " + Arrays.toString(p));
            check(depthIn(p, LAYER_HALF, LAYER_TOP) <= 1e-6 || p[1] < 23.5, hat + ": nichts im Kopf/Hut-Layer " + Arrays.toString(p));
        }
    }

    private static Map<String, double[]> samuraiAnim(float[] swing) {
        Map<String, double[]> a = new HashMap<>();
        for (int r = 0; r < HelmetAnimations.SHIKORO_ROWS; r++) {
            a.put("shikoro" + r, new double[]{swing[r * 3], 0, 0});
            a.put("shikoro" + r + "_l", new double[]{0, 0, -swing[r * 3 + 1]});
            a.put("shikoro" + r + "_r", new double[]{0, 0, swing[r * 3 + 2]});
        }
        return a;
    }

    private static void samuraiHelmet() {
        String id = HelmetAnimations.SAMURAI_HELMET;
        Geo g = load(id);
        for (Bone b : g.bones().values()) check(b.parent() == null || g.bones().containsKey(b.parent()), id + ": Elternknochen existiert: " + b.name());
        check(g.bones().containsKey("base"), id + ": Knochen base");
        for (int r = 0; r < HelmetAnimations.SHIKORO_ROWS; r++) {
            for (String sfx : new String[]{"", "_l", "_r"}) {
                Bone b = g.bones().get("shikoro" + r + sfx);
                check(b != null, id + ": Lamellen-Knochen shikoro" + r + sfx);
                String want = r == 0 ? id : "shikoro" + (r - 1) + sfx;
                check(want.equals(b.parent()), id + ": untere Reihe haengt an der oberen: shikoro" + r + sfx + " -> " + b.parent());
                // Pivot an der Oberkante der Reihe: alle Wuerfel des Knochens liegen darunter
                for (double[] p : points(g, null, c -> b.name().equals(c.bone()))) check(p[1] <= b.pivot()[1] + 1e-3, id + ": Pivot oben an der Reihe " + b.name());
            }
        }
        // Ruhelage: Grenzen, nichts im Kopf, Gesicht frei, symmetrisch, sitzt eng
        List<double[]> all = points(g, null, c -> !c.bone().startsWith("glint"));
        helmetBounds(id, all, 7.5);
        faceClear(id, all);
        double sum = 0;
        for (double[] p : all) sum += p[0];
        check(Math.abs(sum) < 1e-2 * all.size(), id + ": links/rechts symmetrisch (" + sum / all.size() + ")");
        double wall = 1e9, dome = 1e9, rim = 1e9, bottom = 1e9;
        for (double[] p : all) {
            double r = Math.max(Math.abs(p[0]), Math.abs(p[2]));
            if (p[1] > 30.8 && p[1] < 32.3) wall = Math.min(wall, r);
            if (r <= LAYER_HALF) dome = Math.min(dome, p[1]);
            if (Math.abs(p[0]) > 4.0 && Math.abs(p[0]) < 6.0 && Math.abs(p[2]) < 2.0) rim = Math.min(rim, p[1]);
            if (p[2] > 4.5) bottom = Math.min(bottom, p[1]);
        }
        check(wall >= LAYER_HALF && wall <= LAYER_HALF + 0.25, id + ": Helmglocke eng am Kopf (" + wall + ")");
        check(dome >= LAYER_TOP && dome <= LAYER_TOP + 0.8, id + ": Glocke liegt ohne Luecke auf (" + dome + ")");
        check(rim <= 29.0, id + ": Glocke/Shikoro umschliessen die Kopfseiten (" + rim + ")");
        check(bottom <= 26.0, id + ": Nackenschutz reicht bis in den Nacken (" + bottom + ")");
        coversLayerCorners(id, all, 30.3, 32.4);
        // Hoerner vorn, nach oben/aussen; Glanz-Knochen direkt vor den Hornsegmenten
        double hornTop = -1e9, hornX = 0;
        for (double[] p : all) if (p[2] < -5.3 && p[1] > 32.5) { hornTop = Math.max(hornTop, p[1]); hornX = Math.max(hornX, Math.abs(p[0])); }
        check(hornTop > 36.5 && hornTop < 40.0 && hornX > 4.0, id + ": goldene Hoerner vorn (" + hornTop + ", " + hornX + ")");
        for (int i = 0; i < HelmetAnimations.HORN_GLINTS; i++) {
            String b = "glint" + i;
            check(g.bones().containsKey(b) && id.equals(g.bones().get(b).parent()), id + ": Glanz-Knochen " + b);
            List<double[]> gp = points(g, null, c -> b.equals(c.bone()));
            check(!gp.isEmpty(), id + ": Glanz hat Geometrie " + b);
            double lx = 0, rx = 0;
            for (double[] p : gp) {
                check(p[2] < -5.3 && p[1] > 31.0, id + ": Glanz liegt vorn auf den Hoernern " + Arrays.toString(p));
                if (p[0] > 0) lx++; else rx++;
            }
            check(lx > 0 && rx > 0, id + ": Glanz auf beiden Hoernern " + b);
        }
        // Lamellen: groesster Ausschlag aller Reihen nach aussen -> weiter nichts im Kopf, nichts unter 24.5
        float[] max = new float[HelmetAnimations.SHIKORO_ROWS * 3];
        Arrays.fill(max, HelmetAnimations.LAME_MAX_DEG);
        List<double[]> swung = points(g, samuraiAnim(max), c -> !c.bone().startsWith("glint"));
        helmetBounds(id + " (Lamellen ausgeschwungen)", swung, 7.5);
        // nach aussen: Unterkante hinten weiter hinten, Seiten weiter aussen
        double backRest = maxCoord(points(g, null, c -> c.bone().equals("shikoro3")), 2), backSwung = maxCoord(points(g, samuraiAnim(max), c -> c.bone().equals("shikoro3")), 2);
        double sideRest = maxCoord(points(g, null, c -> c.bone().equals("shikoro3_l")), 0), sideSwung = maxCoord(points(g, samuraiAnim(max), c -> c.bone().equals("shikoro3_l")), 0);
        check(backSwung > backRest + 0.2 && sideSwung > sideRest + 0.2, id + ": Lamellen schwingen nach aussen (" + backRest + "->" + backSwung + ", " + sideRest + "->" + sideSwung + ")");
        // im Lauf (echte Werte): nie nach innen, Grenzen eingehalten
        for (int i = 0; i < 120; i++) {
            float t = i * 0.137f, ph = i * 0.41f;
            float[] sw = new float[HelmetAnimations.SHIKORO_ROWS * 3];
            for (int r = 0; r < HelmetAnimations.SHIKORO_ROWS; r++) for (int p = 0; p < 3; p++) sw[r * 3 + p] = HelmetAnimations.lameSwing(r, p, ph, 1.0f, t);
            if (i % 10 == 0) helmetBounds(id + " (laufen " + i + ")", points(g, samuraiAnim(sw), c -> c.bone().startsWith("shikoro")), 7.5);
        }
    }

    private static double maxCoord(List<double[]> pts, int axis) {
        double m = -1e9;
        for (double[] p : pts) m = Math.max(m, p[axis]);
        return m;
    }

    private static Map<String, double[]> knightAnim(float visorPitch, float[] plume) {
        Map<String, double[]> a = new HashMap<>();
        a.put("visor", new double[]{visorPitch, 0, 0});
        if (plume != null) for (int i = 0; i < HelmetAnimations.PLUME_SEGMENTS; i++) a.put("plume" + i, new double[]{-plume[i * 2], 0, plume[i * 2 + 1]});
        return a;
    }

    private static void knightHelmet() {
        String id = HelmetAnimations.KNIGHT_HELMET;
        Geo g = load(id);
        for (Bone b : g.bones().values()) check(b.parent() == null || g.bones().containsKey(b.parent()), id + ": Elternknochen existiert: " + b.name());
        Bone visor = g.bones().get("visor");
        check(visor != null && id.equals(visor.parent()), id + ": Visier als eigener Knochen");
        check(Math.abs(visor.pivot()[0]) < 1e-6 && visor.pivot()[1] > 29.0 && visor.pivot()[1] < 31.5 && visor.pivot()[2] < 0, id + ": Visier-Pivot = Scharnier an den Schlaefen " + Arrays.toString(visor.pivot()));
        for (int i = 0; i < HelmetAnimations.PLUME_SEGMENTS; i++) {
            Bone b = g.bones().get("plume" + i);
            check(b != null && (i == 0 ? id : "plume" + (i - 1)).equals(b.parent()), id + ": Federsegment plume" + i + " (Kette)");
            check(!points(g, null, c -> b.name().equals(c.bone())).isEmpty(), id + ": Federsegment hat Geometrie plume" + i);
        }
        java.util.function.Predicate<Cube> skull = c -> c.bone().equals("base");
        java.util.function.Predicate<Cube> visorCubes = c -> c.bone().equals("visor");
        // Visier geschlossen (Ruhelage): Augen und Mund bedeckt; offen: Augen frei, Kinnreff bleibt vor dem Mund
        Map<String, double[]> closed = knightAnim(HelmetAnimations.visorPitch(1.0f), null);
        Map<String, double[]> open = knightAnim(HelmetAnimations.visorPitch(0.0f), null);
        for (double y = 27.2; y < 29.0; y += 0.3) for (double ax = 1.15; ax < 3.0; ax += 0.3) for (int s = -1; s <= 1; s += 2) {
            check(rayHitsFront(g, closed, c -> true, s * ax, y), id + ": geschlossen deckt das Visier die Augen ab (" + s * ax + ", " + y + ")");
            check(!rayHitsFront(g, open, c -> true, s * ax, y), id + ": offen sind die Augen frei (" + s * ax + ", " + y + ")");
        }
        for (double y = 25.1; y < 26.6; y += 0.3) for (double x = -1.8; x <= 1.8; x += 0.6) {
            check(rayHitsFront(g, closed, c -> true, x, y) && rayHitsFront(g, open, skull, x, y), id + ": Kinnreff/Visier vor dem Mund (" + x + ", " + y + ")");
        }
        // Visier-Winkelbereich: 0 (zu) .. -VISOR_OPEN_DEG (offen), monoton
        check(HelmetAnimations.visorPitch(1.0f) == 0.0f && Math.abs(HelmetAnimations.visorPitch(0.0f) + HelmetAnimations.VISOR_OPEN_DEG) < 1e-4f, "Visier: Endlagen");
        check(HelmetAnimations.VISOR_OPEN_DEG >= 45.0f && HelmetAnimations.VISOR_OPEN_DEG <= 80.0f, "Visier: Klappwinkel plausibel");
        float lastP = -1e9f;
        for (int i = 0; i <= 100; i++) {
            float p = HelmetAnimations.visorPitch(i / 100.0f);
            check(p >= -HelmetAnimations.VISOR_OPEN_DEG - 1e-4f && p <= 1e-4f, "Visier im Winkelbereich (" + p + ")");
            check(p >= lastP - 1e-5f, "Visier monoton");
            lastP = p;
        }
        // Kinematik: ueber den ganzen Klappweg liegt kein Visierpunkt in der Glocke/im Kinnreff oder im Kopf
        List<Cube> skullList = new ArrayList<>();
        for (Cube c : g.cubes()) if (skull.test(c)) skullList.add(c);
        List<double[]> vRest = new ArrayList<>();
        for (Cube c : g.cubes()) if (visorCubes.test(c)) vRest.addAll(dense(c, 5));
        for (int a = 0; a <= (int) HelmetAnimations.VISOR_OPEN_DEG; a++) {
            Map<String, double[]> an = knightAnim(-a, null);
            for (double[] p0 : vRest) {
                double[] p = posed(g, "visor", p0, an);
                check(depthIn(p, LAYER_HALF, LAYER_TOP) <= 1e-6 || p[1] < 23.5, id + ": Visier nie im Kopf (Winkel " + a + ", " + Arrays.toString(p) + ")");
                for (Cube c : skullList) {
                    if (inCube(c, p, 0.0)) throw new AssertionError(id + ": Visier schneidet die Glocke (Winkel " + a + ", " + Arrays.toString(p) + ", Wuerfel " + Arrays.toString(c.origin()) + ")");
                }
            }
            checks++;
        }
        // Grenzen geschlossen und offen; Glocke eng am Kopf
        List<double[]> restAll = points(g, closed, c -> true), openAll = points(g, open, c -> true);
        helmetBounds(id + " (zu)", restAll, 7.5);
        helmetBounds(id + " (offen)", openAll, 7.5);
        double sum = 0;
        for (double[] p : restAll) sum += p[0];
        check(Math.abs(sum) < 1e-2 * restAll.size(), id + ": links/rechts symmetrisch (" + sum / restAll.size() + ")");
        double wall = 1e9, dome = 1e9;
        for (Cube c : skullList) for (double[] p : dense(c, 8)) {
            double r = Math.max(Math.abs(p[0]), Math.abs(p[2]));
            if (p[1] > 30.5 && p[1] < 32.0 && p[2] > -3.0) wall = Math.min(wall, r);
            if (r <= LAYER_HALF && p[1] > 30.0) dome = Math.min(dome, p[1]);
        }
        check(wall >= LAYER_HALF && wall <= LAYER_HALF + 0.25, id + ": Glocke eng am Kopf (" + wall + ")");
        check(dome >= LAYER_TOP && dome <= LAYER_TOP + 0.5, id + ": liegt ohne Luecke auf dem Kopf (" + dome + ")");
        List<double[]> skullDense = new ArrayList<>();
        for (Cube c : skullList) skullDense.addAll(dense(c, 6));
        coversLayerCorners(id, skullDense, 30.5, 32.4);
        // Federbusch oben hinten, auch voll ausgelenkt ueber dem Helm und nicht im Kopf
        double plumeTop = -1e9;
        for (double[] p : points(g, null, c -> c.bone().startsWith("plume"))) plumeTop = Math.max(plumeTop, p[1]);
        check(plumeTop > 36.0 && plumeTop <= 40.0, id + ": Federbusch ragt nach oben (" + plumeTop + ")");
        float M = HelmetAnimations.PLUME_MAX_DEG;
        for (float[] pl : new float[][]{{M, 3, M, 3, M, 3, M, 3}, {-M, -3, -M, -3, -M, -3, -M, -3}, {M, -3, -M, 3, M, -3, -M, 3}, {0, 0, 0, 0, 0, 0, 0, 0}}) {
            List<double[]> pp = points(g, knightAnim(0.0f, pl), c -> c.bone().startsWith("plume"));
            for (double[] p : pp) {
                check(Math.abs(p[2]) <= 8.0 && p[1] <= 40.0 && Math.abs(p[0]) <= 3.0, id + ": Federbusch in den Grenzen " + Arrays.toString(p) + " " + Arrays.toString(pl));
                check(p[1] > 33.5, id + ": Federbusch ueber dem Helm " + Arrays.toString(p));
                for (Cube c : skullList) if (!c.bone().startsWith("plume") && inCube(c, p, 0.05) && p[1] < 34.6) {
                    throw new AssertionError(id + ": Feder in der Glocke " + Arrays.toString(p));
                }
            }
        }
    }

    // ---------------- Animationen der Helme ----------------

    private static void helmetAnimations() {
        // Lamellen: Bereich, nur nach aussen, stetig, deterministisch, laufen > stehen, Reihen versetzt
        float idleMax = 0, walkMax = 0;
        for (int r = 0; r < HelmetAnimations.SHIKORO_ROWS; r++) for (int part = 0; part < 3; part++) {
            float prev = HelmetAnimations.lameSwing(r, part, 0, 1, 0);
            for (int ms = 1; ms <= 20_000; ms++) {
                float t = ms / 1000.0f, ph = t * 9.0f;
                float s = HelmetAnimations.lameSwing(r, part, ph, 1.0f, t);
                check(s >= 0.0f && s <= HelmetAnimations.LAME_MAX_DEG, "Lamelle im Bereich (" + s + ")");
                check(Math.abs(s - prev) < 0.06f, "Lamellen stetig (Reihe " + r + ", t=" + t + ")");
                check(s == HelmetAnimations.lameSwing(r, part, ph, 1.0f, t), "Lamellen deterministisch");
                walkMax = Math.max(walkMax, s);
                idleMax = Math.max(idleMax, HelmetAnimations.lameSwing(r, part, 0.0f, 0.0f, t));
                prev = s;
            }
        }
        check(idleMax < 0.25f && walkMax > 1.5f, "Lamellen schwingen beim Laufen (" + idleMax + " / " + walkMax + ")");
        // versetzt: obere und untere Reihe erreichen ihr Maximum zu verschiedenen Zeiten
        int peak0 = -1, peak3 = -1;
        float m0 = -1, m3 = -1;
        for (int i = 0; i < 500; i++) {
            float ph = i * 0.01f * (float) Math.PI / 0.6662f;
            float a = HelmetAnimations.lameSwing(0, 0, ph, 1.0f, 0.0f), b = HelmetAnimations.lameSwing(3, 0, ph, 1.0f, 0.0f);
            if (a > m0) { m0 = a; peak0 = i; }
            if (b > m3) { m3 = b; peak3 = i; }
        }
        check(peak0 != peak3, "Lamellen-Reihen leicht versetzt (" + peak0 + " vs " + peak3 + ")");
        // Goldglanz: dezent, wandert vom Ansatz zur Spitze, stetig
        float gmin = 9, gmax = -9;
        int firstBase = -1, firstTip = -1;
        float[] prevG = new float[HelmetAnimations.HORN_GLINTS];
        for (int ms = 0; ms <= 12_000; ms++) {
            float t = 30.0f + ms / 1000.0f;
            for (int i = 0; i < HelmetAnimations.HORN_GLINTS; i++) {
                float k = HelmetAnimations.hornGlint(i, t, 42L);
                check(k >= 0 && k <= 1, "Hornglanz 0..1");
                if (ms > 0) check(Math.abs(k - prevG[i]) < 0.02f, "Hornglanz stetig (i=" + i + ")");
                prevG[i] = k;
                gmin = Math.min(gmin, k);
                gmax = Math.max(gmax, k);
            }
            if (firstBase < 0 && HelmetAnimations.hornGlint(0, t, 42L) > 0.5f) firstBase = ms;
            if (firstBase >= 0 && firstTip < 0 && HelmetAnimations.hornGlint(HelmetAnimations.HORN_GLINTS - 1, t, 42L) > 0.5f) firstTip = ms;
        }
        check(gmin < 0.1f && gmax > 0.5f && gmax <= 0.62f, "Hornglanz dezent mit Schimmer (" + gmin + ".." + gmax + ")");
        check(firstBase >= 0 && firstTip > firstBase, "Glanz wandert vom Ansatz zur Spitze (" + firstBase + " -> " + firstTip + ")");

        // Visier: Kampf -> schnell zu; bleibt ~5 s nach dem letzten Signal unten; dann sanft hoch; ohne Spruenge
        HelmetAnimations.Visor v = new HelmetAnimations.Visor();
        long now = 1_000_000L;
        float dt = 1 / 60f;
        for (int i = 0; i < 60; i++) { v.update(now, false, dt); now += 16; }
        check(v.closed == 0.0f, "Visier ohne Kampf offen");
        v.update(now, true, dt);                       // Angriff/Treffer
        long hit = now;
        float last = v.closed;
        boolean closedFast = false;
        while (now - hit < 12_000L) {
            now += 16;
            v.update(now, false, dt);
            check(Math.abs(v.closed - last) <= dt / HelmetAnimations.VISOR_CLOSE_S + 1e-4f, "Visier ohne Spruenge");
            if (now - hit >= 250L && now - hit <= 300L) closedFast = v.closed >= 0.999f;
            if (now - hit > 300L && now - hit < HelmetAnimations.COMBAT_HOLD_MS - 50L) check(v.closed >= 0.999f, "Visier bleibt im Kampf unten (" + (now - hit) + " ms)");
            if (now - hit > HelmetAnimations.COMBAT_HOLD_MS + 50L && now - hit < HelmetAnimations.COMBAT_HOLD_MS + 400L) {
                check(v.closed < 1.0f && v.closed > 0.0f, "Visier klappt sanft hoch (" + v.closed + ")");
                check(last - v.closed <= dt / HelmetAnimations.VISOR_OPEN_S + 1e-4f, "Hochklappen langsam");
            }
            last = v.closed;
        }
        check(closedFast, "Visier faellt schnell (unter 0.3 s)");
        check(v.closed == 0.0f, "Visier nach dem Kampf wieder offen");
        // neues Signal waehrend des Hochklappens: sofort wieder runter
        HelmetAnimations.Visor w = new HelmetAnimations.Visor();
        w.update(0, true, dt);
        for (int i = 1; i <= 400; i++) w.update(i * 16L, false, dt);
        float mid = w.closed;
        w.update(6_420L, false, dt);
        w.update(6_436L, true, dt);
        check(w.closed >= mid && HelmetAnimations.inCombat(6_436L, w.lastCombatMs), "neuer Angriff haelt das Visier unten");
        // Karten-Zyklus stetig und im Bereich
        float lc = HelmetAnimations.cardVisorClosed(0);
        for (int ms = 1; ms <= 12_000; ms++) {
            float c = HelmetAnimations.cardVisorClosed(ms / 1000.0f);
            check(c >= 0 && c <= 1 && Math.abs(c - lc) <= 0.001f / HelmetAnimations.VISOR_CLOSE_S + 1e-4f, "Karten-Visier stetig");
            lc = c;
        }

        // Federbusch: wippt beim Laufen, biegt sich beim Sprung, klingt ab; stetig, begrenzt
        HelmetAnimations.Plume pl = new HelmetAnimations.Plume();
        double y = 64.0;
        float[] prevPose = HelmetAnimations.plumePose(pl, 0, 3L);
        float walkPeak = 0, idlePeak = 0;
        for (int i = 0; i < 60 * 8; i++) {
            float t = i / 60f;
            boolean walking = t >= 2 && t < 5;
            pl.step(1 / 60f, walking ? 1.0f : 0.0f, t * 9.0f, y);
            float[] pose = HelmetAnimations.plumePose(pl, t, 3L);
            for (int k = 0; k < pose.length; k++) {
                check(Math.abs(pose[k]) <= HelmetAnimations.PLUME_MAX_DEG + 1e-4f, "Feder begrenzt (" + pose[k] + ")");
                check(Math.abs(pose[k] - prevPose[k]) < 2.0f, "Feder stetig (t=" + t + ")");
            }
            if (walking && t > 3) walkPeak = Math.max(walkPeak, Math.abs(pl.a));
            if (t < 2) idlePeak = Math.max(idlePeak, Math.abs(pl.a));
            prevPose = pose;
        }
        check(idlePeak < 0.2f && walkPeak > 0.8f, "Feder wippt beim Laufen (" + idlePeak + " / " + walkPeak + ")");
        check(Math.abs(pl.a) < 0.5f, "Feder kommt zur Ruhe (" + pl.a + ")");
        // Sprung: Abheben biegt die Feder nach hinten (Traegheit), in der Luft hebt sie sich, Landung federt nach
        HelmetAnimations.Plume j = new HelmetAnimations.Plume();
        double vy = 0;
        y = 64;
        for (int i = 0; i < 30; i++) j.step(1 / 60f, 0, 0, y);
        float maxBack = 0, minBack = 0;
        vy = 8.4;
        for (int i = 0; i < 60; i++) {
            vy -= 32 * (1 / 60.0);
            y += vy / 60.0;
            if (y < 64) { y = 64; vy = 0; }
            j.step(1 / 60f, 0, 0, y);
            maxBack = Math.max(maxBack, j.a);
            minBack = Math.min(minBack, j.a);
        }
        check(maxBack > 1.0f && minBack < -0.3f, "Feder reagiert auf Sprung/Landung (" + minBack + ".." + maxBack + ")");
        // riesiger Zeitsprung/Teleport: begrenzt, keine NaN
        j.step(5.0f, 1.0f, 3.0f, 9999.0);
        check(!Float.isNaN(j.a) && Math.abs(j.a) <= HelmetAnimations.PLUME_MAX_DEG, "Feder robust bei Zeitspruengen");
        // Zustandstabelle der Helme
        int before = HelmetAnimations.trackedStates();
        HelmetAnimations.state(101, 1000L);
        HelmetAnimations.State s1 = HelmetAnimations.state(102, 1000L);
        check(s1 == HelmetAnimations.state(102, 2000L) && HelmetAnimations.trackedStates() == before + 2, "Helm-Zustand je Spieler");
        HelmetAnimations.state(103, 40_000L);
        check(HelmetAnimations.trackedStates() == 1, "alte Helm-Zustaende entfernt (" + HelmetAnimations.trackedStates() + ")");
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

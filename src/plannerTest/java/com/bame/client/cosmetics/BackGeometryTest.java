package com.bame.client.cosmetics;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.imageio.ImageIO;

/**
 * Prueft ohne Minecraft die neuen Cosmetics: Gasmaske und Fliegerbrille (FACE), Schmetterlingsfluegel (WINGS) und die
 * Ruecken-Items (BACK: Jetpack, Gitarre, Koecher, Katana, Rucksack).
 * <ul>
 *   <li>Modelle laden fehlerfrei (JSON, Knochen-Eltern), in beiden Namespaces gleich, Textur passt (Groesse, UVs in der
 *       Textur).</li>
 *   <li>Geprueft werden nur sichtbare Punkte: jedes deckende Texel (Alpha &gt; 25) jeder Flaeche, abgebildet wie
 *       {@link BedrockCosmeticModel#render} (Wuerfel-/Knochen-Rotation, {@code blockbenchAxisFix}, Animationen).</li>
 *   <li>FACE: liegt ohne Luecke am Hut-Layer an (4.5 px), nichts im Kopf, deckt Augen (und bei der Maske den Mund) ab.</li>
 *   <li>BACK: nichts im Koerper/Kopf, keine Kollision mit den Armen (Schwung +-50 Grad) und Beinen (ruhiges Gehen, +-20 Grad), auch
 *       beim Mitschwingen, wippenden Pfeilen und pendelnder Laterne; mit Cape liegt das Pack hinter dem Cape.</li>
 *   <li>WINGS: Klappwinkel im erlaubten Bereich, stetig; kein Fluegelpunkt im Koerper/Kopf oder in den Armen beim Laufen
 *       (Schwung = Lauf-Amplitude), mit Cape hinter dem Cape.</li>
 *   <li>Animations-Mathematik von {@link BackCosmetics} (Pack nie in den Koerper, Flamme nur in der Luft, Glanzlicht).</li>
 * </ul>
 */
public class BackGeometryTest {

    private static int checks = 0;
    private static final String RES = "src/main/resources/assets/";

    public static void main(String[] args) throws Exception {
        ids();
        animationMath();
        face("gas_mask", true);
        face("aviator_goggles", false);
        vrHeadset();
        eyePatch();
        vrScreenMath();
        butterfly();
        for (String id : BackCosmetics.BACK_IDS) back(id);
        stateTable();
        System.out.println("BACK GEOMETRY TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    // =================================================================================================================
    // Mini-JSON
    // =================================================================================================================

    private static final class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        static Object parse(String s) {
            Json j = new Json(s);
            Object v = j.value();
            j.ws();
            if (j.i != s.length()) throw new AssertionError("JSON: Rest nach Position " + j.i);
            return v;
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = (String) value();
                    ws();
                    if (s.charAt(i++) != ':') throw new AssertionError("JSON: ':' erwartet bei " + i);
                    m.put(k, value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == '}') return m;
                    if (d != ',') throw new AssertionError("JSON: ',' erwartet bei " + i);
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == ']') return l;
                    if (d != ',') throw new AssertionError("JSON: ',' erwartet bei " + i);
                }
            }
            if (c == '"') {
                StringBuilder b = new StringBuilder();
                i++;
                while (s.charAt(i) != '"') {
                    char e = s.charAt(i++);
                    if (e == '\\') e = s.charAt(i++);
                    b.append(e);
                }
                i++;
                return b.toString();
            }
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(st, i));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    private static List<Object> arr(Object o) { return (List<Object>) o; }

    private static double[] vec(Object o) {
        if (o == null) return null;
        List<Object> l = arr(o);
        double[] v = new double[l.size()];
        for (int k = 0; k < v.length; k++) v[k] = (Double) l.get(k);
        return v;
    }

    // =================================================================================================================
    // Modell + sichtbare Punkte (wie BedrockCosmeticModel mit blockbenchAxisFix)
    // =================================================================================================================

    private record Face(String name, double u, double v, double us, double vs) {}

    private record Cube(double[] origin, double[] size, double inflate, double[] pivot, double[] rotation, List<Face> faces) {}

    private static final class Bone {
        String name, parent;
        double[] pivot, rotation;
        final List<Cube> cubes = new ArrayList<>();
    }

    private static final class Geo {
        final Map<String, Bone> bones = new LinkedHashMap<>();
        int texW, texH;
        BufferedImage tex;
        String id;
    }

    private static final Map<String, Geo> CACHE = new HashMap<>();

    private static byte[] bytes(String rel) {
        try {
            return Files.readAllBytes(Path.of(RES + rel));
        } catch (IOException e) {
            throw new AssertionError("Datei fehlt: " + rel);
        }
    }

    private static Geo load(String dir, String id) {
        String key = dir + "/" + id;
        Geo cached = CACHE.get(key);
        if (cached != null) return cached;
        String geoRel = "models/cosmetics/" + dir + "/" + id + ".geo.json", texRel = "textures/cosmetics/" + dir + "/" + id + ".png";
        // beide Namespaces vorhanden und gleich
        check(Arrays.equals(bytes("bameclient/" + geoRel), bytes("caeserclient/" + geoRel)), "beide Namespaces gleich: " + geoRel);
        check(Arrays.equals(bytes("bameclient/" + texRel), bytes("caeserclient/" + texRel)), "beide Namespaces gleich: " + texRel);
        Map<String, Object> root = obj(Json.parse(new String(bytes("bameclient/" + geoRel), java.nio.charset.StandardCharsets.UTF_8)));
        Map<String, Object> g = obj(arr(root.get("minecraft:geometry")).get(0));
        Map<String, Object> desc = obj(g.get("description"));
        Geo geo = new Geo();
        geo.id = id;
        geo.texW = (int) (double) (Double) desc.get("texture_width");
        geo.texH = (int) (double) (Double) desc.get("texture_height");
        check(("geometry." + id).equals(desc.get("identifier")), id + ": Geometrie-ID");
        try {
            geo.tex = ImageIO.read(new java.io.ByteArrayInputStream(bytes("bameclient/" + texRel)));
        } catch (IOException e) {
            throw new AssertionError("PNG unlesbar: " + texRel);
        }
        check(geo.tex.getWidth() == geo.texW && geo.tex.getHeight() == geo.texH,
                id + ": Texturgroesse " + geo.tex.getWidth() + "x" + geo.tex.getHeight() + " passt zu " + geo.texW + "x" + geo.texH);
        int cubes = 0;
        for (Object bo : arr(g.get("bones"))) {
            Map<String, Object> b = obj(bo);
            Bone bone = new Bone();
            bone.name = (String) b.get("name");
            bone.parent = (String) b.get("parent");
            bone.pivot = b.containsKey("pivot") ? vec(b.get("pivot")) : new double[3];
            bone.rotation = b.containsKey("rotation") ? vec(b.get("rotation")) : new double[3];
            check(!geo.bones.containsKey(bone.name), id + ": Knochen eindeutig: " + bone.name);
            if (b.containsKey("cubes")) for (Object co : arr(b.get("cubes"))) {
                Map<String, Object> c = obj(co);
                List<Face> faces = new ArrayList<>();
                for (Map.Entry<String, Object> fe : obj(c.get("uv")).entrySet()) {
                    Map<String, Object> f = obj(fe.getValue());
                    double[] uv = vec(f.get("uv")), us = vec(f.get("uv_size"));
                    check(uv[0] >= 0 && uv[1] >= 0 && uv[0] + us[0] <= geo.texW + 1e-6 && uv[1] + us[1] <= geo.texH + 1e-6,
                            id + ": UV in der Textur (" + bone.name + "/" + fe.getKey() + ")");
                    check(us[0] > 0 && us[1] > 0, id + ": UV-Groesse positiv");
                    faces.add(new Face(fe.getKey(), uv[0], uv[1], us[0], us[1]));
                }
                double inf = c.containsKey("inflate") ? (Double) c.get("inflate") : 0.0;
                bone.cubes.add(new Cube(vec(c.get("origin")), vec(c.get("size")), inf, vec(c.get("pivot")), vec(c.get("rotation")), faces));
                cubes++;
            }
            geo.bones.put(bone.name, bone);
        }
        for (Bone b : geo.bones.values()) {
            check(b.parent == null || geo.bones.containsKey(b.parent), id + ": Elternknochen existiert: " + b.name);
        }
        check(cubes > 0, id + ": hat Wuerfel");
        CACHE.put(key, geo);
        return geo;
    }

    /** Rotation wie BedrockCosmeticModel (AxisFix): Rz(-rz) * Ry(ry) * Rx(-rx) um den Pivot. */
    private static double[] rotate(double[] p, double[] piv, double rx, double ry, double rz) {
        double x = p[0] - piv[0], y = p[1] - piv[1], z = p[2] - piv[2];
        double a = Math.toRadians(-rx);
        double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
        double b = Math.toRadians(ry);
        double x2 = x * Math.cos(b) + z1 * Math.sin(b), z2 = -x * Math.sin(b) + z1 * Math.cos(b);
        double g = Math.toRadians(-rz);
        double x3 = x2 * Math.cos(g) - y1 * Math.sin(g), y3 = x2 * Math.sin(g) + y1 * Math.cos(g);
        return new double[]{x3 + piv[0], y3 + piv[1], z2 + piv[2]};
    }

    /** Animation je Knochen: {rx, ry, rz, tx, ty, tz}. */
    private static double[] posed(Geo g, Bone bone, double[] p, Map<String, double[]> anim) {
        double[] q = p.clone();
        for (Bone b = bone; b != null; b = b.parent != null ? g.bones.get(b.parent) : null) {
            double[] a = anim != null ? anim.get(b.name) : null;
            double rx = b.rotation[0] + (a != null ? a[0] : 0), ry = b.rotation[1] + (a != null ? a[1] : 0), rz = b.rotation[2] + (a != null ? a[2] : 0);
            if (rx != 0 || ry != 0 || rz != 0) q = rotate(q, b.pivot, rx, ry, rz);
            if (a != null && a.length > 3) { q[0] += a[3]; q[1] += a[4]; q[2] += a[5]; }
        }
        return q;
    }

    private record Pt(double[] p, String bone) {}

    /** Lokale Punkte (Knochen-Ruhelage) aller deckenden Texel; stride = jedes n-te Texel. */
    private static final Map<String, List<Pt>> LOCAL = new HashMap<>();

    private static List<Pt> localPoints(Geo g, int stride) {
        String key = g.id + "/" + stride;
        List<Pt> cached = LOCAL.get(key);
        if (cached != null) return cached;
        List<Pt> out = new ArrayList<>();
        for (Bone b : g.bones.values()) for (Cube c : b.cubes) {
            double inf = c.inflate();
            double x0 = c.origin()[0] - inf, y0 = c.origin()[1] - inf, z0 = c.origin()[2] - inf;
            double x1 = c.origin()[0] + c.size()[0] + inf, y1 = c.origin()[1] + c.size()[1] + inf, z1 = c.origin()[2] + c.size()[2] + inf;
            for (Face f : c.faces()) {
                double[][] v = switch (f.name()) {
                    case "west" -> new double[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
                    case "east" -> new double[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
                    case "north" -> new double[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
                    case "south" -> new double[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
                    case "up" -> new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                    case "down" -> new double[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
                    default -> null;
                };
                if (v == null) continue;
                int nu = (int) Math.round(f.us()), nv = (int) Math.round(f.vs());
                for (int j = 0; j < nv; j += stride) for (int i = 0; i < nu; i += stride) {
                    int tx = (int) f.u() + i, ty = (int) f.v() + j;
                    int alpha = (g.tex.getRGB(tx, ty) >>> 24) & 0xFF;
                    if (alpha <= 25) continue;
                    double fu = (i + 0.5) / nu, fv = (j + 0.5) / nv;
                    double[] p = new double[3];
                    for (int k = 0; k < 3; k++) p[k] = v[0][k] + (v[3][k] - v[0][k]) * fu + (v[1][k] - v[0][k]) * fv;
                    if (c.rotation() != null) p = rotate(p, c.pivot(), c.rotation()[0], c.rotation()[1], c.rotation()[2]);
                    out.add(new Pt(p, b.name));
                }
            }
        }
        LOCAL.put(key, out);
        return out;
    }

    private static List<Pt> points(Geo g, int stride, Map<String, double[]> anim, Predicate<String> boneFilter) {
        List<Pt> out = new ArrayList<>();
        for (Pt lp : localPoints(g, stride)) {
            if (!boneFilter.test(lp.bone())) continue;
            out.add(new Pt(posed(g, g.bones.get(lp.bone()), lp.p(), anim), lp.bone()));
        }
        return out;
    }

    private static boolean under(Geo g, String bone, String ancestor) {
        for (Bone b = g.bones.get(bone); b != null; b = b.parent != null ? g.bones.get(b.parent) : null) {
            if (b.name.equals(ancestor)) return true;
        }
        return false;
    }

    /** Eindringtiefe in einen achsparallelen Quader (0 = ausserhalb). */
    private static double depthBox(double[] p, double x0, double x1, double y0, double y1, double z0, double z1) {
        double d = Math.min(Math.min(p[0] - x0, x1 - p[0]), Math.min(Math.min(p[1] - y0, y1 - p[1]), Math.min(p[2] - z0, z1 - p[2])));
        return Math.max(0.0, d);
    }

    // Spieler (Bedrock-Pixel): Koerper mit Jacket, Kopf (innen und mit Hut-Layer)
    private static double depthBody(double[] p) { return depthBox(p, -4.25, 4.25, 11.75, 24.25, -2.25, 2.25); }
    private static double depthHeadInner(double[] p) { return depthBox(p, -4, 4, 24, 32, -4, 4); }
    private static double depthHeadLayer(double[] p) { return depthBox(p, -4.5, 4.5, 23.5, 32.5, -4.5, 4.5); }

    /** Arm (mit Aermel) um den Schulter-Pivot geschwungen (Pitch in Grad, positiv = Arm nach vorn oben wie Vanilla-Pitch negativ). */
    private static double depthArm(double[] p, int side, double swingDeg) {
        // Punkt in den Arm-Raum zuruecktransformieren: Arm-Pivot (side*5, 22, 0), Drehung um x
        double a = Math.toRadians(-swingDeg);
        double y = p[1] - 22, z = p[2];
        double yl = y * Math.cos(a) - z * Math.sin(a), zl = y * Math.sin(a) + z * Math.cos(a);
        double[] q = {p[0], yl + 22, zl};
        double xlo = side > 0 ? 3.75 : -8.25, xhi = side > 0 ? 8.25 : -3.75;
        return depthBox(q, xlo, xhi, 11.75, 24.25, -2.25, 2.25);
    }

    private static double depthLeg(double[] p, int side, double swingDeg) {
        double a = Math.toRadians(-swingDeg);
        double y = p[1] - 12, z = p[2];
        double yl = y * Math.cos(a) - z * Math.sin(a), zl = y * Math.sin(a) + z * Math.cos(a);
        double[] q = {p[0], yl + 12, zl};
        double xlo = side > 0 ? -0.35 : -4.15, xhi = side > 0 ? 4.15 : 0.35;
        return depthBox(q, xlo, xhi, -0.25, 12.25, -2.25, 2.25);
    }

    /** Aussenseite des ruhig haengenden Capes (6 Grad) bei Hoehe y (Bedrock-Pixel). */
    private static double capeOuterZ(double y) {
        double yj = 24.0 - y;
        return 2.0 + yj * Math.sin(Math.toRadians(6)) + Math.cos(Math.toRadians(6));
    }

    // =================================================================================================================
    // IDs
    // =================================================================================================================

    private static void ids() {
        check(BackCosmetics.BACK_IDS.equals(List.of("jetpack", "guitar", "quiver", "katana", "backpack")), "BACK-IDs");
        for (String id : BackCosmetics.BACK_IDS) {
            check(BackCosmetics.isBackItem(id) && BackCosmetics.isBackItem(id.toUpperCase()), "isBackItem " + id);
            check(BackCosmetics.sanitizeBack(" " + id.toUpperCase() + " ").equals(id), "sanitizeBack " + id);
        }
        check(!BackCosmetics.isBackItem("angel") && !BackCosmetics.isBackItem(null) && !BackCosmetics.isBackItem("none"), "keine Fremd-IDs");
        check(BackCosmetics.sanitizeBack("butterfly").equals("none") && BackCosmetics.sanitizeBack(null).equals("none"), "unbekannt = none");
        // Shop-IDs: Mod-ID in GROSS; Fluegel nicht mit dem Butterfly-Emote verwechselbar
        check("BUTTERFLY_WINGS".equals(BackCosmetics.BUTTERFLY_WINGS.toUpperCase()), "Shop-ID BUTTERFLY_WINGS");
        check(!"BUTTERFLY".equals(BackCosmetics.BUTTERFLY_WINGS.toUpperCase()), "nicht das Butterfly-Emote");
        check("GAS_MASK".equals(BackCosmetics.GAS_MASK.toUpperCase()) && "AVIATOR_GOGGLES".equals(BackCosmetics.AVIATOR_GOGGLES.toUpperCase()), "FACE-IDs");
    }

    // =================================================================================================================
    // FACE
    // =================================================================================================================

    private static void face(String id, boolean mask) {
        Geo g = load("face", id);
        check(g.bones.containsKey(id) && g.bones.containsKey("base") && g.bones.containsKey("glint"), id + ": Knochen");
        List<Pt> pts = points(g, 1, null, b -> true);
        double sumX = 0, minFrontZ = 1e9, maxBack = -1e9;
        boolean[] eyeCells = new boolean[64 * 64];
        for (Pt pt : pts) {
            double[] p = pt.p();
            sumX += p[0];
            check(depthHeadInner(p) <= 1e-6, id + ": nichts im Kopf " + Arrays.toString(p));
            check(depthHeadLayer(p) <= 0.03, id + ": nichts im Hut-Layer " + Arrays.toString(p));
            check(Math.abs(p[0]) <= 6.0 && p[1] >= 22.0 && p[1] <= 33.0 && p[2] >= -10.0 && p[2] <= 5.5, id + ": kompakt " + Arrays.toString(p));
            // vorderste Innenflaeche ueber dem Gesicht
            if (p[2] < 0 && Math.abs(p[0]) < 3.5 && p[1] > 25 && p[1] < 31) minFrontZ = Math.min(minFrontZ, -p[2]);
            if (p[2] < -4.4) {
                int cx = (int) Math.floor((p[0] + 8) * 4), cy = (int) Math.floor((p[1] - 20) * 4);
                if (cx >= 0 && cx < 64 && cy >= 0 && cy < 64) eyeCells[cy * 64 + cx] = true;
            }
            maxBack = Math.max(maxBack, p[2]);
        }
        check(Math.abs(sumX) < 0.02 * pts.size(), id + ": links/rechts symmetrisch (" + sumX / pts.size() + ")");
        check(minFrontZ >= 4.5 && minFrontZ <= 4.6, id + ": liegt ohne Luecke am Hut-Layer an (" + minFrontZ + ")");
        check(maxBack > 4.5, id + ": Band hinten um den Kopf (" + maxBack + ")");
        // Augen abgedeckt (x +-1..3, y 27..29); Maske zusaetzlich Mund (x -2..2, y 25..26)
        for (double y = 27.1; y < 29.0; y += 0.25) for (double ax = 1.1; ax < 3.0; ax += 0.25) for (int s = -1; s <= 1; s += 2) {
            int cx = (int) Math.floor((s * ax + 8) * 4), cy = (int) Math.floor((y - 20) * 4);
            check(eyeCells[cy * 64 + cx], id + ": deckt das Auge ab (" + s * ax + ", " + y + ")");
        }
        if (mask) {
            for (double y = 25.1; y < 26.0; y += 0.25) for (double x = -1.9; x < 2.0; x += 0.25) {
                int cx = (int) Math.floor((x + 8) * 4), cy = (int) Math.floor((y - 20) * 4);
                check(eyeCells[cy * 64 + cx], id + ": deckt den Mund ab (" + x + ", " + y + ")");
            }
        }
        // Seitenbaender liegen an den Kopfseiten an
        double side = 1e9;
        for (Pt pt : pts) if (Math.abs(pt.p()[2]) < 2.0 && pt.p()[1] > 26 && pt.p()[1] < 30) side = Math.min(side, Math.abs(pt.p()[0]));
        check(side >= 4.5 && side <= 4.6, id + ": Band liegt seitlich am Hut-Layer an (" + side + ")");
        // Glanzlicht liegt vor den Glaesern, mittig ueber den Augen
        double gx = 0, gy = 0, gzMax = -1e9;
        int n = 0;
        for (Pt pt : points(g, 1, null, b -> b.equals("glint"))) {
            if (pt.p()[0] > 0) { gx += pt.p()[0]; gy += pt.p()[1]; n++; }
            gzMax = Math.max(gzMax, pt.p()[2]);
        }
        check(n > 0, id + ": Glanzlicht sichtbar");
        check(Math.abs(gx / n - 2.05) < 0.5 && Math.abs(gy / n - 28.15) < 0.6, id + ": Glanzlicht ueber dem Auge (" + gx / n + ", " + gy / n + ")");
        check(gzMax < -4.6, id + ": Glanzlicht vor dem Gesicht");
    }

    // =================================================================================================================
    // FACE: VR-Headset und Augenklappe
    // =================================================================================================================

    /** Sichtbare Punkte vor dem Gesicht (z < -4.4) als 0.25-px-Raster (x -8..8, y 20..36). */
    private static boolean[] frontCells(List<Pt> pts) {
        boolean[] cells = new boolean[64 * 64];
        for (Pt pt : pts) {
            double[] p = pt.p();
            if (p[2] >= -4.4) continue;
            int cx = (int) Math.floor((p[0] + 8) * 4), cy = (int) Math.floor((p[1] - 20) * 4);
            if (cx >= 0 && cx < 64 && cy >= 0 && cy < 64) cells[cy * 64 + cx] = true;
        }
        return cells;
    }

    private static boolean cellAt(boolean[] cells, double x, double y) {
        int cx = (int) Math.floor((x + 8) * 4), cy = (int) Math.floor((y - 20) * 4);
        return cells[cy * 64 + cx];
    }

    /** Gemeinsam: nichts im Kopf/Hut-Layer, kompakt, liegt vorn ohne Luecke am Hut-Layer an, Band hinten und seitlich. */
    private static void tightFace(String id, List<Pt> pts, double frontXMin, double frontXMax) {
        double minFrontZ = 1e9, maxBack = -1e9;
        for (Pt pt : pts) {
            double[] p = pt.p();
            check(depthHeadInner(p) <= 1e-6, id + ": nichts im Kopf " + Arrays.toString(p));
            check(depthHeadLayer(p) <= 0.03, id + ": nichts im Hut-Layer " + Arrays.toString(p));
            check(Math.abs(p[0]) <= 6.0 && p[1] >= 22.0 && p[1] <= 33.0 && p[2] >= -10.0 && p[2] <= 5.6, id + ": kompakt " + Arrays.toString(p));
            if (p[2] < 0 && p[0] > frontXMin && p[0] < frontXMax && p[1] > 25 && p[1] < 31) minFrontZ = Math.min(minFrontZ, -p[2]);
            maxBack = Math.max(maxBack, p[2]);
        }
        check(minFrontZ >= 4.5 && minFrontZ <= 4.6, id + ": liegt ohne Luecke am Hut-Layer an (" + minFrontZ + ")");
        check(maxBack > 4.5, id + ": Band hinten um den Kopf (" + maxBack + ")");
        for (int s = -1; s <= 1; s += 2) {
            double side = 1e9;
            for (Pt pt : pts) if (pt.p()[0] * s > 0 && Math.abs(pt.p()[2]) < 2.0 && pt.p()[1] > 26 && pt.p()[1] < 31.5) side = Math.min(side, Math.abs(pt.p()[0]));
            check(side >= 4.5 && side <= 4.6, id + ": Band liegt seitlich am Hut-Layer an (" + (s > 0 ? "links" : "rechts") + ", " + side + ")");
        }
    }

    private static void vrHeadset() {
        String id = HelmetAnimations.VR_HEADSET;
        Geo g = load("face", id);
        for (String b : new String[]{id, "base", "screen", "led"}) check(g.bones.containsKey(b), id + ": Knochen " + b);
        List<Pt> pts = points(g, 1, null, b -> true);
        tightFace(id, pts, -3.5, 3.5);
        double sumX = 0;
        for (Pt pt : pts) sumX += pt.p()[0];
        check(Math.abs(sumX) < 0.02 * pts.size(), id + ": links/rechts symmetrisch (" + sumX / pts.size() + ")");
        // beide Augen abgedeckt, Mund frei
        boolean[] cells = frontCells(pts);
        for (double y = 27.1; y < 29.0; y += 0.25) for (double ax = 1.1; ax < 3.0; ax += 0.25) for (int s = -1; s <= 1; s += 2) {
            check(cellAt(cells, s * ax, y), id + ": deckt das Auge ab (" + s * ax + ", " + y + ")");
        }
        for (double y = 24.2; y < 25.6; y += 0.25) for (double x = -1.9; x < 2.0; x += 0.25) check(!cellAt(cells, x, y), id + ": Mund frei (" + x + ", " + y + ")");
        // Bildschirmlicht: vor dem Glas, ueber beiden Augen; LED oben auf dem Gehaeuse
        double baseFront = 1e9, scrMaxZ = -1e9;
        for (Pt pt : points(g, 1, null, b -> b.equals("base"))) if (Math.abs(pt.p()[0]) < 2.5 && Math.abs(pt.p()[1] - 28.2) < 1.0) baseFront = Math.min(baseFront, pt.p()[2]);
        int left = 0, right = 0;
        for (Pt pt : points(g, 1, null, b -> b.equals("screen"))) {
            double[] p = pt.p();
            if (Math.abs(p[0]) < 2.6) scrMaxZ = Math.max(scrMaxZ, p[2]);
            if (Math.abs(p[1] - 28.2) < 1.2) { if (p[0] > 1.0 && p[0] < 3.2) left++; if (p[0] < -1.0 && p[0] > -3.2) right++; }
        }
        check(scrMaxZ < baseFront, id + ": Bildschirmlicht vor dem Glas (" + scrMaxZ + " < " + baseFront + ")");
        check(left > 50 && right > 50, id + ": leuchtende Linsen ueber beiden Augen (" + left + "/" + right + ")");
        List<Pt> led = points(g, 1, null, b -> b.equals("led"));
        check(!led.isEmpty(), id + ": Status-LED sichtbar");
        for (Pt pt : led) check(pt.p()[1] > 30.8 && pt.p()[2] < -6.0, id + ": LED oben vorn auf dem Gehaeuse " + Arrays.toString(pt.p()));
    }

    private static void eyePatch() {
        String id = HelmetAnimations.EYE_PATCH;
        Geo g = load("face", id);
        check(g.bones.containsKey(id) && g.bones.containsKey("base"), id + ": Knochen");
        List<Pt> pts = points(g, 1, null, b -> true);
        tightFace(id, pts, 0.2, 3.9);
        boolean[] cells = frontCells(pts);
        // linkes Auge (Spieler links = +x) abgedeckt, rechtes frei
        for (double y = 27.1; y < 29.0; y += 0.25) for (double ax = 1.1; ax < 3.0; ax += 0.25) {
            check(cellAt(cells, ax, y), id + ": deckt das linke Auge ab (" + ax + ", " + y + ")");
            check(!cellAt(cells, -ax, y), id + ": rechtes Auge frei (" + -ax + ", " + y + ")");
        }
        // Mund frei; Band schraeg: auf der Stirn rechts hoeher als links, hinten schraeg
        for (double y = 24.2; y < 26.5; y += 0.25) for (double x = -1.9; x < 2.0; x += 0.25) check(!cellAt(cells, x, y), id + ": Mund frei (" + x + ", " + y + ")");
        double rightTemple = -1e9, leftSide = 1e9, backL = 0, backR = 0;
        int nl = 0, nr = 0;
        for (Pt pt : pts) {
            double[] p = pt.p();
            if (p[0] < -4.0 && p[2] < -4.0) rightTemple = Math.max(rightTemple, p[1]);
            if (p[0] > 4.5 && Math.abs(p[2]) < 1.0) leftSide = Math.min(leftSide, p[1]);
            if (p[2] > 4.5 && Math.abs(p[0]) > 2.0) { if (p[0] > 0) { backL += p[1]; nl++; } else { backR += p[1]; nr++; } }
        }
        check(rightTemple > 30.5 && leftSide < 29.0, id + ": Band schraeg ueber die Stirn (" + rightTemple + " / " + leftSide + ")");
        check(nl > 0 && nr > 0 && backR / nr > backL / nl + 0.4, id + ": Band hinten schraeg (" + backR / Math.max(1, nr) + " / " + backL / Math.max(1, nl) + ")");
        // Klappe: Lederschwarz, gewoelbt (Mitte weiter vorn als der Rand)
        double centerZ = 1e9, rimSum = 0;
        int rimN = 0;
        for (Pt pt : pts) {
            double[] p = pt.p();
            if (p[2] > -4.5) continue;
            double r = Math.hypot((p[0] - 2.05) / 1.78, (p[1] - 28.15) / 1.52);
            if (r < 0.3) centerZ = Math.min(centerZ, p[2]);
            if (r > 0.85 && r < 1.0) { rimSum += p[2]; rimN++; }   // Mittel: Oesen am Rand stoeren nicht
        }
        check(rimN > 0 && centerZ < rimSum / rimN - 0.12, id + ": Klappe gewoelbt (" + centerZ + " vs " + rimSum / Math.max(1, rimN) + ")");
    }

    private static void vrScreenMath() {
        check(HelmetAnimations.isNewFace("VR_HEADSET") && HelmetAnimations.isNewFace("eye_patch") && !HelmetAnimations.isNewFace("gas_mask")
                && !HelmetAnimations.isNewFace(null), "FACE-IDs (neu)");
        check("VR_HEADSET".equals(HelmetAnimations.VR_HEADSET.toUpperCase()) && "EYE_PATCH".equals(HelmetAnimations.EYE_PATCH.toUpperCase()), "Shop-IDs = Mod-IDs in GROSS");
        int prev = HelmetAnimations.screenArgb(0, 5L);
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        float fmin = 9, fmax = -9;
        for (int ms = 1; ms <= 60_000; ms += 5) {
            float t = ms / 1000.0f;
            int c = HelmetAnimations.screenArgb(t, 5L);
            check(c == HelmetAnimations.screenArgb(t, 5L), "Bildschirm deterministisch");
            float f = HelmetAnimations.screenFlicker(t, 5L);
            fmin = Math.min(fmin, f);
            fmax = Math.max(fmax, f);
            int a = c >>> 24;
            check(a >= 160 && a <= 255, "Bildschirm-Deckkraft dezent flackernd (" + a + ")");
            for (int sh = 0; sh <= 16; sh += 8) {
                check(Math.abs(((c >> sh) & 0xFF) - ((prev >> sh) & 0xFF)) <= 24, "Bildschirmfarbe ohne Spruenge (t=" + t + ")");
            }
            colors.add(HelmetAnimations.screenRgb((float) Math.floor(t / HelmetAnimations.SCENE_S) * HelmetAnimations.SCENE_S + 0.1f, 5L));
            prev = c;
        }
        check(fmin >= 0.7f && fmin < 0.85f && fmax > 0.9f && fmax <= 1.0f, "Flackern dezent (" + fmin + ".." + fmax + ")");
        check(colors.size() >= 3, "Farbe wechselt wie ein Bildschirm (" + colors.size() + " Farben)");
        int l0 = HelmetAnimations.ledArgb(0) >>> 24, l1 = HelmetAnimations.ledArgb(0.75f) >>> 24;
        check(l0 >= 150 && l1 >= 150 && l0 != l1, "LED atmet");
    }

    // =================================================================================================================
    // WINGS: Schmetterling
    // =================================================================================================================

    private static Map<String, double[]> wingAnim(float[] pose, double capeOffset) {
        Map<String, double[]> a = new HashMap<>();
        a.put("wing_l", new double[]{0, -pose[0], pose[2], 0, 0, 0});
        a.put("hind_l", new double[]{0, -pose[1], pose[2], 0, 0, 0});
        a.put("wing_r", new double[]{0, pose[0], -pose[2], 0, 0, 0});
        a.put("hind_r", new double[]{0, pose[1], -pose[2], 0, 0, 0});
        if (capeOffset != 0) a.put("butterfly", new double[]{0, 0, 0, 0, 0, capeOffset});
        return a;
    }

    private static void butterfly() {
        Geo g = load("wings", "butterfly_wings");
        for (String b : new String[]{"butterfly", "thorax", "wing_l", "hind_l", "wing_r", "hind_r"}) check(g.bones.containsKey(b), "Butterfly: Knochen " + b);
        for (String w : new String[]{"wing_l", "hind_l", "wing_r", "hind_r"}) {
            check("butterfly".equals(g.bones.get(w).parent), "Fluegel haengt an der Wurzel: " + w);
            double[] pv = g.bones.get(w).pivot;
            check(Math.abs(Math.abs(pv[0]) - 0.75) < 1e-6 && pv[2] > 3.0 && pv[2] < 3.6, "Gelenk am Ruecken: " + w + " " + Arrays.toString(pv));
        }
        // Ausgebreitet: grosse Vorderfluegel, kleinere Hinterfluegel, links/rechts symmetrisch
        List<Pt> rest = points(g, 2, wingAnim(new float[]{8, 8, 0}, 0), b -> true);
        double spanF = 0, spanH = 0, sumX = 0, topF = -1e9, lowH = 1e9;
        for (Pt pt : rest) {
            double[] p = pt.p();
            sumX += p[0];
            if (pt.bone().startsWith("wing")) { spanF = Math.max(spanF, Math.abs(p[0])); topF = Math.max(topF, p[1]); }
            if (pt.bone().startsWith("hind")) { spanH = Math.max(spanH, Math.abs(p[0])); lowH = Math.min(lowH, p[1]); }
        }
        check(Math.abs(sumX) < 0.02 * rest.size(), "Butterfly symmetrisch");
        check(spanF > 10.5 && spanF < 13.0 && spanH > 7.5 && spanH < spanF - 1.5, "Vorderfluegel gross, Hinterfluegel kleiner (" + spanF + ", " + spanH + ")");
        check(topF > 27.0 && topF < 30.5 && lowH > 11.5 && lowH < 14.0, "Fluegel an den Schulterblaettern (oben " + topF + ", unten " + lowH + ")");
        // Muster: orange Flaechen, schwarze Adern/Rand, weisse Punkte
        int orange = 0, black = 0, white = 0;
        for (int y = 0; y < g.texH; y++) for (int x = 0; x < g.texW; x++) {
            int c = g.tex.getRGB(x, y);
            if ((c >>> 24) < 26) continue;
            int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
            if (r > 180 && gg > 60 && gg < 170 && b < 70) orange++;
            else if (r < 50 && gg < 50 && b < 50) black++;
            else if (r > 225 && gg > 225 && b > 210) white++;
        }
        check(orange > 4000 && black > 3000 && white > 150, "Monarch-Muster (orange " + orange + ", schwarz " + black + ", weiss " + white + ")");

        // Animation: Bereich, Stetigkeit, Determinismus
        float[] prev = null;
        float minIdle = 999, maxIdle = -999;
        for (int i = 0; i <= 60 * 120; i++) {
            float t = 50.0f + i / 60.0f;
            float[] p = BackCosmetics.butterflyPose(t, 77L, 0, 0, 0);
            check(p[0] >= BackCosmetics.FOLD_MIN && p[0] <= BackCosmetics.FOLD_MAX && p[1] >= BackCosmetics.FOLD_MIN && p[1] <= BackCosmetics.FOLD_MAX, "Klappwinkel im Bereich");
            check(p[2] >= BackCosmetics.ROLL_MIN && p[2] <= BackCosmetics.ROLL_MAX, "Neigung im Bereich");
            if (prev != null) check(Math.abs(p[0] - prev[0]) < 2.0f && Math.abs(p[1] - prev[1]) < 2.0f, "Fluegel stetig (t=" + t + ")");
            check(Arrays.equals(p, BackCosmetics.butterflyPose(t, 77L, 0, 0, 0)), "deterministisch");
            minIdle = Math.min(minIdle, p[0]);
            maxIdle = Math.max(maxIdle, p[0]);
            prev = p;
        }
        check(minIdle < 10 && maxIdle > 55, "im Stand auf- und zuklappen (" + minIdle + ".." + maxIdle + ")");
        // gelegentliche Pausen: es gibt Zyklen, die offen bleiben
        int pauses = 0, cycles = 0;
        for (int k = 20; k < 120; k++) {
            float mx = 0;
            for (int j = 0; j < 36; j++) mx = Math.max(mx, BackCosmetics.idleFold((k + j / 36.0f) * BackCosmetics.IDLE_PERIOD, 77L));
            cycles++;
            if (mx < 15) pauses++;
        }
        check(pauses > cycles / 6 && pauses < cycles / 2, "gelegentliche Pausen (" + pauses + "/" + cycles + ")");
        // Sprinten: angelegt; Laufen: weit hinten flattern; Schleichen: halb geschlossen
        for (int i = 0; i < 600; i++) {
            float t = i * 0.05f;
            float[] s = BackCosmetics.butterflyPose(t, 1L, 1, 1, 0);
            check(s[0] > 78 && s[2] < -8, "Sprinten: angelegt (" + s[0] + ")");
            float[] w = BackCosmetics.butterflyPose(t, 1L, 1, 0, 0);
            check(w[0] >= 69 && w[0] <= 83, "Laufen: Flattern weit hinten (" + w[0] + ")");
            float[] n = BackCosmetics.butterflyPose(t, 1L, 0, 0, 1);
            check(n[0] >= 36 && n[0] <= 48, "Schleichen: halb geschlossen (" + n[0] + ")");
        }
        // Flattern beim Laufen schneller als das Klappen im Stand
        int flips = 0;
        float last = BackCosmetics.butterflyPose(0, 1L, 1, 0, 0)[0], dir = 0;
        for (int i = 1; i <= 600; i++) {
            float f = BackCosmetics.butterflyPose(i / 60.0f, 1L, 1, 0, 0)[0];
            float d = Math.signum(f - last);
            if (d != 0 && d != dir) { flips++; dir = d; }
            last = f;
        }
        check(flips >= 30, "Laufen: schnelles Flattern (" + flips + " Richtungswechsel in 10 s)");

        // Kollisionen: Fluegel nie im Koerper/Kopf; mit Cape hinter dem Cape
        for (int i = 0; i < 160; i++) {
            float t = i * 0.23f;
            for (float[] blend : new float[][]{{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 0, 1}, {0.5f, 0, 0.5f}}) {
                float[] pose = BackCosmetics.butterflyPose(t, 5L, blend[0], blend[1], blend[2]);
                for (Pt pt : points(g, 3, wingAnim(pose, 0), b -> true)) {
                    check(depthBody(pt.p()) <= 0.02, "Butterfly: nicht im Koerper " + Arrays.toString(pt.p()) + " pose " + Arrays.toString(pose));
                    check(depthHeadLayer(pt.p()) <= 0.05, "Butterfly: nicht im Kopf " + Arrays.toString(pt.p()) + " pose " + Arrays.toString(pose));
                }
                if (i % 8 == 0) {
                    for (Pt pt : points(g, 3, wingAnim(pose, BackCosmetics.WINGS_CAPE_OFFSET_PX), b -> true)) {
                        double[] p = pt.p();
                        if (Math.abs(p[0]) < 5.1 && p[1] > 8 && p[1] < 24.2) check(p[2] >= capeOuterZ(p[1]) - 0.02, "Butterfly mit Cape: hinter dem Cape " + Arrays.toString(p));
                    }
                }
            }
        }
        // Arme: Schwung je Lauf-Amplitude (Vanilla: +-Amplitude rad); stetige Blende walkTarget(amp)
        for (int ai = 0; ai <= 10; ai++) {
            float amp = ai / 10.0f;
            float walk = BackCosmetics.walkTarget(amp);
            double maxSwing = Math.toDegrees(amp);
            for (int ti = 0; ti < 40; ti++) {
                float t = 3.0f + ti * 0.27f;
                for (float sprint : amp >= 0.9f ? new float[]{0, 1} : new float[]{0}) {
                    float[] pose = BackCosmetics.butterflyPose(t, 9L, walk, sprint, 0);
                    List<Pt> wp = points(g, 3, wingAnim(pose, 0), b -> !b.equals("thorax"));
                    for (int si = -4; si <= 4; si++) {
                        double sw = maxSwing * si / 4.0;
                        for (Pt pt : wp) {
                            check(depthArm(pt.p(), 1, sw) <= 0.05 && depthArm(pt.p(), -1, -sw) <= 0.05,
                                    "Butterfly: Arm schneidet Fluegel (amp " + amp + ", Schwung " + sw + ", pose " + Arrays.toString(pose) + ", " + Arrays.toString(pt.p()) + ")");
                        }
                    }
                }
            }
        }
    }

    // =================================================================================================================
    // BACK
    // =================================================================================================================

    private static List<Map<String, double[]>> backPoses(String id, boolean cape) {
        List<Map<String, double[]>> out = new ArrayList<>();
        double off = cape ? BackCosmetics.CAPE_OFFSET_PX : 0, tilt = cape ? BackCosmetics.CAPE_TILT_DEG : 0;
        for (int i = 0; i < 24; i++) {
            float phase = i * 0.39f, amp = (i % 3) / 2.0f, t = i * 0.7f;
            float[] sway = BackCosmetics.packSway(phase, amp, t);
            Map<String, double[]> a = new HashMap<>();
            a.put("pack", new double[]{tilt + sway[0], 0, sway[1], 0, 0, off});
            for (int k = 0; k < 6; k++) {
                float[] w = BackCosmetics.arrowWobble(k, phase, amp, t);
                a.put("arrow" + k, new double[]{w[0], 0, w[1], 0, 0, 0});
            }
            float[] l = BackCosmetics.lanternSwing(phase, amp, t);
            a.put("lantern", new double[]{l[0], 0, l[1], 0, 0, 0});
            out.add(a);
        }
        // Extremwerte der Begrenzungen
        for (double[] ex : new double[][]{{1.8, 1.2}, {1.8, -1.2}, {0, 1.2}, {0, -1.2}}) {
            Map<String, double[]> a = new HashMap<>();
            a.put("pack", new double[]{tilt + ex[0], 0, ex[1], 0, 0, off});
            for (int k = 0; k < 6; k++) a.put("arrow" + k, new double[]{(k % 2 == 0 ? 1 : -1) * BackCosmetics.ARROW_MAX_DEG, 0, (k % 3 == 0 ? -1 : 1) * BackCosmetics.ARROW_MAX_DEG, 0, 0, 0});
            a.put("lantern", new double[]{ex[1] > 0 ? 3.5 : -1.5, 0, ex[1] > 0 ? 3.0 : -3.0, 0, 0, 0});
            out.add(a);
        }
        return out;
    }

    private static void back(String id) {
        Geo g = load("back", id);
        for (String b : new String[]{id, "straps", "pack", "body"}) check(g.bones.containsKey(b), id + ": Knochen " + b);
        check("pack".equals(g.bones.get("body").parent) && id.equals(g.bones.get("pack").parent) && id.equals(g.bones.get("straps").parent), id + ": Knochen-Baum");
        double[] pv = g.bones.get("pack").pivot;
        check(Math.abs(pv[1] - 24) < 1e-6 && Math.abs(pv[2] - 2.25) < 1e-6, id + ": Pack-Pivot oben am Ruecken");
        switch (id) {
            case "jetpack" -> {
                check(g.bones.containsKey("flame_l") && g.bones.containsKey("flame_r"), "Jetpack: Flammen-Knochen");
                double[] fp = g.bones.get("flame_l").pivot, fr = g.bones.get("flame_r").pivot;
                check(Math.abs(fp[0] - BackCosmetics.JET_NOZZLE_X) < 1e-3 && Math.abs(fp[1] - BackCosmetics.JET_NOZZLE_Y) < 1e-3
                        && Math.abs(fp[2] - BackCosmetics.JET_NOZZLE_Z) < 1e-3 && Math.abs(fr[0] + BackCosmetics.JET_NOZZLE_X) < 1e-3,
                        "Jetpack: Partikel-Position = Duese " + Arrays.toString(fp));
            }
            case "quiver" -> { for (int k = 0; k < 6; k++) check(g.bones.containsKey("arrow" + k), "Koecher: Pfeil " + k); }
            case "backpack" -> check(g.bones.containsKey("lantern") && "lantern".equals(g.bones.get("glow").parent), "Rucksack: Laterne + Licht");
            default -> { }
        }
        Predicate<String> packBones = b -> under(g, b, "pack") && !b.startsWith("flame") && !b.equals("glow");
        Predicate<String> strapBones = b -> b.equals("straps");
        // Ruhelage: liegt am Ruecken an (Abstand zum Jacket klein), nicht zu weit weg, mittig
        List<Pt> rest = points(g, 2, null, packBones);
        double minZ = 1e9, maxZ = -1e9, maxAbsX = 0;
        for (Pt pt : rest) {
            double[] p = pt.p();
            if (p[1] > 12 && p[1] < 24 && Math.abs(p[0]) < 4) minZ = Math.min(minZ, p[2]);
            maxZ = Math.max(maxZ, p[2]);
            maxAbsX = Math.max(maxAbsX, Math.abs(p[0]));
        }
        check(minZ >= 2.25 && minZ <= 2.7, id + ": liegt am Ruecken an (" + minZ + ")");
        check(maxZ < 9.5, id + ": steht nicht zu weit ab (" + maxZ + ")");
        check(maxAbsX < 8.0, id + ": nicht zu breit (" + maxAbsX + ")");
        // Gurte vorhanden, vorn auf der Brust, an keiner Stelle im Koerper
        boolean front = false;
        for (Pt pt : points(g, 1, null, strapBones)) {
            double[] p = pt.p();
            check(depthBody(p) <= 0.03, id + ": Gurt nicht im Koerper " + Arrays.toString(p));
            if (depthHeadLayer(p) > 0.03) check(p[1] < 24.75, id + ": Gurt nur unter dem Kopf verdeckt " + Arrays.toString(p));
            for (int s = -1; s <= 1; s += 2) for (int sw = -50; sw <= 50; sw += 10) {
                check(depthArm(p, s, sw) <= 0.05, id + ": Gurt nicht im Arm (Schwung " + sw + ") " + Arrays.toString(p));
            }
            if (p[2] < -2.2 && p[1] > 16 && p[1] < 22) front = true;
        }
        check(front, id + ": Gurt vorn ueber der Brust");
        for (boolean cape : new boolean[]{false, true}) {
            List<Map<String, double[]>> poses = backPoses(id, cape);
            for (int pi = 0; pi < poses.size(); pi++) {
                Map<String, double[]> anim = poses.get(pi);
                List<Pt> pts = points(g, 2, anim, packBones);
                for (Pt pt : pts) {
                    double[] p = pt.p();
                    check(depthBody(p) <= 0.02, id + ": nicht im Koerper (cape=" + cape + ", pose " + pi + ", " + pt.bone() + " " + Arrays.toString(p) + ")");
                    check(depthHeadLayer(p) <= 0.05, id + ": nicht im Kopf (cape=" + cape + ", pose " + pi + ", " + pt.bone() + " " + Arrays.toString(p) + ")");
                    if (cape && Math.abs(p[0]) < 5.1 && p[1] > 8 && p[1] < 24.2) {
                        check(p[2] >= capeOuterZ(p[1]) - 0.02, id + ": mit Cape hinter dem Cape (pose " + pi + ", " + pt.bone() + " " + Arrays.toString(p) + ", Cape " + capeOuterZ(p[1]) + ")");
                    }
                }
                // Arme +-50 Grad, Beine +-20 Grad (nur jede dritte Pose, die Grenzen sind enthalten)
                if (pi % 3 == 0 || pi >= poses.size() - 4) {
                    for (Pt pt : pts) {
                        for (int s = -1; s <= 1; s += 2) {
                            for (int sw = -50; sw <= 50; sw += 5) {
                                check(depthArm(pt.p(), s, sw) <= 0.05, id + ": Arm schneidet (cape=" + cape + ", Schwung " + sw + ", " + pt.bone() + " " + Arrays.toString(pt.p()) + ")");
                            }
                            for (int sw = -20; sw <= 20; sw += 5) {
                                check(depthLeg(pt.p(), s, sw) <= 0.05, id + ": Bein schneidet (cape=" + cape + ", Schwung " + sw + ", " + pt.bone() + " " + Arrays.toString(pt.p()) + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    // =================================================================================================================
    // Animations-Mathematik
    // =================================================================================================================

    private static void animationMath() {
        for (int i = 0; i <= 2000; i++) {
            float ph = i * 0.013f, t = i * 0.017f;
            for (float amp : new float[]{0, 0.3f, 0.86f, 1f, 1.4f}) {
                float[] s = BackCosmetics.packSway(ph, amp, t);
                check(s[0] >= 0 && s[0] <= 1.81f, "Pack kippt nie in den Koerper (" + s[0] + ")");
                check(Math.abs(s[1]) <= 1.21f, "Pack pendelt wenig (" + s[1] + ")");
                for (int k = 0; k < 6; k++) {
                    float[] w = BackCosmetics.arrowWobble(k, ph, amp, t);
                    check(Math.abs(w[0]) <= BackCosmetics.ARROW_MAX_DEG && Math.abs(w[1]) <= BackCosmetics.ARROW_MAX_DEG, "Pfeile begrenzt");
                }
                float[] l = BackCosmetics.lanternSwing(ph, amp, t);
                check(l[0] >= -1.5f && l[0] <= 3.5f && Math.abs(l[1]) <= 3.0f, "Laterne begrenzt");
            }
        }
        // Pfeile wippen beim Laufen deutlich mehr als im Stand
        float idle = 0, walk = 0;
        for (int i = 0; i < 500; i++) {
            idle = Math.max(idle, Math.abs(BackCosmetics.arrowWobble(0, i * 0.05f, 0f, i * 0.05f)[0]));
            walk = Math.max(walk, Math.abs(BackCosmetics.arrowWobble(0, i * 0.05f, 1f, i * 0.05f)[0]));
        }
        check(idle < 0.6f && walk > 3.0f, "Pfeile wippen beim Laufen (" + idle + " / " + walk + ")");
        // Jetpack: am Boden aus, nach dem Absprung kurzer Schub, beim schnellen Fallen Bremsschub
        check(BackCosmetics.jetTarget(true, 0, 10) == 0, "am Boden keine Flamme");
        check(BackCosmetics.jetTarget(false, 0.05f, 4f) > 0.9f, "Schub beim Absprung");
        check(BackCosmetics.jetTarget(false, 0.8f, 2f) == 0, "ruhiger Flug ohne Flamme");
        check(BackCosmetics.jetTarget(false, 1.2f, -20f) > 0.6f, "Bremsschub beim Fallen");
        BackCosmetics.State st = new BackCosmetics.State();
        float dt = 1 / 60f;
        double y = 64;
        for (int i = 0; i < 30; i++) st.updateAir(dt, y, true);
        check(st.jet < 0.01f, "Flamme aus am Boden");
        float peak = 0;
        double vy = 8.4; // Sprung: 0.42 Bloecke/Tick
        for (int i = 0; i < 30; i++) {
            vy -= 32 * dt;
            y += vy * dt;
            st.updateAir(dt, y, false);
            peak = Math.max(peak, st.jet);
        }
        check(peak > 0.7f, "Flamme nach dem Absprung (" + peak + ")");
        for (int i = 0; i < 60; i++) { y += 2 * dt; st.updateAir(dt, y, false); }
        check(st.jet < 0.1f, "Flamme klingt ab (" + st.jet + ")");
        for (int i = 0; i < 40; i++) st.updateAir(dt, y, true);
        check(st.jet < 0.01f && st.airTime == 0, "gelandet");
        // Glanzlicht und Flackern
        float gmin = 9, gmax = -9;
        for (int i = 0; i < 20000; i++) {
            float t = i * 0.005f;
            float g = BackCosmetics.lensGlint(t, 42L);
            gmin = Math.min(gmin, g);
            gmax = Math.max(gmax, g);
            float f = BackCosmetics.flicker(t, 3L);
            check(f >= 0.72f && f <= 1.0f, "Flackern im Bereich (" + f + ")");
        }
        check(gmin >= 0.27f && gmin < 0.3f && gmax > 0.95f && gmax <= 1.0f, "Glanzlicht dezent mit Schimmer (" + gmin + ".." + gmax + ")");
        // Bewegungs-Blenden weich
        BackCosmetics.State b = new BackCosmetics.State();
        float lastW = 0;
        for (int i = 0; i < 60; i++) {
            b.update(dt, 1.0f, false, false);
            check(b.walk >= lastW && b.walk - lastW < 0.12f, "Lauf-Blende weich");
            lastW = b.walk;
        }
        check(b.walk > 0.97f, "Lauf-Blende erreicht (" + b.walk + ")");
    }

    private static void stateTable() {
        int before = BackCosmetics.trackedStates();
        check(before == 0, "Start ohne Zustand");
        BackCosmetics.state(1, 1000L);
        BackCosmetics.State a = BackCosmetics.state(2, 1000L);
        check(a == BackCosmetics.state(2, 2000L), "Zustand bleibt erhalten");
        BackCosmetics.state(3, 30_000L);
        check(BackCosmetics.trackedStates() == 1, "alte Spieler entfernt (" + BackCosmetics.trackedStates() + ")");
        BackCosmetics.State s = new BackCosmetics.State();
        check(s.tick(1_000_000_000L) == 0f && Math.abs(s.tick(1_016_000_000L) - 0.016f) < 1e-4, "Zeitschritt");
    }
}

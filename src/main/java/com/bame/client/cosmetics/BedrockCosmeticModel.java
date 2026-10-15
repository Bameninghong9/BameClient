package com.bame.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class BedrockCosmeticModel {

    public static class FaceUV {
        public final float u;
        public final float v;
        public final float uSize;
        public final float vSize;

        public FaceUV(float u, float v, float uSize, float vSize) {
            this.u = u;
            this.v = v;
            this.uSize = uSize;
            this.vSize = vSize;
        }
    }

    public static class ModelCube {
        public final float ox, oy, oz;
        public final float sx, sy, sz;
        public final float inflate;
        public final boolean hasPivot;
        public final float px, py, pz;
        public final boolean hasRot;
        public final float rx, ry, rz;
        public final Map<String, FaceUV> faces = new HashMap<>();

        public ModelCube(float ox, float oy, float oz, float sx, float sy, float sz,
                         float inflate, boolean hasPivot, float px, float py, float pz,
                         boolean hasRot, float rx, float ry, float rz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.inflate = inflate;
            this.hasPivot = hasPivot;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.hasRot = hasRot;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        /*
         * Zeichenfertige Flächen in der Iterationsreihenfolge von faces (gleiche Reihenfolge wie früher über
         * faces.entrySet()): je Fläche 4 Eckindizes und die UV. Einmal aufgebaut, damit renderCube pro Frame keinen
         * Map-Iterator anlegt. Neu aufgebaut, falls sich die Anzahl der Flächen ändert (faces wird nur beim Laden befüllt).
         */
        private int[] drawCorners;
        private FaceUV[] drawUvs;
        private int drawBuiltFor = -1;

        void ensureDrawFaces() {
            if (drawBuiltFor == faces.size() && drawUvs != null) return;
            int[] corners = new int[faces.size() * 4];
            FaceUV[] uvs = new FaceUV[faces.size()];
            int n = 0;
            for (Map.Entry<String, FaceUV> fe : faces.entrySet()) {
                FaceUV uv = fe.getValue();
                if (uv.uSize == 0 && uv.vSize == 0) continue;
                int[] c = cornersOf(fe.getKey());
                if (c == null) continue;
                System.arraycopy(c, 0, corners, n * 4, 4);
                uvs[n++] = uv;
            }
            drawCorners = java.util.Arrays.copyOf(corners, n * 4);
            drawUvs = java.util.Arrays.copyOf(uvs, n);
            drawBuiltFor = faces.size();
        }
    }

    private static final int[] WEST = {2, 0, 1, 3};   // C_010, C_000, C_001, C_011
    private static final int[] EAST = {7, 5, 4, 6};   // C_111, C_101, C_100, C_110
    private static final int[] NORTH = {6, 4, 0, 2};  // C_110, C_100, C_000, C_010
    private static final int[] SOUTH = {3, 1, 5, 7};  // C_011, C_001, C_101, C_111
    private static final int[] UP = {2, 3, 7, 6};     // C_010, C_011, C_111, C_110
    private static final int[] DOWN = {1, 0, 4, 5};   // C_001, C_000, C_100, C_101

    private static int[] cornersOf(String face) {
        return switch (face) {
            case "west" -> WEST;
            case "east" -> EAST;
            case "north" -> NORTH;
            case "south" -> SOUTH;
            case "up" -> UP;
            case "down" -> DOWN;
            default -> null;
        };
    }

    public static class ModelBone {
        public final String name;
        public final String parent;
        public final float px, py, pz;
        public final float rx, ry, rz;
        public final List<ModelCube> cubes = new ArrayList<>();
        public final List<ModelBone> children = new ArrayList<>();

        public float animRx, animRy, animRz;
        public float animTx, animTy, animTz;

        public ModelBone(String name, String parent, float px, float py, float pz, float rx, float ry, float rz) {
            this.name = name;
            this.parent = parent;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        public void resetAnimation() {
            animRx = 0; animRy = 0; animRz = 0;
            animTx = 0; animTy = 0; animTz = 0;
            for (int i = 0, n = children.size(); i < n; i++) {
                children.get(i).resetAnimation();
            }
        }
    }

    public final float textureWidth;
    public final float textureHeight;
    public final Map<String, ModelBone> bonesByName = new HashMap<>();
    public final List<ModelBone> rootBones = new ArrayList<>();

    // Blockbench exportiert X gespiegelt: im Bedrock-Raum muessen Rotationen um X und Z negiert werden.
    // Nur fuer Modelle aktivieren, die nicht per Hand auf die alte Konvention nachjustiert wurden.
    public boolean blockbenchAxisFix = false;

    /**
     * Optional: feste Normale (Java-Modellraum, z. B. {@code {0, -1, 0}} = nach oben) statt der Flächennormale. Für
     * flache, selbst leuchtende Ebenen (Flammen), die sonst je nach Ausrichtung von der Entity-Beleuchtung abgedunkelt
     * würden. {@code null} = Flächennormale (Standard, bestehende Modelle unverändert).
     */
    public float[] fixedNormal = null;

    public BedrockCosmeticModel(float textureWidth, float textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public static BedrockCosmeticModel load(String resourcePath) {
        try {
            InputStream is = BedrockCosmeticModel.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null && resourcePath.contains("caeserclient")) {
                is = BedrockCosmeticModel.class.getClassLoader().getResourceAsStream(resourcePath.replace("caeserclient", "bameclient"));
            } else if (is == null && resourcePath.contains("bameclient")) {
                is = BedrockCosmeticModel.class.getClassLoader().getResourceAsStream(resourcePath.replace("bameclient", "caeserclient"));
            }
            if (is == null) {
                System.err.println("[CaeserCosmetics] Resource not found: " + resourcePath);
                return null;
            }
            JsonObject root;
            try (InputStream finalStream = is) {
                root = JsonParser.parseReader(new InputStreamReader(finalStream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            JsonArray geos = root.getAsJsonArray("minecraft:geometry");
            if (geos == null || geos.size() == 0) return null;

            JsonObject geo = geos.get(0).getAsJsonObject();
            JsonObject desc = geo.getAsJsonObject("description");
            float texW = desc.has("texture_width") ? desc.get("texture_width").getAsFloat() : 64.0f;
            float texH = desc.has("texture_height") ? desc.get("texture_height").getAsFloat() : 64.0f;

            BedrockCosmeticModel model = new BedrockCosmeticModel(texW, texH);
            JsonArray bonesArr = geo.getAsJsonArray("bones");
            if (bonesArr != null) {
                for (JsonElement be : bonesArr) {
                    JsonObject bo = be.getAsJsonObject();
                    String name = bo.get("name").getAsString();
                    if ("bb_main".equals(name)) continue;

                    String parent = bo.has("parent") ? bo.get("parent").getAsString() : null;
                    float px = 0, py = 0, pz = 0;
                    if (bo.has("pivot")) {
                        JsonArray p = bo.getAsJsonArray("pivot");
                        px = p.get(0).getAsFloat(); py = p.get(1).getAsFloat(); pz = p.get(2).getAsFloat();
                    }
                    float rx = 0, ry = 0, rz = 0;
                    if (bo.has("rotation")) {
                        JsonArray r = bo.getAsJsonArray("rotation");
                        rx = r.get(0).getAsFloat(); ry = r.get(1).getAsFloat(); rz = r.get(2).getAsFloat();
                    }

                    ModelBone bone = new ModelBone(name, parent, px, py, pz, rx, ry, rz);

                    if (bo.has("cubes")) {
                        for (JsonElement ce : bo.getAsJsonArray("cubes")) {
                            JsonObject co = ce.getAsJsonObject();
                            JsonArray org = co.getAsJsonArray("origin");
                            JsonArray sz = co.getAsJsonArray("size");
                            float ox = org.get(0).getAsFloat(), oy = org.get(1).getAsFloat(), oz = org.get(2).getAsFloat();
                            float sx = sz.get(0).getAsFloat(), sy = sz.get(1).getAsFloat(), szVal = sz.get(2).getAsFloat();
                            float inf = co.has("inflate") ? co.get("inflate").getAsFloat() : 0.0f;

                            boolean hasP = co.has("pivot");
                            float cpx = 0, cpy = 0, cpz = 0;
                            if (hasP) {
                                JsonArray cp = co.getAsJsonArray("pivot");
                                cpx = cp.get(0).getAsFloat(); cpy = cp.get(1).getAsFloat(); cpz = cp.get(2).getAsFloat();
                            }

                            boolean hasR = co.has("rotation");
                            float crx = 0, cry = 0, crz = 0;
                            if (hasR) {
                                JsonArray cr = co.getAsJsonArray("rotation");
                                crx = cr.get(0).getAsFloat(); cry = cr.get(1).getAsFloat(); crz = cr.get(2).getAsFloat();
                            }

                            ModelCube cube = new ModelCube(ox, oy, oz, sx, sy, szVal, inf, hasP, cpx, cpy, cpz, hasR, crx, cry, crz);

                            if (co.has("uv") && co.get("uv").isJsonObject()) {
                                JsonObject uvo = co.getAsJsonObject("uv");
                                for (String fKey : uvo.keySet()) {
                                    JsonObject fo = uvo.getAsJsonObject(fKey);
                                    if (fo.has("uv") && fo.has("uv_size")) {
                                        JsonArray uva = fo.getAsJsonArray("uv");
                                        JsonArray sza = fo.getAsJsonArray("uv_size");
                                        float u = uva.get(0).getAsFloat();
                                        float v = uva.get(1).getAsFloat();
                                        float us = sza.get(0).getAsFloat();
                                        float vs = sza.get(1).getAsFloat();
                                        cube.faces.put(fKey.toLowerCase(java.util.Locale.ROOT), new FaceUV(u, v, us, vs));
                                    }
                                }
                            }
                            bone.cubes.add(cube);
                        }
                    }
                    model.bonesByName.put(name, bone);
                }
            }

            // Build hierarchy (excluding mannequin dummy bones)
            java.util.Set<String> dummyBones = java.util.Set.of(
                "bb_main", "effect", "bipedrig", "bipedhead", "armorhead", "bipedbody", "armorbody",
                "bipedrightarm", "armorrightarm", "bipedleftarm", "armorleftarm",
                "bipedrightleg", "armorrightleg", "armorrightboot",
                "bipedleftleg", "armorleftleg", "armorleftboot"
            );
            for (ModelBone b : model.bonesByName.values()) {
                if (dummyBones.contains(b.name.toLowerCase(java.util.Locale.ROOT))) {
                    continue;
                }
                String p = b.parent != null ? b.parent.toLowerCase(java.util.Locale.ROOT) : "";
                if (b.parent == null || !model.bonesByName.containsKey(b.parent) || dummyBones.contains(p)) {
                    model.rootBones.add(b);
                } else {
                    model.bonesByName.get(b.parent).children.add(b);
                }
            }
            return model;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public ModelBone getBone(String name) {
        return bonesByName.get(name);
    }

    public void resetAllAnimations() {
        for (int i = 0, n = rootBones.size(); i < n; i++) {
            rootBones.get(i).resetAnimation();
        }
    }

    public void setBoneRotation(String name, float rx, float ry, float rz) {
        ModelBone b = getBone(name);
        if (b != null) {
            b.animRx = rx;
            b.animRy = ry;
            b.animRz = rz;
        }
    }

    public void setBoneTranslation(String name, float tx, float ty, float tz) {
        ModelBone b = getBone(name);
        if (b != null) {
            b.animTx = tx;
            b.animTy = ty;
            b.animTz = tz;
        }
    }

    /**
     * Punkt im Raum eines Knochens (Bedrock-Pixel, Ruhelage) mit den aktuellen Animationswerten in den Java-Modellraum
     * (Bloecke, wie die gerenderten Vertices) umrechnen, z. B. fuer Partikel an einer Modellstelle.
     *
     * @return {@code null}, wenn es den Knochen nicht gibt
     */
    public Vector3f boneToModel(String boneName, float x, float y, float z) {
        ModelBone bone = bonesByName.get(boneName);
        if (bone == null) return null;
        // Kette Wurzel -> Knochen (nur echte Eltern, wie beim Rendern)
        List<ModelBone> chain = new ArrayList<>();
        for (ModelBone b = bone; b != null; ) {
            chain.add(0, b);
            ModelBone p = b.parent != null ? bonesByName.get(b.parent) : null;
            b = (p != null && p.children.contains(b)) ? p : null;
        }
        float xzSign = blockbenchAxisFix ? -1.0f : 1.0f;
        Matrix4f m = new Matrix4f().identity();
        for (ModelBone b : chain) {
            m.translate(b.px + b.animTx, b.py + b.animTy, b.pz + b.animTz);
            m.rotateZ((float) Math.toRadians(xzSign * (b.rz + b.animRz)));
            m.rotateY((float) Math.toRadians(b.ry + b.animRy));
            m.rotateX((float) Math.toRadians(xzSign * (b.rx + b.animRx)));
            m.translate(-b.px, -b.py, -b.pz);
        }
        Vector3f v = new Vector3f(x, y, z);
        m.transformPosition(v);
        toJava(v);
        return v;
    }

    public void render(MatrixStack.Entry entry, VertexConsumer consumer, int light,
                       int frameIndex, int totalFrames) {
        render(entry, consumer, light, frameIndex, totalFrames, false);
    }

    public void render(MatrixStack.Entry entry, VertexConsumer consumer, int light,
                       int frameIndex, int totalFrames, boolean mirrorX) {
        render(entry, consumer, light, frameIndex, totalFrames, mirrorX, 0xFFFFFFFF, null);
    }

    /**
     * Wie {@link #render(MatrixStack.Entry, VertexConsumer, int, int, int, boolean)}, mit Farb-Tint und optional nur
     * einem Knochen samt Kindern.
     *
     * @param argb     Vertex-Farbe (wird mit der Textur multipliziert); {@code 0xFFFFFFFF} = unverändert
     * @param onlyBone nur diesen Knochen (und seine Kinder) zeichnen; {@code null} = alle
     */
    public void render(MatrixStack.Entry entry, VertexConsumer consumer, int light,
                       int frameIndex, int totalFrames, boolean mirrorX, int argb, String onlyBone) {
        render(entry, consumer, light, frameIndex, totalFrames, mirrorX, argb, onlyBone, null);
    }

    /**
     * Wie oben; zusätzlich werden die Würfel der Knochen in {@code skipBones} nicht gezeichnet (ihre Kinder schon, die
     * Transformation wird weitergegeben). Für getrennte Durchgänge, z. B. selbstleuchtende Flammen in eigener Ebene.
     */
    public void render(MatrixStack.Entry entry, VertexConsumer consumer, int light,
                       int frameIndex, int totalFrames, boolean mirrorX, int argb, String onlyBone,
                       java.util.Set<String> skipBones) {
        for (int i = 0, n = rootBones.size(); i < n; i++) {
            renderBone(rootBones.get(i), ROOT_MAT, 0, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb, onlyBone, skipBones);
        }
    }

    /*
     * Matrizen-Puffer (nur Render-Thread, render() ist nicht verschachtelt): eine Matrix pro Hierarchie-Tiefe statt
     * new Matrix4f pro Knochen und gedrehtem Würfel. set(parent) kopiert Werte und Eigenschaften wie der frühere
     * Kopierkonstruktor, die Rechenschritte sind dieselben, also identische Vertices (BedrockModelRenderEqualityTest).
     */
    private static final Matrix4f ROOT_MAT = new Matrix4f().identity();
    private static final Matrix4f CUBE_MAT = new Matrix4f();
    private static Matrix4f[] boneMats = new Matrix4f[16];

    private static Matrix4f boneMat(int depth) {
        if (depth >= boneMats.length) boneMats = java.util.Arrays.copyOf(boneMats, Math.max(depth + 1, boneMats.length * 2));
        Matrix4f m = boneMats[depth];
        if (m == null) {
            m = new Matrix4f();
            boneMats[depth] = m;
        }
        return m;
    }

    private void renderBone(ModelBone bone, Matrix4f parentMat, int depth, MatrixStack.Entry entry,
                            VertexConsumer consumer, int light, int frameIndex, int totalFrames, boolean mirrorX,
                            int argb, String onlyBone, java.util.Set<String> skipBones) {
        // Knochen-Filter: außerhalb des gesuchten Teilbaums nichts zeichnen, aber die Transformation weitergeben
        boolean inTree = onlyBone == null || onlyBone.equals(bone.name);
        boolean draw = inTree && (skipBones == null || !skipBones.contains(bone.name));
        String childFilter = inTree ? null : onlyBone;
        Matrix4f boneMat = boneMat(depth).set(parentMat);

        float px = bone.px;
        float py = bone.py;
        float pz = bone.pz;
        float xzSign = blockbenchAxisFix ? -1.0f : 1.0f;

        // T(pivot + animTrans) @ R(bind + anim) @ T(-pivot)
        boneMat.translate(px + bone.animTx, py + bone.animTy, pz + bone.animTz);
        // Bedrock rotation order: Z, Y, X
        boneMat.rotateZ((float) Math.toRadians(xzSign * (bone.rz + bone.animRz)));
        boneMat.rotateY((float) Math.toRadians(bone.ry + bone.animRy));
        boneMat.rotateX((float) Math.toRadians(xzSign * (bone.rx + bone.animRx)));
        boneMat.translate(-px, -py, -pz);

        // Render cubes of this bone (Index-Schleifen: kein Iterator pro Frame)
        if (draw) {
            List<ModelCube> cubes = bone.cubes;
            for (int ci = 0, cn = cubes.size(); ci < cn; ci++) {
                ModelCube cube = cubes.get(ci);
                Matrix4f cubeMat = boneMat;
                if (cube.hasRot || cube.hasPivot) {
                    cubeMat = CUBE_MAT.set(boneMat);
                    cubeMat.translate(cube.px, cube.py, cube.pz);
                    if (cube.hasRot) {
                        cubeMat.rotateZ((float) Math.toRadians(xzSign * cube.rz));
                        cubeMat.rotateY((float) Math.toRadians(cube.ry));
                        cubeMat.rotateX((float) Math.toRadians(xzSign * cube.rx));
                    }
                    cubeMat.translate(-cube.px, -cube.py, -cube.pz);
                }
                renderCube(cube, cubeMat, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb);
            }
        }

        // Render children
        List<ModelBone> children = bone.children;
        for (int i = 0, n = children.size(); i < n; i++) {
            renderBone(children.get(i), boneMat, depth + 1, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb, childFilter, skipBones);
        }
    }

    /*
     * Rechenpuffer für renderCube (nur Render-Thread; Modelle werden nie parallel gezeichnet). Früher wurden pro Fläche
     * 7 Vector3f angelegt und jede Ecke bis zu dreimal transformiert; jetzt werden die 8 Würfelecken einmal pro Würfel
     * transformiert und wiederverwendet. Gleiche Rechenschritte in gleicher Reihenfolge, also identische Vertices.
     * Index einer Ecke: xi * 4 + yi * 2 + zi (0 = min, 1 = max).
     */
    private static final Vector3f[] CORNERS = new Vector3f[8];
    private static final Vector3f FV0 = new Vector3f(), FV1 = new Vector3f(), FV2 = new Vector3f(), FV3 = new Vector3f();
    private static final Vector3f E1 = new Vector3f(), E2 = new Vector3f(), NORMAL = new Vector3f();
    static {
        for (int i = 0; i < 8; i++) CORNERS[i] = new Vector3f();
    }

    private void renderCube(ModelCube cube, Matrix4f mat, MatrixStack.Entry entry,
                            VertexConsumer consumer, int light, int frameIndex, int totalFrames, boolean mirrorX,
                            int argb) {
        float inf = cube.inflate;
        float x0 = cube.ox - inf;
        float x1 = cube.ox + cube.sx + inf;
        float y0 = cube.oy - inf;
        float y1 = cube.oy + cube.sy + inf;
        float z0 = cube.oz - inf;
        float z1 = cube.oz + cube.sz + inf;

        boolean cornersReady = false;
        cube.ensureDrawFaces();
        int[] faceCorners = cube.drawCorners;
        FaceUV[] faceUvs = cube.drawUvs;
        for (int fi = 0; fi < faceUvs.length; fi++) {
            FaceUV uv = faceUvs[fi];
            int i0 = faceCorners[fi * 4], i1 = faceCorners[fi * 4 + 1], i2 = faceCorners[fi * 4 + 2], i3 = faceCorners[fi * 4 + 3];
            if (!cornersReady) {
                cornersReady = true;
                for (int c = 0; c < 8; c++) {
                    Vector3f v = CORNERS[c];
                    v.set((c & 4) != 0 ? x1 : x0, (c & 2) != 0 ? y1 : y0, (c & 1) != 0 ? z1 : z0);
                    // Transform by bone/cube matrix in Bedrock space, then Bedrock -> Java model space:
                    // X_java = X_bedrock * 0.0625, Y_java = (24.0 - Y_bedrock) * 0.0625, Z_java = Z_bedrock * 0.0625
                    mat.transformPosition(v);
                    toJava(v);
                }
            }

            Vector3f v0 = FV0.set(CORNERS[i0]);
            Vector3f v1 = FV1.set(CORNERS[i1]);
            Vector3f v2 = FV2.set(CORNERS[i2]);
            Vector3f v3 = FV3.set(CORNERS[i3]);

            if (mirrorX) {
                v0.x = -v0.x;
                v1.x = -v1.x;
                v2.x = -v2.x;
                v3.x = -v3.x;
            }

            // Calculate face normal from transformed Java vertices
            Vector3f e1 = E1.set(v1).sub(v0);
            Vector3f e2 = E2.set(v3).sub(v0);
            Vector3f normal = NORMAL.set(e1).cross(e2);
            if (mirrorX) {
                normal.negate();
            }
            if (fixedNormal != null) {
                normal.set(fixedNormal[0], fixedNormal[1], fixedNormal[2]);
            } else if (normal.lengthSquared() > 1e-6f) {
                normal.normalize();
            } else {
                normal.set(0, 1, 0);
            }

            // UV mapping with frame animation support
            float texU1 = uv.u / textureWidth;
            float texU2 = (uv.u + uv.uSize) / textureWidth;
            float rawV1 = uv.v / textureHeight;
            float rawV2 = (uv.v + uv.vSize) / textureHeight;

            float texV1 = (frameIndex + rawV1) / (float) totalFrames;
            float texV2 = (frameIndex + rawV2) / (float) totalFrames;

            if (mirrorX) {
                emitVertex(consumer, entry, v0.x, v0.y, v0.z, texU1, texV1, light, normal.x, normal.y, normal.z, argb);
                emitVertex(consumer, entry, v3.x, v3.y, v3.z, texU2, texV1, light, normal.x, normal.y, normal.z, argb);
                emitVertex(consumer, entry, v2.x, v2.y, v2.z, texU2, texV2, light, normal.x, normal.y, normal.z, argb);
                emitVertex(consumer, entry, v1.x, v1.y, v1.z, texU1, texV2, light, normal.x, normal.y, normal.z, argb);
            } else {
                emitVertex(consumer, entry, v0.x, v0.y, v0.z, texU1, texV1, light, normal.x, normal.y, normal.z, argb);
                emitVertex(consumer, entry, v1.x, v1.y, v1.z, texU1, texV2, light, normal.x, normal.y, normal.z, argb);
                emitVertex(consumer, entry, v2.x, v2.y, v2.z, texU2, texV2, light, normal.x, normal.y, normal.z, argb);
                emitVertex(consumer, entry, v3.x, v3.y, v3.z, texU2, texV1, light, normal.x, normal.y, normal.z, argb);
            }
        }
    }

    private static void toJava(Vector3f v) {
        v.x = v.x * 0.0625f;
        v.y = (24.0f - v.y) * 0.0625f;
        v.z = v.z * 0.0625f;
    }

    private static void emitVertex(VertexConsumer consumer, MatrixStack.Entry entry,
                                   float x, float y, float z, float u, float v,
                                   int light, float nx, float ny, float nz, int argb) {
        consumer.vertex(entry, x, y, z)
                .color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
    }
}

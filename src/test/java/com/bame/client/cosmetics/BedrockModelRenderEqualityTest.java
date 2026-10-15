package com.bame.client.cosmetics;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link BedrockCosmeticModel#render} zeichnet seit der Allokations-Optimierung (Würfelecken einmal transformiert,
 * Rechenpuffer statt 7 neuer Vektoren pro Fläche) exakt dieselben Vertices wie vorher. Verglichen wird Bit für Bit gegen
 * die frühere Implementierung (unten als Referenz übernommen), für alle Cosmetics-Modelle, mit Animationen, Spiegelung,
 * Farb-Tint, Knochen-Filter und übersprungenen Knochen. Danach eine kurze Zeitmessung (nur Ausgabe).
 */
public class BedrockModelRenderEqualityTest {

    private static final String[] MODELS = {
            "wings/gold_wings", "wings/dragon_wings", "wings/butterfly_wings", "hat/angel_halo", "hat/demon_horns",
            "hat/witch_hat", "hat/straw_hat", "hat/crown", "hat/headphones", "hat/viking_helmet", "hat/propeller_cap",
            "hat/cat_ears", "face/sunglasses", "face/skull_mask", "face/gas_mask", "face/aviator_goggles",
            "accessory/minimehand", "pets/baby_dragon", "back/jetpack", "back/guitar", "back/quiver", "back/katana",
            "back/backpack"
    };

    private static int checks = 0;

    public static void main(String[] args) {
        long vertices = 0;
        List<BedrockCosmeticModel> loaded = new ArrayList<>();
        for (String path : MODELS) {
            BedrockCosmeticModel m = BedrockCosmeticModel.load("assets/bameclient/models/cosmetics/" + path + ".geo.json");
            check(m != null, path + " loads");
            loaded.add(m);
            for (int variant = 0; variant < 8; variant++) {
                m.blockbenchAxisFix = (variant & 1) != 0;
                animate(m, variant);
                boolean mirror = (variant & 2) != 0;
                int argb = (variant & 4) != 0 ? 0x80FF8040 : 0xFFFFFFFF;
                String only = variant == 5 && !m.rootBones.isEmpty() ? m.rootBones.get(0).name : null;
                java.util.Set<String> skip = variant == 6 && !m.bonesByName.isEmpty()
                        ? java.util.Set.of(m.bonesByName.keySet().iterator().next()) : null;
                MatrixStack.Entry entry = entry(variant);

                Recorder a = new Recorder();
                m.render(entry, a, 0xF000F0, variant % 3, 3, mirror, argb, only, skip);
                Recorder b = new Recorder();
                ReferenceRender.render(m, entry, b, 0xF000F0, variant % 3, 3, mirror, argb, only, skip);
                check(a.data.size() == b.data.size(), path + " variant " + variant + ": vertex count " + a.data.size() + " vs " + b.data.size());
                for (int i = 0; i < a.data.size(); i++) {
                    check(Float.floatToRawIntBits(a.data.get(i)) == Float.floatToRawIntBits(b.data.get(i)),
                            path + " variant " + variant + ": value " + i + " differs (" + a.data.get(i) + " vs " + b.data.get(i) + ")");
                }
                vertices += a.vertices;
            }
            m.blockbenchAxisFix = false;
        }

        // Zeitmessung: alle Modelle je 300-mal (ungefähr so viel wie ein Spieler mit allen Slots über einige Sekunden)
        MatrixStack.Entry entry = entry(1);
        Counter sinkA = new Counter(), sinkB = new Counter();
        for (int warm = 0; warm < 3; warm++) {
            for (BedrockCosmeticModel m : loaded) {
                for (int i = 0; i < 100; i++) {
                    m.render(entry, sinkA, 0xF000F0, 0, 1);
                    ReferenceRender.render(m, entry, sinkB, 0xF000F0, 0, 1, false, 0xFFFFFFFF, null, null);
                }
            }
        }
        long tNew = Long.MAX_VALUE, tOld = Long.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            long t0 = System.nanoTime();
            for (BedrockCosmeticModel m : loaded) for (int i = 0; i < 300; i++) m.render(entry, sinkA, 0xF000F0, 0, 1);
            long t1 = System.nanoTime();
            for (BedrockCosmeticModel m : loaded) for (int i = 0; i < 300; i++)
                ReferenceRender.render(m, entry, sinkB, 0xF000F0, 0, 1, false, 0xFFFFFFFF, null, null);
            long t2 = System.nanoTime();
            tNew = Math.min(tNew, t1 - t0);
            tOld = Math.min(tOld, t2 - t1);
        }
        System.out.printf(Locale.ROOT, "Cosmetics-Modelle (%d Modelle x 300 Renderdurchgaenge): alt %.1f ms, neu %.1f ms, Faktor %.1fx%n",
                loaded.size(), tOld / 1e6, tNew / 1e6, (double) tOld / Math.max(1, tNew));

        // Zusätzlich gegen den Stand vor der Matrizen-Wiederverwendung (Runde 1) bitgenau vergleichen
        for (BedrockCosmeticModel m : loaded) {
            for (int variant = 0; variant < 8; variant++) {
                m.blockbenchAxisFix = (variant & 1) != 0;
                animate(m, variant);
                boolean mirror = (variant & 2) != 0;
                int argb = (variant & 4) != 0 ? 0x80FF8040 : 0xFFFFFFFF;
                MatrixStack.Entry e = entry(variant);
                Recorder a = new Recorder();
                m.render(e, a, 0xF000F0, variant % 3, 3, mirror, argb, null, null);
                Recorder b = new Recorder();
                Round1Render.render(m, e, b, 0xF000F0, variant % 3, 3, mirror, argb, null, null);
                check(a.data.equals(b.data), "round-1 equality variant " + variant);
            }
            m.blockbenchAxisFix = false;
        }

        allocationReport(loaded, entry);
        System.out.println("BEDROCK MODEL RENDER EQUALITY PASSED (" + checks + " checks, " + vertices + " vertices)");
    }

    /**
     * Allokations-Messung (ThreadMXBean, nur dieser Thread): Bytes pro Renderdurchgang aller Modelle, vorher (Runde 1:
     * neue Matrix pro Knochen/gedrehtem Würfel, Map-Iterator pro Würfel), Original und jetzt.
     */
    private static void allocationReport(List<BedrockCosmeticModel> loaded, MatrixStack.Entry entry) {
        java.lang.management.ThreadMXBean base = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(base instanceof com.sun.management.ThreadMXBean tmx) || !tmx.isThreadAllocatedMemorySupported()) {
            System.out.println("Allokations-Messung nicht verfuegbar");
            return;
        }
        tmx.setThreadAllocatedMemoryEnabled(true);
        long tid = Thread.currentThread().threadId();
        Counter sink = new Counter();
        int passes = 2000;
        long[] result = new long[3];
        for (int round = 0; round < 4; round++) { // erste Runden = Aufwärmen (JIT), letzte zählt
            for (int impl = 0; impl < 3; impl++) {
                long before = tmx.getThreadAllocatedBytes(tid);
                for (int i = 0; i < passes; i++) {
                    for (int k = 0, n = loaded.size(); k < n; k++) {
                        BedrockCosmeticModel m = loaded.get(k);
                        switch (impl) {
                            case 0 -> ReferenceRender.render(m, entry, sink, 0xF000F0, 0, 1, false, 0xFFFFFFFF, null, null);
                            case 1 -> Round1Render.render(m, entry, sink, 0xF000F0, 0, 1, false, 0xFFFFFFFF, null, null);
                            default -> m.render(entry, sink, 0xF000F0, 0, 1);
                        }
                    }
                }
                result[impl] = tmx.getThreadAllocatedBytes(tid) - before;
            }
        }
        System.out.printf(Locale.ROOT,
                "Allokation pro Durchgang (alle %d Modelle): Original %.0f B, vorher (Runde 1) %.0f B, jetzt %.0f B%n",
                loaded.size(), result[0] / (double) passes, result[1] / (double) passes, result[2] / (double) passes);
        check(result[2] <= result[1], "neue Implementierung allokiert nicht mehr als vorher");
    }

    /** Stand nach Runde 1 (vor der Matrizen-Wiederverwendung), unverändert übernommen, als zweite Referenz. */
    private static final class Round1Render {
        private static final Vector3f[] CORNERS = new Vector3f[8];
        private static final Vector3f FV0 = new Vector3f(), FV1 = new Vector3f(), FV2 = new Vector3f(), FV3 = new Vector3f();
        private static final Vector3f E1 = new Vector3f(), E2 = new Vector3f(), NORMAL = new Vector3f();
        static {
            for (int i = 0; i < 8; i++) CORNERS[i] = new Vector3f();
        }

        static void render(BedrockCosmeticModel model, MatrixStack.Entry entry, VertexConsumer consumer, int light,
                           int frameIndex, int totalFrames, boolean mirrorX, int argb, String onlyBone,
                           java.util.Set<String> skipBones) {
            Matrix4f rootMat = new Matrix4f().identity();
            for (BedrockCosmeticModel.ModelBone root : model.rootBones) {
                renderBone(model, root, rootMat, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb, onlyBone, skipBones);
            }
        }

        private static void renderBone(BedrockCosmeticModel model, BedrockCosmeticModel.ModelBone bone, Matrix4f parentMat,
                                       MatrixStack.Entry entry, VertexConsumer consumer, int light, int frameIndex,
                                       int totalFrames, boolean mirrorX, int argb, String onlyBone, java.util.Set<String> skipBones) {
            boolean inTree = onlyBone == null || onlyBone.equals(bone.name);
            boolean draw = inTree && (skipBones == null || !skipBones.contains(bone.name));
            String childFilter = inTree ? null : onlyBone;
            Matrix4f boneMat = new Matrix4f(parentMat);
            float px = bone.px, py = bone.py, pz = bone.pz;
            float xzSign = model.blockbenchAxisFix ? -1.0f : 1.0f;
            boneMat.translate(px + bone.animTx, py + bone.animTy, pz + bone.animTz);
            boneMat.rotateZ((float) Math.toRadians(xzSign * (bone.rz + bone.animRz)));
            boneMat.rotateY((float) Math.toRadians(bone.ry + bone.animRy));
            boneMat.rotateX((float) Math.toRadians(xzSign * (bone.rx + bone.animRx)));
            boneMat.translate(-px, -py, -pz);
            if (draw) for (BedrockCosmeticModel.ModelCube cube : bone.cubes) {
                Matrix4f cubeMat = boneMat;
                if (cube.hasRot || cube.hasPivot) {
                    cubeMat = new Matrix4f(boneMat);
                    cubeMat.translate(cube.px, cube.py, cube.pz);
                    if (cube.hasRot) {
                        cubeMat.rotateZ((float) Math.toRadians(xzSign * cube.rz));
                        cubeMat.rotateY((float) Math.toRadians(cube.ry));
                        cubeMat.rotateX((float) Math.toRadians(xzSign * cube.rx));
                    }
                    cubeMat.translate(-cube.px, -cube.py, -cube.pz);
                }
                renderCube(model, cube, cubeMat, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb);
            }
            for (BedrockCosmeticModel.ModelBone child : bone.children) {
                renderBone(model, child, boneMat, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb, childFilter, skipBones);
            }
        }

        private static void renderCube(BedrockCosmeticModel model, BedrockCosmeticModel.ModelCube cube, Matrix4f mat,
                                       MatrixStack.Entry entry, VertexConsumer consumer, int light, int frameIndex,
                                       int totalFrames, boolean mirrorX, int argb) {
            float inf = cube.inflate;
            float x0 = cube.ox - inf, x1 = cube.ox + cube.sx + inf;
            float y0 = cube.oy - inf, y1 = cube.oy + cube.sy + inf;
            float z0 = cube.oz - inf, z1 = cube.oz + cube.sz + inf;
            boolean cornersReady = false;
            for (Map.Entry<String, BedrockCosmeticModel.FaceUV> fe : cube.faces.entrySet()) {
                BedrockCosmeticModel.FaceUV uv = fe.getValue();
                if (uv.uSize == 0 && uv.vSize == 0) continue;
                int i0, i1, i2, i3;
                switch (fe.getKey()) {
                    case "west" -> { i0 = 2; i1 = 0; i2 = 1; i3 = 3; }
                    case "east" -> { i0 = 7; i1 = 5; i2 = 4; i3 = 6; }
                    case "north" -> { i0 = 6; i1 = 4; i2 = 0; i3 = 2; }
                    case "south" -> { i0 = 3; i1 = 1; i2 = 5; i3 = 7; }
                    case "up" -> { i0 = 2; i1 = 3; i2 = 7; i3 = 6; }
                    case "down" -> { i0 = 1; i1 = 0; i2 = 4; i3 = 5; }
                    default -> { continue; }
                }
                if (!cornersReady) {
                    cornersReady = true;
                    for (int c = 0; c < 8; c++) {
                        Vector3f v = CORNERS[c];
                        v.set((c & 4) != 0 ? x1 : x0, (c & 2) != 0 ? y1 : y0, (c & 1) != 0 ? z1 : z0);
                        mat.transformPosition(v);
                        ReferenceRender.toJava(v);
                    }
                }
                Vector3f v0 = FV0.set(CORNERS[i0]), v1 = FV1.set(CORNERS[i1]), v2 = FV2.set(CORNERS[i2]), v3 = FV3.set(CORNERS[i3]);
                if (mirrorX) { v0.x = -v0.x; v1.x = -v1.x; v2.x = -v2.x; v3.x = -v3.x; }
                Vector3f normal = NORMAL.set(E1.set(v1).sub(v0)).cross(E2.set(v3).sub(v0));
                if (mirrorX) normal.negate();
                if (model.fixedNormal != null) normal.set(model.fixedNormal[0], model.fixedNormal[1], model.fixedNormal[2]);
                else if (normal.lengthSquared() > 1e-6f) normal.normalize();
                else normal.set(0, 1, 0);
                float texU1 = uv.u / model.textureWidth;
                float texU2 = (uv.u + uv.uSize) / model.textureWidth;
                float texV1 = (frameIndex + uv.v / model.textureHeight) / (float) totalFrames;
                float texV2 = (frameIndex + (uv.v + uv.vSize) / model.textureHeight) / (float) totalFrames;
                if (mirrorX) {
                    ReferenceRender.emit(consumer, entry, v0, texU1, texV1, light, normal, argb);
                    ReferenceRender.emit(consumer, entry, v3, texU2, texV1, light, normal, argb);
                    ReferenceRender.emit(consumer, entry, v2, texU2, texV2, light, normal, argb);
                    ReferenceRender.emit(consumer, entry, v1, texU1, texV2, light, normal, argb);
                } else {
                    ReferenceRender.emit(consumer, entry, v0, texU1, texV1, light, normal, argb);
                    ReferenceRender.emit(consumer, entry, v1, texU1, texV2, light, normal, argb);
                    ReferenceRender.emit(consumer, entry, v2, texU2, texV2, light, normal, argb);
                    ReferenceRender.emit(consumer, entry, v3, texU2, texV1, light, normal, argb);
                }
            }
        }
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void animate(BedrockCosmeticModel m, int variant) {
        m.resetAllAnimations();
        int i = 0;
        for (String bone : m.bonesByName.keySet()) {
            float k = (i++ % 5) * 7.5f + variant;
            m.setBoneRotation(bone, k, -k * 0.5f, k * 0.25f);
            m.setBoneTranslation(bone, k * 0.01f, -k * 0.02f, k * 0.03f);
        }
    }

    private static MatrixStack.Entry entry(int variant) {
        MatrixStack ms = new MatrixStack();
        ms.translate(0.3f * variant, -1.2f, 0.7f);
        ms.multiply(new org.joml.Quaternionf().rotateXYZ(0.3f + variant * 0.1f, 1.1f, -0.4f));
        ms.scale(0.9375f, 0.9375f, 0.9375f);
        return ms.peek();
    }

    /** Zeichnet alle Werte als Float-Folge auf (Position, Farbe, UV, Overlay, Licht, Normale). */
    private static final class Recorder implements VertexConsumer {
        final List<Float> data = new ArrayList<>();
        long vertices = 0;

        @Override public VertexConsumer vertex(float x, float y, float z) { vertices++; data.add(x); data.add(y); data.add(z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { data.add((float) r); data.add((float) g); data.add((float) b); data.add((float) a); return this; }
        @Override public VertexConsumer color(int argb) { data.add((float) argb); return this; }
        @Override public VertexConsumer texture(float u, float v) { data.add(u); data.add(v); return this; }
        @Override public VertexConsumer overlay(int u, int v) { data.add((float) u); data.add((float) v); return this; }
        @Override public VertexConsumer light(int u, int v) { data.add((float) u); data.add((float) v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { data.add(x); data.add(y); data.add(z); return this; }
        @Override public VertexConsumer lineWidth(float width) { data.add(width); return this; }
    }

    /** Verwirft alles (für die Zeitmessung), zählt nur. */
    private static final class Counter implements VertexConsumer {
        long n;
        @Override public VertexConsumer vertex(float x, float y, float z) { n += Float.floatToRawIntBits(x) & 1; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer color(int argb) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public VertexConsumer lineWidth(float width) { return this; }
    }

    /** Frühere Implementierung von BedrockCosmeticModel.render (unverändert übernommen) als Referenz. */
    private static final class ReferenceRender {
        static void render(BedrockCosmeticModel model, MatrixStack.Entry entry, VertexConsumer consumer, int light,
                           int frameIndex, int totalFrames, boolean mirrorX, int argb, String onlyBone,
                           java.util.Set<String> skipBones) {
            Matrix4f rootMat = new Matrix4f().identity();
            for (BedrockCosmeticModel.ModelBone root : model.rootBones) {
                renderBone(model, root, rootMat, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb, onlyBone, skipBones);
            }
        }

        private static void renderBone(BedrockCosmeticModel model, BedrockCosmeticModel.ModelBone bone, Matrix4f parentMat,
                                       MatrixStack.Entry entry, VertexConsumer consumer, int light, int frameIndex,
                                       int totalFrames, boolean mirrorX, int argb, String onlyBone, java.util.Set<String> skipBones) {
            boolean inTree = onlyBone == null || onlyBone.equals(bone.name);
            boolean draw = inTree && (skipBones == null || !skipBones.contains(bone.name));
            String childFilter = inTree ? null : onlyBone;
            Matrix4f boneMat = new Matrix4f(parentMat);
            float px = bone.px, py = bone.py, pz = bone.pz;
            float xzSign = model.blockbenchAxisFix ? -1.0f : 1.0f;
            boneMat.translate(px + bone.animTx, py + bone.animTy, pz + bone.animTz);
            boneMat.rotateZ((float) Math.toRadians(xzSign * (bone.rz + bone.animRz)));
            boneMat.rotateY((float) Math.toRadians(bone.ry + bone.animRy));
            boneMat.rotateX((float) Math.toRadians(xzSign * (bone.rx + bone.animRx)));
            boneMat.translate(-px, -py, -pz);
            if (draw) for (BedrockCosmeticModel.ModelCube cube : bone.cubes) {
                Matrix4f cubeMat = boneMat;
                if (cube.hasRot || cube.hasPivot) {
                    cubeMat = new Matrix4f(boneMat);
                    cubeMat.translate(cube.px, cube.py, cube.pz);
                    if (cube.hasRot) {
                        cubeMat.rotateZ((float) Math.toRadians(xzSign * cube.rz));
                        cubeMat.rotateY((float) Math.toRadians(cube.ry));
                        cubeMat.rotateX((float) Math.toRadians(xzSign * cube.rx));
                    }
                    cubeMat.translate(-cube.px, -cube.py, -cube.pz);
                }
                renderCube(model, cube, cubeMat, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb);
            }
            for (BedrockCosmeticModel.ModelBone child : bone.children) {
                renderBone(model, child, boneMat, entry, consumer, light, frameIndex, totalFrames, mirrorX, argb, childFilter, skipBones);
            }
        }

        private static void renderCube(BedrockCosmeticModel model, BedrockCosmeticModel.ModelCube cube, Matrix4f mat,
                                       MatrixStack.Entry entry, VertexConsumer consumer, int light, int frameIndex,
                                       int totalFrames, boolean mirrorX, int argb) {
            float inf = cube.inflate;
            float x0 = cube.ox - inf, x1 = cube.ox + cube.sx + inf;
            float y0 = cube.oy - inf, y1 = cube.oy + cube.sy + inf;
            float z0 = cube.oz - inf, z1 = cube.oz + cube.sz + inf;
            for (Map.Entry<String, BedrockCosmeticModel.FaceUV> fe : cube.faces.entrySet()) {
                BedrockCosmeticModel.FaceUV uv = fe.getValue();
                if (uv.uSize == 0 && uv.vSize == 0) continue;
                Vector3f v0, v1, v2, v3;
                switch (fe.getKey()) {
                    case "west" -> { v0 = new Vector3f(x0, y1, z0); v1 = new Vector3f(x0, y0, z0); v2 = new Vector3f(x0, y0, z1); v3 = new Vector3f(x0, y1, z1); }
                    case "east" -> { v0 = new Vector3f(x1, y1, z1); v1 = new Vector3f(x1, y0, z1); v2 = new Vector3f(x1, y0, z0); v3 = new Vector3f(x1, y1, z0); }
                    case "north" -> { v0 = new Vector3f(x1, y1, z0); v1 = new Vector3f(x1, y0, z0); v2 = new Vector3f(x0, y0, z0); v3 = new Vector3f(x0, y1, z0); }
                    case "south" -> { v0 = new Vector3f(x0, y1, z1); v1 = new Vector3f(x0, y0, z1); v2 = new Vector3f(x1, y0, z1); v3 = new Vector3f(x1, y1, z1); }
                    case "up" -> { v0 = new Vector3f(x0, y1, z0); v1 = new Vector3f(x0, y1, z1); v2 = new Vector3f(x1, y1, z1); v3 = new Vector3f(x1, y1, z0); }
                    case "down" -> { v0 = new Vector3f(x0, y0, z1); v1 = new Vector3f(x0, y0, z0); v2 = new Vector3f(x1, y0, z0); v3 = new Vector3f(x1, y0, z1); }
                    default -> { continue; }
                }
                mat.transformPosition(v0); mat.transformPosition(v1); mat.transformPosition(v2); mat.transformPosition(v3);
                toJava(v0); toJava(v1); toJava(v2); toJava(v3);
                if (mirrorX) { v0.x = -v0.x; v1.x = -v1.x; v2.x = -v2.x; v3.x = -v3.x; }
                Vector3f e1 = new Vector3f(v1).sub(v0);
                Vector3f e2 = new Vector3f(v3).sub(v0);
                Vector3f normal = new Vector3f(e1).cross(e2);
                if (mirrorX) normal.negate();
                if (model.fixedNormal != null) normal.set(model.fixedNormal[0], model.fixedNormal[1], model.fixedNormal[2]);
                else if (normal.lengthSquared() > 1e-6f) normal.normalize();
                else normal.set(0, 1, 0);
                float texU1 = uv.u / model.textureWidth;
                float texU2 = (uv.u + uv.uSize) / model.textureWidth;
                float rawV1 = uv.v / model.textureHeight;
                float rawV2 = (uv.v + uv.vSize) / model.textureHeight;
                float texV1 = (frameIndex + rawV1) / (float) totalFrames;
                float texV2 = (frameIndex + rawV2) / (float) totalFrames;
                if (mirrorX) {
                    emit(consumer, entry, v0, texU1, texV1, light, normal, argb);
                    emit(consumer, entry, v3, texU2, texV1, light, normal, argb);
                    emit(consumer, entry, v2, texU2, texV2, light, normal, argb);
                    emit(consumer, entry, v1, texU1, texV2, light, normal, argb);
                } else {
                    emit(consumer, entry, v0, texU1, texV1, light, normal, argb);
                    emit(consumer, entry, v1, texU1, texV2, light, normal, argb);
                    emit(consumer, entry, v2, texU2, texV2, light, normal, argb);
                    emit(consumer, entry, v3, texU2, texV1, light, normal, argb);
                }
            }
        }

        private static void toJava(Vector3f v) {
            v.x = v.x * 0.0625f;
            v.y = (24.0f - v.y) * 0.0625f;
            v.z = v.z * 0.0625f;
        }

        private static void emit(VertexConsumer consumer, MatrixStack.Entry entry, Vector3f p, float u, float v,
                                 int light, Vector3f n, int argb) {
            consumer.vertex(entry, p.x, p.y, p.z)
                    .color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                    .texture(u, v)
                    .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, n.x, n.y, n.z);
        }
    }
}

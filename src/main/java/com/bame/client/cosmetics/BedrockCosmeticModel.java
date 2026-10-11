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
            for (ModelBone child : children) {
                child.resetAnimation();
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
        for (ModelBone r : rootBones) {
            r.resetAnimation();
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

    public void render(MatrixStack.Entry entry, VertexConsumer consumer, int light,
                       int frameIndex, int totalFrames) {
        render(entry, consumer, light, frameIndex, totalFrames, false);
    }

    public void render(MatrixStack.Entry entry, VertexConsumer consumer, int light,
                       int frameIndex, int totalFrames, boolean mirrorX) {
        Matrix4f rootMat = new Matrix4f().identity();
        for (ModelBone root : rootBones) {
            renderBone(root, rootMat, entry, consumer, light, frameIndex, totalFrames, mirrorX);
        }
    }

    private void renderBone(ModelBone bone, Matrix4f parentMat, MatrixStack.Entry entry,
                            VertexConsumer consumer, int light, int frameIndex, int totalFrames, boolean mirrorX) {
        Matrix4f boneMat = new Matrix4f(parentMat);

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

        // Render cubes of this bone
        for (ModelCube cube : bone.cubes) {
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
            renderCube(cube, cubeMat, entry, consumer, light, frameIndex, totalFrames, mirrorX);
        }

        // Render children
        for (ModelBone child : bone.children) {
            renderBone(child, boneMat, entry, consumer, light, frameIndex, totalFrames, mirrorX);
        }
    }

    private void renderCube(ModelCube cube, Matrix4f mat, MatrixStack.Entry entry,
                            VertexConsumer consumer, int light, int frameIndex, int totalFrames, boolean mirrorX) {
        float inf = cube.inflate;
        float x0 = cube.ox - inf;
        float x1 = cube.ox + cube.sx + inf;
        float y0 = cube.oy - inf;
        float y1 = cube.oy + cube.sy + inf;
        float z0 = cube.oz - inf;
        float z1 = cube.oz + cube.sz + inf;

        for (Map.Entry<String, FaceUV> fe : cube.faces.entrySet()) {
            FaceUV uv = fe.getValue();
            if (uv.uSize == 0 && uv.vSize == 0) continue;

            String face = fe.getKey();
            Vector3f v0, v1, v2, v3;

            switch (face) {
                case "west":
                    v0 = new Vector3f(x0, y1, z0);
                    v1 = new Vector3f(x0, y0, z0);
                    v2 = new Vector3f(x0, y0, z1);
                    v3 = new Vector3f(x0, y1, z1);
                    break;
                case "east":
                    v0 = new Vector3f(x1, y1, z1);
                    v1 = new Vector3f(x1, y0, z1);
                    v2 = new Vector3f(x1, y0, z0);
                    v3 = new Vector3f(x1, y1, z0);
                    break;
                case "north":
                    v0 = new Vector3f(x1, y1, z0);
                    v1 = new Vector3f(x1, y0, z0);
                    v2 = new Vector3f(x0, y0, z0);
                    v3 = new Vector3f(x0, y1, z0);
                    break;
                case "south":
                    v0 = new Vector3f(x0, y1, z1);
                    v1 = new Vector3f(x0, y0, z1);
                    v2 = new Vector3f(x1, y0, z1);
                    v3 = new Vector3f(x1, y1, z1);
                    break;
                case "up":
                    v0 = new Vector3f(x0, y1, z0);
                    v1 = new Vector3f(x0, y1, z1);
                    v2 = new Vector3f(x1, y1, z1);
                    v3 = new Vector3f(x1, y1, z0);
                    break;
                case "down":
                    v0 = new Vector3f(x0, y0, z1);
                    v1 = new Vector3f(x0, y0, z0);
                    v2 = new Vector3f(x1, y0, z0);
                    v3 = new Vector3f(x1, y0, z1);
                    break;
                default:
                    continue;
            }

            // Transform vertices by bone/cube matrix in Bedrock space
            mat.transformPosition(v0);
            mat.transformPosition(v1);
            mat.transformPosition(v2);
            mat.transformPosition(v3);

            // Convert Bedrock coordinate space to Java model space:
            // X_java = X_bedrock * 0.0625
            // Y_java = (24.0 - Y_bedrock) * 0.0625
            // Z_java = Z_bedrock * 0.0625
            toJava(v0);
            toJava(v1);
            toJava(v2);
            toJava(v3);

            if (mirrorX) {
                v0.x = -v0.x;
                v1.x = -v1.x;
                v2.x = -v2.x;
                v3.x = -v3.x;
            }

            // Calculate face normal from transformed Java vertices
            Vector3f e1 = new Vector3f(v1).sub(v0);
            Vector3f e2 = new Vector3f(v3).sub(v0);
            Vector3f normal = new Vector3f(e1).cross(e2);
            if (mirrorX) {
                normal.negate();
            }
            if (normal.lengthSquared() > 1e-6f) {
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
                emitVertex(consumer, entry, v0.x, v0.y, v0.z, texU1, texV1, light, normal.x, normal.y, normal.z);
                emitVertex(consumer, entry, v3.x, v3.y, v3.z, texU2, texV1, light, normal.x, normal.y, normal.z);
                emitVertex(consumer, entry, v2.x, v2.y, v2.z, texU2, texV2, light, normal.x, normal.y, normal.z);
                emitVertex(consumer, entry, v1.x, v1.y, v1.z, texU1, texV2, light, normal.x, normal.y, normal.z);
            } else {
                emitVertex(consumer, entry, v0.x, v0.y, v0.z, texU1, texV1, light, normal.x, normal.y, normal.z);
                emitVertex(consumer, entry, v1.x, v1.y, v1.z, texU1, texV2, light, normal.x, normal.y, normal.z);
                emitVertex(consumer, entry, v2.x, v2.y, v2.z, texU2, texV2, light, normal.x, normal.y, normal.z);
                emitVertex(consumer, entry, v3.x, v3.y, v3.z, texU2, texV1, light, normal.x, normal.y, normal.z);
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
                                   int light, float nx, float ny, float nz) {
        consumer.vertex(entry, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
    }
}

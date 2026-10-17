package com.bame.client.render;

import com.bame.client.module.CustomFireModule;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.InputStream;
import java.util.Optional;

/**
 * Zeichnet das Feuer des Moduls {@link CustomFireModule}. Grundlage ist eine graue Kopie der Vanilla-Feuertexturen
 * (aus dem aktiven Resourcepack gelesen, nicht mitgeliefert): hellster Farbkanal als Grauwert, Alpha unverändert.
 * Mit der Vertex-Farbe multipliziert ergibt das kräftiges Feuer in jeder Farbe. Die Texturen sind Bildstreifen mit
 * quadratischen Animations-Frames; ein Frame pro Tick wie Vanilla. Nur Render-Thread.
 */
public final class CustomFireRenderer {
    private CustomFireRenderer() {}

    private static final GrayFire FIRE_0 = new GrayFire(Identifier.ofVanilla("textures/block/fire_0.png"), Identifier.of("bameclient", "dynamic/gray_fire_0"));
    private static final GrayFire FIRE_1 = new GrayFire(Identifier.ofVanilla("textures/block/fire_1.png"), Identifier.of("bameclient", "dynamic/gray_fire_1"));

    /** Ersatz für InGameOverlayRenderer.renderFireOverlay. Liefert false, wenn die Textur fehlt (dann Vanilla zeichnen). */
    public static boolean renderOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (CustomFireModule.hideOverlay(client)) return true;
        GrayFire fire = FIRE_1.ready();
        if (fire == null) return false;
        float minV = fire.minV(), maxV = fire.maxV();
        int argb = (Math.clamp(Math.round(CustomFireModule.opacity * 255.0f), 10, 255) << 24) | CustomFireModule.rgb();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayers.fireScreenEffect(fire.id));
        float yOffset = CustomFireModule.overlayYOffset();
        for (int k = 0; k < 2; k++) {
            matrices.push();
            matrices.translate(-(k * 2 - 1) * 0.24F, yOffset, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((k * 2 - 1) * 10.0F));
            Matrix4f m = matrices.peek().getPositionMatrix();
            vc.vertex(m, -0.5F, -0.5F, -0.5F).texture(1.0F, maxV).color(argb);
            vc.vertex(m, 0.5F, -0.5F, -0.5F).texture(0.0F, maxV).color(argb);
            vc.vertex(m, 0.5F, 0.5F, -0.5F).texture(0.0F, minV).color(argb);
            vc.vertex(m, -0.5F, 0.5F, -0.5F).texture(1.0F, minV).color(argb);
            matrices.pop();
        }
        return true;
    }

    /** Ersatz für FireCommandRenderer.render (Feuer auf Entities), gleiche Geometrie wie Vanilla. */
    public static boolean renderEntityFire(MatrixStack.Entry entry, VertexConsumerProvider vertexConsumers, EntityRenderState state, Quaternionf rotation) {
        GrayFire f0 = FIRE_0.ready();
        GrayFire f1 = FIRE_1.ready();
        if (f0 == null || f1 == null) return false;
        int argb = 0xFF000000 | CustomFireModule.rgb();
        float scale = state.width * 1.4F;
        entry.scale(scale, scale, scale);
        float halfW = 0.5F;
        float remaining = state.height / scale;
        float yOff = 0.0F;
        entry.rotate(rotation);
        entry.translate(0.0F, 0.0F, 0.3F - (int) remaining * 0.02F);
        float z = 0.0F;
        int layer = 0;
        VertexConsumer vc0 = vertexConsumers.getBuffer(RenderLayers.entityCutout(f0.id));
        VertexConsumer vc1 = vertexConsumers.getBuffer(RenderLayers.entityCutout(f1.id));
        while (remaining > 0.0F) {
            GrayFire fire = layer % 2 == 0 ? f0 : f1;
            VertexConsumer vc = layer % 2 == 0 ? vc0 : vc1;
            float u0 = 0.0F, u1 = 1.0F, v0 = fire.minV(), v1 = fire.maxV();
            if (layer / 2 % 2 == 0) { float t = u1; u1 = u0; u0 = t; }
            vertex(entry, vc, -halfW, -yOff, z, u1, v1, argb);
            vertex(entry, vc, halfW, -yOff, z, u0, v1, argb);
            vertex(entry, vc, halfW, 1.4F - yOff, z, u0, v0, argb);
            vertex(entry, vc, -halfW, 1.4F - yOff, z, u1, v0, argb);
            remaining -= 0.45F;
            yOff -= 0.45F;
            halfW *= 0.9F;
            z -= 0.03F;
            layer++;
        }
        return true;
    }

    private static void vertex(MatrixStack.Entry entry, VertexConsumer vc, float x, float y, float z, float u, float v, int argb) {
        vc.vertex(entry, x, y, z).color(argb).texture(u, v).overlay(0, 10)
                .light(LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE).normal(entry, 0.0F, 1.0F, 0.0F);
    }

    /** Graue Kopie einer Vanilla-Feuertextur; wird neu gebaut, wenn ein anderes Resourcepack die Textur liefert. */
    private static final class GrayFire {
        private static final long PACK_CHECK_MS = 2000L;
        private final Identifier source;
        final Identifier id;
        private NativeImageBackedTexture texture;
        private GpuTexture gpuTexture;
        private TextureManager owner;
        private String packId;
        private long lastPackCheck;
        private boolean failed;
        private int frames = 1;

        GrayFire(Identifier source, Identifier id) {
            this.source = source;
            this.id = id;
        }

        float minV() { return frame() / (float) frames; }
        float maxV() { return (frame() + 1) / (float) frames; }

        private int frame() {
            return frames <= 1 ? 0 : (int) ((System.currentTimeMillis() / 50L) % frames);
        }

        /** Textur bereit (oder neu gebaut), sonst null. */
        GrayFire ready() {
            MinecraftClient client = MinecraftClient.getInstance();
            TextureManager tm = client.getTextureManager();
            long now = System.currentTimeMillis();
            boolean stale = texture == null || owner != tm || gpuTexture == null || gpuTexture.isClosed();
            if (!stale && now - lastPackCheck > PACK_CHECK_MS) {
                lastPackCheck = now;
                Optional<Resource> res = client.getResourceManager().getResource(source);
                String pack = res.map(Resource::getPackId).orElse(null);
                stale = pack != null && !pack.equals(packId);
            }
            if (stale) build(client, tm);
            return texture != null && !failed ? this : null;
        }

        private void build(MinecraftClient client, TextureManager tm) {
            lastPackCheck = System.currentTimeMillis();
            Optional<Resource> res = client.getResourceManager().getResource(source);
            if (res.isEmpty()) { failed = true; return; }
            try (InputStream in = res.get().getInputStream()) {
                NativeImage image = NativeImage.read(in);
                int w = image.getWidth(), h = image.getHeight();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int c = image.getColorArgb(x, y);
                        int v = Math.max(Math.max((c >> 16) & 255, (c >> 8) & 255), c & 255);
                        image.setColorArgb(x, y, (c & 0xFF000000) | (v << 16) | (v << 8) | v);
                    }
                }
                frames = w > 0 && h % w == 0 ? Math.max(1, h / w) : 1;
                // registerTexture schließt die vorherige Textur unter derselben Id selbst
                texture = new NativeImageBackedTexture(id::toString, image);
                tm.registerTexture(id, texture);
                gpuTexture = texture.getGlTexture();
                owner = tm;
                packId = res.get().getPackId();
                failed = false;
            } catch (Exception e) {
                failed = true;
            }
        }
    }
}

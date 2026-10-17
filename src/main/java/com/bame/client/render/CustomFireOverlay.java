package com.bame.client.render;

import com.bame.client.module.CustomFireModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Feuer am Bildschirmrand von Custom Fire: dieselben zwei Flächen wie Vanilla, aber mit den eigenen Flammen aus
 * {@link FlameGenerator} (mit weichen Kanten, Center Clear und Bottom Fade). Die Animation läuft mit 20 Bildern pro
 * Sekunde; das aktuelle Bild wird in eine kleine dynamische Textur kopiert. Nur Render-Thread.
 */
public final class CustomFireOverlay {
    private CustomFireOverlay() {}

    private static final Identifier ID = Identifier.of("bameclient", "dynamic/custom_flame_overlay");

    private static int[][] frames;
    private static String framesKey;
    private static String pendingKey;
    private static CompletableFuture<int[][]> pending;
    private static NativeImageBackedTexture texture;
    private static TextureManager owner;
    private static int shownFrame = -1;

    /** Ersatz für das Vanilla-Feuer am Bildschirm. Liefert false, solange noch keine Flammen erzeugt sind. */
    public static boolean render(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        if (!ensureFrames()) return false;
        int frame = (int) ((System.currentTimeMillis() / 50L) % FlameGenerator.FRAMES);
        Identifier id = upload(frame);
        if (id == null) return false;
        int argb = (Math.clamp(Math.round(CustomFireModule.opacity * 255.0f), 0, 255) << 24) | 0xFFFFFF;
        if ((argb >>> 24) == 0) return true;
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayers.fireScreenEffect(id));
        float yOffset = CustomFireModule.overlayYOffset();
        for (int k = 0; k < 2; k++) {
            matrices.push();
            matrices.translate(-(k * 2 - 1) * 0.24F, yOffset, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((k * 2 - 1) * 10.0F));
            Matrix4f m = matrices.peek().getPositionMatrix();
            // linke Fläche gespiegelt wie bei Vanilla
            float u0 = k == 0 ? 1.0F : 0.0F, u1 = 1.0F - u0;
            vc.vertex(m, -0.5F, -0.5F, -0.5F).texture(u0, 1.0F).color(argb);
            vc.vertex(m, 0.5F, -0.5F, -0.5F).texture(u1, 1.0F).color(argb);
            vc.vertex(m, 0.5F, 0.5F, -0.5F).texture(u1, 0.0F).color(argb);
            vc.vertex(m, -0.5F, 0.5F, -0.5F).texture(u0, 0.0F).color(argb);
            matrices.pop();
        }
        return true;
    }

    /** Flammen für die aktuellen Einstellungen bereitstellen (im Hintergrund erzeugt). */
    private static boolean ensureFrames() {
        String key = FlameGenerator.Params.current(false).toString();
        if (pending != null && pending.isDone()) {
            try {
                int[][] result = pending.join();
                if (result != null) {
                    frames = result;
                    framesKey = pendingKey;
                    shownFrame = -1;
                }
            } catch (RuntimeException ignored) {
            }
            pending = null;
            pendingKey = null;
        }
        if (!Objects.equals(key, framesKey) && !Objects.equals(key, pendingKey) && pending == null) {
            FlameGenerator.Params params = FlameGenerator.Params.current(false);
            pendingKey = key;
            pending = CompletableFuture.supplyAsync(() -> FlameGenerator.generate(params));
        }
        return frames != null;
    }

    private static Identifier upload(int frame) {
        TextureManager manager = MinecraftClient.getInstance().getTextureManager();
        if (texture == null || owner != manager || texture.getGlTexture().isClosed()) {
            texture = new NativeImageBackedTexture(ID::toString, FlameGenerator.SIZE, FlameGenerator.SIZE, false);
            manager.registerTexture(ID, texture);
            owner = manager;
            shownFrame = -1;
        }
        if (frame != shownFrame) {
            NativeImage image = texture.getImage();
            if (image == null) return null;
            int[] pixels = frames[frame];
            for (int y = 0; y < FlameGenerator.SIZE; y++) {
                for (int x = 0; x < FlameGenerator.SIZE; x++) image.setColorArgb(x, y, pixels[y * FlameGenerator.SIZE + x]);
            }
            texture.upload();
            shownFrame = frame;
        }
        return ID;
    }
}

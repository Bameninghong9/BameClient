package com.bame.client.render;

import com.bame.client.module.ShaderFogModule;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;

/**
 * Zeichnet den Himmel von {@link ShaderFogModule} in zwei Schritten, damit er kaum Leistung kostet:
 * <ol>
 *   <li><b>Backen</b> (höchstens 30-mal pro Sekunde und bei geänderten Einstellungen): die aufwendigen Wolken, Lichtbahnen
 *   bzw. der Strudel werden für alle Blickrichtungen in eine Panorama-Textur (2048×1024, Längen-/Breitengrad) gezeichnet
 *   ({@code shaders/core/shader_fog_bake}). Das Rauschen kommt aus einer kleinen Rauschtextur (ein Texturzugriff
 *   statt acht Zufallsberechnungen).</li>
 *   <li><b>Zusammensetzen</b> (jedes Bild): ein Würfel um die Kamera liest die Panorama-Textur je Blickrichtung; Sterne,
 *   Sternschnuppen und der Ringplanet kommen scharf pro Pixel dazu ({@code shaders/core/shader_fog}).</li>
 * </ol>
 * Beim Umdrehen muss nichts neu gebacken werden. Lässt sich ein Shader nicht kompilieren, bleibt der Vanilla-Himmel.
 * Nur Render-Thread.
 */
public final class ShaderFogRenderer {
    private ShaderFogRenderer() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Caeser Shader Fog");

    private static final int BAKE_WIDTH = 2048;
    private static final int BAKE_HEIGHT = 1024;
    private static final long BAKE_INTERVAL_NS = 1_000_000_000L / 30L;
    private static final int NOISE_SIZE = 256;

    /** Pro Bild: Panorama-Textur lesen und Sterne/Planet ergänzen. */
    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET)
            .withLocation(Identifier.of("bameclient", "pipeline/shader_fog"))
            .withVertexShader(Identifier.of("bameclient", "core/shader_fog"))
            .withFragmentShader(Identifier.of("bameclient", "core/shader_fog"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS)
            .build();

    /** Panorama backen: Vollbild-Rechteck in die Panorama-Textur, ohne Tiefe und ohne Mischen. */
    public static final RenderPipeline BAKE_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("bameclient", "pipeline/shader_fog_bake"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withVertexShader(Identifier.of("bameclient", "core/shader_fog_bake"))
            .withFragmentShader(Identifier.of("bameclient", "core/shader_fog_bake"))
            .withSampler("Sampler1")
            .withoutBlend()
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS)
            .build();

    private static final float D = 100.0F;
    private static GpuBuffer cube;
    private static GpuBuffer quad;
    private static NativeImageBackedTexture noise;
    private static GpuTexture bakeTexture;
    private static GpuTextureView bakeView;
    private static boolean warned;

    /** Animationszeit (Sekunden × Geschwindigkeit), läuft weich weiter, auch wenn die Geschwindigkeit geändert wird. */
    private static double animTime;
    private static long lastFrameNs;
    private static long lastBakeNs;
    private static String bakedKey;

    /** Nach der Himmelsscheibe: den Shader-Himmel über den ganzen Himmel. {@code vanillaSkyColor}: für Tag/Nacht. */
    public static void drawSky(ShaderFogModule.Mode mode, int vanillaSkyColor) {
        try {
            GpuDevice device = RenderSystem.getDevice();
            if (!device.precompilePipeline(PIPELINE, null).isValid() || !device.precompilePipeline(BAKE_PIPELINE, null).isValid()) {
                if (!warned) LOGGER.warn("Shader Fog: Himmels-Shader konnte nicht kompiliert werden, Vanilla-Himmel bleibt");
                warned = true;
                return;
            }
            ensureResources(device);

            long now = System.nanoTime();
            float dt = lastFrameNs == 0L ? 0.0F : Math.min(0.25F, (now - lastFrameNs) / 1.0e9F);
            lastFrameNs = now;
            animTime = (animTime + dt * Math.max(0.0F, ShaderFogModule.speed)) % 100_000.0;

            int rgb = ShaderFogModule.rgb();
            Vector4f color = new Vector4f(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F,
                    Math.clamp(ShaderFogModule.alpha, 0.0F, 1.0F));
            Matrix4f params = new Matrix4f()
                    .m00(ShaderFogModule.speed).m01(ShaderFogModule.scale).m02(ShaderFogModule.intensity).m03(ShaderFogModule.planetSize)
                    .m10(ShaderFogModule.colorFog ? 1.0F : 0.0F).m11(0.0F).m12(0.0F).m13(0.0F)
                    .m20(0.0F).m21(0.0F).m22(0.0F).m23(0.0F)
                    .m30(0.0F).m31(0.0F).m32(0.0F).m33(0.0F);

            String key = mode + "|" + rgb + "|" + ShaderFogModule.scale + "|" + ShaderFogModule.intensity;
            boolean animated = ShaderFogModule.speed > 0.0F || ShaderFogModule.chroma;
            if (!Objects.equals(key, bakedKey) || (animated && now - lastBakeNs >= BAKE_INTERVAL_NS)) {
                bake(device, mode, color, params);
                bakedKey = key;
                lastBakeNs = now;
            }

            int vMax = Math.max(Math.max((vanillaSkyColor >> 16) & 255, (vanillaSkyColor >> 8) & 255), vanillaSkyColor & 255);
            float day = ShaderFogModule.dayNightCycle ? Math.clamp(vMax / 255.0F, 0.0F, 1.0F) : 1.0F;
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                    .write(RenderSystem.getModelViewMatrix(), color, new Vector3f((float) animTime, day, mode.ordinal()), params);
            RenderSystem.ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
            GpuBuffer indexBuffer = indices.getIndexBuffer(36);
            GpuTextureView colorTarget = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
            GpuTextureView depthTarget = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
            GpuSampler panorama = RenderSystem.getSamplerCache().get(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
            GpuSampler noiseSampler = RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR, false);
            GpuTimings.begin("shader_fog_sky");
            try (RenderPass pass = device.createCommandEncoder()
                    .createRenderPass(() -> "Shader fog sky", colorTarget, OptionalInt.empty(), depthTarget, OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.bindTexture("Sampler0", bakeView, panorama);
                pass.bindTexture("Sampler1", noise.getGlTextureView(), noiseSampler);
                pass.setVertexBuffer(0, cube);
                pass.setIndexBuffer(indexBuffer, indices.getIndexType());
                pass.drawIndexed(0, 0, 36, 1);
            }
            GpuTimings.end("shader_fog_sky");
        } catch (RuntimeException e) {
            if (!warned) LOGGER.warn("Shader Fog: Himmel konnte nicht gezeichnet werden", e);
            warned = true;
        }
    }

    /** Wolken, Lichtbahnen bzw. Strudel für alle Blickrichtungen in die Panorama-Textur zeichnen. */
    private static void bake(GpuDevice device, ShaderFogModule.Mode mode, Vector4f color, Matrix4f params) {
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(new Matrix4f(), color, new Vector3f((float) animTime, 0.0F, mode.ordinal()), params);
        RenderSystem.ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        GpuBuffer indexBuffer = indices.getIndexBuffer(6);
        GpuTimings.begin("shader_fog_bake");
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Shader fog bake", bakeView, OptionalInt.empty())) {
            pass.setPipeline(BAKE_PIPELINE);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler1", noise.getGlTextureView(), RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR, false));
            pass.setVertexBuffer(0, quad);
            pass.setIndexBuffer(indexBuffer, indices.getIndexType());
            pass.drawIndexed(0, 0, 6, 1);
        }
        GpuTimings.end("shader_fog_bake");
    }

    private static void ensureResources(GpuDevice device) {
        if (cube == null) cube = buildCube();
        if (quad == null) quad = buildQuad();
        if (noise == null || noise.getGlTexture().isClosed()) noise = new NativeImageBackedTexture(() -> "Shader fog noise", noiseImage());
        if (bakeTexture == null || bakeTexture.isClosed()) {
            bakeTexture = device.createTexture(() -> "Shader fog panorama", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, BAKE_WIDTH, BAKE_HEIGHT, 1, 1);
            bakeView = device.createTextureView(bakeTexture);
            bakedKey = null;
        }
    }

    /**
     * Rauschtextur für 3D-Wertrauschen mit einem Texturzugriff: R = Rauschen A in Ebene z, G = dasselbe Rauschen um
     * (37, 17) verschoben (Ebene z+1); B/A dasselbe für ein zweites, unabhängiges Rauschen.
     */
    private static NativeImage noiseImage() {
        int n = NOISE_SIZE;
        Random random = new Random(424242L);
        int[] a = new int[n * n], b = new int[n * n];
        for (int i = 0; i < n * n; i++) {
            a[i] = random.nextInt(256);
            b[i] = random.nextInt(256);
        }
        NativeImage image = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int shifted = ((y + 17) & (n - 1)) * n + ((x + 37) & (n - 1));
                int i = y * n + x;
                image.setColorArgb(x, y, (b[shifted] << 24) | (a[i] << 16) | (a[shifted] << 8) | b[i]);
            }
        }
        return image;
    }

    /** Würfel mit Kantenlänge 200 um die Kamera (von innen sichtbar, ohne Rückseiten-Abschnitt). */
    private static GpuBuffer buildCube() {
        float[][][] faces = {
                {{-D, D, -D}, {D, D, -D}, {D, D, D}, {-D, D, D}},
                {{-D, -D, -D}, {-D, -D, D}, {D, -D, D}, {D, -D, -D}},
                {{-D, -D, -D}, {D, -D, -D}, {D, D, -D}, {-D, D, -D}},
                {{-D, -D, D}, {-D, D, D}, {D, D, D}, {D, -D, D}},
                {{-D, -D, -D}, {-D, D, -D}, {-D, D, D}, {-D, -D, D}},
                {{D, -D, -D}, {D, -D, D}, {D, D, D}, {D, D, -D}},
        };
        try (BufferAllocator allocator = BufferAllocator.fixedSized(24 * VertexFormats.POSITION.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            for (float[][] face : faces) {
                for (float[] p : face) b.vertex(p[0], p[1], p[2]);
            }
            try (BuiltBuffer built = b.end()) {
                return RenderSystem.getDevice().createBuffer(() -> "Shader fog cube", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        }
    }

    /** Vollbild-Rechteck in Bildschirmkoordinaten (-1..1). */
    private static GpuBuffer buildQuad() {
        try (BufferAllocator allocator = BufferAllocator.fixedSized(4 * VertexFormats.POSITION.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            b.vertex(-1.0F, -1.0F, 0.0F);
            b.vertex(1.0F, -1.0F, 0.0F);
            b.vertex(1.0F, 1.0F, 0.0F);
            b.vertex(-1.0F, 1.0F, 0.0F);
            try (BuiltBuffer built = b.end()) {
                return RenderSystem.getDevice().createBuffer(() -> "Shader fog quad", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        }
    }
}

package com.bame.client.render;

import com.bame.client.module.ShaderFogModule;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Zeichnet den Himmel von {@link ShaderFogModule} mit einem eigenen Shader
 * ({@code assets/bameclient/shaders/core/shader_fog.vsh/.fsh}): ein Würfel um die Kamera, jedes Pixel berechnet aus
 * seiner Blickrichtung Nebel, Lichtbahnen, Sterne oder den Ringplaneten, animiert über die Zeit. Die Einstellungen
 * gehen über die Uniforms von {@code DynamicTransforms}: ColorModulator = Farbe und Deckkraft, ModelOffset = (Zeit,
 * Tageshelligkeit, Modus), TextureMat = weitere Werte. Lässt sich der Shader nicht kompilieren, wird nichts gezeichnet
 * (Vanilla-Himmel). Nur Render-Thread.
 */
public final class ShaderFogRenderer {
    private ShaderFogRenderer() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Caeser Shader Fog");

    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET)
            .withLocation(Identifier.of("bameclient", "pipeline/shader_fog"))
            .withVertexShader(Identifier.of("bameclient", "core/shader_fog"))
            .withFragmentShader(Identifier.of("bameclient", "core/shader_fog"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS)
            .build();

    private static final float D = 100.0F;
    private static GpuBuffer cube;
    private static boolean warned;

    /** Nach der Himmelsscheibe: den Shader-Himmel über den ganzen Himmel. {@code vanillaSkyColor}: für Tag/Nacht. */
    public static void drawSky(ShaderFogModule.Mode mode, int vanillaSkyColor) {
        try {
            if (!RenderSystem.getDevice().precompilePipeline(PIPELINE, null).isValid()) {
                if (!warned) LOGGER.warn("Shader Fog: Himmels-Shader konnte nicht kompiliert werden, Vanilla-Himmel bleibt");
                warned = true;
                return;
            }
            if (cube == null) cube = buildCube();
            int rgb = ShaderFogModule.rgb();
            Vector4f color = new Vector4f(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F,
                    Math.clamp(ShaderFogModule.alpha, 0.0F, 1.0F));
            float time = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
            int vMax = Math.max(Math.max((vanillaSkyColor >> 16) & 255, (vanillaSkyColor >> 8) & 255), vanillaSkyColor & 255);
            float day = ShaderFogModule.dayNightCycle ? Math.clamp(vMax / 255.0F, 0.0F, 1.0F) : 1.0F;
            Matrix4f params = new Matrix4f()
                    .m00(ShaderFogModule.speed).m01(ShaderFogModule.scale).m02(ShaderFogModule.intensity).m03(ShaderFogModule.planetSize)
                    .m10(ShaderFogModule.colorFog ? 1.0F : 0.0F).m11(0.0F).m12(0.0F).m13(0.0F)
                    .m20(0.0F).m21(0.0F).m22(0.0F).m23(0.0F)
                    .m30(0.0F).m31(0.0F).m32(0.0F).m33(0.0F);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                    .write(RenderSystem.getModelViewMatrix(), color, new Vector3f(time, day, mode.ordinal()), params);
            RenderSystem.ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
            GpuBuffer indexBuffer = indices.getIndexBuffer(36);
            GpuTextureView colorTarget = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
            GpuTextureView depthTarget = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "Shader fog sky", colorTarget, OptionalInt.empty(), depthTarget, OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, cube);
                pass.setIndexBuffer(indexBuffer, indices.getIndexType());
                pass.drawIndexed(0, 0, 36, 1);
            }
        } catch (RuntimeException e) {
            if (!warned) LOGGER.warn("Shader Fog: Himmel konnte nicht gezeichnet werden", e);
            warned = true;
        }
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
}

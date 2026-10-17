package com.bame.client.render;

import com.bame.client.module.ShaderFogModule;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Zeichnet den Himmel von {@link ShaderFogModule}: eine Skybox (Weltraum mit Nebel und Sternen bzw. Tiefsee) und einen
 * Ringplaneten. Die Texturen werden prozedural erzeugt (keine Bilddateien), im Hintergrund, beim ersten Gebrauch.
 * Die Skybox ist eine Würfel-Textur (sechs Seiten in einem 3×2-Raster): jedes Pixel wird aus seiner Blickrichtung
 * berechnet (3D-Rauschen), deshalb gibt es an den Würfelkanten keine Nähte. Gezeichnet wird wie Sonne und Mond im
 * Himmels-Durchgang mit der Pipeline des End-Himmels (Textur, Vertex-Farbe, Transparenz). Nur Render-Thread.
 */
public final class ShaderFogRenderer {
    private ShaderFogRenderer() {}

    private static final int FACE = 512;
    private static final int PLANET_SIZE = 256;
    private static final float D = 100.0F;
    /** Halber Texel Abstand zum Rand einer Würfelseite im Raster, damit lineares Filtern nicht in die Nachbarseite greift. */
    private static final float INSET = 0.5F / FACE;

    /** Die sechs Würfelseiten, je Ecke bei UV (0,0), (1,0), (1,1), (0,1). Gleich für Geometrie und Textur. */
    private static final float[][][] FACES = buildFaces();

    private static final LazyTexture SPACE = new LazyTexture(Identifier.of("bameclient", "dynamic/shader_fog_space"), () -> skyTexture(true));
    private static final LazyTexture DEEP_SEA = new LazyTexture(Identifier.of("bameclient", "dynamic/shader_fog_deep_sea"), () -> skyTexture(false));
    private static final LazyTexture PLANET = new LazyTexture(Identifier.of("bameclient", "dynamic/shader_fog_planet"), ShaderFogRenderer::planetTexture);

    private static GpuBuffer skyboxBuffer;
    private static GpuBuffer planetBuffer;

    /** Nach der Himmelsscheibe: Skybox des Stils über den ganzen Himmel (solange sie noch erzeugt wird: nichts). */
    public static void drawSkybox(ShaderFogModule.Style style) {
        GpuTextureView view = (style == ShaderFogModule.Style.SPACE ? SPACE : DEEP_SEA).view();
        if (view == null) return;
        if (skyboxBuffer == null) skyboxBuffer = buildSkybox();
        GpuSampler sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
        draw("Shader fog skybox", RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), view, sampler, skyboxBuffer, 6 * 6);
    }

    /** Nach Sonne, Mond und Sternen: der Ringplanet, fest am Himmel (dreht sich nicht mit der Tageszeit). */
    public static void drawPlanet(float alpha) {
        GpuTextureView view = PLANET.view();
        if (view == null) return;
        if (planetBuffer == null) planetBuffer = buildPlanetQuad();
        float size = 28.0F * Math.clamp(ShaderFogModule.planetSize, 0.4F, 2.5F);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(-35.0F));
        stack.rotate(RotationAxis.POSITIVE_X.rotationDegrees(52.0F));
        stack.translate(0.0F, 100.0F, 0.0F);
        stack.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(18.0F));
        stack.scale(size, 1.0F, size);
        GpuSampler sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
        draw("Shader fog planet", stack, new Vector4f(1.0F, 1.0F, 1.0F, Math.clamp(alpha, 0.0F, 1.0F)), view, sampler, planetBuffer, 6);
        stack.popMatrix();
    }

    private static void draw(String name, Matrix4fc modelView, Vector4f color, GpuTextureView texture, GpuSampler sampler,
                             GpuBuffer vertices, int indexCount) {
        RenderSystem.ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        GpuBuffer indexBuffer = indices.getIndexBuffer(indexCount);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(modelView, color, new Vector3f(), new Matrix4f());
        GpuTextureView colorTarget = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
        GpuTextureView depthTarget = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> name, colorTarget, OptionalInt.empty(), depthTarget, OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.POSITION_TEX_COLOR_END_SKY);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", texture, sampler);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indexBuffer, indices.getIndexType());
            pass.drawIndexed(0, 0, indexCount, 1);
        }
    }

    // ------------------------------------------------------------------
    // Geometrie
    // ------------------------------------------------------------------

    /** Seiten: oben, unten, dann die vier Seitenwände. Ecken je {UV00, UV10, UV11, UV01} als {x, y, z}. */
    private static float[][][] buildFaces() {
        float[][][] faces = new float[6][][];
        faces[0] = new float[][]{{-D, D, -D}, {D, D, -D}, {D, D, D}, {-D, D, D}};
        faces[1] = new float[][]{{-D, -D, -D}, {D, -D, -D}, {D, -D, D}, {-D, -D, D}};
        float[][] ring = {{-D, -D}, {D, -D}, {D, D}, {-D, D}};
        for (int i = 0; i < 4; i++) {
            float[] a = ring[i], c = ring[(i + 1) % 4];
            faces[2 + i] = new float[][]{{a[0], D, a[1]}, {c[0], D, c[1]}, {c[0], -D, c[1]}, {a[0], -D, a[1]}};
        }
        return faces;
    }

    /**
     * Reihenfolge der Ecken beim Zeichnen je Seite, Indizes in {UV00, UV10, UV11, UV01}: gleicher Umlaufsinn wie der
     * End-Himmel, damit die Innenseite sichtbar ist (die Rückseite wird weggelassen).
     */
    private static final int[][] WINDING = {{0, 1, 2, 3}, {0, 3, 2, 1}, {0, 3, 2, 1}, {0, 3, 2, 1}, {0, 3, 2, 1}, {0, 3, 2, 1}};
    private static final float[][] CORNER_UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private static GpuBuffer buildSkybox() {
        try (BufferAllocator allocator = BufferAllocator.fixedSized(6 * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int f = 0; f < 6; f++) {
                float cellX = f % 3, cellY = f / 3;
                for (int corner : WINDING[f]) {
                    float[] p = FACES[f][corner];
                    float u = (cellX + INSET + CORNER_UV[corner][0] * (1.0F - 2.0F * INSET)) / 3.0F;
                    float v = (cellY + INSET + CORNER_UV[corner][1] * (1.0F - 2.0F * INSET)) / 2.0F;
                    b.vertex(p[0], p[1], p[2]).texture(u, v).color(0xFFFFFFFF);
                }
            }
            try (BuiltBuffer built = b.end()) {
                return RenderSystem.getDevice().createBuffer(() -> "Shader fog skybox", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        }
    }

    private static GpuBuffer buildPlanetQuad() {
        try (BufferAllocator allocator = BufferAllocator.fixedSized(4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(-1.0F, 0.0F, -1.0F).texture(0, 0).color(0xFFFFFFFF);
            b.vertex(1.0F, 0.0F, -1.0F).texture(1, 0).color(0xFFFFFFFF);
            b.vertex(1.0F, 0.0F, 1.0F).texture(1, 1).color(0xFFFFFFFF);
            b.vertex(-1.0F, 0.0F, 1.0F).texture(0, 1).color(0xFFFFFFFF);
            try (BuiltBuffer built = b.end()) {
                return RenderSystem.getDevice().createBuffer(() -> "Shader fog planet", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        }
    }

    // ------------------------------------------------------------------
    // Prozedurale Texturen
    // ------------------------------------------------------------------

    /** Würfel-Textur (3×2 Seiten): Weltraum (Nebel, Staub, Sterne) oder Tiefsee (Türkis mit hellen Schlieren). */
    static NativeImage skyTexture(boolean space) {
        NativeImage image = new NativeImage(FACE * 3, FACE * 2, false);
        long seed = space ? 1234567L : 7654321L;
        float[] dir = new float[3];
        for (int f = 0; f < 6; f++) {
            int ox = (f % 3) * FACE, oy = (f / 3) * FACE;
            float[][] c = FACES[f];
            for (int py = 0; py < FACE; py++) {
                float v = (py + 0.5F) / FACE;
                for (int px = 0; px < FACE; px++) {
                    float u = (px + 0.5F) / FACE;
                    for (int k = 0; k < 3; k++) {
                        dir[k] = c[0][k] * (1 - u) * (1 - v) + c[1][k] * u * (1 - v) + c[2][k] * u * v + c[3][k] * (1 - u) * v;
                    }
                    float len = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
                    float x = dir[0] / len, y = dir[1] / len, z = dir[2] / len;
                    int rgb = space ? spacePixel(x, y, z, seed) : deepSeaPixel(x, y, z, seed);
                    image.setColorArgb(ox + px, oy + py, 0xFF000000 | rgb);
                }
            }
        }
        return image;
    }

    private static int spacePixel(float x, float y, float z, long seed) {
        float nebula = fbm3(x * 2.2F, y * 2.2F, z * 2.2F, 6, seed);
        float tint = fbm3(x * 1.3F + 7.1F, y * 1.3F, z * 1.3F - 3.3F, 4, seed + 1);
        float dust = fbm3(x * 4.0F - 2.0F, y * 4.0F + 5.0F, z * 4.0F, 5, seed + 2);
        float cloud = smooth(0.42F, 0.86F, nebula) * (1.0F - 0.6F * smooth(0.5F, 0.8F, dust));
        float r = 0.012F + cloud * (0.30F * tint + 0.10F * (1 - tint));
        float g = 0.014F + cloud * (0.10F * tint + 0.16F * (1 - tint));
        float b = 0.035F + cloud * (0.42F * tint + 0.40F * (1 - tint));
        float star = star(x, y, z, seed, 900.0F, 0.012F, 0.6F) + 1.3F * star(x, y, z, seed + 5, 150.0F, 0.012F, 0.55F);
        r += star * 0.95F;
        g += star * 0.95F;
        b += star;
        return rgb(r, g, b);
    }

    private static int deepSeaPixel(float x, float y, float z, long seed) {
        float wave = fbm3(x * 2.5F, y * 2.5F, z * 2.5F, 5, seed);
        float ridge = fbm3(x * 5.0F + 3.0F, y * 5.0F, z * 5.0F - 1.0F, 4, seed + 1);
        float streak = (float) Math.pow(1.0F - Math.abs(ridge * 2.0F - 1.0F), 6.0F);
        float horizon = 0.70F + 0.30F * (1.0F - Math.max(0.0F, y));
        float r = (0.030F + 0.035F * wave + 0.03F * streak) * horizon;
        float g = (0.120F + 0.090F * wave + 0.10F * streak) * horizon;
        float b = (0.165F + 0.110F * wave + 0.11F * streak) * horizon;
        float star = star(x, y, z, seed + 9, 700.0F, 0.0015F, 0.6F) * 0.25F;
        return rgb(r + star * 0.6F, g + star, b + star);
    }

    /**
     * Stern an dieser Richtung: in einem Raster mit {@code density} Zellen je Einheit trägt jede Zelle mit der
     * Wahrscheinlichkeit {@code chance} einen Stern mit weichem Rand ({@code radius} in Zellen). Feines Raster: viele
     * kleine Sterne, grobes Raster: wenige größere.
     */
    private static float star(float x, float y, float z, long seed, float density, float chance, float radius) {
        float gx = x * density, gy = y * density, gz = z * density;
        int ix = (int) Math.floor(gx), iy = (int) Math.floor(gy), iz = (int) Math.floor(gz);
        if (hash(ix, iy, iz, seed + 77) > chance) return 0.0F;
        float brightness = 0.35F + 0.65F * hash(ix, iy, iz, seed + 78);
        float fx = gx - ix - 0.5F, fy = gy - iy - 0.5F, fz = gz - iz - 0.5F;
        float distance = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        return brightness * Math.max(0.0F, 1.0F - distance / radius);
    }

    /** Saturn-artiger Planet mit gekippten Ringen; hinterer Ringteil wird vom Planeten verdeckt. */
    static NativeImage planetTexture() {
        int n = PLANET_SIZE;
        NativeImage image = new NativeImage(n, n, false);
        float c = n / 2.0F, radius = n * 0.24F, ringIn = n * 0.30F, ringOut = n * 0.465F, squash = 0.30F;
        double tilt = Math.toRadians(-16.0);
        float cos = (float) Math.cos(tilt), sin = (float) Math.sin(tilt);
        float lx = -0.55F, ly = -0.45F, lz = 0.70F;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll; ly /= ll; lz /= ll;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = x + 0.5F - c, dy = y + 0.5F - c;
                float rx = dx * cos + dy * sin, ry = -dx * sin + dy * cos;
                float ringR = (float) Math.sqrt(rx * rx + (ry / squash) * (ry / squash));
                float[] out = {0, 0, 0, 0};
                float[] ring = ringR >= ringIn && ringR <= ringOut ? ringColor((ringR - ringIn) / (ringOut - ringIn)) : null;
                boolean front = ry > 0;
                if (ring != null && !front) over(out, ring);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= radius + 1.0F) {
                    float nx = dx / radius, ny = dy / radius;
                    float nz = (float) Math.sqrt(Math.max(0.0F, 1.0F - nx * nx - ny * ny));
                    float light = Math.max(0.0F, nx * lx + ny * ly + nz * lz);
                    float shade = 0.10F + 1.0F * light;
                    float lat = ry / radius;
                    float turbulence = fbm3(rx / radius * 1.5F, lat * 6.0F, 0.5F, 3, 99L);
                    float band = 0.5F + 0.4F * (float) Math.sin(lat * 15.0F + turbulence * 2.2F)
                            + 0.1F * (float) Math.sin(lat * 37.0F + 1.3F);
                    float pr = mix(0.62F, 0.93F, band), pg = mix(0.46F, 0.84F, band), pb = mix(0.30F, 0.66F, band);
                    // Ringschatten auf dem Planeten (schmales Band unterhalb des Rings)
                    float shadow = Math.abs(ry + radius * 0.12F) < radius * 0.05F && Math.abs(rx) < radius * 0.95F ? 0.55F : 1.0F;
                    float alpha = Math.clamp(radius + 0.5F - dist, 0.0F, 1.0F);
                    over(out, new float[]{pr * shade * shadow, pg * shade * shadow, pb * shade * shadow, alpha});
                }
                if (ring != null && front) over(out, ring);
                int a = to255(out[3]);
                image.setColorArgb(x, y, a == 0 ? 0 : (a << 24) | (to255(out[0]) << 16) | (to255(out[1]) << 8) | to255(out[2]));
            }
        }
        return image;
    }

    /** Ringfarbe abhängig vom Abstand (0 innen, 1 außen): helle und dunkle Ringe, Lücke wie die Cassini-Teilung. */
    private static float[] ringColor(float t) {
        float bands = 0.65F + 0.35F * (float) Math.sin(t * 46.0F) * (float) Math.sin(t * 13.0F + 0.7F);
        float gap = t > 0.60F && t < 0.66F ? 0.12F : 1.0F;
        float edge = Math.min(1.0F, Math.min(t, 1.0F - t) * 14.0F);
        float alpha = 0.88F * bands * gap * edge;
        float v = 0.62F + 0.30F * bands;
        return new float[]{v * 0.95F, v * 0.90F, v * 0.82F, alpha};
    }

    private static void over(float[] dst, float[] src) {
        float a = src[3], inv = 1.0F - a;
        float outA = a + dst[3] * inv;
        if (outA <= 0.0F) return;
        for (int i = 0; i < 3; i++) dst[i] = (src[i] * a + dst[i] * dst[3] * inv) / outA;
        dst[3] = outA;
    }

    // ------------------------------------------------------------------
    // Rauschen (3D, richtungsbasiert)
    // ------------------------------------------------------------------

    /** Fraktales 3D-Wertrauschen (0..1). */
    static float fbm3(float x, float y, float z, int octaves, long seed) {
        float sum = 0.0F, amplitude = 0.5F, total = 0.0F, f = 1.0F;
        for (int o = 0; o < octaves; o++) {
            sum += amplitude * valueNoise(x * f, y * f, z * f, seed + o * 131L);
            total += amplitude;
            amplitude *= 0.5F;
            f *= 2.0F;
        }
        return sum / total;
    }

    private static float valueNoise(float x, float y, float z, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float tx = fade(x - x0), ty = fade(y - y0), tz = fade(z - z0);
        float c000 = hash(x0, y0, z0, seed), c100 = hash(x0 + 1, y0, z0, seed);
        float c010 = hash(x0, y0 + 1, z0, seed), c110 = hash(x0 + 1, y0 + 1, z0, seed);
        float c001 = hash(x0, y0, z0 + 1, seed), c101 = hash(x0 + 1, y0, z0 + 1, seed);
        float c011 = hash(x0, y0 + 1, z0 + 1, seed), c111 = hash(x0 + 1, y0 + 1, z0 + 1, seed);
        float a = mix(mix(c000, c100, tx), mix(c010, c110, tx), ty);
        float b = mix(mix(c001, c101, tx), mix(c011, c111, tx), ty);
        return mix(a, b, tz);
    }

    /** Ganzzahl-Hash auf 0..1. */
    private static float hash(int x, int y, int z, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x85157AF5L + z * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    private static float fade(float t) { return t * t * (3.0F - 2.0F * t); }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static int rgb(float r, float g, float b) { return (to255(r) << 16) | (to255(g) << 8) | to255(b); }

    private static int to255(float v) { return Math.clamp(Math.round(v * 255.0F), 0, 255); }

    /**
     * Dynamische Textur: wird beim ersten Gebrauch im Hintergrund erzeugt und danach auf dem Render-Thread hochgeladen;
     * bei einem neuen TextureManager (z. B. Neustart der Grafik) neu angelegt.
     */
    private static final class LazyTexture {
        private final Identifier id;
        private final Supplier<NativeImage> generator;
        private NativeImageBackedTexture texture;
        private TextureManager owner;
        private CompletableFuture<NativeImage> pending;
        private boolean failed;

        LazyTexture(Identifier id, Supplier<NativeImage> generator) {
            this.id = id;
            this.generator = generator;
        }

        /** Fertige Textur oder null, solange sie noch erzeugt wird. */
        GpuTextureView view() {
            TextureManager manager = MinecraftClient.getInstance().getTextureManager();
            if (texture != null && owner == manager && !texture.getGlTexture().isClosed()) return texture.getGlTextureView();
            if (failed) return null;
            if (pending == null) pending = CompletableFuture.supplyAsync(generator);
            if (!pending.isDone()) return null;
            try {
                texture = new NativeImageBackedTexture(id::toString, pending.join());
                manager.registerTexture(id, texture);
                owner = manager;
            } catch (RuntimeException e) {
                failed = true;
                return null;
            } finally {
                pending = null;
            }
            return texture.getGlTextureView();
        }
    }
}

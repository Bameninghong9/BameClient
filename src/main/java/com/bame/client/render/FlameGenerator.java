package com.bame.client.render;

import com.bame.client.module.CustomFireModule;

import java.util.Random;

/**
 * Erzeugt die Flammen von Custom Fire als nahtlos laufende Animation ({@link #FRAMES} Bilder zu je
 * {@link #SIZE}×{@link #SIZE} Pixeln, ARGB). Die Kontur entsteht aus einzelnen Flammenzungen (je nach Stil spitz,
 * weich oder breit), die flackern und seitlich schwanken; dazu kommt aufsteigendes Rauschen. Alle Bewegungen laufen in
 * ganzen Zyklen über die Animation, deshalb springt die Schleife nicht. Reine Berechnung, ohne Minecraft-Klassen.
 */
public final class FlameGenerator {
    private FlameGenerator() {}

    public static final int SIZE = 64;
    public static final int FRAMES = 32;

    /**
     * Einstellungen für eine Erzeugung. {@code world}: Variante für Feuer-Blöcke und Entities. Diese werden im Spiel
     * „ausgestanzt“ gezeichnet (ein Pixel ist ganz sichtbar oder gar nicht), deshalb gibt es dort nur volle oder keine
     * Deckkraft; Center Clear und Bottom Fade wirken nur auf das Bildschirm-Feuer.
     */
    public record Params(int primary, int secondary, float intensity, float edgeGlow, float centerClear, float bottomFade,
                         CustomFireModule.Style style, float height, float speed, boolean world) {
        public static Params current(boolean world) {
            return new Params(CustomFireModule.color, CustomFireModule.effectiveSecondary(), CustomFireModule.intensity,
                    CustomFireModule.edgeGlow, CustomFireModule.centerClear, CustomFireModule.bottomFade, CustomFireModule.style,
                    world ? Math.clamp(CustomFireModule.worldFlameHeight, 0.15f, 1.0f) : 1.0f, CustomFireModule.speed, world);
        }
    }

    /** Form-Parameter je Stil. */
    private record Shape(int tongues, float minWidth, float maxWidth, float minPeak, float maxPeak, float body,
                         float softness, float turbulence, float flicker, boolean sharp) {}

    private static Shape shape(CustomFireModule.Style style) {
        return switch (style) {
            case SHARP -> new Shape(6, 0.07f, 0.12f, 0.42f, 1.00f, 0.26f, 0.018f, 0.035f, 0.55f, true);
            case MINIMAL -> new Shape(3, 0.17f, 0.24f, 0.55f, 0.92f, 0.42f, 0.060f, 0.025f, 0.25f, false);
            case NATIVE -> new Shape(5, 0.10f, 0.17f, 0.50f, 1.00f, 0.36f, 0.040f, 0.110f, 0.45f, false);
        };
    }

    public static int[][] generate(Params p) {
        Shape s = shape(p.style());
        Random random = new Random(91737L + p.style().ordinal() * 1013L);
        int cycles = Math.max(1, Math.round(Math.max(0.0f, p.speed()) * 2.0f));
        int k = s.tongues();
        float[] center = new float[k], width = new float[k], phase = new float[k], swayPhase = new float[k], peakBias = new float[k];
        int[] flickerCycles = new int[k];
        for (int i = 0; i < k; i++) {
            center[i] = (i + 0.5f) / k + (random.nextFloat() - 0.5f) * 0.45f / k;
            width[i] = s.minWidth() + random.nextFloat() * (s.maxWidth() - s.minWidth());
            phase[i] = random.nextFloat() * 6.2831855f;
            swayPhase[i] = random.nextFloat() * 6.2831855f;
            peakBias[i] = random.nextFloat();
            flickerCycles[i] = cycles + (i % 2);
        }
        float h = Math.clamp(p.height(), 0.1f, 1.0f) * 0.96f;
        float[] primary = rgb(p.primary()), secondary = rgb(p.secondary());
        float intensity = Math.max(0.0f, p.intensity());
        float alphaGain = 0.35f + 0.65f * Math.min(intensity, 1.6f);
        float colorGain = 0.70f + 0.30f * Math.min(intensity, 2.5f);

        int[][] frames = new int[FRAMES][SIZE * SIZE];
        float[] top = new float[SIZE];
        for (int f = 0; f < FRAMES; f++) {
            float t = f / (float) FRAMES;
            float scroll = t * cycles;
            // Kontur je Spalte: Körper plus Flammenzungen
            for (int x = 0; x < SIZE; x++) {
                float u = (x + 0.5f) / SIZE;
                float best = s.body() * (0.92f + 0.08f * (float) Math.sin(6.2831855f * (u * 2.0f + scroll)));
                for (int i = 0; i < k; i++) {
                    float sway = 0.035f * (float) Math.sin(6.2831855f * cycles * t + swayPhase[i]);
                    float du = wrap(u - center[i] - sway);
                    float a = Math.abs(du) / width[i];
                    if (a >= 1.0f) continue;
                    float shapeValue = s.sharp() ? 1.0f - a : (float) Math.pow(0.5f + 0.5f * (float) Math.cos(Math.PI * a), 0.75f);
                    float flicker = 0.5f + 0.5f * (float) Math.sin(6.2831855f * flickerCycles[i] * t + phase[i]);
                    float peak = s.minPeak() + (s.maxPeak() - s.minPeak()) * (0.35f * peakBias[i] + 0.65f * (1.0f - s.flicker() + s.flicker() * flicker));
                    best = Math.max(best, peak * shapeValue);
                }
                top[x] = best * h;
            }
            int[] out = frames[f];
            for (int y = 0; y < SIZE; y++) {
                float v = 1.0f - (y + 0.5f) / SIZE; // 0 unten, 1 oben
                for (int x = 0; x < SIZE; x++) {
                    float u = (x + 0.5f) / SIZE;
                    // aufsteigendes Rauschen: verwirbelt die Kontur und hellt das Innere auf
                    float turb = fbm(u * 4.0f, v * 4.0f - scroll * 4.0f, 4, 4, 3, 17L);
                    float edgeTop = top[x] + (turb - 0.5f) * 2.0f * s.turbulence() * h;
                    float d = edgeTop - v;
                    float soft = s.softness();
                    float a = smooth(-soft, soft, d);
                    if (a <= 0.0f) { out[y * SIZE + x] = 0; continue; }
                    float edge = (float) Math.exp(-Math.max(d, 0.0f) / (0.10f * h));
                    float inner = fbm(u * 6.0f, v * 5.0f - scroll * 5.0f, 6, 5, 3, 29L);
                    float g = Math.clamp(v / Math.max(h, 0.01f), 0.0f, 1.0f);
                    float mixT = (float) Math.pow(g, 0.9f);
                    float r = primary[0] + (secondary[0] - primary[0]) * mixT;
                    float gr = primary[1] + (secondary[1] - primary[1]) * mixT;
                    float b = primary[2] + (secondary[2] - primary[2]) * mixT;
                    float bright = (0.82f + 0.36f * inner) * (1.0f + p.edgeGlow() * edge * 0.9f) * colorGain;
                    float white = Math.clamp(p.edgeGlow() * edge * 0.35f, 0.0f, 1.0f);
                    r = r * bright + (1.0f - r * bright) * white;
                    gr = gr * bright + (1.0f - gr * bright) * white;
                    b = b * bright + (1.0f - b * bright) * white;
                    float alpha;
                    if (p.world()) {
                        alpha = a >= 0.5f ? 1.0f : 0.0f;
                    } else {
                        float clear = 1.0f - Math.clamp(p.centerClear(), 0.0f, 1.0f) * 0.9f * smooth(0.15f, 0.9f, 1.0f - edge);
                        float fade = 1.0f + (smooth(0.0f, 0.3f * h, v) - 1.0f) * Math.clamp(p.bottomFade(), 0.0f, 1.0f);
                        alpha = Math.clamp(a * clear * fade * alphaGain, 0.0f, 1.0f);
                    }
                    out[y * SIZE + x] = argb(alpha, r, gr, b);
                }
            }
        }
        return frames;
    }

    /**
     * Ein Animationsbild auf {@code w}×{@code h} Pixel verkleinern (Mittelwert über die Fläche, Farbe mit Deckkraft
     * gewichtet, damit keine dunklen Ränder entstehen). Bei {@code cutout} bleibt die Deckkraft ganz oder gar nicht.
     */
    public static int[] resample(int[] src, int w, int h, boolean cutout) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = y * SIZE / h, y1 = Math.max(y0 + 1, (y + 1) * SIZE / h);
            for (int x = 0; x < w; x++) {
                int x0 = x * SIZE / w, x1 = Math.max(x0 + 1, (x + 1) * SIZE / w);
                float sa = 0, sr = 0, sg = 0, sb = 0;
                int n = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        int c = src[yy * SIZE + xx];
                        float a = (c >>> 24) / 255.0f;
                        sa += a;
                        sr += ((c >> 16) & 255) * a;
                        sg += ((c >> 8) & 255) * a;
                        sb += (c & 255) * a;
                        n++;
                    }
                }
                if (sa <= 0.0f) { out[y * w + x] = 0; continue; }
                float alpha = sa / n;
                if (cutout) alpha = alpha >= 0.4f ? 1.0f : 0.0f;
                if (alpha <= 0.0f) { out[y * w + x] = 0; continue; }
                out[y * w + x] = (Math.round(alpha * 255.0f) << 24) | (Math.round(sr / sa) << 16) | (Math.round(sg / sa) << 8) | Math.round(sb / sa);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------

    private static float wrap(float d) {
        d -= (float) Math.floor(d + 0.5f);
        return d;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.clamp((x - e0) / (e1 - e0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 255) / 255.0f, ((argb >> 8) & 255) / 255.0f, (argb & 255) / 255.0f};
    }

    private static int argb(float a, float r, float g, float b) {
        int ia = Math.clamp(Math.round(a * 255.0f), 0, 255);
        if (ia == 0) return 0;
        return (ia << 24) | (to255(r) << 16) | (to255(g) << 8) | to255(b);
    }

    private static int to255(float v) { return Math.clamp(Math.round(v * 255.0f), 0, 255); }

    /** Fraktales Wertrauschen, in x mit Periode {@code px} und in y mit Periode {@code py} Gitterzellen nahtlos. */
    private static float fbm(float x, float y, int px, int py, int octaves, long seed) {
        float sum = 0.0f, amp = 0.5f, total = 0.0f;
        for (int o = 0; o < octaves; o++) {
            int m = 1 << o;
            sum += amp * noise(x * m, y * m, px * m, py * m, seed + o * 7919L);
            total += amp;
            amp *= 0.5f;
        }
        return sum / total;
    }

    private static float noise(float x, float y, int px, int py, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float tx = x - x0, ty = y - y0;
        tx = tx * tx * (3.0f - 2.0f * tx);
        ty = ty * ty * (3.0f - 2.0f * ty);
        float a = hash(Math.floorMod(x0, px), Math.floorMod(y0, py), seed);
        float b = hash(Math.floorMod(x0 + 1, px), Math.floorMod(y0, py), seed);
        float c = hash(Math.floorMod(x0, px), Math.floorMod(y0 + 1, py), seed);
        float d = hash(Math.floorMod(x0 + 1, px), Math.floorMod(y0 + 1, py), seed);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
    }

    private static float hash(int x, int y, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x85157AF5L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }
}

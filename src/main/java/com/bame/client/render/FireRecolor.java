package com.bame.client.render;

import com.bame.client.mixin.SpriteAnimatorAccessor;
import com.bame.client.mixin.SpriteContentsAccessor;
import com.bame.client.module.CustomFireModule;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Färbt die Feuer-Animationen im Block-Atlas um. Seit 1.21.11 liegt jedes Animationsbild als eigene GPU-Textur vor,
 * die jeden Tick in den Atlas kopiert wird ({@code SpriteContents.Animator}). Diese Einzelbilder werden hier mit
 * eingefärbten Pixeln überschrieben (Helligkeit der Original-Textur mal Wunschfarbe), ausgeschaltet wieder mit dem
 * Original. Weil Feuer-Blöcke, das Feuer am Bildschirm und brennende Entities dieselben Sprites nutzen, ist danach
 * jedes Feuer umgefärbt. Nur auf dem Render-Thread aufrufen.
 */
public final class FireRecolor {
    private FireRecolor() {}

    /** Bei RGB höchstens so oft neu einfärben (ms). */
    private static final long CHROMA_STEP_MS = 80L;

    private static final class Target {
        final SpriteContents contents;
        final SpriteContents.Animator animator;
        final boolean soul;
        NativeImage[] scratch;
        boolean modified;

        Target(SpriteContents contents, SpriteContents.Animator animator, boolean soul) {
            this.contents = contents;
            this.animator = animator;
            this.soul = soul;
        }

        void closeScratch() {
            if (scratch == null) return;
            for (NativeImage image : scratch) if (image != null) image.close();
            scratch = null;
        }
    }

    private static final Map<String, Target> TARGETS = new HashMap<>();
    private static String appliedKey = null;

    /** Aus {@code SpriteContentsMixin}: eine Animation wurde (neu) erzeugt, z. B. beim Laden der Ressourcen. */
    public static void register(SpriteContents contents, SpriteContents.Animator animator) {
        Identifier id = contents.getId();
        if (!"minecraft".equals(id.getNamespace())) return;
        String path = id.getPath();
        boolean fire = path.equals("block/fire_0") || path.equals("block/fire_1");
        boolean soul = path.equals("block/soul_fire_0") || path.equals("block/soul_fire_1");
        if (!fire && !soul) return;
        Target old = TARGETS.put(path, new Target(contents, animator, soul));
        if (old != null) old.closeScratch();
        appliedKey = null; // neue Texturen sind im Original: beim nächsten Tick neu anwenden
    }

    /** Jeden Client-Tick: nur wenn sich etwas geändert hat (oder RGB läuft), wird neu eingefärbt. */
    public static void tick() {
        if (TARGETS.isEmpty()) return;
        String key = stateKey(System.currentTimeMillis());
        if (Objects.equals(key, appliedKey)) return;
        appliedKey = key;
        for (Target target : TARGETS.values()) {
            boolean recolor = CustomFireModule.enabled && (!target.soul || CustomFireModule.soulFire);
            if (!recolor && !target.modified) continue;
            try {
                write(target, recolor);
                target.modified = recolor;
            } catch (RuntimeException e) {
                // Textur wurde inzwischen geschlossen (Ressourcen neu geladen): beim nächsten register neu
                target.modified = false;
            }
        }
    }

    private static String stateKey(long now) {
        if (!CustomFireModule.enabled) return "off";
        return CustomFireModule.color + "|" + CustomFireModule.gradient + "|" + CustomFireModule.topColor + "|"
                + CustomFireModule.soulFire + "|" + (CustomFireModule.chroma ? now / CHROMA_STEP_MS : -1L);
    }

    private static void write(Target target, boolean recolor) {
        NativeImage[] mips = ((SpriteContentsAccessor) target.contents).caeser$getMipmapLevelsImages();
        Int2ObjectMap<GpuTextureView> frames = ((SpriteAnimatorAccessor) (Object) target.animator).caeser$getTextureViewsByFrame();
        if (mips == null || mips.length == 0 || frames == null || frames.isEmpty()) return;
        int width = target.contents.getWidth();
        int height = target.contents.getHeight();
        int perRow = Math.max(1, mips[0].getWidth() / width);
        if (recolor && (target.scratch == null || target.scratch.length != mips.length)) {
            target.closeScratch();
            target.scratch = new NativeImage[mips.length];
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        long now = System.currentTimeMillis();
        for (int level = 0; level < mips.length; level++) {
            int fw = width >> level, fh = height >> level;
            if (fw <= 0 || fh <= 0) break;
            NativeImage source = mips[level];
            NativeImage image = source;
            if (recolor) {
                NativeImage dst = target.scratch[level];
                if (dst == null || dst.getWidth() != source.getWidth() || dst.getHeight() != source.getHeight()) {
                    if (dst != null) dst.close();
                    dst = new NativeImage(source.getWidth(), source.getHeight(), false);
                    target.scratch[level] = dst;
                }
                recolor(source, dst, rowColors(fh, now));
                image = dst;
            }
            for (Int2ObjectMap.Entry<GpuTextureView> frame : frames.int2ObjectEntrySet()) {
                GpuTexture texture = frame.getValue().texture();
                if (texture.isClosed()) return;
                int index = frame.getIntKey();
                int x = (index % perRow) * fw;
                int y = (index / perRow) * fh;
                if (x + fw > image.getWidth() || y + fh > image.getHeight()) continue;
                encoder.writeToTexture(texture, image, level, 0, 0, 0, fw, fh, x, y);
            }
        }
    }

    /** Farbe je Bildzeile eines Animationsbilds (oben = Index 0): Verlauf und RGB. */
    private static int[] rowColors(int frameHeight, long now) {
        int[] rows = new int[frameHeight];
        float hue = (now % 3000L) / 3000.0f;
        for (int y = 0; y < frameHeight; y++) {
            float t = frameHeight <= 1 ? 1.0f : y / (float) (frameHeight - 1); // 0 oben, 1 unten
            int rgb;
            if (CustomFireModule.chroma) {
                float h = CustomFireModule.gradient ? hue + (1.0f - t) * 0.35f : hue;
                rgb = java.awt.Color.HSBtoRGB(h - (float) Math.floor(h), 0.8f, 1.0f);
            } else if (CustomFireModule.gradient) {
                rgb = lerp(CustomFireModule.topColor, CustomFireModule.color, t);
            } else {
                rgb = CustomFireModule.color;
            }
            rows[y] = rgb & 0xFFFFFF;
        }
        return rows;
    }

    /** Helligkeit des Originals mal Zielfarbe; Alpha bleibt. Zeilen wiederholen sich je Animationsbild. */
    private static void recolor(NativeImage source, NativeImage dst, int[] rows) {
        int w = source.getWidth(), h = source.getHeight(), fh = rows.length;
        for (int y = 0; y < h; y++) {
            int rgb = rows[y % fh];
            int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
            for (int x = 0; x < w; x++) {
                int c = source.getColorArgb(x, y);
                int a = c >>> 24;
                if (a == 0) { dst.setColorArgb(x, y, 0); continue; }
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                float v = Math.min(1.0f, 0.30f + 0.85f * lum);
                dst.setColorArgb(x, y, (a << 24) | (Math.round(cr * v) << 16) | (Math.round(cg * v) << 8) | Math.round(cb * v));
            }
        }
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return (r << 16) | (g << 8) | b;
    }
}

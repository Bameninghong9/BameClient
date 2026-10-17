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
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Ersetzt die Feuer-Animationen im Block-Atlas durch die Flammen von {@link FlameGenerator}. Seit 1.21.11 liegt jedes
 * Animationsbild als eigene GPU-Textur vor, die beim Abspielen in den Atlas kopiert wird; diese Einzelbilder werden
 * überschrieben (ausgeschaltet wieder mit dem Original). Erfasst werden alle Feuer-Texturen, auch zusätzliche aus
 * Resourcepacks wie {@code block/fire_floor}; Feuer-Blöcke und brennende Entities zeigen danach die eigenen Flammen.
 * Erzeugt wird im Hintergrund, hochgeladen auf dem Render-Thread ({@link #tick()}).
 */
public final class FireRestyle {
    private FireRestyle() {}

    /** Feuer-Texturen: fire_0, fire_1, fire_floor, soul_fire_0 … (nicht fire_coral, firefly_bush …). */
    private static final Pattern FIRE = Pattern.compile("block/(soul_)?fire_(\\d+|floor\\d*|side\\d*|up\\d*|alt\\d*)");
    /** Während ein Regler gezogen wird, höchstens so oft neu erzeugen (ms). */
    private static final long MIN_INTERVAL_MS = 120L;

    private static final class Target {
        final SpriteContents contents;
        final SpriteContents.Animator animator;
        final boolean soul;
        boolean modified;

        Target(SpriteContents contents, SpriteContents.Animator animator, boolean soul) {
            this.contents = contents;
            this.animator = animator;
            this.soul = soul;
        }
    }

    private static final Map<String, Target> TARGETS = new HashMap<>();
    private static String appliedKey = null;
    private static String pendingKey = null;
    private static CompletableFuture<int[][]> pending = null;
    private static long lastStart = 0L;

    /** Aus {@code SpriteContentsMixin}: eine Animation wurde (neu) erzeugt, z. B. beim Laden der Ressourcen. */
    public static void register(SpriteContents contents, SpriteContents.Animator animator) {
        Identifier id = contents.getId();
        if (!"minecraft".equals(id.getNamespace()) || !FIRE.matcher(id.getPath()).matches()) return;
        TARGETS.put(id.getPath(), new Target(contents, animator, id.getPath().startsWith("block/soul_")));
        appliedKey = null; // neue Texturen sind im Original: beim nächsten Tick neu anwenden
    }

    private static boolean active() {
        return CustomFireModule.enabled && CustomFireModule.restyleWorldFire;
    }

    /** Jeden Client-Tick (Render-Thread): neu erzeugen, wenn sich etwas geändert hat, fertige Bilder hochladen. */
    public static void tick() {
        if (TARGETS.isEmpty()) return;
        String key = active() ? FlameGenerator.Params.current(true) + "|" + CustomFireModule.soulFire : "off";
        if (pending != null && pending.isDone()) {
            int[][] frames;
            try {
                frames = pending.join();
            } catch (RuntimeException e) {
                frames = null;
            }
            String done = pendingKey;
            pending = null;
            pendingKey = null;
            if (frames != null && Objects.equals(done, key)) {
                apply(frames);
                appliedKey = done;
            }
        }
        if (Objects.equals(key, appliedKey) || Objects.equals(key, pendingKey)) return;
        if (!active()) {
            restoreAll();
            appliedKey = key;
            return;
        }
        long now = System.currentTimeMillis();
        if (pending != null || now - lastStart < MIN_INTERVAL_MS) return;
        lastStart = now;
        pendingKey = key;
        FlameGenerator.Params params = FlameGenerator.Params.current(true);
        pending = CompletableFuture.supplyAsync(() -> FlameGenerator.generate(params));
    }

    private static void apply(int[][] frames) {
        for (Target target : TARGETS.values()) {
            boolean restyle = !target.soul || CustomFireModule.soulFire;
            try {
                if (restyle) {
                    write(target, frames);
                    target.modified = true;
                } else if (target.modified) {
                    restore(target);
                    target.modified = false;
                }
            } catch (RuntimeException e) {
                target.modified = false; // Textur wurde inzwischen geschlossen (Ressourcen neu geladen)
            }
        }
    }

    private static void restoreAll() {
        for (Target target : TARGETS.values()) {
            if (!target.modified) continue;
            try {
                restore(target);
            } catch (RuntimeException ignored) {
            }
            target.modified = false;
        }
    }

    /** Eigene Flammen in jedes Animationsbild (alle Mipmap-Stufen) schreiben. */
    private static void write(Target target, int[][] frames) {
        NativeImage[] mips = ((SpriteContentsAccessor) target.contents).caeser$getMipmapLevelsImages();
        Int2ObjectMap<GpuTextureView> views = ((SpriteAnimatorAccessor) (Object) target.animator).caeser$getTextureViewsByFrame();
        if (mips == null || mips.length == 0 || views == null || views.isEmpty()) return;
        int width = target.contents.getWidth(), height = target.contents.getHeight();
        int perRow = Math.max(1, mips[0].getWidth() / width);
        int frameCount = Math.max(1, perRow * Math.max(1, mips[0].getHeight() / height));
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (Int2ObjectMap.Entry<GpuTextureView> entry : views.int2ObjectEntrySet()) {
            GpuTexture texture = entry.getValue().texture();
            if (texture.isClosed()) return;
            int[] master = frames[Math.floorMod(entry.getIntKey(), frameCount) * FlameGenerator.FRAMES / frameCount];
            for (int level = 0; level < mips.length; level++) {
                int w = width >> level, h = height >> level;
                if (w <= 0 || h <= 0) break;
                int[] pixels = FlameGenerator.resample(master, w, h, true);
                try (NativeImage image = new NativeImage(w, h, false)) {
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) image.setColorArgb(x, y, pixels[y * w + x]);
                    }
                    encoder.writeToTexture(texture, image, level, 0, 0, 0, w, h, 0, 0);
                }
            }
        }
    }

    /** Original-Pixel zurückschreiben. */
    private static void restore(Target target) {
        NativeImage[] mips = ((SpriteContentsAccessor) target.contents).caeser$getMipmapLevelsImages();
        Int2ObjectMap<GpuTextureView> views = ((SpriteAnimatorAccessor) (Object) target.animator).caeser$getTextureViewsByFrame();
        if (mips == null || mips.length == 0 || views == null || views.isEmpty()) return;
        int width = target.contents.getWidth(), height = target.contents.getHeight();
        int perRow = Math.max(1, mips[0].getWidth() / width);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int level = 0; level < mips.length; level++) {
            int w = width >> level, h = height >> level;
            if (w <= 0 || h <= 0) break;
            NativeImage source = mips[level];
            for (Int2ObjectMap.Entry<GpuTextureView> entry : views.int2ObjectEntrySet()) {
                GpuTexture texture = entry.getValue().texture();
                if (texture.isClosed()) return;
                int index = entry.getIntKey();
                int x = (index % perRow) * w, y = (index / perRow) * h;
                if (x + w > source.getWidth() || y + h > source.getHeight()) continue;
                encoder.writeToTexture(texture, source, level, 0, 0, 0, w, h, x, y);
            }
        }
    }
}

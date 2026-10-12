package com.bame.client.gui;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug counter for the mod menu, active only with the JVM property {@code -Dcaeser.guiProfile=true}.
 *
 * <p>Counts, per frame of {@link BameClientScreen#render}, the {@code DrawContext.fill} calls (all overloads incl.
 * fillGradient) and all simple GUI elements (fills + textured quads such as the corner textures and icons), and measures
 * the time spent in {@code BameClientScreen.render}. Once per second it logs the averages, the current FPS and the active
 * view/tab. The counting mixins live in {@code bameclient.profiler.mixins.json} and are only applied when the property is
 * set (see {@code GuiProfilerMixinPlugin}); without it the only cost is one constant boolean check per frame.
 * Render thread only.
 */
public final class GuiProfiler {
    public static final boolean ENABLED = Boolean.getBoolean("caeser.guiProfile");

    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserGuiProfile");
    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;

    private static boolean inScreen;
    private static int frameFills;
    private static int frameElements;

    private static long windowStart;
    private static int frames;
    private static long sumFills;
    private static long sumElements;
    private static long sumNanos;
    private static long maxNanos;

    private GuiProfiler() {
    }

    /** Start of a BameClientScreen frame; returns the start time for {@link #endFrame}. */
    public static long beginFrame() {
        inScreen = true;
        frameFills = 0;
        frameElements = 0;
        return System.nanoTime();
    }

    public static void endFrame(long startNanos, String view) {
        long now = System.nanoTime();
        long nanos = now - startNanos;
        inScreen = false;

        if (frames == 0) windowStart = startNanos;
        frames++;
        sumFills += frameFills;
        sumElements += frameElements;
        sumNanos += nanos;
        maxNanos = Math.max(maxNanos, nanos);

        if (now - windowStart >= REPORT_INTERVAL_NANOS) {
            LOGGER.info(String.format(java.util.Locale.ROOT,
                    "[%s] %d fps | fill/frame %.0f | gui elements/frame %.0f | BameClientScreen.render %.2f ms avg, %.2f ms max | %d frames",
                    view, MinecraftClient.getInstance().getCurrentFps(),
                    sumFills / (double) frames, sumElements / (double) frames,
                    sumNanos / (double) frames / 1_000_000.0, maxNanos / 1_000_000.0, frames));
            frames = 0;
            sumFills = 0;
            sumElements = 0;
            sumNanos = 0;
            maxNanos = 0;
        }
    }

    /** Called from the DrawContext mixin for every fill. */
    public static void countFill() {
        if (inScreen) frameFills++;
    }

    /** Called from the GuiRenderState mixin for every simple element (fill, textured quad, sprite). */
    public static void countElement() {
        if (inScreen) frameElements++;
    }
}

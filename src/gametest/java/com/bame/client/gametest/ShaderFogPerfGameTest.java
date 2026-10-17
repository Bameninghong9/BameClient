package com.bame.client.gametest;

import com.bame.client.module.ShaderFogModule;
import com.bame.client.render.GpuTimings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leistungsmessung für Shader Fog: GPU-Zeit des Shader-Himmels und des ganzen Bildes je Modus, bei 1920×1080.
 * Ergebnis im Log und in {@code build/run/clientGameTest/shader_fog_perf.txt}.
 * Mit CAESER_TEST_ONLY=visual wird diese Messung übersprungen.
 */
public class ShaderFogPerfGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("Caeser Perf");
    private static final int WARMUP_TICKS = 40;
    private static final int MEASURE_TICKS = 160;

    @Override
    public void runTest(ClientGameTestContext context) {
        if ("visual".equalsIgnoreCase(System.getenv("CAESER_TEST_ONLY"))) return;
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("time set 6000");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runCommand("gamemode creative @a");
            context.getInput().resizeWindow(1920, 1080);
            context.runOnClient(client -> {
                client.options.getEnableVsync().setValue(false);
                client.options.getMaxFps().setValue(260);
                client.options.setPerspective(Perspective.FIRST_PERSON);
                client.player.setPitch(-15.0f);
                client.player.lastPitch = -15.0f;
                GpuTimings.enabled = true;
            });
            world.getClientWorld().waitForChunksRender();
            String size = context.computeOnClient(client ->
                    client.getWindow().getFramebufferWidth() + "x" + client.getWindow().getFramebufferHeight());

            List<String> lines = new ArrayList<>();
            lines.add("Shader Fog GPU-Zeit bei " + size + " (" + MEASURE_TICKS + " Bilder je Modus)");
            lines.add(String.format(Locale.ROOT, "%-8s %12s %12s %14s %10s", "Modus", "Himmel ms", "Bild ms", "Backen ms", "Back/Bild"));
            measure(context, lines, "Vanilla", null);
            for (ShaderFogModule.Mode mode : ShaderFogModule.Mode.values()) measure(context, lines, mode.label, mode);
            context.runOnClient(client -> {
                ShaderFogModule.enabled = false;
                GpuTimings.enabled = false;
            });

            for (String line : lines) LOGGER.info(line);
            Path out = context.computeOnClient(client -> client.runDirectory.toPath().resolve("shader_fog_perf.txt"));
            try {
                Files.write(out, lines);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void measure(ClientGameTestContext context, List<String> lines, String label, ShaderFogModule.Mode mode) {
        context.runOnClient(client -> {
            ShaderFogModule.resetToDefault();
            ShaderFogModule.enabled = mode != null;
            if (mode != null) ShaderFogModule.mode = mode;
        });
        context.waitTicks(WARMUP_TICKS);
        context.runOnClient(client -> GpuTimings.reset());
        context.waitTicks(MEASURE_TICKS);
        double sky = context.computeOnClient(client -> GpuTimings.averageMs("shader_fog_sky"));
        double frame = context.computeOnClient(client -> GpuTimings.averageMs("frame"));
        double bake = context.computeOnClient(client -> GpuTimings.averageMs("shader_fog_bake"));
        long bakes = context.computeOnClient(client -> GpuTimings.samples("shader_fog_bake"));
        long frames = context.computeOnClient(client -> GpuTimings.samples("frame"));
        lines.add(String.format(Locale.ROOT, "%-8s %12s %12.3f %14s %10s", label,
                sky < 0 ? "-" : String.format(Locale.ROOT, "%.3f", sky), frame,
                bake < 0 ? "-" : String.format(Locale.ROOT, "%.3f", bake),
                frames == 0 ? "-" : String.format(Locale.ROOT, "%d/%d", bakes, frames)));
    }
}

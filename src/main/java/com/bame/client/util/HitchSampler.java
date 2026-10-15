package com.bame.client.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.LockSupport;

/**
 * Hitch-Sampler (Diagnose): ein Daemon-Watchdog beobachtet den Client-Thread. Der Client-Thread setzt pro Frame nur
 * zwei Zeitstempel (Beginn in {@code MinecraftClient.render}, Ende vor dem FPS-Limiter bzw. am Methodenende). Dauert ein
 * Frame länger als {@link #THRESHOLD_NANOS} (25 ms), liest der Watchdog währenddessen etwa jede Millisekunde den
 * Stacktrace des Client-Threads; nach dem Frame wird eine kompakte Zusammenfassung (höchstens 30 Zeilen) asynchron nach
 * {@code config/bameclient/hitches.log} geschrieben (rotierend, ~1 MB, eine Vorgängerdatei {@code hitches.log.1}).
 *
 * <p>Im Normalbetrieb kostet das auf dem Client-Thread nur wenige volatile Schreibzugriffe pro Frame; der Watchdog
 * schläft bis zum Ablauf der Schwelle des laufenden Frames. Standardmäßig aus; einschalten über {@code hitchLoggerDebug=true} in
 * {@code config/CaeserClient/Settings/settings.properties} oder {@code -Dcaeser.hitchLogger=true}; die Schwelle über
 * {@code -Dcaeser.hitchThresholdMs=…}. Frames, in denen ein Overlay (Laden der Ressourcen) aktiv ist, werden übergangen.
 *
 * <p>Im Log stehen nur Klassen, Methoden und Zeilennummern (in der Produktionsumgebung mit Intermediary-Namen für
 * Minecraft-Klassen, z. B. {@code class_310.method_1523}), Dauer und Screen-Klassennamen – keine Pfade, Namen oder Tokens.
 */
public final class HitchSampler {
    public static final long THRESHOLD_NANOS = Math.max(5L, Long.getLong("caeser.hitchThresholdMs", 25L)) * 1_000_000L;
    private static final long SAMPLE_INTERVAL_NANOS = 1_000_000L;
    private static final int MAX_SAMPLES = 3000;
    private static final long GIVE_UP_NANOS = 20_000_000_000L; // sehr lange Frames (z. B. Welt laden) nicht ewig sampeln
    private static final long IDLE_PARK_NANOS = Math.min(10_000_000L, THRESHOLD_NANOS / 2);
    private static final int MAX_LINES = 48;
    private static final long MAX_LOG_BYTES = 1_048_576L;
    private static final int MAX_REPORTS_PER_WINDOW = 20;
    private static final long REPORT_WINDOW_NANOS = 10_000_000_000L;

    private static volatile boolean enabled = false;
    private static Thread watchdog;
    private static volatile Thread clientThread;

    // Zustand des laufenden Frames (vom Client-Thread geschrieben, vom Watchdog gelesen)
    private static volatile long frameStart = 0L;
    private static volatile long frameSeq = 0L;
    private static volatile long endedSeq = 0L;
    private static volatile long frameEnd = 0L;
    private static volatile boolean frameLoading = false;
    // nur Client-Thread
    private static int depth = 0;
    private static boolean ended = true;

    // letzter Screen-Wechsel (Client-Thread schreibt, Watchdog liest)
    private static volatile String screenChange = null;
    private static volatile long screenChangeSeq = -1L;
    private static volatile long screenChangeNanos = 0L;

    private static long reportWindowStart = 0L; // nur Watchdog
    private static int reportsInWindow = 0;     // nur Watchdog
    private static int suppressed = 0;          // nur Watchdog

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CaeserClient-HitchLog");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static boolean sessionHeaderWritten = false; // nur Writer-Thread

    private HitchSampler() {}

    /** Beim Start aufrufen; {@code configEnabled} = {@code BameClientConfig.hitchLogger}. */
    public static synchronized void start(boolean configEnabled) {
        String prop = System.getProperty("caeser.hitchLogger");
        boolean on = prop != null ? Boolean.parseBoolean(prop.trim()) : configEnabled;
        enabled = on;
        if (!on || watchdog != null) return;
        Thread t = new Thread(HitchSampler::watchLoop, "CaeserClient-HitchWatchdog");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        watchdog = t;
        t.start();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // ------------------------------------------------------------------------------------------------
    // Markierungen vom Client-Thread (MinecraftClientMixin)
    // ------------------------------------------------------------------------------------------------

    /** Kopf von {@code MinecraftClient.render}. Verschachtelte Aufrufe (setScreenAndRender) zählen zum äußeren Frame. */
    public static void beginFrame(boolean loading) {
        if (!enabled) return;
        if (depth++ != 0) return;
        if (clientThread == null) clientThread = Thread.currentThread();
        ended = false;
        frameLoading = loading;
        frameStart = System.nanoTime();
        frameSeq = frameSeq + 1; // nach frameStart schreiben: wer die neue Nummer sieht, sieht auch den Start
    }

    /** Vor dem FPS-Limiter: die Arbeit des Frames ist fertig, das Warten zählt nicht mehr dazu. */
    public static void markIdle() {
        if (!enabled) return;
        if (depth == 1 && !ended) finishFrame();
    }

    /** Ende von {@code MinecraftClient.render}. */
    public static void endFrame() {
        if (!enabled) {
            depth = 0;
            ended = true;
            return;
        }
        if (depth > 0) depth--;
        if (depth == 0 && !ended) finishFrame();
    }

    private static void finishFrame() {
        ended = true;
        frameEnd = System.nanoTime();
        endedSeq = frameSeq;
    }

    /** {@code MinecraftClient.setScreen}: merkt den Wechsel für die Zusammenfassung (nur Klassennamen). */
    public static void noteScreenChange(Object from, Object to) {
        if (!enabled) return;
        screenChange = simpleName(from) + " -> " + simpleName(to);
        screenChangeNanos = System.nanoTime();
        screenChangeSeq = frameSeq;
    }

    private static String simpleName(Object o) {
        if (o == null) return "null";
        String n = o.getClass().getName();
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : n;
    }

    // ------------------------------------------------------------------------------------------------
    // Watchdog
    // ------------------------------------------------------------------------------------------------

    private static void watchLoop() {
        while (true) {
            try {
                if (!enabled) {
                    LockSupport.parkNanos(500_000_000L);
                    continue;
                }
                long seq = frameSeq;
                long start = frameStart;
                if (seq == 0L || endedSeq == seq) {
                    LockSupport.parkNanos(IDLE_PARK_NANOS);
                    continue;
                }
                long elapsed = System.nanoTime() - start;
                if (elapsed < THRESHOLD_NANOS) {
                    LockSupport.parkNanos(Math.max(200_000L, THRESHOLD_NANOS - elapsed));
                    continue;
                }
                if (frameLoading) {
                    // Ladebildschirm: nicht sampeln, nur auf den nächsten Frame warten
                    while (enabled && frameSeq == seq && endedSeq != seq) LockSupport.parkNanos(IDLE_PARK_NANOS);
                    continue;
                }
                sampleHitch(seq, start);
            } catch (Throwable t) {
                // Diagnose darf nie das Spiel stören: kurz warten und weitermachen
                LockSupport.parkNanos(100_000_000L);
            }
        }
    }

    private static void sampleHitch(long seq, long start) {
        Thread target = clientThread;
        if (target == null) return;
        HitchAggregator agg = new HitchAggregator();
        int taken = 0;
        while (enabled && frameSeq == seq && endedSeq != seq) {
            if (System.nanoTime() - start > GIVE_UP_NANOS) break;
            if (taken < MAX_SAMPLES) {
                StackTraceElement[] st = target.getStackTrace();
                // nur zählen, wenn der Frame beim Sampeln noch lief
                if (frameSeq == seq && endedSeq != seq) {
                    agg.add(st);
                    taken++;
                }
            }
            LockSupport.parkNanos(SAMPLE_INTERVAL_NANOS);
        }
        long end = endedSeq >= seq ? frameEnd : System.nanoTime();
        long duration = end - start;
        if (duration < THRESHOLD_NANOS || agg.samples() == 0) return;

        long now = System.nanoTime();
        if (now - reportWindowStart > REPORT_WINDOW_NANOS) {
            reportWindowStart = now;
            reportsInWindow = 0;
        }
        if (reportsInWindow >= MAX_REPORTS_PER_WINDOW) {
            suppressed++;
            return;
        }
        reportsInWindow++;

        String change = screenChangeSeq == seq ? screenChange : null;
        long changeAt = screenChangeNanos - start;
        write(format(seq, duration, agg, change, changeAt));
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

    private static String format(long seq, long durationNanos, HitchAggregator agg, String change, long changeAtNanos) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append('[').append(LocalDateTime.now().format(TIME)).append("] HITCH ")
                .append(String.format(Locale.ROOT, "%.1f ms", durationNanos / 1_000_000.0))
                .append(" | frame #").append(seq)
                .append(" | ").append(agg.samples()).append(" samples");
        if (change != null) {
            sb.append(" | screen ").append(change)
                    .append(String.format(Locale.ROOT, " @+%.1f ms", Math.max(0L, changeAtNanos) / 1_000_000.0));
        }
        if (suppressed > 0) {
            sb.append(" | ").append(suppressed).append(" earlier hitches not logged (rate limit)");
            suppressed = 0;
        }
        sb.append('\n');
        List<String> lines = agg.summarize(MAX_LINES - 1);
        for (String line : lines) sb.append(line).append('\n');
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Asynchrones, rotierendes Schreiben
    // ------------------------------------------------------------------------------------------------

    public static Path logFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("bameclient").resolve("hitches.log");
    }

    private static void write(String text) {
        try {
            WRITER.execute(() -> append(text));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private static void append(String text) {
        try {
            Path file = logFile();
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > MAX_LOG_BYTES) {
                Files.move(file, file.resolveSibling("hitches.log.1"), StandardCopyOption.REPLACE_EXISTING);
            }
            String out = text;
            if (!sessionHeaderWritten) {
                sessionHeaderWritten = true;
                out = "=== session " + LocalDateTime.now().format(TIME) + " | threshold "
                        + (THRESHOLD_NANOS / 1_000_000L) + " ms | sample interval ~1 ms ===\n" + text;
            }
            Files.writeString(file, out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }
}

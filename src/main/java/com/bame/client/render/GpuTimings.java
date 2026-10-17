package com.bame.client.render;

import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * GPU-Zeitmessung für Tests (Client-Gametest): misst über GL-Zeitstempel, wie lange die Grafikkarte für einen
 * Abschnitt braucht (z. B. den Shader-Himmel oder ein ganzes Bild). Im Spiel ausgeschaltet ({@link #enabled} = false),
 * dann kostet ein Aufruf nur die Abfrage dieses Schalters. Zeitstempel statt GL_TIME_ELAPSED, damit sich Messungen
 * verschachteln lassen. Nur Render-Thread.
 */
public final class GpuTimings {
    private GpuTimings() {}

    public static volatile boolean enabled = false;

    private record Pending(String key, int start, int end) {}

    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static final Map<String, Integer> OPEN = new HashMap<>();
    private static final Map<String, long[]> TOTALS = new HashMap<>();

    public static void begin(String key) {
        if (!enabled) return;
        int query = GL15C.glGenQueries();
        GL33C.glQueryCounter(query, GL33C.GL_TIMESTAMP);
        Integer previous = OPEN.put(key, query);
        if (previous != null) GL15C.glDeleteQueries(previous);
    }

    public static void end(String key) {
        if (!enabled) return;
        Integer start = OPEN.remove(key);
        if (start == null) return;
        int query = GL15C.glGenQueries();
        GL33C.glQueryCounter(query, GL33C.GL_TIMESTAMP);
        PENDING.add(new Pending(key, start, query));
        poll();
    }

    /** Fertige Messungen einsammeln (nicht blockierend). */
    public static void poll() {
        while (!PENDING.isEmpty()) {
            Pending p = PENDING.peek();
            if (GL15C.glGetQueryObjecti(p.end(), GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) return;
            PENDING.poll();
            long t0 = GL33C.glGetQueryObjecti64(p.start(), GL15C.GL_QUERY_RESULT);
            long t1 = GL33C.glGetQueryObjecti64(p.end(), GL15C.GL_QUERY_RESULT);
            GL15C.glDeleteQueries(p.start());
            GL15C.glDeleteQueries(p.end());
            long[] total = TOTALS.computeIfAbsent(p.key(), k -> new long[2]);
            total[0] += Math.max(0L, t1 - t0);
            total[1]++;
        }
    }

    public static void reset() {
        poll();
        TOTALS.clear();
    }

    /** Durchschnitt in Millisekunden, -1 ohne Messwerte. */
    public static double averageMs(String key) {
        poll();
        long[] total = TOTALS.get(key);
        return total == null || total[1] == 0 ? -1.0 : total[0] / 1.0e6 / total[1];
    }

    public static long samples(String key) {
        long[] total = TOTALS.get(key);
        return total == null ? 0 : total[1];
    }
}

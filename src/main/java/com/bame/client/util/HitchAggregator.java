package com.bame.client.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fasst die Stack-Samples eines zu langen Frames zusammen (reines Java, ohne Minecraft; getestet in
 * {@code HitchAggregatorTest}). Gezählt wird:
 * <ul>
 *   <li><b>self</b>: oberster Frame jedes Samples (wo der Thread gerade steht),</li>
 *   <li><b>mod</b>: inklusive Zählung der Mod-Frames ({@code com.bame.}) – jede Methode höchstens einmal pro Sample,</li>
 *   <li><b>paths</b>: die obersten {@link #PATH_DEPTH} Frames als Kette.</li>
 * </ul>
 * Ausgegeben werden nur Klassen, Methoden und Zeilennummern (keine Dateipfade, keine Werte).
 */
public final class HitchAggregator {
    public static final String MOD_PREFIX = "com.bame.";
    public static final int PATH_DEPTH = 5;
    public static final int TOP_SELF = 8;
    public static final int TOP_MOD = 6;
    public static final int TOP_PATHS = 4;
    public static final int CALLER_DEPTH = 14;
    public static final int TOP_CALLERS = 3;

    private final Map<String, int[]> self = new HashMap<>();
    private final Map<String, int[]> mod = new HashMap<>();
    private final Map<String, int[]> paths = new HashMap<>();
    private final Map<String, int[]> callers = new HashMap<>();
    private final Set<String> seenInSample = new HashSet<>();
    private int samples;
    private int samplesWithMod;

    /** Kompakte Darstellung eines Frames: {@code paket.Klasse.methode:zeile}. */
    public static String frame(String className, String methodName, int line) {
        return className + "." + methodName + ":" + line;
    }

    public static String frame(StackTraceElement e) {
        return frame(e.getClassName(), e.getMethodName(), e.getLineNumber());
    }

    public void add(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) return;
        String[] frames = new String[stack.length];
        String[] owners = new String[stack.length];
        for (int i = 0; i < stack.length; i++) {
            frames[i] = frame(stack[i]);
            owners[i] = stack[i].getClassName();
        }
        add(frames, owners);
    }

    /**
     * Ein Sample hinzufügen: {@code frames[0]} ist der oberste Frame. {@code owners[i]} ist der Klassenname von
     * {@code frames[i]} (für die Mod-Erkennung); {@code owners} darf {@code null} sein, dann zählt der Frame-Text.
     */
    public void add(String[] frames, String[] owners) {
        if (frames == null || frames.length == 0) return;
        samples++;
        inc(self, frames[0]);

        StringBuilder path = new StringBuilder();
        int depth = Math.min(PATH_DEPTH, frames.length);
        for (int i = 0; i < depth; i++) {
            if (i > 0) path.append(" < ");
            path.append(frames[i]);
        }
        inc(paths, path.toString());

        // Aufrufer hinter nativen/JDK-Frames (z. B. JNA, Reflection), damit sichtbar wird, wer den Aufruf auslöst
        int start = 0;
        while (start < frames.length && isPlumbing(owners != null && start < owners.length && owners[start] != null ? owners[start] : frames[start])) start++;
        if (start > 0 && start < frames.length) {
            StringBuilder caller = new StringBuilder();
            int end = Math.min(frames.length, start + CALLER_DEPTH);
            for (int i = start; i < end; i++) {
                if (i > start) caller.append(" < ");
                caller.append(frames[i]);
            }
            inc(callers, caller.toString());
        }

        seenInSample.clear();
        boolean anyMod = false;
        for (int i = 0; i < frames.length; i++) {
            String owner = owners != null && i < owners.length && owners[i] != null ? owners[i] : frames[i];
            if (!owner.startsWith(MOD_PREFIX) && !frames[i].contains("$bameclient$")) continue;
            anyMod = true;
            if (seenInSample.add(frames[i])) inc(mod, frames[i]);
        }
        if (anyMod) samplesWithMod++;
    }

    public int samples() {
        return samples;
    }

    public int samplesWithMod() {
        return samplesWithMod;
    }

    /** Anzahl der Samples mit diesem obersten Frame (0, wenn unbekannt). */
    public int selfCount(String frame) {
        int[] c = self.get(frame);
        return c == null ? 0 : c[0];
    }

    /** Anzahl der Samples, in denen dieser Mod-Frame vorkommt (0, wenn unbekannt). */
    public int modCount(String frame) {
        int[] c = mod.get(frame);
        return c == null ? 0 : c[0];
    }

    /**
     * Zusammenfassung ohne Kopfzeile, höchstens {@code maxLines} Zeilen. Reihenfolge: Mod-Anteil, häufigste oberste
     * Frames, Mod-Frames inklusiv, häufigste Pfade. Gleich häufige Einträge alphabetisch (stabil für Tests).
     */
    public List<String> summarize(int maxLines) {
        List<String> out = new ArrayList<>();
        if (maxLines <= 0) return out;
        if (samples == 0) {
            out.add("  (no samples)");
            return out;
        }
        add(out, maxLines, String.format(Locale.ROOT, "  mod frames in %d/%d samples (%d%%)",
                samplesWithMod, samples, Math.round(100.0 * samplesWithMod / samples)));
        section(out, maxLines, "  top self:", self, TOP_SELF);
        if (!mod.isEmpty()) section(out, maxLines, "  top mod (inclusive):", mod, TOP_MOD);
        section(out, maxLines, "  top paths (" + PATH_DEPTH + " frames, top first):", paths, TOP_PATHS);
        if (!callers.isEmpty()) section(out, maxLines, "  top callers behind native/JDK frames (" + CALLER_DEPTH + " frames):", callers, TOP_CALLERS);
        return out;
    }

    /** JDK-, JNA- und Proxy-Frames, hinter denen der eigentliche Aufrufer steckt. */
    static boolean isPlumbing(String owner) {
        return owner.startsWith("com.sun.jna.") || owner.startsWith("jdk.") || owner.startsWith("java.")
                || owner.startsWith("sun.") || owner.startsWith("org.lwjgl.");
    }

    private static void section(List<String> out, int maxLines, String title, Map<String, int[]> counts, int top) {
        if (out.size() + 2 > maxLines) return; // Überschrift ohne Inhalt lohnt nicht
        add(out, maxLines, title);
        for (Map.Entry<String, int[]> e : sorted(counts, top)) {
            if (!add(out, maxLines, String.format(Locale.ROOT, "    %4dx  %s", e.getValue()[0], e.getKey()))) return;
        }
    }

    private static boolean add(List<String> out, int maxLines, String line) {
        if (out.size() >= maxLines) return false;
        out.add(line);
        return true;
    }

    static List<Map.Entry<String, int[]>> sorted(Map<String, int[]> counts, int top) {
        List<Map.Entry<String, int[]>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> {
            int c = Integer.compare(b.getValue()[0], a.getValue()[0]);
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });
        return list.size() > top ? list.subList(0, top) : list;
    }

    private static void inc(Map<String, int[]> map, String key) {
        map.computeIfAbsent(key, k -> new int[1])[0]++;
    }
}

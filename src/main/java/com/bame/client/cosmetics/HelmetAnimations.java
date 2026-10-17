package com.bame.client.cosmetics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * IDs und Animationen von Samurai-Helm und Ritterhelm (Slot HEAD) sowie VR-Headset und Augenklappe (Slot FACE). Reine
 * Mathematik ohne Minecraft, damit sie ohne Client testbar ist (HatsGeometryTest, BackGeometryTest). Zustand pro Spieler
 * (Visier, Federbusch, Lauf-Blende, Schwung-Pruefung) liegt in einer kleinen Tabelle, die nicht mehr gesehene Spieler nach
 * {@link #STALE_MS} wieder entfernt.
 * <p>
 * Winkel in Grad in der Animations-Konvention von {@link BedrockCosmeticModel} mit {@code blockbenchAxisFix}:
 * <ul>
 *   <li>Lamellen (haengen nach unten): hinten positiver Pitch = Unterkante nach hinten (aussen), Seiten: links negativer,
 *       rechts positiver Roll = Unterkante nach aussen. {@link #lameSwing} liefert den Ausschlag (immer &ge; 0).</li>
 *   <li>Visier (Pivot = Scharnier): Pitch 0 = geschlossen (Ruhelage des Modells), negativ = hochgeklappt.</li>
 *   <li>Federbusch: {@link #plumePose} liefert je Segment {Rueckbiegung, Roll}; der Renderer setzt Pitch = -Rueckbiegung.</li>
 * </ul>
 */
public final class HelmetAnimations {

    private HelmetAnimations() {}

    // ==========================================
    // IDs (Mod-IDs klein; Shop-IDs = dieselben in GROSS)
    // ==========================================

    public static final String SAMURAI_HELMET = "samurai_helmet";
    public static final String KNIGHT_HELMET = "knight_helmet";
    public static final String VR_HEADSET = "vr_headset";
    public static final String EYE_PATCH = "eye_patch";

    /** Hut-Slot: Helme mit eigener Animation. */
    public static boolean isHelmet(String id) {
        if (id == null) return false;
        String s = id.toLowerCase(Locale.ROOT);
        return SAMURAI_HELMET.equals(s) || KNIGHT_HELMET.equals(s);
    }

    /** Gesichts-Slot: VR-Headset und Augenklappe. */
    public static boolean isNewFace(String id) {
        if (id == null) return false;
        String s = id.toLowerCase(Locale.ROOT);
        return VR_HEADSET.equals(s) || EYE_PATCH.equals(s);
    }

    // ==========================================
    // Samurai: Shikoro (Lamellen) und Goldglanz der Hoerner
    // ==========================================

    /** Anzahl der Lamellen-Reihen (Knochen shikoro0..3 hinten, shikoro{i}_l / _r an den Seiten). */
    public static final int SHIKORO_ROWS = 4;
    /** Hoechster eigener Ausschlag einer Reihe (Grad); die Reihen sind verschachtelt, unten addiert es sich auf. */
    public static final float LAME_MAX_DEG = 3.4f;

    /** Teil einer Reihe: hinten (inkl. Ecken), links, rechts. */
    public static final int LAME_BACK = 0, LAME_LEFT = 1, LAME_RIGHT = 2;

    /**
     * Ausschlag (Grad, &ge; 0 = nur nach aussen) von Reihe {@code row} (0 oben .. 3 unten), Teil {@code part}: beim Laufen
     * schwingen die Reihen im Schritt leicht versetzt (untere Reihen spaeter und etwas weiter) mit kurzem "Klappern", im
     * Stand nur ein Hauch. Stetig in allen Argumenten.
     *
     * @param limbPhase Lauf-Phase (limbSwingAnimationProgress)
     * @param walk      Lauf-Blende 0..1 (geglaettet aus der Lauf-Amplitude)
     */
    public static float lameSwing(int row, int part, float limbPhase, float walk, float timeSec) {
        walk = clamp(walk, 0.0f, 1.0f);
        float step = limbPhase * 0.6662f;
        float sideShift = part == LAME_LEFT ? 0.7f : (part == LAME_RIGHT ? 0.7f + (float) Math.PI : 0.0f);
        float amp = (1.15f + 0.4f * row) * walk;
        float swing = amp * (0.5f + 0.5f * (float) Math.sin(2.0f * step - row * 0.85f + sideShift));
        // Klappern: kurze, spitze Ausschlaege kurz nach jedem Schritt (je Reihe versetzt)
        float tick = (float) Math.sin(4.0f * step - row * 1.3f + part * 0.9f);
        float clatter = tick > 0.0f ? 0.5f * walk * (float) Math.pow(tick, 6.0) : 0.0f;
        float idle = (0.07f + 0.03f * row) * (0.5f + 0.5f * (float) Math.sin(timeSec * 1.3f + row * 0.7f + part * 1.9f));
        return clamp(swing + clatter + idle, 0.0f, LAME_MAX_DEG);
    }

    /** Anzahl der Glanz-Knochen glint0..glint6 entlang der Hoerner (0 = Ansatz). */
    public static final int HORN_GLINTS = 7;

    /**
     * Deckkraft (0..1) des Glanz-Overlays {@code i}: dezenter Grundglanz, alle paar Sekunden laeuft ein weicher Goldschimmer
     * vom Ansatz zur Spitze. Deterministisch je Spieler (seed), stetig.
     */
    public static float hornGlint(int i, float timeSec, long seed) {
        float period = 5.5f + (seed & 3) * 0.4f;
        float u = frac(timeSec / period + ((seed >>> 8) & 0xFF) / 255.0f);
        float pos = u * (HORN_GLINTS + 3.0f) - 1.5f;
        float k = 1.0f - Math.abs(i - pos) / 1.6f;
        float shine = k > 0.0f ? k * k * (3.0f - 2.0f * k) : 0.0f;
        return 0.06f + 0.55f * shine;
    }

    // ==========================================
    // Ritterhelm: Visier
    // ==========================================

    /** Hochgeklappt (Grad, als negativer Pitch gesetzt). Kollisionsfrei bis hierher (Generator-Pruefung, Test). */
    public static final float VISOR_OPEN_DEG = 62.0f;
    /** So lange nach dem letzten Kampf-Signal (Angriff/Treffer) bleibt das Visier unten. */
    public static final long COMBAT_HOLD_MS = 5_000L;
    /** Dauer Zuklappen (schnell) bzw. Hochklappen (sanft), Sekunden. */
    public static final float VISOR_CLOSE_S = 0.16f, VISOR_OPEN_S = 0.75f;

    /** Visier-Pitch (Grad) fuer Geschlossenheit c (0 = offen, 1 = zu): weich (smoothstep) zwischen -OPEN und 0. */
    public static float visorPitch(float closed) {
        float c = clamp(closed, 0.0f, 1.0f);
        float e = c * c * (3.0f - 2.0f * c);
        return -VISOR_OPEN_DEG * (1.0f - e);
    }

    /** Gilt der Spieler noch als im Kampf (letztes Signal hoechstens {@link #COMBAT_HOLD_MS} her)? */
    public static boolean inCombat(long nowMs, long lastCombatMs) {
        return nowMs - lastCombatMs <= COMBAT_HOLD_MS;
    }

    /** Visier-Zustand eines Spielers. */
    public static final class Visor {
        /** 0 = offen (hochgeklappt), 1 = geschlossen. */
        public float closed;
        public long lastCombatMs = Long.MIN_VALUE / 4;

        /** Kampf-Signal (Angriff/Schwung bzw. Treffer) merken und das Visier zum Ziel fuehren (zu: schnell, auf: sanft). */
        public void update(long nowMs, boolean combat, float dt) {
            if (combat) lastCombatMs = nowMs;
            float target = inCombat(nowMs, lastCombatMs) ? 1.0f : 0.0f;
            float d = clamp(dt, 0.0f, 0.25f);
            if (target > closed) closed = Math.min(target, closed + d / VISOR_CLOSE_S);
            else closed = Math.max(target, closed - d / VISOR_OPEN_S);
        }
    }

    /** Karten-Vorschau: Visier zyklisch offen (2.4 s), zu (2.3 s), dann wieder hoch (stetig, Periode 5.6 s). */
    public static float cardVisorClosed(float timeSec) {
        float t = frac(timeSec / 5.6f) * 5.6f;
        if (t < 2.4f) return 0.0f;
        if (t < 2.4f + VISOR_CLOSE_S) return (t - 2.4f) / VISOR_CLOSE_S;
        if (t < 4.85f) return 1.0f;
        return Math.max(0.0f, 1.0f - (t - 4.85f) / VISOR_OPEN_S);
    }

    // ==========================================
    // Ritterhelm: Federbusch (Feder-Daempfer, angetrieben von Schritt und Kopfbeschleunigung)
    // ==========================================

    public static final int PLUME_SEGMENTS = 4;
    /** Grenze je Gelenk (Grad); so bleibt der Busch ueber dem Helm und innerhalb der Modell-Grenzen. */
    public static final float PLUME_MAX_DEG = 9.0f;
    private static final float PLUME_ROLL_MAX = 3.0f;

    /** Zwei gekoppelte Daempfer: a (Ansatz, straffer) und b (Spitze, weicher, laeuft nach). Rueckbiegung in Grad. */
    public static final class Plume {
        public float a, va, b, vb;
        public float vy;
        public double lastY;
        public boolean hasY;

        /**
         * Einen Zeitschritt rechnen.
         *
         * @param walk      Lauf-Blende 0..1
         * @param limbPhase Lauf-Phase (Schritt-Takt)
         * @param y         Hoehe (Bloecke) des Spielers; Spruenge/Landungen biegen den Busch (Traegheit)
         */
        public void step(float dt, float walk, float limbPhase, double y) {
            dt = clamp(dt, 0.0f, 0.1f);
            float ay = 0.0f;
            if (hasY && dt > 1.0e-4f) {
                float v = (float) ((y - lastY) / dt);
                if (Math.abs(v) > 60.0f) v = vy;                          // Teleport/Respawn: ignorieren
                float nv = vy + (v - vy) * (1.0f - (float) Math.exp(-dt / 0.05f));
                ay = clamp((nv - vy) / dt, -45.0f, 45.0f);
                vy = nv;
            }
            lastY = y;
            hasY = true;
            float step = limbPhase * 0.6662f;
            float walkForce = 380.0f * clamp(walk, 0.0f, 1.0f) * (float) Math.sin(2.0f * step);
            int n = Math.max(1, (int) Math.ceil(dt / (1.0f / 120.0f)));
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                // Ansatz: ~2.3 Hz, Spitze: ~1.5 Hz, wenig gedaempft (wippt nach)
                float fa = -208.0f * a - 5.0f * va + 9.0f * ay + walkForce;
                va += fa * h;
                a += va * h;
                float fb = -89.0f * (b - a) - 3.2f * vb + 6.0f * ay + 0.6f * walkForce;
                vb += fb * h;
                b += vb * h;
                a = clamp(a, -PLUME_MAX_DEG, PLUME_MAX_DEG);
                b = clamp(b, -2.0f * PLUME_MAX_DEG, 2.0f * PLUME_MAX_DEG);
            }
        }
    }

    /**
     * Pose je Segment {rueck0, roll0, rueck1, roll1, ...} (Grad): Ansatz folgt a, Spitze b (Peitschen-Effekt), dazu ein
     * leichtes Wiegen; jedes Gelenk begrenzt auf {@link #PLUME_MAX_DEG}.
     */
    public static float[] plumePose(Plume p, float timeSec, long seed) {
        float[] out = new float[PLUME_SEGMENTS * 2];
        float ph = (seed & 0xFF) * 0.05f;
        for (int i = 0; i < PLUME_SEGMENTS; i++) {
            float w = i / (float) (PLUME_SEGMENTS - 1);
            float bend = (p.a * (1.0f - w) + p.b * w) * (0.45f + 0.15f * i);
            float sway = 0.9f * (float) Math.sin(timeSec * 1.7f + i * 0.55f + ph) * (0.4f + 0.2f * i);
            out[i * 2] = clamp(bend + sway, -PLUME_MAX_DEG, PLUME_MAX_DEG);
            out[i * 2 + 1] = clamp(1.4f * (float) Math.sin(timeSec * 1.1f + i * 0.7f + ph * 1.3f) * (0.3f + 0.25f * i),
                    -PLUME_ROLL_MAX, PLUME_ROLL_MAX);
        }
        return out;
    }

    // ==========================================
    // VR-Headset: Bildschirmlicht und Status-LED
    // ==========================================

    /** Bildschirm-Farben (RGB): Szenen wechseln wie bei einem Film/Spiel. */
    private static final int[] SCREEN = {0x58C8FF, 0x6C8CFF, 0xA874FF, 0x48E0C8, 0xDDEBFF, 0xFF82CC, 0x7CD6FF};
    public static final float SCENE_S = 2.8f;

    /** Farbe des Bildschirmlichts (RGB): je Szene eine Farbe, weicher Uebergang am Szenenende (stetig). */
    public static int screenRgb(float timeSec, long seed) {
        float x = timeSec / SCENE_S + (seed & 0xFF) / 97.0f;
        long k = (long) Math.floor(x);
        float f = x - k;
        int c0 = SCREEN[(int) (hash01(k, seed, 5) * SCREEN.length) % SCREEN.length];
        int c1 = SCREEN[(int) (hash01(k + 1, seed, 5) * SCREEN.length) % SCREEN.length];
        return mixRgb(c0, c1, smooth(0.82f, 1.0f, f));
    }

    /** Helligkeit 0.7..1: dezentes Flackern (mehrere Frequenzen) und ab und zu ein kurzes Abdunkeln (Szenenwechsel). */
    public static float screenFlicker(float timeSec, long seed) {
        float s = (seed & 0x3F) * 0.1f;
        float n = 0.5f * (float) Math.sin(timeSec * 7.3f + s) + 0.3f * (float) Math.sin(timeSec * 13.7f + 1.1f + s)
                + 0.2f * (float) Math.sin(timeSec * 29.3f + 2.3f);
        float x = timeSec / SCENE_S + (seed & 0xFF) / 97.0f;
        float f = x - (float) Math.floor(x);
        float dip = 1.0f - 0.12f * (float) Math.exp(-Math.pow((f - 0.91f) / 0.03f, 2));
        return clamp((0.88f + 0.08f * n) * dip, 0.7f, 1.0f);
    }

    /** ARGB fuer den selbstleuchtenden Bildschirm-Durchgang (Deckkraft = Flackern). */
    public static int screenArgb(float timeSec, long seed) {
        float fl = screenFlicker(timeSec, seed);
        int rgb = screenRgb(timeSec, seed);
        int a = Math.round(255.0f * 0.92f * fl);
        return (a << 24) | scaleRgbOnly(rgb, 0.78f + 0.22f * fl);
    }

    /** Status-LED: kuehles Weiss, langsam atmend. */
    public static int ledArgb(float timeSec) {
        float k = 0.62f + 0.38f * (0.5f + 0.5f * (float) Math.sin(timeSec * 2.1f));
        return (Math.round(255.0f * k) << 24) | 0xCFEFFF;
    }

    // ==========================================
    // Zustand pro Spieler
    // ==========================================

    public static final long STALE_MS = 10_000L;
    private static final float WALK_TAU = 0.18f;

    public static final class State {
        public final Visor visor = new Visor();
        public final Plume plume = new Plume();
        public float walk;
        public long lastNanos = -1L;
        public long lastSeenMs;
        /** Schwung-Pruefung (Angriff?): letzter Stand, Zeitpunkt und Ergebnis der letzten Pruefung. */
        public boolean wasSwinging;
        public long swingCheckMs;
        public boolean swingCombat;

        /** Sekunden seit dem letzten Aufruf (erster Aufruf: 0), begrenzt. */
        public float tick(long nanos) {
            float dt = lastNanos < 0 ? 0.0f : (nanos - lastNanos) / 1.0e9f;
            lastNanos = nanos;
            return clamp(dt, 0.0f, 0.25f);
        }

        /** Lauf-Blende weich nachfuehren (Ziel aus der Lauf-Amplitude). */
        public void updateWalk(float dt, float limbAmplitude) {
            walk = approach(walk, smooth(0.02f, 0.3f, limbAmplitude), dt, WALK_TAU);
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long lastCleanupMs = 0L;

    /** Zustand fuer Schluessel (Entity-ID; negative IDs fuer Menue-Vorschauen). Nur vom Render-Thread. */
    public static synchronized State state(int key, long nowMs) {
        if (nowMs - lastCleanupMs > 5_000L) {
            lastCleanupMs = nowMs;
            Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
            while (it.hasNext()) {
                if (nowMs - it.next().getValue().lastSeenMs > STALE_MS) it.remove();
            }
        }
        State s = STATES.computeIfAbsent(key, k -> new State());
        s.lastSeenMs = nowMs;
        return s;
    }

    public static synchronized int trackedStates() {
        return STATES.size();
    }

    // ==========================================
    // Hilfen
    // ==========================================

    public static float approach(float cur, float target, float dt, float tau) {
        dt = clamp(dt, 0.0f, 0.25f);
        return cur + (target - cur) * (1.0f - (float) Math.exp(-dt / tau));
    }

    public static float smooth(float a, float b, float x) {
        float t = clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    static int mixRgb(int c0, int c1, float t) {
        t = clamp(t, 0.0f, 1.0f);
        int r = Math.round(((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * t);
        int g = Math.round(((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * t);
        int b = Math.round((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    static int scaleRgbOnly(int rgb, float k) {
        k = clamp(k, 0.0f, 1.0f);
        int r = Math.round(((rgb >> 16) & 0xFF) * k);
        int g = Math.round(((rgb >> 8) & 0xFF) * k);
        int b = Math.round((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    public static float hash01(long a, long b, long c) {
        return HatAnimations.hash01(a, b, c);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

package com.bame.client.cosmetics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IDs und Animationen der Ruecken-Cosmetics (Slot BACK: Jetpack, Gitarre, Koecher, Katana, Rucksack), der
 * Schmetterlingsfluegel und der Glanzlichter von Gasmaske/Fliegerbrille. Reine Mathematik ohne Minecraft, damit sie ohne
 * Client testbar ist (BackGeometryTest). Zustand pro Spieler (geglaettete Bewegungs-Blenden, Flug-/Sprungzeit) liegt in
 * einer kleinen Tabelle, die nicht mehr gesehene Spieler nach {@link #STALE_MS} wieder entfernt.
 * <p>
 * Winkel in Grad in der Animations-Konvention von {@link BedrockCosmeticModel} mit {@code blockbenchAxisFix}:
 * Pack-Pitch positiv = Unterkante nach hinten (vom Koerper weg), Fluegel-Yaw links negativ/rechts positiv = nach hinten
 * zuklappen.
 */
public final class BackCosmetics {

    private BackCosmetics() {}

    // ==========================================
    // IDs
    // ==========================================

    /** Slot BACK (Mod-IDs, klein; Shop-IDs sind dieselben in GROSS). */
    public static final List<String> BACK_IDS = List.of("jetpack", "guitar", "quiver", "katana", "backpack");
    public static final String BUTTERFLY_WINGS = "butterfly_wings";
    public static final String GAS_MASK = "gas_mask";
    public static final String AVIATOR_GOGGLES = "aviator_goggles";

    public static boolean isBackItem(String id) {
        return id != null && BACK_IDS.contains(id.toLowerCase(Locale.ROOT));
    }

    /** Bekannte BACK-ID (klein) oder {@code "none"}. */
    public static String sanitizeBack(String id) {
        if (id == null) return "none";
        String s = id.trim().toLowerCase(Locale.ROOT);
        return BACK_IDS.contains(s) ? s : "none";
    }

    // ==========================================
    // Cape/Elytra: Pack steht etwas ab und haengt schraeg (Cape liegt dazwischen und haengt ruhig)
    // ==========================================

    /** Abstand (Pixel) des Packs nach hinten, wenn ein Cape bzw. eine Elytra getragen wird. */
    public static final float CAPE_OFFSET_PX = 0.85f;
    /** Neigung (Grad, Unterkante nach hinten) bei Cape: folgt dem ruhig haengenden Cape (6 Grad). */
    public static final float CAPE_TILT_DEG = 6.5f;
    /** Abstand der Schmetterlingsfluegel bei Cape. */
    public static final float WINGS_CAPE_OFFSET_PX = 1.5f;

    // ==========================================
    // Schmetterlingsfluegel
    // ==========================================

    /** Erlaubter Klappwinkel (0 = flach ausgebreitet, 90 = nach hinten zusammengeklappt). */
    public static final float FOLD_MIN = 4.0f, FOLD_MAX = 86.0f;
    public static final float IDLE_OPEN = 8.0f, IDLE_CLOSED = 62.0f;
    public static final float IDLE_PERIOD = 3.6f;
    public static final float WALK_FOLD = 76.0f, WALK_FLUTTER = 6.0f, WALK_HZ = 2.4f;
    public static final float SPRINT_FOLD = 82.0f, SPRINT_ROLL = -12.0f;
    public static final float SNEAK_FOLD = 42.0f, SNEAK_ROLL = -8.0f;
    public static final float ROLL_MIN = -14.0f, ROLL_MAX = 2.0f;

    /** Blende Laufen (0..1) aus der Lauf-Amplitude. */
    public static float walkTarget(float limbAmplitude) {
        return smooth(0.02f, 0.3f, limbAmplitude);
    }

    /**
     * Im Stand: langsames Auf- und Zuklappen (Zyklus {@link #IDLE_PERIOD} s: zuklappen, kurz halten, oeffnen), etwa jeder
     * dritte Zyklus ist eine Pause (offen, nur leichtes Atmen). Stetig, deterministisch je Spieler.
     */
    public static float idleFold(float timeSec, long seed) {
        float c = timeSec / IDLE_PERIOD;
        long k = (long) Math.floor(c);
        float ph = c - k;
        float s;
        if (hash01(k, seed, 31) < 0.34f) {
            s = 0.06f * (float) Math.pow(Math.sin(Math.PI * ph), 2); // Pause: nur Atmen
        } else if (ph < 0.42f) {
            s = smooth(0.0f, 0.42f, ph);
        } else if (ph < 0.56f) {
            s = 1.0f;
        } else {
            s = 1.0f - smooth(0.56f, 1.0f, ph);
        }
        return IDLE_OPEN + (IDLE_CLOSED - IDLE_OPEN) * s;
    }

    /**
     * Fluegel-Pose {foreFold, hindFold, roll} (Grad). Blenden (0..1, geglaettet): walk (Laufen), sprint, sneak.
     * Laufen: schnelleres, leichtes Flattern weit hinten (ausserhalb der schwingenden Arme); Sprinten: angelegt und leicht
     * nach unten; Schleichen: halb geschlossen, leicht gesenkt.
     */
    public static float[] butterflyPose(float timeSec, long seed, float walk, float sprint, float sneak) {
        walk = clamp(walk, 0, 1);
        sprint = clamp(sprint, 0, 1);
        sneak = clamp(sneak, 0, 1);
        float fold = idleFold(timeSec, seed);
        float roll = 0.6f * (float) Math.sin(timeSec * 1.1f + (seed & 0xFF) * 0.05f);
        float walkFold = WALK_FOLD + WALK_FLUTTER * (float) Math.sin(timeSec * WALK_HZ * 2.0 * Math.PI);
        fold = lerp(fold, walkFold, walk);
        roll = lerp(roll, -2.0f, walk);
        float sneakFold = SNEAK_FOLD + 5.0f * (float) Math.sin(timeSec * 0.5 * 2.0 * Math.PI);
        // Schleichen im Laufen: nicht offener als das Lauf-Flattern
        fold = lerp(fold, Math.max(sneakFold, walk * walkFold), sneak);
        roll = lerp(roll, SNEAK_ROLL, sneak);
        float sprintFold = SPRINT_FOLD + 1.5f * (float) Math.sin(timeSec * 3.5 * 2.0 * Math.PI);
        fold = lerp(fold, sprintFold, sprint);
        roll = lerp(roll, SPRINT_ROLL, sprint);
        fold = clamp(fold, FOLD_MIN, FOLD_MAX);
        // Hinterfluegel folgen etwas weniger weit (wirkt weich, kein Gleichtakt)
        float hind = clamp(fold * 0.93f + 1.0f, FOLD_MIN, FOLD_MAX);
        roll = clamp(roll, ROLL_MIN, ROLL_MAX);
        return new float[]{fold, hind, roll};
    }

    // ==========================================
    // BACK: Mitschwingen beim Laufen, Pfeile, Laterne, Jetpack-Flamme
    // ==========================================

    /** Pack {pitch, roll}: Pitch nur vom Koerper weg (nie hinein), leichtes seitliches Pendeln im Schritt. */
    public static float[] packSway(float limbPhase, float limbAmplitude, float timeSec) {
        float a = clamp(limbAmplitude, 0, 1);
        float step = limbPhase * 0.6662f;
        float pitch = 1.8f * a * (0.5f + 0.5f * (float) Math.sin(step * 2.0f));
        float roll = 1.0f * a * (float) Math.sin(step) + 0.2f * (float) Math.sin(timeSec * 1.3f);
        return new float[]{pitch, roll};
    }

    public static final float ARROW_MAX_DEG = 4.0f;

    /** Pfeil i {pitch, roll}: wippen im Schritt (je Pfeil leicht versetzt), im Stand fast ruhig. */
    public static float[] arrowWobble(int i, float limbPhase, float limbAmplitude, float timeSec) {
        float a = clamp(limbAmplitude, 0, 1);
        float step = limbPhase * 0.6662f * 2.0f + i * 1.1f;
        float amp = 0.35f + 3.2f * a;
        float pitch = amp * (float) Math.sin(step);
        float roll = amp * 0.6f * (float) Math.cos(step * 0.5f + i) + 0.2f * (float) Math.sin(timeSec * 1.7f + i);
        return new float[]{clamp(pitch, -ARROW_MAX_DEG, ARROW_MAX_DEG), clamp(roll, -ARROW_MAX_DEG, ARROW_MAX_DEG)};
    }

    /** Laterne {pitch, roll}: kleines Pendeln (begrenzt, damit sie weder in den Rucksack noch zum Arm schwingt). */
    public static float[] lanternSwing(float limbPhase, float limbAmplitude, float timeSec) {
        float a = clamp(limbAmplitude, 0, 1);
        float step = limbPhase * 0.6662f;
        float pitch = 1.0f + 2.0f * a * (float) Math.sin(step * 2.0f - 0.7f) + 0.3f * (float) Math.sin(timeSec * 1.9f);
        float roll = 2.5f * a * (float) Math.sin(step - 0.6f) + 0.4f * (float) Math.sin(timeSec * 1.4f);
        return new float[]{clamp(pitch, -1.5f, 3.5f), clamp(roll, -3.0f, 3.0f)};
    }

    /** Kerzen-/Duesen-Flackern 0.72..1, deterministisch. */
    public static float flicker(float timeSec, long seed) {
        float n = 0.5f * (float) Math.sin(timeSec * 23.0f + (seed & 0xFF)) + 0.3f * (float) Math.sin(timeSec * 37.0f + 1.3f)
                + 0.2f * (float) Math.sin(timeSec * 61.0f + 2.1f);
        return 0.86f + 0.14f * n;
    }

    /** Duesenmitte des linken Tanks (Bedrock-Pixel, Koerperraum; rechts gespiegelt) = Pivot des Knochens flame_l. */
    public static final float JET_NOZZLE_X = 1.75f, JET_NOZZLE_Y = 12.0f, JET_NOZZLE_Z = 4.43f;

    public static final float JET_BURST_S = 0.45f;
    /** Fallgeschwindigkeit (Bloecke/s), ab der die Duesen beim Fallen bremsen. */
    public static final float JET_FALL_SPEED = 7.0f;

    /**
     * Ziel-Staerke (0..1) der Jetpack-Flamme: kurzer Schub nach dem Absprung, beim schnellen Fallen ein ruhiger
     * Bremsschub; am Boden aus.
     */
    public static float jetTarget(boolean onGround, float airTime, float vy) {
        if (onGround) return 0.0f;
        float burst = airTime < JET_BURST_S ? 1.0f - 0.4f * (airTime / JET_BURST_S) : 0.0f;
        float fall = smooth(JET_FALL_SPEED, JET_FALL_SPEED + 6.0f, -vy) * 0.75f;
        return Math.max(burst, fall);
    }

    // ==========================================
    // Glanzlicht der Glaeser (Gasmaske, Fliegerbrille)
    // ==========================================

    /** Deckkraft des Glanzlichts: dezenter Grundglanz, alle paar Sekunden ein weicher heller Schimmer. */
    public static float lensGlint(float timeSec, long seed) {
        float period = 4.6f + (seed & 7) * 0.15f;
        float u = frac(timeSec / period + (seed >>> 8 & 0xFF) / 255.0f);
        float k = 1.0f - Math.abs(u - 0.5f) / 0.09f;
        float pulse = k > 0 ? k * k * (3 - 2 * k) : 0.0f;
        return 0.28f + 0.72f * pulse;
    }

    // ==========================================
    // Zustand pro Spieler
    // ==========================================

    public static final long STALE_MS = 10_000L;
    private static final float BLEND_TAU = 0.18f;
    private static final float JET_TAU_UP = 0.05f, JET_TAU_DOWN = 0.16f;

    public static final class State {
        public float walk, sprint, sneak, jet;
        public float airTime;
        public boolean wasOnGround = true;
        public double lastY;
        public boolean hasY;
        public float vy;
        public long lastNanos = -1L;
        public long lastSeenMs;
        public long lastParticleMs;

        /** Sekunden seit dem letzten Aufruf (erster Aufruf: 0), begrenzt. */
        public float tick(long nanos) {
            float dt = lastNanos < 0 ? 0.0f : (nanos - lastNanos) / 1.0e9f;
            lastNanos = nanos;
            return clamp(dt, 0.0f, 0.25f);
        }

        /** Bewegungs-Blenden weich nachfuehren. */
        public void update(float dt, float limbAmplitude, boolean sprinting, boolean sneaking) {
            walk = approach(walk, walkTarget(limbAmplitude), dt, BLEND_TAU);
            sprint = approach(sprint, sprinting ? 1.0f : 0.0f, dt, BLEND_TAU);
            sneak = approach(sneak, sneaking ? 1.0f : 0.0f, dt, BLEND_TAU);
        }

        /** Hoehe (Bloecke) und Bodenkontakt: Luftzeit, vertikale Geschwindigkeit, Flammen-Blende. */
        public void updateAir(float dt, double y, boolean onGround) {
            if (hasY && dt > 1.0e-4f) {
                float v = (float) ((y - lastY) / dt);
                vy = vy + (v - vy) * (1.0f - (float) Math.exp(-dt / 0.08f));
            }
            lastY = y;
            hasY = true;
            if (onGround) airTime = 0.0f;
            else airTime = wasOnGround ? 0.0f : airTime + dt;
            wasOnGround = onGround;
            float target = jetTarget(onGround, airTime, vy);
            jet = approach(jet, target, dt, target > jet ? JET_TAU_UP : JET_TAU_DOWN);
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

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
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

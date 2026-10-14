package com.bame.client.cosmetics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Animationen der Hut-Cosmetics Krone, Kopfhoerer, Propellermuetze und Katzenohren. Reine Mathematik ohne Minecraft,
 * damit sie ohne Client testbar ist (HatsGeometryTest). Zustand pro Spieler (Propeller-Drehung, Schleich-Blende) liegt
 * in einer kleinen Tabelle, die nicht mehr gesehene Spieler nach {@link #STALE_MS} wieder entfernt.
 */
public final class HatAnimations {

    private HatAnimations() {}

    // ==========================================
    // Krone: gestaffelt aufblitzende Glanzlichter + seltener Funke
    // ==========================================

    /** Anzahl der Glanzlicht-Knochen glint0..glint9 im Kronen-Modell. */
    public static final int CROWN_GLINTS = 10;

    /** Position der Glanzlichter (Bedrock-Pixel, Ruhelage), fuer Funken-Partikel. */
    public static final float[][] CROWN_GLINT_POS = {
            {0.000f, 32.400f, -5.330f},
            {5.330f, 32.400f, 0.000f},
            {0.000f, 32.400f, 5.330f},
            {-5.330f, 32.400f, 0.000f},
            {0.000f, 36.220f, -5.095f},
            {4.734f, 35.050f, -4.734f},
            {5.095f, 36.220f, 0.000f},
            {-4.734f, 35.050f, -4.734f},
            {0.000f, 36.220f, 5.095f},
            {-5.095f, 36.220f, 0.000f}
    };

    private static final float GLINT_WIDTH = 0.085f;

    /** Helligkeit (0..1) von Glanzlicht i: kurzer, weicher Blitz, je Glanzlicht eigene Periode und Phase. */
    public static float crownGlint(int i, float timeSec) {
        float period = 2.6f + (i % 3) * 0.37f;
        float phase = frac(i * 0.6180339f);
        float u = frac(timeSec / period + phase);
        float k = 1.0f - Math.abs(u - 0.5f) / GLINT_WIDTH;
        if (k <= 0.0f) return 0.0f;
        return k * k * (3.0f - 2.0f * k);
    }

    /** Sekunden-Slot fuer Funken: in Slot n (Laenge {@link #SPARKLE_SLOT}) gibt es hoechstens einen Funken. */
    public static final float SPARKLE_SLOT = 1.3f;

    /**
     * Glanzlicht-Index, an dem im Slot {@code slot} ein Funke erscheint, oder -1 (selten: ca. jeder vierte Slot).
     */
    public static int crownSparkle(long slot, long seed) {
        if (hash01(slot, seed, 71) > 0.26f) return -1;
        return (int) (hash01(slot, seed, 72) * CROWN_GLINTS) % CROWN_GLINTS;
    }

    // ==========================================
    // Kopfhoerer: sanft pulsierendes Logo
    // ==========================================

    /** Caeser-Lila (Theme "Ultraviolet"): Logo-Farbe fremder Spieler, deren Theme wir nicht kennen. */
    public static final int CAESER_PURPLE = 0xB23CEE;

    public static float headphonePulse(float timeSec) {
        return 0.78f + 0.22f * (0.5f + 0.5f * (float) Math.sin(timeSec * 2.4f));
    }

    /** RGB mit Faktor k (0..1) skaliert, volle Deckkraft. */
    public static int scaleRgb(int rgb, float k) {
        k = clamp(k, 0.0f, 1.0f);
        int r = Math.round(((rgb >> 16) & 0xFF) * k);
        int g = Math.round(((rgb >> 8) & 0xFF) * k);
        int b = Math.round((rgb & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ==========================================
    // Propellermuetze
    // ==========================================

    public static final float PROPELLER_IDLE_DPS = 150.0f;
    public static final float PROPELLER_MAX_DPS = 1800.0f;
    private static final float PROPELLER_ACCEL_TAU = 0.4f;
    private static final float PROPELLER_DECEL_TAU = 1.1f;

    /** Zieldrehzahl (Grad/s) aus der Lauf-Amplitude (limbSwingAmplitude, 0 = steht, ~1 = sprintet). */
    public static float propellerTargetSpeed(float limbAmplitude) {
        float a = clamp(limbAmplitude / 0.9f, 0.0f, 1.0f);
        float s = a * a * (3.0f - 2.0f * a);
        return PROPELLER_IDLE_DPS + (PROPELLER_MAX_DPS - PROPELLER_IDLE_DPS) * s;
    }

    /** Propeller-Zustand: Winkel (Grad) und aktuelle Drehzahl, weich beschleunigt/abgebremst. */
    public static final class Spinner {
        public float angle;
        public float speed = PROPELLER_IDLE_DPS;

        public void advance(float target, float dt) {
            dt = clamp(dt, 0.0f, 0.1f);
            float tau = target > speed ? PROPELLER_ACCEL_TAU : PROPELLER_DECEL_TAU;
            speed += (target - speed) * (1.0f - (float) Math.exp(-dt / tau));
            angle = (angle + speed * dt) % 360.0f;
        }
    }

    // ==========================================
    // Katzenohren
    // ==========================================

    /** Laenge eines Zuck-Slots (s); pro Slot zuckt hoechstens ein Ohr. */
    public static final float TWITCH_SLOT = 1.0f;
    public static final float TWITCH_LEN = 0.32f;
    private static final float TWITCH_CHANCE = 0.22f;
    public static final float FLATTEN_PITCH = -48.0f;
    public static final float FLATTEN_ROLL = 10.0f;
    public static final float TWITCH_ROLL = 14.0f;
    public static final float TWITCH_PITCH = -10.0f;
    private static final float SNEAK_TAU = 0.12f;

    /** Zuck-Staerke (0..1) von Ohr {@code ear} (0 = links, 1 = rechts); deterministisch aus Zeit und Spieler-Seed. */
    public static float earTwitch(float timeSec, long seed, int ear) {
        long slot = (long) Math.floor(timeSec / TWITCH_SLOT);
        if (hash01(slot, seed, 11) > TWITCH_CHANCE) return 0.0f;
        int which = hash01(slot, seed, 12) < 0.5f ? 0 : 1;
        if (which != ear) return 0.0f;
        float start = slot * TWITCH_SLOT + 0.08f + 0.5f * hash01(slot, seed, 13);
        float u = (timeSec - start) / TWITCH_LEN;
        if (u <= 0.0f || u >= 1.0f) return 0.0f;
        float s = (float) Math.sin(Math.PI * u);
        // schneller Doppel-Schlag: Grundbogen mit kleiner Nachfederung
        return s * s * (0.8f + 0.2f * (float) Math.cos(4.0 * Math.PI * u));
    }

    /**
     * Ohr-Rotationen in Modell-Grad {pitchL, rollL, pitchR, rollR} (Animations-Konvention von BedrockCosmeticModel
     * mit blockbenchAxisFix: negativer Pitch = nach hinten, Roll nach aussen = links positiv, rechts negativ).
     *
     * @param sneak 0 = aufrecht, 1 = ganz angelegt (geglaettet, siehe {@link #approach})
     */
    public static float[] catEarPose(float timeSec, long seed, float sneak) {
        sneak = clamp(sneak, 0.0f, 1.0f);
        float sway = (float) Math.sin(timeSec * 1.3f + (seed & 0xFF) * 0.1f) * 1.5f;
        float[] out = new float[4];
        for (int ear = 0; ear < 2; ear++) {
            float tw = earTwitch(timeSec, seed, ear) * (1.0f - 0.5f * sneak);
            float pitch = FLATTEN_PITCH * sneak + TWITCH_PITCH * tw + sway * 0.4f;
            float rollOut = FLATTEN_ROLL * sneak + TWITCH_ROLL * tw + sway;
            out[ear * 2] = pitch;
            out[ear * 2 + 1] = ear == 0 ? rollOut : -rollOut;
        }
        return out;
    }

    /** Exponentielle Annaeherung an target mit Zeitkonstante tau (stetig, unabhaengig von der Bildrate). */
    public static float approach(float cur, float target, float dt, float tau) {
        dt = clamp(dt, 0.0f, 0.25f);
        return cur + (target - cur) * (1.0f - (float) Math.exp(-dt / tau));
    }

    public static float sneakStep(float cur, boolean sneaking, float dt) {
        return approach(cur, sneaking ? 1.0f : 0.0f, dt, SNEAK_TAU);
    }

    // ==========================================
    // Zustand pro Spieler
    // ==========================================

    public static final long STALE_MS = 10_000L;

    public static final class PlayerHatState {
        public final Spinner spinner = new Spinner();
        public float sneak;
        public long lastNanos = -1L;
        public long lastSeenMs;
        public long sparkleSlot = Long.MIN_VALUE;

        /** Sekunden seit dem letzten Aufruf (erster Aufruf: 0). */
        public float tick(long nanos) {
            float dt = lastNanos < 0 ? 0.0f : (nanos - lastNanos) / 1.0e9f;
            lastNanos = nanos;
            return Math.max(0.0f, dt);
        }
    }

    private static final Map<Integer, PlayerHatState> STATES = new HashMap<>();
    private static long lastCleanupMs = 0L;

    /** Zustand fuer Schluessel (Entity-ID; negative IDs fuer Menue-Vorschauen). Nur vom Render-Thread. */
    public static synchronized PlayerHatState state(int key, long nowMs) {
        if (nowMs - lastCleanupMs > 5_000L) {
            lastCleanupMs = nowMs;
            Iterator<Map.Entry<Integer, PlayerHatState>> it = STATES.entrySet().iterator();
            while (it.hasNext()) {
                if (nowMs - it.next().getValue().lastSeenMs > STALE_MS) it.remove();
            }
        }
        PlayerHatState s = STATES.computeIfAbsent(key, k -> new PlayerHatState());
        s.lastSeenMs = nowMs;
        return s;
    }

    public static synchronized int trackedStates() {
        return STATES.size();
    }

    // ==========================================
    // Hilfen
    // ==========================================

    public static float hash01(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L;
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 32);
        return (h >>> 40) / (float) (1L << 24);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

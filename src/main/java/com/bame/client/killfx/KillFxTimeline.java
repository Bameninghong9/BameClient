package com.bame.client.killfx;

/**
 * Reine Zeitverläufe der Kill-Effekte Sarg, Amboss, Thanos Snap und Hell Gate (ohne Minecraft, testbar). Alle Zeiten in Millisekunden seit
 * dem Start des Effekts, Längen in Blöcken.
 */
public final class KillFxTimeline {

    private KillFxTimeline() {}

    // ==========================================
    // Sarg (3,2 s)
    // ==========================================

    public static final long COFFIN_TOTAL_MS = 3_200L;
    /** Aus dem Boden steigen (0 bis 0,8 s). */
    public static final long COFFIN_RISE_MS = 800L;
    /** Der Deckel beginnt zuzufallen. */
    public static final long COFFIN_LID_START_MS = 1_000L;
    /** Der Deckel schlägt zu ("Klack"). */
    public static final long COFFIN_KLACK_MS = 1_400L;
    /** Kurzer Nachfederer des Deckels nach dem Zuschlagen. */
    public static final long COFFIN_REBOUND_MS = 160L;
    /** Leise Glocke nach dem Klack. */
    public static final long COFFIN_BELL_MS = 1_650L;
    /** Versinken (2,2 bis 3,2 s). */
    public static final long COFFIN_SINK_START_MS = 2_200L;
    /** Deckel offen (Grad, um das Scharnier an der Längskante). */
    public static final float COFFIN_LID_OPEN_DEG = 105.0F;

    /**
     * Wie tief der Sarg im Boden steckt: 1 = ganz vergraben, 0 = steht auf dem Boden. Aufsteigen mit Abbremsen,
     * Versinken beschleunigt.
     */
    public static float coffinBurial(long ms) {
        if (ms <= 0L) return 1.0F;
        if (ms < COFFIN_RISE_MS) {
            float p = 1.0F - ms / (float) COFFIN_RISE_MS;
            return p * p * p;
        }
        if (ms < COFFIN_SINK_START_MS) return 0.0F;
        if (ms < COFFIN_TOTAL_MS) {
            float p = (ms - COFFIN_SINK_START_MS) / (float) (COFFIN_TOTAL_MS - COFFIN_SINK_START_MS);
            return p * p;
        }
        return 1.0F;
    }

    /** Leichtes Wackeln um die Längsachse (Grad): beim Aufsteigen, beim Zuschlagen und beim Versinken. */
    public static float coffinWobbleDeg(long ms) {
        if (ms < 0L) return 0.0F;
        if (ms < COFFIN_RISE_MS) {
            float p = ms / (float) COFFIN_RISE_MS;
            return (float) (Math.sin(p * Math.PI * 4.0) * 3.5 * (1.0F - p));
        }
        if (ms >= COFFIN_KLACK_MS && ms < COFFIN_KLACK_MS + 250L) {
            float q = (ms - COFFIN_KLACK_MS) / 250.0F;
            return (float) (Math.sin(q * Math.PI * 3.0) * 1.2 * (1.0F - q));
        }
        if (ms >= COFFIN_SINK_START_MS && ms < COFFIN_TOTAL_MS) {
            float p = (ms - COFFIN_SINK_START_MS) / (float) (COFFIN_TOTAL_MS - COFFIN_SINK_START_MS);
            return (float) (Math.sin(p * Math.PI * 5.0) * 2.0 * p);
        }
        return 0.0F;
    }

    /** Öffnungswinkel des Deckels (Grad): offen, fällt beschleunigt zu (wie unter Schwerkraft), federt kurz nach. */
    public static float coffinLidDeg(long ms) {
        if (ms < COFFIN_LID_START_MS) return COFFIN_LID_OPEN_DEG;
        if (ms < COFFIN_KLACK_MS) {
            float p = (ms - COFFIN_LID_START_MS) / (float) (COFFIN_KLACK_MS - COFFIN_LID_START_MS);
            return COFFIN_LID_OPEN_DEG * (1.0F - p * p);
        }
        if (ms < COFFIN_KLACK_MS + COFFIN_REBOUND_MS) {
            float q = (ms - COFFIN_KLACK_MS) / (float) COFFIN_REBOUND_MS;
            return (float) (Math.sin(q * Math.PI) * 5.0);
        }
        return 0.0F;
    }

    /** Größe der Erdklumpen um den Sarg (0..1): erscheinen am Anfang, verschwinden in den letzten 0,2 s. */
    public static float coffinMound(long ms) {
        if (ms < 0L || ms >= COFFIN_TOTAL_MS) return 0.0F;
        if (ms < 200L) return ms / 200.0F;
        if (ms > COFFIN_TOTAL_MS - 200L) return (COFFIN_TOTAL_MS - ms) / 200.0F;
        return 1.0F;
    }

    // ==========================================
    // Amboss (2,6 s)
    // ==========================================

    public static final long ANVIL_TOTAL_MS = 2_600L;
    /** Figur ploppt auf (0 bis 0,2 s). */
    public static final long ANVIL_POP_MS = 200L;
    /** Amboss fällt (0,4 bis 1,1 s). */
    public static final long ANVIL_FALL_START_MS = 400L;
    public static final long ANVIL_IMPACT_MS = 1_100L;
    /** So schnell wird die Figur plattgedrückt. */
    public static final long ANVIL_SQUASH_MS = 80L;
    /** Ende des kleinen Nachhüpfers des Ambosses. */
    public static final long ANVIL_BOUNCE_END_MS = 1_400L;
    /** Figur und Amboss versinken (2,0 bis 2,6 s). */
    public static final long ANVIL_SINK_START_MS = 2_000L;
    /** Fallhöhe über dem Kopf. */
    public static final float ANVIL_DROP_HEIGHT = 8.0F;
    /** Höhe des Kopfes (Oberkante der Figur). */
    public static final float FIGURE_HEIGHT = 1.85F;
    public static final float SQUASH_Y = 0.12F;
    public static final float SQUASH_XZ = 1.35F;
    public static final float ANVIL_BOUNCE_HEIGHT = 0.3F;
    /** So tief versinken Figur und Amboss am Ende (Blöcke). */
    public static final float ANVIL_SINK_DEPTH = 1.4F;

    /** Aufploppen der Figur (Skalierung, mit kurzem Überschwingen). */
    public static float figurePop(long ms) {
        if (ms <= 0L) return 0.0F;
        if (ms >= ANVIL_POP_MS) return 1.0F;
        float p = ms / (float) ANVIL_POP_MS - 1.0F;
        float c1 = 1.70158F, c3 = c1 + 1.0F;
        return 1.0F + c3 * p * p * p + c1 * p * p;
    }

    /** Wie weit die Figur plattgedrückt ist (0 = normal, 1 = platt). */
    public static float squashAmount(long ms) {
        if (ms < ANVIL_IMPACT_MS) return 0.0F;
        float p = Math.min(1.0F, (ms - ANVIL_IMPACT_MS) / (float) ANVIL_SQUASH_MS);
        return 1.0F - (1.0F - p) * (1.0F - p);
    }

    /** Höhen-Skalierung der Figur. */
    public static float squashY(long ms) {
        return 1.0F + (SQUASH_Y - 1.0F) * squashAmount(ms);
    }

    /** Breiten-Skalierung der Figur (mit leichtem Nachschwingen nach dem Aufprall, cartoon-artig). */
    public static float squashXZ(long ms) {
        float a = squashAmount(ms);
        float base = 1.0F + (SQUASH_XZ - 1.0F) * a;
        long after = ms - ANVIL_IMPACT_MS - ANVIL_SQUASH_MS;
        if (after > 0L && after < 300L) {
            float q = after / 300.0F;
            base += (float) (Math.sin(q * Math.PI * 3.0) * 0.08 * (1.0F - q));
        }
        return base;
    }

    /** Ist der Amboss zu sehen? */
    public static boolean anvilVisible(long ms) {
        return ms >= ANVIL_FALL_START_MS && ms < ANVIL_TOTAL_MS;
    }

    /** Ruhehöhe des Ambosses (Unterkante) auf der plattgedrückten Figur. */
    public static float anvilRestHeight() {
        return FIGURE_HEIGHT * SQUASH_Y;
    }

    /** Unterkante des Ambosses über dem Boden: beschleunigter Fall, folgt beim Plattdrücken dem Kopf, hüpft kurz. */
    public static float anvilHeight(long ms) {
        if (ms < ANVIL_FALL_START_MS) return FIGURE_HEIGHT + ANVIL_DROP_HEIGHT;
        if (ms < ANVIL_IMPACT_MS) {
            float p = (ms - ANVIL_FALL_START_MS) / (float) (ANVIL_IMPACT_MS - ANVIL_FALL_START_MS);
            return FIGURE_HEIGHT + ANVIL_DROP_HEIGHT * (1.0F - p * p);
        }
        long squashEnd = ANVIL_IMPACT_MS + ANVIL_SQUASH_MS;
        if (ms < squashEnd) return FIGURE_HEIGHT * squashY(ms);
        float rest = anvilRestHeight();
        if (ms < ANVIL_BOUNCE_END_MS) {
            float q = (ms - squashEnd) / (float) (ANVIL_BOUNCE_END_MS - squashEnd);
            return rest + ANVIL_BOUNCE_HEIGHT * 4.0F * q * (1.0F - q);
        }
        return rest;
    }

    /** Wie tief Figur und Amboss am Ende im Boden stecken (Blöcke). */
    public static float anvilSink(long ms) {
        if (ms < ANVIL_SINK_START_MS) return 0.0F;
        float p = Math.min(1.0F, (ms - ANVIL_SINK_START_MS) / (float) (ANVIL_TOTAL_MS - ANVIL_SINK_START_MS));
        return ANVIL_SINK_DEPTH * p * p;
    }

    /** Arme vor Schreck leicht gehoben (0..1): ab 0,5 s, voll ab 0,9 s, bis zum Aufprall. */
    public static float armRaise(long ms) {
        if (ms < 500L || ms >= ANVIL_IMPACT_MS) return 0.0F;
        if (ms >= 900L) return 1.0F;
        float p = (ms - 500L) / 400.0F;
        return p * p * (3.0F - 2.0F * p);
    }

    /** Blick nach oben zum Amboss (0..1): ab dem Fallbeginn. */
    public static float lookUp(long ms) {
        if (ms < ANVIL_FALL_START_MS || ms >= ANVIL_IMPACT_MS) return 0.0F;
        return Math.min(1.0F, (ms - ANVIL_FALL_START_MS) / 300.0F);
    }

    /** Deckkraft des Amboss-Schattens am Boden (0..1): wächst beim Fallen, verblasst beim Versinken. */
    public static float shadowStrength(long ms) {
        if (ms < ANVIL_FALL_START_MS || ms >= ANVIL_TOTAL_MS) return 0.0F;
        if (ms < ANVIL_IMPACT_MS) {
            float p = (ms - ANVIL_FALL_START_MS) / (float) (ANVIL_IMPACT_MS - ANVIL_FALL_START_MS);
            return 0.15F + 0.6F * p * p;
        }
        if (ms < ANVIL_SINK_START_MS) return 0.75F;
        float p = (ms - ANVIL_SINK_START_MS) / (float) (ANVIL_TOTAL_MS - ANVIL_SINK_START_MS);
        return 0.75F * (1.0F - p);
    }

    /** Radius des Amboss-Schattens (Blöcke): wächst beim Fallen. */
    public static float shadowRadius(long ms) {
        if (ms < ANVIL_FALL_START_MS) return 0.25F;
        if (ms < ANVIL_IMPACT_MS) {
            float p = (ms - ANVIL_FALL_START_MS) / (float) (ANVIL_IMPACT_MS - ANVIL_FALL_START_MS);
            return 0.25F + 0.4F * p;
        }
        return 0.65F;
    }

    // ==========================================
    // Thanos Snap (4,2 s)
    // ==========================================

    public static final long SNAP_TOTAL_MS = 4_200L;
    /** Die Zerfallsgrenze beginnt an den Füßen zu wandern (davor steht die Figur kurz still). */
    public static final long SNAP_START_MS = 300L;
    /** Die Zerfallsgrenze hat den Kopf passiert (2,25 s nach dem Start). */
    public static final long SNAP_END_MS = 2_550L;
    /** Höhe der Zerfallsgrenze vor dem Start bzw. am Ende (Blöcke über dem Boden). */
    public static final float SNAP_BOUNDARY_FROM = -0.3F;
    public static final float SNAP_BOUNDARY_TO = 2.25F;
    /** Höhe der ausgefransten Kante (Spitze zu Spitze, Blöcke). */
    public static final float SNAP_FRAY = 0.24F;
    /** Die windabgewandte Seite zerfällt etwas früher (höchstens so viele Blöcke Vorsprung). */
    public static final float SNAP_WIND_LEAD = 0.08F;
    /** Breite des dunkel werdenden Saums oberhalb der Grenze (Blöcke). */
    public static final float SNAP_EDGE_BAND = 0.09F;
    /** Lebensdauer einer Ascheflocke (Sekunden). */
    public static final float SNAP_FLAKE_LIFE_MIN = 1.1F;
    public static final float SNAP_FLAKE_LIFE_MAX = 1.6F;
    /** Höchstens so viele Pixel werden zu Flocken (der Rest verschwindet direkt an der Kante). */
    public static final int SNAP_MAX_FLAKES = 1_300;

    /**
     * Höhe der Zerfallsgrenze über dem Boden (Blöcke): steigt nach kurzem Stillstand gleichmäßig von den Füßen zum Kopf,
     * anfangs etwas langsamer. Monoton steigend.
     */
    public static float snapBoundary(long ms) {
        if (ms <= SNAP_START_MS) return SNAP_BOUNDARY_FROM;
        if (ms >= SNAP_END_MS) return SNAP_BOUNDARY_TO;
        float p = (ms - SNAP_START_MS) / (float) (SNAP_END_MS - SNAP_START_MS);
        float e = 0.35F * p * p + 0.65F * p;
        return SNAP_BOUNDARY_FROM + (SNAP_BOUNDARY_TO - SNAP_BOUNDARY_FROM) * e;
    }

    /** Zeitpunkt (ms), zu dem die Grenze die Höhe {@code threshold} erreicht (Umkehrung von {@link #snapBoundary}). */
    public static long snapBirthMs(float threshold) {
        float e = (threshold - SNAP_BOUNDARY_FROM) / (SNAP_BOUNDARY_TO - SNAP_BOUNDARY_FROM);
        if (e <= 0.0F) return SNAP_START_MS;
        if (e >= 1.0F) return SNAP_END_MS;
        double p = (-0.65 + Math.sqrt(0.65 * 0.65 + 4.0 * 0.35 * e)) / (2.0 * 0.35);
        long ms = SNAP_START_MS + (long) Math.ceil(p * (SNAP_END_MS - SNAP_START_MS));
        // Rundung (float/double) ausgleichen: zum Zerfallszeitpunkt hat die Grenze die Schwelle sicher erreicht
        while (ms < SNAP_END_MS && snapBoundary(ms) < threshold) ms++;
        while (ms > SNAP_START_MS && snapBoundary(ms - 1) >= threshold) ms--;
        return ms;
    }

    /**
     * Schwelle eines Pixels: es zerfällt, sobald die Grenze diese Höhe erreicht. Höhe über dem Boden plus ausgefranste
     * Kante ({@code fray} in -0,5..0,5) plus Vorsprung auf der windabgewandten Seite ({@code lee} in -1..1).
     */
    public static float snapThreshold(float height, float fray, float lee) {
        float t = height + SNAP_FRAY * Math.max(-0.5F, Math.min(0.5F, fray)) - SNAP_WIND_LEAD * Math.max(-1.0F, Math.min(1.0F, lee));
        return Math.max(SNAP_BOUNDARY_FROM + 0.01F, Math.min(SNAP_BOUNDARY_TO - 0.01F, t));
    }

    /** Leichtes Zusammensacken der Figur (0..1): Kopf sinkt nach vorne, Arme hängen. */
    public static float snapSlump(long ms) {
        if (ms <= 0L) return 0.0F;
        float p = Math.min(1.0F, ms / (float) (SNAP_END_MS - 300L));
        return p * p * (3.0F - 2.0F * p);
    }

    /** Lebensdauer einer Flocke (s), aus ihrem Zufallswert. */
    public static float snapFlakeLife(long seed) {
        return SNAP_FLAKE_LIFE_MIN + (SNAP_FLAKE_LIFE_MAX - SNAP_FLAKE_LIFE_MIN) * unit(seed, 0);
    }

    /**
     * Versatz einer Flocke seit dem Ablösen (Alter in s): {@code out[0]} in Windrichtung (immer schneller), {@code out[1]}
     * nach oben (steigt leicht), {@code out[2]} seitlich zur Windrichtung (Wirbel).
     */
    public static void snapFlakeOffset(float age, long seed, float[] out) {
        float a = Math.max(0.0F, age);
        float r1 = unit(seed, 1), r2 = unit(seed, 2), r3 = unit(seed, 3), r4 = unit(seed, 4);
        float along = (0.22F + 0.16F * r1) * a + (0.7F + 0.5F * r2) * a * a;
        float w = 4.0F + 3.0F * r1;
        float phase = r3 * 6.2831855F;
        float up = (0.12F + 0.12F * r3) * a + (0.08F + 0.14F * r4) * a * a + 0.05F * a * (float) Math.cos(w * 1.3F * a + phase);
        float side = (0.10F + 0.08F * r2) * a * (float) Math.sin(w * a + phase);
        out[0] = along;
        out[1] = up;
        out[2] = side;
    }

    /** Wie weit die Flocke schon zu dunkler Asche geworden ist (0 = Skin-Farbe, 1 = Asche). */
    public static float snapFlakeAsh(float age) {
        float p = Math.max(0.0F, Math.min(1.0F, age / 0.5F));
        return p * p * (3.0F - 2.0F * p);
    }

    /** Deckkraft einer Flocke: voll bis zur Hälfte der Lebensdauer, dann verblassend, am Ende 0. */
    public static float snapFlakeAlpha(float age, float life) {
        if (age <= 0.0F) return 1.0F;
        if (age >= life) return 0.0F;
        float q = age / life;
        if (q < 0.5F) return 1.0F;
        float f = 1.0F - (q - 0.5F) / 0.5F;
        return f * f;
    }

    /** Größe einer Flocke relativ zum Skin-Pixel: schrumpft beim Wegwehen. */
    public static float snapFlakeScale(float age, float life) {
        float q = Math.max(0.0F, Math.min(1.0F, age / life));
        return 1.0F - 0.45F * q;
    }

    /** Gleichverteilter Zufallswert 0..1 aus einem Startwert (für mehrere unabhängige Werte pro Flocke). */
    public static float unit(long seed, int index) {
        long h = seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    // ==========================================
    // Hell Gate (3,9 s)
    // ==========================================

    public static final long HELLGATE_TOTAL_MS = 3_900L;
    /** Der Runenkreis baut sich auf (0 bis 0,5 s). */
    public static final long HELLGATE_OPEN_MS = 500L;
    /** Die erste Hand bricht hervor, die weiteren folgen versetzt. */
    public static final long HELLGATE_HAND_START_MS = 420L;
    public static final long HELLGATE_HAND_STAGGER_MS = 90L;
    /** So lange braucht eine Hand vom Schlund bis zur Figur. */
    public static final long HELLGATE_HAND_REACH_MS = 520L;
    /** So schnell schließen sich die Finger nach dem Zupacken. */
    public static final long HELLGATE_GRIP_MS = 140L;
    /** Die Hände ziehen die Figur hinab (1,38 bis 2,7 s). */
    public static final long HELLGATE_PULL_START_MS = 1_380L;
    public static final long HELLGATE_PULL_END_MS = 2_700L;
    /** Das Tor schließt sich schnell und knallt zu. */
    public static final long HELLGATE_CLOSE_START_MS = 2_700L;
    public static final long HELLGATE_BANG_MS = 2_900L;
    /** Radius des Runenkreises (Blöcke). */
    public static final float HELLGATE_RADIUS = 1.15F;
    /** So tief wird die Figur gezogen (mehr als ihre Höhe: am Ende ganz unter dem Boden). */
    public static final float HELLGATE_SINK_DEPTH = 2.5F;
    public static final int HELLGATE_HANDS = 5;
    /** Schulter der Arme tief im Schlund. */
    public static final float HAND_ROOT_RADIUS = 0.62F;
    public static final float HAND_ROOT_DEPTH = 1.35F;
    /** Startpunkt der Hände (knapp unter dem Boden, im Schlund). */
    public static final float HAND_START_RADIUS = 0.6F;
    public static final float HAND_START_DEPTH = 0.35F;
    /** Höchster seitlicher Ellbogen-Ausschlag (Blöcke). */
    public static final float HAND_ELBOW_BEND = 0.35F;
    /** Halbe Länge eines Arms (Schulter bis Ellbogen bzw. Ellbogen bis Hand). */
    public static final float HAND_SEGMENT = 1.35F;
    /** Greifpunkte an der Figur: Höhe über den Füßen und Abstand von der Körperachse (Blöcke). */
    private static final float[] HAND_HEIGHT = {0.35F, 1.05F, 0.62F, 1.3F, 0.85F};
    private static final float[] HAND_RADIUS = {0.2F, 0.25F, 0.21F, 0.26F, 0.23F};
    private static final float[] HAND_ANGLE_JITTER = {0.0F, 0.25F, -0.2F, 0.15F, -0.1F};
    /** Reihenfolge, in der die Hände hervorbrechen (abwechselnd um den Kreis). */
    private static final int[] HAND_ORDER = {0, 3, 1, 4, 2};

    /** Radius des Tors: baut sich mit kurzem Überschwingen auf, pulsiert leicht, fällt beim Schließen schnell zusammen. */
    public static float gateRadius(long ms) {
        if (ms <= 0L || ms >= HELLGATE_BANG_MS) return 0.0F;
        if (ms < HELLGATE_OPEN_MS) {
            float p = ms / (float) HELLGATE_OPEN_MS;
            float e = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
            return HELLGATE_RADIUS * e * (1.0F + 0.08F * (float) Math.sin(p * Math.PI));
        }
        if (ms < HELLGATE_CLOSE_START_MS) {
            return HELLGATE_RADIUS * (1.0F + 0.015F * (float) Math.sin(ms * 0.012));
        }
        float q = (ms - HELLGATE_CLOSE_START_MS) / (float) (HELLGATE_BANG_MS - HELLGATE_CLOSE_START_MS);
        return HELLGATE_RADIUS * (1.0F - q * q);
    }

    /** Helligkeit der Runen (0..1): blenden beim Aufbau ein, flackern leicht. */
    public static float gateGlow(long ms) {
        if (ms <= 0L || ms >= HELLGATE_BANG_MS) return 0.0F;
        float in = Math.min(1.0F, ms / (float) HELLGATE_OPEN_MS);
        float flicker = 0.88F + 0.12F * (float) Math.sin(ms * 0.031) * (float) Math.sin(ms * 0.0071 + 1.0);
        return in * flicker;
    }

    /** Drehwinkel des Runenrings (Grad, langsam, beim Schließen schneller). */
    public static float gateSpinDeg(long ms) {
        float s = ms * 0.045F;
        if (ms > HELLGATE_CLOSE_START_MS) {
            float q = (ms - HELLGATE_CLOSE_START_MS) / 1000.0F;
            s += 400.0F * q * q;
        }
        return s;
    }

    /** Wann Hand {@code i} hervorbricht. */
    public static long handStartMs(int i) {
        return HELLGATE_HAND_START_MS + HAND_ORDER[Math.floorMod(i, HELLGATE_HANDS)] * HELLGATE_HAND_STAGGER_MS;
    }

    /** Wann Hand {@code i} die Figur packt. */
    public static long handGrabMs(int i) {
        return handStartMs(i) + HELLGATE_HAND_REACH_MS;
    }

    /** Wie weit Hand {@code i} schon gegriffen hat (0 = im Schlund, 1 = an der Figur), schnell und abbremsend. */
    public static float handReach(int i, long ms) {
        long start = handStartMs(i);
        if (ms <= start) return 0.0F;
        float p = Math.min(1.0F, (ms - start) / (float) HELLGATE_HAND_REACH_MS);
        float q = 1.0F - p;
        return 1.0F - q * q * q;
    }

    /** Finger-Krümmung (0 = gespreizt, 1 = fest zugepackt). */
    public static float handCurl(int i, long ms) {
        long grab = handGrabMs(i);
        if (ms < grab) return 0.12F * handReach(i, ms);
        return Math.min(1.0F, 0.12F + (ms - grab) / (float) HELLGATE_GRIP_MS * 0.88F);
    }

    /** Winkel (Bogenmaß) der Hand {@code i} um die Mitte. */
    public static float handAngle(int i, float baseRad) {
        int k = Math.floorMod(i, HELLGATE_HANDS);
        return baseRad + k * (float) (Math.PI * 2.0 / HELLGATE_HANDS) + HAND_ANGLE_JITTER[k];
    }

    /** Wie tief die Figur hinabgezogen ist (Blöcke): ruckartig (in Schüben), aber nie wieder nach oben. */
    public static float hellSink(long ms) {
        if (ms <= HELLGATE_PULL_START_MS) return 0.0F;
        if (ms >= HELLGATE_PULL_END_MS) return HELLGATE_SINK_DEPTH;
        float p = (ms - HELLGATE_PULL_START_MS) / (float) (HELLGATE_PULL_END_MS - HELLGATE_PULL_START_MS);
        // Schübe: Ableitung 1 + 0,9 cos(...) >= 0, also monoton
        float jerky = p + 0.9F * (float) Math.sin(p * Math.PI * 8.0) / (float) (Math.PI * 8.0);
        jerky = Math.max(0.0F, Math.min(1.0F, jerky));
        return HELLGATE_SINK_DEPTH * (float) Math.pow(jerky, 1.5);
    }

    /** Zappeln der Figur (0..1): ab dem Aufbau des Tors, bis sie unter dem Boden ist. */
    public static float hellStruggle(long ms) {
        if (ms < 250L || ms >= HELLGATE_PULL_END_MS) return 0.0F;
        if (ms < 550L) return (ms - 250L) / 300.0F;
        return 1.0F;
    }

    /** Kippen der Figur beim Zappeln (Grad, klein). */
    public static float hellTiltDeg(long ms) {
        return 6.0F * hellStruggle(ms) * (float) Math.sin(ms * 0.013);
    }

    /** Greifpunkt von Hand {@code i} an der Figur, relativ zur Tormitte am Boden (folgt der sinkenden Figur). */
    public static void hellGrabPoint(int i, long ms, float baseRad, float[] out) {
        int k = Math.floorMod(i, HELLGATE_HANDS);
        float a = handAngle(k, baseRad);
        out[0] = (float) Math.cos(a) * HAND_RADIUS[k];
        out[1] = HAND_HEIGHT[k] - hellSink(ms);
        out[2] = (float) Math.sin(a) * HAND_RADIUS[k];
    }

    /** Schulter von Arm {@code i} tief im Schlund. */
    public static void hellHandRoot(int i, float baseRad, float[] out) {
        float a = handAngle(i, baseRad);
        out[0] = (float) Math.cos(a) * HAND_ROOT_RADIUS;
        out[1] = -HAND_ROOT_DEPTH;
        out[2] = (float) Math.sin(a) * HAND_ROOT_RADIUS;
    }

    /**
     * Position der Hand {@code i} (Handgelenk), relativ zur Tormitte am Boden: bricht aus dem Schlund hervor und greift im
     * Bogen nach dem Greifpunkt, danach hält sie ihn fest (und sinkt mit der Figur).
     */
    public static void hellHandPos(int i, long ms, float baseRad, float[] out) {
        float a = handAngle(i, baseRad);
        hellGrabPoint(i, ms, baseRad, out);
        float e = handReach(i, ms);
        if (e >= 1.0F) return;
        float sx = (float) Math.cos(a) * HAND_START_RADIUS;
        float sy = -HAND_START_DEPTH;
        float sz = (float) Math.sin(a) * HAND_START_RADIUS;
        float arc = 0.35F * (float) Math.sin(e * Math.PI);
        out[0] = sx + (out[0] - sx) * e;
        out[1] = sy + (out[1] - sy) * e + arc;
        out[2] = sz + (out[2] - sz) * e;
    }

    /**
     * Ellbogen zwischen Schulter und Hand: seitlich (abwechselnd links/rechts) und etwas nach oben ausgestellt, der
     * Ausschlag ist begrenzt, damit der Arm immer innerhalb des Tors aus dem Boden kommt.
     */
    public static void hellElbow(int i, float baseRad, float[] root, float[] hand, float[] out) {
        float a = handAngle(i, baseRad);
        float mx = (root[0] + hand[0]) * 0.5F, my = (root[1] + hand[1]) * 0.5F, mz = (root[2] + hand[2]) * 0.5F;
        float dx = hand[0] - root[0], dy = hand[1] - root[1], dz = hand[2] - root[2];
        float half = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5F;
        float off = half >= HAND_SEGMENT ? 0.0F : (float) Math.sqrt(HAND_SEGMENT * HAND_SEGMENT - half * half);
        off = Math.min(HAND_ELBOW_BEND, off);
        float side = (Math.floorMod(i, 2) == 0) ? 1.0F : -1.0F;
        float tx = -(float) Math.sin(a) * side, tz = (float) Math.cos(a) * side;
        float bx = tx * 0.8F, by = 0.6F, bz = tz * 0.8F;
        float len = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        out[0] = mx + bx / len * off;
        out[1] = my + by / len * off;
        out[2] = mz + bz / len * off;
    }

    /** Deckkraft des Brandflecks nach dem Knall (0..1, verblasst bis zum Ende). */
    public static float scorchAlpha(long ms) {
        if (ms < HELLGATE_BANG_MS || ms >= HELLGATE_TOTAL_MS) return 0.0F;
        float p = (ms - HELLGATE_BANG_MS) / (float) (HELLGATE_TOTAL_MS - HELLGATE_BANG_MS);
        return 0.85F * (1.0F - p * p);
    }

    /** Lichtblitz beim Zuschlagen (0..1, 250 ms). */
    public static float bangFlash(long ms) {
        long t = ms - HELLGATE_BANG_MS;
        if (t < 0L || t >= 250L) return 0.0F;
        float p = t / 250.0F;
        return (1.0F - p) * (1.0F - p);
    }

    // ==========================================
    // Rüstung der Figur
    // ==========================================

    /**
     * Lederrüstung nur, wenn das Opfer gar keine Rüstung trug (alle vier Plätze leer). Teilweise gerüstete Opfer
     * werden so gezeigt, wie sie waren.
     */
    public static boolean useLeatherFallback(boolean head, boolean chest, boolean legs, boolean feet) {
        return !head && !chest && !legs && !feet;
    }
}

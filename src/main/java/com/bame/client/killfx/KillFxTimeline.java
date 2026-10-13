package com.bame.client.killfx;

/**
 * Reine Zeitverläufe der Kill-Effekte Sarg und Amboss (ohne Minecraft, testbar). Alle Zeiten in Millisekunden seit
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

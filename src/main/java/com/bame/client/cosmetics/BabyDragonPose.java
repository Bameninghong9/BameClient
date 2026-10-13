package com.bame.client.cosmetics;

import java.util.Locale;

/**
 * Animation des Baby-Drachen (Modell {@code baby_dragon.geo.json}, {@link BedrockCosmeticModel} mit
 * {@code blockbenchAxisFix}). Reines Java ohne Minecraft-Klassen, damit es ohne Client testbar ist.
 *
 * <p>Winkel in Grad wie {@link BedrockCosmeticModel#setBoneRotation}: rx &gt; 0 senkt die Vorderseite (Kopf nickt,
 * Schwanz hebt sich), ry &gt; 0 dreht die Vorderseite nach -x, rz &gt; 0 senkt die +x-Seite. Translationen in
 * Modell-Pixeln (Bedrock-Raum, y nach oben).
 *
 * <p>Modell: Fuesse auf y = 24 (= Java-Modell-y 0), Blick nach -z; daher sitzt der Drache mit {@link #seatOffsetY}
 * direkt auf der Schulter bzw. dem Kopf.
 */
public final class BabyDragonPose {

    public static final String[] BONES = {
        "body", "neck", "head", "jaw", "lid_l", "lid_r",
        "wing_l", "wing_l2", "wing_r", "wing_r2",
        "tail1", "tail2", "tail3", "tail4", "tail5", "tail_tip",
        "leg_fl", "leg_fl2", "leg_fr", "leg_fr2", "leg_bl", "leg_bl2", "leg_br", "leg_br2"
    };
    private static final String[] TAIL = {"tail1", "tail2", "tail3", "tail4", "tail5", "tail_tip"};

    /** Modellmassstab (1 Modell-Pixel = 1/16 Block * MODEL_SCALE); gleiche Grundflaeche wie das alte Modell (0.26). */
    public static final float MODEL_SCALE = 0.19f;
    /** Massstab, in dem die (gemeinsam genutzten) Zzz-Partikel gezeichnet werden: wie beim alten Modell. */
    public static final float ZZZ_SCALE = 0.26f;
    /** Kartenvorschau (Menue): Hoehe/Bildmitte wie beim alten Modell, etwas seitlicher gedreht (zeigt die Laenge). */
    public static final float CARD_SCALE = 1.0f;
    public static final float CARD_TRANSLATE_Y = 0.86f;
    public static final float CARD_YAW = -45.0f;

    /** Nuester (Mitte der Nasenoeffnung) im Kopfknochen, Bedrock-Pixel; rechte Seite: x gespiegelt. */
    public static final float NOSTRIL_X = 0.75f, NOSTRIL_Y = 35.7f, NOSTRIL_Z = -12.45f;

    public static final float SNEEZE_PERIOD = 5.0f, SNEEZE_START = 4.1f, SNEEZE_LEN = 0.8f;
    public static final float WING_PERIOD = 7.0f, WING_OPEN_LEN = 2.2f;
    public static final float BLINK_PERIOD = 4.3f, BLINK_START = 4.12f;
    /** Absenkung des Koerpers im Schlaf (Bauchplatten liegen auf). */
    public static final float SLEEP_DROP = 2.5f;

    public final float[][] rot = new float[BONES.length][3];
    public final float[][] trans = new float[BONES.length][3];

    private static int index(String bone) {
        for (int i = 0; i < BONES.length; i++) if (BONES[i].equals(bone)) return i;
        throw new IllegalArgumentException(bone);
    }

    private void set(String bone, float rx, float ry, float rz) {
        float[] r = rot[index(bone)];
        r[0] = rx; r[1] = ry; r[2] = rz;
    }

    private void move(String bone, float tx, float ty, float tz) {
        float[] t = trans[index(bone)];
        t[0] = tx; t[1] = ty; t[2] = tz;
    }

    public float[] rotation(String bone) {
        return rot[index(bone)];
    }

    public float[] translation(String bone) {
        return trans[index(bone)];
    }

    private static float sm(float a, float b, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - a) / (b - a)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float mod(float t, float p) {
        float c = t % p;
        return c < 0 ? c + p : c;
    }

    private static float sin(double a) {
        return (float) Math.sin(a);
    }

    /** Niesen: Fortschritt 0..1 innerhalb des Zyklus, sonst -1. */
    public static float sneezePhase(float timeSec) {
        float c = mod(timeSec, SNEEZE_PERIOD);
        return c >= SNEEZE_START && c <= SNEEZE_START + SNEEZE_LEN ? (c - SNEEZE_START) / SNEEZE_LEN : -1.0f;
    }

    /** Rauchwoelkchen aus den Nuestern: direkt nach dem Ruck nach unten. */
    public static boolean isSmokeMoment(float timeSec) {
        float p = sneezePhase(timeSec);
        return p >= 0.58f && p <= 0.85f;
    }

    /** Zusaetzliche Hoehe (Bloecke, Java-y) nach {@code applyPlacementTransform}, damit die Fuesse aufliegen. */
    public static float seatOffsetY(String placement) {
        switch (placement != null ? placement.toLowerCase(Locale.ROOT) : "") {
            case "head":
                return 0.17f;   // Kopf oben bei -0.5, Hut-Layer -0.53
            case "floating":
                return 0.05f;
            default:
                return 0.204f;  // Schulter: Arm oben bei 0, Aermel-Layer -0.016
        }
    }

    public static boolean isFlying(String placement) {
        return placement != null && "floating".equalsIgnoreCase(placement);
    }

    public void compute(float t, boolean afk, boolean flying) {
        for (int i = 0; i < BONES.length; i++) {
            rot[i][0] = rot[i][1] = rot[i][2] = 0.0f;
            trans[i][0] = trans[i][1] = trans[i][2] = 0.0f;
        }
        if (afk) {
            computeSleep(t);
        } else {
            computeAwake(t, flying);
        }
    }

    private void computeAwake(float t, boolean fly) {
        // Kopf: umschauen
        float nrx = -6.0f + sin(t * 1.3f) * 4.0f, nry = (float) Math.cos(t * 0.9f) * 8.0f;
        float hrx = 8.0f + sin(t * 1.3f) * 5.0f, hry = (float) Math.cos(t * 0.9f) * 14.0f, hrz = sin(t * 1.1f) * 6.0f;
        float jaw = 1.5f + Math.max(0.0f, sin(t * 0.7f)) * 2.0f;
        float sp = sneezePhase(t);
        if (sp >= 0.0f) {
            float wind = sm(0.0f, 0.5f, sp) * (1.0f - sm(0.5f, 0.62f, sp));   // Luft holen: Kopf hoch, Maul auf
            float snap = sm(0.5f, 0.62f, sp) * (1.0f - sm(0.72f, 1.0f, sp));   // Ruck nach unten
            float k = 1.0f - sm(0.0f, 0.25f, sp) * (1.0f - sm(0.8f, 1.0f, sp));
            hry *= k; hrz *= k; nry *= k;
            nrx += -10.0f * wind + 14.0f * snap;
            hrx += -20.0f * wind + 22.0f * snap;
            jaw += 10.0f * wind + 6.0f * snap;
        }
        float blink = mod(t, BLINK_PERIOD) > BLINK_START ? 0.0f : 1.0f; // 1 = offen (Lider im Schaedel)
        move("lid_l", -0.45f * blink, 0.0f, 0.0f);
        move("lid_r", 0.45f * blink, 0.0f, 0.0f);
        set("neck", nrx, nry, 0.0f);
        set("head", hrx, hry, hrz);
        set("jaw", jaw, 0.0f, 0.0f);

        // Fluegel: angelegt; alle WING_PERIOD s ausbreiten und schlagen (schwebend: dauerhaft fliegen)
        float wc = mod(t, WING_PERIOD);
        float open = fly ? 1.0f : (wc < WING_OPEN_LEN ? sm(0.0f, 0.4f, wc) * (1.0f - sm(WING_OPEN_LEN - 0.45f, WING_OPEN_LEN, wc)) : 0.0f);
        float ph = fly ? t * 7.0f : wc * 8.5f;
        float beat = open * sin(ph);
        float lagb = open * sin(ph - 0.9f);
        float idle = sin(t * 2.2f) * 2.0f;
        float wrx = lerp(-35.0f, -4.0f, open), wry = lerp(70.0f, 12.0f, open);
        float wrz = lerp(5.0f, 40.0f, open) + beat * 24.0f + idle;
        float hfold = lerp(110.0f, 12.0f, open) - lagb * 14.0f;
        set("wing_l", wrx, -wry, -wrz);
        set("wing_r", wrx, wry, wrz);
        set("wing_l2", 0.0f, -hfold, 0.0f);
        set("wing_r2", 0.0f, hfold, 0.0f);

        // Schwanz: Welle, die zur Spitze hin laeuft und groesser wird
        float[] base = fly ? new float[]{-14, -6, 2, 4, 6, 4} : new float[]{-20, -12, 2, 10, 12, 8};
        for (int i = 0; i < TAIL.length; i++) {
            float pitch = base[i] + (fly ? sin(ph - 1.2f - i * 0.5f) * 3.0f : 0.0f);
            float yaw = (fly ? 0.0f : 6.0f) + sin(t * 2.4f - i * 0.8f) * (6.0f + i * 2.5f);
            set(TAIL[i], pitch, yaw, 0.0f);
        }
        if (fly) {
            float d = sin(ph - 1.6f) * 4.0f;
            set("leg_fl", 28.0f + d, 0.0f, 0.0f); set("leg_fr", 28.0f + d, 0.0f, 0.0f);
            set("leg_fl2", 18.0f, 0.0f, 0.0f); set("leg_fr2", 18.0f, 0.0f, 0.0f);
            set("leg_bl", 34.0f + d, 0.0f, 0.0f); set("leg_br", 34.0f + d, 0.0f, 0.0f);
            set("leg_bl2", 12.0f, 0.0f, 0.0f); set("leg_br2", 12.0f, 0.0f, 0.0f);
        }
    }

    private void computeSleep(float t) {
        // Eingerollt: Bauch liegt auf, Kopf seitlich abgelegt, Fluegel angelegt, Schwanz um den Koerper, langsames Atmen
        float br = sin(t * 1.0f);
        move("body", 0.0f, -SLEEP_DROP + br * 0.1f, 0.0f);
        set("neck", 42.0f, 34.0f, 0.0f);
        set("head", 17.0f + br * 1.5f, 30.0f, 12.0f);
        set("wing_l", -30.0f, -72.0f, 2.0f - br * 1.5f);
        set("wing_r", -30.0f, 72.0f, -(2.0f - br * 1.5f));
        set("wing_l2", 0.0f, -118.0f, 0.0f);
        set("wing_r2", 0.0f, 118.0f, 0.0f);
        float[] trx = {-28, 22, 0, -6, -10, -10};
        float[] tr = {-22, -30, -34, -36, -36, -30};
        for (int i = 0; i < TAIL.length; i++) set(TAIL[i], trx[i], tr[i], 0.0f);
        set("leg_fl", -58.0f, 0.0f, 0.0f); set("leg_fr", -58.0f, 0.0f, 0.0f);
        set("leg_fl2", -28.0f, 0.0f, 0.0f); set("leg_fr2", -28.0f, 0.0f, 0.0f);
        set("leg_bl", -66.0f, 0.0f, -18.0f); set("leg_br", -66.0f, 0.0f, 18.0f);
        set("leg_bl2", 64.0f, 0.0f, 0.0f); set("leg_br2", 64.0f, 0.0f, 0.0f);
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }
}

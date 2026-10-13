package com.bame.client.emotes;

/**
 * Zeitleiste und Geometrie der Liegestütze (ohne Minecraft-Klassen, damit sie im plannerTest geprüft werden kann).
 * <p>
 * Einheiten: Modell-Pixel (1/16 Block vor der Spieler-Skalierung 0.9375). Die Kippung dreht den ganzen Spieler um die
 * Fußspitzen (Drehpunkt am Boden, 2 px vor der Körpermitte) nach vorne. Der Winkel wird so gewählt, dass die
 * senkrechten Arme genau auf dem Boden aufstützen: Oben liegt die Schulter eine Armlänge (10 px) über dem Boden, unten
 * 5,5 px tiefer. Dadurch berühren Fußspitzen und Hände gleichzeitig den Boden und nichts schwebt.
 */
public final class PushUpMotion {

    private PushUpMotion() {}

    /** Einstieg aus dem Stand in die Plank (s). */
    public static final float ENTRY = 0.4F;
    /** Dauer einer Wiederholung (s): 0–0,55 runter, 0,55–0,65 halten, 0,65–1,2 hoch, 1,2–1,3 halten. */
    public static final float REP = 1.3F;
    private static final float DOWN_END = 0.55F;
    private static final float HOLD_BOTTOM_END = 0.65F;
    private static final float UP_END = 1.2F;

    /** Schulter-Drehpunkt über den Füßen (24.016 - 2 px) und Fußspitze vor der Körpermitte (px). */
    public static final float SHOULDER_UP_PX = 22.016F;
    public static final float TOE_FWD_PX = 2.0F;
    /** Kopfmitte über den Füßen (px). */
    public static final float HEAD_UP_PX = 28.016F;
    /** Arm vom Schulter-Drehpunkt bis zur Hand (px). */
    public static final float ARM_REACH_PX = 10.0F;
    /** So viel tiefer liegt die Schulter in der unteren Position (px). */
    public static final float LOWER_PX = 5.5F;
    /** Unten kippen die Arme so weit Richtung Füße (Grad, gegenüber senkrecht). */
    public static final float ARM_BACK_DEG = 35.0F;
    /** Arme leicht schulterbreit nach außen (Grad). */
    public static final float ARM_SPLAY_DEG = 8.0F;
    /** Kopf leicht angehoben, Blick nach vorne (Grad, gegenüber dem Körper). */
    public static final float HEAD_RAISE_DEG = 45.0F;
    /** Blöcke pro Modell-Pixel (Spieler-Skalierung 0.9375). */
    public static final float BLOCKS_PER_PX = 0.9375F / 16.0F;

    private static final double R = Math.hypot(SHOULDER_UP_PX, TOE_FWD_PX);
    private static final double PHI = Math.atan2(TOE_FWD_PX, SHOULDER_UP_PX);

    /** Pose relativ zum gekippten Körper (Winkel im Bogenmaß, außer {@link #tiltDeg}). */
    public static final class Pose {
        /** Kippung des ganzen Spielers nach vorne um die Fußspitzen (Grad, 0 = Stand). */
        public float tiltDeg;
        public float rightArmPitch, rightArmYaw, leftArmPitch, leftArmYaw;
        /** Längenfaktor der Arme (deutet unten gebeugte Ellbogen an). */
        public float armScale = 1.0F;
        public float headPitch;
        /** 0 = oben, 1 = unten. */
        public float depth;
        /** Einblendung 0..1 während des Einstiegs. */
        public float blend;
    }

    public static float easeInOut(float x) {
        x = clamp01(x);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float clamp01(float x) {
        return x < 0.0F ? 0.0F : (x > 1.0F ? 1.0F : x);
    }

    public static float entryBlend(float t) {
        return easeInOut(t / ENTRY);
    }

    /** Tiefe 0 (oben) .. 1 (unten) zur Zeit t. */
    public static float depth(float t) {
        if (t <= ENTRY) return 0.0F;
        float c = (t - ENTRY) % REP;
        if (c < DOWN_END) return easeInOut(c / DOWN_END);
        if (c < HOLD_BOTTOM_END) return 1.0F;
        if (c < UP_END) return 1.0F - easeInOut((c - HOLD_BOTTOM_END) / (UP_END - HOLD_BOTTOM_END));
        return 0.0F;
    }

    /** Abgeschlossene Wiederholungen bis zur Zeit t. */
    public static int completedReps(float t) {
        if (t <= ENTRY) return 0;
        return (int) Math.floor((t - ENTRY) / REP);
    }

    /** Zeitpunkt (s) innerhalb von Wiederholung {@code rep}, ab Emote-Start. */
    public static float repTime(int rep, float phase) {
        return ENTRY + rep * REP + phase;
    }

    /** Höhe des Schulter-Drehpunkts über dem Boden (px). */
    public static float shoulderHeightPx(float depth) {
        return ARM_REACH_PX - LOWER_PX * clamp01(depth);
    }

    /** Kippwinkel des Körpers (Grad von senkrecht), damit die Schulter auf der gewünschten Höhe liegt. */
    public static float bodyTiltDeg(float depth) {
        // Schulterhöhe nach Drehung um die Fußspitze: 22*cos(b) + 2*sin(b) = R*cos(b - phi)
        double h = shoulderHeightPx(depth);
        return (float) Math.toDegrees(PHI + Math.acos(h / R));
    }

    public static void sample(float t, Pose out) {
        float b = entryBlend(t);
        float d = depth(t);
        float beta = bodyTiltDeg(d);
        float back = ARM_BACK_DEG * d;

        out.blend = b;
        out.depth = d;
        out.tiltDeg = beta * b;

        // Arm senkrecht zum Boden: Pitch -beta; unten um 'back' Richtung Füße gekippt
        double pitch = Math.toRadians(-beta + back);
        // Abspreizen nach außen: im gekippten Körper zeigt der Arm fast entlang -Z, daher über Yaw (siehe aimArm)
        double sinP = Math.sin(pitch);
        double splay = Math.sin(Math.toRadians(ARM_SPLAY_DEG));
        double yaw = Math.abs(sinP) > 1.0E-3 ? Math.asin(Math.max(-1.0, Math.min(1.0, splay / Math.abs(sinP)))) : 0.0;

        out.rightArmPitch = (float) (pitch * b);
        out.leftArmPitch = out.rightArmPitch;
        out.rightArmYaw = (float) (yaw * b);
        out.leftArmYaw = -out.rightArmYaw;

        float scale = (float) (shoulderHeightPx(d) / (ARM_REACH_PX * Math.cos(Math.toRadians(back))));
        out.armScale = 1.0F + (scale - 1.0F) * b;
        out.headPitch = (float) Math.toRadians(-HEAD_RAISE_DEG) * b;
    }

    /**
     * Position eines Punkts des stehenden Körpers nach der Kippung, relativ zu den Füßen (px).
     *
     * @param upPx  Höhe über den Füßen im Stand
     * @param fwdPx Abstand nach vorne im Stand
     * @return {Höhe, Abstand nach vorne}
     */
    public static float[] tiltedPoint(float upPx, float fwdPx, float tiltDeg) {
        double b = Math.toRadians(tiltDeg);
        double u = upPx;
        double f = fwdPx - TOE_FWD_PX;
        double up = u * Math.cos(b) - f * Math.sin(b);
        double fwd = u * Math.sin(b) + f * Math.cos(b) + TOE_FWD_PX;
        return new float[]{(float) up, (float) fwd};
    }

    // ------------------------------------------------------------------
    // Kamera (lokaler Spieler)
    // ------------------------------------------------------------------

    /** Kamera-Drehpunkt: Rumpfmitte (14 px über den Füßen im Stand) ... */
    public static final float CAMERA_PIVOT_UP_PX = 14.0F;
    /** ... plus so viel darüber (Blöcke), damit man von hinten oben auf den Rücken schaut. */
    public static final float CAMERA_PIVOT_LIFT = 0.4F;
    /** Kamera-Übergang in die Plank (s); etwas länger als der Körper-Einstieg, damit es weich wirkt. */
    public static final float CAMERA_BLEND_TIME = 0.6F;
    /** Mindest-Neigung der Kamera nach unten (Grad): aus der Ego-Perspektive bzw. bei gewählter 3rd-Person. */
    public static final float CAMERA_MIN_PITCH_FROM_FIRST_PERSON = 20.0F;
    public static final float CAMERA_MIN_PITCH_THIRD_PERSON = 10.0F;

    /** Übergang 0..1 der Kamera ab Emote-Start. */
    public static float cameraBlend(float t) {
        return easeInOut(t / CAMERA_BLEND_TIME);
    }

    /**
     * Drehpunkt der Kamera relativ zu den Füßen in Blöcken (vor der Spieler-Skalierung): {Höhe, nach vorne entlang des
     * Körper-Yaw}. Folgt dem Einstieg in die Plank, aber nicht jeder Wiederholung (sonst wippt das Bild mit).
     */
    public static float[] cameraPivot(float t) {
        float tilt = bodyTiltDeg(0.0F) * entryBlend(t);
        float[] p = tiltedPoint(CAMERA_PIVOT_UP_PX, 0.0F, tilt);
        return new float[]{p[0] * BLOCKS_PER_PX + CAMERA_PIVOT_LIFT, p[1] * BLOCKS_PER_PX};
    }
}

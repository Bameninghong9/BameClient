package com.bame.client.emotes;

/**
 * Zeitleiste, Schaukeln und Pose des Hängematten-Emotes (ohne Minecraft-Klassen, im hammockTest geprüft). Alles hängt nur
 * von der Emote-Zeit {@code t} (ab der gemeinsamen Startzeit) ab, daher sehen alle Clients dasselbe.
 * <p>
 * Ablauf: 0–0,5 s wachsen die Pfosten aus dem Boden, 0,3–0,55 s fallen die Seile herab, 0,4–0,8 s rollt
 * sich das Tuch vom Kopf- zum Fußende aus, 0,1–0,9 s legt sich der Spieler hinein (dreht sich zum Fußende und lässt sich
 * rückwärts fallen). Ab 0,9 s schaukelt die Hängematte samt Spieler sanft um die Achse durch beide Aufhängepunkte; die
 * Amplitude wächst bis 2,6 s auf {@link #SWAY_AMP_DEG}. Alle {@link #YAWN_EVERY} s streckt er sich kurz (Gähnen).
 * Herausfallen gibt es nicht: der Körper ist starr mit dem Tuch verbunden.
 * <p>
 * Körpertransformation im Hängematten-Rahmen (siehe {@link HammockLogic}, Blöcke), angewendet auf den stehenden Spieler
 * (Raum von {@code setupTransforms}, Füße im Ursprung):
 * {@code T = Tr(0, AXIS_Y, 0) · Rx(swayDeg) · Tr(0, -AXIS_Y, 0) · Tr(hip) · Ry(lieYawDeg) · Rx(liePitchDeg) · Tr(-HIP_LOCAL)}.
 * Modellwinkel im Bogenmaß wie {@code ModelPart}.
 */
public final class HammockMotion {

    private HammockMotion() {}

    // ------------------------------------------------------------------
    // Zeitleiste (s)
    // ------------------------------------------------------------------

    public static final float POST_GROW_END = 0.5F;
    public static final float ROPE_START = 0.3F, ROPE_END = 0.55F;
    public static final float UNROLL_START = 0.4F, UNROLL_END = 0.8F;
    /** Jedes Segment wächst in dieser Zeit auf volle Größe; die Starts sind über UNROLL_START..END verteilt. */
    public static final float SEGMENT_GROW = 0.14F;
    public static final float LIE_START = 0.1F, LIE_END = 0.9F;
    /** Erst drehen (zum Fußende), dann rückwärts hineinlegen. */
    public static final float LIE_TURN_END = 0.5F, LIE_TILT_START = 0.3F;
    /** Ende des Erscheinens (danach ist alles in Ruhelage + Schaukeln). */
    public static final float INTRO = 0.9F;

    public static final float SWAY_START = 0.9F, SWAY_FULL = 2.6F;
    public static final float SWAY_PERIOD = 3.8F;
    public static final float SWAY_AMP_DEG = 10.0F;
    /** Leichtes Verweilen an den Umkehrpunkten (Anteil der 3. Harmonischen). */
    public static final float SWAY_EASE = 0.05F;

    public static final float YAWN_FIRST = 7.0F, YAWN_EVERY = 12.0F, YAWN_LENGTH = 2.6F;

    /** Knarzen an jedem dritten Umkehrpunkt (ab dem zweiten). */
    public static final int CREAK_EVERY = 3;

    // ------------------------------------------------------------------
    // Körper
    // ------------------------------------------------------------------

    public static final double K = HammockLogic.BLOCKS_PER_PX;
    /** Hüftgelenk (Bein-Drehpunkt, 12 px unter dem Hals) über den Füßen im Stand (Blöcke). */
    public static final double HIP_LOCAL_Y = (HammockLogic.MODEL_ORIGIN_UP_PX - 12.0) * K;
    /** Kopf und Oberkörper liegen um so viel höher als waagerecht (Grad), passend zum Durchhang. */
    public static final float CURVE_DEG = 12.0F;
    /** Beine steigen zusätzlich so weit an (Grad gegenüber waagerecht). */
    public static final float FEET_UP_DEG = 14.0F;
    /** Rechtes Knie leicht angewinkelt: Bein so viel weiter angehoben (Grad). */
    public static final float KNEE_DEG = 22.0F;
    /** Auflagepunkt der Hüfte (Rückseite) auf dem Tuch: x im Rahmen und Einsinken ins Tuch (Blöcke). */
    public static final double HIP_CONTACT_X = 0.1;
    public static final double SINK = 0.02;
    /** Rückseite des Rumpfs hinter der Körperachse (px). */
    public static final double BACK_PX = 2.0;
    /** Beim Hineinlegen kurz etwas angehoben (Blöcke), damit beim Drehen keine Fußkante in den Boden taucht. */
    public static final double LIE_LIFT = 0.04;

    // ------------------------------------------------------------------
    // Pose
    // ------------------------------------------------------------------

    /** Ergebnis von {@link #sample}; wiederverwendbar (keine Allokation pro Frame). */
    public static final class Pose {
        public float t;
        /** Erscheinen: Pfosten 0..1 (mit Überschwingen), Seile 0..1, Tuch 0..1, Liegen 0..1. */
        public float postScale, ropeExtend, unroll, lie;
        /** Schaukelwinkel (Grad) um die Achse; Amplitude 0..1. */
        public float swayDeg, swayEnv;

        /** Körpertransformation (siehe Klassenkommentar): Hüfte im Rahmen (echte Blöcke), Drehungen (Grad). */
        public double hipX, hipY, hipZ;
        public float lieYawDeg, liePitchDeg;

        public float headPitch, headYaw, headRoll;
        public float rArmPitch, rArmYaw, rArmRoll;
        public float lArmPitch, lArmYaw, lArmRoll;
        public float rLegPitch, rLegYaw, rLegRoll;
        public float lLegPitch, lLegYaw, lLegRoll;
        /** Atmen: Brustkorb-Dicke (zScale des Rumpfs). */
        public float chestScale = 1.0F;
        /** Strecken/Gähnen 0..1. */
        public float yawn;
    }

    private static final float DEG = (float) (Math.PI / 180.0);

    // Ruhepose (liegend, Grad) und Streck-Pose
    public static final float HEAD_PITCH = 30.0F;
    /** Kopf in Ruhe leicht zur rechten Seite (zur Kamera in der 3rd-Person-Ansicht von hinten) gedreht (Grad). */
    public static final float HEAD_YAW = 15.0F;
    public static final float R_ARM_PITCH = -28.0F, R_ARM_YAW = 0.0F, R_ARM_ROLL = -30.0F;
    public static final float L_ARM_PITCH = -176.0F, L_ARM_YAW = 0.0F, L_ARM_ROLL = -20.0F;
    public static final float STRETCH_ARM_PITCH = -172.0F, STRETCH_ARM_ROLL = 14.0F;

    public static void sample(float t, Pose out) {
        sample(t, 0.0F, 0.0F, false, out);
    }

    /**
     * Pose zur Emote-Zeit {@code t}.
     *
     * @param lookYawDeg   Blickrichtung des Spielers relativ zur Ausrichtung der Hängematte (Grad, + = rechts)
     * @param lookPitchDeg Blick nach unten (+) / oben (-) (Grad)
     * @param look         {@code false}: nur Leerlauf-Blicke (Vorschau, Tests)
     */
    public static void sample(float t, float lookYawDeg, float lookPitchDeg, boolean look, Pose out) {
        sample(t, lookYawDeg, lookPitchDeg, look, 1.0F, out);
    }

    /**
     * Wie {@link #sample(float, float, float, boolean, Pose)} für einen skalierten Spieler ({@code scale} = Spieler-
     * Skalierung): Die Hüfte ({@link Pose#hipX} ...) ist dann in echten Blöcken angegeben, Stand und Liegelage passen zur
     * Körpergröße (der Renderer teilt durch {@code scale}). Tests und Effekte verwenden 1.
     */
    public static void sample(float t, float lookYawDeg, float lookPitchDeg, boolean look, float scale, Pose out) {
        if (t < 0.0F) t = 0.0F;
        if (!(scale > 0.0F)) scale = 1.0F;
        out.t = t;
        out.postScale = easeOutBack(clamp01(t / POST_GROW_END));
        out.ropeExtend = easeInOut((t - ROPE_START) / (ROPE_END - ROPE_START));
        out.unroll = clamp01((t - UNROLL_START) / (UNROLL_END - UNROLL_START));
        float turn = easeInOut((t - LIE_START) / (LIE_TURN_END - LIE_START));
        float tilt = easeInOut((t - LIE_TILT_START) / (LIE_END - LIE_TILT_START));
        float move = easeInOut((t - LIE_START) / (LIE_END - LIE_START));
        out.lie = tilt;

        // Schaukeln
        out.swayEnv = easeInOut((t - SWAY_START) / (SWAY_FULL - SWAY_START));
        out.swayDeg = SWAY_AMP_DEG * out.swayEnv * swayShape(t);

        // Körpertransformation: aus dem Stand (Hüfte bei HIP_LOCAL) in die Liegelage
        double b = 90.0 - CURVE_DEG;
        double[] hip = lyingHip(scale);
        double stand = HIP_LOCAL_Y * scale;
        out.hipX = hip[0] * move;
        // Absenken erst mit dem Zurücklehnen (quadratisch), sonst stünden die Füße kurz im Boden
        out.hipY = stand + (hip[1] - stand) * tilt * tilt + LIE_LIFT * scale * Math.sin(Math.PI * move);
        out.hipZ = 0.0;
        out.lieYawDeg = -90.0F * turn;
        out.liePitchDeg = (float) (b * tilt);

        // Gähnen/Strecken
        float y = yawn(t);
        out.yawn = y;

        // Kopf: liegt etwas erhöht (Kinn leicht zur Brust), schaut gemächlich umher; beim Strecken in den Nacken
        float idleYaw = HEAD_YAW + (float) (13.0 * Math.sin(t * 2.0 * Math.PI / 9.7) + 5.0 * Math.sin(t * 2.0 * Math.PI / 4.3 + 1.0));
        float idlePitch = (float) (4.0 * Math.sin(t * 2.0 * Math.PI / 7.1 + 0.4));
        float headYaw = idleYaw, headPitch = HEAD_PITCH + idlePitch;
        if (look) {
            float wy = (float) HammockLogic.wrapDegrees(lookYawDeg);
            float ly = clamp(wy, -70.0F, 70.0F);
            // Schaut der Spieler nach hinten, kehrt der Kopf weich zurück (kein Umspringen bei ±180°)
            float follow = easeInOut((Math.abs(ly) - 10.0F) / 30.0F) * lookBackFade(wy);
            headYaw = lerp(idleYaw, ly, follow);
            headPitch = HEAD_PITCH + lerp(idlePitch, clamp(lookPitchDeg, -40.0F, 40.0F) * 0.5F, follow);
        }
        headPitch = lerp(headPitch, HEAD_PITCH - 10.0F, y);
        out.headPitch = headPitch * tilt * DEG;
        out.headYaw = headYaw * tilt * DEG;
        out.headRoll = 0.0F;

        // Atmen (über die ganze Emote-Zeit, daher ohne Naht)
        float breath = (float) Math.sin(t * 2.2);
        out.chestScale = 1.0F + 0.05F * (0.5F + 0.5F * breath) * tilt;

        // Arme: rechts (zur Kamera) locker auf dem Bauch, links Hand hinter dem Kopf; beim Strecken beide nach oben
        float rp = lerp(R_ARM_PITCH + breath * 1.5F, STRETCH_ARM_PITCH, y);
        float rr = lerp(R_ARM_ROLL, STRETCH_ARM_ROLL, y);
        float ryw = lerp(R_ARM_YAW, 0.0F, y);
        float lp = lerp(L_ARM_PITCH, STRETCH_ARM_PITCH, y);
        float lr = lerp(L_ARM_ROLL, -STRETCH_ARM_ROLL, y);
        float lyw = lerp(L_ARM_YAW, 0.0F, y);
        out.rArmPitch = rp * move * DEG;
        out.rArmYaw = ryw * move * DEG;
        out.rArmRoll = rr * move * DEG;
        out.lArmPitch = lp * move * DEG;
        out.lArmYaw = lyw * move * DEG;
        out.lArmRoll = lr * move * DEG;

        // Beine: folgen dem Durchhang, rechtes Knie angewinkelt (beim Strecken ausgestreckt)
        float legBase = -(CURVE_DEG + FEET_UP_DEG);
        float knee = KNEE_DEG * (1.0F - y);
        out.rLegPitch = (legBase - knee) * move * DEG;
        out.rLegYaw = 6.0F * move * DEG;
        out.rLegRoll = 0.0F;
        out.lLegPitch = (legBase + 2.0F * y) * move * DEG;
        out.lLegYaw = -4.0F * move * DEG;
        out.lLegRoll = 0.0F;
    }

    /** Hüftgelenk in der Ruhelage (Rahmen, ohne Schaukeln): Rückseite der Hüfte liegt auf dem Tuch. */
    public static double[] lyingHip() {
        return lyingHip(1.0F);
    }

    /** Wie {@link #lyingHip()} für die Spieler-Skalierung {@code scale} (echte Blöcke). */
    public static double[] lyingHip(float scale) {
        double b = Math.toRadians(90.0 - CURVE_DEG);
        double back = BACK_PX * K * scale;
        double cx = HIP_CONTACT_X;
        double cy = HammockLogic.slingY(cx) - SINK;
        return new double[]{cx + back * Math.cos(b), cy + back * Math.sin(b), 0.0};
    }

    /** Form des Schaukelns -1..1 (Sinus mit leichtem Verweilen an den Umkehrpunkten). */
    public static float swayShape(float t) {
        double phi = 2.0 * Math.PI * (t - SWAY_START) / SWAY_PERIOD;
        return (float) ((Math.sin(phi) + SWAY_EASE * Math.sin(3.0 * phi)) / (1.0 - SWAY_EASE));
    }

    /** Zeitpunkt (s) des n-ten Umkehrpunkts (n = 0, 1, ...). */
    public static float extremeTime(int n) {
        return SWAY_START + (0.25F + 0.5F * n) * SWAY_PERIOD;
    }

    /** Knarzt es am n-ten Umkehrpunkt? (jeder dritte ab n = 1, gleich auf allen Clients) */
    public static boolean creaksAt(int n) {
        return n >= 1 && n % CREAK_EVERY == 1;
    }

    /** Strecken/Gähnen 0..1 (weicher Buckel, alle {@link #YAWN_EVERY} s). */
    public static float yawn(float t) {
        if (t < YAWN_FIRST) return 0.0F;
        float u = ((t - YAWN_FIRST) % YAWN_EVERY) / YAWN_LENGTH;
        if (u >= 1.0F) return 0.0F;
        float s = (float) Math.sin(Math.PI * u);
        return s * s;
    }

    /** Segment i (0 = Kopfende, -X) des Tuchs: Länge 0..1 beim Ausrollen (der Renderer streckt es entlang X ab seinem Anfang). */
    public static float segmentScale(float unrollT, int i) {
        float span = (UNROLL_END - UNROLL_START) - SEGMENT_GROW;
        float start = UNROLL_START + span * i / (HammockLogic.SEGMENTS - 1);
        float x = clamp01((unrollT - start) / SEGMENT_GROW);
        float y = 1.0F - x;
        return 1.0F - y * y * y;
    }

    // ------------------------------------------------------------------
    // Körpertransformation (für Tests, Effekte und Kamera)
    // ------------------------------------------------------------------

    /**
     * Punkt des stehenden Spielers (Raum von {@code setupTransforms}: Blöcke, +X rechts, +Y oben, +Z hinten, Füße im
     * Ursprung) in den Hängematten-Rahmen.
     */
    public static double[] bodyToFrame(Pose p, double x, double y, double z, double[] out) {
        // Tr(-HIP_LOCAL)
        y -= HIP_LOCAL_Y;
        // Rx(liePitch)
        double b = Math.toRadians(p.liePitchDeg);
        double y1 = y * Math.cos(b) - z * Math.sin(b), z1 = y * Math.sin(b) + z * Math.cos(b);
        // Ry(lieYaw)
        double a = Math.toRadians(p.lieYawDeg);
        double x2 = x * Math.cos(a) + z1 * Math.sin(a), z2 = -x * Math.sin(a) + z1 * Math.cos(a);
        // Tr(hip), dann schaukeln
        return HammockLogic.swing(x2 + p.hipX, y1 + p.hipY, z2 + p.hipZ, p.swayDeg, out);
    }

    /** Modellpunkt (px wie {@code PlayerEntityModel}, +Y unten, -Z vorne, -X rechte Hand) in den Raum des stehenden Spielers. */
    public static double[] modelToBody(double mx, double my, double mz, double[] out) {
        out[0] = -mx * K;
        out[1] = (HammockLogic.MODEL_ORIGIN_UP_PX - my) * K;
        out[2] = mz * K;
        return out;
    }

    /** Körperteile des (breiten) Spielermodells: Drehpunkt und Quader (px) wie Vanilla. */
    public enum Part {
        HEAD(0, 0, 0, -4, -8, -4, 8, 8, 8),
        BODY(0, 0, 0, -4, 0, -2, 8, 12, 4),
        RIGHT_ARM(-5, 2, 0, -3, -2, -2, 4, 12, 4),
        LEFT_ARM(5, 2, 0, -1, -2, -2, 4, 12, 4),
        RIGHT_LEG(-1.9, 12, 0, -2, 0, -2, 4, 12, 4),
        LEFT_LEG(1.9, 12, 0, -2, 0, -2, 4, 12, 4);

        public final double px, py, pz, x0, y0, z0, sx, sy, sz;

        Part(double px, double py, double pz, double x0, double y0, double z0, double sx, double sy, double sz) {
            this.px = px; this.py = py; this.pz = pz;
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.sx = sx; this.sy = sy; this.sz = sz;
        }
    }

    /** Winkel {pitch, yaw, roll} eines Körperteils aus der Pose. */
    public static float[] partAngles(Pose p, Part part) {
        return switch (part) {
            case HEAD -> new float[]{p.headPitch, p.headYaw, p.headRoll};
            case BODY -> new float[]{0.0F, 0.0F, 0.0F};
            case RIGHT_ARM -> new float[]{p.rArmPitch, p.rArmYaw, p.rArmRoll};
            case LEFT_ARM -> new float[]{p.lArmPitch, p.lArmYaw, p.lArmRoll};
            case RIGHT_LEG -> new float[]{p.rLegPitch, p.rLegYaw, p.rLegRoll};
            case LEFT_LEG -> new float[]{p.lLegPitch, p.lLegYaw, p.lLegRoll};
        };
    }

    /**
     * Punkt eines Körperteils (lokale px im Quader-Raum des Teils) in den Hängematten-Rahmen: Teil-Drehung
     * (Z·Y·X wie {@code ModelPart}), Modell -> Körper, Körper -> Rahmen. Brustkorb-Atmung wird ignoriert (&lt; 0,1 px).
     */
    public static double[] partToFrame(Pose p, Part part, double lx, double ly, double lz, double[] out) {
        float[] a = partAngles(p, part);
        double[] m = new double[3];
        CampfireMotion.rotZYX(lx, ly, lz, a[0], a[1], a[2], m);
        modelToBody(m[0] + part.px, m[1] + part.py, m[2] + part.pz, m);
        return bodyToFrame(p, m[0], m[1], m[2], out);
    }

    // ------------------------------------------------------------------
    // Kamera (lokaler Spieler)
    // ------------------------------------------------------------------

    public static final float CAMERA_BLEND_TIME = 0.8F;
    public static final float CAMERA_MIN_PITCH_FROM_FIRST_PERSON = 15.0F;
    public static final float CAMERA_MIN_PITCH_THIRD_PERSON = 8.0F;
    /** Drehpunkt über der Mitte des liegenden Körpers (Rahmen, Blöcke): x, Höhe über dem Tuch. */
    public static final double CAMERA_PIVOT_X = -0.25, CAMERA_PIVOT_LIFT = 0.45;

    public static float cameraBlend(float t) {
        return easeInOut(t / CAMERA_BLEND_TIME);
    }

    /** Drehpunkt der Kamera im Rahmen (Blöcke), ohne Schaukeln (sonst wippt das Bild). */
    public static double[] cameraPivot() {
        return new double[]{CAMERA_PIVOT_X, HammockLogic.slingY(CAMERA_PIVOT_X) + CAMERA_PIVOT_LIFT, 0.0};
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    /** Blick-Folgen des Kopfes wird zwischen diesen Blick-Yaws (Grad, relativ zur Ausrichtung) ausgeblendet. */
    public static final float LOOK_BACK_FROM = 100.0F, LOOK_BACK_TO = 150.0F;

    /**
     * Gewicht 1..0 für das Blick-Folgen des Kopfes: 1 bis {@link #LOOK_BACK_FROM}, 0 ab {@link #LOOK_BACK_TO}. Schaut
     * der Spieler nach hinten (z. B. Kamera von vorne), dreht sich der Kopf nicht über die Schulter, sondern kehrt weich
     * nach vorne zurück; bei ±180° springt er daher nicht von einer Seite auf die andere.
     *
     * @param wrappedYawDeg Blick-Yaw relativ zur Ausrichtung, schon auf (-180, 180] gebracht
     */
    public static float lookBackFade(float wrappedYawDeg) {
        return 1.0F - easeInOut((Math.abs(wrappedYawDeg) - LOOK_BACK_FROM) / (LOOK_BACK_TO - LOOK_BACK_FROM));
    }

    public static float clamp01(float x) {
        return x < 0.0F ? 0.0F : (x > 1.0F ? 1.0F : x);
    }

    public static float clamp(float x, float lo, float hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }

    public static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    public static float easeInOut(float x) {
        x = clamp01(x);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Wächst leicht über 1 hinaus und federt zurück (Erscheinen mit „Puff“). */
    public static float easeOutBack(float x) {
        x = clamp01(x);
        float c1 = 1.70158F, c3 = c1 + 1.0F;
        float y = x - 1.0F;
        return 1.0F + c3 * y * y * y + c1 * y * y;
    }
}

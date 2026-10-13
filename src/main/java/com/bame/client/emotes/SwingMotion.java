package com.bame.client.emotes;

/**
 * Zeitleiste, Schaukeln und Pose des Schaukel-Emotes (ohne Minecraft-Klassen, im swingPicnicTest geprüft). Alles hängt
 * nur von der Emote-Zeit {@code t} (bzw. beim Gerüst vom Alter des Gerüsts) ab, daher sehen alle Clients dasselbe.
 * <p>
 * Gerüst (Alter {@code a} ab dem Start des Spielers, der es aufgebaut hat): 0–0,5 s wachsen die A-Beine aus dem Boden,
 * 0,25–0,6 s fällt der Balken von oben ein, 0,2–0,5 s erscheinen die Sitzbretter, 0,45–0,8 s wachsen
 * die Seile vom Sitz zum Haken. Spieler: 0,15–0,85 s setzt er sich (Spieler auf dem zweiten Sitz rutschen dabei vom
 * eigenen Standpunkt auf den Sitz und drehen sich in Schaukelrichtung), greift die Seile; ab 1 s schaukelt er nach
 * vorne/hinten, die Amplitude wächst in 3 s auf {@link #AMP_DEG}. Beine pumpen phasenrichtig (vorne ausgestreckt, hinten
 * unter den Sitz gezogen), der Oberkörper lehnt sich vorne zurück und hinten vor.
 * <p>
 * Körpertransformation im Schaukel-Rahmen (siehe {@link SwingLogic}, Blöcke), angewendet auf den stehenden Spieler
 * (Raum von {@code setupTransforms}, Füße im Ursprung):
 * {@code T = Tr(0, PIVOT_Y, 0) · Rx(swingDeg) · Tr(0, -PIVOT_Y, 0) · Tr(hip) · Ry(turnDeg) · Rx(leanDeg) · Tr(-HIP_LOCAL)}.
 * Modellwinkel im Bogenmaß wie {@code ModelPart}.
 */
public final class SwingMotion {

    private SwingMotion() {}

    public static final double K = HammockLogic.BLOCKS_PER_PX;
    public static final double HIP_LOCAL_Y = HammockMotion.HIP_LOCAL_Y;

    // ------------------------------------------------------------------
    // Gerüst erscheint (Alter des Gerüsts, s)
    // ------------------------------------------------------------------

    public static final float LEG_GROW_END = 0.5F;
    public static final float BEAM_START = 0.25F, BEAM_END = 0.6F;
    public static final float SEAT_START = 0.2F, SEAT_END = 0.5F;
    public static final float ROPE_START = 0.45F, ROPE_END = 0.8F;
    /** So hoch über seiner Ruhelage beginnt der Balken (Blöcke). */
    public static final double BEAM_DROP = 0.45;

    /** Erscheinen des Gerüsts; wiederverwendbar. */
    public static final class Appear {
        /** Beine 0..1 (mit Überschwingen), Balken 0..1 (Einblenden + Fall), Sitz 0..1 (mit Überschwingen), Seile 0..1. */
        public float legs, beam, seat, ropes;
    }

    public static void appear(float age, Appear out) {
        if (age < 0.0F) age = 0.0F;
        out.legs = easeOutBack(clamp01(age / LEG_GROW_END));
        out.beam = easeInOut((age - BEAM_START) / (BEAM_END - BEAM_START));
        out.seat = easeOutBack(clamp01((age - SEAT_START) / (SEAT_END - SEAT_START)));
        out.ropes = easeInOut((age - ROPE_START) / (ROPE_END - ROPE_START));
    }

    /** Höhenversatz des fallenden Balkens (Blöcke) bei Fortschritt {@code beam}. */
    public static double beamOffset(float beam) {
        float f = 1.0F - beam;
        return BEAM_DROP * f * f;
    }

    // ------------------------------------------------------------------
    // Spieler (Emote-Zeit, s)
    // ------------------------------------------------------------------

    public static final float SIT_START = 0.15F, SIT_END = 0.85F;
    public static final float INTRO = SIT_END;
    public static final float SWING_START = 1.0F;
    /** Amplitude wächst in dieser Zeit auf den vollen Wert. */
    public static final float RAMP = 3.0F;
    public static final float AMP_DEG = 38.0F;
    public static final float PERIOD = 2.8F;
    /** Leichtes Verweilen an den Umkehrpunkten (Anteil der 3. Harmonischen). */
    public static final float EASE = 0.06F;
    /** Pumpen läuft dem Schwung etwas voraus (Bogenmaß). */
    public static final float PUMP_LEAD = 0.35F;
    /** Bein-Winkel zum Sitz (Modell-Pitch, Grad): Mitte und Ausschlag (vorne -92°, hinten -32°). */
    public static final float LEG_MID_DEG = -62.0F, LEG_PUMP_DEG = 30.0F;
    /** Oberkörper: vorne so weit zurück-, hinten so weit vorgelehnt (Grad). */
    public static final float LEAN_DEG = 13.0F;
    /** Hüftgelenk über der Sitzfläche (Unterseite der Oberschenkel, 2 px) und etwas hinter der Seilebene (Blöcke). */
    public static final double HIP_ABOVE_SEAT = 2.0 * K;
    public static final double HIP_Z = 0.03;
    /**
     * Griffpunkt in der Hand (Arm-Lokalsystem, px; x zur Außenseite, Armmitte bei 1). Das Seil liegt an der Innenseite
     * der Faust: die Arme stehen dadurch etwas nach außen gespreizt und verdecken den Kopf nicht.
     */
    public static final float GRIP_X = -0.5F, GRIP_Y = 9.0F;
    /** Schulter-Drehpunkte (Standard, breite Arme). */
    public static final float SHOULDER_X = 5.0F, SHOULDER_Y = 2.0F;

    /** Knarzen an jedem dritten Umkehrpunkt (ab dem dritten), leises Rauschen an jedem vierten Tiefpunkt. */
    public static final int CREAK_EVERY = 3, WHOOSH_EVERY = 4;

    /** Ergebnis von {@link #sample}; wiederverwendbar (keine Allokation pro Frame). */
    public static final class Pose {
        public float t;
        /** Hinsetzen 0..1, Schaukel-Amplitude 0..1, Schaukelwinkel (Grad, + = nach vorne). */
        public float sit, env, swingDeg;
        /** Pumpen -1..1 (+1 = Beine vorne ausgestreckt). */
        public float pump;
        /** Hüfte im Rahmen (Blöcke, ohne Schaukeln), Drehung zur Schaukelrichtung und Lehnen (Grad). */
        public double hipX, hipY, hipZ;
        public float turnDeg, leanDeg;

        public float headPitch, headYaw, headRoll;
        public float rArmPitch, rArmYaw, rArmRoll;
        public float lArmPitch, lArmYaw, lArmRoll;
        public float rLegPitch, rLegYaw, rLegRoll;
        public float lLegPitch, lLegYaw, lLegRoll;
    }

    private static final float DEG = (float) (Math.PI / 180.0);

    /** Pose ohne Blick-Folgen, Sitz 0, Spieler steht genau am Sitz (Vorschau, Tests). */
    public static void sample(float t, Pose out) {
        sample(t, 0, null, 0.0F, 0.0F, 0.0F, false, 1.0F, out);
    }

    /**
     * Pose zur Emote-Zeit {@code t}.
     *
     * @param seat         Sitz 0 oder 1
     * @param standFeet    Füße beim Start im Rahmen (Blöcke) oder {@code null} = genau am Sitz (Aufbauer)
     * @param turn0        Körper-Yaw beim Start minus Rahmen-Yaw (Grad): dreht sich beim Hinsetzen auf 0
     * @param lookYawDeg   Blickrichtung relativ zur Schaukelrichtung (Grad, + = rechts), {@code lookPitchDeg} + = unten
     * @param look         {@code false}: nur Leerlauf-Blicke (Vorschau, Tests)
     * @param scale        Spieler-Skalierung (Hüfte in echten Blöcken; Tests 1)
     */
    public static void sample(float t, int seat, double[] standFeet, float turn0, float lookYawDeg, float lookPitchDeg,
                              boolean look, float scale, Pose out) {
        if (t < 0.0F) t = 0.0F;
        if (!(scale > 0.0F)) scale = 1.0F;
        out.t = t;
        float sit = easeInOut((t - SIT_START) / (SIT_END - SIT_START));
        out.sit = sit;

        // Schaukeln
        double phi = phase(t);
        out.env = envelope(t);
        out.swingDeg = AMP_DEG * out.env * shape(t);
        float pumpEnv = easeInOut((t - SWING_START) / (RAMP * 0.6F));
        out.pump = (float) (pumpEnv * Math.sin(phi + PUMP_LEAD));
        out.leanDeg = LEAN_DEG * out.pump;
        out.turnDeg = turn0 * (1.0F - sit);

        // Hüfte: vom Stand (eigene Füße) auf den Sitz
        double sx = SwingLogic.seatX(seat);
        double tx = sx, ty = SwingLogic.SEAT_TOP_Y + HIP_ABOVE_SEAT * scale, tz = HIP_Z * scale;
        double fx = standFeet != null ? standFeet[0] : sx;
        double fy = standFeet != null ? standFeet[1] : 0.0;
        double fz = standFeet != null ? standFeet[2] : 0.0;
        double ox = fx, oy = fy + HIP_LOCAL_Y * scale, oz = fz;
        double dist = Math.hypot(tx - ox, tz - oz);
        // kleiner Hüpfer: Fersen tauchen beim Anwinkeln der Beine nicht in den Boden; weiter weg (zweiter Sitz) etwas höher
        double hop = (0.06 + Math.min(0.2, dist * 0.12)) * Math.sin(Math.PI * sit);
        out.hipX = ox + (tx - ox) * sit;
        out.hipY = oy + (ty - oy) * sit + hop;
        out.hipZ = oz + (tz - oz) * sit;

        // Arme: Hände an den Seilen (Lösung bei voller Sitzhaltung), beim Hinsetzen dorthin
        float[] r = ARM_SCRATCH.get();
        ropeArm(out.leanDeg, scale, true, r);
        out.rArmPitch = r[0] * sit;
        out.rArmYaw = 0.0F;
        out.rArmRoll = r[1] * sit;
        ropeArm(out.leanDeg, scale, false, r);
        out.lArmPitch = r[0] * sit;
        out.lArmYaw = 0.0F;
        out.lArmRoll = r[1] * sit;

        // Beine: Winkel zum Sitz, Lehnen herausgerechnet (Körper-Rx(b) entspricht Modell-Pitch -b)
        float legSeat = LEG_MID_DEG - LEG_PUMP_DEG * out.pump;
        float legModel = (legSeat + out.leanDeg) * sit;
        out.rLegPitch = legModel * DEG;
        out.lLegPitch = (legModel + 3.0F * (float) Math.sin(phi * 0.5 + 0.3) * pumpEnv) * DEG;
        out.rLegYaw = 4.0F * sit * DEG;
        out.lLegYaw = -4.0F * sit * DEG;
        out.rLegRoll = 0.0F;
        out.lLegRoll = 0.0F;

        // Kopf: sitzt auf dem Hals (kippt mit Schaukeln und Lehnen mit), gleicht die Kippung aber zum Teil aus und schaut
        // so ruhig nach vorne in Schaukelrichtung; leichte Leerlauf-Bewegung. Folgt deutlichem Umschauen nur in Grenzen
        // (weich begrenzt) und nie über die Schulter nach hinten (siehe HammockMotion.lookBackFade).
        float idleYaw = (float) (6.0 * Math.sin(t * 2.0 * Math.PI / 7.3) + 3.0 * Math.sin(t * 2.0 * Math.PI / 3.1 + 0.7));
        float idlePitch = (float) (3.0 * Math.sin(t * 2.0 * Math.PI / 5.3 + 0.2)) - 4.0F * out.pump;
        float headYaw = idleYaw, headPitch = idlePitch;
        if (look) {
            float wy = (float) HammockLogic.wrapDegrees(lookYawDeg);
            float back = HammockMotion.lookBackFade(wy);
            float follow = easeInOut((Math.abs(wy) - 10.0F) / 30.0F) * back;
            headYaw = lerp(idleYaw, softClamp(wy, -HEAD_YAW_MAX, HEAD_YAW_MAX, HEAD_KNEE), follow);
            float lp = softClamp(lookPitchDeg, HEAD_LOOK_PITCH_MIN, HEAD_LOOK_PITCH_MAX, HEAD_KNEE);
            float followP = Math.max(follow, easeInOut((Math.abs(lookPitchDeg) - 10.0F) / 20.0F) * back);
            headPitch = lerp(idlePitch, lp, followP);
        }
        // Körper-Kippung (Schaukeln + Lehnen, beides um X) entspricht Modell-Pitch -(swing + lean): teilweise ausgleichen
        float pitch = headPitch + HEAD_TILT_COMP * (out.swingDeg + out.leanDeg);
        out.headPitch = softClamp(pitch, -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT, HEAD_KNEE) * DEG;
        out.headYaw = headYaw * sit * DEG;
        out.headRoll = 0.0F;
    }

    /** Kopf folgt dem Blick höchstens so weit (Grad, weich begrenzt): Yaw ±, Pitch oben (-) / unten (+). */
    public static final float HEAD_YAW_MAX = 60.0F, HEAD_LOOK_PITCH_MIN = -30.0F, HEAD_LOOK_PITCH_MAX = 25.0F;
    /** Anteil der Körper-Kippung (Schaukeln + Lehnen), den der Kopf ausgleicht. */
    public static final float HEAD_TILT_COMP = 0.6F;
    /** Kopf-Pitch gegenüber dem Rumpf insgesamt höchstens ± so viel (Grad, weich begrenzt). */
    public static final float HEAD_PITCH_LIMIT = 40.0F;
    /** Breite des weichen Übergangs an den Grenzen (Grad). */
    public static final float HEAD_KNEE = 10.0F;

    /**
     * Weiche Begrenzung auf [lo, hi]: innerhalb von {@code knee} vor der Grenze läuft der Wert stetig (C¹) gegen die
     * Grenze, statt hart abzuknicken; das Ergebnis liegt immer in [lo, hi].
     */
    public static float softClamp(float x, float lo, float hi, float knee) {
        float a = hi - knee;
        if (x > a) return hi - knee * (float) Math.exp(-(x - a) / knee);
        float b = lo + knee;
        if (x < b) return lo + knee * (float) Math.exp((x - b) / knee);
        return x;
    }

    private static final ThreadLocal<float[]> ARM_SCRATCH = ThreadLocal.withInitial(() -> new float[2]);

    /** Phase des Schaukelns (Bogenmaß, 0 beim Start). */
    public static double phase(float t) {
        return 2.0 * Math.PI * (t - SWING_START) / PERIOD;
    }

    /** Amplitude 0..1 (ab {@link #SWING_START} in {@link #RAMP} s). */
    public static float envelope(float t) {
        return easeInOut((t - SWING_START) / RAMP);
    }

    /** Form des Schaukelns -1..1 (Sinus mit leichtem Verweilen an den Umkehrpunkten). */
    public static float shape(float t) {
        double phi = phase(t);
        return (float) ((Math.sin(phi) + EASE * Math.sin(3.0 * phi)) / (1.0 - EASE));
    }

    /** Zeitpunkt (s) des n-ten Umkehrpunkts (n = 0 vorne, 1 hinten, ...). */
    public static float extremeTime(int n) {
        return SWING_START + (0.25F + 0.5F * n) * PERIOD;
    }

    /** Zeitpunkt (s) des n-ten Tiefpunkts (n ≥ 1). */
    public static float bottomTime(int n) {
        return SWING_START + 0.5F * n * PERIOD;
    }

    public static boolean creaksAt(int n) {
        return n >= 2 && n % CREAK_EVERY == 2;
    }

    public static boolean whooshAt(int n) {
        return n >= 4 && n % WHOOSH_EVERY == 0;
    }

    // ------------------------------------------------------------------
    // Hände an den Seilen (Rückwärtskinematik)
    // ------------------------------------------------------------------

    /**
     * Armwinkel {pitch, roll} (Bogenmaß), mit denen der Griffpunkt der Hand genau auf dem Seil liegt (über der Schulter,
     * Arm nach oben am Seil entlang). Rechnung im Sitz-Raum ohne Schaukeln: Das Seil ist dort senkrecht bei
     * x = ±{@link SwingLogic#ROPE_X}, z = 0 relativ zur Sitzmitte; der Körper ist um die Hüfte gelehnt.
     */
    public static void ropeArm(float leanDeg, float scale, boolean right, float[] out) {
        double[] a = new double[3], b = new double[3];
        double side = right ? 1.0 : -1.0;
        // zwei Punkte auf dem Seil (relativ zur Hüfte, Blöcke) -> Modell-px
        double hipY = SwingLogic.SEAT_TOP_Y + HIP_ABOVE_SEAT * scale;
        seatToModel(side * SwingLogic.ROPE_X, SwingLogic.SEAT_TOP_Y - hipY, -HIP_Z * scale, leanDeg, scale, a);
        seatToModel(side * SwingLogic.ROPE_X, SwingLogic.PIVOT_Y - hipY, -HIP_Z * scale, leanDeg, scale, b);
        double sx = right ? -SHOULDER_X : SHOULDER_X, sy = SHOULDER_Y, sz = 0.0;
        double gx = right ? -GRIP_X : GRIP_X, gy = GRIP_Y;
        double g = Math.hypot(gx, gy);
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double wx = a[0] - sx, wy = a[1] - sy, wz = a[2] - sz;
        double uu = ux * ux + uy * uy + uz * uz, uw = ux * wx + uy * wy + uz * wz, ww = wx * wx + wy * wy + wz * wz;
        double disc = uw * uw - uu * (ww - g * g);
        double s = disc >= 0.0 ? (-uw + Math.sqrt(disc)) / uu : -uw / uu;
        double dx = wx + s * ux, dy = wy + s * uy, dz = wz + s * uz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-9 && Math.abs(len - g) > 1.0E-9) {
            dx *= g / len;
            dy *= g / len;
            dz *= g / len;
        }
        double pitch = -Math.PI - Math.asin(Math.max(-1.0, Math.min(1.0, dz / gy)));
        double vx = gx, vy = gy * Math.cos(pitch);
        double roll = Math.atan2(dy, dx) - Math.atan2(vy, vx);
        while (roll > Math.PI) roll -= 2.0 * Math.PI;
        while (roll <= -Math.PI) roll += 2.0 * Math.PI;
        out[0] = (float) pitch;
        out[1] = (float) roll;
    }

    /** Punkt relativ zur Hüfte im Sitz-Raum (Blöcke) in Modell-px des (um {@code leanDeg} gelehnten) Spielers. */
    private static void seatToModel(double x, double y, double z, float leanDeg, float scale, double[] out) {
        x /= scale;
        y /= scale;
        z /= scale;
        double b = Math.toRadians(-leanDeg);
        double y1 = y * Math.cos(b) - z * Math.sin(b), z1 = y * Math.sin(b) + z * Math.cos(b);
        double by = y1 + HIP_LOCAL_Y;
        out[0] = -x / K;
        out[1] = HammockLogic.MODEL_ORIGIN_UP_PX - by / K;
        out[2] = z1 / K;
    }

    /** Griffpunkt (Modell-px) eines Arms mit diesen Winkeln (breite Arme). */
    public static double[] gripPoint(boolean right, float pitch, float yaw, float roll, double[] out) {
        CampfireMotion.rotZYX(right ? -GRIP_X : GRIP_X, GRIP_Y, 0.0, pitch, yaw, roll, out);
        out[0] += right ? -SHOULDER_X : SHOULDER_X;
        out[1] += SHOULDER_Y;
        return out;
    }

    // ------------------------------------------------------------------
    // Körpertransformation (Tests, Effekte, Kamera)
    // ------------------------------------------------------------------

    /** Punkt des stehenden Spielers (Raum von {@code setupTransforms}, Blöcke, Füße im Ursprung) in den Rahmen. */
    public static double[] bodyToFrame(Pose p, double x, double y, double z, double[] out) {
        bodyToSeat(p, x, y, z, out);
        return SwingLogic.swing(out[0], out[1], out[2], p.swingDeg, out);
    }

    /** Wie {@link #bodyToFrame}, aber ohne Schaukeln (Sitz in Ruhelage). */
    public static double[] bodyToSeat(Pose p, double x, double y, double z, double[] out) {
        y -= HIP_LOCAL_Y;
        double b = Math.toRadians(p.leanDeg);
        double y1 = y * Math.cos(b) - z * Math.sin(b), z1 = y * Math.sin(b) + z * Math.cos(b);
        double a = Math.toRadians(p.turnDeg);
        double x2 = x * Math.cos(a) + z1 * Math.sin(a), z2 = -x * Math.sin(a) + z1 * Math.cos(a);
        out[0] = x2 + p.hipX;
        out[1] = y1 + p.hipY;
        out[2] = z2 + p.hipZ;
        return out;
    }

    /** Winkel {pitch, yaw, roll} eines Körperteils aus der Pose. */
    public static float[] partAngles(Pose p, HammockMotion.Part part) {
        return switch (part) {
            case HEAD -> new float[]{p.headPitch, p.headYaw, p.headRoll};
            case BODY -> new float[]{0.0F, 0.0F, 0.0F};
            case RIGHT_ARM -> new float[]{p.rArmPitch, p.rArmYaw, p.rArmRoll};
            case LEFT_ARM -> new float[]{p.lArmPitch, p.lArmYaw, p.lArmRoll};
            case RIGHT_LEG -> new float[]{p.rLegPitch, p.rLegYaw, p.rLegRoll};
            case LEFT_LEG -> new float[]{p.lLegPitch, p.lLegYaw, p.lLegRoll};
        };
    }

    /** Punkt eines Körperteils (lokale px im Quader-Raum des Teils) in den Rahmen (mit Schaukeln). */
    public static double[] partToFrame(Pose p, HammockMotion.Part part, double lx, double ly, double lz, double[] out) {
        float[] a = partAngles(p, part);
        double[] m = new double[3];
        CampfireMotion.rotZYX(lx, ly, lz, a[0], a[1], a[2], m);
        HammockMotion.modelToBody(m[0] + part.px, m[1] + part.py, m[2] + part.pz, m);
        return bodyToFrame(p, m[0], m[1], m[2], out);
    }

    /** Modellpunkt (px) in den Rahmen (mit Schaukeln). */
    public static double[] modelToFrame(Pose p, double mx, double my, double mz, double[] out) {
        double[] m = HammockMotion.modelToBody(mx, my, mz, new double[3]);
        return bodyToFrame(p, m[0], m[1], m[2], out);
    }

    // ------------------------------------------------------------------
    // Kamera (lokaler Spieler): Drehpunkt über dem Sitz, schaukelt nicht mit
    // ------------------------------------------------------------------

    public static final double CAMERA_LIFT = 1.05;

    public static double[] cameraPivot(int seat) {
        return new double[]{SwingLogic.seatX(seat), SwingLogic.SEAT_TOP_Y + CAMERA_LIFT, 0.0};
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

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

    public static float easeOutBack(float x) {
        return HammockMotion.easeOutBack(x);
    }
}

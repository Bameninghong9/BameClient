package com.bame.client.emotes;

/**
 * Zeitleiste und Pose des fliegenden Teppichs (ohne Minecraft-Klassen, im flyingCarpetTest geprüft).
 * <p>
 * Ablauf: Mit einem goldenen Funkel-„Puff“ erscheint der Teppich eingerollt unter dem Spieler am Boden und rollt sich in
 * 0,4 s von der Mitte aus aus. Ab 0,08 s setzt sich der Spieler im Schneidersitz darauf (Beine zuerst angewinkelt, dann
 * abgesenkt). Erst wenn er sitzt (0,6 s), steigen Teppich und Reiter in 0,7 s weich (Ease-out) mit Funkeln auf die
 * Schwebehöhe (im Stand 1,2, in Fahrt bis 1,5, beim Schleichen 0,9 Blöcke, siehe {@link FlyingCarpetLogic}). Danach
 * folgen Schweben, Welle, Neigung und Haltung dem beobachteten Zustand. Endet das Emote, sinkt der Teppich mit dem
 * sitzenden Reiter weich zu Boden; erst dann steht der Spieler auf, der Teppich rollt sich zur Mitte ein (unter sich,
 * nie in die Beine) und verschwindet mit Funkeln ({@link #outro}).
 * <p>
 * Sitz: In der Pose liegt der Teppich am Boden; alle Körperteile werden per {@code originY} um {@link Pose#drop}
 * abgesenkt, die Unterseite der Oberschenkel liegt dann 0,2 px über dem Teppich. Die Schwebehöhe ist eine Verschiebung
 * der Render-Matrix ({@link #tilt}) für Reiter und Teppich gemeinsam. Vorbeugen dreht Rumpf, Kopf und Schultern um die
 * Hüfte (Drehpunkte versetzt wie beim Picknick). Im Stand liegen die Hände locker auf den Knien, in Fahrt greift die
 * rechte Hand nach der Vorderkante und die linke hält sich an der Seitenkante fest.
 * Modell-px wie {@code PlayerEntityModel} (+Y unten, −Z vorne, −X rechte Hand); Rahmen F wie in
 * {@link FlyingCarpetLogic}.
 */
public final class FlyingCarpetMotion {

    private FlyingCarpetMotion() {}

    public static final double K = HammockLogic.BLOCKS_PER_PX;
    public static final double O = HammockLogic.MODEL_ORIGIN_UP_PX;
    public static final double PX = FlyingCarpetLogic.PX;
    public static final double HIP_LOCAL_Y = HammockMotion.HIP_LOCAL_Y;
    /** Fußsohlen des stehenden Modells über dem Boden (Blöcke). */
    public static final double FEET_Y = (O - 24.0) * K;

    // ------------------------------------------------------------------
    // Zeitleiste (s)
    // ------------------------------------------------------------------

    /** Ausrollen von der Mitte aus. */
    public static final float UNROLL = 0.4F;
    /** Füße vom Boden auf den Läufer (1 px). */
    public static final float STEP_UP = 0.1F;
    public static final float SIT_START = 0.08F, SIT_END = 0.6F;
    /** Aufsteigen auf die Schwebehöhe, sobald der Spieler sitzt: weiches Ease-out (ohne Ruck an Anfang und Ende). */
    public static final float RISE_START = 0.6F, RISE_END = 1.3F;
    public static final float INTRO = RISE_END;
    /**
     * Auslaufen nach dem Ende: mit dem sitzenden Reiter weich zu Boden sinken, danach aufstehen (Hinsetzen rückwärts im
     * selben Tempo, ab {@code OUTRO_BLEND_START} weich in die Vanilla-Pose), dabei zur Mitte einrollen, kleiner werden und
     * mit Funkeln verschwinden.
     */
    public static final float OUTRO_DESCENT = 0.7F;
    public static final float OUTRO_STAND_START = 0.7F, OUTRO_BLEND_START = 0.9F, OUTRO_STAND_END = 1.3F;
    public static final float OUTRO_ROLL_START = 0.8F, OUTRO_ROLL_END = 1.15F;
    public static final float OUTRO_SHRINK_START = 1.1F, OUTRO_SHRINK = 0.4F;
    public static final float OUTRO_SPARKLE = 1.3F, OUTRO = 1.4F;

    // ------------------------------------------------------------------
    // Sitz und Haltung
    // ------------------------------------------------------------------

    /** Hüftgelenk so weit über der Oberseite des Teppichs (px): Oberschenkel 2 px plus etwas Luft. */
    public static final double SEAT_GAP_PX = 2.2;
    public static final double SEAT_REL = SEAT_GAP_PX * K;
    /** Hüfte über den Fußsohlen im Stand (Blöcke). */
    public static final double STAND_REL = HIP_LOCAL_Y - FEET_Y;
    /** Hüfte des sitzenden Reiters in der Pose (Teppich am Boden; die Schwebehöhe kommt in der Render-Matrix dazu). */
    public static final double HIP_SIT_Y = FlyingCarpetLogic.GROUND_TOP_Y + SEAT_REL;
    /** So weit sitzt der Spieler tiefer als im Stand (Blöcke). */
    public static final double SEAT_DROP = HIP_LOCAL_Y - HIP_SIT_Y;
    /** Beim Anwinkeln kurz angehoben (Fersen nicht im Teppich). */
    public static final double HOP = 0.05;
    /** Ego-Perspektive: beim Schleichen liegt die Kamera so viel tiefer (Augenhöhe 1,62 statt 1,27 Blöcke). */
    public static final double SNEAK_EYE_DROP = 1.62 - 1.27;

    /** Schneidersitz (Grad): Beine nach vorne angewinkelt und nach innen gekreuzt, rechtes Bein etwas höher. */
    public static final float R_LEG_PITCH = -96.0F, R_LEG_YAW = -35.0F, L_LEG_PITCH = -91.0F, L_LEG_YAW = 35.0F;
    public static final double LEG_X = 1.9, HIP_PX = 12.0;

    /** Vorbeugen (Grad): im Stand, in Fahrt (+ mit der Geschwindigkeit), beim Ducken, Atmen. */
    public static final float LEAN_IDLE = 7.0F, LEAN_MOVE = 22.0F, LEAN_FAST = 8.0F, LEAN_DUCK = 5.0F, BREATH_LEAN = 0.8F;
    public static final float BREATH_S = 3.4F, CHEST = 0.035F;

    public static final double SHOULDER_X = 5.0, SHOULDER_Y = 2.0, SHOULDER_Y_SLIM = 2.5;
    /** Handende entlang des Arms (px ab der Schulter). */
    public static final double ARM_END = 10.0;
    /** Hände auf den Knien: Stelle auf dem Schienbein des anderen Beins (px ab der Hüfte), knapp über der Oberseite. */
    public static final double KNEE_D = 8.0, KNEE_D_MIN = 3.5, KNEE_D_MAX = 12.0, HAND_LIFT = 1.2;
    /** Beim Wechsel zwischen Knie und Kante hebt sich die Hand in einem Bogen über die Beine (px, Mitte des Wechsels). */
    public static final double ARC_PX = 4.0;
    /** In Fahrt: rechte Hand an der Vorderkante (rechts vorne), linke Hand hält sich an der linken Seitenkante fest
     * (Rahmen, Blöcke). */
    public static final double HOLD_X = 0.49;
    public static final double HOLD_Z = FlyingCarpetLogic.CENTER_Z - FlyingCarpetLogic.HALF_LENGTH + 0.9 * PX;
    public static final double SIDE_X = -(FlyingCarpetLogic.HALF_WIDTH - 0.5 * PX);
    public static final double SIDE_Z = FlyingCarpetLogic.CENTER_Z - 0.44;

    /** Kopf: beim Ducken nach unten (Grad), gleicht die Kurven-Neigung zu diesem Anteil aus. */
    public static final float HEAD_DUCK = 8.0F, HEAD_COUNTER_ROLL = 0.4F;

    /** Vorschau: sanftes Neigen im Wechsel links/rechts, leicht vorne unten, ruhige Fahrt. */
    public static final float PREVIEW_BANK = 8.0F, PREVIEW_BANK_S = 4.2F, PREVIEW_NOSE = -2.5F, PREVIEW_SPEED = 0.35F;

    private static final float DEG = (float) (Math.PI / 180.0);

    // ------------------------------------------------------------------
    // Eingabe und Ergebnis
    // ------------------------------------------------------------------

    /** Eingaben für eine Pose (alles schon geglättet bzw. interpoliert). */
    public static final class Input {
        public float t;
        public float speed, sneak, hold;
        /** Kurven-Neigung der Glättung (Grad, wie {@link FlyingCarpetLogic.Track#bank}; angewendet mit {@link Pose#tiltW}). */
        public float bank;
        /** Blick relativ zum Körper (Grad, + = rechts) und Neigung (+ = unten). */
        public float lookYaw, lookPitch;
        public boolean look, slim;
    }

    /** Ergebnis von {@link #sample}; wiederverwendbar. */
    public static final class Pose {
        public float t;
        /** Hinsetzen 0..1, Beine anwinkeln, Absenken auf den Teppich, Aufsteigen auf die Schwebehöhe, Ausrollen. */
        public float sit, fold, lower, rise, unroll;
        /** Anteil, mit dem Neigung und Kippen angewendet werden (am Boden 0). */
        public float tiltW;
        /** Oberseite und Mitte des Teppichs, Hüftgelenk (Rahmen, Blöcke, ohne Schwebehöhe und Neigung). */
        public double carpetTop, carpetMid, hipY;
        /** {@code originY}-Versatz aller Körperteile (px, + = nach unten). */
        public float drop;
        /** Vorbeugen um die Hüfte (Bogenmaß) und daraus Versatz von Hals und Schultern (px). */
        public float lean, neckDY, neckDZ, shoulderDY, shoulderDZ;
        public float chestScale = 1.0F;
        public float headPitch, headYaw, headRoll;
        public float rArmPitch, rArmYaw, rArmRoll;
        public float lArmPitch, lArmYaw, lArmRoll;
        public float rLegPitch, rLegYaw, lLegPitch, lLegYaw;
        public boolean slim;
        /** Rest bis zum Ziel (px, + = zu kurz): Hände am Knie, rechte Hand an der Vorderkante, linke an der Seite. */
        public float rKneeGap, rHoldGap, lKneeGap, lHoldGap;
    }

    // ------------------------------------------------------------------
    // Zeitleiste, Schwebehöhe, Neigung
    // ------------------------------------------------------------------

    /**
     * Erscheinen, Hinsetzen und Aufsteigen zur Emote-Zeit {@code t} (Teppich in der Pose am Boden). Eingerollt steckt
     * die Rolle bis zu ihrer Oberseite im Boden und taucht beim Ausrollen auf ({@link #sinkAt}); der Spieler steigt auf
     * ihre Oberseite.
     */
    public static void timeline(float t, Pose out) {
        if (!(t > 0.0F)) t = 0.0F;
        out.t = t;
        out.unroll = clamp01(t / UNROLL);
        out.sit = easeInOut((t - SIT_START) / (SIT_END - SIT_START));
        out.fold = easeInOut((t - SIT_START) / ((SIT_END - SIT_START) * 0.6F));
        out.lower = easeInOut((t - SIT_START - 0.1F) / (SIT_END - SIT_START - 0.1F));
        out.rise = rise(t);
        out.tiltW = out.rise;
        out.carpetMid = FlyingCarpetLogic.GROUND_MID_Y - sinkAt(out.unroll);
        out.carpetTop = standTop(out.carpetMid, FlyingCarpetLogic.scaleAt(out.unroll));
        seat(t, out);
    }

    /** Worauf der Spieler steht: Oberseite des Teppichs (Mitte {@code mid}, Größe {@code scale}), mindestens der Boden. */
    private static double standTop(double mid, double scale) {
        return Math.max(FEET_Y, mid + 0.5 * FlyingCarpetLogic.THICK_PX * PX * scale);
    }

    /** Hüfte aus Hinsetzen und Absenken über der Oberseite {@code out.carpetTop}, mit dem Hochsteigen zur Zeit {@code t}. */
    private static void seat(float t, Pose out) {
        double rel = STAND_REL + (SEAT_REL - STAND_REL) * out.lower + HOP * Math.sin(Math.PI * out.fold) * (1.0 - out.lower);
        double onCarpet = out.carpetTop + rel;
        float stepUp = easeInOut(t / STEP_UP);
        out.hipY = HIP_LOCAL_Y + (onCarpet - HIP_LOCAL_Y) * stepUp;
        out.drop = (float) ((HIP_LOCAL_Y - out.hipY) / K);
    }

    /** Eingerollt steckt der Teppich so tief im Boden, dass die Oberseite der Rolle (in der Mitte) auf Bodenhöhe liegt. */
    public static final double ROLLED_SINK = FlyingCarpetLogic.GROUND_MID_Y
            + 0.5 * FlyingCarpetLogic.THICK_PX * PX * FlyingCarpetLogic.SCALE_ROLLED;

    /** So tief steckt der Teppich beim Ausrollen {@code unroll} im Boden (Blöcke; flach 0). */
    public static double sinkAt(float unroll) {
        return ROLLED_SINK * (1.0 - WitchBroomLogic.smoother(clamp01(unroll)));
    }

    /**
     * Aufsteigen 0..1 zur Emote-Zeit {@code t}: weiches Ease-out {@code 1 − (1 − x)³ · (1 + 3x)} – zu Beginn schnell, am
     * Ende langsam, Steigung 0 an Anfang und Ende (kein Ruck).
     */
    public static float rise(float t) {
        if (!(t > RISE_START)) return 0.0F;
        if (t >= RISE_END) return 1.0F;
        float x = (t - RISE_START) / (RISE_END - RISE_START);
        float u = 1.0F - x;
        return 1.0F - u * u * u * (1.0F + 3.0F * x);
    }

    /**
     * Angewendete Schwebehöhe (Blöcke): {@code rise(t) · alt} mit der geglätteten Höhe {@code alt}. In der
     * Ego-Perspektive ({@code fp} = 1: die Kamera bleibt auf Augenhöhe, das eigene Modell wird mit ausgeblendetem Kopf
     * gezeichnet) höchstens so hoch, dass der Kopf an der Kamera bleibt und man nicht in den eigenen Körper schaut;
     * Zwischenwerte mischen weich.
     *
     * @param tl Zeitleiste zur selben Zeit ({@link #timeline})
     */
    public static double altitude(float t, float alt, float sneak, float fp, Pose tl) {
        double a = (Float.isFinite(alt) ? Math.max(0.0F, alt) : (float) FlyingCarpetLogic.ALT_IDLE) * rise(t);
        if (fp > 0.0F) {
            double limit = Math.max(0.0, HIP_LOCAL_Y - tl.hipY - SNEAK_EYE_DROP * clamp01(sneak));
            if (a > limit) a -= (a - limit) * clamp01(fp);
        }
        return a;
    }

    /**
     * Neigung des ganzen Teppichs samt Reiter (Render-Matrix in {@code setupTransforms}): Schwebehöhe {@code alt}
     * ({@link #altitude}), Schweben, Kurven-Neigung und Kippen der Vorderkante um die Mitte des Teppichs; am Boden noch
     * ohne Bewegung, beim Aufsteigen weich eingeblendet.
     */
    public static void tilt(float t, float speed, float sneak, float bank, float nose, double alt,
                            FlyingCarpetLogic.Tilt out) {
        if (!(t > 0.0F)) t = 0.0F;
        float w = rise(t);
        // Schweben: in Fahrt und beim Schleichen ruhiger
        double bob = FlyingCarpetLogic.BOB * (1.0 - FlyingCarpetLogic.BOB_MOVING * clamp01(speed))
                * (1.0 - FlyingCarpetLogic.BOB_SNEAK * clamp01(sneak))
                * Math.sin(2.0 * Math.PI * FlyingCarpetLogic.BOB_HZ * t) * w;
        out.alt = Double.isFinite(alt) ? Math.max(0.0, alt) : 0.0;
        out.dy = out.alt + bob;
        out.pivotY = FlyingCarpetLogic.GROUND_MID_Y;
        out.pivotZ = FlyingCarpetLogic.CENTER_Z;
        out.bank = FlyingCarpetLogic.clamp(bank, -FlyingCarpetLogic.BANK_MAX, FlyingCarpetLogic.BANK_MAX) * w;
        out.nose = FlyingCarpetLogic.clamp(nose, FlyingCarpetLogic.NOSE_MIN, FlyingCarpetLogic.CLIMB_MAX) * w;
    }

    /**
     * So weit liegt der Kopf des Reiters über seiner normalen Höhe (Blöcke, ≥ 0): Nametag und Drehpunkt der
     * 3rd-Person-Kamera werden darum angehoben.
     *
     * @param dy    Verschiebung der Render-Matrix ({@link FlyingCarpetLogic.Tilt#dy})
     * @param hipY  Hüfte in der Pose ({@link Pose#hipY})
     * @param rider Anteil der Teppich-Pose (1 im Emote, beim Auslaufen {@link Outro#rider})
     */
    public static double headLift(double dy, double hipY, float rider) {
        double d = dy - (HIP_LOCAL_Y - hipY) * clamp01(rider);
        return d > 0.0 && Double.isFinite(d) ? d : 0.0;
    }

    /** Vorschau: Neigung im Wechsel links/rechts (Grad). */
    public static float previewBank(float t) {
        return PREVIEW_BANK * (float) Math.sin(2.0 * Math.PI * t / PREVIEW_BANK_S);
    }

    /** Vorschau: Phasen von Welle und Quasten bei ruhiger Fahrt. */
    public static double previewWave(float t) {
        return 2.0 * Math.PI * FlyingCarpetLogic.waveHz(PREVIEW_SPEED, 0.0F) * t;
    }

    public static double previewTassel(float t) {
        return 2.0 * Math.PI * FlyingCarpetLogic.tasselHz(PREVIEW_SPEED) * t;
    }

    // ------------------------------------------------------------------
    // Auslaufen nach dem Ende
    // ------------------------------------------------------------------

    /** Auslaufen nach dem Ende des Emotes; wiederverwendbar. */
    public static final class Outro {
        public boolean visible;
        /** Verbleibender Anteil von Schwebehöhe, Neigung und Welle (1 → 0, weich). */
        public float descent;
        /** Anteil der Teppich-Pose am Reiter (1 = sitzt wie im Emote, 0 = Vanilla-Pose). */
        public float rider;
        /** Zeit in der Zeitleiste für das Aufstehen (Hinsetzen rückwärts, höchstens bis zum Hochsteigen). */
        public float seatT;
        /** Ausrollen (1 = flach) und zusätzliche Verkleinerung. */
        public float unroll, shrink;
        /** So tief steckt der Teppich beim Einrollen im Boden (Blöcke, {@link #sinkAt}): die Rolle bleibt unter den Füßen. */
        public double sink;
    }

    /**
     * Auslaufen {@code age} s nach dem Ende (Emote-Zeit beim Ende {@code tEnd}): erst sinken Teppich und sitzender Reiter
     * weich zu Boden, dann steht der Reiter auf, währenddessen rollt sich der Teppich zur Mitte ein (die Enden unter den
     * Teppich, also in den Boden, nie in die Beine; die Rolle sinkt dabei auf Bodenhöhe), wird kleiner und verschwindet.
     */
    public static void outro(float age, float tEnd, Outro out) {
        out.visible = age >= 0.0F && age < OUTRO;
        if (!(age > 0.0F)) age = 0.0F;
        if (!(tEnd > 0.0F)) tEnd = 0.0F;
        out.descent = 1.0F - easeInOut(age / OUTRO_DESCENT);
        // Aufstehen: Hinsetzen rückwärts im selben Tempo wie beim Start (die Übergänge der Zeitleiste sind weich). Vom
        // Teppich steigt der Spieler nicht selbst herunter: er steht auf dessen Oberseite, die beim Einrollen im Boden
        // versinkt (siehe sampleOutro)
        out.seatT = Math.max(Math.min(tEnd, STEP_UP), Math.min(tEnd, SIT_END) - Math.max(0.0F, age - OUTRO_STAND_START));
        // Vanilla-Pose erst einmischen, wenn sich die Hüfte schon gehoben hat (sonst sinken die Füße kurz in den Teppich)
        out.rider = 1.0F - (float) WitchBroomLogic.smoother((age - OUTRO_BLEND_START) / (OUTRO_STAND_END - OUTRO_BLEND_START));
        float roll = (float) WitchBroomLogic.smoother((age - OUTRO_ROLL_START) / (OUTRO_ROLL_END - OUTRO_ROLL_START));
        out.unroll = clamp01(tEnd / UNROLL) * (1.0F - roll);
        out.sink = sinkAt(out.unroll);
        out.shrink = 1.0F - OUTRO_SHRINK
                * (float) WitchBroomLogic.smoother((age - OUTRO_SHRINK_START) / (OUTRO - OUTRO_SHRINK_START));
    }

    /**
     * Neigung beim Auslaufen: Verschiebung {@code dy0} vom Ende (Schwebehöhe samt Schweben) und die abklingende
     * Neigung der Glättung ({@code bank}, {@code nose}, beim Ende mit {@code w0} angewendet) gehen weich auf 0.
     */
    public static void outroTilt(Outro o, double dy0, float w0, float bank, float nose, FlyingCarpetLogic.Tilt out) {
        double d = Double.isFinite(dy0) ? Math.max(0.0, dy0) : 0.0;
        out.dy = d * o.descent;
        out.alt = out.dy;
        out.pivotY = FlyingCarpetLogic.GROUND_MID_Y;
        out.pivotZ = FlyingCarpetLogic.CENTER_Z;
        float w = clamp01(w0) * o.descent;
        out.bank = FlyingCarpetLogic.clamp(bank, -FlyingCarpetLogic.BANK_MAX, FlyingCarpetLogic.BANK_MAX) * w;
        out.nose = FlyingCarpetLogic.clamp(nose, FlyingCarpetLogic.NOSE_MIN, FlyingCarpetLogic.CLIMB_MAX) * w;
    }

    // ------------------------------------------------------------------
    // Pose
    // ------------------------------------------------------------------

    private static final ThreadLocal<double[][]> SCRATCH = ThreadLocal.withInitial(() -> new double[6][3]);
    private static final ThreadLocal<float[]> ANGLES = ThreadLocal.withInitial(() -> new float[2]);

    /** Pose zur Emote-Zeit {@code in.t}. */
    public static void sample(Input in, Pose out) {
        timeline(in.t, out);
        posture(in, out);
    }

    /**
     * Reiter beim Auslaufen ({@link #outro}): sitzt weiter (Haltung und Hände folgen der abklingenden Glättung), steht
     * nach dem Absinken auf (Hinsetzen rückwärts); Atmen und Umschauen laufen weiter. Das Mischen mit der Vanilla-Pose
     * ({@link Outro#rider}) übernimmt der Animator.
     *
     * @param w0 Anteil der Neigung beim Ende ({@link Pose#tiltW} zur Zeit {@code tEnd})
     */
    public static void sampleOutro(Input in, Outro o, float tEnd, float age, float w0, Pose out) {
        outroSeat(o, out);
        out.t = Math.max(0.0F, tEnd) + Math.max(0.0F, age);
        out.tiltW = clamp01(w0) * o.descent;
        out.rise = out.tiltW;
        posture(in, out);
    }

    /**
     * Zeitleiste des Reiters beim Auslaufen in {@code out}: Aufstehen ({@link Outro#seatT}) auf dem einrollenden, kleiner
     * werdenden Teppich bzw. dem Boden (nie darin).
     *
     * @return Hüfte ({@link Pose#hipY})
     */
    public static double outroSeat(Outro o, Pose out) {
        timeline(o.seatT, out);
        out.unroll = o.unroll;
        out.carpetMid = FlyingCarpetLogic.GROUND_MID_Y - o.sink;
        out.carpetTop = standTop(out.carpetMid, FlyingCarpetLogic.scaleAt(o.unroll) * o.shrink);
        seat(o.seatT, out);
        return out.hipY;
    }

    /** Haltung aus der Zeitleiste in {@code out} (Hinsetzen, Absenken) und den Eingaben. */
    private static void posture(Input in, Pose out) {
        float t = out.t;
        out.slim = in.slim;
        float s = out.sit;
        float speed = clamp01(in.speed), sneak = clamp01(in.sneak), hold = clamp01(in.hold);

        // Oberkörper: Vorbeugen um die Hüfte (im Stand leicht, in Fahrt mehr, beim Ducken etwas mehr), Atmen
        float breath = (float) Math.sin(2.0 * Math.PI * t / BREATH_S);
        float leanDeg = (lerp(LEAN_IDLE, LEAN_MOVE + LEAN_FAST * speed, hold) + LEAN_DUCK * sneak + BREATH_LEAN * breath) * s;
        float lean = leanDeg * DEG;
        out.lean = lean;
        double cl = Math.cos(lean), sl = Math.sin(lean);
        out.neckDY = (float) (HIP_PX * (1.0 - cl));
        out.neckDZ = (float) (-HIP_PX * sl);
        double shY = in.slim ? SHOULDER_Y_SLIM : SHOULDER_Y;
        double rel = shY - HIP_PX;
        out.shoulderDY = (float) (HIP_PX + rel * cl - shY);
        out.shoulderDZ = (float) (rel * sl);
        out.chestScale = 1.0F + CHEST * (0.5F + 0.5F * breath) * s;

        // Beine: Schneidersitz, keine Laufbewegung
        out.rLegPitch = R_LEG_PITCH * out.fold * DEG;
        out.rLegYaw = R_LEG_YAW * out.fold * DEG;
        out.lLegPitch = L_LEG_PITCH * out.fold * DEG;
        out.lLegYaw = L_LEG_YAW * out.fold * DEG;

        // Arme: im Stand Hände auf den Knien (Schienbein des jeweils anderen Beins liegt auf der eigenen Seite), in
        // Fahrt greift die rechte Hand nach der Vorderkante, die linke hält sich an der Seitenkante fest
        // Wechsel: das Griffziel wandert in einem Bogen über die Beine vom Knie zur Kante
        double[][] sc = SCRATCH.get();
        double[] sR = sc[0], sL = sc[1], knee = sc[2], edge = sc[5];
        shoulder(out, true, sR);
        shoulder(out, false, sL);
        float[] ang = ANGLES.get();
        double arc = ARC_PX * Math.sin(Math.PI * hold);
        out.rKneeGap = kneeTarget(out, sR, false, knee);
        frameToModel(HOLD_X, out.carpetTop + HAND_LIFT * K, HOLD_Z, edge);
        out.rHoldGap = (float) reachOut(sR, edge);
        armToward(sR, knee, edge, hold, arc, ang);
        out.rArmPitch = ang[0] * s;
        out.rArmYaw = ang[1] * s;
        out.rArmRoll = 0.0F;
        out.lKneeGap = kneeTarget(out, sL, true, knee);
        frameToModel(SIDE_X, out.carpetTop + HAND_LIFT * K, SIDE_Z, edge);
        out.lHoldGap = (float) reachOut(sL, edge);
        armToward(sL, knee, edge, hold, arc, ang);
        out.lArmPitch = ang[0] * s;
        out.lArmYaw = ang[1] * s;
        out.lArmRoll = 0.0F;

        // Kopf: schaut sich im Stand ab und zu langsam um, in Fahrt nach vorne; folgt deutlichem Umschauen
        float idleYaw = (float) (30.0 * cube(Math.sin(2.0 * Math.PI * t / 14.0)) + 4.0 * Math.sin(2.0 * Math.PI * t / 5.3 + 0.6));
        float idlePitch = (float) (3.0 + 3.0 * Math.sin(2.0 * Math.PI * t / 8.7 + 0.4));
        float baseYaw = idleYaw * (1.0F - hold);
        float basePitch = idlePitch * (1.0F - hold);
        float headYaw, headPitch;
        if (in.look) {
            float wy = (float) HammockLogic.wrapDegrees(in.lookYaw);
            float back = HammockMotion.lookBackFade(wy);
            float follow = Math.max(easeInOut((Math.abs(wy) - 12.0F) / 35.0F), hold) * back;
            // beim Start schaut der Kopf noch wie zuvor in Blickrichtung
            follow = lerp(1.0F, follow, s);
            headYaw = lerp(baseYaw, SwingMotion.softClamp(wy, -70.0F, 70.0F, 10.0F), follow);
            float lp = SwingMotion.softClamp(Float.isFinite(in.lookPitch) ? in.lookPitch : 0.0F, -40.0F, 40.0F, 8.0F);
            float followP = Math.max(follow, easeInOut((Math.abs(lp) - 8.0F) / 20.0F) * back);
            headPitch = lerp(basePitch, lp, followP);
        } else {
            headYaw = baseYaw * s;
            headPitch = basePitch * s;
        }
        headPitch += HEAD_DUCK * sneak * s;
        out.headPitch = headPitch * DEG;
        out.headYaw = headYaw * DEG;
        out.headRoll = -HEAD_COUNTER_ROLL * in.bank * out.tiltW * DEG;
    }

    /** Schulter-Drehpunkt (Modell-px) mit Vorbeugen und Absenken. */
    public static double[] shoulder(Pose p, boolean right, double[] out) {
        out[0] = right ? -SHOULDER_X : SHOULDER_X;
        out[1] = (p.slim ? SHOULDER_Y_SLIM : SHOULDER_Y) + p.shoulderDY + p.drop;
        out[2] = p.shoulderDZ;
        return out;
    }

    /** Oberseite eines Beins (Modell-px): Punkt {@code d} px entlang des Beins, {@code lift} px über der Oberfläche. */
    public static double[] legTop(Pose p, boolean right, double d, double lift, double[] out) {
        float pitch = right ? p.rLegPitch : p.lLegPitch, yaw = right ? p.rLegYaw : p.lLegYaw;
        CampfireMotion.rotZYX(0.0, d, -2.0 - lift, pitch, yaw, 0.0F, out);
        out[0] += right ? -LEG_X : LEG_X;
        out[1] += HIP_PX + p.drop;
        return out;
    }

    /**
     * Hand auf das Schienbein: Punkt auf der Oberseite des Beins ({@code rightLeg}), an dem das Handende genau aufliegt
     * (Abstand {@link #ARM_END} zur Schulter), möglichst bei {@link #KNEE_D}.
     *
     * @return Rest bis zum Ziel (px, + = zu kurz, − = Hand im Bein)
     */
    private static float kneeTarget(Pose p, double[] s, boolean rightLeg, double[] tgt) {
        double[] a = SCRATCH.get()[3], b = SCRATCH.get()[4];
        legTop(p, rightLeg, 0.0, HAND_LIFT, a);
        legTop(p, rightLeg, 1.0, HAND_LIFT, b);
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double wx = a[0] - s[0], wy = a[1] - s[1], wz = a[2] - s[2];
        double uw = ux * wx + uy * wy + uz * wz;
        double disc = uw * uw - (wx * wx + wy * wy + wz * wz) + ARM_END * ARM_END;
        double d = KNEE_D;
        if (disc >= 0.0) {
            double r = Math.sqrt(disc);
            // weitere Lösung: die Hand liegt zum Knie hin, nicht an der Hüfte
            d = Math.max(KNEE_D_MIN, Math.min(KNEE_D_MAX, -uw + r));
        }
        tgt[0] = a[0] + ux * d;
        tgt[1] = a[1] + uy * d;
        tgt[2] = a[2] + uz * d;
        return (float) (dist(s, tgt) - ARM_END);
    }

    private static final ThreadLocal<double[]> PATH = ThreadLocal.withInitial(() -> new double[3]);

    /**
     * Arm zum Griffziel zwischen Knie ({@code hold} = 0) und Kante ({@code hold} = 1) ausrichten; unterwegs liegt das
     * Ziel {@code arc} px höher (Bogen über die Beine) und rückt bei Bedarf nach außen (Hand nie im Bein).
     */
    private static void armToward(double[] s, double[] knee, double[] edge, float hold, double arc, float[] ang) {
        double[] t = PATH.get();
        t[0] = knee[0] + (edge[0] - knee[0]) * hold;
        t[1] = knee[1] + (edge[1] - knee[1]) * hold - arc;
        t[2] = knee[2] + (edge[2] - knee[2]) * hold;
        if (hold >= 1.0F) reachOut(s, t);
        aim(s, t[0], t[1], t[2], ang);
    }

    /**
     * Liegt das Griffziel näher als die Armlänge (starkes Vorbeugen, schmale Arme), wird es auf gleicher Höhe nach
     * außen geschoben, bis das Handende genau dort liegt (die Hand drückt nicht in den Teppich).
     *
     * @return Rest bis zum Ziel (px, ≥ 0; + = Hand reicht nicht ganz)
     */
    private static double reachOut(double[] s, double[] tgt) {
        double dx = tgt[0] - s[0], dy = tgt[1] - s[1], dz = tgt[2] - s[2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= ARM_END) return d - ARM_END;
        double h = Math.sqrt(dx * dx + dz * dz), want = ARM_END * ARM_END - dy * dy;
        if (h < 1.0E-6 || want <= 0.0) return 0.0;
        double k = Math.sqrt(want) / h;
        tgt[0] = s[0] + dx * k;
        tgt[2] = s[2] + dz * k;
        return 0.0;
    }

    /**
     * Arm (zeigt ungedreht entlang +Y) vom Drehpunkt {@code s} zum Ziel ausrichten: {pitch, yaw} (Bogenmaß),
     * {@code R = Ry(yaw)·Rx(pitch)}: (0, 1, 0) → (sin p · sin y, cos p, sin p · cos y), pitch in [−π, 0].
     */
    public static void aim(double[] s, double tx, double ty, double tz, float[] out) {
        double vx = tx - s[0], vy = ty - s[1], vz = tz - s[2];
        double rho = Math.sqrt(vx * vx + vz * vz);
        if (rho < 1.0E-9) {
            out[0] = vy >= 0.0 ? 0.0F : (float) -Math.PI;
            out[1] = 0.0F;
            return;
        }
        out[0] = (float) Math.atan2(-rho, vy);
        out[1] = (float) Math.atan2(-vx, -vz);
    }

    // ------------------------------------------------------------------
    // Umrechnung Modell <-> Rahmen (Tests, Effekte)
    // ------------------------------------------------------------------

    /** Punkt im Rahmen (Blöcke) in Modell-px (Raum des Modells; Körperteile tragen {@link Pose#drop} selbst). */
    public static double[] frameToModel(double x, double y, double z, double[] out) {
        out[0] = -x / K;
        out[1] = O - y / K;
        out[2] = z / K;
        return out;
    }

    /** Modell-px in den Rahmen (Umkehrung von {@link #frameToModel}). */
    public static double[] modelToFrame(double mx, double my, double mz, double[] out) {
        out[0] = -mx * K;
        out[1] = (O - my) * K;
        out[2] = mz * K;
        return out;
    }

    /** Drehpunkt (Modell-px) eines Körperteils in der Pose (Absenken für alle, Vorbeugen für Rumpf/Kopf/Arme). */
    public static double[] partPivot(Pose p, HammockMotion.Part part, double[] out) {
        out[0] = part.px;
        out[1] = part.py + p.drop;
        out[2] = part.pz;
        switch (part) {
            case HEAD, BODY -> {
                out[1] += p.neckDY;
                out[2] += p.neckDZ;
            }
            case RIGHT_ARM, LEFT_ARM -> {
                if (p.slim) out[1] += SHOULDER_Y_SLIM - SHOULDER_Y;
                out[1] += p.shoulderDY;
                out[2] += p.shoulderDZ;
            }
            default -> {}
        }
        return out;
    }

    /** Winkel {pitch, yaw, roll} eines Körperteils (Bogenmaß). */
    public static float[] partAngles(Pose p, HammockMotion.Part part) {
        return switch (part) {
            case HEAD -> new float[]{p.headPitch, p.headYaw, p.headRoll};
            case BODY -> new float[]{p.lean, 0.0F, 0.0F};
            case RIGHT_ARM -> new float[]{p.rArmPitch, p.rArmYaw, p.rArmRoll};
            case LEFT_ARM -> new float[]{p.lArmPitch, p.lArmYaw, p.lArmRoll};
            case RIGHT_LEG -> new float[]{p.rLegPitch, p.rLegYaw, 0.0F};
            case LEFT_LEG -> new float[]{p.lLegPitch, p.lLegYaw, 0.0F};
        };
    }

    /** Punkt eines Körperteils (lokale px im Quader-Raum des Teils) in den Rahmen (ohne Schwebehöhe und Neigung). */
    public static double[] partToFrame(Pose p, HammockMotion.Part part, double lx, double ly, double lz, double[] out) {
        float[] a = partAngles(p, part);
        double[] m = new double[3];
        CampfireMotion.rotZYX(lx, ly, lz, a[0], a[1], a[2], m);
        double[] pv = partPivot(p, part, new double[3]);
        return modelToFrame(m[0] + pv[0], m[1] + pv[1], m[2] + pv[2], out);
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double cube(double x) {
        return x * x * x;
    }

    public static float clamp01(float x) {
        return x < 0.0F ? 0.0F : (x > 1.0F ? 1.0F : x);
    }

    public static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    public static float easeInOut(float x) {
        x = clamp01(x);
        return x * x * (3.0F - 2.0F * x);
    }
}

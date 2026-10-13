package com.bame.client.emotes;

/**
 * Zeitleiste und Pose des Picknick-Emotes (ohne Minecraft-Klassen, im swingPicnicTest geprüft). Alles hängt nur von der
 * Emote-Zeit {@code t} und (für die Ameisen) von UUID + gemeinsamer Zeitbasis ab, daher sehen alle Clients dasselbe.
 * <p>
 * Erscheinen: Die Decke entfaltet sich (erst in der Breite, dann in der Tiefe, 0,05–0,65 s), der Korb springt auf
 * (0,45–0,75 s), Flasche, Serviette und Apfel erscheinen (0,6–0,9 s); der Spieler setzt sich im Schneidersitz
 * (0,1–0,9 s). Ab {@link #LOOP_START} läuft eine 18-s-Schleife: Sandwich aus dem Korb nehmen (Deckel geht auf), drei
 * Bissen mit Kauen (das Sandwich wird von vorne her kleiner), Flasche nehmen, drei Schlucke mit zurückgelegtem Kopf,
 * Flasche zurückstellen, zufrieden umschauen. Krabbelt in der ruhigen Phase eine Ameise vorbei, schaut er hin und
 * schnippt sie weg (siehe {@link PicnicLogic}).
 * <p>
 * Körper: Der ganze Spieler wird um {@link Pose#drop} abgesenkt (Raum von {@code setupTransforms}); Beine gekreuzt nach
 * vorne. Vorbeugen dreht Rumpf, Kopf und Schultern um die Hüfte (Versatz der Drehpunkte wie beim Lagerfeuer). Arme
 * zeigen auf Zielpunkte im Modellraum (Korb, Mund, Flasche, Ameise). Modell-px wie {@code PlayerEntityModel}.
 */
public final class PicnicMotion {

    private PicnicMotion() {}

    public static final double K = HammockLogic.BLOCKS_PER_PX;
    public static final double O = HammockLogic.MODEL_ORIGIN_UP_PX;
    public static final double HIP_LOCAL_Y = HammockMotion.HIP_LOCAL_Y;

    // ------------------------------------------------------------------
    // Zeitleiste (s)
    // ------------------------------------------------------------------

    public static final float SIT_START = 0.1F, SIT_END = 0.9F;
    public static final float UNFOLD_X_START = 0.05F, UNFOLD_X_END = 0.35F, UNFOLD_Z_START = 0.3F, UNFOLD_Z_END = 0.65F;
    public static final float BASKET_START = 0.45F, BASKET_END = 0.75F;
    public static final float ITEMS_START = 0.6F, ITEMS_END = 0.9F;
    public static final float LOOP_START = 1.0F, LOOP = 18.0F;

    // Schleifenzeit
    public static final float LID_OPEN_START = 1.2F, LID_OPEN_END = 1.55F, LID_CLOSE_START = 2.1F, LID_CLOSE_END = 2.45F;
    public static final float SANDWICH_TAKE = 1.75F;
    public static final float[] BITES = {3.3F, 4.5F, 5.7F};
    public static final float BITE_SHRINK = 0.08F;
    public static final float BOTTLE_PICK = 7.7F, BOTTLE_PUT = 11.1F;
    public static final float[] GULPS = {9.05F, 9.55F, 10.05F};
    /** Ruhige Phase, in der eine Ameise weggeschnippt werden darf (Schleifenzeit). */
    public static final float FLICK_FROM = 13.0F, FLICK_TO = 16.8F;

    /** Schleifenzeit [0, LOOP) für t ≥ LOOP_START (davor 0). */
    public static float loopTime(float t) {
        if (t <= LOOP_START) return 0.0F;
        return (t - LOOP_START) % LOOP;
    }

    /** Liegt der Schleifen-Zeitpunkt {@code e} in irgendeiner Schleife im Intervall (t0, t1]? */
    public static boolean crossed(float t0, float t1, float e) {
        if (t1 <= LOOP_START) return false;
        float a = Math.max(t0, LOOP_START) - LOOP_START;
        float b = t1 - LOOP_START;
        int k0 = (int) Math.floor((a - e) / LOOP);
        for (int k = Math.max(0, k0); k <= k0 + 2; k++) {
            float abs = k * LOOP + e;
            if (abs > a && abs <= b && abs + LOOP_START > t0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Körper (Modell-px bzw. Blöcke)
    // ------------------------------------------------------------------

    /** Hüftgelenk über der Decke: Unterseite der Oberschenkel (2 px) plus etwas Luft. */
    public static final double HIP_SIT_Y = PicnicLogic.BLANKET_TOP + 2.2 * K;
    /** So weit wird der ganze Spieler im Sitzen abgesenkt (Blöcke). */
    public static final double DROP = HIP_LOCAL_Y - HIP_SIT_Y;
    /** Schneidersitz (Grad): rechtes Bein oben. */
    public static final float R_LEG_PITCH = -97.0F, R_LEG_YAW = -36.0F, L_LEG_PITCH = -92.0F, L_LEG_YAW = 37.0F;
    public static final float SHOULDER_X = 5.0F, SHOULDER_Y = 2.0F, HIP_PX = 12.0F;
    /** Griffpunkt in der Hand (Arm-Lokalsystem, px). */
    public static final float GRIP_Y = 9.0F;

    /** Ergebnis von {@link #sample}; wiederverwendbar. */
    public static final class Pose {
        public float t, u;
        public float sit;
        /** Absenkung des ganzen Spielers (Blöcke, ≥ 0). */
        public double drop;
        /** Erscheinen: Decke (Breite, Tiefe), Korb, kleine Sachen 0..1 (Korb/Sachen mit Überschwingen). */
        public float blanketX, blanketZ, basket, items;
        /** Korbdeckel offen 0..1. */
        public float lid;
        /** Vorbeugen (Bogenmaß, + = vor) und daraus Versatz von Rumpf/Kopf (neck) und Schultern (px). */
        public float lean, neckDY, neckDZ, shoulderDY, shoulderDZ;
        public float headPitch, headYaw, headRoll;
        public float rArmPitch, rArmYaw, rArmRoll;
        public float lArmPitch, lArmYaw, lArmRoll;
        public float rLegPitch, rLegYaw, lLegPitch, lLegYaw;
        public float chestScale = 1.0F;
        /** Sandwich in der rechten Hand und Größe der drei Teile (0 = hinten/fern, 2 = vorne/zum Mund). */
        public boolean sandwichHeld;
        public final float[] sandwichParts = new float[3];
        /** Flasche in der linken Hand (sonst auf der Decke) und ihre Neigung (Grad um X, - = Hals zum Gesicht). */
        public boolean bottleHeld;
        public float bottleTilt;
        /** Ameise, die gerade weggeschnippt wird (Index) oder -1. */
        public int flickAnt = -1;
    }

    // Kanäle der Schlüsselposen
    private static final int RX = 0, RY = 1, RZ = 2, LX = 3, LY = 4, LZ = 5, RROLL = 6, LROLL = 7, LEAN = 8, HP = 9,
            HY = 10, HR = 11, LOOKW = 12, BTILT = 13, N = 14;
    private static final float[][] KEYS;

    /** Weltpunkt im Rahmen (Blöcke) in Modell-px des sitzenden Spielers (ohne Vorbeugen). */
    public static double[] frameToModel(double x, double y, double z, double[] out) {
        out[0] = -x / K;
        out[1] = O - (y + DROP) / K;
        out[2] = z / K;
        return out;
    }

    static {
        double[] m = new double[3];
        float[] rest = new float[N];
        set3(rest, RX, -3.6F, 9.3F, -5.2F);
        set3(rest, LX, 3.6F, 9.3F, -5.2F);
        rest[LOOKW] = 1.0F;

        frameToModel(PicnicLogic.BASKET_X - 0.09, 0.2, PicnicLogic.BASKET_Z + 0.02, m);
        float[] basket = with(rest, LEAN, 18.0F, HP, 24.0F, HY, 30.0F, LOOKW, 0.0F, RROLL, -8.0F);
        set3(basket, RX, (float) m[0], (float) m[1], (float) m[2]);
        float[] basketUp = with(basket, LEAN, 10.0F, HP, 14.0F, HY, 18.0F);
        set3(basketUp, RX, -6.0F, 6.5F, -6.0F);
        float[] hold = with(rest, HP, 10.0F, HY, 0.0F, LOOKW, 0.0F);
        set3(hold, RX, -2.0F, 1.5F, -8.2F);
        float[] mouth = with(hold, HP, 2.0F);
        set3(mouth, RX, -0.8F, -1.8F, -7.6F);
        float[] bite = with(mouth, HP, 9.0F);
        set3(bite, RX, -0.6F, -2.0F, -7.0F);
        float[] chew = with(mouth, HP, -1.0F);
        set3(chew, RX, -1.6F, -0.6F, -8.0F);
        float[] done = with(rest, HP, 6.0F, LOOKW, 0.3F);

        frameToModel(PicnicLogic.BOTTLE_X, PicnicLogic.BOTTLE_GRIP_Y, PicnicLogic.BOTTLE_Z, m);
        float[] bottle = with(rest, LEAN, 30.0F, HP, 26.0F, HY, -22.0F, LOOKW, 0.0F);
        set3(bottle, LX, (float) m[0], (float) m[1], (float) m[2]);
        float[] lift = with(rest, HP, -4.0F, LOOKW, 0.0F, BTILT, -40.0F);
        set3(lift, LX, 1.6F, -0.5F, -8.0F);
        float[] drink = with(lift, HP, -24.0F, BTILT, -112.0F, LROLL, 6.0F);
        set3(drink, LX, 0.4F, -4.6F, -6.3F);
        float[] restAfter = rest.clone();

        KEYS = new float[][]{
                k(0.0F, rest),
                k(1.0F, rest),
                k(1.7F, basket),
                k(1.95F, basketUp),
                k(2.5F, hold),
                k(3.0F, mouth),
                k(BITES[0], bite),
                k(3.65F, chew),
                k(4.2F, mouth),
                k(BITES[1], bite),
                k(4.85F, chew),
                k(5.4F, mouth),
                k(BITES[2], bite),
                k(6.1F, chew),
                k(6.8F, done),
                k(7.1F, done),
                k(BOTTLE_PICK, bottle),
                k(8.3F, lift),
                k(8.8F, drink),
                k(10.25F, drink),
                k(10.6F, with(lift, BTILT, -30.0F)),
                k(BOTTLE_PUT, bottle),
                k(11.8F, restAfter),
                k(LOOP, restAfter),
        };
    }

    private static void set3(float[] v, int c, float x, float y, float z) {
        v[c] = x;
        v[c + 1] = y;
        v[c + 2] = z;
    }

    private static float[] k(float time, float[] values) {
        float[] out = new float[N + 1];
        out[0] = time;
        System.arraycopy(values, 0, out, 1, N);
        return out;
    }

    private static float[] with(float[] base, float... pairs) {
        float[] out = base.clone();
        for (int i = 0; i + 1 < pairs.length; i += 2) out[(int) pairs[i]] = pairs[i + 1];
        return out;
    }

    private static final float DEG = (float) (Math.PI / 180.0);

    /** Pose ohne Ameisen und ohne Blick-Folgen (Vorschau, Tests). */
    public static void sample(float t, Pose out) {
        sample(t, null, 0L, false, 0.0F, 0.0F, false, out);
    }

    /**
     * Pose zur Emote-Zeit {@code t}.
     *
     * @param id/seedMs Spieler-UUID und gemeinsame Zeitbasis für die Ameisen
     * @param ants      Ameisen berücksichtigen (Wegschnippen)
     * @param lookYawDeg Blick relativ zur Ausrichtung (Grad, + = rechts), {@code lookPitchDeg} + = unten
     */
    public static void sample(float t, String id, long seedMs, boolean ants, float lookYawDeg, float lookPitchDeg,
                              boolean look, Pose out) {
        if (t < 0.0F) t = 0.0F;
        out.t = t;
        float u = loopTime(t);
        out.u = u;

        // Erscheinen
        out.blanketX = easeOutCubic((t - UNFOLD_X_START) / (UNFOLD_X_END - UNFOLD_X_START));
        out.blanketZ = easeOutCubic((t - UNFOLD_Z_START) / (UNFOLD_Z_END - UNFOLD_Z_START));
        out.basket = HammockMotion.easeOutBack(clamp01((t - BASKET_START) / (BASKET_END - BASKET_START)));
        out.items = HammockMotion.easeOutBack(clamp01((t - ITEMS_START) / (ITEMS_END - ITEMS_START)));
        out.sit = easeInOut((t - SIT_START) / (SIT_END - SIT_START));
        // Beine falten zuerst, Absenken folgt (sonst tauchen die Füße beim Hinsetzen in den Boden)
        float fold = easeInOut((t - SIT_START) / ((SIT_END - SIT_START) * 0.6F));
        float lower = easeInOut((t - SIT_START - 0.1F) / (SIT_END - SIT_START - 0.1F));
        // beim Anwinkeln kurz angehoben (Fersen nicht in der Decke)
        out.drop = DROP * lower - 0.05 * Math.sin(Math.PI * fold) * (1.0 - lower);

        // Schlüsselposen (weich zwischen zwei benachbarten)
        float[] a = KEYS[0], b = KEYS[0];
        float f = 0.0F;
        if (t > LOOP_START) {
            int i = 0;
            while (i < KEYS.length - 2 && u >= KEYS[i + 1][0]) i++;
            a = KEYS[i];
            b = KEYS[i + 1];
            f = easeInOut((u - a[0]) / (b[0] - a[0]));
        }
        float[] v = VALUES.get();
        for (int c = 0; c < N; c++) v[c] = a[c + 1] + (b[c + 1] - a[c + 1]) * f;

        // Kauen: Kopf nickt zweimal nach jedem Bissen; Schlucken: kleiner Ruck
        for (float bt : BITES) {
            float d = u - bt - 0.1F;
            if (d > 0.0F && d < 0.9F) v[HP] += 3.0F * (float) Math.sin(d / 0.9F * Math.PI * 4.0) * (1.0F - d / 0.9F);
        }
        for (float g : GULPS) {
            float d = u - g + 0.15F;
            if (d > 0.0F && d < 0.3F) v[HP] -= 3.0F * (float) Math.sin(d / 0.3F * Math.PI);
        }

        // Ameise wegschnippen (nur in der ruhigen Phase, siehe PicnicLogic.ant)
        out.flickAnt = -1;
        float flickLook = 0.0F, flickArm = 0.0F, snap = 0.0F;
        double[] antModel = null;
        if (ants && t > LOOP_START) {
            int slot = PicnicLogic.antSlot(t + 1.2F);
            for (int s = Math.max(0, slot - 1); s <= slot; s++) {
                if (s < 0) continue;
                PicnicLogic.Ant ant = PicnicLogic.ant(id, seedMs, s);
                if (!ant.flicked()) continue;
                float d = t - ant.flickAt();
                if (d < -1.2F || d > 0.9F) continue;
                flickLook = bump(d, -1.2F, -0.6F, 0.35F, 0.9F);
                flickArm = bump(d, -0.6F, -0.12F, 0.12F, 0.8F);
                snap = d > -0.12F && d < 0.2F ? (float) Math.sin((d + 0.12F) / 0.32F * Math.PI) : 0.0F;
                double[] st = PicnicLogic.antState(ant, Math.min(t, ant.flickAt()), new double[6]);
                antModel = frameToModel(st[0], st[1], st[2], new double[3]);
                out.flickAnt = s;
                break;
            }
        }
        if (antModel != null) {
            v[LEAN] = lerp(v[LEAN], 26.0F, flickArm);
            v[LOOKW] *= 1.0F - flickLook;
            v[RX] = lerp(v[RX], (float) antModel[0], flickArm);
            v[RY] = lerp(v[RY], (float) antModel[1], flickArm);
            v[RZ] = lerp(v[RZ], (float) antModel[2], flickArm);
            v[RROLL] += 40.0F * snap;
        }

        // Vorbeugen um die Hüfte: Rumpf/Kopf (Hals) und Schultern versetzen
        float s = out.sit;
        float breath = (float) Math.sin(t * 2.1);
        float lean = (v[LEAN] * s + 1.2F * breath * s) * DEG;
        out.lean = lean;
        double cl = Math.cos(lean), sl = Math.sin(lean);
        out.neckDY = (float) (HIP_PX * (1.0 - cl));
        out.neckDZ = (float) (-HIP_PX * sl);
        double shoulderRel = SHOULDER_Y - HIP_PX;
        out.shoulderDY = (float) (HIP_PX + shoulderRel * cl - SHOULDER_Y);
        out.shoulderDZ = (float) (shoulderRel * sl);
        out.chestScale = 1.0F + 0.04F * (0.5F + 0.5F * breath) * s;

        // Arme: auf die Zielpunkte zeigen (von der versetzten Schulter aus); beim Hinsetzen aus dem Stand
        float[] ang = ANGLES.get();
        aim(-SHOULDER_X, SHOULDER_Y + out.shoulderDY, out.shoulderDZ, v[RX], v[RY], v[RZ], ang);
        out.rArmPitch = ang[0] * s;
        out.rArmYaw = ang[1] * s;
        out.rArmRoll = v[RROLL] * DEG * s;
        aim(SHOULDER_X, SHOULDER_Y + out.shoulderDY, out.shoulderDZ, v[LX], v[LY], v[LZ], ang);
        out.lArmPitch = ang[0] * s;
        out.lArmYaw = ang[1] * s;
        out.lArmRoll = v[LROLL] * DEG * s;

        // Beine: Schneidersitz
        out.rLegPitch = R_LEG_PITCH * fold * DEG;
        out.rLegYaw = R_LEG_YAW * fold * DEG;
        out.lLegPitch = L_LEG_PITCH * fold * DEG;
        out.lLegYaw = L_LEG_YAW * fold * DEG;

        // Kopf: gemächlich umschauen (in der Ruhephase weiter), zur Ameise, folgt deutlichem Umschauen des Spielers
        float lw = v[LOOKW];
        float idleYaw = (float) ((8.0 + 22.0 * lw) * Math.sin(t * 2.0 * Math.PI / 9.1)
                + (3.0 + 7.0 * lw) * Math.sin(t * 2.0 * Math.PI / 3.7 + 1.0));
        float idlePitch = (float) ((2.0 + 4.0 * lw) * Math.sin(t * 2.0 * Math.PI / 6.3 + 0.4)) - 4.0F * lw;
        float headYaw = idleYaw * Math.max(lw, 0.25F) + v[HY];
        float headPitch = idlePitch + v[HP];
        if (antModel != null && flickLook > 0.0F) {
            float[] la = ANGLES.get();
            CampfireMotion.lookAt(0.0F, out.neckDY, out.neckDZ, antModel, la);
            headYaw = lerp(headYaw, la[1] / DEG, flickLook);
            headPitch = lerp(headPitch, la[0] / DEG, flickLook);
        }
        if (look) {
            float wy = (float) HammockLogic.wrapDegrees(lookYawDeg);
            float ly = clamp(wy, -70.0F, 70.0F);
            // nach hinten schauen: Kopf kehrt weich zurück (kein Umspringen bei ±180°)
            float follow = easeInOut((Math.abs(ly) - 25.0F) / 30.0F) * Math.max(lw, 0.3F) * HammockMotion.lookBackFade(wy);
            headYaw = lerp(headYaw, ly, follow);
            headPitch = lerp(headPitch, clamp(lookPitchDeg, -40.0F, 40.0F) * 0.6F, follow);
        }
        // Kopfwinkel gelten im Modellraum (der Kopf kippt beim Vorbeugen nicht mit)
        out.headPitch = headPitch * DEG * s;
        out.headYaw = headYaw * DEG * s;
        out.headRoll = (float) (2.0 * Math.sin(t * 2.0 * Math.PI / 5.7)) * DEG * s * lw + v[HR] * DEG;

        // Requisiten
        out.lid = t <= LOOP_START ? 0.0F
                : easeInOut((u - LID_OPEN_START) / (LID_OPEN_END - LID_OPEN_START))
                * (1.0F - easeInOut((u - LID_CLOSE_START) / (LID_CLOSE_END - LID_CLOSE_START)));
        boolean inLoop = t > LOOP_START;
        out.sandwichHeld = inLoop && u >= SANDWICH_TAKE && u < BITES[2] + BITE_SHRINK;
        for (int p = 0; p < 3; p++) {
            float biteAt = BITES[2 - p];
            out.sandwichParts[p] = out.sandwichHeld ? 1.0F - clamp01((u - biteAt) / BITE_SHRINK) : 0.0F;
        }
        if (out.sandwichHeld) {
            // frisch aus dem Korb: kurz aufploppen
            float g = clamp01((u - SANDWICH_TAKE) / 0.12F);
            for (int p = 0; p < 3; p++) out.sandwichParts[p] *= g;
        }
        out.bottleHeld = inLoop && u >= BOTTLE_PICK && u < BOTTLE_PUT;
        out.bottleTilt = out.bottleHeld ? v[BTILT] : 0.0F;
    }

    private static final ThreadLocal<float[]> VALUES = ThreadLocal.withInitial(() -> new float[N]);
    private static final ThreadLocal<float[]> ANGLES = ThreadLocal.withInitial(() -> new float[2]);

    /** Weicher Buckel 0..1: steigt von a nach b, hält bis c, fällt bis d. */
    private static float bump(float x, float a, float b, float c, float d) {
        return easeInOut((x - a) / (b - a)) * (1.0F - easeInOut((x - c) / (d - c)));
    }

    /** Vorwärts-Bias (px) für den Yaw: hängt der Arm fast senkrecht, bleibt der Yaw so stetig (kein Umklappen). */
    private static final double AIM_BIAS = 2.0;

    /**
     * Arm (zeigt ungedreht entlang +Y) vom Drehpunkt (sx, sy, sz) zum Ziel ausrichten: {pitch, yaw} (Bogenmaß).
     * R = Ry(yaw)·Rx(pitch): (0,1,0) → (sin p · sin y, cos p, sin p · cos y), pitch in [-π, 0]. Der Yaw wird mit einem
     * kleinen Vorwärts-Bias bestimmt (stetig auch bei senkrecht hängendem Arm), der Pitch dazu bestmöglich.
     */
    public static void aim(double sx, double sy, double sz, double tx, double ty, double tz, float[] out) {
        double vx = tx - sx, vy = ty - sy, vz = tz - sz;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1.0E-6) {
            out[0] = 0.0F;
            out[1] = 0.0F;
            return;
        }
        double yaw = Math.atan2(-vx, -vz + AIM_BIAS);
        double along = vx * Math.sin(yaw) + vz * Math.cos(yaw);
        out[0] = (float) Math.atan2(along, vy);
        out[1] = (float) yaw;
    }

    /** Griffpunkt (Modell-px) der rechten bzw. linken Hand aus der Pose (breite Arme, ohne Griff-Versatz quer). */
    public static double[] gripPoint(Pose p, boolean right, double[] out) {
        float pitch = right ? p.rArmPitch : p.lArmPitch, yaw = right ? p.rArmYaw : p.lArmYaw;
        float roll = right ? p.rArmRoll : p.lArmRoll;
        CampfireMotion.rotZYX(0.0, GRIP_Y, 0.0, pitch, yaw, roll, out);
        out[0] += right ? -SHOULDER_X : SHOULDER_X;
        out[1] += SHOULDER_Y + p.shoulderDY;
        out[2] += p.shoulderDZ;
        return out;
    }

    /** Mund (Modell-px) aus der Pose: vor dem Gesicht, mit Kopfdrehung. */
    public static double[] mouthPoint(Pose p, double[] out) {
        CampfireMotion.rotZYX(0.0, -2.0, -4.2, p.headPitch, p.headYaw, p.headRoll, out);
        out[1] += p.neckDY;
        out[2] += p.neckDZ;
        return out;
    }

    // ------------------------------------------------------------------
    // Körper -> Rahmen (Tests, Effekte)
    // ------------------------------------------------------------------

    /** Drehpunkt (Modell-px) eines Körperteils in der Pose (Vorbeugen versetzt Rumpf, Kopf und Arme). */
    public static double[] partPivot(Pose p, HammockMotion.Part part, double[] out) {
        out[0] = part.px;
        out[1] = part.py;
        out[2] = part.pz;
        switch (part) {
            case HEAD, BODY -> {
                out[1] += p.neckDY;
                out[2] += p.neckDZ;
            }
            case RIGHT_ARM, LEFT_ARM -> {
                out[1] += p.shoulderDY;
                out[2] += p.shoulderDZ;
            }
            default -> {}
        }
        return out;
    }

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

    /** Modellpunkt (px) in den Rahmen (Blöcke). */
    public static double[] modelToFrame(Pose p, double mx, double my, double mz, double[] out) {
        HammockMotion.modelToBody(mx, my, mz, out);
        out[1] -= p.drop;
        return out;
    }

    public static double[] partToFrame(Pose p, HammockMotion.Part part, double lx, double ly, double lz, double[] out) {
        float[] a = partAngles(p, part);
        double[] m = new double[3];
        CampfireMotion.rotZYX(lx, ly, lz, a[0], a[1], a[2], m);
        double[] pv = partPivot(p, part, new double[3]);
        return modelToFrame(p, m[0] + pv[0], m[1] + pv[1], m[2] + pv[2], out);
    }

    // ------------------------------------------------------------------
    // Kamera: Drehpunkt über dem sitzenden Spieler
    // ------------------------------------------------------------------

    public static double[] cameraPivot() {
        return new double[]{0.0, 0.95, 0.0};
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

    public static float easeOutCubic(float x) {
        x = clamp01(x);
        float y = 1.0F - x;
        return 1.0F - y * y * y;
    }
}

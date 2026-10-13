package com.bame.client.emotes;

/**
 * Zeitleiste, Pose und Marshmallow-Zustand des Lagerfeuer-Emotes (ohne Minecraft-Klassen, im plannerTest geprüft).
 * <p>
 * Ablauf: ein Durchgang dauert 14 s. Der erste beginnt mit dem Hinsetzen (0–0,6 s), jeder weitere direkt beim Rösten
 * (Durchgangszeit 0,6). Rösten bis 8 s (Variante A, essen) bzw. 6,5 s (Variante B, verbrennt); danach Essen bzw.
 * Auspusten/Wegwerfen, ab 11,3 s bei beiden gleich: neuen Marshmallow aufstecken, zurück in die Röst-Pose. Die Pose am
 * Ende eines Durchgangs ist genau die Pose am Anfang des nächsten, daher gibt es an der Schleifennaht keinen Sprung.
 * <p>
 * Einheiten: Modell-Pixel wie {@code PlayerEntityModel} (+Y nach unten, -Z nach vorne, -X zur rechten Hand), Ursprung
 * 24.016 px über den Füßen. Winkel im Bogenmaß (außer Felder mit {@code Deg}).
 */
public final class CampfireMotion {

    private CampfireMotion() {}

    // ------------------------------------------------------------------
    // Zeitleiste
    // ------------------------------------------------------------------

    /** Hinsetzen, Stamm und Feuer erscheinen (nur im ersten Durchgang, s). */
    public static final float INTRO = 0.6F;
    /** Props wachsen in dieser Zeit auf volle Größe (s). */
    public static final float PROP_GROW = 0.45F;
    /** Ende eines Durchgangs (Durchgangszeit, s). */
    public static final float PASS_END = 14.0F;
    /** Länge der Folgedurchgänge (ohne Hinsetzen, s). */
    public static final float PASS = PASS_END - INTRO;

    public static final float ROAST_END_A = 8.0F;
    public static final float ROAST_END_B = 6.5F;

    // Variante A (essen)
    public static final float A_BITE_1 = 9.85F;
    public static final float A_BITE_2 = 10.35F;
    public static final float A_HEARTS = 10.5F;
    // Variante B (verbrennt)
    public static final float B_IGNITE = ROAST_END_B;
    public static final float[] B_BLOWS = {7.25F, 7.6F, 7.95F};
    public static final float B_FLAME_OUT = 7.95F;
    public static final float B_RELEASE = 9.75F;
    public static final float B_SPARKS = 9.95F;
    // beide
    public static final float ATTACH = 12.5F;
    public static final float MALLOW_GROW = 0.15F;
    /** Bissen werden in dieser Zeit kleiner (s). */
    private static final float BITE_SHRINK = 0.08F;

    /** Röst-Farbe: so lange dauert es von weiß bis goldbraun (s). */
    private static final float TOAST_TIME = ROAST_END_A - INTRO;
    /** Verbrennen: von der aktuellen Farbe bis schwarz (s). */
    private static final float BURN_TIME = 1.8F;

    /** Durchgangsnummer (0 = erster Durchgang mit Hinsetzen). */
    public static int pass(float t) {
        if (t < PASS_END) return 0;
        return 1 + (int) ((t - PASS_END) / PASS);
    }

    /** Zeit innerhalb des Durchgangs: [0, 14) im ersten, [0,6, 14) in allen weiteren. */
    public static float passTime(float t) {
        if (t < 0.0F) return 0.0F;
        if (t < PASS_END) return t;
        float r = (t - PASS_END) % PASS;
        return INTRO + r;
    }

    /**
     * Liegt der Zeitpunkt {@code e} (Durchgangszeit) im Durchgang {@code pass} im Intervall (t0, t1] der Emote-Zeit?
     * Zeitpunkte vor 0,6 s gibt es nur im ersten Durchgang (Hinsetzen).
     */
    public static boolean crossed(float t0, float t1, int pass, float e) {
        if (pass > 0 && e < INTRO) return false;
        float abs = pass == 0 ? e : PASS_END + (pass - 1) * PASS + (e - INTRO);
        return abs > t0 && abs <= t1;
    }

    // ------------------------------------------------------------------
    // Geometrie (Modell-Pixel)
    // ------------------------------------------------------------------

    public static final double BLOCKS_PER_PX = 0.9375 / 16.0;
    public static final double MODEL_ORIGIN_UP_PX = 24.016;

    /** Sitzversatz (wie WHEELCHAIR): tiefer und ein Stück nach hinten auf den Stamm. */
    public static final float SEAT_DY = 1.2F;
    public static final float SEAT_DZ = 1.5F;
    /** Oberschenkel fast waagerecht, Unterschenkel hängen leicht nach unten. */
    public static final float LEG_PITCH_DEG = -78.0F;
    public static final float LEG_SPLAY_DEG = 8.0F;

    /**
     * Sitzstamm (Weltmaße in Blöcken, Modell {@code emotes/campfire/seat_log.geo.json}): Länge quer zum Spieler, Tiefe
     * (ohne Aststummel), Höhe der Oberseite = Sitzfläche; Mitte so weit hinter den Füßen (px). Im campfireTest gegen das
     * Modell geprüft.
     */
    public static final float LOG_LENGTH = 0.9F, LOG_DEPTH = 0.575F, LOG_HEIGHT = 0.52F;
    public static final float LOG_BACK_PX = 2.0F;

    /**
     * Lagerfeuer-Modell ({@code campfire.geo.json} + {@code campfire_flames.geo.json}, Blöcke, normale Größe):
     * größter Radius (Steinring), Oberkante der Flammen-Ebenen und höchster sichtbarer Flammen-Texel (Kern oder
     * translucent Rand, aus dem Textur-Generator: 0,667, aufgerundet). Der Marshmallow ({@link #ROAST_HEIGHT}) hängt
     * über den Flammenspitzen.
     */
    public static final double FIRE_RADIUS = 0.53, FLAME_PLANE_TOP = 13.0 / 16.0, FLAME_VISIBLE_TOP = 0.68;
    /** Anzahl Flammen-Ebenen; jede ist ein eigener Knochen {@code f0..f7} mit eigenem Frame-Versatz. */
    public static final int FLAME_PLANES = 8;

    /** Marshmallow über dem Feuer: so hoch über dem Boden des Feuers (Blöcke). */
    public static final double ROAST_HEIGHT = 0.85;
    /** ... und so weit von der Feuermitte zum Spieler hin (Blöcke). */
    public static final double ROAST_PULL = 0.18;

    /** Stock: Griff bis Marshmallow-Mitte (px), Länge des Stocks, kleinster Abstand Griff–Marshmallow. */
    public static final float STICK_REACH_PX = 12.5F;
    public static final float STICK_LENGTH_PX = 14.0F;
    public static final float MIN_REACH_PX = 6.0F;
    /** Weiter entfernte Feuer (Runde): Stock höchstens so weit gestreckt. */
    public static final float MAX_STRETCH = 2.0F;
    /** Bei 2,2 Blöcken Abstand: Arm so viel weiter nach vorne (Grad) und Oberkörper so weit vorgelehnt. */
    public static final float FAR_ARM_PITCH_DEG = -22.0F, FAR_LEAN_DEG = 14.0F;

    /** Griffpunkt in der Hand (im Arm-Lokalsystem, breite bzw. schmale Arme). */
    public static final float GRIP_X_WIDE = -1.0F, GRIP_X_SLIM = -0.5F, GRIP_Y = 9.5F, GRIP_Z = 0.0F;
    /** Schulter-Drehpunkte (Standard, ohne Versatz). */
    public static final float SHOULDER_X = 5.0F, SHOULDER_Y_WIDE = 2.0F, SHOULDER_Y_SLIM = 2.5F;
    /** Blickpunkt (Augen) relativ zum Kopf-Drehpunkt. */
    public static final float EYE_DY = -3.5F;

    // Punkte relativ zum sitzenden Körper (px)
    private static final float[] P_BLOW_A = {-1.5F, -1.0F, -10.5F};
    private static final float[] P_MOUTH = {0.0F, -0.6F, -5.0F};
    private static final float[] P_MOUTH_AWAY = {-0.5F, 0.5F, -8.5F};
    private static final float[] P_ATTACH = {1.0F, 4.0F, -10.0F};
    private static final float[] P_FLINCH = {-2.5F, -3.5F, -14.5F};
    private static final float[] P_BLOW_B = {-1.0F, -1.5F, -10.0F};
    private static final float[] P_INSPECT = {-1.0F, -1.0F, -11.0F};
    private static final float[] P_WINDUP = {-2.0F, -5.0F, -8.0F};

    // ------------------------------------------------------------------
    // Pose
    // ------------------------------------------------------------------

    /** Ergebnis von {@link #sample}; wiederverwendbar (keine Allokation pro Frame). */
    public static final class Pose {
        public int pass;
        public float passTime;
        public boolean burns;

        /** Hinsetzen 0..1 und Größe von Stamm/Feuer/Stock 0..1 (nur im ersten Durchgang < 1). */
        public float sit, propScale;

        public float rArmPitch, rArmYaw, rArmRoll;
        public float lArmPitch, lArmYaw, lArmRoll;
        /** Linke Hand greift zum Marshmallow (0..1). */
        public float leftAim;
        /** Oberkörper vor-/zurücklehnen (Drehung um die Hüfte), Schulterzucken (px nach oben). */
        public float lean, shrugPx;
        public float headPitchAdd, headYawAdd, headRollAdd;
        /** Stockspitze: 1 = über dem Feuer, 0 = beim Punkt {@link #target} (relativ zum Körper). */
        public float fireWeight;
        public final float[] target = new float[3];
        /** Kleine Drehung des Stocks um die eigene Achse beim Rösten (Grad). */
        public float stickTwistDeg;

        /** Marshmallow: geröstet 0..1, verbrannt 0..1, Größe 0..1, Flammen 0..1. */
        public float toast, burnt, mallowScale, burning;

        // Versatz der Körperteile (px), aus Sitzen, Lehnen und Schulterzucken
        public float bodyDY, bodyDZ, legDY, legDZ, armDY;
        public float legPitch, legYaw;
    }

    // Kanäle der Schlüsselposen
    private static final int RP = 0, RY = 1, RR = 2, LP = 3, LY = 4, LR = 5, LEAN = 6, SHRUG = 7, HP = 8, HY = 9, HR = 10,
            FIRE = 11, TX = 12, TY = 13, TZ = 14, LAIM = 15, N = 16;

    private static final float[] ROAST = new float[N];
    private static final float[][] KEYS_A;
    private static final float[][] KEYS_B;

    static {
        ROAST[RP] = -55.0F; ROAST[RY] = -10.0F; ROAST[RR] = 0.0F;
        ROAST[LP] = -42.0F; ROAST[LY] = 12.0F; ROAST[LR] = -4.0F;
        ROAST[LEAN] = 5.0F; ROAST[FIRE] = 1.0F;
        setTarget(ROAST, P_BLOW_A);

        float[] pull = with(ROAST, RP, -38.0F, RY, -28.0F, LEAN, 2.0F, FIRE, 0.0F, HP, 4.0F);
        float[] blow = with(pull, HP, 12.0F);
        float[] mouth = target(with(pull, RP, -42.0F, RY, -32.0F, HP, -4.0F), P_MOUTH);
        float[] attach = target(with(ROAST, RP, -30.0F, RY, -18.0F, LEAN, 3.0F, FIRE, 0.0F), P_ATTACH);
        float[] attachReach = with(attach, LAIM, 1.0F, HP, 4.0F);
        float[] roastEnd = ROAST.clone();

        KEYS_A = new float[][]{
                k(INTRO, ROAST),
                k(ROAST_END_A, ROAST),
                k(8.7F, pull),
                k(8.9F, blow),
                k(9.15F, pull),
                k(9.7F, mouth),
                k(A_BITE_1, with(mouth, HP, -7.0F)),
                k(10.05F, with(mouth, HP, 5.0F)),
                k(10.25F, with(mouth, HP, -2.0F)),
                k(A_BITE_2, with(mouth, HP, -7.0F)),
                k(10.55F, target(with(mouth, HP, 5.0F), P_MOUTH_AWAY)),
                k(10.8F, target(with(mouth, HP, -1.0F), P_MOUTH_AWAY)),
                k(11.3F, attach),
                k(11.9F, attachReach),
                k(ATTACH + 0.2F, attachReach),
                k(13.0F, attach),
                k(PASS_END, roastEnd),
        };

        float[] flinch = target(with(ROAST, RP, -48.0F, RY, -14.0F, FIRE, 0.0F, LEAN, -8.0F, HP, -14.0F,
                LP, -30.0F, LY, 20.0F, LR, -10.0F), P_FLINCH);
        float[] blowB = target(with(ROAST, RP, -40.0F, RY, -26.0F, FIRE, 0.0F, LEAN, 2.0F), P_BLOW_B);
        float[] puff = with(blowB, HP, 12.0F, LEAN, 4.0F);
        float[] inspect = target(with(blowB, HP, 2.0F), P_INSPECT);
        float[] shrug = with(inspect, SHRUG, 1.8F, LP, -25.0F, LY, -12.0F, LR, -28.0F, RR, 8.0F, HR, 10.0F, HP, -3.0F);
        float[] windup = target(with(inspect, RP, -62.0F, RY, -20.0F, LEAN, -2.0F, HP, 0.0F), P_WINDUP);
        float[] thrown = with(ROAST, RP, -64.0F, RY, -10.0F, LEAN, 9.0F, FIRE, 1.0F, HP, 4.0F);
        setTarget(thrown, P_ATTACH);

        KEYS_B = new float[][]{
                k(INTRO, ROAST),
                k(ROAST_END_B, ROAST),
                k(6.75F, flinch),
                k(6.95F, with(flinch, HP, -10.0F)),
                k(7.1F, blowB),
                k(B_BLOWS[0], puff),
                k(7.4F, blowB),
                k(B_BLOWS[1], puff),
                k(7.75F, blowB),
                k(B_BLOWS[2], puff),
                k(8.1F, blowB),
                k(8.5F, inspect),
                k(8.8F, shrug),
                k(9.2F, inspect),
                k(9.45F, windup),
                k(B_RELEASE, thrown),
                k(10.1F, thrown),
                k(10.7F, with(attach, LAIM, 0.0F)),
                k(11.3F, attach),
                k(11.9F, attachReach),
                k(ATTACH + 0.2F, attachReach),
                k(13.0F, attach),
                k(PASS_END, roastEnd),
        };
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

    private static float[] target(float[] base, float[] p) {
        float[] out = base.clone();
        setTarget(out, p);
        return out;
    }

    private static void setTarget(float[] v, float[] p) {
        v[TX] = p[0];
        v[TY] = p[1];
        v[TZ] = p[2];
    }

    private static final float DEG = (float) (Math.PI / 180.0);

    /**
     * Pose zur Emote-Zeit {@code t}. {@code burns}: Variante dieses Durchgangs (siehe
     * {@link CampfireLogic#burns(String, long, int)}).
     */
    public static void sample(float t, boolean burns, Pose out) {
        sample(t, burns, 0.0F, out);
    }

    /**
     * Wie {@link #sample(float, boolean, Pose)} für ein weiter entferntes Feuer (Runde): {@code far} 0..1 (siehe
     * {@link #farness}) streckt den Arm und lehnt den Oberkörper vor, solange der Stock zum Feuer zeigt.
     */
    public static void sample(float t, boolean burns, float far, Pose out) {
        if (t < 0.0F) t = 0.0F;
        int pass = pass(t);
        float u = passTime(t);
        out.pass = pass;
        out.passTime = u;
        out.burns = burns;

        boolean first = pass == 0;
        out.sit = first ? easeInOut(u / INTRO) : 1.0F;
        out.propScale = first ? easeOutBack(clamp01(u / PROP_GROW)) : 1.0F;

        float[][] keys = burns ? KEYS_B : KEYS_A;
        float r0 = 0, r1 = 0, r2 = 0, l0 = 0, l1 = 0, l2 = 0, lean = 0, shrug = 0, hp = 0, hy = 0, hr = 0, fire = 1;
        float tx = 0, ty = 0, tz = 0, laim = 0;
        // Schlüsselposen: weich (smoothstep) zwischen zwei benachbarten Posen; vor der ersten gilt die erste
        float[] a = keys[0], b = keys[0];
        float f = 0.0F;
        if (u > keys[0][0]) {
            int i = 0;
            while (i < keys.length - 2 && u >= keys[i + 1][0]) i++;
            a = keys[i];
            b = keys[i + 1];
            f = easeInOut((u - a[0]) / (b[0] - a[0]));
        }
        r0 = mix(a, b, RP, f); r1 = mix(a, b, RY, f); r2 = mix(a, b, RR, f);
        l0 = mix(a, b, LP, f); l1 = mix(a, b, LY, f); l2 = mix(a, b, LR, f);
        lean = mix(a, b, LEAN, f); shrug = mix(a, b, SHRUG, f);
        hp = mix(a, b, HP, f); hy = mix(a, b, HY, f); hr = mix(a, b, HR, f);
        fire = mix(a, b, FIRE, f);
        tx = mix(a, b, TX, f); ty = mix(a, b, TY, f); tz = mix(a, b, TZ, f);
        laim = mix(a, b, LAIM, f);

        // Weiter entferntes Feuer: Arm strecken und vorlehnen (nur solange der Stock zum Feuer zeigt)
        float reach = clamp01(far) * fire;
        r0 += FAR_ARM_PITCH_DEG * reach;
        lean += FAR_LEAN_DEG * reach;

        // Rösten: Stock langsam hin und her drehen (Hüllkurve blendet vor dem Ende des Röstens aus)
        float roastEnd = burns ? ROAST_END_B : ROAST_END_A;
        float env = u < INTRO ? 0.0F : 1.0F - easeInOut((u - (roastEnd - 0.4F)) / 0.4F);
        float osc = (float) Math.sin((u - INTRO) * (Math.PI * 2.0 / 2.6));
        out.stickTwistDeg = env * 14.0F * osc;
        r2 += env * 3.0F * osc;

        // Atmen (über die ganze Emote-Zeit, daher ohne Naht)
        float breath = (float) Math.sin(t * 2.2) * 0.6F;
        lean += breath;
        hp -= breath * 0.5F;

        // Hinsetzen: aus dem Stand in die Röst-Pose
        float s = out.sit;
        out.rArmPitch = r0 * s * DEG;
        out.rArmYaw = r1 * s * DEG;
        out.rArmRoll = r2 * s * DEG;
        out.lArmPitch = l0 * s * DEG;
        out.lArmYaw = l1 * s * DEG;
        out.lArmRoll = l2 * s * DEG;
        out.lean = lean * s * DEG;
        out.shrugPx = shrug;
        out.headPitchAdd = hp * DEG;
        out.headYawAdd = hy * DEG;
        out.headRollAdd = hr * DEG;
        out.fireWeight = fire;
        out.target[0] = tx;
        out.target[1] = ty;
        out.target[2] = tz;
        out.leftAim = laim;

        // Versatz: Sitzen (Hüfte auf den Stamm), Lehnen um die Hüfte (12 px unter dem Hals), Schulterzucken
        double lr = out.lean;
        float hipToNeck = 12.0F;
        out.bodyDY = SEAT_DY * s + (float) (hipToNeck * (1.0 - Math.cos(lr)));
        out.bodyDZ = SEAT_DZ * s - (float) (hipToNeck * Math.sin(lr));
        out.armDY = out.bodyDY - shrug;
        out.legDY = SEAT_DY * s;
        out.legDZ = SEAT_DZ * s;
        out.legPitch = LEG_PITCH_DEG * s * DEG;
        out.legYaw = LEG_SPLAY_DEG * s * DEG;

        mallow(u, burns, out);
        if (first) out.mallowScale *= out.propScale;
    }

    /** Marshmallow-Zustand innerhalb des Durchgangs. */
    private static void mallow(float u, boolean burns, Pose out) {
        boolean fresh = u >= ATTACH;
        float roastEnd = burns ? ROAST_END_B : ROAST_END_A;
        out.toast = fresh ? 0.0F : clamp01((Math.min(u, roastEnd) - INTRO) / TOAST_TIME);
        out.burnt = (!fresh && burns && u >= B_IGNITE) ? easeInOut((u - B_IGNITE) / BURN_TIME) : 0.0F;
        if (burns && u >= B_IGNITE && u < B_FLAME_OUT) {
            out.burning = Math.min(clamp01((u - B_IGNITE) / 0.12F), clamp01((B_FLAME_OUT - u) / 0.1F));
        } else {
            out.burning = 0.0F;
        }
        float scale;
        if (fresh) {
            scale = easeOutBack(clamp01((u - ATTACH) / MALLOW_GROW));
        } else if (!burns) {
            if (u < A_BITE_1) scale = 1.0F;
            else if (u < A_BITE_2) scale = 1.0F - 0.45F * clamp01((u - A_BITE_1) / BITE_SHRINK);
            else scale = 0.55F * (1.0F - clamp01((u - A_BITE_2) / BITE_SHRINK));
        } else {
            scale = u < B_RELEASE ? 1.0F : 1.0F - clamp01((u - B_RELEASE) / 0.05F);
        }
        out.mallowScale = scale;
    }

    // ------------------------------------------------------------------
    // Farbe
    // ------------------------------------------------------------------

    private static final int[] WHITE = {255, 252, 244};
    private static final int[] GOLD = {246, 220, 152};
    private static final int[] BROWN = {192, 122, 54};
    private static final int[] BLACK = {38, 32, 30};

    /** Marshmallow-Farbe (ARGB): weiß → hellgold → goldbraun, beim Verbrennen nach schwarz. */
    public static int mallowColor(float toast, float burnt) {
        float r, g, b;
        toast = clamp01(toast);
        if (toast < 0.5F) {
            float f = toast / 0.5F;
            r = lerp(WHITE[0], GOLD[0], f); g = lerp(WHITE[1], GOLD[1], f); b = lerp(WHITE[2], GOLD[2], f);
        } else {
            float f = (toast - 0.5F) / 0.5F;
            r = lerp(GOLD[0], BROWN[0], f); g = lerp(GOLD[1], BROWN[1], f); b = lerp(GOLD[2], BROWN[2], f);
        }
        float k = clamp01(burnt);
        r = lerp(r, BLACK[0], k); g = lerp(g, BLACK[1], k); b = lerp(b, BLACK[2], k);
        return 0xFF000000 | (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b);
    }

    // ------------------------------------------------------------------
    // Stock: Ausrichtung zum Ziel (Vorwärtskinematik des rechten Arms)
    // ------------------------------------------------------------------

    /** Ergebnis von {@link #aimStick}; wiederverwendbar. */
    public static final class Stick {
        /** Drehung des Stocks im Arm-Lokalsystem (nach dem Griffpunkt): erst Yaw um Y, dann Pitch um X. */
        public float yaw, pitch;
        /** Streckung des Stocks (≥ 1) und Verschiebung entlang der Achse (≤ 0, der Griff rutscht nach vorne). */
        public float stretch = 1.0F, slide;
        /** Griffpunkt und Marshmallow-Mitte im Modellraum (px). */
        public final double[] grip = new double[3];
        public final double[] mallow = new double[3];
    }

    /**
     * Richtet den Stock so aus, dass der Marshmallow auf {@code target} (Modell-px) liegt: Ist das Ziel näher als die
     * normale Reichweite, rutscht der Stock durch die Hand (das Ende steht hinten über); ist es weiter weg (Runde mit
     * entferntem Feuer), wird der Stock bis {@link #MAX_STRETCH} gestreckt.
     *
     * @param ox/oy/oz Arm-Drehpunkt, pitch/yaw/roll Armwinkel wie {@code ModelPart} (Reihenfolge Z·Y·X)
     */
    public static void aimStick(float ox, float oy, float oz, float pitch, float yaw, float roll, float gripX,
                                double tx, double ty, double tz, Stick out) {
        // Griff im Modellraum
        rotZYX(gripX, GRIP_Y, GRIP_Z, pitch, yaw, roll, out.grip);
        out.grip[0] += ox;
        out.grip[1] += oy;
        out.grip[2] += oz;
        // Ziel ins Arm-Lokalsystem (inverse Drehung) relativ zum Griff
        double dx = tx - ox, dy = ty - oy, dz = tz - oz;
        double[] l = SCRATCH.get();
        rotXYZInverse(dx, dy, dz, pitch, yaw, roll, l);
        l[0] -= gripX;
        l[1] -= GRIP_Y;
        l[2] -= GRIP_Z;
        double len = Math.sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]);
        double vx, vy, vz;
        if (len < 1.0E-6) {
            vx = 0.0; vy = 0.0; vz = -1.0; len = 0.0;
        } else {
            vx = l[0] / len; vy = l[1] / len; vz = l[2] / len;
        }
        out.pitch = (float) Math.asin(Math.max(-1.0, Math.min(1.0, vy)));
        out.yaw = (float) Math.atan2(-vx, -vz);
        double reach;
        if (len >= STICK_REACH_PX) {
            out.stretch = (float) Math.min(len / STICK_REACH_PX, MAX_STRETCH);
            out.slide = 0.0F;
            reach = STICK_REACH_PX * out.stretch;
        } else {
            out.stretch = 1.0F;
            reach = Math.max(len, MIN_REACH_PX);
            out.slide = (float) (reach - STICK_REACH_PX);
        }
        // Marshmallow-Mitte: Griff + Richtung * Reichweite (lokal), zurück in den Modellraum
        double mx = gripX + vx * reach, my = GRIP_Y + vy * reach, mz = GRIP_Z + vz * reach;
        rotZYX(mx, my, mz, pitch, yaw, roll, out.mallow);
        out.mallow[0] += ox;
        out.mallow[1] += oy;
        out.mallow[2] += oz;
    }

    private static final ThreadLocal<double[]> SCRATCH = ThreadLocal.withInitial(() -> new double[3]);

    /** v' = Rz(roll)·Ry(yaw)·Rx(pitch)·v (wie {@code ModelPart.applyTransform}). */
    public static void rotZYX(double x, double y, double z, float pitch, float yaw, float roll, double[] out) {
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double y1 = y * cp - z * sp, z1 = y * sp + z * cp;
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
        double cr = Math.cos(roll), sr = Math.sin(roll);
        out[0] = x2 * cr - y1 * sr;
        out[1] = x2 * sr + y1 * cr;
        out[2] = z2;
    }

    /** Inverse von {@link #rotZYX}: v = Rx(-pitch)·Ry(-yaw)·Rz(-roll)·v'. */
    public static void rotXYZInverse(double x, double y, double z, float pitch, float yaw, float roll, double[] out) {
        double cr = Math.cos(roll), sr = Math.sin(roll);
        double x1 = x * cr + y * sr, y1 = -x * sr + y * cr;
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double x2 = x1 * cy - z * sy, z2 = x1 * sy + z * cy;
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        out[0] = x2;
        out[1] = y1 * cp + z2 * sp;
        out[2] = -y1 * sp + z2 * cp;
    }

    // ------------------------------------------------------------------
    // Feuer und Ziele im Modellraum
    // ------------------------------------------------------------------

    /**
     * Ziel der Stockspitze (Modell-px): zwischen dem Punkt über dem Feuer und dem Körper-Punkt der Pose gemischt.
     *
     * @param fire Punkt über dem Feuer im Modellraum (px)
     */
    public static void stickTarget(Pose p, double[] fire, double[] out) {
        float w = p.fireWeight;
        // Körper-Punkte gelten für den sitzenden, aufrechten Körper; Lehnen (und Aufstehen) verschiebt sie mit
        double bx = p.target[0], by = p.target[1] + p.bodyDY - SEAT_DY, bz = p.target[2] + p.bodyDZ - SEAT_DZ;
        out[0] = bx + (fire[0] - bx) * w;
        out[1] = by + (fire[1] - by) * w;
        out[2] = bz + (fire[2] - bz) * w;
    }

    /** Punkt über dem Feuer im Modellraum, wenn das Feuer 1,3 Blöcke geradeaus steht (Vorschau, Rückfall). */
    public static void defaultFireTarget(double[] out) {
        out[0] = 0.0;
        out[1] = MODEL_ORIGIN_UP_PX - ROAST_HEIGHT / BLOCKS_PER_PX;
        out[2] = -CampfireLogic.FIRE_DISTANCE / BLOCKS_PER_PX;
        pullTowardPlayer(out);
    }

    /**
     * Röstpunkt etwas zur dem Spieler zugewandten Seite der Flammen ziehen (waagerecht, {@link #ROAST_PULL} Blöcke): So
     * reicht der 14-px-Stock beim Gastgeber ohne Streckung, und der Marshmallow hängt trotzdem über den Flammen.
     */
    public static void pullTowardPlayer(double[] target) {
        double h = Math.hypot(target[0], target[2]);
        double pull = ROAST_PULL / BLOCKS_PER_PX;
        if (h <= pull * 2.0) return;
        double f = (h - pull) / h;
        target[0] *= f;
        target[2] *= f;
    }

    /** Armstreckung für weiter entfernte Feuer: 0 bei 1,3 Blöcken, 1 ab 2,2 Blöcken. */
    public static float farness(double fireDistanceBlocks) {
        return clamp01((float) ((fireDistanceBlocks - CampfireLogic.FIRE_DISTANCE)
                / (CampfireLogic.MAX_REACH - CampfireLogic.FIRE_DISTANCE)));
    }

    /**
     * Linker Arm: aus der Pose, beim Aufstecken zum Marshmallow gedreht ({@code leftAim}). Schreibt {pitch, yaw}.
     */
    public static void leftArm(Pose p, float shoulderX, float shoulderY, float shoulderZ, double[] mallow, float[] out) {
        out[0] = p.lArmPitch;
        out[1] = p.lArmYaw;
        if (p.leftAim <= 0.0F) return;
        // Arm zeigt ungedreht entlang +Y; R = Ry(yaw)·Rx(pitch): (0,1,0) -> (sin p · sin y, cos p, sin p · cos y)
        double vx = mallow[0] - shoulderX, vy = mallow[1] - shoulderY, vz = mallow[2] - shoulderZ;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1.0E-6) return;
        double pitch = -Math.acos(Math.max(-1.0, Math.min(1.0, vy / len)));
        double sp = Math.sin(pitch);
        double yaw = Math.abs(sp) < 1.0E-6 ? 0.0 : Math.atan2(vx / len / sp, vz / len / sp);
        out[0] = lerp(p.lArmPitch, (float) pitch, p.leftAim);
        out[1] = lerp(p.lArmYaw, (float) yaw, p.leftAim);
    }

    /**
     * Kopf zum Marshmallow: {pitch, yaw} (Bogenmaß, begrenzt), vom Augenpunkt über dem Kopf-Drehpunkt aus.
     */
    public static void lookAt(float headX, float headY, float headZ, double[] point, float[] out) {
        double dx = point[0] - headX, dy = point[1] - (headY + EYE_DY), dz = point[2] - headZ;
        double h = Math.hypot(dx, dz);
        float yaw = (float) Math.atan2(-dx, -dz);
        float pitch = (float) Math.atan2(dy, Math.max(h, 1.0E-6));
        out[0] = clamp(pitch, -60.0F * DEG, 60.0F * DEG);
        out[1] = clamp(yaw, -70.0F * DEG, 70.0F * DEG);
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    private static float mix(float[] a, float[] b, int channel, float f) {
        float x = a[channel + 1];
        return x + (b[channel + 1] - x) * f;
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

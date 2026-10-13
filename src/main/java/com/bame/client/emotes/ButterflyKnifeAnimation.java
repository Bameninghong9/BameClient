package com.bame.client.emotes;

/**
 * Butterfly-Messer-Routine (Balisong, angelehnt an die Valorant-/CS-Inspect-Animationen): gemeinsame Zeitleiste fuer
 * die Spielerpose ({@link EmoteAnimator}) und das Messer ({@link EmotePropFeatureRenderer}).
 * <p>
 * Ablauf pro Schleife ({@link #PERIOD} s):
 * <pre>
 * 0.00-0.35  zugeklappt halten
 * 0.35-0.80  Snap-Open: Bite-Griff faellt weg, Klinge + Bite-Griff schnappen mit Handgelenk-Flick auf (Anschlag + Rueckprall)
 * 1.05-1.85  Twirl: offenes Messer dreht sich 3x um die Pivot-Pins, Bite-Griff peitscht nach, Hand kreist mit
 * 2.20-3.15  Zen-Rollover: Messer rollt ueber den Handruecken (um die Unterarmachse) und wieder zurueck
 * 3.35-4.35  Chaplin/Helix: 2 Rollen um die Laengsachse, erst klappt der Bite-, dann der Safe-Griff auf und zu
 * 4.38-5.78  Aerial: ausholen, Wurf mit 2 Ueberschlaegen + 1 Seitwaertsdrehung, Hand kommt beim Fangen entgegen
 * 5.88-6.86  Helicopter: Messer flach kippen und 2x um die Hochachse ueber der Faust drehen, zurueckkippen
 * 6.95-8.15  Inspect: Klinge hochhalten, langsam beide Seiten zeigen, Glanzlicht laeuft zweimal ueber die Klinge
 * 8.20-8.62  Schliessen mit Schnapp-Anschlag + kleinem Rueckstoss im Arm, danach halten
 * </pre>
 * Alle Messerkanaele sind an den Segmentgrenzen stetig (Winkelspruenge nur um volle 360 Grad = identische Lage);
 * Arm/Kopf laufen ueber geschlossene Hermite-Splines plus Zusatzbewegungen mit Huellkurven, die an den Grenzen 0 sind.
 * <p>
 * Winkel in Grad, Laengen in Modell-Pixeln (1/16 Block). Bewusst ohne Minecraft-Klassen, damit der Ablauf
 * im plannerTest-Sourceset (ButterflyKnifeAnimationTest) numerisch geprueft werden kann.
 */
public final class ButterflyKnifeAnimation {

    private ButterflyKnifeAnimation() {}

    public static final float PERIOD = 9.0F;
    /** Einblenden aus der Ruhepose beim Start des Emotes. */
    public static final float BLEND_IN = 0.35F;

    // ---------- Segmente (Sekunden innerhalb einer Schleife) ----------
    static final float OPEN_A = 0.35F, OPEN_B = 0.80F;
    static final float TW_A = 1.05F, TW_B = 1.85F;
    static final float RO_A = 2.20F, RO_M = 2.62F, RO_N = 2.72F, RO_B = 3.15F;
    static final float CH_A = 3.35F, CH_B = 4.35F;
    static final float WIND_A = 4.38F, WIND_B = 4.66F, RELEASE = 4.62F, CATCH = 5.45F, ABS_A = 5.42F, ABS_B = 5.78F;
    static final float FLIP_RAMP = 0.08F;
    static final float HT_A = 5.88F, HT_B = 6.08F, HS_A = 6.02F, HS_B = 6.68F, HR_A = 6.64F, HR_B = 6.86F;
    static final float IN_A = 6.95F, IN_B = 8.15F;
    static final float G1_A = 7.04F, G1_B = 7.48F, G2_A = 7.58F, G2_B = 8.02F;
    static final float CLOSE_A = 8.20F, CLOSE_B = 8.62F;

    /** Alle Segmentgrenzen (fuer den Stetigkeitstest). */
    public static final float[] BOUNDARIES = {
            OPEN_A, OPEN_B, TW_A, TW_B, RO_A, RO_M, RO_N, RO_B, CH_A, CH_B, WIND_A, WIND_B, RELEASE - FLIP_RAMP,
            RELEASE, CATCH, CATCH + FLIP_RAMP, ABS_A, ABS_B, HT_A, HT_B, HS_A, HS_B, HR_A, HR_B, IN_A, IN_B,
            G1_A, G1_B, G2_A, G2_B, CLOSE_A, CLOSE_B, snapImpact(OPEN_A, OPEN_B), snapImpact(CLOSE_A, CLOSE_B), 0.0F
    };

    // ---------- Messer-Konstanten ----------
    /** Grundrolle um die Laengsachse: Klingenflaeche schraeg zur Kamera. */
    public static final float BASE_ROLL = 60.0F;
    /** Neigung des Messers nach aussen (Grad um die Hand-Y-Achse). */
    public static final float KNIFE_LEAN = 18.0F;
    /** Gesamtskalierung des Messermodells (Geometrie: offen ca. 11.5 px lang). */
    public static final float KNIFE_SCALE = 0.85F;
    /** Modell-Einheiten pro Pixel (Geometrie in 1/8 px, damit die Textur feiner aufgeloest ist). */
    public static final float UNITS_PER_PIXEL = 8.0F;
    /** Lage des Pivots in der rechten Hand (Arm-Lokalkoordinaten in px, Hand-Unterseite bei Y = 10). */
    public static final float GRIP_X_WIDE = -1.0F, GRIP_X_SLIM = -0.5F, GRIP_Y = 10.3F, GRIP_Z = -2.4F;
    /** Abstand Pivot -> Unterarmachse (Faustmitte) entlang der Hand-Z-Achse, fuer den Rollover. */
    static final float ROLL_AXIS_Z = 2.4F;
    /** Schwerpunkt des offenen Messers entlang der Griffe (px), um den es sich in der Luft dreht. */
    static final float CENTER_Z = 1.2F;
    static final float TOSS_HEIGHT = 15.0F;
    static final float TOSS_FORWARD = 2.0F;

    // ---------- Rechter Arm (geschlossener Spline) ----------
    private static final float[] ARM_T = {
            0.00F, 0.30F, 0.46F, 0.62F, 0.82F, 1.05F, 1.45F, 1.85F, 2.05F, 2.40F, 2.67F, 2.93F, 3.20F, 3.35F, 3.85F,
            4.30F, 4.47F, 4.62F, 4.85F, 5.18F, 5.43F, 5.60F, 5.85F, 6.10F, 6.65F, 6.90F, 7.25F, 7.85F, 8.15F, 8.38F, 8.80F};
    private static final float[] ARM_PITCH = {
            -76F, -73F, -66F, -93F, -82F, -84F, -88F, -84F, -81F, -84F, -82F, -84F, -80F, -82F, -90F,
            -85F, -67F, -104F, -90F, -93F, -103F, -77F, -82F, -76F, -77F, -86F, -97F, -95F, -87F, -83F, -77F};
    private static final float[] ARM_YAW = {
            -9F, -9F, -8F, -10F, -11F, -11F, -10F, -11F, -12F, -12F, -11F, -11F, -11F, -10F, -12F,
            -10F, -9F, -10F, -10F, -11F, -11F, -10F, -12F, -14F, -14F, -12F, -15F, -16F, -12F, -10F, -9F};
    private static final float[] ARM_ROLL = {
            4F, 2F, 0F, 10F, 5F, 4F, 6F, 4F, 3F, 18F, 6F, -8F, 4F, 4F, 8F,
            4F, 2F, 8F, 4F, 4F, 6F, 2F, 3F, 2F, 2F, 4F, 3F, 6F, 4F, 2F, 4F};

    /** Ergebnis einer Abtastung. */
    public static final class Pose {
        // Spielermodell (Radiant, absolute Werte nach resetTransform)
        public float rightArmPitch, rightArmYaw, rightArmRoll;
        public float leftArmPitch, leftArmYaw, leftArmRoll;
        public float headPitch, headYaw, headRoll;
        public float bodyPitch, bodyYaw, bodyRoll;
        // Messer (Grad / px)
        public float bladeAngle;   // 180 = zugeklappt (Klinge zwischen den Griffen), 0 = offen
        public float biteAngle;    // 360 = zugeklappt, 0 = offen
        public float safeAngle;    // 0 = Safe-Griff in der Hand; != 0 nur beim Chaplin
        public float offX, offY, offZ;       // Zusatzversatz in der Hand (px, Hand-Koordinaten)
        public float rollover;     // Rolle um die Unterarmachse (Zen-Rollover)
        public float yawSpin;      // Drehung um die Hand-Hochachse (Helicopter)
        public float tilt;         // Kippen nach vorne/hinten (Handgelenk)
        public float knifeRoll;    // Rolle um die Messer-Laengsachse
        public float flipX, flipZ; // Ueberschlag / Seitwaertsdrehung um den Schwerpunkt (Wurf)
        public float twirl;        // ganzes Messer um die Pivot-Pins
        public float tossX, tossY, tossZ; // Wurfversatz in Modellkoordinaten (px, -Y = oben)
        public float glint = -1.0F;  // Position des Glanzlichts entlang der Klinge 0..1, < 0 = aus
        public float glintStrength;
        public float trail;        // Staerke der Bewegungsspur 0..1
    }

    /** Minimale Transformationsschnittstelle (MatrixStack im Renderer, eigene Matrix im Test). Laengen in px. */
    public interface Xform {
        void translate(float x, float y, float z);
        void rotateX(float deg);
        void rotateY(float deg);
        void rotateZ(float deg);
    }

    /**
     * Messer-Transformation nach {@code rightArm.applyTransform}: endet im Messer-Lokalsystem am Pivot
     * (X = Pin-Achse, -Z = Klingenrichtung offen, +Y = Schneide). Danach folgen KNIFE_SCALE und die Teilrotationen.
     */
    public static void applyKnifeTransform(Pose p, boolean slim, Xform xf) {
        xf.translate((slim ? GRIP_X_SLIM : GRIP_X_WIDE) + p.offX, GRIP_Y + p.offY, GRIP_Z + p.offZ);
        if (p.rollover != 0.0F) {
            xf.translate(0.0F, 0.0F, ROLL_AXIS_Z);
            xf.rotateY(p.rollover);
            xf.translate(0.0F, 0.0F, -ROLL_AXIS_Z);
        }
        if (p.yawSpin != 0.0F) xf.rotateZ(p.yawSpin);
        xf.rotateY(KNIFE_LEAN);
        if (p.tilt != 0.0F) xf.rotateX(p.tilt);
        xf.rotateZ(p.knifeRoll);
        if (p.flipX != 0.0F || p.flipZ != 0.0F) {
            float c = CENTER_Z * KNIFE_SCALE;
            xf.translate(0.0F, 0.0F, c);
            xf.rotateZ(p.flipZ);
            xf.rotateX(p.flipX);
            xf.translate(0.0F, 0.0F, -c);
        }
        if (p.twirl != 0.0F) xf.rotateX(p.twirl);
    }

    public static void sample(float t, Pose out) {
        if (t < 0.0F) t = 0.0F;
        float u = t % PERIOD;
        float blend = smoothstep(Math.min(t / BLEND_IN, 1.0F));

        // ---------- Auf-/Zuklappen ----------
        float blade, bite, safe = 0.0F;
        if (u < OPEN_A) {
            blade = 180.0F; bite = 360.0F;
        } else if (u < OPEN_B) {
            // 1) Bite-Griff faellt weg (360 -> 180), 2) Klinge + Bite-Griff schwingen zusammen auf (Anschlag + Rueckprall)
            float s = seg(u, OPEN_A, OPEN_B);
            float a = easeInOutCubic(clamp01(s / 0.45F));
            float b = snap(clamp01((s - 0.3F) / 0.7F));
            blade = 180.0F - 180.0F * b;
            bite = 360.0F - 180.0F * a - 180.0F * b;
        } else if (u < CLOSE_A) {
            blade = 0.0F; bite = 0.0F;
        } else if (u < CLOSE_B) {
            // 1) Bite-Griff legt sich um die Klinge (0 -> 180), 2) beide zusammen zurueck zum Safe-Griff
            float s = seg(u, CLOSE_A, CLOSE_B);
            float a = easeInOutCubic(clamp01(s / 0.45F));
            float b = snap(clamp01((s - 0.3F) / 0.7F));
            blade = 180.0F * b;
            bite = 180.0F * a + 180.0F * b;
        } else {
            blade = 180.0F; bite = 360.0F;
        }

        // ---------- Tricks ----------
        float twirl = 0.0F, rollover = 0.0F, yawSpin = 0.0F;
        float roll = BASE_ROLL, tilt = 0.0F, offZ = 0.0F;
        float flipX = 0.0F, flipZ = 0.0F, tossH = 0.0F, tossF = 0.0F, tossLook = 0.0F;
        float trail = 0.0F;

        if (in(u, OPEN_A, OPEN_B)) {
            float s = seg(u, OPEN_A, OPEN_B);
            roll += 20.0F * bump(s);
            tilt += -24.0F * showWave(s);     // Handgelenk-Flick
        }
        if (in(u, TW_A, TW_B)) {
            float s = seg(u, TW_A, TW_B);
            twirl = 1080.0F * smoother(s);
            bite += -40.0F * bump(s);         // Bite-Griff peitscht hinterher
            trail = Math.max(trail, bump(s));
        }
        if (in(u, RO_A, RO_M)) rollover = 360.0F * smoother(seg(u, RO_A, RO_M));
        if (in(u, RO_N, RO_B)) rollover = -360.0F * smoother(seg(u, RO_N, RO_B));
        if (in(u, CH_A, CH_B)) {
            float s = seg(u, CH_A, CH_B);
            roll += 720.0F * smoother(s);
            float w = (float) Math.sin(2.0 * Math.PI * s);
            if (s < 0.5F) bite += 150.0F * w * w; else safe -= 150.0F * w * w;
        }
        if (in(u, WIND_A, WIND_B)) tilt += -18.0F * bump(seg(u, WIND_A, WIND_B));
        if (in(u, RELEASE - FLIP_RAMP, CATCH + FLIP_RAMP)) {
            float w = flipProgress(u);
            flipX = 720.0F * w;
            flipZ = 360.0F * w;
        }
        if (in(u, RELEASE, CATCH)) {
            float f = seg(u, RELEASE, CATCH);
            tossH = 4.0F * TOSS_HEIGHT * f * (1.0F - f);
            tossF = TOSS_FORWARD * (float) Math.sin(Math.PI * f);
            tossLook = TOSS_HEIGHT * bump(f); // Blickziel ohne Geschwindigkeitssprung beim Fangen
            bite += 110.0F * bump(f);         // halb offen in der Luft
            trail = Math.max(trail, 0.55F * bump(f));
        }
        if (in(u, ABS_A, ABS_B)) tilt += 12.0F * bump(seg(u, ABS_A, ABS_B));
        float heli = 0.0F;
        if (in(u, HT_A, HR_B)) {
            heli = u < HT_B ? smoother(seg(u, HT_A, HT_B)) : (u < HR_A ? 1.0F : 1.0F - smoother(seg(u, HR_A, HR_B)));
            tilt += 90.0F * heli;
            roll += 30.0F * heli;
            offZ -= 1.5F * heli;
        }
        if (in(u, HS_A, HS_B)) {
            float s = seg(u, HS_A, HS_B);
            yawSpin = 720.0F * smoother(s);
            trail = Math.max(trail, bump(s));
        }
        float inspect = 0.0F, inspectWave = 0.0F;
        if (in(u, IN_A, IN_B)) {
            float s = seg(u, IN_A, IN_B);
            inspect = bump(s);
            inspectWave = showWave(s);
            roll += 75.0F * inspectWave;
            tilt += -12.0F * inspect;
        }
        float glint = -1.0F, glintStrength = 0.0F;
        if (in(u, G1_A, G1_B) || in(u, G2_A, G2_B)) {
            float s = in(u, G1_A, G1_B) ? seg(u, G1_A, G1_B) : seg(u, G2_A, G2_B);
            glint = smoothstep(s);
            glintStrength = (float) Math.sin(Math.PI * s);
        }
        if (in(u, CLOSE_A, CLOSE_B)) {
            float s = seg(u, CLOSE_A, CLOSE_B);
            roll += -18.0F * bump(s);
            tilt += 20.0F * showWave(s);
        }

        out.bladeAngle = blade;
        out.biteAngle = bite;
        out.safeAngle = safe;
        out.offX = 0.0F; out.offY = 0.0F; out.offZ = offZ;
        out.rollover = rollover;
        out.yawSpin = yawSpin;
        out.tilt = tilt;
        out.knifeRoll = roll;
        out.flipX = flipX;
        out.flipZ = flipZ;
        out.twirl = twirl;
        out.tossX = 0.0F;
        out.tossY = -tossH;
        out.tossZ = -tossF;
        out.glint = glint;
        out.glintStrength = glintStrength;
        out.trail = trail;

        // ---------- Rechter Arm ----------
        float armPitch = loopSpline(ARM_T, ARM_PITCH, u);
        float armYaw = loopSpline(ARM_T, ARM_YAW, u);
        float armRoll = loopSpline(ARM_T, ARM_ROLL, u);
        if (in(u, TW_A, TW_B)) {
            // Hand kreist im Takt des Twirls mit (wie beim echten Fan)
            float s = seg(u, TW_A, TW_B);
            double ph = Math.toRadians(twirl);
            armPitch += 4.0F * bump(s) * (float) Math.sin(ph);
            armYaw += 3.0F * bump(s) * (float) Math.cos(ph);
        }
        if (in(u, CH_A, CH_B)) armRoll += 7.0F * bump(seg(u, CH_A, CH_B)) * (float) Math.sin(Math.toRadians(roll - BASE_ROLL));
        if (in(u, HS_A, HS_B)) armYaw += 3.0F * bump(seg(u, HS_A, HS_B)) * (float) Math.sin(Math.toRadians(yawSpin));
        // Rueckstoss beim Anschlag (Auf- und Zuklappen)
        armPitch -= 4.0F * recoil(u - snapImpact(OPEN_A, OPEN_B));
        armPitch -= 7.0F * recoil(u - snapImpact(CLOSE_A, CLOSE_B));
        armRoll += 3.0F * recoil(u - snapImpact(CLOSE_A, CLOSE_B));

        // ---------- Kopf verfolgt das Messer (Hand + Wurfversatz, ohne die schnellen Drehungen) ----------
        float[] target = {GRIP_X_WIDE, GRIP_Y, GRIP_Z};
        armToModel(armPitch * blend, armYaw * blend, armRoll * blend, false, target);
        target[0] += out.tossX;
        target[1] += -tossLook - 3.0F;
        target[2] += out.tossZ;
        float dx = target[0], dy = target[1] + 4.0F, dz = target[2]; // relativ zu den Augen (0, -4, 0)
        float trackYaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));
        float trackPitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float headYaw = clamp(0.8F * trackYaw, -45.0F, 45.0F) - 4.0F * inspectWave;
        float headPitch = clamp(0.8F * trackPitch + 6.0F, -55.0F, 45.0F);
        float headRoll = 7.0F + 6.0F * inspect + 4.0F * inspectWave;

        // ---------- Koerper / linker Arm ----------
        float aerial = in(u, WIND_A, ABS_B) ? bump(seg(u, WIND_A, ABS_B)) : 0.0F;
        float breath = (float) Math.sin(t * 2.2F);
        float sway = (float) Math.sin(2.0 * Math.PI * 3.0 * u / PERIOD);
        out.rightArmPitch = rad(armPitch) * blend;
        out.rightArmYaw = rad(armYaw) * blend;
        out.rightArmRoll = rad(armRoll) * blend;
        out.leftArmPitch = rad(-6.0F + breath * 2.0F - 16.0F * aerial) * blend;
        out.leftArmYaw = rad(4.0F + 6.0F * aerial) * blend;
        out.leftArmRoll = rad(-5.0F - breath * 1.5F - 10.0F * aerial) * blend;
        out.headPitch = rad(headPitch) * blend;
        out.headYaw = rad(headYaw) * blend;
        out.headRoll = rad(headRoll) * blend;
        out.bodyPitch = rad(2.5F + breath * 0.6F - 4.0F * aerial + 3.0F * inspect) * blend;
        out.bodyYaw = rad(3.0F + 2.0F * inspect) * blend;
        out.bodyRoll = rad(1.2F * sway) * blend;
    }

    /**
     * Punkt in Arm-Lokalkoordinaten (px) -> Modellkoordinaten, wie ModelPart.applyTransform des rechten Arms
     * (Pivot (-5, 2|2.5, 0), Rotation Z*Y*X). Winkel in Grad.
     */
    public static void armToModel(float pitchDeg, float yawDeg, float rollDeg, boolean slim, float[] p) {
        rotX(p, pitchDeg);
        rotY(p, yawDeg);
        rotZ(p, rollDeg);
        p[0] += -5.0F;
        p[1] += slim ? 2.5F : 2.0F;
    }

    static void rotX(float[] p, float deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        float y = (float) (p[1] * c - p[2] * s), z = (float) (p[1] * s + p[2] * c);
        p[1] = y; p[2] = z;
    }

    static void rotY(float[] p, float deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        float x = (float) (p[0] * c + p[2] * s), z = (float) (-p[0] * s + p[2] * c);
        p[0] = x; p[2] = z;
    }

    static void rotZ(float[] p, float deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        float x = (float) (p[0] * c - p[1] * s), y = (float) (p[0] * s + p[1] * c);
        p[0] = x; p[1] = y;
    }

    /** Fortschritt 0..1 der Wurfdrehung: in der Hand anlaufen, in der Luft konstante Winkelgeschwindigkeit, beim Fangen abbremsen. */
    private static float flipProgress(float u) {
        float r = FLIP_RAMP, d = CATCH - RELEASE, v = 1.0F / (d + r);
        if (u < RELEASE) { float tau = u - (RELEASE - r); return v * tau * tau / (2.0F * r); }
        if (u < CATCH) return v * (r / 2.0F + (u - RELEASE));
        float tau = Math.min(u - CATCH, r);
        return v * (r / 2.0F + d + tau - tau * tau / (2.0F * r));
    }

    /** Zeitpunkt des Anschlags innerhalb eines Auf-/Zuklapp-Segments (siehe {@link #snap}). */
    private static float snapImpact(float a, float b) {
        return a + (0.3F + SNAP_HIT * 0.7F) * (b - a);
    }

    private static final float SNAP_HIT = 0.62F;
    private static final float SNAP_BOUNCE = 0.06F;

    /** Beschleunigt bis zum Anschlag (p = SNAP_HIT), danach kleiner gedaempfter Rueckprall, Ende mit Geschwindigkeit 0. */
    private static float snap(float p) {
        if (p < SNAP_HIT) { float q = p / SNAP_HIT; return q * q; }
        float q = (p - SNAP_HIT) / (1.0F - SNAP_HIT);
        return 1.0F - SNAP_BOUNCE * (float) Math.sin(Math.PI * q) * (1.0F - q);
    }

    /** Kurzer Rueckstoss nach einem Anschlag (tau in s ab Anschlag), 0 ausserhalb [0, 0.3]. */
    private static float recoil(float tau) {
        if (tau <= 0.0F || tau >= 0.3F) return 0.0F;
        if (tau < 0.05F) return (float) Math.sin(0.5 * Math.PI * tau / 0.05F);
        return 0.5F + 0.5F * (float) Math.cos(Math.PI * (tau - 0.05F) / 0.25F);
    }

    /** sin(2*pi*s) * sin(pi*s): einmal hin, einmal her, Geschwindigkeit 0 an beiden Enden. */
    private static float showWave(float s) {
        return (float) (Math.sin(2.0 * Math.PI * s) * Math.sin(Math.PI * s));
    }

    /** sin^2(pi*s): Huellkurve 0 -> 1 -> 0 mit Steigung 0 an den Enden. */
    private static float bump(float s) {
        float v = (float) Math.sin(Math.PI * s);
        return v * v;
    }

    private static boolean in(float u, float a, float b) {
        return u >= a && u < b;
    }

    private static float seg(float u, float a, float b) {
        return clamp01((u - a) / (b - a));
    }

    /**
     * Geschlossener kubischer Hermite-Spline (Tangenten aus den Nachbarn, auch ueber die Schleifengrenze).
     * times[0] muss 0 sein, alle Zeiten < PERIOD.
     */
    private static float loopSpline(float[] times, float[] values, float u) {
        int n = times.length;
        int i = n - 1;
        for (int k = 0; k < n - 1; k++) {
            if (u < times[k + 1]) { i = k; break; }
        }
        int j = (i + 1) % n;
        float t0 = times[i];
        float t1 = (j == 0) ? PERIOD : times[j];
        float h = t1 - t0;
        float s = h > 0.0F ? (u - t0) / h : 0.0F;
        float m0 = tangent(times, values, i) * h;
        float m1 = tangent(times, values, j) * h;
        float s2 = s * s, s3 = s2 * s;
        return (2 * s3 - 3 * s2 + 1) * values[i] + (s3 - 2 * s2 + s) * m0
                + (-2 * s3 + 3 * s2) * values[j] + (s3 - s2) * m1;
    }

    private static float tangent(float[] times, float[] values, int k) {
        int n = times.length;
        int prev = (k - 1 + n) % n;
        int next = (k + 1) % n;
        float tPrev = times[prev] - (prev > k ? PERIOD : 0.0F);
        float tNext = times[next] + (next < k ? PERIOD : 0.0F);
        return (values[next] - values[prev]) / (tNext - tPrev);
    }

    private static float easeInOutCubic(float x) {
        return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3) / 2.0F;
    }

    private static float smoothstep(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    /** Quintische Rampe: Geschwindigkeit und Beschleunigung 0 an beiden Enden. */
    private static float smoother(float x) {
        return x * x * x * (x * (6.0F * x - 15.0F) + 10.0F);
    }

    private static float clamp01(float x) {
        return x < 0.0F ? 0.0F : (x > 1.0F ? 1.0F : x);
    }

    private static float clamp(float x, float lo, float hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }
}

package com.bame.client.emotes;

/**
 * Zeitleiste und Pose des Hexenbesen-Emotes (ohne Minecraft-Klassen, im witchBroomTest geprüft). Alles hängt nur von
 * der Emote-Zeit {@code t} ab, daher sehen alle Clients dasselbe.
 * <p>
 * Ablauf: 0,05–0,42 s springt der Spieler leicht hoch und nimmt die Sitzhaltung ein (Beine nach vorne gespreizt,
 * Oberkörper vor); 0,26 s erscheint der Besen mit einem „Puff“ hinter ihm und gleitet bis 0,64 s entlang seiner Achse
 * zwischen den Beinen nach vorne, die Hände greifen den Stiel; bis 0,8 s landet der Spieler auf dem Stiel. Ab 0,9 s
 * fliegt der Besen weich in eine Kreisbahn ({@link WitchBroomLogic}); der Reiter ist starr mit dem Besen verbunden
 * (kein Herausfallen), Kopf schaut voraus bzw. folgt dem Blick, die Beine baumeln leicht.
 * <p>
 * Körpertransformation im Rahmen (Raum von {@code setupTransforms}, Füße im Ursprung), angewendet auf den stehenden
 * Spieler: {@code T = Tr(q) · Ry(riderHeading) · Rz(riderBank) · Rx(riderPitch) · Tr(-E)}; {@code E} ist die vordere
 * Unterkante des Rumpfs (Schritt), die im Sitzen auf dem Stiel liegt. Der Besen liegt bei
 * {@code W = Tr(b) · Ry(broomHeading) · Rz(broomBank) · Rx(broomNose) · Tr(0, 0, broomSlide) · S(broomScale)}.
 * Modellwinkel im Bogenmaß wie {@code ModelPart}.
 */
public final class WitchBroomMotion {

    private WitchBroomMotion() {}

    public static final double K = HammockLogic.BLOCKS_PER_PX;
    public static final double HIP_LOCAL_Y = HammockMotion.HIP_LOCAL_Y;
    /** Schritt = vordere Unterkante des Rumpfs (Modell y = 12, z = -2) im Raum des stehenden Spielers. */
    public static final double EDGE_Y = HIP_LOCAL_Y, EDGE_Z = -2.0 * K;
    /** Schritt liegt so weit über der Stielachse (äußerste Stielkante + 0,05 px Luft). */
    public static final double SEAT_Y = WitchBroomLogic.STICK_R_MAX + 0.05 * WitchBroomLogic.PX;

    // ------------------------------------------------------------------
    // Zeitleiste (s)
    // ------------------------------------------------------------------

    public static final float MOUNT_START = 0.05F, POSE_END = 0.42F, MOUNT_END = 0.8F;
    /** Kleiner Sprung beim Aufsitzen (Blöcke über der Sitzhöhe, sin²-Bogen über MOUNT_START..MOUNT_END). */
    public static final double LIFT = 0.10;
    /** Besen: erscheint bei SLIDE_START so weit hinter seiner Sitzlage (Blöcke entlang der Achse) und gleitet nach vorne. */
    public static final float SLIDE_START = 0.26F, SLIDE_END = 0.64F, GROW = 0.2F;
    public static final double SLIDE = 1.4;
    /** Beim Hereingleiten liegt der Besen anfangs flacher (Anteil der Nasen-Neigung, um den Sitzpunkt), damit das Reisig
     * nicht in den Boden taucht; er richtet sich mit dem Gleiten auf. */
    public static final float SLIDE_LEVEL = 0.8F;
    /** Hände greifen zum Stiel. */
    public static final float ARM_START = 0.22F, ARM_END = 0.6F;
    public static final float INTRO = WitchBroomLogic.FLY_START;

    // ------------------------------------------------------------------
    // Sitzhaltung
    // ------------------------------------------------------------------

    /** Oberkörper gegenüber dem Stiel vorgebeugt (Grad, um den Schritt) und leichtes Atmen/Wippen. */
    public static final float LEAN_DEG = 60.0F, LEAN_OSC_DEG = 1.0F;
    /** Beine gegenüber der Senkrechten des Besens nach vorne (Grad), gespreizt (Yaw) und leicht nach außen gerollt. */
    public static final float LEG_FWD_DEG = 9.0F, LEG_SPREAD_DEG = 12.0F, LEG_ROLL_DEG = 14.0F, LEG_DANGLE_DEG = 3.0F;
    /** Hüftgelenke im Sitzen so weit nach außen (px): Platz für den Stiel zwischen den Oberschenkeln. */
    public static final float LEG_SHIFT = 0.8F;
    /** Schultern beim Greifen leicht nach unten/vorne (px), wie beim Nach-vorne-Greifen. */
    public static final float SHOULDER_DROP = 1.0F, SHOULDER_FWD = 1.2F;
    /** Griffpunkte auf der Stielachse (Besen-z, Blöcke): rechte Hand vorne, linke dahinter. */
    public static final double GRIP_R_Z = -0.72, GRIP_L_Z = -0.44;
    /** Griff im Arm (px): seitlich (0 = innere Hälfte der Faust, breite und schmale Arme) und entlang des Arms (Faust). */
    public static final float GRIP_X = 0.0F, GRIP_Y_MIN = 7.2F, GRIP_Y_MAX = 9.45F;
    /** Schulter-Drehpunkte (breite Arme; schmale Arme 0,5 px tiefer). */
    public static final float SHOULDER_X = 5.0F, SHOULDER_Y = 2.0F, SHOULDER_Y_SLIM = 2.5F;
    /** Kopf gleicht die Vorlage im Raum zu diesem Anteil aus (Blick nach vorne statt auf den Stiel). */
    public static final float HEAD_LEVEL = 0.85F;
    /** Kopf schaut leicht in die Kurve (Grad, rechts). */
    public static final float HEAD_INTO_TURN = 9.0F;

    // ------------------------------------------------------------------
    // Vorschau (Menü, Emote-Rad): Besen schwebt auf der Stelle, schräg von vorne
    // ------------------------------------------------------------------

    public static final float PREVIEW_HEADING = -62.0F, PREVIEW_SWAY_DEG = 9.0F;

    /** Ergebnis von {@link #sample}; wiederverwendbar (keine Allokation pro Frame). */
    public static final class Pose {
        public float t;
        /** Aufsitzen 0..1, Sprung (Blöcke), Flug 0..1. */
        public float mount, flight;
        public double lift;
        /** Besen: Sitzpunkt im Rahmen, Ausrichtung (Grad), Versatz entlang der Achse beim Hereingleiten, Größe 0..1. */
        public double bx, by, bz, broomSlide;
        /** Zusätzliche Drehung des gezeichneten Besens um den Sitzpunkt beim Hereingleiten (Grad, Rx; negativ = flacher). */
        public float broomSlideTilt;
        public float broomHeading, broomBank, broomNose, broomScale;
        /** Reiter: Lage des Schritts im Rahmen und Drehungen (siehe Klassenkommentar). */
        public double qx, qy, qz;
        public float riderHeading, riderBank, riderPitch;
        /** Vorlage gegenüber dem Stiel (Grad). */
        public float lean;

        public float headPitch, headYaw, headRoll;
        public float rArmPitch, rArmYaw, rArmRoll;
        public float lArmPitch, lArmYaw, lArmRoll;
        public float rLegPitch, rLegYaw, rLegRoll;
        public float lLegPitch, lLegYaw, lLegRoll;
        /** Schulterversatz (px, Modellraum: +Y unten, -Z vorne). */
        public float shoulderDY, shoulderDZ;
        /** Hüftgelenke nach außen (px, je Seite). */
        public float legShift;
        /** Griff entlang des Arms (px) und Abweichung der Hand vom Griffpunkt (px, 0 = genau auf der Achse). */
        public float rGripY, lGripY, rGripErr, lGripErr;
        public boolean slim;
    }

    private static final float DEG = (float) (Math.PI / 180.0);

    /** Pose ohne Blick-Folgen, breite Arme, Skalierung 1 (Tests). */
    public static void sample(float t, Pose out) {
        sample(t, 0.0F, 0.0F, false, false, false, 1.0F, out);
    }

    /**
     * Pose zur Emote-Zeit {@code t}.
     *
     * @param lookYawDeg   Blickrichtung relativ zur Start-Ausrichtung (Grad, + = rechts), {@code lookPitchDeg} + = unten
     * @param look         {@code false}: nur Leerlauf-Blicke (Vorschau, Tests)
     * @param preview      Vorschau: der Besen schwebt auf der Stelle (keine Kreisbahn), schräg von vorne
     * @param slim         schmale Arme (Schulter 0,5 px tiefer)
     * @param scale        Spieler-Skalierung; die Kreisbahn ist in echten Blöcken, alles andere wächst mit dem Spieler
     */
    public static void sample(float t, float lookYawDeg, float lookPitchDeg, boolean look, boolean preview, boolean slim,
                              float scale, Pose out) {
        if (t < 0.0F) t = 0.0F;
        if (!(scale > 0.0F)) scale = 1.0F;
        out.t = t;
        out.slim = slim;
        float env = (float) WitchBroomLogic.flightEnv(t);
        out.flight = env;

        // --- Besen ---
        double[] pos = new double[3];
        WitchBroomLogic.position(t, pos);
        double heading = WitchBroomLogic.headingDeg(t);
        if (preview) {
            double ph = 2.0 * Math.PI * (t - WitchBroomLogic.FLY_START) / WitchBroomLogic.PERIOD;
            pos[0] = 0.0;
            pos[2] = 0.0;
            heading = PREVIEW_HEADING + PREVIEW_SWAY_DEG * env * Math.sin(ph);
        }
        out.bx = pos[0] / scale;
        out.by = pos[1];
        out.bz = pos[2] / scale;
        out.broomHeading = (float) heading;
        out.broomBank = (float) WitchBroomLogic.bankDeg(t);
        out.broomNose = (float) WitchBroomLogic.noseDeg(t);
        float slide = (float) WitchBroomLogic.smoother((t - SLIDE_START) / (SLIDE_END - SLIDE_START));
        out.broomSlide = SLIDE * (1.0 - slide);
        out.broomSlideTilt = -SLIDE_LEVEL * out.broomNose * (1.0F - slide);
        float g = HammockMotion.clamp01((t - SLIDE_START) / GROW);
        out.broomScale = t < SLIDE_START ? 0.0F : 1.0F - (1.0F - g) * (1.0F - g) * (1.0F - g);

        // --- Reiter ---
        float m = (float) WitchBroomLogic.smoother((t - MOUNT_START) / (POSE_END - MOUNT_START));
        out.mount = m;
        double u = HammockMotion.clamp01((t - MOUNT_START) / (MOUNT_END - MOUNT_START));
        double su = Math.sin(Math.PI * u);
        out.lift = LIFT * su * su;
        double bob = 2.0 * Math.PI * (t - WitchBroomLogic.FLY_START) / WitchBroomLogic.BOB_PERIOD;
        out.lean = LEAN_DEG + (t > WitchBroomLogic.FLY_START ? (float) (LEAN_OSC_DEG * env * Math.sin(bob + 0.9)) : 0.0F);
        // Schritt im Sitzen: über der Stielachse am Sitzpunkt
        double[] seat = {0.0, SEAT_Y, 0.0};
        broomRotate(out, seat);
        double sx = out.bx + seat[0], sy = out.by + seat[1], sz = out.bz + seat[2];
        out.qx = sx * m;
        out.qy = EDGE_Y + (sy - EDGE_Y) * m + out.lift;
        out.qz = EDGE_Z + (sz - EDGE_Z) * m;
        out.riderHeading = out.broomHeading * m;
        out.riderBank = out.broomBank * m;
        out.riderPitch = (out.broomNose - out.lean) * m;

        // --- Beine: nach vorne (gegen die Vorlage gerechnet), gespreizt, baumeln leicht ---
        float legBase = -(LEG_FWD_DEG + out.lean);
        float dangle = (float) (LEG_DANGLE_DEG * env * Math.sin(bob * 0.5 + 0.4));
        out.legShift = LEG_SHIFT * m;
        out.rLegPitch = (legBase + dangle) * m * DEG;
        out.lLegPitch = (legBase - dangle * 0.8F) * m * DEG;
        out.rLegYaw = LEG_SPREAD_DEG * m * DEG;
        out.lLegYaw = -LEG_SPREAD_DEG * m * DEG;
        out.rLegRoll = LEG_ROLL_DEG * m * DEG;
        out.lLegRoll = -LEG_ROLL_DEG * m * DEG;

        // --- Arme: Hände am Stiel (Rückwärtskinematik zum ruhenden Besen) ---
        float a = (float) WitchBroomLogic.smoother((t - ARM_START) / (ARM_END - ARM_START));
        out.shoulderDY = SHOULDER_DROP * a;
        out.shoulderDZ = -SHOULDER_FWD * a;
        float[] r = new float[4];
        armToStick(out, true, GRIP_R_Z, r);
        out.rArmPitch = r[0] * a;
        out.rArmYaw = r[1] * a;
        out.rArmRoll = 0.0F;
        out.rGripY = r[2];
        out.rGripErr = r[3];
        armToStick(out, false, GRIP_L_Z, r);
        out.lArmPitch = r[0] * a;
        out.lArmYaw = r[1] * a;
        out.lArmRoll = 0.0F;
        out.lGripY = r[2];
        out.lGripErr = r[3];

        // --- Kopf: gleicht Vorlage und Kurvenlage teilweise aus, schaut voraus/in die Kurve, folgt dem Blick ---
        float worldLean = (out.lean - out.broomNose) * m;
        float idleYaw = (float) (5.0 * Math.sin(t * 2.0 * Math.PI / 6.1) + 2.5 * Math.sin(t * 2.0 * Math.PI / 2.9 + 0.8))
                + HEAD_INTO_TURN * env;
        float idlePitch = (float) (2.5 * Math.sin(t * 2.0 * Math.PI / 4.7 + 0.3));
        float headYaw = idleYaw, headPitch = idlePitch;
        if (look) {
            float wy = (float) HammockLogic.wrapDegrees(lookYawDeg + out.riderHeading);
            float back = HammockMotion.lookBackFade(wy);
            float follow = SwingMotion.easeInOut((Math.abs(wy) - 10.0F) / 30.0F) * back;
            headYaw = SwingMotion.lerp(idleYaw, SwingMotion.softClamp(wy, -60.0F, 60.0F, 10.0F), follow);
            float lp = SwingMotion.softClamp(lookPitchDeg, -30.0F, 30.0F, 10.0F);
            float followP = Math.max(follow, SwingMotion.easeInOut((Math.abs(lookPitchDeg) - 10.0F) / 20.0F) * back);
            headPitch = SwingMotion.lerp(idlePitch, lp, followP);
        }
        float pitch = headPitch - HEAD_LEVEL * worldLean;
        out.headPitch = SwingMotion.softClamp(pitch, -45.0F, 40.0F, 8.0F) * DEG;
        out.headYaw = headYaw * m * DEG;
        out.headRoll = -0.5F * out.riderBank * DEG;
    }

    /** Vektor im Besen-Raum mit der Ausrichtung des Besens drehen (ohne Versatz/Größe). */
    public static void broomRotate(Pose p, double[] v) {
        WitchBroomLogic.rotX(v, p.broomNose);
        WitchBroomLogic.rotZ(v, p.broomBank);
        WitchBroomLogic.rotY(v, p.broomHeading);
    }

    // ------------------------------------------------------------------
    // Rückwärtskinematik: Hand an die Stielachse
    // ------------------------------------------------------------------

    /**
     * Armwinkel {pitch, yaw, gripY, err}: Der Griffpunkt (GRIP_X, gripY) im Arm liegt auf dem Punkt (0, 0, z) der
     * Stielachse des <b>ruhenden</b> Besens (ohne Hereingleiten). gripY wird innerhalb der Faust gewählt; reicht der Arm
     * nicht, bleibt die Abweichung {@code err} (px).
     */
    public static void armToStick(Pose p, boolean right, double z, float[] out) {
        double[] w = {0.0, 0.0, z};
        broomRotate(p, w);
        w[0] += p.bx;
        w[1] += p.by;
        w[2] += p.bz;
        double[] b = frameToBody(p, w[0], w[1], w[2], new double[3]);
        double tx = -b[0] / K, ty = HammockLogic.MODEL_ORIGIN_UP_PX - b[1] / K, tz = b[2] / K;
        double sx = right ? -SHOULDER_X : SHOULDER_X;
        double sy = (p.slim ? SHOULDER_Y_SLIM : SHOULDER_Y) + p.shoulderDY;
        double sz = p.shoulderDZ;
        double gx = right ? -GRIP_X : GRIP_X;
        double dx = tx - sx, dy = ty - sy, dz = tz - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double gy = Math.sqrt(Math.max(0.0, len * len - gx * gx));
        gy = Math.max(GRIP_Y_MIN, Math.min(GRIP_Y_MAX, gy));
        double g = Math.hypot(gx, gy);
        float err = (float) Math.abs(len - g);
        if (len > 1.0E-9) {
            dx *= g / len;
            dy *= g / len;
            dz *= g / len;
        }
        // R = Ry(yaw) · Rx(pitch): erst nach vorne-unten heben, dann waagerecht nach innen schwenken (der Arm bleibt
        // dabei vor dem Rumpf statt quer davor zu rollen)
        double pitch = -Math.acos(Math.max(-1.0, Math.min(1.0, dy / gy)));
        double yaw = Math.atan2(dx, dz) - Math.atan2(gx, gy * Math.sin(pitch));
        while (yaw > Math.PI) yaw -= 2.0 * Math.PI;
        while (yaw <= -Math.PI) yaw += 2.0 * Math.PI;
        out[0] = (float) pitch;
        out[1] = (float) yaw;
        out[2] = (float) gy;
        out[3] = err;
    }

    /** Griffpunkt (Modell-px) eines Arms mit diesen Winkeln (Schulter wie in der Pose). */
    public static double[] gripPoint(Pose p, boolean right, float pitch, float yaw, float gripY, double[] out) {
        CampfireMotion.rotZYX(right ? -GRIP_X : GRIP_X, gripY, 0.0, pitch, yaw, 0.0F, out);
        out[0] += right ? -SHOULDER_X : SHOULDER_X;
        out[1] += (p.slim ? SHOULDER_Y_SLIM : SHOULDER_Y) + p.shoulderDY;
        out[2] += p.shoulderDZ;
        return out;
    }

    // ------------------------------------------------------------------
    // Transformationen (Tests, Effekte, Renderer)
    // ------------------------------------------------------------------

    /** Punkt des stehenden Spielers (Raum von {@code setupTransforms}, Füße im Ursprung) in den Rahmen. */
    public static double[] bodyToFrame(Pose p, double x, double y, double z, double[] out) {
        out[0] = x;
        out[1] = y - EDGE_Y;
        out[2] = z - EDGE_Z;
        WitchBroomLogic.rotX(out, p.riderPitch);
        WitchBroomLogic.rotZ(out, p.riderBank);
        WitchBroomLogic.rotY(out, p.riderHeading);
        out[0] += p.qx;
        out[1] += p.qy;
        out[2] += p.qz;
        return out;
    }

    /** Umkehrung von {@link #bodyToFrame}. */
    public static double[] frameToBody(Pose p, double x, double y, double z, double[] out) {
        out[0] = x - p.qx;
        out[1] = y - p.qy;
        out[2] = z - p.qz;
        WitchBroomLogic.rotY(out, -p.riderHeading);
        WitchBroomLogic.rotZ(out, -p.riderBank);
        WitchBroomLogic.rotX(out, -p.riderPitch);
        out[1] += EDGE_Y;
        out[2] += EDGE_Z;
        return out;
    }

    /** Punkt im Raum des Besens (ruhend, ohne Hereingleiten/Größe) in den Rahmen. */
    public static double[] broomToFrame(Pose p, double x, double y, double z, double[] out) {
        out[0] = x;
        out[1] = y;
        out[2] = z;
        broomRotate(p, out);
        out[0] += p.bx;
        out[1] += p.by;
        out[2] += p.bz;
        return out;
    }

    /** Wie {@link #broomToFrame}, aber mit Hereingleiten und Größe (so, wie der Besen gezeichnet wird). */
    public static double[] drawnBroomToFrame(Pose p, double x, double y, double z, double[] out) {
        out[0] = x * p.broomScale;
        out[1] = y * p.broomScale;
        out[2] = z * p.broomScale + p.broomSlide;
        WitchBroomLogic.rotX(out, p.broomSlideTilt);
        return broomToFrame(p, out[0], out[1], out[2], out);
    }

    /** Umkehrung von {@link #drawnBroomToFrame} (Punkt im Rahmen in den Modellraum des gezeichneten Besens). */
    public static double[] frameToDrawnBroom(Pose p, double x, double y, double z, double[] out) {
        frameToBroom(p, x, y, z, out);
        WitchBroomLogic.rotX(out, -p.broomSlideTilt);
        double s = p.broomScale > 1.0E-6F ? p.broomScale : 1.0E-6;
        out[0] /= s;
        out[1] /= s;
        out[2] = (out[2] - p.broomSlide) / s;
        return out;
    }

    /** Punkt im Rahmen in den Raum des ruhenden Besens. */
    public static double[] frameToBroom(Pose p, double x, double y, double z, double[] out) {
        out[0] = x - p.bx;
        out[1] = y - p.by;
        out[2] = z - p.bz;
        WitchBroomLogic.rotY(out, -p.broomHeading);
        WitchBroomLogic.rotZ(out, -p.broomBank);
        WitchBroomLogic.rotX(out, -p.broomNose);
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

    /** Drehpunkt eines Körperteils (Modell-px) inkl. Schulterversatz der Pose. */
    public static double[] partPivot(Pose p, HammockMotion.Part part, double[] out) {
        out[0] = part.px;
        out[1] = part.py;
        out[2] = part.pz;
        if (part == HammockMotion.Part.RIGHT_LEG) out[0] -= p.legShift;
        if (part == HammockMotion.Part.LEFT_LEG) out[0] += p.legShift;
        if (part == HammockMotion.Part.RIGHT_ARM || part == HammockMotion.Part.LEFT_ARM) {
            if (p.slim) out[1] += SHOULDER_Y_SLIM - SHOULDER_Y;
            out[1] += p.shoulderDY;
            out[2] += p.shoulderDZ;
        }
        return out;
    }

    /** Punkt eines Körperteils (lokale px im Quader-Raum des Teils) in den Rahmen. */
    public static double[] partToFrame(Pose p, HammockMotion.Part part, double lx, double ly, double lz, double[] out) {
        float[] a = partAngles(p, part);
        double[] m = new double[3];
        CampfireMotion.rotZYX(lx, ly, lz, a[0], a[1], a[2], m);
        double[] pv = partPivot(p, part, new double[3]);
        HammockMotion.modelToBody(m[0] + pv[0], m[1] + pv[1], m[2] + pv[2], m);
        return bodyToFrame(p, m[0], m[1], m[2], out);
    }

    /** Modellpunkt (px) in den Rahmen. */
    public static double[] modelToFrame(Pose p, double mx, double my, double mz, double[] out) {
        double[] m = HammockMotion.modelToBody(mx, my, mz, new double[3]);
        return bodyToFrame(p, m[0], m[1], m[2], out);
    }

    /** Ende des Reisigs (Mitte) im Rahmen: Quelle des Funkenschweifs. */
    public static double[] bristleTip(Pose p, double[] out) {
        return drawnBroomToFrame(p, 0.0, 0.0, WitchBroomLogic.BRISTLE_END_Z, out);
    }

    // ------------------------------------------------------------------
    // Kamera (lokaler Spieler): Drehpunkt über der Kreismitte, fest in der Welt
    // ------------------------------------------------------------------

    public static final double CAMERA_PIVOT_Y = 1.25;

    public static double[] cameraPivot() {
        return new double[]{0.0, CAMERA_PIVOT_Y, 0.0};
    }

    // ------------------------------------------------------------------
    // Effekte (Funken)
    // ------------------------------------------------------------------

    /** Puff beim Erscheinen des Besens (Emote-Zeit). */
    public static final float PUFF_AT = SLIDE_START;
    /** Funkenschweif ab dieser Flugstärke; ein Stern alle STAR_EVERY Ticks. */
    public static final float TRAIL_FROM = 0.15F;
    public static final int STAR_EVERY = 7;
}

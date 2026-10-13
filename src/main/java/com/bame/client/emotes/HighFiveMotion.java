package com.bame.client.emotes;

/**
 * Bewegungsablauf des High-Five (ohne Minecraft-Klassen, im plannerTest geprüft). Alles hängt nur von der Emote-Zeit
 * und dem gemeinsamen Klatsch-Zeitpunkt ab, daher sehen alle Clients dieselbe Bewegung.
 * <p>
 * Ablauf (τ = Zeit relativ zum Klatsch in ms):
 * <ul>
 *   <li><b>Warten</b> (nur HIGH_FIVE): Arm in 0,3 s hoch (leicht überschwingend), Handfläche nach vorne, eifriges
 *       Wippen, Finger-Wackeln (Drehung der Hand um die Armachse) und alle 2,6 s ein „Komm schon“-Wink mit
 *       Kopfneigen.</li>
 *   <li><b>Ausholen</b> τ ∈ [-250, -130): Arm zieht nach hinten oben, Oberkörper dreht die rechte Schulter zurück und
 *       geht leicht in die Knie (Antizipation). Beim Einschlagen (HIGH_FIVE_ACCEPT) hebt sich der Arm stattdessen in
 *       den ersten 170 ms direkt in diese Ausholpose.</li>
 *   <li><b>Schwung</b> τ ∈ [-130, 0): beschleunigt (ease-in) auf einem Bogen zum Treffpunkt; Oberkörper dreht zum
 *       Partner, lehnt sich vor und macht notfalls einen Ausfallschritt, damit die Hand den Treffpunkt erreicht.</li>
 *   <li><b>Kontakt</b> τ = 0: Handfläche (Innenseite der Hand) liegt genau auf dem gemeinsamen Zielpunkt.</li>
 *   <li><b>Rückprall</b> τ ∈ [0, 180): Hände federn auseinander und wieder zurück (gedämpfte Schwingung).</li>
 *   <li><b>Nachschwung</b> τ ∈ [180, 470): Faust-Pumpe nach unten vorne, kleiner Hüpfer (190–450 ms).</li>
 *   <li><b>Ausklingen</b> τ ∈ [470, 800): zurück in die Grundhaltung, bei τ = 800 exakt neutral.</li>
 * </ul>
 * Modellraum wie {@link HighFiveLogic}: Pixel, +Y nach unten, -Z nach vorne, -X = rechte Hand.
 */
public final class HighFiveMotion {

    private HighFiveMotion() {}

    public enum Role { WAITER, ACCEPTER, PREVIEW }

    // ------------------------------------------------------------------
    // Zeiten (ms relativ zum Klatsch, wenn nicht anders angegeben)
    // ------------------------------------------------------------------

    /** Schneller Schwung bis zum Kontakt. */
    public static final float SWING_MS = 130.0F;
    /** Ausholen des Wartenden (REACH_MS - Schwung = 120 ms). */
    public static final float ANTICIPATION_MS = HighFiveLogic.REACH_MS - SWING_MS;
    /** Einschlagender: Arm hebt sich vom Start bis zur Ausholpose (300 - 130 = 170 ms). */
    public static final float ACCEPT_RAISE_MS = HighFiveLogic.ACCEPT_CLAP_DELAY_MS - SWING_MS;
    public static final float RECOIL_MS = 180.0F;
    public static final float PUMP_AT_MS = 360.0F;
    public static final float SETTLE_FROM_MS = 470.0F;
    public static final float END_MS = HighFiveLogic.END_MS;
    public static final float HOP_FROM_MS = 190.0F, HOP_TO_MS = 450.0F;
    /** Höhe des kleinen Hüpfers (px). */
    public static final float HOP_PX = 1.8F;
    /** Rückprall der Hand nach dem Kontakt (px, Amplitude der gedämpften Schwingung). */
    public static final float RECOIL_PX = 3.5F;
    /** Arm heben beim Warten (s). */
    public static final float RAISE_S = 0.3F;
    /** Einschlagen ohne Partner: nach so vielen Sekunden Arm wieder senken. */
    public static final float ACCEPT_GIVE_UP_S = HighFiveLogic.ACCEPT_MATCH_WINDOW_MS / 1000.0F;
    public static final float ACCEPT_LOWER_S = 0.3F;
    /** Vorschau: Zyklus mit Warten und einem Klatsch ins Leere. */
    public static final float PREVIEW_PERIOD_S = 2.2F;
    public static final float PREVIEW_CLAP_S = 1.25F;
    public static final double PREVIEW_PARTNER_DIST = 1.1;
    /** „Komm schon“-Wink beim Warten: alle 2,6 s (ab 1,2 s), Dauer 0,55 s. */
    public static final float COME_ON_FROM_S = 1.2F, COME_ON_PERIOD_S = 2.6F, COME_ON_LEN_S = 0.55F;

    // ------------------------------------------------------------------
    // Geometrie
    // ------------------------------------------------------------------

    /** Handfläche: so weit entlang des Arms vom Schulter-Drehpunkt (px) ... */
    public static final double PALM_ALONG_PX = 9.0;
    /** ... und so weit zur Innenseite (+X im Arm, Fläche des Arm-Quaders). */
    public static final double PALM_INNER_PX = 1.0;
    public static final double ARM_SCALE_MIN = 0.85, ARM_SCALE_MAX = 1.12;
    /** Ausfallschritt nach vorne bzw. zurück (px), falls der Arm allein den Treffpunkt nicht erreicht. */
    public static final double SHIFT_MAX_PX = 12.0, SHIFT_BACK_MAX_PX = 6.0;
    public static final double HIP_Y_PX = 12.0;
    /** Höchstens so weit dreht der Oberkörper zum Partner (rad). */
    public static final float MAX_TURN = 0.5F;

    // Posen (rad)
    private static final float WAIT_PITCH = (float) Math.toRadians(-160);
    private static final float WAIT_YAW = (float) Math.toRadians(-8);
    private static final float WAIT_ROLL = (float) Math.toRadians(-11);
    private static final float COCK_PITCH = (float) Math.toRadians(-168);
    private static final float COCK_YAW = (float) Math.toRadians(-18);
    private static final float COCK_ROLL = (float) Math.toRadians(-17);
    private static final float COCK_TURN = 0.22F;
    private static final float COCK_LEAN = -0.10F;
    private static final float COCK_DIP = 0.9F;
    private static final float CONTACT_EXTRA_LEAN = 0.04F;
    private static final float UNCOIL = 0.06F;
    private static final float PUMP_PITCH = (float) Math.toRadians(-62);

    private static final double[] FORWARD = {0.0, 0.0, -1.0};

    /** Eingaben für {@link #sample}. */
    public static final class Input {
        public Role role = Role.WAITER;
        /** Emote-Zeit (s). */
        public float t;
        /** Gepaart (Koordinator liefert Klatsch-Zeit und Zielpunkt)? */
        public boolean paired;
        /** Jetzt minus Klatsch-Zeit (ms). */
        public double tauMs;
        /** Klatsch-Zeit minus Zeitpunkt, ab dem dieser Client das Paar kennt (ms). */
        public double leadMs = Double.MAX_VALUE;
        /** Zielpunkt im Modellraum (px). */
        public double[] target;
        /** Partner (Füße) im Modellraum (px), nur x/z werden benutzt. */
        public double partnerX, partnerZ = -1.0;
        /** Waagerechter Abstand zum Partner (Blöcke). */
        public double partnerDistance = 1.0;
        /** Ruhe-Drehpunkte der Teile (Modell-Defaults). */
        public double[] bodyRest = {0.0, 0.0, 0.0};
        public double[] headRest = {0.0, 0.0, 0.0};
        public double[] rightShoulder = {HighFiveLogic.SHOULDER_X_PX, HighFiveLogic.SHOULDER_Y_PX, 0.0};
        public double[] leftShoulder = {-HighFiveLogic.SHOULDER_X_PX, HighFiveLogic.SHOULDER_Y_PX, 0.0};
    }

    /** Ergebnis: absolute Drehpunkte (px) und Winkel (rad) der Modellteile. */
    public static final class Pose {
        public float bodyX, bodyY, bodyZ, bodyPitch, bodyYaw, bodyRoll;
        public float headX, headY, headZ;
        /** Zusätzlich zum Blick: Kopf heben/neigen. */
        public float headPitchAdd, headRoll;
        /** Vorlehnen (rad), damit der Kopf gegensteuern kann. */
        public float lean;
        public float rArmX, rArmY, rArmZ, rArmPitch, rArmYaw, rArmRoll;
        /** Drehung um die eigene Armachse (nach pitch/yaw/roll, lokal) und Längenfaktor. */
        public float rArmTwist, rArmScale = 1.0F;
        public float lArmX, lArmY, lArmZ, lArmPitch, lArmYaw, lArmRoll;
        /** Versatz beider Beine (px) und Ausfallschritt. */
        public float legDX, legDY, legDZ, rLegPitch, lLegPitch;
        /** 0..1: wie stark Kopf/Körper gerade zum Partner gerichtet sind (zum Mischen mit dem Blick). */
        public float partnerFocus;
    }

    /** Zwischenwerte, die linear gemischt werden. */
    private static final class Key {
        float lean, turn, roll, dip, shiftX, shiftZ;
        float rp, ry, rr, rtw, rs = 1.0F;
        float lp, lr;
        float headPitch, headRoll, focus;

        Key copy() {
            Key k = new Key();
            k.lean = lean; k.turn = turn; k.roll = roll; k.dip = dip; k.shiftX = shiftX; k.shiftZ = shiftZ;
            k.rp = rp; k.ry = ry; k.rr = rr; k.rtw = rtw; k.rs = rs; k.lp = lp; k.lr = lr;
            k.headPitch = headPitch; k.headRoll = headRoll; k.focus = focus;
            return k;
        }

        static Key lerp(Key a, Key b, float f) {
            Key k = new Key();
            k.lean = mix(a.lean, b.lean, f);
            k.turn = mix(a.turn, b.turn, f);
            k.roll = mix(a.roll, b.roll, f);
            k.dip = mix(a.dip, b.dip, f);
            k.shiftX = mix(a.shiftX, b.shiftX, f);
            k.shiftZ = mix(a.shiftZ, b.shiftZ, f);
            k.rp = mix(a.rp, b.rp, f);
            k.ry = mix(a.ry, b.ry, f);
            k.rr = mix(a.rr, b.rr, f);
            k.rtw = mix(a.rtw, b.rtw, f);
            k.rs = mix(a.rs, b.rs, f);
            k.lp = mix(a.lp, b.lp, f);
            k.lr = mix(a.lr, b.lr, f);
            k.headPitch = mix(a.headPitch, b.headPitch, f);
            k.headRoll = mix(a.headRoll, b.headRoll, f);
            k.focus = mix(a.focus, b.focus, f);
            return k;
        }
    }

    /** Pro Abtastung: abgeleitete Werte. */
    private static final class Ctx {
        Input in;
        float t;
        /** Zeit fürs Armheben beim Warten (s); in der Vorschau die Zyklus-Zeit. */
        float raiseT;
        double tau;
        boolean paired;
        double[] target;
        double partnerX, partnerZ, distance;
        float partnerTurn;
        /** Gesamtfenster vor dem Kontakt (Ausholen + Schwung) und Schwungdauer (ms). */
        double total, swing;
        Key contactBody;
        double[] palmDir;
    }

    // ------------------------------------------------------------------
    // Abtastung
    // ------------------------------------------------------------------

    public static void sample(Input in, Pose out) {
        Ctx c = new Ctx();
        c.in = in;
        c.t = Math.max(0.0F, in.t);
        c.raiseT = c.t;
        c.paired = in.paired && in.target != null;
        c.tau = in.tauMs;
        c.target = in.target;
        c.partnerX = in.partnerX;
        c.partnerZ = in.partnerZ;
        c.distance = in.partnerDistance;

        if (in.role == Role.PREVIEW) {
            float cyc = c.t % PREVIEW_PERIOD_S;
            c.raiseT = cyc;
            c.tau = (cyc - PREVIEW_CLAP_S) * 1000.0;
            c.paired = true;
            double ppx = HighFiveLogic.BLOCKS_PER_PX;
            double fwd = PREVIEW_PARTNER_DIST / ppx;
            double sy = in.rightShoulder[1];
            c.target = new double[]{0.0, sy - HighFiveLogic.TARGET_ABOVE_SHOULDER / ppx, -fwd * 0.5};
            c.partnerX = 0.0;
            c.partnerZ = -fwd;
            c.distance = PREVIEW_PARTNER_DIST;
        }

        Key k;
        float hop = 0.0F;
        if (!c.paired) {
            k = in.role == Role.ACCEPTER ? accepterPre(c) : waitRaised(c);
        } else {
            prepare(c);
            k = timeline(c);
            hop = hop(c.tau);
        }
        build(in, k, hop, out);
    }

    private static void prepare(Ctx c) {
        double lead = Math.max(1.0, c.in.role == Role.PREVIEW ? Double.MAX_VALUE : c.in.leadMs);
        if (c.in.role == Role.ACCEPTER) {
            double sinceStart = Math.max(1.0, c.t * 1000.0 - c.tau); // Klatsch minus Emote-Start
            c.swing = Math.min(SWING_MS, sinceStart * 0.55);
            c.total = c.swing;
        } else {
            c.total = Math.min(HighFiveLogic.REACH_MS, lead);
            c.swing = Math.min(SWING_MS, c.total * 0.55);
        }
        double pl = Math.hypot(c.partnerX, c.partnerZ);
        c.partnerTurn = pl < 1.0E-6 ? 0.0F
                : (float) clamp(Math.atan2(-c.partnerX, -c.partnerZ), -MAX_TURN, MAX_TURN);
        contactBody(c);
    }

    private static Key timeline(Ctx c) {
        double tau = c.tau;
        if (tau >= END_MS) return rest();
        if (tau >= 0.0) return post(c, tau);
        if (tau >= -c.swing) {
            float u = (float) ((tau + c.swing) / c.swing);
            return Key.lerp(pre(c, tau), contact(c, 0.0), u * u);
        }
        return pre(c, tau);
    }

    /** Pose vor dem Schwung (zur Zeit τ). */
    private static Key pre(Ctx c, double tau) {
        if (c.in.role == Role.ACCEPTER) return accepterPre(c);
        Key wait = waitRaised(c);
        double anticStart = -c.total;
        if (tau < anticStart) return wait;
        double antic = c.total - c.swing;
        float u = antic <= 1.0E-6 ? 1.0F : (float) clamp((tau - anticStart) / antic, 0.0, 1.0);
        return Key.lerp(wait, cock(c, true), easeOutCubic(u));
    }

    /** Nach dem Kontakt: Rückprall, Faust-Pumpe, Ausklingen. */
    private static Key post(Ctx c, double tau) {
        Key k = contact(c, tau);
        if (tau >= RECOIL_MS) {
            float e = easeInOut((float) ((tau - RECOIL_MS) / (PUMP_AT_MS - RECOIL_MS)));
            k = Key.lerp(k, pump(c, tau), e);
        }
        if (tau >= SETTLE_FROM_MS) {
            float e = easeInOut((float) ((tau - SETTLE_FROM_MS) / (END_MS - SETTLE_FROM_MS)));
            Key r = rest();
            // Arm ausschütteln, klingt bis zum Ende ab
            float shake = (float) (Math.sin((tau - SETTLE_FROM_MS) / 110.0 * Math.PI * 2.0) * 0.12 * (1.0F - e));
            k = Key.lerp(k, r, e);
            k.rr += shake;
        }
        return k;
    }

    // ------------------------------------------------------------------
    // Posen
    // ------------------------------------------------------------------

    private static Key rest() {
        return new Key();
    }

    /** Wartepose mit Wippen, Finger-Wackeln und „Komm schon“-Wink. */
    private static Key wait(float t) {
        Key k = new Key();
        double bob = Math.sin(t * Math.PI * 2.0 * 2.2);
        k.lean = 0.04F + 0.015F * (float) bob;
        k.roll = 0.035F * (float) Math.sin(t * Math.PI * 2.0 * 0.9);
        k.dip = 0.45F * (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 2.0 * 2.2));
        k.rp = WAIT_PITCH + 0.06F * (float) Math.sin(t * Math.PI * 2.0 * 2.2 + 0.6);
        k.ry = WAIT_YAW;
        k.rr = WAIT_ROLL + 0.05F * (float) Math.sin(t * Math.PI * 2.0 * 0.9);
        k.lp = 0.06F * (float) Math.sin(t * Math.PI * 2.0 * 0.9 + Math.PI);
        k.lr = -0.08F;

        float b = comeOn(t);
        if (b > 0.0F) {
            k.rp += 0.24F * b;
            k.lean += 0.06F * b;
            k.headRoll += 0.18F * b;
            k.headPitch -= 0.08F * b;
        }
        // Handfläche nach vorne, dazu ein leichtes Wackeln um die Armachse
        k.rtw = twistFor(k.rp, k.ry, k.rr, FORWARD) + 0.16F * (float) Math.sin(t * Math.PI * 2.0 * 3.3);
        return k;
    }

    /** 0..1..0 während des „Komm schon“-Winks. */
    public static float comeOn(float t) {
        if (t < COME_ON_FROM_S) return 0.0F;
        float cyc = (t - COME_ON_FROM_S) % COME_ON_PERIOD_S;
        if (cyc >= COME_ON_LEN_S) return 0.0F;
        float s = (float) Math.sin(Math.PI * cyc / COME_ON_LEN_S);
        return s * s;
    }

    /** Wartepose, in den ersten 0,3 s (leicht überschwingend) aus der Grundhaltung gehoben. */
    private static Key waitRaised(Ctx c) {
        float r = c.raiseT / RAISE_S;
        if (r >= 1.0F) return wait(c.t);
        return Key.lerp(rest(), wait(c.t), easeOutBack(r));
    }

    /** Ausholen: Arm hinten oben, rechte Schulter zurück, leicht in die Knie. */
    private static Key cock(Ctx c, boolean towardPartner) {
        Key k = new Key();
        k.lean = COCK_LEAN;
        k.turn = COCK_TURN + (towardPartner ? c.partnerTurn : 0.0F);
        k.dip = COCK_DIP;
        k.rp = COCK_PITCH;
        k.ry = COCK_YAW;
        k.rr = COCK_ROLL;
        k.rtw = twistFor(k.rp, k.ry, k.rr, FORWARD);
        k.lp = 0.4F;
        k.lr = -0.12F;
        k.headPitch = -0.06F;
        k.focus = 1.0F;
        return k;
    }

    /** Einschlagender vor dem Schwung: Arm direkt in die Ausholpose; ohne Partner nach 1 s wieder runter. */
    private static Key accepterPre(Ctx c) {
        Key cock = cock(c, false); // Partner-Drehung erst im Schwung: der Partner kann bei anderen Clients später erkannt werden
        cock.rp += 0.03F * (float) Math.sin(c.t * Math.PI * 2.0 * 5.0); // ungeduldiges Zittern
        float raise = easeInOut(c.t * 1000.0F / ACCEPT_RAISE_MS);
        Key k = Key.lerp(rest(), cock, raise);
        if (!c.paired && c.t > ACCEPT_GIVE_UP_S) {
            k = Key.lerp(k, rest(), easeInOut((c.t - ACCEPT_GIVE_UP_S) / ACCEPT_LOWER_S));
        }
        return k;
    }

    /** Körper beim Kontakt (Vorlehnen, Drehen, Ausfallschritt) – einmal pro Abtastung. */
    private static void contactBody(Ctx c) {
        Key k = new Key();
        k.lean = (float) Math.toRadians(HighFiveLogic.leanDeg(c.distance)) + CONTACT_EXTRA_LEAN;
        k.turn = c.partnerTurn - UNCOIL;
        k.dip = 0.2F;
        k.lp = -0.25F;
        k.lr = -0.15F;
        k.focus = 1.0F;

        double[] s1 = bodyPoint(k, c.in.rightShoulder, 0.0F);
        double vx = c.target[0] - s1[0], vy = c.target[1] - s1[1], vz = c.target[2] - s1[2];
        double vh = Math.hypot(vx, vz);
        double len = Math.sqrt(vh * vh + vy * vy);
        double lMin = Math.hypot(PALM_ALONG_PX * ARM_SCALE_MIN, PALM_INNER_PX);
        double lMax = Math.hypot(PALM_ALONG_PX * ARM_SCALE_MAX, PALM_INNER_PX);
        double want = clamp(len, lMin, lMax);
        if (want != len && want * want > vy * vy && vh > 1.0E-6) {
            double shift = clamp(vh - Math.sqrt(want * want - vy * vy), -SHIFT_BACK_MAX_PX, SHIFT_MAX_PX);
            k.shiftX = (float) (vx / vh * shift);
            k.shiftZ = (float) (vz / vh * shift);
        }
        // Handfläche zeigt waagerecht zur rechten Schulter des Partners (von der Ruhe-Schulter aus gemessen: der
        // Zielpunkt ist deren Mitte, daher zeigen beide Hände genau entgegengesetzt). Ohne Handgelenk liegt die
        // Normale senkrecht zum Arm; nur der waagerechte Anteil ist bei beiden exakt gegenläufig.
        double[] r0 = c.in.rightShoulder;
        double px = c.target[0] - r0[0], pz = c.target[2] - r0[2];
        double ph = Math.hypot(px, pz);
        c.palmDir = ph > 1.0E-6 ? new double[]{px / ph, 0.0, pz / ph} : FORWARD.clone();
        c.contactBody = k;
    }

    /** Kontaktpose mit Rückprall zur Zeit τ ≥ 0 (bei τ = 0 liegt die Handfläche genau auf dem Zielpunkt). */
    private static Key contact(Ctx c, double tau) {
        Key k = c.contactBody.copy();
        double recoil = 0.0;
        if (tau > 0.0) {
            recoil = RECOIL_PX * Math.sin(Math.PI * tau / 120.0) * Math.exp(-tau / 100.0);
            if (tau < RECOIL_MS) {
                // Wucht des Treffers: Oberkörper federt kurz nach
                k.lean += 0.06F * (float) (Math.sin(Math.PI * tau / RECOIL_MS) * (1.0 - tau / RECOIL_MS));
            }
        }
        double[] n = c.palmDir;
        double[] goal = {c.target[0] - n[0] * recoil, c.target[1], c.target[2] - n[2] * recoil};
        solveArm(bodyPoint(k, c.in.rightShoulder, 0.0F), goal, n, k);
        return k;
    }

    /** Faust-Pumpe nach unten vorne (mit kurzem Nachzittern). */
    private static Key pump(Ctx c, double tau) {
        Key k = new Key();
        k.lean = 0.06F;
        k.turn = c.partnerTurn * 0.6F;
        k.roll = -0.04F;
        k.shiftX = c.contactBody.shiftX * 0.4F;
        k.shiftZ = c.contactBody.shiftZ * 0.4F;
        k.rp = PUMP_PITCH;
        k.ry = 0.05F;
        k.rr = 0.25F;
        k.rtw = 0.0F;
        k.lp = -0.35F;
        k.lr = -0.2F;
        k.headPitch = -0.1F;
        k.headRoll = -0.08F;
        k.focus = 0.8F;
        if (tau > PUMP_AT_MS) {
            double d = tau - PUMP_AT_MS;
            k.rp += (float) (0.18 * Math.sin(d / 90.0 * Math.PI) * Math.exp(-d / 120.0));
        }
        return k;
    }

    private static float hop(double tau) {
        if (tau <= HOP_FROM_MS || tau >= HOP_TO_MS) return 0.0F;
        return HOP_PX * (float) Math.sin(Math.PI * (tau - HOP_FROM_MS) / (HOP_TO_MS - HOP_FROM_MS));
    }

    // ------------------------------------------------------------------
    // Aufbau der Pose
    // ------------------------------------------------------------------

    private static void build(Input in, Key k, float hop, Pose out) {
        double[] body = bodyPoint(k, in.bodyRest, hop);
        out.bodyX = (float) body[0];
        out.bodyY = (float) body[1];
        out.bodyZ = (float) body[2];
        out.bodyPitch = k.lean;
        out.bodyYaw = k.turn;
        out.bodyRoll = k.roll;

        double[] head = bodyPoint(k, in.headRest, hop);
        out.headX = (float) head[0];
        out.headY = (float) head[1];
        out.headZ = (float) head[2];
        out.headPitchAdd = k.headPitch;
        out.headRoll = k.headRoll;
        out.lean = k.lean;

        double[] r = bodyPoint(k, in.rightShoulder, hop);
        out.rArmX = (float) r[0];
        out.rArmY = (float) r[1];
        out.rArmZ = (float) r[2];
        out.rArmPitch = k.rp;
        out.rArmYaw = k.ry;
        out.rArmRoll = k.rr;
        out.rArmTwist = k.rtw;
        out.rArmScale = k.rs;

        double[] l = bodyPoint(k, in.leftShoulder, hop);
        out.lArmX = (float) l[0];
        out.lArmY = (float) l[1];
        out.lArmZ = (float) l[2];
        out.lArmPitch = k.lean + k.lp;
        out.lArmYaw = k.turn;
        out.lArmRoll = k.roll + k.lr;

        out.legDX = k.shiftX;
        out.legDY = -hop;
        out.legDZ = k.shiftZ;
        float step = (float) clamp(Math.hypot(k.shiftX, k.shiftZ) / 10.0, 0.0, 1.0);
        out.lLegPitch = -0.45F * step;
        out.rLegPitch = 0.3F * step;
        out.partnerFocus = (float) clamp(k.focus, 0.0, 1.0);
    }

    /** Punkt des Oberkörpers (Ruhelage {@code p0}) nach Drehung um die Hüfte, Einknicken, Hüpfer und Schritt. */
    private static double[] bodyPoint(Key k, double[] p0, float hop) {
        double[] v = {p0[0], p0[1] - HIP_Y_PX, p0[2]};
        v = rotX(v, k.lean);
        v = rotY(v, k.turn);
        v = rotZ(v, k.roll);
        return new double[]{v[0] + k.shiftX, v[1] + HIP_Y_PX + k.dip - hop, v[2] + k.shiftZ};
    }

    // ------------------------------------------------------------------
    // Arm: Ausrichtung auf den Zielpunkt
    // ------------------------------------------------------------------

    /**
     * Arm so ausrichten, dass die Handfläche (Innenseite, {@link #PALM_ALONG_PX} entlang des Arms) genau auf {@code goal}
     * liegt und ihre Normale möglichst in Richtung {@code palmDir} zeigt. Länge über {@code rs} (begrenzt).
     */
    private static void solveArm(double[] shoulder, double[] goal, double[] palmDir, Key k) {
        double[] n = palmDir.clone();
        float pitch = 0.0F, yaw = 0.0F, twist = 0.0F;
        double[] g = goal;
        for (int i = 0; i < 8; i++) {
            g = new double[]{goal[0] - n[0] * PALM_INNER_PX, goal[1] - n[1] * PALM_INNER_PX, goal[2] - n[2] * PALM_INNER_PX};
            float[] aim = HighFiveLogic.aimArm(shoulder, g);
            pitch = aim[0];
            yaw = aim[1];
            twist = twistFor(pitch, yaw, 0.0F, palmDir);
            n = palmNormal(pitch, yaw, 0.0F, twist);
        }
        double len = Math.sqrt(sq(g[0] - shoulder[0]) + sq(g[1] - shoulder[1]) + sq(g[2] - shoulder[2]));
        k.rp = pitch;
        k.ry = yaw;
        k.rr = 0.0F;
        k.rtw = twist;
        k.rs = (float) clamp(len / PALM_ALONG_PX, ARM_SCALE_MIN, ARM_SCALE_MAX);
    }

    /** Drehung um die Armachse, sodass die Innenseite (+X im Arm) möglichst in Richtung {@code dir} zeigt. */
    public static float twistFor(float pitch, float yaw, float roll, double[] dir) {
        double[] l = rotZ(dir, -roll);
        l = rotY(l, -yaw);
        l = rotX(l, -pitch);
        if (Math.abs(l[0]) < 1.0E-9 && Math.abs(l[2]) < 1.0E-9) return 0.0F;
        return (float) Math.atan2(-l[2], l[0]);
    }

    /** Normale der Handfläche im Modellraum: Rz·Ry·Rx·Ry(twist)·(1, 0, 0). */
    public static double[] palmNormal(float pitch, float yaw, float roll, float twist) {
        return armToModel(pitch, yaw, roll, twist, new double[]{1.0, 0.0, 0.0});
    }

    /** Handfläche der Pose im Modellraum (px). */
    public static double[] palmPoint(Pose p) {
        double[] v = armToModel(p.rArmPitch, p.rArmYaw, p.rArmRoll, p.rArmTwist,
                new double[]{PALM_INNER_PX, PALM_ALONG_PX * p.rArmScale, 0.0});
        return new double[]{p.rArmX + v[0], p.rArmY + v[1], p.rArmZ + v[2]};
    }

    private static double[] armToModel(float pitch, float yaw, float roll, float twist, double[] local) {
        double[] v = rotY(local, twist);
        v = rotX(v, pitch);
        v = rotY(v, yaw);
        return rotZ(v, roll);
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    static double[] rotX(double[] v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
    }

    static double[] rotY(double[] v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
    }

    static double[] rotZ(double[] v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2]};
    }

    public static float easeInOut(float x) {
        return PushUpMotion.easeInOut(x);
    }

    public static float easeOutCubic(float x) {
        x = (float) clamp(x, 0.0, 1.0);
        float i = 1.0F - x;
        return 1.0F - i * i * i;
    }

    /** Leicht überschwingend (ca. 10 %), endet genau bei 1. */
    public static float easeOutBack(float x) {
        x = (float) clamp(x, 0.0, 1.0);
        float c1 = 1.70158F, c3 = c1 + 1.0F;
        float y = x - 1.0F;
        return 1.0F + c3 * y * y * y + c1 * y * y;
    }

    private static float mix(float a, float b, float f) {
        return a + (b - a) * f;
    }

    private static double sq(double x) {
        return x * x;
    }

    private static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }
}

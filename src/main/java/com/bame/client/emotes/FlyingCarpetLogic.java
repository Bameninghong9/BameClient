package com.bame.client.emotes;

/**
 * Geometrie, Glättung und Verformung des fliegenden Teppichs (ohne Minecraft-Klassen, im flyingCarpetTest geprüft).
 * <p>
 * <b>Rein optisch:</b> Die Bewegung des Spielers bleibt unverändert (keine andere Geschwindigkeit, kein Schweben, kein
 * Fliegen, keine Pakete). Hier wird nur aus dem beobachteten Zustand (Körper-Yaw, Geschwindigkeit, Boden, Schleichen)
 * berechnet, wie Teppich und Reiter gezeichnet werden.
 * <p>
 * Rahmen F: Raum von {@code setupTransforms} (Blöcke bei Spieler-Skalierung 1, Füße im Ursprung, +X rechts, +Y oben,
 * +Z hinten, bezogen auf den aktuellen Körper-Yaw). In der Pose liegt der Teppich wie ein Läufer am Boden (Mitte bei
 * (0, GROUND_MID_Y, CENTER_Z)), der Spieler sitzt darauf; die Schwebehöhe (im Stand 1,2, in Fahrt bis 1,5, beim
 * Schleichen 0,9 Blöcke) kommt als Verschiebung der Render-Matrix dazu und gilt für Reiter und Teppich gemeinsam.
 * <p>
 * Teppich-Raum T (px des Modells, wie Bedrock +Y oben, +Z hinten): Ursprung in der Mitte des Teppichs auf halber Dicke
 * (Anker = Gelenk zwischen seg2 und seg3). Die 6 Segmente liegen quer zur Fahrtrichtung, seg0 vorne (−Z), jedes am
 * vorderen Ende mit dem vorherigen verbunden. Ihre Lage wird als Drehung um X im Rahmen angegeben (wie
 * {@code RotationAxis.POSITIVE_X}: positiv = hinteres Ende des Segments tiefer).
 */
public final class FlyingCarpetLogic {

    private FlyingCarpetLogic() {}

    /** Blöcke pro Teppich-px (echte Weltgröße bei Skalierung 1) bzw. pro Spielermodell-px. */
    public static final double PX = 1.0 / 16.0;
    public static final double K = HammockLogic.BLOCKS_PER_PX;

    // ------------------------------------------------------------------
    // Teppich (px)
    // ------------------------------------------------------------------

    public static final int SEGMENTS = 6;
    public static final double SEG_PX = 4.0;
    public static final double LENGTH_PX = SEGMENTS * SEG_PX;
    public static final double WIDTH_PX = 16.0;
    public static final double THICK_PX = 1.0;
    /** Index des Gelenks in der Mitte (vor seg3). */
    public static final int ANCHOR = 3;
    /** Quasten je kurzes Ende: Lage quer (Bedrock-x, px) und Länge ab der Kante (px). */
    public static final int TASSELS = 4;
    public static final double[] TASSEL_X = {-6.0, -2.0, 2.0, 6.0};
    public static final double TASSEL_LEN_PX = 3.0;

    // ------------------------------------------------------------------
    // Lage unter dem Spieler (Rahmen F, Blöcke)
    // ------------------------------------------------------------------

    /** In der Pose liegt der Teppich wie ein Läufer am Boden (Unterseite bei 0); der Spieler sitzt darauf. */
    public static final double GROUND_MID_Y = 0.5 * THICK_PX * PX;
    public static final double GROUND_TOP_Y = THICK_PX * PX;
    /** Mitte des Teppichs so weit hinter der Hüfte: Der Spieler sitzt etwas vor der Mitte, die Füße bleiben darauf. */
    public static final double CENTER_Z = 1.5 * PX;
    public static final double HALF_LENGTH = 0.5 * LENGTH_PX * PX;
    public static final double HALF_WIDTH = 0.5 * WIDTH_PX * PX;

    /**
     * Schwebehöhe der Unterseite über dem Boden (Blöcke; nur Render-Matrix): im Stand, in voller Fahrt (weich mit dem
     * Tempo steigend) und beim Schleichen. Beim erlaubten Fliegen dieselbe Höhe relativ zum Spieler. Vorschau niedriger,
     * damit alles im Rahmen bleibt.
     */
    public static final double ALT_IDLE = 1.2, ALT_FAST = 1.5, ALT_SNEAK = 0.9, PREVIEW_ALT = 0.45;
    /** Kritisch gedämpfte Feder für die Höhe (rad/s): Wechsel in ca. 0,8 s, ohne Überschwingen. */
    public static final float ALT_OMEGA = 5.0F;

    /** Schweben: ±0,04 Blöcke mit 0,5 Hz, in voller Fahrt und beim Schleichen nur noch zur Hälfte. */
    public static final double BOB = 0.04;
    public static final double BOB_HZ = 0.5;
    public static final double BOB_MOVING = 0.5, BOB_SNEAK = 0.5;

    // ------------------------------------------------------------------
    // Neigung (Grad) und Glättung
    // ------------------------------------------------------------------

    /** Kurven-Neigung: Grad pro Grad/s Körper-Drehung, höchstens ±15°; im Stand nur 40 % davon. */
    public static final float BANK_MAX = 15.0F;
    public static final float BANK_PER_DEG_S = 0.1F;
    public static final float BANK_STANDING = 0.4F;
    /** In voller Fahrt kippt die Vorderkante so weit nach unten. */
    public static final float NOSE_DOWN_MAX = 9.0F;
    /** Erlaubtes Fliegen: Steigen hebt die Vorderkante, Sinken senkt sie (Grad pro Block/Tick, höchstens ±12°). */
    public static final float CLIMB_DEG_PER_BT = 30.0F;
    public static final float CLIMB_MAX = 12.0F;
    public static final float NOSE_MIN = -18.0F;
    /** Kritisch gedämpfte Federn (rad/s): Neigung folgt in ca. 0,25 s, ohne Überschwingen. */
    public static final float BANK_OMEGA = 14.0F, NOSE_OMEGA = 10.0F;

    /** Sprint-Geschwindigkeit (Blöcke/Tick) = volle Fahrt. */
    public static final double SPRINT_SPEED = 0.28;
    public static final float TICK_S = 0.05F;
    /** Glättung pro Tick: Geschwindigkeit, Schleichen, Hand an der Vorderkante. */
    public static final float SPEED_ALPHA = 0.35F, SNEAK_ALPHA = 0.4F, HOLD_ALPHA = 0.2F;

    /** Fremde Spieler: Fliegen wird aus der Bewegung geschätzt (lange in der Luft und kein schneller Fall). */
    public static final int FLIGHT_AIR_TICKS = 14;
    public static final double FLIGHT_MAX_SINK = 0.5, FLIGHT_EXIT_SINK = 0.7;

    // ------------------------------------------------------------------
    // Verformung: Welle, Sprung, Quasten, Ein-/Ausrollen
    // ------------------------------------------------------------------

    /** Welle (Grad Neigung der Segmente) im Stand und in voller Fahrt; Frequenz (Hz); Schleichen beruhigt. */
    public static final float WAVE_IDLE_DEG = 3.5F, WAVE_FAST_DEG = 8.5F;
    public static final float WAVE_IDLE_HZ = 0.45F, WAVE_FAST_HZ = 1.6F;
    public static final float WAVE_SNEAK_AMP = 0.55F, WAVE_SNEAK_HZ = 0.7F;
    /** Phasenversatz pro Segment (rad): Die Welle läuft von vorne nach hinten. */
    public static final double WAVE_LAG = 0.95;
    /** Hinter dem Reiter frei, unter Beinen und Füßen nur ein leichtes Absenken (nie nach oben in den Reiter). */
    public static final float[] WAVE_ENV_BACK = {0.35F, 0.7F, 1.0F};
    public static final float FRONT_RIPPLE_IDLE = 1.2F, FRONT_RIPPLE_FAST = 1.8F;
    public static final float[] WAVE_ENV_FRONT = {0.0F, 0.35F, 0.8F};
    /** Vorderstes Segment kippt in voller Fahrt zusätzlich nach unten (Grad). */
    public static final float FRONT_DROOP_FAST = 3.0F;

    /** Sprung: beim Abheben kurzes Durchbiegen (Enden nach unten), beim Landen gedämpftes Nachfedern. */
    public static final float SAG_DEG = 9.0F, SAG_S = 0.32F;
    public static final float LAND_DEG = 12.0F, LAND_DECAY_S = 0.16F, LAND_PERIOD_S = 0.3F, LAND_S = 0.9F;
    public static final double JUMP_VY = 0.15;
    public static final float FLEX_MIN = -10.0F, FLEX_MAX = 14.0F;
    /** Gewicht des Durchbiegens je Schritt von der Mitte nach außen; vorne nur nach unten und schwächer (Füße). */
    public static final float[] FLEX_W = {0.35F, 0.75F, 1.0F};
    public static final float FLEX_FRONT = 0.4F;

    /** Quasten (Grad, Drehung um X im Rahmen): hängen im Stand, hinten wehen sie in voller Fahrt nach hinten. */
    public static final float TASSEL_HANG = 62.0F, TASSEL_BLOWN = 12.0F, TASSEL_FRONT_BLOWN = 135.0F;
    public static final float TASSEL_HZ_IDLE = 0.7F, TASSEL_HZ_FAST = 2.6F;

    /** Eingerollt: Knickwinkel je Gelenk von der Mitte nach außen (Grad, summiert); vorne leicht anders, damit sich die Rollen nicht decken. */
    public static final float[] CURL_BACK = {60.0F, 118.0F, 122.0F};
    public static final float[] CURL_FRONT = {57.0F, 116.0F, 124.0F};
    /** Ausrollen von der Mitte aus: Gelenk j streckt sich im Fortschritt [j·STEP, j·STEP + SPAN]. */
    public static final float CURL_STEP = 0.22F, CURL_SPAN = 0.56F;
    /** Größe des eingerollten Teppichs. */
    public static final float SCALE_ROLLED = 0.55F;

    /** Kein Ereignis (Sprung/Landung). */
    public static final long NO_EVENT = Long.MIN_VALUE / 4;

    // ------------------------------------------------------------------
    // Effekte (im Koordinator; hier, damit der Test ohne Minecraft-Klassen auskommt)
    // ------------------------------------------------------------------

    /** Partikel fremder Spieler bis 32 Blöcke, Geräusche bis 24 Blöcke. */
    public static final double FX_RANGE = 32.0, SOUND_RANGE = 24.0;
    /** Funkelspur: in Fahrt alle 3 Ticks (0,15 s) ein bis zwei Funken, im Stand selten einer. */
    public static final int TRAIL_EVERY = 3;
    public static final float TRAIL_FROM = 0.15F, IDLE_SPARK_CHANCE = 1.0F / 30.0F;
    /** Wind-Rauschen (Elytra-Flug, sehr leise und hoch): Lautstärke im Stand bzw. in voller Fahrt, Tonhöhe. */
    public static final float WIND_IDLE = 0.012F, WIND_FAST = 0.11F, WIND_PITCH = 1.55F, WIND_PITCH_FAST = 0.35F;
    /** Puff nur bei frischem Start (Emote-Zeit), nicht für Spieler, die schon länger fliegen. */
    public static final float PUFF_FRESH_S = 0.6F;

    // ------------------------------------------------------------------
    // Glättung pro Spieler (UUID; im Koordinator gespeichert)
    // ------------------------------------------------------------------

    /** Zustand eines Spielers mit Teppich; {@code prev*} = Wert vor dem letzten Tick (zum Interpolieren). */
    public static final class Track {
        /** Kurven-Neigung (Grad, Rz: negativ = rechte Seite unten) und ihre Geschwindigkeit. */
        public float bank, bankVel, prevBank;
        /** Kippen der Vorderkante (Grad, Rx: negativ = vorne unten). */
        public float nose, noseVel, prevNose;
        public float speed, prevSpeed;
        public float sneak, prevSneak;
        /** 0 = Hände auf den Knien, 1 = eine Hand an der Vorderkante. */
        public float hold, prevHold;
        /** Phasen von Welle und Quasten (rad, aufsummiert: stetig auch bei wechselnder Frequenz). */
        public double wave, prevWave;
        public double tassel, prevTassel;
        /** Schwebehöhe (Blöcke, ohne das Aufsteigen beim Start): folgt weich dem Ziel aus Fahrt und Schleichen. */
        public float alt = (float) ALT_IDLE, altVel, prevAlt = (float) ALT_IDLE;
        public int airTicks;
        public boolean onGround = true;
        public boolean flight;
        public double vy;
        public long takeoffMs = NO_EVENT, landMs = NO_EVENT;
        public float landAmp;
        public int steps;

        public void reset(double phase) {
            bank = bankVel = prevBank = 0.0F;
            nose = noseVel = prevNose = 0.0F;
            speed = prevSpeed = 0.0F;
            sneak = prevSneak = 0.0F;
            hold = prevHold = 0.0F;
            wave = prevWave = phase;
            tassel = prevTassel = phase * 0.5;
            alt = prevAlt = (float) ALT_IDLE;
            altVel = 0.0F;
            airTicks = 0;
            onGround = true;
            flight = false;
            vy = 0.0;
            takeoffMs = landMs = NO_EVENT;
            landAmp = 0.0F;
            steps = 0;
        }

        public float bank(float f) {
            return lerp(prevBank, bank, f);
        }

        public float nose(float f) {
            return lerp(prevNose, nose, f);
        }

        public float speed(float f) {
            return lerp(prevSpeed, speed, f);
        }

        public float sneak(float f) {
            return lerp(prevSneak, sneak, f);
        }

        public float hold(float f) {
            return lerp(prevHold, hold, f);
        }

        public double wave(float f) {
            return prevWave + (wave - prevWave) * f;
        }

        public double tassel(float f) {
            return prevTassel + (tassel - prevTassel) * f;
        }

        public float alt(float f) {
            return lerp(prevAlt, alt, f);
        }

        /** Durchbiegen (Grad) zur Zeit {@code nowMs} aus Abheben und Landung. */
        public float flex(long nowMs) {
            return FlyingCarpetLogic.flex(seconds(nowMs, takeoffMs), seconds(nowMs, landMs), landAmp);
        }

        private static double seconds(long now, long event) {
            return event == NO_EVENT ? -1.0 : (now - event) / 1000.0;
        }
    }

    /**
     * Ein Client-Tick (0,05 s) für einen Spieler mit Teppich. Nur Werte für die Darstellung; Eingaben werden gelesen,
     * nichts am Spieler wird verändert.
     *
     * @param yawRate     Änderung des Körper-Yaws (Grad/s, + = Rechtskurve)
     * @param hSpeed      waagerechte Geschwindigkeit (Blöcke/Tick, aus der Positionsänderung)
     * @param vy          senkrechte Geschwindigkeit (Blöcke/Tick)
     * @param flightKnown Flug ist bekannt (lokaler Spieler: vom Server erlaubtes Fliegen), sonst aus der Bewegung geschätzt
     * @param flightNow   nur mit {@code flightKnown}: fliegt gerade
     * @param ending      Teppich läuft aus (Neigung und Fahrt klingen ab)
     * @param nowMs       Zeitpunkt des Ticks (ms) für Abheben und Landung
     */
    public static void step(Track tr, float yawRate, double hSpeed, double vy, boolean onGround, boolean sneaking,
                            boolean flightKnown, boolean flightNow, boolean ending, long nowMs) {
        if (!Float.isFinite(yawRate)) yawRate = 0.0F;
        if (!Double.isFinite(hSpeed) || hSpeed < 0.0) hSpeed = 0.0;
        if (!Double.isFinite(vy)) vy = 0.0;
        tr.prevBank = tr.bank;
        tr.prevNose = tr.nose;
        tr.prevSpeed = tr.speed;
        tr.prevSneak = tr.sneak;
        tr.prevHold = tr.hold;
        tr.prevWave = tr.wave;
        tr.prevTassel = tr.tassel;
        tr.prevAlt = tr.alt;

        // Fahrt, Schleichen, Hand an der Vorderkante (weich)
        float sTarget = ending ? 0.0F : clamp01((float) (hSpeed / SPRINT_SPEED));
        tr.speed += (sTarget - tr.speed) * SPEED_ALPHA;
        tr.sneak += ((sneaking && !ending ? 1.0F : 0.0F) - tr.sneak) * SNEAK_ALPHA;
        float holdTarget = ending ? 0.0F : easeInOut((tr.speed - 0.1F) / 0.3F);
        tr.hold += (holdTarget - tr.hold) * HOLD_ALPHA;

        // Schwebehöhe: im Stand 1,2, mit dem Tempo bis 1,5, beim Schleichen 0,9 Blöcke (Feder, ohne Überschwingen).
        // Beim Auslaufen bleibt sie stehen (das Absinken rechnet FlyingCarpetMotion.outro)
        if (!ending) {
            float[] h = springStep(tr.alt, tr.altVel, hoverTarget(tr.speed, tr.sneak), ALT_OMEGA, TICK_S);
            tr.alt = h[0];
            tr.altVel = h[1];
        } else {
            tr.altVel = 0.0F;
        }

        // Erlaubtes Fliegen: lokal bekannt, bei fremden Spielern aus der Bewegung (lange in der Luft, kein Fall)
        boolean wasOnGround = tr.onGround;
        int airBefore = tr.airTicks;
        double lastVy = tr.vy;
        tr.airTicks = onGround ? 0 : Math.min(airBefore + 1, 10_000);
        if (flightKnown) {
            tr.flight = flightNow;
        } else if (onGround || vy < -FLIGHT_EXIT_SINK) {
            tr.flight = false;
        } else if (tr.airTicks >= FLIGHT_AIR_TICKS && vy > -FLIGHT_MAX_SINK) {
            tr.flight = true;
        }

        // Abheben und Landen (nicht beim Fliegen)
        if (!tr.flight && !ending) {
            if (wasOnGround && !onGround && vy > JUMP_VY) tr.takeoffMs = nowMs;
            if (!wasOnGround && onGround && airBefore >= 3) {
                double fall = -Math.min(vy, lastVy);
                tr.landMs = nowMs;
                tr.landAmp = clamp((float) ((fall - 0.1) / 0.6), 0.25F, 1.0F);
            }
        }
        tr.onGround = onGround;
        tr.vy = vy;

        // Kurven-Neigung aus der Drehgeschwindigkeit des Körpers (Rechtskurve: rechte Seite nach unten)
        float move = BANK_STANDING + (1.0F - BANK_STANDING) * tr.speed;
        float bankTarget = ending ? 0.0F : clamp(-BANK_PER_DEG_S * yawRate * move, -BANK_MAX, BANK_MAX);
        float[] b = springStep(tr.bank, tr.bankVel, bankTarget, BANK_OMEGA, TICK_S);
        tr.bank = clamp(b[0], -BANK_MAX, BANK_MAX);
        tr.bankVel = b[1];

        // Vorderkante: in Fahrt nach unten, beim erlaubten Fliegen zusätzlich mit dem Steigen bzw. Sinken
        float noseTarget = noseTarget(tr.speed, tr.flight, vy);
        if (ending) noseTarget = 0.0F;
        float[] n = springStep(tr.nose, tr.noseVel, noseTarget, NOSE_OMEGA, TICK_S);
        tr.nose = clamp(n[0], NOSE_MIN, CLIMB_MAX);
        tr.noseVel = n[1];

        // Phasen (aufsummiert, damit sich die Frequenz ohne Sprung ändern kann)
        tr.wave += 2.0 * Math.PI * waveHz(tr.speed, tr.sneak) * TICK_S;
        tr.tassel += 2.0 * Math.PI * tasselHz(tr.speed) * TICK_S;
        // Ganze Vielfache von 2π (auch für 0,8- und 2,3-fache Phasen) abziehen: gleiche Form, keine großen Zahlen
        double cut = 2.0 * Math.PI * 100.0;
        if (tr.wave > cut) {
            tr.wave -= cut;
            tr.prevWave -= cut;
        }
        if (tr.tassel > cut) {
            tr.tassel -= cut;
            tr.prevTassel -= cut;
        }
        tr.steps++;
    }

    /** Ziel der Schwebehöhe (Blöcke): steigt mit dem Tempo von 1,2 auf 1,5, beim Schleichen 0,9. */
    public static float hoverTarget(float speed, float sneak) {
        float a = (float) (ALT_IDLE + (ALT_FAST - ALT_IDLE) * clamp01(speed));
        return a + ((float) ALT_SNEAK - a) * clamp01(sneak);
    }

    /** Ziel für das Kippen der Vorderkante (Grad, Rx). */
    public static float noseTarget(float speed, boolean flight, double vy) {
        float t = -NOSE_DOWN_MAX * clamp01(speed);
        if (flight) t += clamp((float) (vy * CLIMB_DEG_PER_BT), -CLIMB_MAX, CLIMB_MAX);
        return clamp(t, NOSE_MIN, CLIMB_MAX);
    }

    /**
     * Exakter Schritt einer kritisch gedämpften Feder zum Ziel {@code target} (für die Dauer {@code dt} konstant):
     * {@code e(t) = (e0 + (v0 + ω·e0)·t)·e^(−ω·t)}. Ohne Überschwingen; bei beschränktem Ziel bleibt auch der Wert
     * beschränkt (gewichtetes Mittel früherer Ziele).
     *
     * @return {Wert, Geschwindigkeit}
     */
    public static float[] springStep(float x, float v, float target, float omega, float dt) {
        double e = x - target;
        double c = v + omega * e;
        double a = Math.exp(-omega * dt);
        double nx = target + (e + c * dt) * a;
        double nv = (v - omega * c * dt) * a;
        return new float[]{(float) nx, (float) nv};
    }

    public static float waveHz(float speed, float sneak) {
        float hz = WAVE_IDLE_HZ + (WAVE_FAST_HZ - WAVE_IDLE_HZ) * clamp01(speed);
        return hz * (1.0F - (1.0F - WAVE_SNEAK_HZ) * clamp01(sneak));
    }

    public static float waveAmp(float speed, float sneak) {
        float a = WAVE_IDLE_DEG + (WAVE_FAST_DEG - WAVE_IDLE_DEG) * clamp01(speed);
        return a * (1.0F - (1.0F - WAVE_SNEAK_AMP) * clamp01(sneak));
    }

    public static float tasselHz(float speed) {
        return TASSEL_HZ_IDLE + (TASSEL_HZ_FAST - TASSEL_HZ_IDLE) * clamp01(speed);
    }

    /**
     * Durchbiegen der Enden (Grad, + = Enden nach unten) aus der Zeit seit dem Abheben bzw. der Landung (s, negativ =
     * kein Ereignis): kurzes Durchwellen beim Abheben, gedämpftes Nachfedern nach der Landung. Beginnt und endet bei 0.
     */
    public static float flex(double sinceTakeoff, double sinceLand, float landAmp) {
        double f = 0.0;
        if (sinceTakeoff >= 0.0 && sinceTakeoff < SAG_S) f += SAG_DEG * Math.sin(Math.PI * sinceTakeoff / SAG_S);
        if (sinceLand >= 0.0 && sinceLand < LAND_S) {
            f += LAND_DEG * landAmp * Math.exp(-sinceLand / LAND_DECAY_S) * Math.sin(2.0 * Math.PI * sinceLand / LAND_PERIOD_S);
        }
        return clamp((float) f, FLEX_MIN, FLEX_MAX);
    }

    // ------------------------------------------------------------------
    // Form des Teppichs
    // ------------------------------------------------------------------

    /** Form des Teppichs (wiederverwendbar). */
    public static final class Shape {
        /** Drehung jedes Segments um X im Rahmen (Grad; positiv = hinteres Ende des Segments tiefer). */
        public final float[] seg = new float[SEGMENTS];
        /** Gelenke J0..J6 im Teppich-Raum (px): Höhe (+ oben) und Lage entlang Z (+ hinten); J3 = (0, 0). */
        public final double[] jy = new double[SEGMENTS + 1], jz = new double[SEGMENTS + 1];
        /** Quasten: Drehung um X relativ zum Segment, an dem sie hängen (Grad), und im Rahmen. */
        public final float[] front = new float[TASSELS], back = new float[TASSELS];
        public final float[] frontAbs = new float[TASSELS], backAbs = new float[TASSELS];
        /** Größe 0..1 und Ausrollen 0..1 (1 = flach). */
        public float scale = 1.0F, unroll = 1.0F;
    }

    /** Größe des Teppichs beim Ausrollen {@code unroll} (eingerollt {@link #SCALE_ROLLED}, flach 1). */
    public static float scaleAt(float unroll) {
        return (float) (SCALE_ROLLED + (1.0 - SCALE_ROLLED) * WitchBroomLogic.smoother(clamp01(unroll)));
    }

    /** Anteil der Einrollung von Gelenk {@code j} (0 = Mitte, 2 = Rand) beim Fortschritt {@code unroll}: 1 = eingerollt. */
    public static float curl(float unroll, int j) {
        return 1.0F - (float) WitchBroomLogic.smoother((unroll - j * CURL_STEP) / CURL_SPAN);
    }

    /**
     * Form des Teppichs.
     *
     * @param unroll 0 = eingerollt, 1 = flach (von der Mitte aus ausgerollt bzw. zur Mitte eingerollt)
     * @param dyn    0..1: Anteil der Bewegung (Welle, Durchbiegen, Hängen und Wehen der Quasten); am Boden 0
     * @param wave   Phase der Welle (rad)
     * @param tassel Phase der Quasten (rad)
     * @param flex   Durchbiegen der Enden (Grad, + = nach unten), siehe {@link #flex}
     */
    public static void shape(float unroll, float dyn, float speed, float sneak, double wave, double tassel, float flex,
                             Shape out) {
        unroll = clamp01(unroll);
        dyn = clamp01(dyn);
        speed = clamp01(speed);
        sneak = clamp01(sneak);
        if (!Double.isFinite(wave)) wave = 0.0;
        if (!Double.isFinite(tassel)) tassel = 0.0;
        if (!Float.isFinite(flex)) flex = 0.0F;
        out.unroll = unroll;
        out.scale = scaleAt(unroll);

        float amp = waveAmp(speed, sneak) * dyn;
        float ripple = (FRONT_RIPPLE_IDLE + FRONT_RIPPLE_FAST * speed) * (1.0F - 0.45F * sneak) * dyn;
        float fl = flex * dyn;
        // Einrollen: Knickwinkel der Gelenke summieren sich von der Mitte nach außen; die Enden rollen sich unter den
        // Teppich (am Boden in den Boden hinein, nie nach oben in den Reiter)
        double curlBack = 0.0, curlFront = 0.0;
        for (int j = 0; j < 3; j++) {
            float c = curl(unroll, j);
            curlBack += CURL_BACK[j] * c;
            curlFront += CURL_FRONT[j] * c;
            // hinten (seg3..seg5): Welle läuft nach außen, Enden biegen sich beim Sprung durch
            double aBack = -curlBack + amp * WAVE_ENV_BACK[j] * Math.sin(wave - j * WAVE_LAG) - fl * FLEX_W[j];
            // vorne (seg2..seg0): unter Beinen und Füßen nur nach unten
            double droop = ripple * WAVE_ENV_FRONT[j] * (0.5 + 0.5 * Math.sin(wave + 1.3 + j * WAVE_LAG))
                    + Math.max(fl, 0.0F) * FLEX_W[j] * FLEX_FRONT
                    + (j == 2 ? FRONT_DROOP_FAST * speed * dyn : 0.0F);
            double aFront = -curlFront - droop;
            out.seg[2 - j] = (float) aFront;
            out.seg[3 + j] = (float) -aBack;
        }

        // Gelenke vom Anker aus (Segment k läuft von J_k nach J_{k+1} in Richtung Rx(seg_k)·(0, 0, 1))
        out.jy[ANCHOR] = 0.0;
        out.jz[ANCHOR] = 0.0;
        for (int k = ANCHOR; k < SEGMENTS; k++) {
            double a = Math.toRadians(out.seg[k]);
            out.jy[k + 1] = out.jy[k] - SEG_PX * Math.sin(a);
            out.jz[k + 1] = out.jz[k] + SEG_PX * Math.cos(a);
        }
        for (int k = ANCHOR - 1; k >= 0; k--) {
            double a = Math.toRadians(out.seg[k]);
            out.jy[k] = out.jy[k + 1] + SEG_PX * Math.sin(a);
            out.jz[k] = out.jz[k + 1] - SEG_PX * Math.cos(a);
        }

        // Quasten: hängen (Schwerkraft im Rahmen), hinten wehen sie mit der Fahrt nach hinten; am Boden (dyn = 0)
        // liegen sie flach neben dem Teppich statt in den Boden zu hängen
        float blow = easeInOut(speed) * dyn;
        float hang = TASSEL_HANG * dyn;
        for (int i = 0; i < TASSELS; i++) {
            double flB = ((2.5 + 9.0 * speed) * Math.sin(tassel + 1.1 * i) + 2.0 * Math.sin(2.3 * tassel + 0.7 * i)) * dyn;
            float bAbs = (float) (hang + (TASSEL_BLOWN - hang) * blow + flB);
            double flF = (1.5 + 3.0 * speed) * Math.sin(0.8 * tassel + 0.9 * i) * dyn;
            float fAbs = (float) (-(hang + (TASSEL_FRONT_BLOWN - hang) * blow) + flF);
            out.backAbs[i] = bAbs;
            out.frontAbs[i] = fAbs;
            out.back[i] = bAbs - out.seg[SEGMENTS - 1];
            out.front[i] = fAbs - out.seg[0];
        }
    }

    // ------------------------------------------------------------------
    // Neigung des ganzen Teppichs samt Reiter (Render-Matrix)
    // ------------------------------------------------------------------

    /**
     * Transformation in {@code setupTransforms}: {@code Tr(0, dy, 0) · Tr(P) · Rz(bank) · Rx(nose) · Tr(−P)} mit dem
     * Drehpunkt P in der Mitte des (in der Pose am Boden liegenden) Teppichs; {@code dy} = Schwebehöhe + Schweben.
     */
    public static final class Tilt {
        public double dy, pivotY, pivotZ;
        /** Angewendete Schwebehöhe ohne Schweben (Blöcke). */
        public double alt;
        public float bank, nose;
    }

    /** Punkt im Rahmen (Ruhelage) nach der Neigung von {@code tl}. */
    public static double[] tiltPoint(Tilt tl, double x, double y, double z, double[] out) {
        double[] v = {x, y - tl.pivotY, z - tl.pivotZ};
        WitchBroomLogic.rotX(v, tl.nose);
        WitchBroomLogic.rotZ(v, tl.bank);
        out[0] = v[0];
        out[1] = v[1] + tl.pivotY + tl.dy;
        out[2] = v[2] + tl.pivotZ;
        return out;
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
}

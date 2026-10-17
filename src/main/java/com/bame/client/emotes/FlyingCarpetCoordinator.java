package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.module.EmoteModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Fliegender Teppich (nur Darstellung auf dem Client). <b>Kein echtes Fliegen:</b> Bewegung, Geschwindigkeit, Position
 * und Hitbox des Spielers bleiben unverändert, es gehen keine Pakete an den Minecraft-Server; Teppich und Reiter werden
 * nur höher gezeichnet (Render-Matrix), der Vanilla-Schatten bleibt am Boden. Fliegen ist nur sichtbar, wenn der Server
 * es selbst erlaubt (Kreativmodus, {@code /fly}); das Emote schaltet es nie ein. Keine eigenen Sync-Nachrichten (nur das
 * vorhandene {@code emote} PLAY/STOP).
 * <ul>
 *   <li>Pro Spieler (UUID) eine Glättung ({@link FlyingCarpetLogic.Track}): Schwebehöhe (1,2 im Stand, bis 1,5 in
 *       Fahrt, 0,9 beim Schleichen), Kurven-Neigung aus der Drehgeschwindigkeit des Körpers, Kippen nach
 *       Geschwindigkeit bzw. Steigen/Sinken beim erlaubten Fliegen, Schleichen, Sprung und Landung. Einmal pro Tick
 *       aktualisiert, beim Zeichnen zwischen den Ticks interpoliert.</li>
 *   <li>Was mit der Höhe mitgeht (nur Darstellung): Nametag ({@link #liftNameLabel}), Drehpunkt der eigenen
 *       3rd-Person-Kamera ({@link #localHeadLift}), Sichtbarkeits-Box ({@link #cullBox}), Partikel am Teppich.</li>
 *   <li>Effekte nur bis 32 Blöcke (Geräusche bis 24): goldener Funkel-„Puff“ mit magischem „Pling“ beim Start,
 *       Funkeln beim Aufsteigen, Funkelspur hinten am Teppich (in Fahrt alle 0,15 s ein bis zwei Funken, im Stand
 *       selten einer), leises Wind-Rauschen als Schleife (lauter mit der Geschwindigkeit), Funkeln beim
 *       Verschwinden.</li>
 *   <li>Eigener Teppich endet von selbst beim Schwimmen bzw. mit dem Kopf unter Wasser, in Lava, an Leiter oder Ranken,
 *       beim Reiten, Schlafen, Elytra-Gleiten und im Zuschauermodus.</li>
 * </ul>
 */
public final class FlyingCarpetCoordinator {

    private FlyingCarpetCoordinator() {}

    /** Reichweiten, Takt der Funkelspur und Wind-Lautstärke stehen in {@link FlyingCarpetLogic} (ohne Minecraft getestet). */
    private static final double FX_RANGE = FlyingCarpetLogic.FX_RANGE, SOUND_RANGE = FlyingCarpetLogic.SOUND_RANGE;
    private static final int TRAIL_EVERY = FlyingCarpetLogic.TRAIL_EVERY;
    private static final float TRAIL_FROM = FlyingCarpetLogic.TRAIL_FROM, IDLE_SPARK_CHANCE = FlyingCarpetLogic.IDLE_SPARK_CHANCE;
    private static final float WIND_IDLE = FlyingCarpetLogic.WIND_IDLE, WIND_FAST = FlyingCarpetLogic.WIND_FAST;
    private static final float WIND_PITCH = FlyingCarpetLogic.WIND_PITCH, WIND_PITCH_FAST = FlyingCarpetLogic.WIND_PITCH_FAST;
    private static final float PUFF_FRESH_S = FlyingCarpetLogic.PUFF_FRESH_S;
    /** Ego-Perspektive verlassen: Begrenzung der Höhe klingt pro Tick um diesen Anteil ab (ca. 0,3 s). */
    private static final float FP_RELEASE = 0.3F;
    /** Vanilla-Spieler: Höhe im Stand (Nametag sitzt darüber) und Versatz beim Schleichen (Blöcke bei Skalierung 1). */
    private static final double STANDING_HEIGHT = 1.8, VANILLA_SNEAK_OFFSET = 2.0 / 16.0;

    private static final int GOLD = 0xFFC83A, GOLD_DEEP = 0xE8A317, CREAM = 0xFFF1B8;

    private static final class State {
        final FlyingCarpetLogic.Track track = new FlyingCarpetLogic.Track();
        long startMs;
        /** Beginn des Auslaufens (lokale ms), 0 = Teppich aktiv. */
        long outroMs;
        /** Zuletzt gezeichnet bzw. im Tick berechnet: Emote-Zeit, Verschiebung (Schwebehöhe samt Schweben), Neigungsanteil. */
        float lastT, lastW;
        double lastDy;
        /** Beim Ende gemerkt (stetiges Auslaufen ab genau der zuletzt gezeigten Lage). */
        float endT, endW;
        double endDy;
        boolean puffed, sparkled;
        /** Auslaufen abgebrochen (anderes Emote, Vanilla-Pose): Teppich verschwindet sofort mit Funkeln. */
        boolean cut;
        /** Vanilla-Pose (Gleiten, Schwimmen, Reiten ...) dreht das Modell: dann kein Auslaufen im Modellraum. */
        boolean plainPose = true;
        /** Eigener Spieler in der Ego-Perspektive (0..1, interpoliert): Schwebehöhe begrenzen. */
        float fp, prevFp;
        /** Beim Auslaufen: Schlag bzw. Item-Benutzung (0..1, weich) – dann zeigen die Arme die Vanilla-Bewegung. */
        float arm, prevArm;
        WindSound sound;
        int seen, soundTick;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Random RNG = new Random();
    private static ClientWorld lastWorld;
    private static long tickNs = System.nanoTime();
    private static int tickNo;

    // ------------------------------------------------------------------
    // Werte für das Zeichnen
    // ------------------------------------------------------------------

    /** Interpolierte Werte eines Spielers mit Teppich (wiederverwendbar, nur Render-Thread). */
    public static final class View {
        public boolean preview;
        public float t, speed, sneak, hold, bank, nose, flex;
        /** Geglättete Schwebehöhe (ohne Aufsteigen) und Anteil der Ego-Perspektive. */
        public float alt, fp;
        public double wave, tassel;
        private State st;
    }

    private static final View VIEW = new View();
    private static final FlyingCarpetMotion.Pose TL_POSE = new FlyingCarpetMotion.Pose();
    private static final FlyingCarpetLogic.Tilt LIFT_TILT = new FlyingCarpetLogic.Tilt();

    /** Anteil der Zeit seit dem letzten Tick (0..1) zum Interpolieren. */
    private static float frac() {
        float f = (System.nanoTime() - tickNs) / 5.0E7F;
        return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
    }

    /** Werte für einen Render-State mit laufendem FLYING_CARPET (Vorschau: eigene, ruhige Bewegung). */
    public static View view(PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active) {
        if (active != null && active.preview()) {
            // Menü und Emote-Rad: sanftes Wellen und Neigen im Wechsel, etwas niedriger, keine Partikel und Geräusche
            View v = VIEW;
            float t = active.t();
            v.t = t;
            v.preview = true;
            v.st = null;
            v.speed = FlyingCarpetMotion.PREVIEW_SPEED;
            v.sneak = 0.0F;
            v.hold = 0.0F;
            v.bank = FlyingCarpetMotion.previewBank(t);
            v.nose = FlyingCarpetMotion.PREVIEW_NOSE;
            v.flex = 0.0F;
            v.alt = (float) FlyingCarpetLogic.PREVIEW_ALT;
            v.fp = 0.0F;
            v.wave = FlyingCarpetMotion.previewWave(t);
            v.tassel = FlyingCarpetMotion.previewTassel(t);
            return v;
        }
        UUID uuid = state != null ? CaeserPlayerSyncManager.getRenderStateUuid(state) : null;
        return viewFor(uuid, active);
    }

    private static View viewFor(UUID uuid, EmoteAnimator.ActiveEmote active) {
        View v = VIEW;
        float t = active != null ? active.t() : 0.0F;
        v.t = t;
        v.preview = false;
        State st = uuid != null ? STATES.get(uuid) : null;
        if (st == null || active == null || st.startMs != active.startMs() || st.track.steps == 0) {
            // Noch kein Tick für diesen Start (höchstens 50 ms): Ausgangswerte der Glättung, damit der erste Tick
            // ohne Sprung anschließt
            v.st = st != null && active != null && st.startMs == active.startMs() ? st : null;
            v.speed = 0.0F;
            v.sneak = 0.0F;
            v.hold = 0.0F;
            v.bank = 0.0F;
            v.nose = 0.0F;
            v.flex = 0.0F;
            v.alt = (float) FlyingCarpetLogic.ALT_IDLE;
            v.fp = st != null ? st.fp : 0.0F;
            v.wave = initialPhase(t);
            v.tassel = initialPhase(t) * 0.5;
            return v;
        }
        v.st = st;
        float f = frac();
        FlyingCarpetLogic.Track tr = st.track;
        v.speed = tr.speed(f);
        v.sneak = tr.sneak(f);
        v.hold = tr.hold(f);
        v.bank = tr.bank(f);
        v.nose = tr.nose(f);
        v.wave = tr.wave(f);
        v.tassel = tr.tassel(f);
        v.alt = tr.alt(f);
        v.fp = FlyingCarpetLogic.lerp(st.prevFp, st.fp, f);
        v.flex = tr.flex(System.currentTimeMillis());
        return v;
    }

    /** Phase der Welle bei Spielern, die schon fliegen, wenn sie erstmals gesehen werden. */
    private static double initialPhase(float t) {
        return (2.0 * Math.PI * FlyingCarpetLogic.WAVE_IDLE_HZ * t) % (2.0 * Math.PI * 100.0);
    }

    /** Schwebehöhe (mit Aufsteigen und Begrenzung in der Ego-Perspektive) und Neigung aus interpolierten Werten. */
    private static void applied(View v, FlyingCarpetLogic.Tilt out) {
        FlyingCarpetMotion.timeline(v.t, TL_POSE);
        double a = FlyingCarpetMotion.altitude(v.t, v.alt, v.sneak, v.fp, TL_POSE);
        FlyingCarpetMotion.tilt(v.t, v.speed, v.sneak, v.bank, v.nose, a, out);
    }

    /**
     * Neigung samt Schwebehöhe für einen Render-State mit laufendem Teppich (Render-Matrix im Animator). Merkt sich die
     * gezeigte Lage, damit das Auslaufen genau dort beginnt.
     */
    public static View tilt(PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active, FlyingCarpetLogic.Tilt out) {
        View v = view(state, active);
        applied(v, out);
        remember(v, out);
        return v;
    }

    private static void remember(View v, FlyingCarpetLogic.Tilt tl) {
        State st = v.st;
        if (st == null || v.preview || st.outroMs != 0L) return;
        st.lastT = v.t;
        st.lastDy = tl.dy;
        st.lastW = FlyingCarpetMotion.rise(v.t);
    }

    // ------------------------------------------------------------------
    // Auslaufen nach dem Ende (Animator, Renderer)
    // ------------------------------------------------------------------

    /** Auslaufen eines Spielers: Alter (s), Lage beim Ende, abklingende Glättung und daraus der Ablauf. */
    public static final class OutroView {
        public float age, tEnd, w0;
        public double dy0;
        public float bank, nose, speed, sneak, hold, flex;
        /** Anteil der Vanilla-Arme (Schlag, Item-Benutzung). */
        public float arm;
        public double wave, tassel;
        public final FlyingCarpetMotion.Outro motion = new FlyingCarpetMotion.Outro();
    }

    private static final OutroView OUTRO_VIEW = new OutroView();

    /**
     * Auslaufen für einen Render-State ohne Teppich-Emote oder {@code null} (ohne Teppich-Spieler sofort). Beginnt im
     * ersten Bild ohne Emote (nicht erst im nächsten Tick), damit nichts springt. Nur für echte Spieler, nie für
     * Vorschauen; läuft schon ein anderes Emote, endet der Teppich sofort (mit Funkeln im nächsten Tick).
     */
    public static OutroView outro(PlayerEntityRenderState state) {
        if (STATES.isEmpty() || state == null) return null;
        if (state instanceof EmotePreviewHolder holder && holder.bame$getPreviewEmote() != null) return null;
        UUID uuid = CaeserPlayerSyncManager.getRenderStateUuid(state);
        State st = uuid != null ? STATES.get(uuid) : null;
        if (st == null) return null;
        if (EmoteAnimator.resolveActiveEmote(state) != null) {
            if (st.outroMs != 0L) st.cut = true;
            return null;
        }
        return outroOf(st);
    }

    private static OutroView outroOf(State st) {
        if (st.cut || !st.plainPose) return null;
        long now = System.currentTimeMillis();
        if (st.outroMs == 0L) beginOutro(st, now);
        OutroView o = OUTRO_VIEW;
        o.age = (now - st.outroMs) / 1000.0F;
        if (o.age >= FlyingCarpetMotion.OUTRO) return null;
        o.tEnd = st.endT;
        o.dy0 = st.endDy;
        o.w0 = st.endW;
        float f = frac();
        FlyingCarpetLogic.Track tr = st.track;
        o.bank = tr.bank(f);
        o.nose = tr.nose(f);
        o.speed = tr.speed(f);
        o.sneak = tr.sneak(f);
        o.hold = tr.hold(f);
        o.wave = tr.wave(f);
        o.tassel = tr.tassel(f);
        o.flex = tr.flex(now);
        o.arm = FlyingCarpetLogic.lerp(st.prevArm, st.arm, f);
        FlyingCarpetMotion.outro(o.age, o.tEnd, o.motion);
        return o;
    }

    private static void beginOutro(State st, long nowMs) {
        st.outroMs = nowMs;
        st.endT = st.lastT;
        st.endDy = st.lastDy;
        st.endW = st.lastW;
        if (st.sound != null) st.sound = null; // Wind endet (die Schleife beendet sich im nächsten Sound-Tick)
    }

    // ------------------------------------------------------------------
    // Was mit der Höhe mitgeht: Nametag, Kamera
    // ------------------------------------------------------------------

    private static final FlyingCarpetMotion.Pose LIFT_POSE = new FlyingCarpetMotion.Pose();

    /**
     * Anhebung des Kopfes (Blöcke bei Skalierung 1, ≥ 0) für einen Spieler mit laufendem oder auslaufendem Teppich, sonst
     * {@code NaN}; {@code riderOut[0]} = Anteil der Teppich-Pose.
     */
    private static double headLift(UUID uuid, EmoteAnimator.ActiveEmote active, float[] riderOut) {
        State st = uuid != null ? STATES.get(uuid) : null;
        if (st == null) return Double.NaN;
        if (active != null) {
            if (active.emote() != EmoteType.FLYING_CARPET || active.preview()) return Double.NaN;
            View v = viewFor(uuid, active);
            applied(v, LIFT_TILT);
            riderOut[0] = 1.0F;
            return FlyingCarpetMotion.headLift(LIFT_TILT.dy, TL_POSE.hipY, 1.0F);
        }
        OutroView o = outroOf(st);
        if (o == null) return Double.NaN;
        FlyingCarpetMotion.outroTilt(o.motion, o.dy0, o.w0, o.bank, o.nose, LIFT_TILT);
        riderOut[0] = o.motion.rider;
        return FlyingCarpetMotion.headLift(LIFT_TILT.dy, FlyingCarpetMotion.outroSeat(o.motion, LIFT_POSE), o.motion.rider);
    }

    private static final float[] RIDER = new float[1];

    /**
     * Nametag (aus dem Renderer-Mixin nach {@code updateRenderState}): solange der Teppich den Spieler höher zeichnet,
     * sitzt der Name um dieselbe Anhebung höher über dem Kopf statt im Körper (eigener und fremde Spieler). Nur der
     * Render-State wird geändert.
     */
    public static void liftNameLabel(PlayerEntityRenderState state) {
        if (STATES.isEmpty() || state == null || state.nameLabelPos == null) return;
        if (!EmoteAnimator.hammockTransformApplies(state)) return;
        if (state instanceof EmotePreviewHolder holder && holder.bame$getPreviewEmote() != null) return;
        EmoteAnimator.ActiveEmote active = EmoteAnimator.resolveActiveEmote(state);
        if (active == null && outro(state) == null) return;
        double lift = headLift(CaeserPlayerSyncManager.getRenderStateUuid(state), active, RIDER);
        if (Double.isNaN(lift)) return;
        double scale = state.baseScale > 0.0F ? state.baseScale : 1.0;
        double up = lift * scale;
        // Schleichen: Vanilla senkt den Namen ab (geduckte Hitbox, 2 px tiefer gezeichnet); der Reiter duckt sich nicht so
        if (state.isInSneakingPose) {
            up += (Math.max(0.0, STANDING_HEIGHT * scale - state.height) + VANILLA_SNEAK_OFFSET * scale) * RIDER[0];
        }
        if (up > 1.0E-4) state.nameLabelPos = state.nameLabelPos.add(0.0, up, 0.0);
    }

    /**
     * Drehpunkt der eigenen 3rd-Person-Kamera ({@code EmoteCamera}): so weit liegt der Kopf des Reiters über seiner
     * normalen Höhe (Blöcke in der Welt, ≥ 0), solange der Teppich läuft bzw. ausläuft; sonst 0.
     */
    public static double localHeadLift(PlayerEntity self) {
        if (STATES.isEmpty() || self == null) return 0.0;
        if (self.isGliding() || self.isInSwimmingPose() || self.hasVehicle() || self.isSleeping()) return 0.0;
        UUID uuid = self.getUuid();
        double lift = headLift(uuid, EmoteAnimator.resolveActiveEmote(uuid, true), RIDER);
        return Double.isNaN(lift) ? 0.0 : lift * self.getScale();
    }

    // ------------------------------------------------------------------
    // Start des lokalen Spielers
    // ------------------------------------------------------------------

    /**
     * Starten nur an Land oder beim vom Server erlaubten Fliegen; nicht im Wasser, in Lava, an Leiter/Ranken, beim
     * Reiten, Schlafen, Gleiten oder im Zuschauermodus. Liest den Spieler nur.
     */
    public static boolean canStart(ClientPlayerEntity p) {
        if (p == null || mustEnd(p) || p.isTouchingWater()) return false;
        return p.isOnGround() || p.getAbilities().flying;
    }

    /** Endet der eigene Teppich von selbst? (Schwimmen bzw. Kopf unter Wasser, Lava, Leiter/Ranken, Reiten, Schlafen,
     * Elytra-Gleiten, Zuschauer.) */
    public static boolean mustEnd(ClientPlayerEntity p) {
        return p.isSwimming() || p.isSubmergedInWater() || p.isInLava() || p.isClimbing() || p.hasVehicle()
                || p.isSleeping() || p.isGliding() || p.isSpectator();
    }

    /** Beim eigenen Start sofort frisch beginnen (Glättung zurücksetzen, laufendes Auslaufen abbrechen). */
    public static void onLocalStart(ClientPlayerEntity p, long startMs) {
        if (p == null || p.getUuid() == null) return;
        State st = STATES.computeIfAbsent(p.getUuid(), k -> new State());
        begin(st, startMs, 0.0F);
        st.seen = tickNo;
    }

    private static void begin(State st, long startMs, float t) {
        st.startMs = startMs;
        st.outroMs = 0L;
        st.puffed = t >= PUFF_FRESH_S;
        st.sparkled = false;
        st.cut = false;
        st.plainPose = true;
        st.lastT = t;
        st.lastDy = 0.0;
        st.lastW = 0.0F;
        st.arm = st.prevArm = 0.0F;
        st.track.reset(initialPhase(t));
    }

    // ------------------------------------------------------------------
    // Sichtbarkeit
    // ------------------------------------------------------------------

    /**
     * Sichtbarkeits-Box mit dem Teppich (ragt seitlich über die Hitbox hinaus) und dem höher gezeichneten Reiter (bis
     * 1,5 Blöcke Schwebehöhe samt Schweben und Neigung); {@code null} ohne Teppich.
     */
    public static Box cullBox(PlayerEntity player) {
        if (STATES.isEmpty() || player == null || !STATES.containsKey(player.getUuid())) return null;
        double s = player.getScale();
        double r = 0.95 * s;
        return player.getBoundingBox().expand(r, 0.1, r).stretch(0.0, (FlyingCarpetLogic.ALT_FAST + 0.3) * s, 0.0);
    }

    // ------------------------------------------------------------------
    // Tick: Glättung, Ende, Effekte
    // ------------------------------------------------------------------

    private static final FlyingCarpetLogic.Tilt FX_TILT = new FlyingCarpetLogic.Tilt();
    private static final FlyingCarpetMotion.Outro FX_OUTRO = new FlyingCarpetMotion.Outro();
    private static final double[] FX_F = new double[3];

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            clearAll();
            lastWorld = world;
        }
        ClientPlayerEntity self = client.player;
        if (world == null || self == null) return;

        // Eigener Teppich endet von selbst (Wasser, Lava, Leiter, Reiten, Schlafen, Elytra, Zuschauer)
        if (EmoteModule.isEmoting() && EmoteModule.getCurrentEmote() == EmoteType.FLYING_CARPET && mustEnd(self)) {
            EmoteModule.stopEmote();
        }
        if (STATES.isEmpty() && !anyCarpet(self, world)) return;

        tickNs = System.nanoTime();
        tickNo++;
        long nowMs = System.currentTimeMillis();
        ParticlesMode mode = client.options != null ? client.options.getParticles().getValue() : ParticlesMode.ALL;
        boolean firstPerson = client.options != null && client.options.getPerspective().isFirstPerson();
        for (PlayerEntity p : world.getPlayers()) {
            UUID uuid = p.getUuid();
            if (uuid == null) continue;
            boolean isSelf = p == self;
            EmoteAnimator.ActiveEmote e = EmoteAnimator.resolveActiveEmote(uuid, isSelf);
            boolean carpet = e != null && e.emote() == EmoteType.FLYING_CARPET;
            State st = STATES.get(uuid);
            if (!carpet && st == null) continue;
            if (st == null) {
                st = new State();
                STATES.put(uuid, st);
                begin(st, e.startMs(), e.t());
            } else if (carpet && st.startMs != e.startMs()) {
                begin(st, e.startMs(), e.t());
            } else if (carpet && st.outroMs != 0L) {
                // Kurz kein Emote gemeldet (z. B. Besitzprüfung im LAN), gleicher Start: weiter wie bisher
                st.outroMs = 0L;
                st.cut = false;
                st.sparkled = false;
            }
            st.seen = tickNo;
            double d2 = isSelf ? 0.0 : self.squaredDistanceTo(p);
            st.plainPose = !(p.isGliding() || p.isInSwimmingPose() || p.hasVehicle() || p.isSleeping() || p.isDead());

            // Ego-Perspektive des eigenen Spielers: Höhe sofort begrenzen (kein Blick in den eigenen Körper), beim
            // Wechsel in die 3rd Person weich auf die volle Höhe
            st.prevFp = st.fp;
            if (isSelf && firstPerson) {
                st.fp = 1.0F;
                st.prevFp = 1.0F;
            } else {
                st.fp = st.fp < 0.01F ? 0.0F : st.fp * (1.0F - FP_RELEASE);
            }

            if (carpet) {
                st.arm = st.prevArm = 0.0F;
                float yawRate = (float) HammockLogic.wrapDegrees(p.getBodyYaw() - p.lastBodyYaw) * 20.0F;
                double dx = p.getX() - p.lastX, dy = p.getY() - p.lastY, dz = p.getZ() - p.lastZ;
                double h = Math.sqrt(dx * dx + dz * dz);
                if (h > 3.0 || Math.abs(dy) > 3.0) {
                    // Teleport o. Ä.: keine Fahrt daraus ableiten
                    h = 0.0;
                    dy = 0.0;
                    yawRate = 0.0F;
                }
                boolean flightNow = isSelf && self.getAbilities().flying;
                FlyingCarpetLogic.step(st.track, yawRate, h, dy, p.isOnGround(), p.isInSneakingPose(), isSelf, flightNow,
                        false, nowMs);
                // Lage wie gerade gezeichnet (Interpolation steht direkt nach dem Tick am Anfang): Rückfall für das
                // Auslaufen, falls der Spieler nicht gezeichnet wurde, und Ort für die Partikel
                View v = viewFor(uuid, e);
                applied(v, FX_TILT);
                remember(v, FX_TILT);
                if (d2 <= FX_RANGE * FX_RANGE) effects(world, p, st, v.t, FX_TILT, d2, mode);
                windSound(client, p, st, d2);
            } else {
                // Emote beendet: Teppich sinkt mit dem Reiter zu Boden und rollt sich zusammen; bei einem anderen Emote
                // oder einer Vanilla-Pose (Schwimmen, Reiten ...) verschwindet er sofort mit Funkeln
                if (st.outroMs == 0L) beginOutro(st, nowMs);
                if (e != null || !st.plainPose) st.cut = true;
                FlyingCarpetLogic.step(st.track, 0.0F, 0.0, 0.0, true, false, true, false, true, nowMs);
                // Schlägt der Spieler (oft der Grund für das Ende) oder benutzt ein Item, zeigen die Arme das weich an
                st.prevArm = st.arm;
                st.arm += ((p.handSwinging || p.isUsingItem() ? 1.0F : 0.0F) - st.arm) * 0.5F;
                float age = (nowMs - st.outroMs) / 1000.0F;
                if (!st.sparkled && (age >= FlyingCarpetMotion.OUTRO_SPARKLE || st.cut)) {
                    st.sparkled = true;
                    if (d2 <= FX_RANGE * FX_RANGE && mode != ParticlesMode.MINIMAL) {
                        FlyingCarpetMotion.outro(age, st.endT, FX_OUTRO);
                        double y = st.cut ? FlyingCarpetLogic.GROUND_MID_Y + st.endDy * FX_OUTRO.descent
                                : FlyingCarpetLogic.GROUND_MID_Y + 0.06;
                        double[] c = frameToWorld(p, 0.0, y, FlyingCarpetLogic.CENTER_Z);
                        sparkle(world, c, 9, p.getScale(), false);
                    }
                }
            }
        }
        for (Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            State st = it.next().getValue();
            boolean finished = st.outroMs != 0L
                    && (st.cut || nowMs - st.outroMs > (long) (FlyingCarpetMotion.OUTRO * 1000.0F) + 150L);
            if (st.seen != tickNo || finished) {
                st.sound = null;
                it.remove();
            }
        }
    }

    private static boolean anyCarpet(PlayerEntity self, ClientWorld world) {
        for (PlayerEntity p : world.getPlayers()) {
            EmoteAnimator.ActiveEmote e = EmoteAnimator.resolveActiveEmote(p.getUuid(), p == self);
            if (e != null && e.emote() == EmoteType.FLYING_CARPET) return true;
        }
        return false;
    }

    private static void clearAll() {
        for (State st : STATES.values()) st.sound = null;
        STATES.clear();
    }

    /** Rahmenpunkt (Ruhelage, ohne Neigung) in Weltkoordinaten (Spieler-Skalierung, Körper-Yaw). */
    private static double[] frameToWorld(PlayerEntity p, double fx, double fy, double fz) {
        double s = p.getScale();
        return HammockLogic.frameToWorld(fx * s, fy * s, fz * s, p.getX(), p.getY(), p.getZ(), p.getBodyYaw());
    }

    /** Punkt am Teppich (Rahmen, Ruhelage am Boden) mit Schwebehöhe und Neigung {@code tl} in Weltkoordinaten. */
    private static double[] tiltedToWorld(PlayerEntity p, FlyingCarpetLogic.Tilt tl, double fx, double fy, double fz) {
        FlyingCarpetLogic.tiltPoint(tl, fx, fy, fz, FX_F);
        return frameToWorld(p, FX_F[0], FX_F[1], FX_F[2]);
    }

    /** Puff mit „Pling“ beim Start (am Boden), Funkeln beim Aufsteigen, Funkelspur hinten am schwebenden Teppich. */
    private static void effects(ClientWorld world, PlayerEntity p, State st, float t, FlyingCarpetLogic.Tilt tl, double d2,
                                ParticlesMode mode) {
        float scale = p.getScale() > 0.0F ? p.getScale() : 1.0F;
        if (!st.puffed) {
            st.puffed = true;
            // Nur beim frischen Start (wer erst später in Reichweite kommt, bekommt keinen nachträglichen Puff)
            if (t < PUFF_FRESH_S) {
                double[] c = frameToWorld(p, 0.0, FlyingCarpetLogic.GROUND_MID_Y + 0.12, FlyingCarpetLogic.CENTER_Z);
                if (mode != ParticlesMode.MINIMAL) sparkle(world, c, mode == ParticlesMode.DECREASED ? 9 : 16, scale, true);
                if (d2 <= SOUND_RANGE * SOUND_RANGE) {
                    world.playSoundClient(c[0], c[1], c[2], SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS,
                            0.9F, 1.35F + RNG.nextFloat() * 0.1F, false);
                    world.playSoundClient(c[0], c[1], c[2], SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS,
                            0.35F, 1.78F, false);
                }
            }
        }
        if (mode == ParticlesMode.MINIMAL || t < FlyingCarpetMotion.RISE_START) return;
        if (t < FlyingCarpetMotion.RISE_END) {
            // Aufsteigen: Funkeln rund um den Rand des steigenden Teppichs (rieselt nach unten)
            int n = mode == ParticlesMode.DECREASED ? (tickNo % 2 == 0 ? 1 : 0) : 2;
            for (int i = 0; i < n; i++) {
                double fx, fz;
                if (RNG.nextBoolean()) {
                    fx = (RNG.nextBoolean() ? 1.0 : -1.0) * FlyingCarpetLogic.HALF_WIDTH;
                    fz = FlyingCarpetLogic.CENTER_Z + (RNG.nextDouble() * 2.0 - 1.0) * FlyingCarpetLogic.HALF_LENGTH;
                } else {
                    fx = (RNG.nextDouble() * 2.0 - 1.0) * FlyingCarpetLogic.HALF_WIDTH;
                    fz = FlyingCarpetLogic.CENTER_Z + (RNG.nextBoolean() ? 1.0 : -1.0) * FlyingCarpetLogic.HALF_LENGTH;
                }
                double[] w = tiltedToWorld(p, tl, fx, FlyingCarpetLogic.GROUND_MID_Y - 0.03, fz);
                if (i % 2 == 0) {
                    world.addParticleClient(ParticleTypes.WAX_ON, w[0], w[1], w[2], (RNG.nextDouble() - 0.5) * 0.8,
                            -1.0 - RNG.nextDouble(), (RNG.nextDouble() - 0.5) * 0.8);
                } else {
                    world.addParticleClient(new DustParticleEffect(RNG.nextBoolean() ? GOLD : CREAM, 0.7F), w[0], w[1], w[2],
                            0.0, -0.02, 0.0);
                }
            }
            return;
        }
        FlyingCarpetLogic.Track tr = st.track;
        int count = 0;
        if (tr.speed >= TRAIL_FROM) {
            if (tickNo % TRAIL_EVERY == 0) count = 1 + (RNG.nextFloat() < 0.6F * tr.speed ? 1 : 0);
        } else if (RNG.nextFloat() < IDLE_SPARK_CHANCE) {
            count = 1;
        }
        if (mode == ParticlesMode.DECREASED && count > 1) count = 1;
        for (int i = 0; i < count; i++) {
            double fx = (RNG.nextDouble() - 0.5) * 1.6 * FlyingCarpetLogic.HALF_WIDTH;
            double fz = FlyingCarpetLogic.CENTER_Z + FlyingCarpetLogic.HALF_LENGTH + 0.03 + RNG.nextDouble() * 0.05;
            double[] w = tiltedToWorld(p, tl, fx, FlyingCarpetLogic.GROUND_MID_Y - 0.02, fz);
            // WAX_ON: goldene Funken, Geschwindigkeit wird mit 0,01 multipliziert (sinken langsam, verblassen)
            world.addParticleClient(ParticleTypes.WAX_ON, w[0], w[1], w[2], (RNG.nextDouble() - 0.5) * 0.6,
                    -1.2 - RNG.nextDouble() * 0.8, (RNG.nextDouble() - 0.5) * 0.6);
        }
    }

    /** Goldener Funkel-Puff (Start) bzw. Funkeln beim Verschwinden. */
    private static void sparkle(ClientWorld world, double[] c, int n, float scale, boolean big) {
        double spread = (big ? 0.55 : 0.35) * scale;
        for (int i = 0; i < n; i++) {
            double ox = (RNG.nextDouble() - 0.5) * spread, oy = (RNG.nextDouble() - 0.3) * spread * 0.5;
            double oz = (RNG.nextDouble() - 0.5) * spread * 1.4;
            double x = c[0] + ox, y = c[1] + oy, z = c[2] + oz;
            switch (i % 4) {
                case 0, 2 -> world.addParticleClient(ParticleTypes.WAX_ON, x, y, z, ox * 9.0, 2.0 + RNG.nextDouble() * 3.0, oz * 9.0);
                case 1 -> world.addParticleClient(new DustParticleEffect(i % 8 == 1 ? GOLD : GOLD_DEEP, 0.9F), x, y, z,
                        ox * 0.4, 0.02, oz * 0.4);
                default -> {
                    if (big || i % 8 == 3) {
                        world.addParticleClient(ParticleTypes.FIREWORK, x, y, z, ox * 0.06, 0.02 + RNG.nextDouble() * 0.02, oz * 0.06);
                    } else {
                        world.addParticleClient(new DustParticleEffect(CREAM, 0.7F), x, y, z, 0.0, 0.01, 0.0);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Wind-Rauschen
    // ------------------------------------------------------------------

    private static void windSound(MinecraftClient client, PlayerEntity p, State st, double d2) {
        if (d2 > SOUND_RANGE * SOUND_RANGE) {
            st.sound = null;
            return;
        }
        if (st.sound != null && !st.sound.isDone()
                && (client.getSoundManager().isPlaying(st.sound) || tickNo - st.soundTick < 40)) {
            return;
        }
        // Neu starten (erstmals, nach dem Ende oder falls der Start abgelehnt wurde, höchstens alle 2 s)
        WindSound s = new WindSound(p);
        st.sound = s;
        st.soundTick = tickNo;
        client.getSoundManager().play(s);
    }

    /** Leise, hohe Elytra-Schleife am Teppich; Lautstärke nach Geschwindigkeit, endet mit dem Teppich bzw. außer Hörweite. */
    private static final class WindSound extends MovingSoundInstance {
        private final PlayerEntity player;
        private final UUID uuid;

        WindSound(PlayerEntity player) {
            super(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, SoundInstance.createRandom());
            this.player = player;
            this.uuid = player.getUuid();
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = WIND_IDLE;
            this.pitch = WIND_PITCH;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public void tick() {
            State st = STATES.get(uuid);
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean far = mc.player == null
                    || (player != mc.player && mc.player.squaredDistanceTo(player) > SOUND_RANGE * SOUND_RANGE);
            if (player.isRemoved() || st == null || st.sound != this || st.outroMs != 0L || far) {
                if (st != null && st.sound == this) st.sound = null;
                this.setDone();
                return;
            }
            this.x = player.getX();
            this.y = player.getY() + (st.lastDy + 0.15) * player.getScale();
            this.z = player.getZ();
            float s = st.track.speed;
            this.volume = WIND_IDLE + (WIND_FAST - WIND_IDLE) * s;
            this.pitch = WIND_PITCH + WIND_PITCH_FAST * s;
        }
    }
}

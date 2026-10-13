package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.shop.CaeserApi;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Schaukel-Emote (nur Darstellung auf dem Client, keine Blöcke, keine Pakete an den Minecraft-Server, kein Licht).
 * Einmal pro Client-Tick ({@link #tick}) werden alle Spieler mit SWING nach den Regeln aus {@link SwingLogic} auf
 * Gerüste und Sitze verteilt; Renderer, {@link EmoteAnimator} und Kamera fragen nur ab ({@link #frame}). Jeder Client
 * rechnet mit denselben Daten (Startzeiten aus dem vorhandenen {@code emote} PLAY/STOP, Positionen aus der Welt), es
 * gibt keine eigenen Netzwerknachrichten.
 * <ul>
 *   <li>Feste Ausrichtung wie bei der Hängematte: Pro Emote (UUID + Startzeit) werden Position und Körper-Yaw beim
 *       Start gemerkt (lokal sofort, fremde Spieler im ersten Tick). Das Gerüst steht fest in der Welt.</li>
 *   <li>Der Besitzer eines Gerüsts (frühester Start) zeichnet Beine, Balken, Haken und freie Sitze; jeder Spieler
 *       zeichnet seinen eigenen Sitz mit Seilen (schaukelt mit ihm). Spieler auf dem zweiten Sitz werden nur in der
 *       Darstellung auf den Sitz versetzt (höchstens ca. 2 Blöcke), nicht teleportiert.</li>
 *   <li>Effekte (leises Knarzen an den Umkehrpunkten, sehr leises Rauschen) nur bis 32 Blöcke. Beim Erscheinen und
 *       Verschwinden gibt es keinen Rauch-Puff.</li>
 * </ul>
 */
public final class SwingCoordinator {

    private SwingCoordinator() {}

    private static final double FX_RANGE = 32.0;
    /** So lange nach dem Start gilt ein Emote noch als „frisch“ (Effekte ab 0 auswerten, s). */
    private static final float FRESH_S = 1.5F;

    /** Pro Spieler (UUID32) das laufende Schaukel-Emote (erster gesehener Zustand). */
    private static final Map<String, SwingLogic.Participant> ENTRIES = new HashMap<>();
    private static final List<SwingLogic.SwingSet> SETS = new ArrayList<>();

    private record Seat(SwingLogic.SwingSet set, SwingLogic.Member member) {}

    private static final Map<String, Seat> SEATS = new HashMap<>();
    private static final Map<String, PlayerEntity> TICK_ENTITIES = new TreeMap<>();
    private static final List<SwingLogic.Participant> TICK_ACTIVE = new ArrayList<>();
    /** Zuletzt ausgewertete Emote-Zeit pro Spieler (Knarzen, Rauschen). */
    private static final Map<String, Float> LAST_T = new HashMap<>();
    private static final Random RNG = new Random();
    private static ClientWorld lastWorld;

    // ------------------------------------------------------------------
    // Abfragen für Renderer, Animator und Kamera
    // ------------------------------------------------------------------

    /** Darstellungsrahmen eines Spielers mit SWING für diesen Frame (wiederverwendbar). */
    public static final class Frame {
        /** Gegendrehung gegenüber dem Vanilla-Körper-Yaw auf die Ausrichtung des Gerüsts (Grad). */
        public float counterYaw;
        /** Ursprung des Gerüsts (Sitz 0) relativ zu den aktuellen Füßen, in Rahmen-Achsen (Blöcke). */
        public final double[] off = new double[3];
        public boolean preview;
        /** Eigene Emote-Zeit und Alter des Gerüsts (s). */
        public float t, frameAge;
        public int seat;
        /** Füße beim Start im Rahmen; {@code null} = genau am Sitz (Aufbauer). */
        public double[] standFeet;
        private final double[] feetBuf = new double[3];
        /** Rahmen-Yaw minus Start-Yaw (Grad): Drehung beim Hinsetzen. */
        public float turn0;
        /** Zeichnet dieser Spieler Gerüst und freie Sitze (Besitzer bzw. Vorschau)? */
        public boolean drawFrame;
        public final boolean[] freeSeat = new boolean[2];
    }

    private static final Frame SCRATCH = new Frame();

    /**
     * Rahmen für einen Render-State mit aktivem SWING, oder {@code null}, solange der Spieler (noch) keinem Gerüst
     * zugeordnet ist (höchstens ein Tick). In der Vorschau: keine Gegendrehung, Gerüst am Spieler, beide Sitze.
     */
    public static Frame frame(PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active) {
        if (state == null || active == null || active.emote() != EmoteType.SWING) return null;
        Frame f = SCRATCH;
        f.t = active.t();
        if (active.preview()) {
            f.preview = true;
            f.counterYaw = 0.0F;
            f.off[0] = 0.0;
            f.off[1] = 0.0;
            f.off[2] = 0.0;
            f.frameAge = active.t();
            f.seat = 0;
            f.standFeet = null;
            f.turn0 = 0.0F;
            f.drawFrame = true;
            f.freeSeat[0] = false;
            f.freeSeat[1] = true;
            return f;
        }
        String id = CaeserApi.uuid32(CaeserPlayerSyncManager.getRenderStateUuid(state));
        if (id == null) return null;
        Seat seat = SEATS.get(id);
        if (seat == null || seat.member().startMs != active.startMs()) return null;
        return fill(f, id, seat, state.x, state.y, state.z, state.bodyYaw, active.t());
    }

    private static Frame fill(Frame f, String id, Seat seat, double x, double y, double z, float bodyYaw, float t) {
        SwingLogic.SwingSet s = seat.set();
        SwingLogic.Member m = seat.member();
        f.preview = false;
        f.counterYaw = (float) HammockLogic.wrapDegrees(bodyYaw - s.yaw);
        HammockLogic.worldToFrame(s.x, s.y, s.z, x, y, z, s.yaw, f.off);
        f.frameAge = (System.currentTimeMillis() - s.createdStartMs) / 1000.0F;
        f.seat = m.seat;
        boolean creator = m.seat == 0 && m.startMs == s.createdStartMs && Math.abs(m.x - s.x) < 1.0E-9
                && Math.abs(m.z - s.z) < 1.0E-9;
        if (creator) {
            f.standFeet = null;
            f.turn0 = 0.0F;
        } else {
            HammockLogic.worldToFrame(m.x, m.y, m.z, s.x, s.y, s.z, s.yaw, f.feetBuf);
            f.standFeet = f.feetBuf;
            f.turn0 = (float) HammockLogic.wrapDegrees(s.yaw - m.startYaw);
        }
        SwingLogic.Member owner = s.owner();
        f.drawFrame = owner != null && owner.id.equals(id);
        for (int i = 0; i < 2; i++) f.freeSeat[i] = f.drawFrame && s.seats[i] == null;
        f.t = t;
        return f;
    }

    /**
     * Zusätzliche Sichtbarkeits-Box für das Frustum-Culling: Gerüst samt Schwungbereich (der Besitzer zeichnet das
     * Gerüst, der zweite Spieler wird am Sitz gezeichnet, nicht an seiner Position). {@code null} ohne Schaukel.
     */
    public static Box cullBox(PlayerEntity player) {
        if (SETS.isEmpty() || player == null) return null;
        String id = CaeserApi.uuid32(player.getUuid());
        Seat seat = id != null ? SEATS.get(id) : null;
        if (seat == null) return null;
        SwingLogic.SwingSet s = seat.set();
        double minX = 1e18, minZ = 1e18, maxX = -1e18, maxZ = -1e18;
        double half = SwingLogic.LEG_FOOT_X + 0.3;
        for (double fx : new double[]{SwingLogic.CENTER_X - half, SwingLogic.CENTER_X + half}) {
            for (double fz : new double[]{-2.3, 2.3}) {
                double[] w = HammockLogic.frameToWorld(fx, 0.0, fz, s.x, s.y, s.z, s.yaw);
                minX = Math.min(minX, w[0]);
                maxX = Math.max(maxX, w[0]);
                minZ = Math.min(minZ, w[2]);
                maxZ = Math.max(maxZ, w[2]);
            }
        }
        return new Box(minX, s.y - 0.2, minZ, maxX, s.y + SwingLogic.BEAM_Y + 0.4, maxZ);
    }

    /** Kamera-Drehpunkt (Welt) für den lokalen Spieler: über seinem Sitz, schaukelt nicht mit; sonst {@code null}. */
    public static double[] localCameraPivot(PlayerEntity self) {
        if (self == null) return null;
        String id = CaeserApi.uuid32(self.getUuid());
        Seat seat = id != null ? SEATS.get(id) : null;
        if (seat == null || seat.member().startMs != com.bame.client.module.EmoteModule.getStartTimeMillis()) return null;
        double[] c = SwingMotion.cameraPivot(seat.member().seat);
        double sc = self.getScale();
        SwingLogic.SwingSet s = seat.set();
        return HammockLogic.frameToWorld(c[0], SwingLogic.SEAT_TOP_Y + SwingMotion.CAMERA_LIFT * sc, c[2], s.x, s.y, s.z, s.yaw);
    }

    // ------------------------------------------------------------------
    // Start des lokalen Spielers
    // ------------------------------------------------------------------

    public enum StartCheck { OK, NO_SPACE, TOO_FAR, OCCUPIED }

    /**
     * Darf der lokale Spieler die Schaukel jetzt starten? Freier Sitz bis 2,0 Blöcke → hinsetzen; freier Sitz weiter
     * weg (bis 3,5) → „Geh näher“; sonst neues Gerüst, wenn in der Welt Platz ist (und kein Gerüst überschnitten wird),
     * neben einem vollen Gerüst ohne Platz → „besetzt“.
     */
    public static StartCheck checkLocalStart(ClientPlayerEntity p) {
        if (p == null || p.getEntityWorld() == null) return StartCheck.NO_SPACE;
        String id = CaeserApi.uuid32(p.getUuid());
        SwingLogic.StartResult r = SwingLogic.checkStart(SETS, id, p.getX(), p.getY(), p.getZ(), p.getBodyYaw());
        switch (r) {
            case JOIN:
                return StartCheck.OK;
            case TOO_FAR:
                return StartCheck.TOO_FAR;
            case OCCUPIED:
                return StartCheck.OCCUPIED;
            default:
                break;
        }
        if (hasSpace(p)) return StartCheck.OK;
        return SwingLogic.nearFullSet(SETS, p.getX(), p.getY(), p.getZ()) ? StartCheck.OCCUPIED : StartCheck.NO_SPACE;
    }

    /** Platz für ein neues Gerüst: Luft im Gerüst- und Schwungbereich, fester Boden unter den vier Bein-Füßen. */
    private static boolean hasSpace(ClientPlayerEntity p) {
        var world = p.getEntityWorld();
        for (double[] c : SwingLogic.checkPoints(p.getX(), p.getY(), p.getZ(), p.getBodyYaw())) {
            BlockPos pos = BlockPos.ofFloored(c[0], c[1], c[2]);
            BlockState at = world.getBlockState(pos);
            if (!at.getCollisionShape(world, pos).isEmpty() || !at.getFluidState().isEmpty()) return false;
            if (c[3] > 0.5) {
                BlockPos below = pos.down();
                if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) return false;
            }
        }
        return true;
    }

    /** Lokaler Start: Position und Körper-Yaw sofort merken und gleich einem Gerüst/Sitz zuordnen. */
    public static void onLocalStart(ClientPlayerEntity p, long startMs) {
        if (p == null) return;
        String id = CaeserApi.uuid32(p.getUuid());
        if (id == null) return;
        ENTRIES.put(id, new SwingLogic.Participant(id, startMs, p.getX(), p.getY(), p.getZ(), p.getBodyYaw()));
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.world == lastWorld) update(client, client.world, false);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            ENTRIES.clear();
            SETS.clear();
            SEATS.clear();
            LAST_T.clear();
            lastWorld = world;
        }
        if (world == null || client.player == null) return;
        update(client, world, true);
    }

    private static void update(MinecraftClient client, ClientWorld world, boolean effects) {
        Map<String, PlayerEntity> entities = TICK_ENTITIES;
        List<SwingLogic.Participant> active = TICK_ACTIVE;
        entities.clear();
        active.clear();
        for (PlayerEntity p : world.getPlayers()) {
            String id = CaeserApi.uuid32(p.getUuid());
            if (id == null) continue;
            EmoteAnimator.ActiveEmote emote = EmoteAnimator.resolveActiveEmote(p.getUuid(), p == client.player);
            if (emote == null || emote.emote() != EmoteType.SWING) continue;
            SwingLogic.Participant e = ENTRIES.get(id);
            if (e == null || e.startMs() != emote.startMs()) {
                // Erster Tick, in dem dieses Emote gesehen wird: Position und Ausrichtung festhalten
                e = new SwingLogic.Participant(id, emote.startMs(), p.getX(), p.getY(), p.getZ(), p.getBodyYaw());
                ENTRIES.put(id, e);
            }
            active.add(e);
            entities.put(id, p);
        }
        ENTRIES.keySet().retainAll(entities.keySet());
        if (active.isEmpty() && SETS.isEmpty()) {
            SEATS.clear();
            LAST_T.clear();
            return;
        }

        SwingLogic.update(SETS, active);
        SEATS.clear();
        for (SwingLogic.SwingSet s : SETS) {
            for (SwingLogic.Member m : s.seats) if (m != null) SEATS.put(m.id, new Seat(s, m));
        }
        if (!effects) return;

        PlayerEntity self = client.player;
        long now = System.currentTimeMillis();
        // Spieler: Knarzen an den Umkehrpunkten, leises Rauschen im Tiefpunkt großer Schwünge
        for (Map.Entry<String, PlayerEntity> en : entities.entrySet()) {
            String id = en.getKey();
            Seat seat = SEATS.get(id);
            if (seat == null) continue;
            float t = (now - seat.member().startMs) / 1000.0F;
            Float last = LAST_T.get(id);
            float t0 = last != null ? last : (t < FRESH_S ? -0.001F : t);
            if (t > t0 && inRange(self, seat.set())) memberEffects(world, seat, t0, t);
            LAST_T.put(id, t);
        }
        LAST_T.keySet().retainAll(entities.keySet());
    }

    private static boolean inRange(PlayerEntity self, SwingLogic.SwingSet s) {
        return self.squaredDistanceTo(s.x, s.y, s.z) <= FX_RANGE * FX_RANGE;
    }

    private static void memberEffects(ClientWorld world, Seat seat, float t0, float t1) {
        SwingLogic.SwingSet s = seat.set();
        int si = seat.member().seat;
        if (t1 <= SwingMotion.SWING_START) return;
        float half = SwingMotion.PERIOD * 0.5F;
        int n0 = Math.max(0, (int) Math.floor((Math.max(t0, SwingMotion.SWING_START) - SwingMotion.SWING_START) / half) - 1);
        for (int n = n0; n <= n0 + 4; n++) {
            float te = SwingMotion.extremeTime(n);
            if (te > t1) break;
            if (te <= t0 || !SwingMotion.creaksAt(n)) continue;
            // Knarzen am Haken (leise, tief)
            double side = (n / SwingMotion.CREAK_EVERY) % 2 == 0 ? 1.0 : -1.0;
            double[] w = HammockLogic.frameToWorld(SwingLogic.seatX(si) + side * SwingLogic.ROPE_X, SwingLogic.PIVOT_Y, 0.0,
                    s.x, s.y, s.z, s.yaw);
            world.playSoundClient(w[0], w[1], w[2], SoundEvents.BLOCK_LADDER_STEP, SoundCategory.PLAYERS,
                    0.18F, 0.5F + RNG.nextFloat() * 0.08F, false);
        }
        for (int n = n0 + 1; n <= n0 + 5; n++) {
            float tb = SwingMotion.bottomTime(n);
            if (tb > t1) break;
            if (tb <= t0 || !SwingMotion.whooshAt(n) || SwingMotion.envelope(tb) < 0.9F) continue;
            double[] w = HammockLogic.frameToWorld(SwingLogic.seatX(si), SwingLogic.SEAT_TOP_Y + 0.5, 0.0, s.x, s.y, s.z, s.yaw);
            world.playSoundClient(w[0], w[1], w[2], SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS,
                    0.05F, 0.55F + RNG.nextFloat() * 0.1F, false);
        }
    }
}

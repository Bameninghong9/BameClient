package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.shop.CaeserApi;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Koordiniert das Lagerfeuer-Emote (nur Darstellung auf dem Client). Einmal pro Client-Tick ({@link #tick}) werden alle
 * Spieler mit CAMPFIRE nach den Regeln aus {@link CampfireLogic} zu Runden zusammengefasst; Renderer und
 * {@link EmoteAnimator} fragen nur ab ({@link #frame}). Jeder Client rechnet mit denselben Daten (Startzeiten aus
 * {@code PlayerEmoteData}, Positionen aus der Welt), es gibt keine eigenen Netzwerknachrichten.
 * <ul>
 *   <li>Feste Ausrichtung: Pro Emote (UUID + Startzeit) wird beim Start der Körper-Yaw gemerkt (lokal sofort, fremde
 *       Spieler beim ersten Tick, in dem das Emote gesehen wird). Der Spieler wird mit dieser Ausrichtung gerendert
 *       (Gegendrehung in {@code setupTransforms}); Stamm und Feuer stehen fest in der Welt.</li>
 *   <li>Nur der Gastgeber einer Runde rendert das Feuer, seine Partikel und das Knistern.</li>
 *   <li>Partikel und Sounds nur bis 32 Blöcke Entfernung, gedrosselt.</li>
 * </ul>
 */
public final class CampfireCoordinator {

    private CampfireCoordinator() {}

    /** Effekte nur in diesem Umkreis um den lokalen Spieler (Blöcke). */
    private static final double FX_RANGE = 32.0;
    private static final long SMOKE_EVERY_MS = 500L;
    private static final long CRACKLE_MIN_MS = 2_000L, CRACKLE_MAX_MS = 3_000L;
    /** So lange nach dem Start gilt ein Emote noch als „frisch“ (Effekte ab 0 auswerten, ms). */
    private static final long FRESH_MS = 1_500L;

    /**
     * Feste Daten eines laufenden Emotes: erster gesehener Zustand und gemeinsame Zeitbasis für den Zufall (Serverzeit).
     * Beim eigenen Emote zuerst die Schätzung; sobald das Echo des Sync-Servers da ist (normalerweise < 1 s, lange vor
     * der ersten Varianten-Entscheidung bei 6,1 s), dessen exakte Serverzeit – dieselbe Zahl wie bei allen Beobachtern.
     */
    private static final class Entry {
        final CampfireLogic.Participant participant;
        long seedMs;
        /** {@code true}: {@link #seedMs} ist die maßgebliche Serverzeit (bzw. für fremde Spieler endgültig). */
        boolean exact;

        Entry(CampfireLogic.Participant participant, long seedMs, boolean exact) {
            this.participant = participant;
            this.seedMs = seedMs;
            this.exact = exact;
        }
    }

    /** Pro Spieler (UUID32) das laufende Lagerfeuer-Emote; wird beim Ende des Emotes entfernt. */
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final List<CampfireLogic.Round> ROUNDS = new ArrayList<>();
    /** Sitzplatz pro Spieler (pro Tick neu): Runde + Teilnehmer. */
    private record Seat(CampfireLogic.Round round, CampfireLogic.Member member) {}

    private static final Map<String, Seat> SEATS = new HashMap<>();
    private static final Map<String, PlayerEntity> TICK_ENTITIES = new TreeMap<>();
    private static final List<CampfireLogic.Participant> TICK_ACTIVE = new ArrayList<>();
    private static ClientWorld lastWorld;

    /** Gedrosselte Effekte pro Feuer. */
    private static final class FireFx {
        long nextSmoke;
        long nextCrackle;

        FireFx(long now) {
            nextSmoke = now + SMOKE_EVERY_MS;
            nextCrackle = now + CRACKLE_MIN_MS;
        }
    }

    private static final Map<CampfireLogic.Round, FireFx> FIRE_FX = new IdentityHashMap<>();
    /** Letzte ausgewertete Emote-Zeit pro Teilnehmer (für Ereignisse wie Bissen, Pusten). */
    private static final class MemberFx {
        final long startMs;
        float lastT;

        MemberFx(long startMs, float lastT) {
            this.startMs = startMs;
            this.lastT = lastT;
        }
    }

    private static final Map<String, MemberFx> MEMBER_FX = new HashMap<>();
    private static final Random RNG = new Random();

    // ------------------------------------------------------------------
    // Abfragen für Renderer und Animator
    // ------------------------------------------------------------------

    /**
     * Darstellungsrahmen eines Spielers mit CAMPFIRE für diesen Frame (wiederverwendbar). Alle Positionen im Modellraum
     * des Spielers (px, +Y nach unten, -Z nach vorne), bezogen auf die gerenderte Ausrichtung {@link #renderYaw}.
     */
    public static final class Frame {
        /** Ausrichtung, mit der der Spieler gerendert wird (Grad, Minecraft-Yaw). */
        public float renderYaw;
        /** Gegendrehung gegenüber dem Vanilla-Körper-Yaw (Grad); 0 = keine. */
        public float counterYaw;
        /** Stamm: Drehung im Modellraum (Grad) gegenüber der gerenderten Ausrichtung. */
        public float logYaw;
        /** Rendert dieser Spieler das Feuer (Gastgeber bzw. Vorschau)? */
        public boolean drawFire;
        /** Feuer-Boden (px) und Drehung des Feuer-Modells (Grad), Größe (inkl. Einblenden und Runde ab 3). */
        public final double[] fireBase = new double[3];
        public float fireYaw, fireScale;
        /** Punkt über dem Feuer, auf den der Marshmallow zeigt (px). */
        public final double[] fireTarget = new double[3];
        /** Waagerechter Abstand Spieler–Feuer (Blöcke). */
        public double fireDistance;
        /** Gemeinsame Zeitbasis für den Zufall (ms). */
        public long seedMs;
        public String id;
    }

    private static final Frame SCRATCH_FRAME = new Frame();

    /**
     * Rahmen für einen Render-State mit aktivem CAMPFIRE ({@code active}), oder {@code null}, wenn der Spieler (noch)
     * keiner Runde zugeordnet ist. In der Vorschau: Feuer 1,3 Blöcke geradeaus, keine Gegendrehung.
     * Das Ergebnis ist ein wiederverwendetes Objekt und nur bis zum nächsten Aufruf gültig.
     */
    public static Frame frame(PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active) {
        if (state == null || active == null || active.emote() != EmoteType.CAMPFIRE) return null;
        Frame f = SCRATCH_FRAME;
        if (active.preview()) {
            f.id = null;
            f.seedMs = 0L;
            f.renderYaw = state.bodyYaw;
            f.counterYaw = 0.0F;
            f.logYaw = 0.0F;
            f.drawFire = true;
            f.fireYaw = 0.0F;
            f.fireDistance = CampfireLogic.FIRE_DISTANCE;
            CampfireMotion.defaultFireTarget(f.fireTarget);
            f.fireBase[0] = 0.0;
            f.fireBase[1] = CampfireMotion.MODEL_ORIGIN_UP_PX;
            f.fireBase[2] = -CampfireLogic.FIRE_DISTANCE / CampfireMotion.BLOCKS_PER_PX;
            f.fireScale = CampfireMotion.easeOutBack(CampfireMotion.clamp01(active.t() / CampfireMotion.PROP_GROW));
            return f;
        }
        String id = CaeserApi.uuid32(CaeserPlayerSyncManager.getRenderStateUuid(state));
        if (id == null) return null;
        Seat seat = SEATS.get(id);
        if (seat == null || seat.member().startMs != active.startMs()) return null;
        return fill(f, id, seat, state.x, state.y, state.z, state.baseScale, state.bodyYaw, active.t());
    }

    private static Frame fill(Frame f, String id, Seat seat, double x, double y, double z, float baseScale,
                              float bodyYaw, float t) {
        CampfireLogic.Round r = seat.round();
        CampfireLogic.Member m = seat.member();
        Entry e = ENTRIES.get(id);
        f.id = id;
        f.seedMs = e != null ? e.seedMs : m.startMs;
        // Hinsetzen: Joiner drehen sich in der Sitz-Zeit vom Start-Yaw zum Feuer
        float sit = CampfireMotion.pass(t) == 0 ? CampfireMotion.easeInOut(t / CampfireMotion.INTRO) : 1.0F;
        float turn = (float) CampfireLogic.wrapDegrees(m.anchorYaw - m.startYaw);
        f.renderYaw = m.startYaw + turn * sit;
        f.counterYaw = (float) CampfireLogic.wrapDegrees(bodyYaw - f.renderYaw);
        f.logYaw = (float) CampfireLogic.wrapDegrees(m.anchorYaw - f.renderYaw);
        CampfireLogic.Member host = r.host();
        f.drawFire = host != null && host.id.equals(id);
        f.fireYaw = (float) CampfireLogic.wrapDegrees(r.fireYaw - f.renderYaw);
        f.fireDistance = r.horizontalDistanceTo(x, z);
        float scale = baseScale > 0.0F ? baseScale : 1.0F;
        worldToModel(r.fireX, r.fireY, r.fireZ, x, y, z, f.renderYaw, scale, f.fireBase);
        worldToModel(r.fireX, r.fireY + CampfireMotion.ROAST_HEIGHT, r.fireZ, x, y, z, f.renderYaw, scale, f.fireTarget);
        CampfireMotion.pullTowardPlayer(f.fireTarget);
        float age = (System.currentTimeMillis() - r.createdStartMs) / 1000.0F;
        f.fireScale = CampfireMotion.easeOutBack(CampfireMotion.clamp01(age / CampfireMotion.PROP_GROW))
                * (r.isBig() ? 1.2F : 1.0F);
        return f;
    }

    /** Weltpunkt in Modell-px des Spielers (Füße bei x/y/z, gerenderte Ausrichtung {@code yaw}, Skalierung). */
    private static void worldToModel(double px, double py, double pz, double x, double y, double z, float yaw,
                                     float scale, double[] out) {
        double dx = px - x, dy = py - y, dz = pz - z;
        double r = Math.toRadians(yaw);
        double fwd = -Math.sin(r) * dx + Math.cos(r) * dz;
        double right = -Math.cos(r) * dx - Math.sin(r) * dz;
        double k = CampfireMotion.BLOCKS_PER_PX * scale;
        out[0] = -right / k;
        out[1] = CampfireMotion.MODEL_ORIGIN_UP_PX - dy / k;
        out[2] = -fwd / k;
    }

    /** Verbrennt der Marshmallow in diesem Durchgang? Gemeinsame Zeitbasis aus dem Rahmen (Vorschau: nur Durchgang). */
    public static boolean burns(Frame frame, float t) {
        int pass = CampfireMotion.pass(Math.max(0.0F, t));
        return frame != null ? CampfireLogic.burns(frame.id, frame.seedMs, pass) : CampfireLogic.burns(null, 0L, pass);
    }

    /**
     * Heller Licht-Wert für Spieler, Stamm und Stock am Feuer: hoher Block-Light-Anteil mit leichtem Flackern. Der
     * Himmels-Anteil bleibt unverändert; es wird kein Licht in der Welt verändert.
     */
    public static int fireLight(int light, float strength, int phase) {
        double now = System.currentTimeMillis() / 1000.0;
        double flicker = Math.sin(now * 9.1 + phase) * 0.6 + Math.sin(now * 23.7 + phase * 0.37) * 0.4;
        int target = 13 + (int) Math.round(flicker * 1.4 + 0.6);
        target = Math.max(11, Math.min(15, target));
        int block = LightmapTextureManager.getBlockLightCoordinates(light);
        int sky = LightmapTextureManager.getSkyLightCoordinates(light);
        int lit = Math.round(block + (Math.max(block, target) - block) * CampfireMotion.clamp01(strength));
        return LightmapTextureManager.pack(lit, sky);
    }

    /**
     * Zusätzliche Sichtbarkeits-Box für das Frustum-Culling: Der Gastgeber zeichnet das Feuer mit; steht nur das Feuer
     * im Bild, würde er sonst samt Feuer ausgeblendet. {@code null}, wenn der Spieler kein Feuer zeichnet.
     */
    public static Box fireCullBox(PlayerEntity player) {
        if (ROUNDS.isEmpty() || player == null) return null;
        String id = CaeserApi.uuid32(player.getUuid());
        Seat seat = id != null ? SEATS.get(id) : null;
        if (seat == null) return null;
        CampfireLogic.Member host = seat.round().host();
        if (host == null || !host.id.equals(id)) return null;
        CampfireLogic.Round r = seat.round();
        return new Box(r.fireX - 0.8, r.fireY - 0.2, r.fireZ - 0.8, r.fireX + 0.8, r.fireY + 1.6, r.fireZ + 0.8);
    }

    // ------------------------------------------------------------------
    // Start des lokalen Spielers
    // ------------------------------------------------------------------

    public enum StartCheck { OK, NO_SPACE, TOO_FAR }

    /**
     * Darf der lokale Spieler das Lagerfeuer jetzt starten? Ist innerhalb von 3 Blöcken schon ein Feuer, setzt er sich
     * dazu (höchstens 2,2 Blöcke entfernt). Sonst braucht ein neues Feuer 1,3 Blöcke vor ihm auf Fußhöhe Luft und
     * darunter einen festen Block.
     */
    public static StartCheck checkLocalStart(ClientPlayerEntity p) {
        if (p == null || p.getEntityWorld() == null) return StartCheck.NO_SPACE;
        String id = CaeserApi.uuid32(p.getUuid());
        CampfireLogic.StartResult r = CampfireLogic.checkStart(ROUNDS, id, p.getX(), p.getY(), p.getZ());
        if (r == CampfireLogic.StartResult.TOO_FAR) return StartCheck.TOO_FAR;
        if (r == CampfireLogic.StartResult.JOIN) return StartCheck.OK;
        return hasFireSpace(p) ? StartCheck.OK : StartCheck.NO_SPACE;
    }

    private static boolean hasFireSpace(ClientPlayerEntity p) {
        var world = p.getEntityWorld();
        double[] f = CampfireLogic.firePosition(p.getX(), p.getY(), p.getZ(), p.getBodyYaw());
        BlockPos foot = BlockPos.ofFloored(f[0], p.getY() + 0.05, f[2]);
        BlockState at = world.getBlockState(foot);
        if (!at.getCollisionShape(world, foot).isEmpty() || !at.getFluidState().isEmpty()) return false;
        BlockPos below = foot.down();
        return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
    }

    /** Lokaler Start: Körper-Yaw und Position sofort merken und gleich einer Runde zuordnen. */
    public static void onLocalStart(ClientPlayerEntity p, long startMs) {
        if (p == null) return;
        String id = CaeserApi.uuid32(p.getUuid());
        if (id == null) return;
        CampfireLogic.Participant part = new CampfireLogic.Participant(id, startMs, p.getX(), p.getY(), p.getZ(),
                p.getBodyYaw());
        ENTRIES.put(id, localEntry(part, startMs));
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.world == lastWorld) {
            update(client, client.world, System.currentTimeMillis(), false);
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            ENTRIES.clear();
            ROUNDS.clear();
            SEATS.clear();
            FIRE_FX.clear();
            MEMBER_FX.clear();
            lastWorld = world;
        }
        if (world == null || client.player == null) return;
        update(client, world, System.currentTimeMillis(), true);
    }

    /** Teilnehmer erfassen, Runden fortschreiben und (im Tick) Effekte auslösen. */
    private static void update(MinecraftClient client, ClientWorld world, long now, boolean effects) {
        // 1. Alle Spieler mit CAMPFIRE, nach UUID sortiert (Sammlungen werden wiederverwendet)
        Map<String, PlayerEntity> entities = TICK_ENTITIES;
        List<CampfireLogic.Participant> active = TICK_ACTIVE;
        entities.clear();
        active.clear();
        for (PlayerEntity p : world.getPlayers()) {
            String id = CaeserApi.uuid32(p.getUuid());
            if (id == null) continue;
            boolean local = p == client.player;
            EmoteAnimator.ActiveEmote emote = EmoteAnimator.resolveActiveEmote(p.getUuid(), local);
            if (emote == null || emote.emote() != EmoteType.CAMPFIRE) continue;
            Entry e = ENTRIES.get(id);
            if (e == null || e.participant.startMs() != emote.startMs()) {
                // Erster Frame/Tick, in dem dieses Emote gesehen wird: Ausrichtung und Position festhalten
                CampfireLogic.Participant part = new CampfireLogic.Participant(id, emote.startMs(), p.getX(), p.getY(),
                        p.getZ(), p.getBodyYaw());
                e = local ? localEntry(part, emote.startMs())
                        : new Entry(part, CaeserPlayerSyncManager.sharedStartTime(id, EmoteType.CAMPFIRE, emote.startMs()), true);
                ENTRIES.put(id, e);
            } else if (local && !e.exact) {
                // Echo des Sync-Servers angekommen: ab jetzt dieselbe Serverzeit wie alle anderen Clients
                long exact = CaeserPlayerSyncManager.ownEmoteServerTime(EmoteType.CAMPFIRE, emote.startMs());
                if (exact > 0L) {
                    e.seedMs = exact;
                    e.exact = true;
                }
            }
            active.add(e.participant);
            entities.put(id, p);
        }
        ENTRIES.keySet().retainAll(entities.keySet());
        // Niemand am Lagerfeuer und nichts aufzuräumen: nichts zu tun
        if (active.isEmpty() && ROUNDS.isEmpty()) {
            SEATS.clear();
            MEMBER_FX.clear();
            return;
        }

        // 2. Runden fortschreiben
        CampfireLogic.Changes changes = CampfireLogic.update(ROUNDS, active);
        SEATS.clear();
        for (CampfireLogic.Round r : ROUNDS) {
            for (CampfireLogic.Member m : r.members.values()) SEATS.put(m.id, new Seat(r, m));
        }
        for (CampfireLogic.Round r : changes.created) FIRE_FX.put(r, new FireFx(now));
        boolean removedAny = !changes.removed.isEmpty();
        if (!effects) {
            if (removedAny) changes.removed.forEach(FIRE_FX::remove);
            return;
        }

        PlayerEntity self = client.player;
        // 3. Aufgelöste Feuer: Effekt-Zustand vergessen (ohne Rauch-Puff)
        for (CampfireLogic.Round r : changes.removed) FIRE_FX.remove(r);

        // 4. Feuer (wird nur vom Gastgeber gezeichnet, es gibt pro Runde genau eins): Rauch, Funken, Knistern
        for (CampfireLogic.Round r : ROUNDS) {
            FireFx fx = FIRE_FX.computeIfAbsent(r, k -> new FireFx(now));
            if (!inRange(self, r) || now - r.createdStartMs < 300L) continue;
            fireEffects(world, r, fx, now);
        }

        // 5. Teilnehmer: Bissen, Herzchen, Pusten, brennender Marshmallow, Funkenregen
        for (Map.Entry<String, PlayerEntity> en : entities.entrySet()) {
            String id = en.getKey();
            PlayerEntity p = en.getValue();
            Seat seat = SEATS.get(id);
            Entry e = ENTRIES.get(id);
            if (seat == null || e == null) continue;
            float t = (now - seat.member().startMs) / 1000.0F;
            MemberFx fx = MEMBER_FX.get(id);
            if (fx == null || fx.startMs != seat.member().startMs) {
                // Neu gesehen: frisches Emote ab 0 auswerten, sonst erst ab jetzt
                fx = new MemberFx(seat.member().startMs, t < FRESH_MS / 1000.0F ? -0.001F : t);
                MEMBER_FX.put(id, fx);
            }
            if (t > fx.lastT && self.squaredDistanceTo(p) <= FX_RANGE * FX_RANGE) {
                memberEffects(world, p, seat, e, fx.lastT, t);
            }
            fx.lastT = t;
        }
        MEMBER_FX.keySet().retainAll(entities.keySet());
    }

    /**
     * Eintrag für das eigene Emote: Zeitbasis ist die Serverzeit aus dem Echo des Sync-Servers, solange es fehlt die
     * Schätzung (wird beim Eintreffen des Echos ersetzt, siehe {@link #update}).
     */
    private static Entry localEntry(CampfireLogic.Participant part, long startMs) {
        long exact = CaeserPlayerSyncManager.ownEmoteServerTime(EmoteType.CAMPFIRE, startMs);
        return exact > 0L ? new Entry(part, exact, true)
                : new Entry(part, CaeserPlayerSyncManager.estimateServerTime(startMs), false);
    }

    private static boolean inRange(PlayerEntity self, CampfireLogic.Round r) {
        return self.squaredDistanceTo(r.fireX, r.fireY, r.fireZ) <= FX_RANGE * FX_RANGE;
    }

    // ------------------------------------------------------------------
    // Effekte (nur Darstellung, nur lokal)
    // ------------------------------------------------------------------

    private static void fireEffects(ClientWorld world, CampfireLogic.Round r, FireFx fx, long now) {
        double x = r.fireX, y = r.fireY, z = r.fireZ;
        boolean big = r.isBig();
        if (now >= fx.nextSmoke) {
            fx.nextSmoke = now + SMOKE_EVERY_MS + RNG.nextInt(120) - 60;
            // Rauch steigt aus den Flammenspitzen des eigenen Modells auf (große Runde: Modell 1,2-fach)
            double flameTop = CampfireMotion.FLAME_VISIBLE_TOP * (big ? 1.2 : 1.0);
            world.addImportantParticleClient(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    x + (RNG.nextDouble() - 0.5) * 0.25, y + flameTop * 0.75 + RNG.nextDouble() * 0.15,
                    z + (RNG.nextDouble() - 0.5) * 0.25, 0.0, 0.06, 0.0);
        }
        // Funken bzw. Glut: selten (bei großer Runde doppelt so oft)
        if (RNG.nextInt(big ? 30 : 60) == 0) {
            world.addParticleClient(ParticleTypes.LAVA, x + (RNG.nextDouble() - 0.5) * 0.4, y + 0.3,
                    z + (RNG.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
        } else if (RNG.nextInt(big ? 20 : 40) == 0) {
            world.addParticleClient(ParticleTypes.SMALL_FLAME, x + (RNG.nextDouble() - 0.5) * 0.3, y + 0.5,
                    z + (RNG.nextDouble() - 0.5) * 0.3, 0.0, 0.02, 0.0);
        }
        if (now >= fx.nextCrackle) {
            fx.nextCrackle = now + CRACKLE_MIN_MS + RNG.nextInt((int) (CRACKLE_MAX_MS - CRACKLE_MIN_MS));
            world.playSoundClient(x, y + 0.3, z, SoundEvents.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.BLOCKS,
                    0.7F + RNG.nextFloat() * 0.3F, 0.85F + RNG.nextFloat() * 0.3F, false);
        }
    }

    private static final CampfireMotion.Pose FX_POSE = new CampfireMotion.Pose();
    private static final CampfireMotion.Stick FX_STICK = new CampfireMotion.Stick();
    private static final Frame FX_FRAME = new Frame();
    private static final double[] FX_TARGET = new double[3];

    private static void memberEffects(ClientWorld world, PlayerEntity p, Seat seat, Entry e, float t0, float t1) {
        CampfireLogic.Round r = seat.round();
        long seedMs = e.seedMs;
        String id = seat.member().id;

        int pass = CampfireMotion.pass(t1);
        int firstPass = CampfireMotion.pass(Math.max(0.0F, t0));
        for (int k = firstPass; k <= pass; k++) {
            boolean burns = CampfireLogic.burns(id, seedMs, k);
            if (!burns) {
                if (CampfireMotion.crossed(t0, t1, k, CampfireMotion.A_BITE_1) || CampfireMotion.crossed(t0, t1, k, CampfireMotion.A_BITE_2)) {
                    double[] mouth = mouth(p, seat, t1);
                    world.playSoundClient(mouth[0], mouth[1], mouth[2], SoundEvents.ENTITY_GENERIC_EAT.value(),
                            SoundCategory.PLAYERS, 0.35F, 1.05F + RNG.nextFloat() * 0.2F, false);
                }
                if (CampfireMotion.crossed(t0, t1, k, CampfireMotion.A_HEARTS)) {
                    double[] mouth = mouth(p, seat, t1);
                    for (int i = 0; i < 2; i++) {
                        world.addParticleClient(ParticleTypes.HEART, mouth[0] + (RNG.nextDouble() - 0.5) * 0.4,
                                mouth[1] + 0.45 + RNG.nextDouble() * 0.15, mouth[2] + (RNG.nextDouble() - 0.5) * 0.4,
                                0.0, 0.0, 0.0);
                    }
                }
            } else {
                for (float blow : CampfireMotion.B_BLOWS) {
                    if (CampfireMotion.crossed(t0, t1, k, blow)) {
                        double[] m = mallow(p, seat, seedMs, t1);
                        for (int i = 0; i < 3; i++) {
                            world.addParticleClient(ParticleTypes.SMOKE, m[0] + (RNG.nextDouble() - 0.5) * 0.08,
                                    m[1] + (RNG.nextDouble() - 0.5) * 0.08, m[2] + (RNG.nextDouble() - 0.5) * 0.08,
                                    (RNG.nextDouble() - 0.5) * 0.02, 0.03, (RNG.nextDouble() - 0.5) * 0.02);
                        }
                    }
                }
                if (CampfireMotion.crossed(t0, t1, k, CampfireMotion.B_FLAME_OUT)) {
                    double[] m = mallow(p, seat, seedMs, t1);
                    world.playSoundClient(m[0], m[1], m[2], SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS,
                            0.15F, 1.6F + RNG.nextFloat() * 0.2F, false);
                }
                if (CampfireMotion.crossed(t0, t1, k, CampfireMotion.B_SPARKS)) {
                    // Wegwerfen: kurzer Funkenregen im Feuer
                    for (int i = 0; i < 3; i++) {
                        world.addParticleClient(ParticleTypes.LAVA, r.fireX + (RNG.nextDouble() - 0.5) * 0.3,
                                r.fireY + 0.4, r.fireZ + (RNG.nextDouble() - 0.5) * 0.3, 0.0, 0.0, 0.0);
                    }
                    for (int i = 0; i < 5; i++) {
                        world.addParticleClient(ParticleTypes.SMALL_FLAME, r.fireX + (RNG.nextDouble() - 0.5) * 0.3,
                                r.fireY + 0.5, r.fireZ + (RNG.nextDouble() - 0.5) * 0.3,
                                (RNG.nextDouble() - 0.5) * 0.06, 0.05 + RNG.nextDouble() * 0.05, (RNG.nextDouble() - 0.5) * 0.06);
                    }
                }
            }
        }
        // Brennender Marshmallow: kleine Flammen an der Stockspitze (jeden zweiten Tick)
        if (CampfireLogic.burns(id, seedMs, pass)) {
            float u = CampfireMotion.passTime(t1);
            if (u >= CampfireMotion.B_IGNITE && u < CampfireMotion.B_FLAME_OUT && (p.age & 1) == 0) {
                double[] m = mallow(p, seat, seedMs, t1);
                world.addParticleClient(ParticleTypes.SMALL_FLAME, m[0] + (RNG.nextDouble() - 0.5) * 0.06, m[1] + 0.05,
                        m[2] + (RNG.nextDouble() - 0.5) * 0.06, 0.0, 0.01, 0.0);
            }
        }
    }

    /** Mund des Spielers in Weltkoordinaten (aus der Pose). */
    private static double[] mouth(PlayerEntity p, Seat seat, float t) {
        Frame f = fill(FX_FRAME, seat.member().id, seat, p.getX(), p.getY(), p.getZ(), p.getScale(), p.getBodyYaw(), t);
        CampfireMotion.sample(t, false, FX_POSE);
        return HighFiveLogic.modelToWorld(0.0, FX_POSE.bodyDY - 1.5, FX_POSE.bodyDZ - 4.5, p.getX(), p.getY(), p.getZ(),
                f.renderYaw, p.getScale());
    }

    /** Marshmallow-Mitte in Weltkoordinaten (gleiche Rechnung wie im Renderer, breite Arme). */
    private static double[] mallow(PlayerEntity p, Seat seat, long seedMs, float t) {
        Frame f = fill(FX_FRAME, seat.member().id, seat, p.getX(), p.getY(), p.getZ(), p.getScale(), p.getBodyYaw(), t);
        CampfireMotion.Pose pose = FX_POSE;
        CampfireMotion.sample(t, CampfireLogic.burns(seat.member().id, seedMs, CampfireMotion.pass(t)),
                CampfireMotion.farness(f.fireDistance), pose);
        CampfireMotion.stickTarget(pose, f.fireTarget, FX_TARGET);
        float pitch = pose.rArmPitch;
        CampfireMotion.aimStick(-CampfireMotion.SHOULDER_X, CampfireMotion.SHOULDER_Y_WIDE + pose.armDY, pose.bodyDZ,
                pitch, pose.rArmYaw, pose.rArmRoll, CampfireMotion.GRIP_X_WIDE, FX_TARGET[0], FX_TARGET[1], FX_TARGET[2],
                FX_STICK);
        double[] m = FX_STICK.mallow;
        return HighFiveLogic.modelToWorld(m[0], m[1], m[2], p.getX(), p.getY(), p.getZ(), f.renderYaw, p.getScale());
    }
}

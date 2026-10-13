package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.shop.CaeserApi;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Hängematten-Emote (nur Darstellung auf dem Client, keine Blöcke, keine Pakete an den Minecraft-Server, kein Licht).
 * Jeder Spieler hat seine eigene Hängematte; es gibt keine gemeinsame Logik zwischen Spielern und keine eigenen
 * Sync-Nachrichten (nur das vorhandene {@code emote} PLAY/STOP).
 * <ul>
 *   <li>Feste Ausrichtung: Pro Emote (UUID + Startzeit) werden Körper-Yaw und Position gemerkt (lokal sofort beim Start,
 *       fremde Spieler im ersten Frame bzw. Tick, in dem das Emote gesehen wird). Pfosten und Tuch stehen damit fest in
 *       der Welt; der Spieler wird mit dieser Ausrichtung gerendert (Gegendrehung in {@code setupTransforms}).</li>
 *   <li>Zeitabhängiges (Erscheinen, Schaukeln, Strecken, Knarzen) kommt aus {@link HammockMotion} und hängt nur von der
 *       Emote-Zeit ab.</li>
 *   <li>Partikel und Sounds nur bis 32 Blöcke Entfernung.</li>
 * </ul>
 */
public final class HammockCoordinator {

    private HammockCoordinator() {}

    /** Effekte nur in diesem Umkreis um den lokalen Spieler (Blöcke). */
    private static final double FX_RANGE = 32.0;
    /** So lange nach dem Start gilt ein Emote noch als „frisch“ (Effekte ab 0 auswerten, s). */
    private static final float FRESH_S = 1.5F;
    /** Ein fallendes Blatt alle LEAF_EVERY s (ab LEAF_FIRST), an einem der Pfosten. */
    private static final float LEAF_FIRST = 3.0F, LEAF_EVERY = 6.5F;
    private static final int LEAF_COLOR = 0xFF7DA43C;

    /** Beim Start gemerkt: Startzeit, Füße (Welt) und Körper-Yaw. */
    private record Anchor(long startMs, double x, double y, double z, float yaw) {}

    private static final Map<String, Anchor> ANCHORS = new HashMap<>();
    /** Zuletzt ausgewertete Emote-Zeit pro Spieler (für Ereignisse wie Knarzen). */
    private static final Map<String, Float> LAST_T = new HashMap<>();
    private static final Set<String> TICK_SEEN = new HashSet<>();
    private static final Random RNG = new Random();
    private static ClientWorld lastWorld;

    // ------------------------------------------------------------------
    // Abfragen für Renderer, Animator und Kamera
    // ------------------------------------------------------------------

    /** Darstellungsrahmen eines Spielers mit HAMMOCK für diesen Frame (wiederverwendbar). */
    public static final class Frame {
        /** Ausrichtung der Hängematte (Grad, Minecraft-Yaw) und Gegendrehung gegenüber dem Vanilla-Körper-Yaw. */
        public float renderYaw, counterYaw;
        /** Ursprung der Hängematte relativ zu den aktuellen Füßen, in Rahmen-Achsen (Blöcke). */
        public final double[] off = new double[3];
        public boolean preview;
        public float t;
    }

    private static final Frame SCRATCH = new Frame();

    /**
     * Rahmen für einen Render-State mit aktivem HAMMOCK, oder {@code null}. Fremde Spieler werden beim ersten Aufruf
     * verankert. In der Vorschau: keine Gegendrehung, Hängematte unter dem Spieler. Wiederverwendetes Objekt.
     */
    public static Frame frame(PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active) {
        if (state == null || active == null || active.emote() != EmoteType.HAMMOCK) return null;
        Frame f = SCRATCH;
        f.t = active.t();
        if (active.preview()) {
            f.preview = true;
            f.renderYaw = state.bodyYaw;
            f.counterYaw = 0.0F;
            f.off[0] = 0.0;
            f.off[1] = 0.0;
            f.off[2] = 0.0;
            return f;
        }
        String id = CaeserApi.uuid32(CaeserPlayerSyncManager.getRenderStateUuid(state));
        if (id == null) return null;
        Anchor a = ANCHORS.get(id);
        if (a == null || a.startMs() != active.startMs()) {
            // Erster Frame, in dem dieses Emote gesehen wird: Ausrichtung und Position festhalten
            a = new Anchor(active.startMs(), state.x, state.y, state.z, state.bodyYaw);
            ANCHORS.put(id, a);
        }
        f.preview = false;
        f.renderYaw = a.yaw();
        f.counterYaw = (float) HammockLogic.wrapDegrees(state.bodyYaw - a.yaw());
        HammockLogic.worldToFrame(a.x(), a.y(), a.z(), state.x, state.y, state.z, a.yaw(), f.off);
        return f;
    }

    /**
     * Zusätzliche Sichtbarkeits-Box für das Frustum-Culling: Jeder Spieler zeichnet seine Hängematte im eigenen
     * Modellraum mit; steht nur ein Pfosten im Bild, würde er sonst samt Hängematte ausgeblendet. {@code null} ohne
     * Hängematte.
     */
    public static Box cullBox(PlayerEntity player) {
        if (ANCHORS.isEmpty() || player == null) return null;
        String id = CaeserApi.uuid32(player.getUuid());
        Anchor a = id != null ? ANCHORS.get(id) : null;
        if (a == null) return null;
        double reach = HammockLogic.POST_X + 0.45;
        double[] l = HammockLogic.frameToWorld(-reach, 0.0, 0.0, a.x(), a.y(), a.z(), a.yaw());
        double[] r = HammockLogic.frameToWorld(reach, 0.0, 0.0, a.x(), a.y(), a.z(), a.yaw());
        double m = 0.7;
        return new Box(Math.min(l[0], r[0]) - m, a.y() - 0.2, Math.min(l[2], r[2]) - m,
                Math.max(l[0], r[0]) + m, a.y() + 2.3, Math.max(l[2], r[2]) + m);
    }

    /** Kamera-Drehpunkt (Welt) für den lokalen Spieler, oder {@code null} ohne eigene Hängematte. */
    public static double[] localCameraPivot(PlayerEntity self) {
        if (self == null) return null;
        String id = CaeserApi.uuid32(self.getUuid());
        Anchor a = id != null ? ANCHORS.get(id) : null;
        if (a == null || a.startMs() != com.bame.client.module.EmoteModule.getStartTimeMillis()) return null;
        double[] c = HammockMotion.cameraPivot();
        double s = self.getScale();
        return HammockLogic.frameToWorld(c[0] * s, c[1] * s, c[2] * s, a.x(), a.y(), a.z(), a.yaw());
    }

    // ------------------------------------------------------------------
    // Start des lokalen Spielers
    // ------------------------------------------------------------------

    /**
     * Ist neben dem lokalen Spieler Platz für die Hängematte? Quer zur Blickrichtung (Pfosten 1,6 Blöcke links und
     * rechts) muss auf Fuß- und Kopfhöhe Luft sein (an beiden Pfosten, den Tuch-Enden und in der Mitte), und unter beiden
     * Pfosten ein fester Block.
     */
    public static boolean hasSpace(ClientPlayerEntity p) {
        if (p == null || p.getEntityWorld() == null) return false;
        var world = p.getEntityWorld();
        for (double[] c : HammockLogic.checkPoints(p.getX(), p.getY(), p.getZ(), p.getBodyYaw())) {
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

    /** Lokaler Start: Körper-Yaw und Position sofort merken. */
    public static void onLocalStart(ClientPlayerEntity p, long startMs) {
        if (p == null) return;
        String id = CaeserApi.uuid32(p.getUuid());
        if (id == null) return;
        ANCHORS.put(id, new Anchor(startMs, p.getX(), p.getY(), p.getZ(), p.getBodyYaw()));
    }

    // ------------------------------------------------------------------
    // Tick: Anker fremder Spieler, Aufräumen, Effekte
    // ------------------------------------------------------------------

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            ANCHORS.clear();
            LAST_T.clear();
            lastWorld = world;
        }
        if (world == null || client.player == null) return;
        if (ANCHORS.isEmpty() && !anyHammock(client, world)) return;

        PlayerEntity self = client.player;
        TICK_SEEN.clear();
        for (PlayerEntity p : world.getPlayers()) {
            UUID uuid = p.getUuid();
            String id = CaeserApi.uuid32(uuid);
            if (id == null) continue;
            EmoteAnimator.ActiveEmote emote = EmoteAnimator.resolveActiveEmote(uuid, p == self);
            if (emote == null || emote.emote() != EmoteType.HAMMOCK) continue;
            TICK_SEEN.add(id);
            Anchor a = ANCHORS.get(id);
            if (a == null || a.startMs() != emote.startMs()) {
                a = new Anchor(emote.startMs(), p.getX(), p.getY(), p.getZ(), p.getBodyYaw());
                ANCHORS.put(id, a);
                LAST_T.remove(id);
            }
            float t = emote.t();
            Float last = LAST_T.get(id);
            // Neu gesehen: frisches Emote ab 0 auswerten, sonst erst ab jetzt
            float t0 = last != null ? last : (t < FRESH_S ? -0.001F : t);
            if (t > t0 && self.squaredDistanceTo(a.x(), a.y(), a.z()) <= FX_RANGE * FX_RANGE) {
                effects(world, a, t0, t);
            }
            LAST_T.put(id, t);
        }
        // Beendete Hängematten vergessen (ohne Rauch-Puff)
        for (Iterator<Map.Entry<String, Anchor>> it = ANCHORS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Anchor> en = it.next();
            if (TICK_SEEN.contains(en.getKey())) continue;
            it.remove();
            LAST_T.remove(en.getKey());
        }
    }

    private static boolean anyHammock(MinecraftClient client, ClientWorld world) {
        for (PlayerEntity p : world.getPlayers()) {
            EmoteAnimator.ActiveEmote e = EmoteAnimator.resolveActiveEmote(p.getUuid(), p == client.player);
            if (e != null && e.emote() == EmoteType.HAMMOCK) return true;
        }
        return false;
    }

    /** Ereignisse im Zeitintervall (t0, t1] der Emote-Zeit. */
    private static void effects(ClientWorld world, Anchor a, float t0, float t1) {
        // Leises Knarzen an den Umkehrpunkten (jeder dritte, gleich auf allen Clients), abwechselnd an einem Pfosten
        if (t1 > HammockMotion.SWAY_START) {
            int n0 = Math.max(0, (int) Math.floor((t0 - HammockMotion.SWAY_START) / (HammockMotion.SWAY_PERIOD * 0.5F)) - 1);
            for (int n = n0; n <= n0 + 4; n++) {
                float te = HammockMotion.extremeTime(n);
                if (te > t1) break;
                if (te <= t0 || !HammockMotion.creaksAt(n)) continue;
                double side = (n / HammockMotion.CREAK_EVERY) % 2 == 0 ? 1.0 : -1.0;
                double[] w = HammockLogic.frameToWorld(side * HammockLogic.ATTACH_X, HammockLogic.AXIS_Y, 0.0,
                        a.x(), a.y(), a.z(), a.yaw());
                world.playSoundClient(w[0], w[1], w[2], SoundEvents.BLOCK_LADDER_STEP, SoundCategory.PLAYERS,
                        0.22F, 0.55F + RNG.nextFloat() * 0.1F, false);
            }
        }
        // Ab und zu ein fallendes Blatt von einer Pfostenspitze
        if (t1 >= LEAF_FIRST) {
            int k1 = (int) Math.floor((t1 - LEAF_FIRST) / LEAF_EVERY);
            float tk = LEAF_FIRST + k1 * LEAF_EVERY;
            if (tk > t0) {
                double side = (k1 & 1) == 0 ? -1.0 : 1.0;
                double[] w = HammockLogic.frameToWorld(side * (HammockLogic.POST_X + 0.15), 2.05,
                        (RNG.nextDouble() - 0.5) * 0.3, a.x(), a.y(), a.z(), a.yaw());
                world.addParticleClient(TintedParticleEffect.create(ParticleTypes.TINTED_LEAVES, LEAF_COLOR),
                        w[0], w[1], w[2], 0.0, 0.0, 0.0);
            }
        }
    }

}

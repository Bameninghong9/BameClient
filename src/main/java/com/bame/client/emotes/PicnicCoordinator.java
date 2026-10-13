package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.shop.CaeserApi;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
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
 * Picknick-Emote (nur Darstellung auf dem Client, keine Blöcke, keine Pakete an den Minecraft-Server). Jeder Spieler hat
 * seine eigene Decke; keine eigenen Sync-Nachrichten (nur das vorhandene {@code emote} PLAY/STOP).
 * <ul>
 *   <li>Feste Ausrichtung wie bei der Hängematte: Position und Körper-Yaw beim Start (lokal sofort, fremde Spieler im
 *       ersten Frame bzw. Tick), Decke und Korb stehen fest in der Welt (Gegendrehung in {@code setupTransforms}).</li>
 *   <li>Zeitabhängiges kommt aus {@link PicnicMotion}; die Ameisen hängen zusätzlich von UUID und gemeinsamer
 *       Zeitbasis ab ({@link CaeserPlayerSyncManager#sharedStartTime}), daher auf allen Clients gleich.</li>
 *   <li>Partikel (Krümel) und Sounds (Essen, Trinken, leise) nur bis 32 Blöcke; kein Rauch-Puff beim Erscheinen oder
 *       Verschwinden.</li>
 * </ul>
 */
public final class PicnicCoordinator {

    private PicnicCoordinator() {}

    private static final double FX_RANGE = 32.0;
    private static final float FRESH_S = 1.5F;

    private record Anchor(long startMs, double x, double y, double z, float yaw) {}

    private static final Map<String, Anchor> ANCHORS = new HashMap<>();
    private static final Map<String, Float> LAST_T = new HashMap<>();
    private static final Set<String> TICK_SEEN = new HashSet<>();
    private static final Random RNG = new Random();
    private static ClientWorld lastWorld;

    /** Darstellungsrahmen eines Spielers mit PICNIC (wiederverwendbar). */
    public static final class Frame {
        public float counterYaw;
        /** Ursprung (Startpunkt) relativ zu den aktuellen Füßen, in Rahmen-Achsen (Blöcke). */
        public final double[] off = new double[3];
        public boolean preview;
        public float t;
        /** UUID32 und gemeinsame Zeitbasis für die Ameisen (Vorschau: null/0). */
        public String id;
        public long seedMs;
    }

    private static final Frame SCRATCH = new Frame();

    public static Frame frame(PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active) {
        if (state == null || active == null || active.emote() != EmoteType.PICNIC) return null;
        Frame f = SCRATCH;
        f.t = active.t();
        if (active.preview()) {
            f.preview = true;
            f.counterYaw = 0.0F;
            f.off[0] = 0.0;
            f.off[1] = 0.0;
            f.off[2] = 0.0;
            f.id = null;
            f.seedMs = 0L;
            return f;
        }
        String id = CaeserApi.uuid32(CaeserPlayerSyncManager.getRenderStateUuid(state));
        if (id == null) return null;
        Anchor a = ANCHORS.get(id);
        if (a == null || a.startMs() != active.startMs()) {
            a = new Anchor(active.startMs(), state.x, state.y, state.z, state.bodyYaw);
            ANCHORS.put(id, a);
        }
        f.preview = false;
        f.counterYaw = (float) HammockLogic.wrapDegrees(state.bodyYaw - a.yaw());
        HammockLogic.worldToFrame(a.x(), a.y(), a.z(), state.x, state.y, state.z, a.yaw(), f.off);
        f.id = id;
        f.seedMs = CaeserPlayerSyncManager.sharedStartTime(id, EmoteType.PICNIC, active.startMs());
        return f;
    }

    /** Sichtbarkeits-Box: Decke mit Korb und sitzendem Spieler. {@code null} ohne Picknick. */
    public static Box cullBox(PlayerEntity player) {
        if (ANCHORS.isEmpty() || player == null) return null;
        String id = CaeserApi.uuid32(player.getUuid());
        Anchor a = id != null ? ANCHORS.get(id) : null;
        if (a == null) return null;
        double h = PicnicLogic.BLANKET_HALF + 0.3;
        double minX = 1e18, minZ = 1e18, maxX = -1e18, maxZ = -1e18;
        for (double fx : new double[]{PicnicLogic.BLANKET_CX - h, PicnicLogic.BLANKET_CX + h}) {
            for (double fz : new double[]{PicnicLogic.BLANKET_CZ - h - 0.5, PicnicLogic.BLANKET_CZ + h}) {
                double[] w = HammockLogic.frameToWorld(fx, 0.0, fz, a.x(), a.y(), a.z(), a.yaw());
                minX = Math.min(minX, w[0]);
                maxX = Math.max(maxX, w[0]);
                minZ = Math.min(minZ, w[2]);
                maxZ = Math.max(maxZ, w[2]);
            }
        }
        return new Box(minX, a.y() - 0.2, minZ, maxX, a.y() + 1.6, maxZ);
    }

    public static double[] localCameraPivot(PlayerEntity self) {
        if (self == null) return null;
        String id = CaeserApi.uuid32(self.getUuid());
        Anchor a = id != null ? ANCHORS.get(id) : null;
        if (a == null || a.startMs() != com.bame.client.module.EmoteModule.getStartTimeMillis()) return null;
        double[] c = PicnicMotion.cameraPivot();
        double s = self.getScale();
        return HammockLogic.frameToWorld(c[0] * s, c[1] * s, c[2] * s, a.x(), a.y(), a.z(), a.yaw());
    }

    // ------------------------------------------------------------------
    // Start des lokalen Spielers
    // ------------------------------------------------------------------

    /** Flache, freie Fläche (ca. 2 × 2) für Decke und Korb: Luft auf Fußhöhe, darunter überall ein fester Block. */
    public static boolean hasSpace(ClientPlayerEntity p) {
        if (p == null || p.getEntityWorld() == null) return false;
        var world = p.getEntityWorld();
        for (double[] c : PicnicLogic.checkPoints(p.getX(), p.getY(), p.getZ(), p.getBodyYaw())) {
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

    public static void onLocalStart(ClientPlayerEntity p, long startMs) {
        if (p == null) return;
        String id = CaeserApi.uuid32(p.getUuid());
        if (id == null) return;
        ANCHORS.put(id, new Anchor(startMs, p.getX(), p.getY(), p.getZ(), p.getBodyYaw()));
    }

    // ------------------------------------------------------------------
    // Tick: Anker fremder Spieler, Aufräumen, Effekte
    // ------------------------------------------------------------------

    private static final PicnicMotion.Pose FX_POSE = new PicnicMotion.Pose();

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            ANCHORS.clear();
            LAST_T.clear();
            lastWorld = world;
        }
        if (world == null || client.player == null) return;
        if (ANCHORS.isEmpty() && !anyPicnic(client, world)) return;

        PlayerEntity self = client.player;
        TICK_SEEN.clear();
        for (PlayerEntity p : world.getPlayers()) {
            UUID uuid = p.getUuid();
            String id = CaeserApi.uuid32(uuid);
            if (id == null) continue;
            EmoteAnimator.ActiveEmote emote = EmoteAnimator.resolveActiveEmote(uuid, p == self);
            if (emote == null || emote.emote() != EmoteType.PICNIC) continue;
            TICK_SEEN.add(id);
            Anchor a = ANCHORS.get(id);
            if (a == null || a.startMs() != emote.startMs()) {
                a = new Anchor(emote.startMs(), p.getX(), p.getY(), p.getZ(), p.getBodyYaw());
                ANCHORS.put(id, a);
                LAST_T.remove(id);
            }
            float t = emote.t();
            Float last = LAST_T.get(id);
            float t0 = last != null ? last : (t < FRESH_S ? -0.001F : t);
            if (t > t0 && self.squaredDistanceTo(a.x(), a.y(), a.z()) <= FX_RANGE * FX_RANGE) {
                effects(world, id, a, emote.startMs(), t0, t);
            }
            LAST_T.put(id, t);
        }
        for (Iterator<Map.Entry<String, Anchor>> it = ANCHORS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Anchor> en = it.next();
            if (TICK_SEEN.contains(en.getKey())) continue;
            // Beendetes Picknick vergessen (ohne Rauch-Puff)
            it.remove();
            LAST_T.remove(en.getKey());
        }
    }

    private static boolean anyPicnic(MinecraftClient client, ClientWorld world) {
        for (PlayerEntity p : world.getPlayers()) {
            EmoteAnimator.ActiveEmote e = EmoteAnimator.resolveActiveEmote(p.getUuid(), p == client.player);
            if (e != null && e.emote() == EmoteType.PICNIC) return true;
        }
        return false;
    }

    /** Ereignisse im Zeitintervall (t0, t1] der Emote-Zeit. */
    private static void effects(ClientWorld world, String id, Anchor a, long startMs, float t0, float t1) {
        // Bissen: leises Essgeräusch und Brotkrümel am Mund
        for (float b : PicnicMotion.BITES) {
            if (!PicnicMotion.crossed(t0, t1, b)) continue;
            double[] m = mouthWorld(a, t1);
            world.playSoundClient(m[0], m[1], m[2], SoundEvents.ENTITY_GENERIC_EAT.value(), SoundCategory.PLAYERS,
                    0.3F, 1.0F + RNG.nextFloat() * 0.2F, false);
            ItemStackParticleEffect crumbs = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BREAD));
            for (int i = 0; i < 4; i++) {
                world.addParticleClient(crumbs, m[0] + (RNG.nextDouble() - 0.5) * 0.12, m[1] - 0.05,
                        m[2] + (RNG.nextDouble() - 0.5) * 0.12, (RNG.nextDouble() - 0.5) * 0.04, 0.03, (RNG.nextDouble() - 0.5) * 0.04);
            }
        }
        // Schlucke: leises Trinkgeräusch
        for (float g : PicnicMotion.GULPS) {
            if (!PicnicMotion.crossed(t0, t1, g)) continue;
            double[] m = mouthWorld(a, t1);
            world.playSoundClient(m[0], m[1], m[2], SoundEvents.ENTITY_GENERIC_DRINK.value(), SoundCategory.PLAYERS,
                    0.25F, 1.0F + RNG.nextFloat() * 0.15F, false);
        }
        // Weggeschnippte Ameise: winziges Geräusch
        long seed = CaeserPlayerSyncManager.sharedStartTime(id, EmoteType.PICNIC, startMs);
        int slot = PicnicLogic.antSlot(t1);
        if (slot >= 0) {
            PicnicLogic.Ant ant = PicnicLogic.ant(id, seed, slot);
            if (ant.flicked() && ant.flickAt() > t0 && ant.flickAt() <= t1) {
                double[] st = PicnicLogic.antState(ant, ant.flickAt(), new double[6]);
                double[] w = HammockLogic.frameToWorld(st[0], st[1], st[2], a.x(), a.y(), a.z(), a.yaw());
                world.playSoundClient(w[0], w[1], w[2], SoundEvents.BLOCK_WOOL_HIT, SoundCategory.PLAYERS,
                        0.15F, 1.8F, false);
            }
        }
    }

    /** Mund des Spielers in Weltkoordinaten (Pose ohne Ameisen; Spieler-Skalierung vernachlässigt). */
    private static double[] mouthWorld(Anchor a, float t) {
        PicnicMotion.Pose p = FX_POSE;
        PicnicMotion.sample(t, p);
        double[] m = PicnicMotion.mouthPoint(p, new double[3]);
        m[2] -= 2.0;
        double[] f = PicnicMotion.modelToFrame(p, m[0], m[1], m[2], new double[3]);
        return HammockLogic.frameToWorld(f[0], f[1], f[2], a.x(), a.y(), a.z(), a.yaw());
    }
}

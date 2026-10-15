package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.shop.CaeserApi;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Hexenbesen-Emote (nur Darstellung auf dem Client). <b>Kein echter Flug:</b> Position, Bewegung und Hitbox des
 * Spielers bleiben unverändert, es gehen keine Pakete an den Minecraft-Server; gezeichnet wird der Spieler nur
 * versetzt (Transformation in {@code setupTransforms}). Keine eigenen Sync-Nachrichten (nur das vorhandene
 * {@code emote} PLAY/STOP).
 * <ul>
 *   <li>Feste Ausrichtung wie bei der Hängematte: Position und Körper-Yaw beim Start (lokal sofort, fremde Spieler im
 *       ersten Frame bzw. Tick). Die Kreisbahn liegt damit fest in der Welt um den Startpunkt.</li>
 *   <li>Bahn, Pose und Zeitpunkte kommen aus {@link WitchBroomMotion}/{@link WitchBroomLogic} und hängen nur von der
 *       Emote-Zeit ab.</li>
 *   <li>Effekte nur bis 32 Blöcke: „Puff“ (lila/grüne Funken, leiser Zauber-Sound) beim Erscheinen, dezenter
 *       Funkenschweif am Reisig (lila/grün/orange, gelegentlich ein kurz glimmender Stern; ca. 1 Partikel pro Tick),
 *       kleiner Puff beim Verschwinden.</li>
 * </ul>
 */
public final class WitchBroomCoordinator {

    private WitchBroomCoordinator() {}

    private static final double FX_RANGE = 32.0;
    private static final float FRESH_S = 1.5F;
    private static final int PURPLE = 0x9B4DFF, GREEN = 0x6CFF4A, ORANGE = 0xFF8A1E;

    private record Anchor(long startMs, double x, double y, double z, float yaw) {}

    private static final Map<String, Anchor> ANCHORS = new HashMap<>();
    private static final Map<String, Float> LAST_T = new HashMap<>();
    /** Zuletzt gesehene Mitte des Besens (Welt) für den Puff beim Verschwinden. */
    private static final Map<String, double[]> LAST_BROOM = new HashMap<>();
    private static final Set<String> TICK_SEEN = new HashSet<>();
    private static final Random RNG = new Random();
    private static ClientWorld lastWorld;

    /** Darstellungsrahmen eines Spielers mit WITCH_BROOM (wiederverwendbar). */
    public static final class Frame {
        public float counterYaw;
        /** Startpunkt relativ zu den aktuellen Füßen, in Rahmen-Achsen (Blöcke). */
        public final double[] off = new double[3];
        public boolean preview;
        public float t;
    }

    private static final Frame SCRATCH = new Frame();

    public static Frame frame(PlayerEntityRenderState state, EmoteAnimator.ActiveEmote active) {
        if (state == null || active == null || active.emote() != EmoteType.WITCH_BROOM) return null;
        Frame f = SCRATCH;
        f.t = active.t();
        if (active.preview()) {
            f.preview = true;
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
            a = new Anchor(active.startMs(), state.x, state.y, state.z, state.bodyYaw);
            ANCHORS.put(id, a);
        }
        f.preview = false;
        f.counterYaw = (float) HammockLogic.wrapDegrees(state.bodyYaw - a.yaw());
        HammockLogic.worldToFrame(a.x(), a.y(), a.z(), state.x, state.y, state.z, a.yaw(), f.off);
        return f;
    }

    /** Sichtbarkeits-Box: Kreisbahn samt Besen und Reiter um den Startpunkt. {@code null} ohne Besen. */
    public static Box cullBox(PlayerEntity player) {
        if (ANCHORS.isEmpty() || player == null) return null;
        String id = CaeserApi.uuid32(player.getUuid());
        Anchor a = id != null ? ANCHORS.get(id) : null;
        if (a == null) return null;
        double r = WitchBroomLogic.RADIUS + 1.1;
        return new Box(a.x() - r, a.y() - 0.1, a.z() - r, a.x() + r, a.y() + 2.3, a.z() + r);
    }

    /** Kamera-Drehpunkt (Welt) für den lokalen Spieler: über der Kreismitte, fest; sonst {@code null}. */
    public static double[] localCameraPivot(PlayerEntity self) {
        if (self == null) return null;
        String id = CaeserApi.uuid32(self.getUuid());
        Anchor a = id != null ? ANCHORS.get(id) : null;
        if (a == null || a.startMs() != com.bame.client.module.EmoteModule.getStartTimeMillis()) return null;
        double[] c = WitchBroomMotion.cameraPivot();
        double s = self.getScale();
        return HammockLogic.frameToWorld(c[0], c[1] * s, c[2], a.x(), a.y(), a.z(), a.yaw());
    }

    // ------------------------------------------------------------------
    // Start des lokalen Spielers
    // ------------------------------------------------------------------

    /** Luft rund um den Startpunkt (Kreisbahn mit Besen und Reiter), keine Flüssigkeit. */
    public static boolean hasSpace(ClientPlayerEntity p) {
        if (p == null || p.getEntityWorld() == null) return false;
        var world = p.getEntityWorld();
        for (double[] c : WitchBroomLogic.checkPoints(p.getX(), p.getY(), p.getZ(), p.getBodyYaw())) {
            BlockPos pos = BlockPos.ofFloored(c[0], c[1], c[2]);
            BlockState at = world.getBlockState(pos);
            if (!at.getCollisionShape(world, pos).isEmpty() || !at.getFluidState().isEmpty()) return false;
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

    private static final WitchBroomMotion.Pose FX_POSE = new WitchBroomMotion.Pose();

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            ANCHORS.clear();
            LAST_T.clear();
            LAST_BROOM.clear();
            lastWorld = world;
        }
        if (world == null || client.player == null) return;
        if (ANCHORS.isEmpty() && !anyBroom(client, world)) return;

        PlayerEntity self = client.player;
        TICK_SEEN.clear();
        for (PlayerEntity p : world.getPlayers()) {
            UUID uuid = p.getUuid();
            String id = CaeserApi.uuid32(uuid);
            if (id == null) continue;
            EmoteAnimator.ActiveEmote emote = EmoteAnimator.resolveActiveEmote(uuid, p == self);
            if (emote == null || emote.emote() != EmoteType.WITCH_BROOM) continue;
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
            boolean inRange = self.squaredDistanceTo(a.x(), a.y(), a.z()) <= FX_RANGE * FX_RANGE;
            if (t > t0 && inRange) effects(world, id, a, p.getScale(), t0, t);
            LAST_T.put(id, t);
        }
        for (Iterator<Map.Entry<String, Anchor>> it = ANCHORS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Anchor> en = it.next();
            if (TICK_SEEN.contains(en.getKey())) continue;
            // Beendet: kleiner Puff an der letzten Stelle des Besens
            double[] b = LAST_BROOM.remove(en.getKey());
            if (b != null && self.squaredDistanceTo(b[0], b[1], b[2]) <= FX_RANGE * FX_RANGE) puff(world, b, 6, false);
            it.remove();
            LAST_T.remove(en.getKey());
        }
    }

    private static boolean anyBroom(MinecraftClient client, ClientWorld world) {
        for (PlayerEntity p : world.getPlayers()) {
            EmoteAnimator.ActiveEmote e = EmoteAnimator.resolveActiveEmote(p.getUuid(), p == client.player);
            if (e != null && e.emote() == EmoteType.WITCH_BROOM) return true;
        }
        return false;
    }

    /** Ereignisse im Zeitintervall (t0, t1] der Emote-Zeit. */
    private static void effects(ClientWorld world, String id, Anchor a, float scale, float t0, float t1) {
        if (!(scale > 0.0F)) scale = 1.0F;
        WitchBroomMotion.Pose p = FX_POSE;
        // Puff beim Erscheinen: an der Mitte des (noch hinter dem Spieler liegenden) Besens
        if (t0 < WitchBroomMotion.PUFF_AT && t1 >= WitchBroomMotion.PUFF_AT) {
            WitchBroomMotion.sample(WitchBroomMotion.PUFF_AT + 0.02F, 0.0F, 0.0F, false, false, false, scale, p);
            double[] c = toWorld(a, WitchBroomMotion.drawnBroomToFrame(p, 0.0, 0.0, 0.2, new double[3]), scale);
            puff(world, c, 14, true);
        }
        WitchBroomMotion.sample(t1, 0.0F, 0.0F, false, false, false, scale, p);
        if (p.broomScale > 0.5F) {
            LAST_BROOM.put(id, toWorld(a, WitchBroomMotion.drawnBroomToFrame(p, 0.0, 0.0, 0.0, new double[3]), scale));
        }
        // Funkenschweif am Reisig: ein Partikel pro Tick (Farbe reihum), gelegentlich ein kurz glimmender Stern
        if (p.flight < WitchBroomMotion.TRAIL_FROM) return;
        double[] tip = toWorld(a, WitchBroomMotion.bristleTip(p, new double[3]), scale);
        int tick = (int) Math.floor(t1 * 20.0F);
        double jx = (RNG.nextDouble() - 0.5) * 0.12 * scale, jy = (RNG.nextDouble() - 0.5) * 0.08 * scale;
        double jz = (RNG.nextDouble() - 0.5) * 0.12 * scale;
        switch (Math.floorMod(tick, 3)) {
            case 0 -> world.addParticleClient(ParticleTypes.WITCH, tip[0] + jx, tip[1] + jy, tip[2] + jz, 0.0, 0.01, 0.0);
            case 1 -> world.addParticleClient(new DustParticleEffect(GREEN, 0.7F), tip[0] + jx, tip[1] + jy, tip[2] + jz,
                    0.0, 0.0, 0.0);
            default -> world.addParticleClient(new DustParticleEffect(ORANGE, 0.7F), tip[0] + jx, tip[1] + jy,
                    tip[2] + jz, 0.0, 0.0, 0.0);
        }
        if (Math.floorMod(tick, WitchBroomMotion.STAR_EVERY) == 0) {
            world.addParticleClient(ParticleTypes.FIREWORK, tip[0] + jz, tip[1] + jy, tip[2] + jx,
                    (RNG.nextDouble() - 0.5) * 0.01, 0.004, (RNG.nextDouble() - 0.5) * 0.01);
        }
    }

    /** Lila/grüne Funken (und beim Erscheinen ein leiser Zauber-Sound). */
    private static void puff(ClientWorld world, double[] c, int n, boolean sound) {
        for (int i = 0; i < n; i++) {
            double vx = (RNG.nextDouble() - 0.5) * 0.08, vy = RNG.nextDouble() * 0.05, vz = (RNG.nextDouble() - 0.5) * 0.08;
            double ox = (RNG.nextDouble() - 0.5) * 0.4, oy = (RNG.nextDouble() - 0.5) * 0.25, oz = (RNG.nextDouble() - 0.5) * 0.4;
            if (i % 3 == 0) {
                world.addParticleClient(ParticleTypes.WITCH, c[0] + ox, c[1] + oy, c[2] + oz, vx, vy, vz);
            } else {
                world.addParticleClient(new DustParticleEffect(i % 3 == 1 ? PURPLE : GREEN, 1.0F), c[0] + ox, c[1] + oy,
                        c[2] + oz, vx, vy, vz);
            }
        }
        if (sound) {
            world.playSoundClient(c[0], c[1], c[2], SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS,
                    0.25F, 1.5F + RNG.nextFloat() * 0.15F, false);
        }
    }

    /** Rahmenpunkt (Einheiten von setupTransforms) in Weltkoordinaten. */
    private static double[] toWorld(Anchor a, double[] f, float scale) {
        return HammockLogic.frameToWorld(f[0] * scale, f[1] * scale, f[2] * scale, a.x(), a.y(), a.z(), a.yaw());
    }
}

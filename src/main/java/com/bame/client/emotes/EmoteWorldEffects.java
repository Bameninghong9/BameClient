package com.bame.client.emotes;

import com.bame.client.shop.CaeserApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Partikel zu Emotes in der Welt, nur auf dem Client. Der Zeitpunkt kommt aus der Emote-Zeit {@code t}, daher sehen
 * alle Caeser-Spieler die Effekte ungefähr gleichzeitig.
 */
public final class EmoteWorldEffects {

    private EmoteWorldEffects() {}

    /** Schweißtropfen ab dieser Wiederholung, danach jede dritte. */
    private static final int SWEAT_FROM_REP = 10;
    private static final int SWEAT_EVERY = 3;
    /** Zeitpunkt innerhalb der Wiederholung (s): unten gehalten. */
    private static final float SWEAT_PHASE = 0.6F;

    private static final Map<String, Integer> LAST_SWEAT = new HashMap<>();

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            LAST_SWEAT.clear();
            return;
        }
        Set<String> active = new HashSet<>();
        for (PlayerEntity p : world.getPlayers()) {
            EmoteAnimator.ActiveEmote emote = EmoteAnimator.resolveActiveEmote(p.getUuid(), p == client.player);
            if (emote == null || emote.emote() != EmoteType.PUSH_UPS) continue;
            String id = CaeserApi.uuid32(p.getUuid());
            if (id == null) continue;
            active.add(id);

            int rep = PushUpMotion.completedReps(emote.t());
            if (rep < SWEAT_FROM_REP || (rep - SWEAT_FROM_REP) % SWEAT_EVERY != 0) continue;
            if (emote.t() < PushUpMotion.repTime(rep, SWEAT_PHASE)) continue;
            Integer last = LAST_SWEAT.get(id);
            if (last != null && last == rep) continue;
            LAST_SWEAT.put(id, rep);
            spawnSweat(world, p, emote.t());
        }
        LAST_SWEAT.keySet().retainAll(active);
    }

    /** Ein Tropfen an der Stirn des gekippten Kopfes. */
    private static void spawnSweat(ClientWorld world, PlayerEntity p, float t) {
        PushUpMotion.Pose pose = new PushUpMotion.Pose();
        PushUpMotion.sample(t, pose);
        float[] head = PushUpMotion.tiltedPoint(PushUpMotion.HEAD_UP_PX, 0.0F, pose.tiltDeg);
        double s = PushUpMotion.BLOCKS_PER_PX * p.getScale();
        double yaw = Math.toRadians(p.getBodyYaw());
        double fwd = head[1] * s;
        double x = p.getX() - Math.sin(yaw) * fwd;
        double z = p.getZ() + Math.cos(yaw) * fwd;
        double y = p.getY() + head[0] * s;
        world.addParticleClient(ParticleTypes.FALLING_WATER, x, y, z, 0.0, 0.0, 0.0);
    }
}

package com.bame.client.killfx;

import com.bame.client.cosmetics.CosmeticsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Erkennt Kills des <b>lokalen</b> Spielers, rein auf dem Client (unabhängig von den Kampf-Modulen):
 * <ol>
 *   <li>Treffer: Nahkampf über {@code ClientPlayerInteractionManager.attackEntity} (Mixin), Fernkampf über eigene
 *       Pfeile/Dreizacke, die an bzw. kurz vor dem Verschwinden in höchstens 1,5 Blöcken an einem Lebewesen mit frisch
 *       gesetztem {@code hurtTime} sind.</li>
 *   <li>Tod: Entity-Status 3 ({@link EntityStatuses#PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES}, Mixin in
 *       {@code ClientPlayNetworkHandler.onEntityStatus}); als Rückfall pro Tick der Übergang lebendig → tot
 *       ({@code isDead()} bzw. {@code deathTime > 0}) gemerkter Opfer. Status 35 (Totem) wird gemerkt und schließt
 *       einen Effekt aus.</li>
 *   <li>Minigames ohne echten Tod: Kill-Nachricht im Chat (abschaltbar), siehe {@link KillLogic#chatMatches}.</li>
 * </ol>
 */
public final class KillDetector {

    private static final KillLogic LOGIC = new KillLogic();
    /** Eigene Projektile (Entity-ID). */
    private static final Set<Integer> OWN_PROJECTILES = new HashSet<>();
    /** Gerade entfernte eigene Projektile: Position und verbleibende Ticks für die Treffer-Prüfung. */
    private static final List<Removed> REMOVED = new ArrayList<>();
    /** Zuletzt gesehener Todes-Zustand gemerkter Opfer (nur Übergang lebendig → tot zählt). */
    private static final Map<UUID, Boolean> DEAD_STATE = new HashMap<>();
    private static long lastPrune;

    private record Removed(Vec3d pos, int[] ticksLeft) {}

    private KillDetector() {}

    // ==========================================
    // Treffer
    // ==========================================

    /** Nahkampf-Treffer (aus dem attackEntity-Mixin, nach der vorhandenen Logik). */
    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity living) recordHit(living);
    }

    private static boolean isCandidate(LivingEntity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e == null || e == mc.player) return false;
        return e instanceof PlayerEntity || e instanceof MobEntity;
    }

    private static void recordHit(LivingEntity e) {
        if (!isCandidate(e) || isDeadNow(e)) return;
        String name = e.getName() != null ? e.getName().getString() : null;
        long now = System.currentTimeMillis();
        LOGIC.recordHit(e.getUuid(), e.getId(), name, e instanceof PlayerEntity, now);
        KillLogic.Hit hit = LOGIC.recentHit(e.getUuid(), now);
        if (hit != null) hit.setPos(e.getX(), e.getY(), e.getZ());
        DEAD_STATE.put(e.getUuid(), Boolean.FALSE);
        // Amboss: Rüstung/Skin des Opfers merken, solange es lebt
        KillVictims.record(e);
    }

    private static boolean isDeadNow(LivingEntity e) {
        return e.isDead() || e.deathTime > 0;
    }

    /** ClientEntityEvents.ENTITY_LOAD: eigene Pfeile und Dreizacke merken. */
    public static void onEntityLoad(Entity entity, ClientWorld world) {
        if (entity instanceof PersistentProjectileEntity p && isOwn(p)) OWN_PROJECTILES.add(p.getId());
    }

    /** ClientEntityEvents.ENTITY_UNLOAD: Ein eigenes Projektil verschwindet (meist beim Treffer). */
    public static void onEntityUnload(Entity entity, ClientWorld world) {
        if (!(entity instanceof PersistentProjectileEntity p)) return;
        boolean known = OWN_PROJECTILES.remove(p.getId());
        if (!known && !isOwn(p)) return;
        Vec3d pos = p.getEntityPos();
        if (!checkProjectileHit(world, pos)) {
            // Schadens-Paket kann im selben oder nächsten Tick nach dem Entfernen kommen
            REMOVED.add(new Removed(pos, new int[]{3}));
        }
    }

    private static boolean isOwn(PersistentProjectileEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            Entity owner = p.getOwner();
            return owner != null && mc.player != null && owner == mc.player;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Lebewesen in höchstens 1,5 Blöcken mit frisch gesetztem hurtTime → Treffer. */
    private static boolean checkProjectileHit(ClientWorld world, Vec3d pos) {
        if (world == null || pos == null) return false;
        double r = KillLogic.PROJECTILE_HIT_RADIUS;
        Box area = new Box(pos.x - r, pos.y - r, pos.z - r, pos.x + r, pos.y + r, pos.z + r);
        boolean hit = false;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area, KillDetector::isCandidate)) {
            double d2 = e.getBoundingBox().squaredMagnitude(pos);
            if (KillLogic.isProjectileHit(d2, e.hurtTime, e.maxHurtTime)) {
                recordHit(e);
                hit = true;
            }
        }
        return hit;
    }

    // ==========================================
    // Tod
    // ==========================================

    /** Aus dem onEntityStatus-Mixin (Haupt-Thread), vor der Vanilla-Verarbeitung. */
    public static void onEntityStatus(Entity entity, byte status) {
        if (!(entity instanceof LivingEntity living)) return;
        long now = System.currentTimeMillis();
        if (status == EntityStatuses.USE_TOTEM_OF_UNDYING) {
            LOGIC.recordTotemPop(living.getUuid(), now);
        } else if (status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) {
            onDeath(living, now);
        }
    }

    private static void onDeath(LivingEntity e, long now) {
        Boolean wasDead = DEAD_STATE.put(e.getUuid(), Boolean.TRUE);
        if (Boolean.TRUE.equals(wasDead)) return; // schon verarbeitet
        KillLogic.Hit hit = LOGIC.recentHit(e.getUuid(), now);
        if (hit == null) return;
        if (LOGIC.onDeath(e.getUuid(), CosmeticsConfig.killFxMobs, now) == KillLogic.Decision.TRIGGER) {
            String name = e instanceof PlayerEntity ? hit.name : null;
            if (name == null && CosmeticsConfig.killFxMobs) name = hit.name;
            KillEffects.onLocalKill(e.getEntityPos(), name, e.getUuid(), e instanceof PlayerEntity);
        }
    }

    // ==========================================
    // Chat
    // ==========================================

    /** ClientReceiveMessageEvents.GAME (bzw. CHAT ohne Absender). */
    public static void onChatMessage(Text message) {
        if (!CosmeticsConfig.killFxChat || message == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.getSession() == null) return;
        if (LOGIC.hitCount() == 0) return;
        String own = mc.getSession().getUsername();
        long now = System.currentTimeMillis();
        KillLogic.Hit hit = LOGIC.onChatMessage(message.getString(), own, now);
        if (hit == null) return;
        DEAD_STATE.put(hit.uuid, Boolean.TRUE);
        Vec3d pos = null;
        Entity e = mc.world.getEntityById(hit.entityId);
        if (e != null && hit.uuid.equals(e.getUuid())) pos = e.getEntityPos();
        if (pos == null && hit.hasPos) {
            // Opfer ist schon weg (z. B. sofort Zuschauer): letzte bekannte Position
            pos = new Vec3d(hit.x, hit.y, hit.z);
        }
        if (pos != null) KillEffects.onLocalKill(pos, hit.name, hit.uuid, hit.player);
    }

    // ==========================================
    // Tick
    // ==========================================

    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();

        // Eigene Projektile: im Flug Treffer erkennen (Dreizacke prallen ab und verschwinden nicht)
        if (!OWN_PROJECTILES.isEmpty()) {
            for (Iterator<Integer> it = OWN_PROJECTILES.iterator(); it.hasNext(); ) {
                Entity e = world.getEntityById(it.next());
                if (!(e instanceof PersistentProjectileEntity p) || p.isRemoved()) {
                    it.remove();
                    continue;
                }
                // Nur fliegende Projektile (steckende Pfeile neben einem gerade getroffenen Lebewesen zählen nicht)
                if (p.getVelocity().lengthSquared() > 0.01) checkProjectileHit(world, p.getEntityPos());
            }
        }
        for (Iterator<Removed> it = REMOVED.iterator(); it.hasNext(); ) {
            Removed r = it.next();
            if (checkProjectileHit(world, r.pos()) || --r.ticksLeft()[0] <= 0) it.remove();
        }

        // Rückfall: Übergang lebendig → tot bei gemerkten Opfern
        if (LOGIC.hitCount() > 0) {
            List<KillLogic.Hit> hits = LOGIC.recentHits(now);
            for (KillLogic.Hit hit : hits) {
                Entity e = world.getEntityById(hit.entityId);
                if (!(e instanceof LivingEntity living) || !hit.uuid.equals(living.getUuid())) continue;
                if (isDeadNow(living)) {
                    onDeath(living, now);
                } else {
                    DEAD_STATE.put(hit.uuid, Boolean.FALSE);
                    if (!living.isInvisible()) hit.setPos(living.getX(), living.getY(), living.getZ());
                    // Amboss: letzte Rüstung vor dem Tod (jeden Tick; der Server leert die Ausrüstung beim Tod)
                    KillVictims.record(living);
                }
            }
        }

        if (now - lastPrune > 2_000L) {
            lastPrune = now;
            LOGIC.prune(now);
            if (DEAD_STATE.size() > 64) DEAD_STATE.clear();
        }
    }

    /** Welt verlassen bzw. gewechselt. */
    public static void reset() {
        if (LOGIC.hitCount() > 0) LOGIC.clear();
        OWN_PROJECTILES.clear();
        REMOVED.clear();
        DEAD_STATE.clear();
    }
}

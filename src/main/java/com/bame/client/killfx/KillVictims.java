package com.bame.client.killfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Aussehen von Kill-Opfern für den Amboss-Effekt, rein auf dem Client:
 * <ul>
 *   <li>Zwischenspeicher der zuletzt gesehenen Rüstung (und des Skins) lebender Spieler in der Nähe (64 Blöcke, alle 4
 *       Ticks, Einträge verfallen nach 10 s). Getroffene Opfer des lokalen Spielers werden zusätzlich jeden Tick
 *       erfasst ({@link KillDetector}). Nach dem Tod wird nichts mehr erfasst (der Server leert die Ausrüstung).</li>
 *   <li>{@link Figure}: Skin und Rüstung für die Figur unter dem Amboss. Ohne jede Rüstung → komplette Lederrüstung.</li>
 *   <li>Skin für Fremde: Spieler in der Welt → Zwischenspeicher → Tab-Liste → asynchrone Profil-Abfrage über den Namen
 *       (bis dahin der Standard-Skin).</li>
 * </ul>
 */
public final class KillVictims {

    public static final long TTL_MS = 10_000L;
    public static final double RANGE = 64.0;
    private static final int MAX_ENTRIES = 128;

    private static final ExpiringCache<Snapshot> BY_NAME = new ExpiringCache<>(TTL_MS, MAX_ENTRIES);
    private static final ExpiringCache<Snapshot> BY_UUID = new ExpiringCache<>(TTL_MS, MAX_ENTRIES);
    /** Ergebnisse der Skin-Abfrage per Name (auch "nicht gefunden"), damit nicht jedes Mal neu gefragt wird. */
    private static final Map<String, CompletableFuture<Optional<SkinTextures>>> NAME_LOOKUPS = new HashMap<>();
    private static int ticks;

    /** Rüstung in der Reihenfolge Kopf, Brust, Beine, Füße (Kopien). */
    record Snapshot(UUID uuid, String name, ItemStack[] armor, SkinTextures skin) {}

    /** Aussehen der Figur. Der Skin kann sich noch ändern, wenn er asynchron nachgeladen wird. */
    public static final class Figure {
        final ItemStack[] armor;
        volatile SkinTextures skin;

        Figure(ItemStack[] armor, SkinTextures skin) {
            this.armor = armor;
            this.skin = skin;
        }
    }

    private KillVictims() {}

    // ==========================================
    // Erfassen
    // ==========================================

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            clear();
            return;
        }
        if (++ticks % 4 != 0) return;
        long now = System.currentTimeMillis();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.squaredDistanceTo(mc.player) > RANGE * RANGE) continue;
            record(p, now);
        }
        if (ticks % 40 == 0) {
            BY_NAME.prune(now);
            BY_UUID.prune(now);
            if (NAME_LOOKUPS.size() > 64) NAME_LOOKUPS.clear();
        }
    }

    /** Lebenden Spieler erfassen (tote bzw. Zuschauer nicht: deren Ausrüstung ist schon geleert). */
    static void record(LivingEntity e, long now) {
        if (!(e instanceof PlayerEntity player)) return;
        if (player.isDead() || player.deathTime > 0 || player.getHealth() <= 0.0F || player.isSpectator()) return;
        ItemStack[] armor = new ItemStack[]{
                player.getEquippedStack(EquipmentSlot.HEAD).copy(),
                player.getEquippedStack(EquipmentSlot.CHEST).copy(),
                player.getEquippedStack(EquipmentSlot.LEGS).copy(),
                player.getEquippedStack(EquipmentSlot.FEET).copy()
        };
        SkinTextures skin = player instanceof AbstractClientPlayerEntity cp ? cp.getSkin() : null;
        String name = player.getGameProfile() != null ? player.getGameProfile().name() : player.getName().getString();
        Snapshot s = new Snapshot(player.getUuid(), name, armor, skin);
        BY_UUID.put(player.getUuid().toString(), s, now);
        if (name != null) BY_NAME.put(name, s, now);
    }

    static void record(LivingEntity e) {
        record(e, System.currentTimeMillis());
    }

    public static void clear() {
        BY_NAME.clear();
        BY_UUID.clear();
        NAME_LOOKUPS.clear();
        ticks = 0;
    }

    // ==========================================
    // Figur
    // ==========================================

    /**
     * Figur für ein Opfer.
     *
     * @param uuid   UUID des Opfers (lokaler Kill) oder {@code null} (fremder Effekt)
     * @param name   Name des Opfers oder {@code null}
     * @param player war das Opfer ein Spieler? Nur dann wird der Skin über den Namen nachgeschlagen.
     */
    static Figure figure(UUID uuid, String name, boolean player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        Snapshot s = uuid != null ? BY_UUID.get(uuid.toString(), now) : null;
        if (s == null && name != null) s = BY_NAME.get(name, now);

        ItemStack[] armor = s != null ? s.armor() : null;
        SkinTextures skin = s != null ? s.skin() : null;

        // Opfer noch in der Welt (fremder Effekt): Skin von dort
        if (skin == null && mc.world != null && name != null) {
            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p.getGameProfile() != null && name.equalsIgnoreCase(p.getGameProfile().name())) {
                    skin = p.getSkin();
                    break;
                }
            }
        }
        // Tab-Liste (Server-Spieler)
        if (skin == null && name != null && mc.getNetworkHandler() != null) {
            try {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(name);
                if (entry != null) skin = entry.getSkinTextures();
            } catch (Throwable ignored) {}
        }
        boolean lookup = skin == null && player && name != null;
        if (skin == null) skin = uuid != null ? DefaultSkinHelper.getSkinTextures(uuid) : DefaultSkinHelper.getSteve();

        Figure figure = new Figure(withFallback(armor), skin);
        if (lookup) lookupSkin(mc, name, figure);
        return figure;
    }

    /** Figur des lokalen Spielers (Testen-Knopf): eigener Skin und eigene Rüstung (ohne Rüstung: Leder). */
    static Figure localFigure() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return new Figure(withFallback(null), DefaultSkinHelper.getSteve());
        ItemStack[] armor = new ItemStack[]{
                mc.player.getEquippedStack(EquipmentSlot.HEAD).copy(),
                mc.player.getEquippedStack(EquipmentSlot.CHEST).copy(),
                mc.player.getEquippedStack(EquipmentSlot.LEGS).copy(),
                mc.player.getEquippedStack(EquipmentSlot.FEET).copy()
        };
        return new Figure(withFallback(armor), mc.player.getSkin());
    }

    /** Rüstungsregel: ohne jede Rüstung → komplette Lederrüstung (Standardfarbe), sonst unverändert. */
    static ItemStack[] withFallback(ItemStack[] armor) {
        boolean head = armor != null && !armor[0].isEmpty();
        boolean chest = armor != null && !armor[1].isEmpty();
        boolean legs = armor != null && !armor[2].isEmpty();
        boolean feet = armor != null && !armor[3].isEmpty();
        if (KillFxTimeline.useLeatherFallback(head, chest, legs, feet)) {
            return new ItemStack[]{
                    new ItemStack(Items.LEATHER_HELMET),
                    new ItemStack(Items.LEATHER_CHESTPLATE),
                    new ItemStack(Items.LEATHER_LEGGINGS),
                    new ItemStack(Items.LEATHER_BOOTS)
            };
        }
        return armor;
    }

    /** Skin über den Namen nachladen (Mojang-Profil, im Hintergrund); bis dahin bleibt der Standard-Skin. */
    private static void lookupSkin(MinecraftClient mc, String name, Figure figure) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        CompletableFuture<Optional<SkinTextures>> future = NAME_LOOKUPS.get(key);
        if (future == null) {
            try {
                future = CompletableFuture
                        .supplyAsync(() -> mc.getApiServices().profileResolver().getProfileByName(name), Util.getDownloadWorkerExecutor())
                        .thenCompose(profile -> profile.isPresent()
                                ? mc.getSkinProvider().fetchSkinTextures(profile.get())
                                : CompletableFuture.completedFuture(Optional.<SkinTextures>empty()))
                        .exceptionally(t -> Optional.empty());
            } catch (Throwable t) {
                return;
            }
            NAME_LOOKUPS.put(key, future);
        }
        future.thenAccept(opt -> opt.ifPresent(s -> figure.skin = s));
    }
}

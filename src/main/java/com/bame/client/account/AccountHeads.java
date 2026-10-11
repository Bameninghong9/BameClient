package com.bame.client.account;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lädt Spielerköpfe für Accounts asynchron über den Skin-Provider von Minecraft.
 * Bis der Skin da ist, wird der Standard-Skin (Steve/Alex je nach UUID) gezeigt.
 */
public final class AccountHeads {
    private static final Map<UUID, SkinTextures> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private AccountHeads() {}

    public static SkinTextures get(UUID uuid) {
        if (uuid == null) return DefaultSkinHelper.getSteve();
        SkinTextures cached = CACHE.get(uuid);
        if (cached != null) return cached;
        load(uuid);
        return DefaultSkinHelper.getSkinTextures(uuid);
    }

    /** Zeichnet Gesicht und Hut-Layer. */
    public static void draw(DrawContext c, UUID uuid, int x, int y, int size, int color) {
        SkinTextures tex = get(uuid);
        try {
            PlayerSkinDrawer.draw(c, tex, x, y, size, color);
        } catch (Throwable t) {
            PlayerSkinDrawer.draw(c, DefaultSkinHelper.getSteve(), x, y, size, color);
        }
    }

    public static void clear() {
        CACHE.clear();
        PENDING.clear();
    }

    private static void load(UUID uuid) {
        if (!PENDING.add(uuid)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        CompletableFuture.supplyAsync(() -> {
                    ProfileResult result = mc.getApiServices().sessionService().fetchProfile(uuid, false);
                    return result != null ? result.profile() : null;
                }, Util.getDownloadWorkerExecutor())
                .thenCompose(profile -> profile != null
                        ? mc.getSkinProvider().fetchSkinTextures(profile)
                        : CompletableFuture.completedFuture(Optional.<SkinTextures>empty()))
                .whenComplete((opt, err) -> {
                    if (err == null && opt != null && opt.isPresent()) {
                        CACHE.put(uuid, opt.get());
                    }
                });
    }
}

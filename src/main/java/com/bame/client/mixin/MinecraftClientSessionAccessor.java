package com.bame.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.realms.RealmsPeriodicCheckers;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.telemetry.TelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/**
 * Setzt die Felder von {@link MinecraftClient}, die im Konstruktor aus der Session abgeleitet werden.
 * Wird ausschließlich vom Account-Wechsel ({@code com.bame.client.account.SessionSwitcher}) benutzt.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientSessionAccessor {
    @Mutable
    @Accessor("session")
    void caeser$setSession(Session session);

    @Mutable
    @Accessor("userApiService")
    void caeser$setUserApiService(UserApiService service);

    @Mutable
    @Accessor("userPropertiesFuture")
    void caeser$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

    @Mutable
    @Accessor("gameProfileFuture")
    void caeser$setGameProfileFuture(CompletableFuture<ProfileResult> future);

    @Mutable
    @Accessor("profileKeys")
    void caeser$setProfileKeys(ProfileKeys keys);

    @Mutable
    @Accessor("socialInteractionsManager")
    void caeser$setSocialInteractionsManager(SocialInteractionsManager manager);

    @Accessor("abuseReportContext")
    void caeser$setAbuseReportContext(AbuseReportContext context);

    @Mutable
    @Accessor("telemetryManager")
    void caeser$setTelemetryManager(TelemetryManager manager);

    @Mutable
    @Accessor("realmsPeriodicCheckers")
    void caeser$setRealmsPeriodicCheckers(RealmsPeriodicCheckers checkers);
}

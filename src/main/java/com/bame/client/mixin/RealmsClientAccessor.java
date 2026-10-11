package com.bame.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.realms.RealmsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * RealmsClient ist ein Singleton, das Session-ID und Namen beim ersten Erzeugen festhält.
 * Nach einem Account-Wechsel wird es durch eine neue Instanz für die neue Session ersetzt.
 */
@Mixin(RealmsClient.class)
public interface RealmsClientAccessor {
    @Accessor("instance")
    static void caeser$setInstance(RealmsClient instance) {
        throw new AssertionError();
    }

    @Invoker("<init>")
    static RealmsClient caeser$create(String sessionId, String username, MinecraftClient client) {
        throw new AssertionError();
    }
}

package com.bame.client.mixin;

import net.minecraft.client.resource.SplashTextResourceSupplier;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SplashTextResourceSupplier.class)
public interface SplashTextResourceSupplierAccessor {
    @Mutable
    @Accessor("session")
    void caeser$setSession(Session session);
}

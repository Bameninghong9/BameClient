package com.bame.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Invoker("doAttack")
    boolean callDoAttack();

    @Invoker("doItemUse")
    void callDoItemUse();

    @Accessor("itemUseCooldown")
    void setItemUseCooldown(int val);
}

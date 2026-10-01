package com.bame.client.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {
    @Invoker("syncSelectedSlot")
    void invokeSyncSelectedSlot();

    @Accessor("lastSelectedSlot")
    void setLastSelectedSlot(int slot);

    @Accessor("lastSelectedSlot")
    int getLastSelectedSlot();
}

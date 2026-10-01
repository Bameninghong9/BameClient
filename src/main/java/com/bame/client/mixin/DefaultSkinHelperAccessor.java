package com.bame.client.mixin;

import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DefaultSkinHelper.class)
public interface DefaultSkinHelperAccessor {
    @Accessor("SKINS")
    static SkinTextures[] getSkins() {
        throw new AssertionError();
    }
}

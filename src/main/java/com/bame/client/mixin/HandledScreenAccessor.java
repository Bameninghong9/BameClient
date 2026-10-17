package com.bame.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lage und Größe des Container-Fensters, damit die Container-Suche ihr Feld darunter (bzw. darüber) setzen kann. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int caeser$getX();

    @Accessor("y")
    int caeser$getY();

    @Accessor("backgroundWidth")
    int caeser$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int caeser$getBackgroundHeight();
}

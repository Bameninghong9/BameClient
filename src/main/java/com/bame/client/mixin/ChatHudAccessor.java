package com.bame.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Sichtbare Chat-Zeilen und Scroll-Stand, damit das Spieler-Auswahlrad nicht über dem Chat-Text reagiert. */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> caeser$getVisibleMessages();

    @Accessor("scrolledLines")
    int caeser$getScrolledLines();
}

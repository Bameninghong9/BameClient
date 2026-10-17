package com.bame.client.mixin;

import com.bame.client.gui.search.ContainerSearch;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Container-Suche: nicht passende Slots ausgrauen bzw. Treffer umrahmen, direkt nachdem die Slots gezeichnet wurden
 * (Matrix ist dann schon um das Fenster verschoben, also Slot-Koordinaten). Damit liegt das Overlay über Item und
 * Stapelzahl, aber unter Hover-Markierung, Item am Mauszeiger und Tooltip. {@code require = 0}: fehlt der
 * Einhängepunkt in einer anderen Version, fehlt nur das Overlay (kein Absturz).
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Inject(method = "renderMain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlots(Lnet/minecraft/client/gui/DrawContext;II)V",
            shift = At.Shift.AFTER), require = 0)
    private void caeser$containerSearchOverlay(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        ContainerSearch.renderSlotOverlay((HandledScreen<?>) (Object) this, context);
    }
}

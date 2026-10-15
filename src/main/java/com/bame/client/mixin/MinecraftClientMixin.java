package com.bame.client.mixin;

import com.bame.client.emotes.HighFiveCoordinator;
import com.bame.client.util.HitchSampler;
import com.bame.client.util.Prof;
import com.bame.client.util.WindowTitleHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    /** Zuletzt an GLFW übergebener Fenstertitel (nur Client-Thread). */
    @Unique
    private static String caeser$appliedTitle = null;

    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void caeser$customWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(WindowTitleHelper.formatWindowTitle());
    }

    /**
     * Vanilla setzt den Fenstertitel bei jedem Screen-Wechsel neu (glfwSetWindowTitle = SetWindowTextW unter Windows),
     * obwohl unser Titel konstant ist. Einen unveränderten Titel nicht erneut an das Betriebssystem schicken; sonst den
     * Aufruf unter {@code caeser_window_title} messen.
     */
    @Inject(method = "updateWindowTitle", at = @At("HEAD"), cancellable = true)
    private void caeser$skipUnchangedTitle(CallbackInfo ci) {
        String title = WindowTitleHelper.formatWindowTitle();
        if (title.equals(caeser$appliedTitle)) {
            ci.cancel();
            return;
        }
        caeser$appliedTitle = title;
        Prof.push("caeser_window_title");
    }

    @Inject(method = "updateWindowTitle", at = @At("RETURN"))
    private void caeser$skipUnchangedTitleEnd(CallbackInfo ci) {
        Prof.pop();
    }

    /**
     * High-Five einschlagen: vor den Vanilla-Tasten auswerten, damit derselbe Tastendruck (Standard F) nicht zusätzlich
     * die Hände tauscht. Ohne sichtbaren Hinweis bleibt die Taste unverändert.
     */
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void caeser$highFiveAcceptKey(CallbackInfo ci) {
        Prof.push("caeser_highfive_key");
        try {
            HighFiveCoordinator.handleAcceptKey((MinecraftClient) (Object) this);
        } finally {
            Prof.pop();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Hitch-Sampler: Frame-Grenzen (nur Zeitstempel). Ende vor dem FPS-Limiter, damit Warten nicht als Arbeit zählt.
    // ------------------------------------------------------------------------------------------------

    @Inject(method = "render(Z)V", at = @At("HEAD"))
    private void caeser$hitchFrameBegin(boolean tick, CallbackInfo ci) {
        if (HitchSampler.isEnabled()) {
            HitchSampler.beginFrame(((MinecraftClient) (Object) this).getOverlay() != null);
        }
    }

    @Inject(method = "render(Z)V", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    private void caeser$hitchFrameIdle(boolean tick, CallbackInfo ci) {
        HitchSampler.markIdle();
    }

    @Inject(method = "render(Z)V", at = @At("RETURN"))
    private void caeser$hitchFrameEnd(boolean tick, CallbackInfo ci) {
        HitchSampler.endFrame();
    }

    // ------------------------------------------------------------------------------------------------
    // Diagnose: eigene Abschnitte für die Schritte von setScreen (Vanilla-Code, der beim Öffnen eines Screens läuft):
    // ... > caeser_diag_set_screen > caeser_diag_screen_removed | caeser_diag_unlock_cursor | caeser_diag_unpress_keys |
    //       caeser_diag_screen_init | caeser_diag_lock_cursor | caeser_window_title
    // Die inneren Paare mit require = 0: fehlt ein Ziel, entfällt nur dieser Abschnitt (Push und Pop gemeinsam).
    // ------------------------------------------------------------------------------------------------

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void caeser$diagSetScreenBegin(Screen screen, CallbackInfo ci) {
        HitchSampler.noteScreenChange(((MinecraftClient) (Object) this).currentScreen, screen);
        Prof.push("caeser_diag_set_screen");
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void caeser$diagSetScreenEnd(Screen screen, CallbackInfo ci) {
        Prof.pop();
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;removed()V"))
    private void caeser$diagRemovedBegin(Screen screen, CallbackInfo ci) {
        Prof.push("caeser_diag_screen_removed");
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;removed()V", shift = At.Shift.AFTER))
    private void caeser$diagRemovedEnd(Screen screen, CallbackInfo ci) {
        Prof.pop();
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;unlockCursor()V"))
    private void caeser$diagUnlockBegin(Screen screen, CallbackInfo ci) {
        Prof.push("caeser_diag_unlock_cursor");
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;unlockCursor()V", shift = At.Shift.AFTER))
    private void caeser$diagUnlockEnd(Screen screen, CallbackInfo ci) {
        Prof.pop();
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;unpressAll()V"))
    private void caeser$diagUnpressBegin(Screen screen, CallbackInfo ci) {
        Prof.push("caeser_diag_unpress_keys");
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;unpressAll()V", shift = At.Shift.AFTER))
    private void caeser$diagUnpressEnd(Screen screen, CallbackInfo ci) {
        Prof.pop();
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;init(II)V"))
    private void caeser$diagInitBegin(Screen screen, CallbackInfo ci) {
        Prof.push("caeser_diag_screen_init");
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;init(II)V", shift = At.Shift.AFTER))
    private void caeser$diagInitEnd(Screen screen, CallbackInfo ci) {
        Prof.pop();
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;lockCursor()V"))
    private void caeser$diagLockBegin(Screen screen, CallbackInfo ci) {
        Prof.push("caeser_diag_lock_cursor");
    }

    @Inject(method = "setScreen", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;lockCursor()V", shift = At.Shift.AFTER))
    private void caeser$diagLockEnd(Screen screen, CallbackInfo ci) {
        Prof.pop();
    }
}

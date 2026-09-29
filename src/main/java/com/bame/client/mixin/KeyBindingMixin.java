package com.bame.client.mixin;

import com.bame.client.module.CpsModule;
import com.bame.client.module.InvMoveModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
    @Shadow protected InputUtil.Key boundKey;

    @Inject(method = "onKeyPressed", at = @At("HEAD"))
    private static void onKeyPressed(InputUtil.Key key, CallbackInfo ci) {
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            if (key.getCode() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                CpsModule.registerClick(false);
            } else if (key.getCode() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                CpsModule.registerClick(true);
            }
        }
    }

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void onIsPressed(CallbackInfoReturnable<Boolean> cir) {
        if (InvMoveModule.shouldOverrideKey((KeyBinding)(Object)this)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getWindow() != null && this.boundKey != null && this.boundKey.getCode() != -1) {
                if (InputUtil.isKeyPressed(client.getWindow(), this.boundKey.getCode())) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}

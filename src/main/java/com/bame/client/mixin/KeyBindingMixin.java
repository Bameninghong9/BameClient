package com.bame.client.mixin;

import com.bame.client.module.CpsModule;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
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
}

package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class InvMoveModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static boolean jump = true;
    public static boolean sprint = true;
    public static boolean sneak = false;
    public static boolean rotateWithArrows = true;

    private static boolean wasKeyBindPressed = false;

    public static boolean shouldMove() {
        if (!enabled) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return false;
        if (client.currentScreen == null) return false;

        Screen screen = client.currentScreen;

        // Disallow movement in chat, signs, books
        if (screen instanceof ChatScreen) return false;
        if (screen instanceof AbstractSignEditScreen) return false;
        if (screen instanceof BookEditScreen) return false;

        // Disallow movement if any text field is focused
        Element focused = screen.getFocused();
        if (focused instanceof TextFieldWidget tf) {
            if (tf.isVisible() && tf.isActive()) return false;
        }
        if (focused instanceof com.bame.client.gui.CustomTextFieldWidget ctf) {
            if (ctf.visible && ctf.isFocused()) return false;
        }
        if (focused instanceof EditBoxWidget) {
            return false;
        }

        return true;
    }

    public static boolean shouldOverrideKey(KeyBinding key) {
        if (!shouldMove()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (key == client.options.forwardKey) return true;
        if (key == client.options.backKey) return true;
        if (key == client.options.leftKey) return true;
        if (key == client.options.rightKey) return true;
        if (jump && key == client.options.jumpKey) return true;
        if (sneak && key == client.options.sneakKey) return true;
        if (sprint && key == client.options.sprintKey) return true;
        return false;
    }

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
            && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            com.bame.client.BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;

        // Arrow keys steering while in GUI/Inventory
        if (shouldMove() && rotateWithArrows && client.player != null && client.getWindow() != null) {
            float rotSpeed = 3.0f;
            if (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT)) {
                client.player.setYaw(client.player.getYaw() - rotSpeed);
            }
            if (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT)) {
                client.player.setYaw(client.player.getYaw() + rotSpeed);
            }
            if (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_UP)) {
                client.player.setPitch(Math.clamp(client.player.getPitch() - rotSpeed, -90.0f, 90.0f));
            }
            if (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_DOWN)) {
                client.player.setPitch(Math.clamp(client.player.getPitch() + rotSpeed, -90.0f, 90.0f));
            }
        }
    }

    public static void resetToDefault() {
        jump = true;
        sprint = true;
        sneak = false;
        rotateWithArrows = true;
    }
}

package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

public class HandPositionModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    // Offsets range from -0.80 to +0.80
    public static float posX = 0.0f;  // negative = left / center (Doom), positive = right
    public static float posY = 0.0f;  // negative = lower / down, positive = up
    public static float posZ = 0.0f;  // negative = forward, positive = back
    public static float scale = 1.0f; // 0.30 to 1.50 (30% to 150%)

    // Rotations range from -180 to +180 degrees
    public static float pitch = 0.0f; // tilt forward / backward
    public static float yaw = 0.0f;   // turn left / right
    public static float roll = 0.0f;  // tilt sideways (Katana / Dagger)

    // Swing Styles: 0 = Slash (Default), 1 = Thrust (Stab), 2 = Side Swipe, 3 = Punch (Bashing)
    public static int swingStyle = 0;

    public static boolean applyToOffhand = false;
    public static boolean weaponsOnly = false;

    private static boolean wasKeyBindPressed = false;

    public static boolean shouldApply(ItemStack item) {
        if (!enabled) return false;
        if (weaponsOnly) {
            if (item == null || item.isEmpty()) return false;
            return item.isIn(ItemTags.SWORDS) || item.isIn(ItemTags.AXES);
        }
        return true;
    }

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }

    public static void applyPreset(int index) {
        switch (index) {
            case 0 -> { // Default
                posX = 0.0f;
                posY = 0.0f;
                posZ = 0.0f;
                scale = 1.0f;
                pitch = 0.0f;
                yaw = 0.0f;
                roll = 0.0f;
            }
            case 1 -> { // Lowered (Sweat PvP: sword lowered down, less screen blockage)
                posX = 0.0f;
                posY = -0.30f;
                posZ = 0.0f;
                scale = 0.85f;
                pitch = 0.0f;
                yaw = 0.0f;
                roll = 0.0f;
            }
            case 2 -> { // Doom / Centered (weapon centered in bottom-middle)
                posX = -0.55f;
                posY = -0.15f;
                posZ = -0.10f;
                scale = 0.90f;
                pitch = 0.0f;
                yaw = 0.0f;
                roll = 0.0f;
            }
            case 3 -> { // Compact / Small (minimal screen obstruction)
                posX = 0.05f;
                posY = -0.25f;
                posZ = 0.15f;
                scale = 0.70f;
                pitch = 0.0f;
                yaw = 0.0f;
                roll = 0.0f;
            }
        }
    }

    public static void resetToDefault() {
        applyPreset(0);
        swingStyle = 0;
        applyToOffhand = false;
        weaponsOnly = false;
    }
}

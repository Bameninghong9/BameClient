package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

public class NoFogModule {
    public static boolean enabled = true;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static boolean allFog = true;
    public static boolean netherFog = true;
    public static boolean waterFog = true;
    public static boolean lavaFog = true;

    private static boolean wasKeyBindPressed = false;

    public static boolean shouldDisableFog(Camera camera, ClientWorld world) {
        if (!enabled) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (camera == null && mc != null && mc.gameRenderer != null) {
            camera = mc.gameRenderer.getCamera();
        }
        if (world == null && mc != null) {
            world = mc.world;
        }

        if (camera != null) {
            CameraSubmersionType sub = camera.getSubmersionType();
            if (sub == CameraSubmersionType.LAVA) {
                return lavaFog;
            }
            if (sub == CameraSubmersionType.WATER) {
                return waterFog;
            }
            if (sub == CameraSubmersionType.POWDER_SNOW) {
                return allFog;
            }
        }

        if (world != null) {
            if (world.getRegistryKey().equals(World.NETHER)
                    || (world.getDimension() != null && world.getDimension().hasCeiling())
                    || (world.getRegistryKey().getValue() != null && world.getRegistryKey().getValue().getPath().contains("nether"))) {
                return netherFog;
            }
        }

        return allFog;
    }

    public static boolean shouldDisableFog() {
        return shouldDisableFog(null, null);
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

    public static void resetToDefault() {
        allFog = true;
        netherFog = true;
        waterFog = true;
        lavaFog = true;
    }
}

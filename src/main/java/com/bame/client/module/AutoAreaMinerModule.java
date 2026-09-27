package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class AutoAreaMinerModule {
    public static boolean enabled = false;
    public static boolean wasEnabled = false;
    public static boolean mode3x3 = false;
    public static BlockPos corner1 = null;
    public static BlockPos corner2 = null;
    public static int keyBind = -1;
    private static boolean wasKeyBindPressed;

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
            && net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            if (enabled && (corner1 == null || corner2 == null)) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§c[AutoAreaMiner] Bitte zuerst Corner 1 und Corner 2 festlegen!"), false);
                }
                enabled = false;
            }
        }
        wasKeyBindPressed = pressed;

        if (!enabled) {
            NormalPickaxeMiner.reset(client);
            ThreeByThreeMiner.reset(client);
            wasEnabled = false;
            return;
        }

        if (corner1 == null || corner2 == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[AutoAreaMiner] Bitte zuerst Corner 1 und Corner 2 festlegen!"), false);
            }
            enabled = false;
            wasEnabled = false;
            NormalPickaxeMiner.reset(client);
            ThreeByThreeMiner.reset(client);
            return;
        }

        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        if (mode3x3) {
            NormalPickaxeMiner.reset(client);
            ThreeByThreeMiner.tick(client);
        } else {
            ThreeByThreeMiner.reset(client);
            NormalPickaxeMiner.tick(client);
        }
    }
}
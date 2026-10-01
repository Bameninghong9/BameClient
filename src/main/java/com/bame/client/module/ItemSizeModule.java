package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.util.LinkedHashSet;
import java.util.Set;

public class ItemSizeModule {
    public static boolean enabled = true;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static float scale = 1.5f;   // 0.25f to 4.0f (25% to 400%)
    public static float yOffset = 0.0f; // -0.2f to 1.0f

    public static Set<String> selectedItems = new LinkedHashSet<>();

    private static boolean wasKeyBindPressed = false;

    public static boolean matches(Item item) {
        if (!enabled) return false;
        if (selectedItems.isEmpty()) return true;
        if (item == null) return false;
        String id = Registries.ITEM.getId(item).toString();
        return selectedItems.contains(id);
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
        scale = 1.0f;
        yOffset = 0.0f;
        selectedItems.clear();
    }
}

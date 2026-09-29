package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class AutoClickerModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static int cps = 12; // 6 to 20
    public static boolean randomJitter = true; // +- 1-2 CPS variation
    public static boolean weaponOnly = false;
    public static int button = 0; // 0 = Left Click, 1 = Right Click

    private static boolean wasKeyBindPressed = false;
    private static long nextClickTime = 0;
    private static final Random random = new Random();

    public static void onTick(MinecraftClient client) {
        // Keybind toggle
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;

        if (!enabled || client.player == null || client.world == null || client.currentScreen != null) {
            return;
        }

        // Check if mouse button is held down
        long window = client.getWindow().getHandle();
        boolean isMouseDown = false;
        if (button == 0) {
            isMouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                    || client.options.attackKey.isPressed();
        } else {
            isMouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
                    || client.options.useKey.isPressed();
        }

        if (!isMouseDown) {
            return;
        }

        if (weaponOnly) {
            ItemStack held = client.player.getMainHandStack();
            if (held.isEmpty()) return;
            String itemName = held.getItem().toString().toLowerCase();
            boolean isWeapon = held.isIn(ItemTags.SWORDS) || held.isIn(ItemTags.AXES)
                    || itemName.contains("sword") || itemName.contains("axe") || itemName.contains("mace");
            if (!isWeapon) return;
        }

        long now = System.currentTimeMillis();
        if (now >= nextClickTime) {
            MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;
            if (button == 0) {
                client.attackCooldown = 0;
                accessor.callDoAttack();
                CpsModule.registerClick(false);
            } else {
                accessor.setItemUseCooldown(0);
                accessor.callDoItemUse();
                CpsModule.registerClick(true);
            }

            // Calculate next click delay in ms
            double currentCps = cps;
            if (randomJitter) {
                currentCps += (random.nextDouble() * 3.0 - 1.5); // +-1.5 CPS jitter
                if (currentCps < 1.0) currentCps = 1.0;
            }
            long delay = Math.max(10, Math.round(1000.0 / currentCps));
            nextClickTime = now + delay;
        }
    }

    public static void resetToDefault() {
        cps = 12;
        randomJitter = true;
        weaponOnly = false;
        button = 0;
    }
}

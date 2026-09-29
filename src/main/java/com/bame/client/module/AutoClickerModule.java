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

    // Modes:
    // 0 = CPS (1 to 20 CPS)
    // 1 = Delay (0.1s to 5.0s, e.g. 1.0s = hits once every second)
    // 2 = Cooldown (1.9+ Weapon Full Charge)
    public static int mode = 0;

    public static int cps = 12; // 1 to 20
    public static float delaySeconds = 1.0f; // 0.1s to 5.0s
    public static boolean holdMouse = false; // false = automatically hits continuously; true = only when holding mouse
    public static boolean onlyOnTarget = false; // true = only hits when crosshair aims at an entity
    public static boolean randomJitter = true; // +- 1-2 CPS or +- 50ms variation
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

        // Check if mouse button is held down (if holdMouse is required)
        if (holdMouse) {
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
        }

        // Check if aiming at an entity (if onlyOnTarget is enabled)
        if (onlyOnTarget) {
            boolean hasTarget = client.targetedEntity != null
                    || (client.crosshairTarget != null && client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY);
            if (!hasTarget) {
                return;
            }
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

        // If in Cooldown mode (Mode 2) for Left Click (Attack)
        if (mode == 2 && button == 0) {
            if (client.player.getAttackCooldownProgress(0.0f) < 1.0f) {
                return;
            }
        }

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

            // Calculate next click delay in ms depending on mode
            if (mode == 0) {
                // CPS Mode (1 - 20)
                double currentCps = Math.max(1, cps);
                if (randomJitter) {
                    currentCps += (random.nextDouble() * 3.0 - 1.5); // +-1.5 CPS jitter
                    if (currentCps < 0.5) currentCps = 0.5;
                }
                long delay = Math.max(10, Math.round(1000.0 / currentCps));
                nextClickTime = now + delay;
            } else if (mode == 1) {
                // Delay Mode (0.1s - 5.0s, e.g. 1.0s = hit every second)
                long baseMs = Math.round(Math.max(0.1f, delaySeconds) * 1000.0f);
                if (randomJitter) {
                    long jitter = Math.round(random.nextDouble() * 100.0 - 50.0); // +-50ms
                    baseMs = Math.max(50, baseMs + jitter);
                }
                nextClickTime = now + baseMs;
            } else {
                // Cooldown Mode (1.9+)
                // Small buffer (100ms) to allow cooldown counter to begin ticking
                nextClickTime = now + 100;
            }
        }
    }

    public static void resetToDefault() {
        mode = 0;
        cps = 12;
        delaySeconds = 1.0f;
        holdMouse = false;
        onlyOnTarget = false;
        randomJitter = true;
        weaponOnly = false;
        button = 0;
    }
}

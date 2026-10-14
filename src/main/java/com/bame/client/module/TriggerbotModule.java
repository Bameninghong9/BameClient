package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class TriggerbotModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static boolean showDetails = false;

    // Modes:
    // 0 = 1.9+ Cooldown (Wait for weapon attack charge >= cooldownThreshold)
    // 1 = CPS (Custom CPS click speed)
    public static int mode = 0;

    public static float cooldownThreshold = 0.95f; // 0.8 to 1.0
    public static int cps = 12; // 1 to 20 CPS
    public static boolean playersOnly = true;
    public static boolean weaponOnly = true;
    public static boolean holdAttackKey = false; // Only trigger while holding LMB
    public static double range = 3.2; // 2.5 to 4.5 blocks
    public static boolean randomJitter = true;

    private static boolean wasKeyBindPressed = false;
    private static long nextClickTime = 0;
    private static final Random random = new Random();

    public static void onTick(MinecraftClient client) {
        // Keybind toggle
        // Versteckt (Geheim-Menü): Taste nur aktiv, wenn entsperrt oder das Modul schon läuft
        boolean pressed = BameClientConfig.hiddenModuleHotkeyAllowed(enabled) && keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            com.bame.client.notification.NotificationManager.showModuleToggle("Triggerbot", enabled);
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;

        if (!enabled || client.player == null || client.world == null || client.currentScreen != null) {
            return;
        }

        ClientPlayerEntity player = client.player;

        // Check weapon if required
        if (weaponOnly) {
            ItemStack held = player.getMainHandStack();
            if (held.isEmpty()) return;
            boolean isWeapon = held.isIn(ItemTags.SWORDS)
                    || held.isIn(ItemTags.AXES)
                    || held.isOf(Items.MACE)
                    || held.isOf(Items.TRIDENT);
            if (!isWeapon) return;
        }

        // Check hold attack key
        if (holdAttackKey && client.getWindow() != null) {
            long handle = client.getWindow().getHandle();
            boolean lmb = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                    || (client.options != null && client.options.attackKey.isPressed());
            if (!lmb) return;
        }

        // Target under crosshair
        Entity target = getTarget(client, player);
        if (target == null) return;

        long now = System.currentTimeMillis();

        if (mode == 0) {
            // 1.9+ Attack Cooldown Mode
            if (player.getAttackCooldownProgress(0.0f) < cooldownThreshold) {
                return;
            }
        } else {
            // CPS Mode
            if (now < nextClickTime) {
                return;
            }
            int baseCps = Math.max(1, Math.min(20, cps));
            int delayMs = 1000 / baseCps;
            if (randomJitter) {
                delayMs += random.nextInt(41) - 20; // +- 20ms jitter
            }
            nextClickTime = now + Math.max(40, delayMs);
        }

        // Perform attack
        if (client.interactionManager != null) {
            client.attackCooldown = 0;
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            CpsModule.registerClick(false);
        }
    }

    public static Entity getTarget(MinecraftClient client, ClientPlayerEntity player) {
        Entity target = null;
        if (client.targetedEntity != null) {
            target = client.targetedEntity;
        } else if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY
                && client.crosshairTarget instanceof EntityHitResult eHit) {
            target = eHit.getEntity();
        }

        if (target instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
            if (target == player) return null;
            if (playersOnly && !(target instanceof PlayerEntity)) return null;

            double dist = player.getEyePos().distanceTo(target.getBoundingBox().getCenter());
            if (dist <= range) {
                return target;
            }
        }
        return null;
    }

    public static void applyPreset(int presetId) {
        switch (presetId) {
            case 0 -> { // 1.9+ PvP
                mode = 0;
                cooldownThreshold = 0.95f;
                range = 3.2;
                playersOnly = true;
                weaponOnly = true;
                holdAttackKey = false;
                randomJitter = true;
            }
            case 1 -> { // Legit (LMB)
                mode = 0;
                cooldownThreshold = 0.90f;
                range = 3.0;
                playersOnly = true;
                weaponOnly = true;
                holdAttackKey = true;
                randomJitter = true;
            }
            case 2 -> { // 1.8 CPS
                mode = 1;
                cps = 12;
                range = 3.2;
                playersOnly = true;
                weaponOnly = true;
                holdAttackKey = false;
                randomJitter = true;
            }
            case 3 -> { // Testen (Mobs & All Items)
                mode = 0;
                cooldownThreshold = 0.90f;
                range = 3.5;
                playersOnly = false;
                weaponOnly = false;
                holdAttackKey = false;
                randomJitter = false;
            }
        }
        BameClientConfig.save();
    }

    public static int getActivePreset() {
        if (!playersOnly && !weaponOnly && !holdAttackKey && mode == 0) return 3; // Test
        if (mode == 1 && playersOnly && weaponOnly && !holdAttackKey) return 2; // 1.8 CPS
        if (mode == 0 && holdAttackKey && playersOnly && weaponOnly) return 1; // Legit
        if (mode == 0 && !holdAttackKey && playersOnly && weaponOnly) return 0; // 1.9+ PvP
        return -1;
    }

    public static void resetToDefault() {
        enabled = false;
        keyBind = -1;
        expanded = false;
        showDetails = false;
        mode = 0;
        cooldownThreshold = 0.95f;
        cps = 12;
        playersOnly = true;
        weaponOnly = true;
        holdAttackKey = false;
        range = 3.2;
        randomJitter = true;
        BameClientConfig.save();
    }
}

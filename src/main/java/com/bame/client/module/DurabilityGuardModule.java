package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.CustomGuiUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public class DurabilityGuardModule {
    public static boolean enabled = true;
    public static int keyBind = -1;
    public static boolean expanded = false;

    // 0 = Subtitle, 1 = Actionbar, 2 = Both
    public static int alertMode = 0;

    // Tool ID -> Durability Threshold (warn & cancel when remaining durability <= threshold)
    public static final Map<String, Integer> toolThresholds = new LinkedHashMap<>();

    public static final Identifier ALARM_ID = Identifier.of("bameclient", "alarm");
    public static final SoundEvent ALARM_SOUND = SoundEvent.of(ALARM_ID);

    private static long lastWarningTime = 0;
    private static boolean wasKeyBindPressed = false;

    public static boolean shouldCancel(ItemStack stack) {
        if (!enabled || stack == null || stack.isEmpty()) return false;
        if (!stack.isDamageable()) return false;
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        Integer threshold = toolThresholds.get(id);
        if (threshold == null) return false;
        int remaining = stack.getMaxDamage() - stack.getDamage();
        return remaining <= threshold;
    }

    public static void triggerWarning(ItemStack stack) {
        if (!enabled || stack == null || stack.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        long now = System.currentTimeMillis();
        String toolName = stack.getName().getString();

        // 1. Text alert
        if (client.inGameHud != null) {
            Text alertMsg = Text.literal("Durability Guard canceld (" + toolName + ")")
                    .setStyle(CustomGuiUtils.SANS_STYLE.withColor(Formatting.RED));

            if (alertMode == 0 || alertMode == 2) {
                client.inGameHud.setTitleTicks(5, 40, 10);
                client.inGameHud.setSubtitle(alertMsg);
                client.inGameHud.setTitle(Text.empty());
            }
            if (alertMode == 1 || alertMode == 2) {
                client.inGameHud.setOverlayMessage(alertMsg, false);
            }
        }

        // 2. Alarm sound (2 sec cooldown between plays)
        if (now - lastWarningTime > 2000) {
            lastWarningTime = now;
            try {
                client.getSoundManager().play(PositionedSoundInstance.master(ALARM_SOUND, 1.0f));
            } catch (Exception ignored) {}
        }
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
        alertMode = 0;
        toolThresholds.clear();
    }
}

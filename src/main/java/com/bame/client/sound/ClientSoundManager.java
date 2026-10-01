package com.bame.client.sound;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public class ClientSoundManager {
    public static final String[] SOUND_PACKS = {"Default", "Mechanical", "Pop", "Metallic", "Modern"};
    public static final String[] HOVER_STYLES = {"Soft Tick", "Pop", "Subtle", "Breeze"};

    private static String lastHoveredId = "";
    private static long lastHoverTime = 0;

    public static void playClick() {
        if (!BameClientConfig.moduleSound || BameClientConfig.soundVolume <= 0.001f) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) return;

        SoundEvent event = switch (BameClientConfig.soundPack) {
            case 1 -> SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON;
            case 2 -> SoundEvents.ITEM_BUNDLE_INSERT;
            case 3 -> SoundEvents.BLOCK_TRIPWIRE_CLICK_ON;
            case 4 -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            default -> SoundEvents.UI_BUTTON_CLICK.value();
        };

        float pitch = switch (BameClientConfig.soundPack) {
            case 1 -> 1.3f;
            case 2 -> 1.4f;
            case 3 -> 1.5f;
            case 4 -> 1.8f;
            default -> 1.2f;
        };

        try {
            client.getSoundManager().play(PositionedSoundInstance.master(event, pitch, BameClientConfig.soundVolume));
        } catch (Exception ignored) {}
    }

    public static void playHover(String elementId) {
        if (!BameClientConfig.hoverSound || BameClientConfig.hoverVolume <= 0.001f) return;
        if (elementId == null || elementId.isEmpty() || elementId.equals(lastHoveredId)) return;
        long now = System.currentTimeMillis();
        if (now - lastHoverTime < 50) return;

        lastHoveredId = elementId;
        lastHoverTime = now;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) return;

        SoundEvent event = switch (BameClientConfig.hoverStyle) {
            case 1 -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case 2 -> SoundEvents.BLOCK_LEVER_CLICK;
            case 3 -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
            default -> SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
        };

        float pitch = switch (BameClientConfig.hoverStyle) {
            case 1 -> 2.0f;
            case 2 -> 2.0f;
            case 3 -> 1.9f;
            default -> 2.0f;
        };

        try {
            client.getSoundManager().play(PositionedSoundInstance.master(event, pitch, BameClientConfig.hoverVolume * 0.4f));
        } catch (Exception ignored) {}
    }

    public static void clearHoverIfDifferent(String currentHoveredId) {
        if (currentHoveredId == null || !currentHoveredId.equals(lastHoveredId)) {
            lastHoveredId = currentHoveredId != null ? currentHoveredId : "";
        }
    }
}

package com.bame.client.module;

import com.bame.client.emotes.EmoteType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

public class EmoteModule {
    public static boolean enabled = true;
    public static int keyBind = GLFW.GLFW_KEY_B;
    public static boolean expanded = false;
    public static boolean smoothAnimations = true;

    private static EmoteType currentEmote = EmoteType.NONE;
    private static long startTime = 0;

    public static boolean coinFlipIsKopf = true;
    public static int coinFlipAmount = 99;
    private static boolean coinFlipAnnounced = false;

    public static boolean isEmoting() {
        return enabled && currentEmote != null && currentEmote != EmoteType.NONE && currentEmote != EmoteType.STOP;
    }

    public static EmoteType getCurrentEmote() {
        return currentEmote;
    }

    public static float getElapsedSeconds() {
        if (!isEmoting()) return 0.0f;
        return (System.currentTimeMillis() - startTime) / 1000.0f;
    }

    public static void playEmote(EmoteType type) {
        if (!enabled || type == null || type == EmoteType.NONE || type == EmoteType.STOP) {
            stopEmote();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        currentEmote = type;
        startTime = System.currentTimeMillis();

        if (type == EmoteType.COIN_FLIP) {
            java.util.Random rnd = new java.util.Random();
            coinFlipIsKopf = rnd.nextBoolean();
            coinFlipAmount = 1 + rnd.nextInt(99);
            coinFlipAnnounced = false;
        }
    }

    public static void stopEmote() {
        if (currentEmote == EmoteType.NONE) return;
        currentEmote = EmoteType.NONE;
        startTime = 0;
        coinFlipAnnounced = false;
    }

    public static void onTick(MinecraftClient client) {
        if (!isEmoting() || client.player == null) return;

        boolean canWalk = currentEmote.allowsWalking();

        if (!canWalk) {
            // Stationary emotes (e.g. SIT): cancel if the player moves, jumps or sneaks
            boolean isMoving = client.player.getVelocity().horizontalLengthSquared() > 0.002 ||
                               Math.abs(client.player.getVelocity().y) > 0.08 ||
                               client.player.isSneaking();

            if (isMoving || client.player.isUsingItem() || (getElapsedSeconds() > 0.4f && client.player.handSwinging)) {
                stopEmote();
            }
        } else {
            // Mobile emotes (T-Pose, Wave, Clap, Headspin, Zombie, Dance, Juggle):
            // The player CAN WALK, SPRINT, AND JUMP freely!
            // Only stop if the player is actively using an item (eating, drinking, bow)
            // or attacks/swings after the initial trigger click (> 0.4s)
            if (client.player.isUsingItem() || (getElapsedSeconds() > 0.4f && client.player.handSwinging)) {
                stopEmote();
                return;
            }
        }

        if (currentEmote == EmoteType.COIN_FLIP && !coinFlipAnnounced && getElapsedSeconds() >= 2.3f) {
            coinFlipAnnounced = true;
            try {
                client.getSoundManager().play(
                    net.minecraft.client.sound.PositionedSoundInstance.master(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.6f, 0.9f
                    )
                );
            } catch (Throwable ignored) {}
        }

        // Auto-stop Coin Flip after flip is inspected (one-shot, do not loop)
        if (currentEmote == EmoteType.COIN_FLIP && getElapsedSeconds() >= 3.8f) {
            stopEmote();
        }
    }
}

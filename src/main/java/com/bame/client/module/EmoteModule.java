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

    /** Startzeit des laufenden Emotes in lokaler Zeit (ms), 0 ohne Emote. */
    public static long getStartTimeMillis() {
        return isEmoting() ? startTime : 0L;
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

        // Interne Emotes (High-Five annehmen) nur über playHighFiveAccept, nie aus Menü/Emote-Rad
        if (type.isHidden()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (!com.bame.client.emotes.EmoteUnlockManager.isEmoteUnlocked(type)) {
            try {
                client.getSoundManager().play(
                    net.minecraft.client.sound.PositionedSoundInstance.master(
                        net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f
                    )
                );
                client.player.sendMessage(net.minecraft.text.Text.literal("§c🔒 Dieses Emote ist nicht freigeschaltet!"), true);
            } catch (Throwable ignored) {}
            return;
        }

        // Liegestütze nur im Stehen auf dem Boden
        if (type == EmoteType.PUSH_UPS && !canStartPushUps(client)) {
            playStartDeniedSound(client); // keine Action-Bar bei Push-Ups (Wunsch des Inhabers)
            return;
        }

        // Lagerfeuer: fester Boden, Platz für das Feuer bzw. nah genug an einem vorhandenen Feuer
        if (type == EmoteType.CAMPFIRE) {
            String problem = campfireStartProblem(client);
            if (problem != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal(problem), true);
                return;
            }
        }

        // Hängematte: fester Boden, Platz für Pfosten und Tuch links und rechts
        if (type == EmoteType.HAMMOCK) {
            String problem = hammockStartProblem(client);
            if (problem != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal(problem), true);
                return;
            }
        }

        // Schaukel: fester Boden, Platz für das Gerüst bzw. freier Sitz an einem vorhandenen Gerüst
        if (type == EmoteType.SWING) {
            String problem = swingStartProblem(client);
            if (problem != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal(problem), true);
                return;
            }
        }

        // Picknick: fester, flacher Boden mit Platz für Decke und Korb
        if (type == EmoteType.PICNIC) {
            net.minecraft.client.network.ClientPlayerEntity p = client.player;
            if (!canStartPushUps(client) || p.isTouchingWater() || p.isInLava()
                    || !com.bame.client.emotes.PicnicCoordinator.hasSpace(p)) {
                client.player.sendMessage(net.minecraft.text.Text.literal("§cHier ist kein Platz für das Picnic"), true);
                return;
            }
        }

        // Fliegender Teppich: nur an Land oder beim vom Server erlaubten Fliegen (das Emote schaltet nie Fliegen ein).
        // Ohne Text (keine Action-Bar bei Emotes): nur ein leises Zisch-Geräusch wie beim Hexenbesen
        if (type == EmoteType.FLYING_CARPET && !com.bame.client.emotes.FlyingCarpetCoordinator.canStart(client.player)) {
            playStartDeniedSound(client);
            return;
        }

        // Hexenbesen: fester Boden und Luft rund um den Startpunkt (die Kreisbahn ist nur gezeichnet). Ohne Text: nur
        // ein leises Zisch-Geräusch, wenn es hier nicht geht
        if (type == EmoteType.WITCH_BROOM) {
            net.minecraft.client.network.ClientPlayerEntity p = client.player;
            if (!canStartPushUps(client) || p.isTouchingWater() || p.isInLava()
                    || !com.bame.client.emotes.WitchBroomCoordinator.hasSpace(p)) {
                playStartDeniedSound(client);
                return;
            }
        }

        currentEmote = type;
        startTime = System.currentTimeMillis();
        if (type == EmoteType.CAMPFIRE) {
            // Körper-Yaw und Position sofort merken (feste Ausrichtung von Stamm und Feuer)
            com.bame.client.emotes.CampfireCoordinator.onLocalStart(client.player, startTime);
        }
        if (type == EmoteType.HAMMOCK) {
            // Körper-Yaw und Position sofort merken (Pfosten und Tuch stehen fest in der Welt)
            com.bame.client.emotes.HammockCoordinator.onLocalStart(client.player, startTime);
        }
        if (type == EmoteType.SWING) {
            // Position/Yaw sofort merken und gleich einem Gerüst bzw. freien Sitz zuordnen
            com.bame.client.emotes.SwingCoordinator.onLocalStart(client.player, startTime);
        }
        if (type == EmoteType.PICNIC) {
            com.bame.client.emotes.PicnicCoordinator.onLocalStart(client.player, startTime);
        }
        if (type == EmoteType.WITCH_BROOM) {
            // Startpunkt und Ausrichtung sofort merken (Mitte der gezeichneten Kreisbahn)
            com.bame.client.emotes.WitchBroomCoordinator.onLocalStart(client.player, startTime);
        }
        if (type == EmoteType.FLYING_CARPET) {
            // Fliegender Teppich: Glättung frisch beginnen (Neigung, Welle; ein noch auslaufender Teppich endet)
            com.bame.client.emotes.FlyingCarpetCoordinator.onLocalStart(client.player, startTime);
        }

        com.bame.client.cosmetics.CaeserPlayerSyncManager.broadcastLocalEmote(type, false);
    }

    /** Hinweis, warum das Lagerfeuer hier nicht geht, oder {@code null}. */
    private static String campfireStartProblem(MinecraftClient client) {
        net.minecraft.client.network.ClientPlayerEntity p = client.player;
        if (!canStartPushUps(client) || p.isTouchingWater() || p.isInLava()) {
            return "§cHier ist kein Platz für das Campfire";
        }
        return switch (com.bame.client.emotes.CampfireCoordinator.checkLocalStart(p)) {
            case OK -> null;
            case TOO_FAR -> "§cGeh näher ans Feuer";
            case NO_SPACE -> "§cHier ist kein Platz für das Campfire";
        };
    }

    /** Hinweis, warum die Hängematte hier nicht geht, oder {@code null}. */
    private static String hammockStartProblem(MinecraftClient client) {
        net.minecraft.client.network.ClientPlayerEntity p = client.player;
        if (!canStartPushUps(client) || p.isTouchingWater() || p.isInLava()
                || !com.bame.client.emotes.HammockCoordinator.hasSpace(p)) {
            return "§cHier ist kein Platz für die Hammock";
        }
        return null;
    }

    /** Hinweis, warum die Schaukel hier nicht geht, oder {@code null}. */
    private static String swingStartProblem(MinecraftClient client) {
        net.minecraft.client.network.ClientPlayerEntity p = client.player;
        if (!canStartPushUps(client) || p.isTouchingWater() || p.isInLava()) {
            return "§cHier ist kein Platz für die Swing";
        }
        return switch (com.bame.client.emotes.SwingCoordinator.checkLocalStart(p)) {
            case OK -> null;
            case TOO_FAR -> "§cGeh näher an die Schaukel";
            case OCCUPIED -> "§cDie Schaukel ist besetzt";
            case NO_SPACE -> "§cHier ist kein Platz für die Swing";
        };
    }

    /**
     * Einschlagen bei einem wartenden High-Five (Taste, siehe HighFiveCoordinator). Das Emote ist gratis und versteckt,
     * daher ohne Freischalt-Prüfung. Ersetzt ein laufendes Emote.
     *
     * @return Startzeit (lokale ms) oder 0, wenn nicht möglich
     */
    public static long playHighFiveAccept() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null) return 0L;
        currentEmote = EmoteType.HIGH_FIVE_ACCEPT;
        startTime = System.currentTimeMillis();
        coinFlipAnnounced = false;
        com.bame.client.cosmetics.CaeserPlayerSyncManager.broadcastLocalEmote(EmoteType.HIGH_FIVE_ACCEPT, false);
        return startTime;
    }

    public static void stopEmote() {
        if (currentEmote == EmoteType.NONE) return;
        currentEmote = EmoteType.NONE;
        startTime = 0;
        coinFlipAnnounced = false;
        com.bame.client.cosmetics.CaeserPlayerSyncManager.broadcastLocalEmote(EmoteType.STOP, true);
    }

    /** Emote kann hier nicht starten: leises Zisch-Geräusch statt Text (Emotes zeigen keine Action-Bar-Meldungen). */
    private static void playStartDeniedSound(MinecraftClient client) {
        try {
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                    net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.6f, 0.25f));
        } catch (Throwable ignored) {}
    }

    /** Auf dem Boden stehend, nicht schleichend, schwimmend, fliegend, reitend oder schlafend. */
    private static boolean canStartPushUps(MinecraftClient client) {
        net.minecraft.client.network.ClientPlayerEntity p = client.player;
        return p.isOnGround() && !p.isSneaking() && !p.isSwimming() && !p.isGliding()
                && !p.getAbilities().flying && !p.hasVehicle() && !p.isSleeping();
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
                return;
            }

        } else {
            // Mobile emotes (T-Pose, Wave, Basketball, Wheelchair, Zombie, Juggle):
            // The player CAN WALK, SPRINT, AND JUMP freely!
            // Only stop if the player is actively using an item (eating, drinking, bow)
            // or attacks/swings after the initial trigger click (> 0.4s)
            if (client.player.isUsingItem() || (getElapsedSeconds() > 0.4f && client.player.handSwinging)) {
                stopEmote();
                return;
            }
        }
    }
}

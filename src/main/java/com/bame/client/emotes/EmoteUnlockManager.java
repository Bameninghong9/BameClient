package com.bame.client.emotes;

import com.bame.client.shop.CaeserAuth;
import com.bame.client.shop.CaeserApi;
import com.bame.client.shop.Wallet;

/**
 * Freischaltungen von Cosmetics und Emotes für den <b>lokalen</b> Spieler.
 * <p>
 * Quelle ist das Caeser-Backend ({@link Wallet}): Ein Item ist freigeschaltet, wenn es in {@code owned} steht oder laut
 * Katalog gratis ist ({@code price == 0}). Skins sind immer frei. Solange weder der Server noch der Cache etwas
 * geliefert hat, ist sicherheitshalber alles gesperrt.
 * <p>
 * Die Methodensignaturen sind unverändert, damit CosmeticsTab, EmoteWheelScreen und EmoteModule gleich bleiben.
 */
public class EmoteUnlockManager {

    private EmoteUnlockManager() {}

    /** Beim Spielstart: letzten bekannten Stand (Cache) laden. Die Anmeldung startet nach dem Client-Start. */
    public static void init() {
        Wallet.init();
    }

    /** Neu laden, wenn der letzte Abruf länger als 60 s her ist. Nicht blockierend. */
    public static void checkRefresh() {
        Wallet.refreshIfOlderThan(60_000L);
    }

    /** Sofort neu laden (z. B. beim Server-Beitritt). Nicht blockierend. */
    public static void forceRefresh() {
        Wallet.refresh();
    }

    /**
     * Checks if the currently active Minecraft player has unlocked the given emote.
     */
    public static boolean isEmoteUnlocked(EmoteType emote) {
        if (emote == null || emote == EmoteType.NONE || emote == EmoteType.STOP) {
            return true;
        }
        return Wallet.isOwned(emote.name());
    }

    /**
     * Checks if the currently active player has unlocked the given cosmetic/emote.
     * Supports categories: "EMOTES", "WINGS", "HEAD", "FACE", "BACK", "ACCESSORIES", "PETS", "SKINS".
     */
    public static boolean isCosmeticUnlocked(String category, String id) {
        if (id == null || id.isBlank() || "none".equalsIgnoreCase(id)) {
            return true;
        }
        if (category != null && "SKINS".equalsIgnoreCase(category)) {
            return true;
        }
        return Wallet.isOwned(id);
    }

    /**
     * Gilt nur für den lokalen Spieler: Für andere Namen/UUIDs gibt es keine Freischaltungen mehr (fremde Spieler
     * werden über den Sync-Server bzw. den OwnershipCache geprüft).
     */
    public static boolean isCosmeticUnlocked(String category, String id, String username, String uuidStr) {
        if (id == null || id.isBlank() || "none".equalsIgnoreCase(id)) {
            return true;
        }
        if (category != null && "SKINS".equalsIgnoreCase(category)) {
            return true;
        }
        String local = CaeserAuth.currentSessionUuid32();
        String asked = CaeserApi.uuid32(uuidStr);
        if (asked != null && local != null && !asked.equals(local)) {
            return false;
        }
        return Wallet.isOwned(id);
    }

    public static boolean isEmoteUnlocked(EmoteType emote, String username, String uuidStr) {
        if (emote == null || emote == EmoteType.NONE || emote == EmoteType.STOP) {
            return true;
        }
        return isCosmeticUnlocked("EMOTES", emote.name(), username, uuidStr);
    }
}

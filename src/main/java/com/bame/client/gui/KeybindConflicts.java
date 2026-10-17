package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.module.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Übersicht aller Modul-Tasten und der Menütaste, um doppelt belegte Tasten zu erkennen.
 * Die IDs sind die einfachen Klassennamen der Module (bzw. "menu" für die Menütaste).
 * Versteckte Module (nur im Geheim-Menü) zählen nur, solange das Geheim-Menü entsperrt ist,
 * damit ihr Name im normalen Menü nie auftaucht.
 */
public final class KeybindConflicts {
    public record Entry(String id, String name, IntSupplier key, boolean hidden) {}

    public static final String MENU_ID = "menu";

    private static final List<Entry> ENTRIES = List.of(
            new Entry(MENU_ID, "Menu Key", () -> BameClientConfig.menuBind, false),
            // Combat / Visuals / Movement / Misc / World
            new Entry("AutoClickerModule", "Auto Clicker", () -> AutoClickerModule.keyBind, false),
            new Entry("ReachDisplayModule", "Reach Display", () -> ReachDisplayModule.keyBind, false),
            new Entry("HitColorModule", "Hit Color", () -> HitColorModule.keyBind, false),
            new Entry("LowShieldModule", "Low Shield", () -> LowShieldModule.keyBind, false),
            new Entry("CustomHitboxesModule", "Hitboxes", () -> CustomHitboxesModule.keyBind, false),
            new Entry("InvMoveModule", "Inv Move", () -> InvMoveModule.keyBind, false),
            new Entry("FreelookModule", "Freelook", () -> FreelookModule.keyBind, false),
            new Entry("EmoteModule", "Animations & Emotes", () -> EmoteModule.keyBind, false),
            new Entry("FullbrightModule", "Fullbright", () -> FullbrightModule.keyBind, false),
            new Entry("ShowHudModule", "Show HUD", () -> ShowHudModule.keyBind, false),
            new Entry("ZoomModule", "Zoom", () -> ZoomModule.keyBind, false),
            new Entry("SpotifyHudModule", "Spotify HUD", () -> SpotifyHudModule.keyBind, false),
            new Entry("ScoreboardModule", "Scoreboard", () -> ScoreboardModule.keyBind, false),
            new Entry("CustomCrosshairModule", "Crosshair", () -> CustomCrosshairModule.keyBind, false),
            new Entry("BlockOutlineModule", "Block Outline", () -> BlockOutlineModule.keyBind, false),
            new Entry("HandPositionModule", "Hand Position", () -> HandPositionModule.keyBind, false),
            new Entry("ItemSizeModule", "Item Size", () -> ItemSizeModule.keyBind, false),
            new Entry("TabBadgeModule", "Tablist Badge", () -> TabBadgeModule.keyBind, false),
            new Entry("HideArmorModule", "Hide Armor", () -> HideArmorModule.keyBind, false),
            new Entry("NametagsModule", "Nametags", () -> NametagsModule.keyBind, false),
            new Entry("CustomSkyModule", "Custom Sky", () -> CustomSkyModule.keyBind, false),
            new Entry("CustomFireModule", "Custom Fire", () -> CustomFireModule.keyBind, false),
            new Entry("KeyStrokesModule", "Key Strokes", () -> KeyStrokesModule.keyBind, false),
            new Entry("FpsModule", "FPS", () -> FpsModule.keyBind, false),
            new Entry("PingModule", "Ping", () -> PingModule.keyBind, false),
            new Entry("CpsModule", "CPS", () -> CpsModule.keyBind, false),
            new Entry("ServerInfoModule", "Server Info", () -> ServerInfoModule.keyBind, false),
            new Entry("AutoToolModule", "Auto Tool", () -> AutoToolModule.keyBind, false),
            new Entry("DurabilityGuardModule", "Durability Guard", () -> DurabilityGuardModule.keyBind, false),
            new Entry("AutoAreaMinerModule", "Area Miner", () -> AutoAreaMinerModule.keyBind, false),
            new Entry("NoFogModule", "No Fog", () -> NoFogModule.keyBind, false),
            new Entry("TimeChangerModule", "Time Changer", () -> TimeChangerModule.keyBind, false),
            new Entry("SkinProtectModule", "Skin Protect", () -> SkinProtectModule.keyBind, false),
            // Nur im Geheim-Menü
            new Entry("PearlPredictionModule", "Pearl Prediction", () -> PearlPredictionModule.keyBind, true),
            new Entry("AutoCartModule", "Auto Cart", () -> AutoCartModule.keyBind, true),
            new Entry("AutoMaceModule", "Auto Mace", () -> AutoMaceModule.keyBind, true),
            new Entry("DTapModule", "D-Tap", () -> DTapModule.keyBind, true),
            new Entry("TriggerbotModule", "Triggerbot", () -> TriggerbotModule.keyBind, true),
            new Entry("PearlCatchModule", "Pearl Catch", () -> PearlCatchModule.keyBind, true),
            new Entry("AggroPearlModule", "Aggro Pearl", () -> AggroPearlModule.keyBind, true),
            new Entry("ShieldBreakerModule", "Shield Breaker", () -> ShieldBreakerModule.keyBind, true),
            new Entry("NameProtectModule", "Name Protect", () -> NameProtectModule.keyBind, true),
            new Entry("FakeScoreboardModule", "Fake Scoreboard", () -> FakeScoreboardModule.keyBind, true)
    );

    private KeybindConflicts() {}

    private static boolean counts(Entry e) {
        return !e.hidden() || BameClientConfig.secretUnlocked;
    }

    private static boolean isBound(int key) {
        return key >= 0; // -1 / GLFW_KEY_UNKNOWN = nicht belegt
    }

    /** Alle (sichtbaren) Einträge, z. B. für die Tastenübersicht in den Einstellungen. */
    public static List<Entry> visibleEntries() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) if (counts(e)) out.add(e);
        return out;
    }

    /** Name des ersten anderen Eintrags mit derselben Taste, sonst null. */
    public static String conflictFor(String selfId, int key) {
        if (!isBound(key)) return null;
        for (Entry e : ENTRIES) {
            if (e.id().equals(selfId) || !counts(e)) continue;
            if (e.key().getAsInt() == key) return e.name();
        }
        return null;
    }

    /** Anzeigename zu einer ID (oder die ID selbst). */
    public static String nameOf(String id) {
        for (Entry e : ENTRIES) if (e.id().equals(id)) return e.name();
        return id;
    }

    /** Anzahl der aktuell doppelt belegten Tasten (für die Übersicht). */
    public static int conflictCount() {
        int n = 0;
        for (Entry e : ENTRIES) {
            if (counts(e) && conflictFor(e.id(), e.key().getAsInt()) != null) n++;
        }
        return n;
    }

    /** Nach dem Belegen aufrufen: zeigt einen Hinweis, wenn die Taste schon vergeben ist. */
    public static void notifyIfConflict(String selfId, int key) {
        String other = conflictFor(selfId, key);
        if (other != null) {
            com.bame.client.notification.NotificationManager.showInfo("Key already in use",
                    nameOf(selfId) + ": also used by " + other);
        }
    }

    /**
     * Nach dem Belegen ohne bekannte ID: meldet, falls die Taste jetzt mehrfach vergeben ist.
     */
    public static void notifyIfKeyShared(int key) {
        if (!isBound(key)) return;
        List<String> names = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (counts(e) && e.key().getAsInt() == key) names.add(e.name());
        }
        if (names.size() > 1) {
            com.bame.client.notification.NotificationManager.showInfo("Key already in use", String.join(", ", names));
        }
    }
}

package com.bame.client.util;

import com.bame.client.gui.KeybindConflicts;
import com.bame.client.module.EmoteModule;
import com.bame.client.notification.Notification;
import com.bame.client.notification.NotificationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.NarratorMode;
import org.lwjgl.glfw.GLFW;

/**
 * Vanilla-Sprachausgabe (Narrator) und Caeser-Tasten.
 *
 * <p><b>Strg+B:</b> Vanilla schaltet die Sprachausgabe in {@code Keyboard.onKey} mit Strg+B (Mac: Cmd+B) weiter, bei
 * jedem Druck <i>und</i> jeder Tastenwiederholung, solange kein Textfeld fokussiert ist. B ist die Standardtaste des
 * Emote-Rads, Strg die Standard-Sprinttaste: Wer beim Sprinten das Emote-Rad öffnet (B halten, auswählen, loslassen),
 * schaltet sonst unbemerkt die Sprachausgabe um, bei offenem Rad sogar mit jeder Wiederholung erneut. Die Sprachausgabe
 * blockiert dann den Client-Thread (Windows-SAPI), z. B. beim Öffnen der Tab-Liste.
 *
 * <p>Ablauf: {@code KeyboardMixin} ruft am Anfang von {@code Keyboard.onKey} {@link #beginKey} und an jedem Ende
 * {@link #endKey} auf; {@code SimpleOptionMixin} verwirft {@code setValue} der Narrator-Option, solange das Flag
 * gesetzt ist (unabhängig von der genauen Stelle des Umschaltcodes). Unterdrückt wird nur ein Druck von B, der ohne
 * offenen Screen begann, während eine aktive Caeser-Taste auf B liegt, samt seiner Wiederholungen (dann ist meist schon
 * das Emote-Rad offen). Begann der Druck in einem Screen (Chat, Inventar, Optionen ...), bleibt alles wie in Vanilla –
 * außer beim Tippen in die Container-Suche (dort wie in Vanilla-Textfeldern kein Umschalten).
 *
 * <p><b>Hinweis:</b> Ist die Sprachausgabe beim Betreten einer Welt bzw. eines Servers an, erscheint einmal pro
 * Spielsitzung ein Toast, wie man sie ausschaltet.
 */
public final class NarratorGuard {
    /** Nur während {@code Keyboard.onKey} auf dem Client-Thread gesetzt: Narrator-Option jetzt nicht ändern. */
    private static boolean suppressToggle = false;
    /** Der laufende Druck von B begann ohne offenen Screen bei aktiver Caeser-Taste B (gilt auch für Wiederholungen). */
    private static boolean bPressFromGame = false;

    private static boolean hintPending = false;
    private static boolean hintShown = false;

    private NarratorGuard() {}

    /** Anfang von {@code Keyboard.onKey} (jeder Tastendruck, jede Wiederholung, jedes Loslassen). */
    public static void beginKey(int action, KeyInput input) {
        suppressToggle = false; // auch ein nach einer Ausnahme hängengebliebenes Flag zurücksetzen
        if (input == null || input.key() != GLFW.GLFW_KEY_B) return;
        if (action == GLFW.GLFW_RELEASE) {
            bPressFromGame = false; // Loslassen schaltet in Vanilla nicht um
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            MinecraftClient client = MinecraftClient.getInstance();
            bPressFromGame = client != null && client.currentScreen == null && caeserKeyOnB();
        }
        // Druck und Wiederholung: dieselbe Modifier-Abfrage wie Vanilla (Strg, auf dem Mac Cmd)
        suppressToggle = bPressFromGame && input.hasCtrlOrCmd();
        // Wie bei Vanilla-Textfeldern: beim Tippen in die Container-Suche schaltet Strg+B die Sprachausgabe nicht um
        if (!suppressToggle && input.hasCtrlOrCmd() && com.bame.client.gui.search.ContainerSearch.isTyping()) {
            suppressToggle = true;
        }
    }

    /** Jedes Ende von {@code Keyboard.onKey}. */
    public static void endKey() {
        suppressToggle = false;
    }

    /** Für {@code SimpleOptionMixin.setValue}: diese Änderung verwerfen? Ohne laufendes Strg+B sofort {@code false}. */
    public static boolean shouldBlockNarratorChange(Object option) {
        if (!suppressToggle) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.options != null && client.isOnThread()
                && option == client.options.getNarrator();
    }

    /**
     * Reagiert der Caeser Client im Spiel auf B? Emote-Rad nur bei eingeschaltetem Modul (wie seine Tastenabfrage),
     * übrige Modul- und Menütasten wie in der Tastenübersicht ({@link KeybindConflicts}).
     */
    private static boolean caeserKeyOnB() {
        final int b = GLFW.GLFW_KEY_B;
        if (EmoteModule.enabled && EmoteModule.keyBind == b) return true;
        return KeybindConflicts.conflictFor(EmoteModule.class.getSimpleName(), b) != null;
    }

    /** Welt bzw. Server betreten ({@code ClientPlayConnectionEvents.JOIN}). */
    public static void onJoin() {
        if (!hintShown) hintPending = true;
    }

    /** Jeder Client-Tick: den Hinweis erst zeigen, wenn man im Spiel ist (nicht hinter dem Ladebildschirm). */
    public static void tick(MinecraftClient client) {
        if (!hintPending) return;
        if (client.player == null || client.world == null || client.currentScreen != null || client.options == null) return;
        hintPending = false;
        // Ohne verfügbare Sprachausgabe (keine TTS-Bibliothek) wird nichts vorgelesen und nichts ruckelt
        if (client.options.getNarrator().getValue() == NarratorMode.OFF || !client.getNarratorManager().isActive()) return;
        hintShown = true;
        NotificationManager.add(new Notification("Sprachausgabe ist an",
                "Kann ruckeln (z. B. bei Tab). Aus: Optionen → Barrierefreiheit", Notification.Type.INFO, 8000));
    }
}

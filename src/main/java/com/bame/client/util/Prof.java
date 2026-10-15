package com.bame.client.util;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;

/**
 * Benannte Abschnitte im Vanilla-Profiler (F3+L, Debug-Tortendiagramm), damit Messungen die Kosten der Mod-Hooks
 * einzeln zeigen ({@code caeser_...}). Ohne aktiven Profiler liefert {@link Profilers#get()} den Dummy-Profiler: dann
 * kosten push/pop nur einen Methodenaufruf und erzeugen keine Objekte. Namen immer als Konstanten übergeben (keine
 * zusammengesetzten Strings pro Aufruf). Aufrufer sorgen mit {@code try/finally} für ein ausgeglichenes push/pop.
 */
public final class Prof {
    private Prof() {}

    public static Profiler get() {
        return Profilers.get();
    }

    public static void push(String section) {
        Profilers.get().push(section);
    }

    public static void pop() {
        Profilers.get().pop();
    }

    /**
     * Aufgabe für {@code MinecraftClient.execute(...)} in einen eigenen Abschnitt packen (läuft dann unter
     * {@code scheduledExecutables > caeser_task_<name>}). {@code section} muss der vollständige, konstante Name sein.
     */
    public static Runnable task(String section, Runnable body) {
        return () -> {
            Profiler p = Profilers.get();
            p.push(section);
            try {
                body.run();
            } finally {
                p.pop();
            }
        };
    }

    /** HUD-Callback in einen eigenen Abschnitt packen (einmal bei der Registrierung, nicht pro Frame). */
    public static HudRenderCallback hud(String section, HudRenderCallback body) {
        return (context, tickCounter) -> {
            Profiler p = Profilers.get();
            p.push(section);
            try {
                body.onHudRender(context, tickCounter);
            } finally {
                p.pop();
            }
        };
    }
}

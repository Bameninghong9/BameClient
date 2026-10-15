package com.bame.client.util;

import java.util.List;

/**
 * Prüft ohne Minecraft den Aggregator des Hitch-Samplers: Zählung der obersten Frames, inklusive Zählung der
 * Mod-Frames (höchstens einmal pro Sample, auch bei Rekursion), Pfade, Sortierung (Anzahl, dann alphabetisch),
 * Zeilenbegrenzung und dass nur Klasse/Methode/Zeile ausgegeben werden.
 */
public class HitchAggregatorTest {
    private static int checks = 0;

    public static void main(String[] args) {
        frameFormat();
        counting();
        recursionCountedOnce();
        lineLimit();
        emptyAggregator();
        stackTraceElements();
        System.out.println("HITCH AGGREGATOR TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static StackTraceElement el(String cls, String method, int line) {
        // Dateiname absichtlich mit Pfad: darf nie in der Ausgabe landen
        return new StackTraceElement(cls, method, "C:/Users/someone/secret/" + cls + ".java", line);
    }

    private static StackTraceElement[] stack(StackTraceElement... frames) {
        return frames;
    }

    static final StackTraceElement GLFW = el("org.lwjgl.system.JNI", "invokePV", -2);
    static final StackTraceElement UNLOCK = el("net.minecraft.class_312", "method_1610", 442);
    static final StackTraceElement SET_SCREEN = el("net.minecraft.class_310", "method_1507", 1243);
    static final StackTraceElement MENU_INIT = el("com.bame.client.gui.BameClientScreen", "init", 507);
    static final StackTraceElement TICK = el("com.bame.client.BameClient", "lambda$onInitializeClient$5", 434);
    static final StackTraceElement RUN = el("net.minecraft.class_310", "method_1523", 1363);

    private static void frameFormat() {
        check(HitchAggregator.frame(UNLOCK).equals("net.minecraft.class_312.method_1610:442"), "frame format");
        check(HitchAggregator.frame("a.B", "c", 1).equals("a.B.c:1"), "frame format strings");
    }

    private static void counting() {
        HitchAggregator a = new HitchAggregator();
        // 6x im GLFW-Aufruf beim Entsperren der Maus (über das Mod-Menü geöffnet)
        for (int i = 0; i < 6; i++) a.add(stack(GLFW, UNLOCK, SET_SCREEN, TICK, RUN));
        // 3x im init des Mod-Menüs
        for (int i = 0; i < 3; i++) a.add(stack(MENU_INIT, SET_SCREEN, TICK, RUN));
        // 1x reiner Vanilla-Pfad ohne Mod-Frame
        a.add(stack(UNLOCK, SET_SCREEN, RUN));

        check(a.samples() == 10, "samples");
        check(a.samplesWithMod() == 9, "samples with mod frames");
        check(a.selfCount(HitchAggregator.frame(GLFW)) == 6, "self GLFW");
        check(a.selfCount(HitchAggregator.frame(MENU_INIT)) == 3, "self menu init");
        check(a.selfCount(HitchAggregator.frame(UNLOCK)) == 1, "self unlock");
        check(a.modCount(HitchAggregator.frame(TICK)) == 9, "mod inclusive tick");
        check(a.modCount(HitchAggregator.frame(MENU_INIT)) == 3, "mod inclusive menu init");
        check(a.modCount(HitchAggregator.frame(UNLOCK)) == 0, "vanilla frame is no mod frame");

        List<String> lines = a.summarize(30);
        check(lines.get(0).equals("  mod frames in 9/10 samples (90%)"), "mod share line: " + lines.get(0));
        check(lines.get(1).equals("  top self:"), "self header");
        check(lines.get(2).contains("6x") && lines.get(2).endsWith("org.lwjgl.system.JNI.invokePV:-2"), "top self first: " + lines.get(2));
        check(lines.get(3).contains("3x") && lines.get(3).endsWith("com.bame.client.gui.BameClientScreen.init:507"), "top self second: " + lines.get(3));
        check(lines.get(4).contains("1x") && lines.get(4).endsWith("net.minecraft.class_312.method_1610:442"), "top self third");
        int modHeader = lines.indexOf("  top mod (inclusive):");
        check(modHeader == 5, "mod header position");
        check(lines.get(6).contains("9x") && lines.get(6).endsWith("com.bame.client.BameClient.lambda$onInitializeClient$5:434"), "mod first");
        check(lines.get(7).contains("3x") && lines.get(7).endsWith("com.bame.client.gui.BameClientScreen.init:507"), "mod second");
        int pathHeader = lines.indexOf("  top paths (5 frames, top first):");
        check(pathHeader == 8, "path header position");
        check(lines.get(9).contains("6x") && lines.get(9).contains("org.lwjgl.system.JNI.invokePV:-2 < net.minecraft.class_312.method_1610:442 < "),
                "top path: " + lines.get(9));
        for (String l : lines) check(!l.contains("secret") && !l.contains(".java") && !l.contains("Users"), "no file paths: " + l);
    }

    private static void recursionCountedOnce() {
        HitchAggregator a = new HitchAggregator();
        StackTraceElement rec = el("com.bame.client.gui.tab.CosmeticsTab", "layout", 990);
        a.add(stack(rec, rec, rec, RUN));
        a.add(stack(rec, RUN));
        check(a.modCount(HitchAggregator.frame(rec)) == 2, "recursive frame counted once per sample");
        check(a.selfCount(HitchAggregator.frame(rec)) == 2, "self recursive");
    }

    private static void lineLimit() {
        HitchAggregator a = new HitchAggregator();
        for (int i = 0; i < 40; i++) {
            StackTraceElement top = el("pkg.C" + i, "m", i);
            StackTraceElement mod = el("com.bame.client.X" + i, "run", i);
            for (int k = 0; k <= i % 5; k++) a.add(stack(top, mod, RUN));
        }
        List<String> lines = a.summarize(29);
        check(lines.size() <= 29, "line limit 29, got " + lines.size());
        int self = 0;
        boolean inSelf = false;
        for (String l : lines) {
            if (l.equals("  top self:")) { inSelf = true; continue; }
            if (l.startsWith("  top ")) inSelf = false;
            else if (inSelf) self++;
        }
        check(self == HitchAggregator.TOP_SELF, "15 self frames, got " + self);
        // Gleichstand: alphabetisch (pkg.C14 vor pkg.C19 bei je 5 Samples)
        check(lines.get(2).endsWith("pkg.C14.m:14") && lines.get(2).contains("5x"), "tie order: " + lines.get(2));
        check(a.summarize(3).size() == 3, "tiny limit respected");
        check(a.summarize(0).isEmpty(), "zero limit");
    }

    private static void emptyAggregator() {
        HitchAggregator a = new HitchAggregator();
        a.add((StackTraceElement[]) null);
        a.add(new StackTraceElement[0]);
        check(a.samples() == 0, "empty samples ignored");
        List<String> lines = a.summarize(30);
        check(lines.size() == 1 && lines.get(0).contains("no samples"), "empty summary");
    }

    private static void stackTraceElements() {
        // Echter Stack des Test-Threads: muss ohne Fehler verarbeitet werden, Mod-Frames (dieser Test) werden erkannt
        HitchAggregator a = new HitchAggregator();
        a.add(Thread.currentThread().getStackTrace());
        check(a.samples() == 1, "real stack sample");
        check(a.samplesWithMod() == 1, "test class counts as mod frame");
    }
}

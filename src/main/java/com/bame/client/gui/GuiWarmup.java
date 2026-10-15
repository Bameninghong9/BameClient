package com.bame.client.gui;

import com.bame.client.BameClient;
import com.bame.client.util.Prof;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Nimmt dem ersten Öffnen des Mod-Menüs bzw. des Emote-Rads das Klassenladen ab, ohne etwas am Verhalten zu ändern:
 * <ol>
 *   <li>{@link #preloadInBackground()} (Spielstart): lädt alle Klassen der GUI-Pakete in einem Hintergrund-Thread
 *       ({@code Class.forName(name, false, …)} = nur laden/definieren, keine statischen Initialisierer).</li>
 *   <li>{@link #initializeOnce()} (Welt-Beitritt, Client-Thread, ohnehin Ladebildschirm): initialisiert die Klassen,
 *       die beim Öffnen gebraucht werden – also genau die statischen Initialisierer, die sonst beim ersten Öffnen im
 *       Spiel liefen, nur früher und auf demselben Thread.</li>
 * </ol>
 * Das Geheim-Menü wird ausgelassen.
 */
public final class GuiWarmup {
    private static final String[] PACKAGES = {
            "com/bame/client/gui", "com/bame/client/wallpaper", "com/bame/client/notification"
    };

    /** Beim ersten Öffnen benötigte Klassen (Client-Thread, einmal pro Sitzung). */
    private static final String[] OPEN_PATH = {
            "com.bame.client.gui.BameClientScreen",
            "com.bame.client.gui.tab.ColumnTracker",
            "com.bame.client.gui.tab.CombatTab",
            "com.bame.client.gui.tab.MovementTab",
            "com.bame.client.gui.tab.RenderTab",
            "com.bame.client.gui.tab.MiscTab",
            "com.bame.client.gui.tab.WorldTab",
            "com.bame.client.gui.tab.CosmeticsTab",
            "com.bame.client.gui.ThemeSettingsPanel",
            "com.bame.client.gui.AmbientLighting",
            "com.bame.client.gui.Panel3D",
            "com.bame.client.gui.ThreeDGuiManager",
            "com.bame.client.gui.CustomTextFieldWidget",
            "com.bame.client.gui.CustomToggleWidget",
            "com.bame.client.gui.CustomGuiUtils",
            "com.bame.client.gui.RoundedRectGeometry",
            "com.bame.client.gui.AlphaMaskTexture",
            "com.bame.client.gui.GuiTheme",
            "com.bame.client.gui.KeybindConflicts",
            "com.bame.client.gui.OutlineColorPicker",
            "com.bame.client.gui.ShopOverlay",
            "com.bame.client.gui.GuiProfiler",
            "com.bame.client.gui.EmoteWheelScreen",
            "com.bame.client.emotes.EmotePreviewRenderer",
            "com.bame.client.wallpaper.WallpaperManager",
            "com.bame.client.notification.NotificationManager",
            "com.bame.client.sound.ClientSoundManager",
    };

    private static volatile boolean preloadStarted = false;
    private static boolean initialized = false; // nur Client-Thread

    private GuiWarmup() {}

    public static void preloadInBackground() {
        if (preloadStarted) return;
        preloadStarted = true;
        Thread t = new Thread(GuiWarmup::preload, "CaeserClient-GuiPreload");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void preload() {
        ClassLoader loader = GuiWarmup.class.getClassLoader();
        int loaded = 0;
        long start = System.nanoTime();
        for (String name : classNames()) {
            try {
                Class.forName(name, false, loader);
                loaded++;
            } catch (Throwable ignored) {
                // einzelne Klasse nicht ladbar: beim echten Öffnen gilt das normale Verhalten
            }
        }
        BameClient.LOGGER.debug("Caeser Client: preloaded {} GUI classes in {} ms", loaded, (System.nanoTime() - start) / 1_000_000L);
    }

    private static List<String> classNames() {
        List<String> out = new ArrayList<>();
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer("bameclient");
        if (mod.isPresent()) {
            for (String pkg : PACKAGES) {
                try {
                    Optional<Path> dir = mod.get().findPath(pkg);
                    if (dir.isEmpty() || !Files.isDirectory(dir.get())) continue;
                    Path root = dir.get();
                    String base = pkg.replace('/', '.');
                    try (Stream<Path> files = Files.walk(root)) {
                        files.forEach(p -> {
                            String rel = root.relativize(p).toString().replace('\\', '/');
                            if (!rel.endsWith(".class")) return;
                            String name = base + "." + rel.substring(0, rel.length() - 6).replace('/', '.');
                            if (name.contains("SecretScreen")) return;
                            out.add(name);
                        });
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (out.isEmpty()) {
            for (String n : OPEN_PATH) out.add(n);
        }
        return out;
    }

    /** Einmal pro Sitzung auf dem Client-Thread (Welt-Beitritt). Abschnitt {@code caeser_gui_warmup}. */
    public static void initializeOnce() {
        if (initialized) return;
        initialized = true;
        Prof.push("caeser_gui_warmup");
        try {
            ClassLoader loader = GuiWarmup.class.getClassLoader();
            for (String name : OPEN_PATH) {
                try {
                    Class.forName(name, true, loader);
                } catch (Throwable t) {
                    BameClient.LOGGER.debug("Caeser Client: GUI warm-up skipped {} ({})", name, t.toString());
                }
            }
        } finally {
            Prof.pop();
        }
    }
}

package com.bame.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class ScreenshotHelper {

    public static Text enhanceScreenshotMessage(Text message) {
        try {
            if (message == null) return null;
            if (message.getContent() instanceof TranslatableTextContent content) {
                if ("screenshot.success".equals(content.getKey())) {
                    Object[] args = content.getArgs();
                    if (args.length > 0) {
                        String filePath = null;
                        if (args[0] instanceof Text fileText) {
                            if (fileText.getStyle().getClickEvent() instanceof ClickEvent.OpenFile openFile) {
                                filePath = openFile.path();
                            }
                            if (filePath == null) {
                                String filename = fileText.getString();
                                File runDir = MinecraftClient.getInstance().runDirectory;
                                File shot = new File(new File(runDir, "screenshots"), filename);
                                filePath = shot.getAbsolutePath();
                            }
                        } else if (args[0] instanceof String filename) {
                            File runDir = MinecraftClient.getInstance().runDirectory;
                            File shot = new File(new File(runDir, "screenshots"), filename);
                            filePath = shot.getAbsolutePath();
                        }

                        if (filePath != null) {
                            String safePath = filePath.replace('\\', '/');

                            MutableText copyBtn = Text.literal(" [COPY]")
                                .styled(s -> s
                                    .withColor(Formatting.GREEN)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent.RunCommand("/caeser_copy_screenshot " + safePath))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("§aScreenshot in Zwischenablage kopieren")))
                                );

                            MutableText delBtn = Text.literal(" [DELETE]")
                                .styled(s -> s
                                    .withColor(Formatting.RED)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent.RunCommand("/caeser_del_screenshot " + safePath))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("§cScreenshot löschen")))
                                );

                            MutableText openBtn = Text.literal(" [OPEN]")
                                .styled(s -> s
                                    .withColor(Formatting.YELLOW)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent.RunCommand("/caeser_open_screenshot " + safePath))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("§eScreenshot öffnen")))
                                );

                            return Text.empty()
                                .append(message)
                                .append(copyBtn)
                                .append(delBtn)
                                .append(openBtn);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return message;
    }

    /** Minecraft-Ordner "screenshots" (echter Pfad, falls vorhanden). */
    private static Path screenshotsDir() throws java.io.IOException {
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");
        Files.createDirectories(dir);
        return dir.toRealPath();
    }

    /**
     * Prüft einen Pfad aus einem Chat-Befehl: nur existierende Bilddateien (.png/.jpg/.jpeg) direkt oder in
     * Unterordnern des Minecraft-Ordners "screenshots" sind erlaubt. Der Pfad wird normalisiert (echter Pfad,
     * Symlinks und ".." aufgelöst), damit kein Befehl Dateien außerhalb öffnen, kopieren oder löschen kann.
     * @return die Datei oder null (dann wurde schon eine Meldung gezeigt)
     */
    static File resolveScreenshot(String rawPath) {
        try {
            if (rawPath == null) return reject();
            String cleanPath = rawPath.trim();
            if (cleanPath.startsWith("\"") && cleanPath.endsWith("\"") && cleanPath.length() >= 2) {
                cleanPath = cleanPath.substring(1, cleanPath.length() - 1);
            }
            if (cleanPath.isEmpty()) return reject();
            Path candidate = Path.of(cleanPath.replace('/', File.separatorChar));
            if (!Files.isRegularFile(candidate)) {
                sendMessage("§cScreenshot not found");
                return null;
            }
            Path real = candidate.toRealPath();
            if (!isAllowedScreenshot(screenshotsDir(), real)) return reject();
            return real.toFile();
        } catch (Throwable t) {
            sendMessage("§cScreenshot not found");
            return null;
        }
    }

    /** Reine Prüfung (ohne Dateisystem): liegt {@code real} im Ordner {@code dir} und hat eine Bildendung? */
    static boolean isAllowedScreenshot(Path dir, Path real) {
        Path d = dir.toAbsolutePath().normalize();
        Path r = real.toAbsolutePath().normalize();
        if (!r.startsWith(d) || r.equals(d)) return false;
        String name = r.getFileName() == null ? "" : r.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static File reject() {
        sendMessage("§cOnly images inside the screenshots folder are allowed");
        return null;
    }

    /** Öffnet den Screenshot-Ordner selbst (Galerie-Knopf "Open Folder"). */
    public static void openScreenshotsFolder() {
        new Thread(() -> {
            try {
                File dir = screenshotsDir().toFile();
                try {
                    Util.getOperatingSystem().open(dir);
                } catch (Throwable t) {
                    if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir);
                }
            } catch (Throwable t) {
                sendMessage("§cScreenshot folder not found");
            }
        }, "Caeser-Screenshot-Folder").start();
    }

    public static void openScreenshot(String rawPath) {
        new Thread(() -> {
            try {
                File file = resolveScreenshot(rawPath);
                if (file == null) return;

                boolean opened = false;
                try {
                    Util.getOperatingSystem().open(file);
                    opened = true;
                } catch (Throwable ignored) {}

                if (!opened) {
                    String os = System.getProperty("os.name", "").toLowerCase();
                    if (os.contains("win")) {
                        new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", file.getAbsolutePath()).start();
                        opened = true;
                    } else if (os.contains("mac")) {
                        new ProcessBuilder("open", file.getAbsolutePath()).start();
                        opened = true;
                    } else if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(file);
                        opened = true;
                    }
                }
            } catch (Throwable t) {
                sendMessage("§cScreenshot not found");
            }
        }, "Caeser-Screenshot-Open").start();
    }

    public static void copyToClipboard(String rawPath) {
        new Thread(() -> {
            try {
                File file = resolveScreenshot(rawPath);
                if (file == null) return;

                boolean copied = false;

                // 1. Ultra-fast in-process Java AWT clipboard (5-15ms)
                try {
                    System.setProperty("java.awt.headless", "false");
                    BufferedImage image = ImageIO.read(file);
                    if (image != null) {
                        ImageSelection selection = new ImageSelection(image, file);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                        copied = true;
                    }
                } catch (Throwable ignored) {}

                // 2. Fallbacks if in-process AWT clipboard failed
                if (!copied) {
                    String os = System.getProperty("os.name", "").toLowerCase();
                    if (os.contains("win")) {
                        copied = copyViaWindowsPowerShell(file);
                    } else if (os.contains("mac")) {
                        copied = copyViaMacOsascript(file);
                    } else if (os.contains("nix") || os.contains("nux")) {
                        copied = copyViaLinux(file);
                    }
                }

                if (copied) {
                    sendMessage("§aScreenshot copied to Clipboard");
                } else {
                    sendMessage("§cScreenshot not found");
                }
            } catch (Throwable t) {
                sendMessage("§cScreenshot not found");
            }
        }, "Caeser-Screenshot-Copy").start();
    }

    private static boolean copyViaWindowsPowerShell(File file) {
        try {
            String absPath = file.getAbsolutePath().replace("'", "''");
            String script = "Add-Type -AssemblyName System.Windows.Forms;\n" +
                "Add-Type -AssemblyName System.Drawing;\n" +
                "$p = '" + absPath + "';\n" +
                "$img = [System.Drawing.Image]::FromFile($p);\n" +
                "$data = New-Object System.Windows.Forms.DataObject;\n" +
                "$data.SetImage($img);\n" +
                "$files = New-Object System.Collections.Specialized.StringCollection;\n" +
                "$files.Add($p);\n" +
                "$data.SetFileDropList($files);\n" +
                "[System.Windows.Forms.Clipboard]::SetDataObject($data, $true);\n";

            byte[] bytes = script.getBytes(StandardCharsets.UTF_16LE);
            String b64 = Base64.getEncoder().encodeToString(bytes);

            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle",
                "Hidden",
                "-EncodedCommand",
                b64
            );
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean copyViaMacOsascript(File file) {
        try {
            String script = "set the clipboard to (read (POSIX file \"" + file.getAbsolutePath() + "\") as «class PNGf»)";
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            Process process = pb.start();
            return process.waitFor(4, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean copyViaLinux(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder("xclip", "-selection", "clipboard", "-t", "image/png", "-i", file.getAbsolutePath());
            Process process = pb.start();
            if (process.waitFor(4, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            ProcessBuilder pb = new ProcessBuilder("wl-copy", "-t", "image/png");
            pb.redirectInput(file);
            Process process = pb.start();
            if (process.waitFor(4, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void deleteScreenshot(String rawPath) {
        try {
            File file = resolveScreenshot(rawPath);
            if (file == null) return;
            if (file.delete()) {
                sendMessage("§aScreenshot deleted");
            } else {
                sendMessage("§cScreenshot not found");
            }
        } catch (Throwable t) {
            sendMessage("§cScreenshot not found");
        }
    }

    private static void sendMessage(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.send(() -> {
                if (client.inGameHud != null && client.inGameHud.getChatHud() != null) {
                    client.inGameHud.getChatHud().addMessage(Text.literal(msg));
                }
            });
        }
    }
}

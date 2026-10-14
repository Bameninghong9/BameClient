package com.bame.client.spotify;

import com.bame.client.BameClientConfig;
import com.bame.client.module.SpotifyHudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpotifyService {
    public static final Identifier COVER_ID = Identifier.of("caeserclient", "spotify_cover");

    public static volatile String title = "";
    public static volatile String artist = "";
    public static volatile String album = "";
    public static volatile float position = 0f;
    public static volatile float duration = 0f;
    public static volatile boolean isPlaying = false;
    public static volatile boolean hasMedia = false;
    public static volatile long lastUpdateMs = 0;

    public static volatile boolean coverDirty = false;
    public static volatile boolean hasCoverTexture = false;
    public static volatile int coverWidth = 64;
    public static volatile int coverHeight = 64;
    /** Durchschnittsfarbe des aktuellen Covers (ARGB, deckend), für den Card-Stil und den Cover-Akzent. */
    public static volatile int coverColor = 0xFF1DB954;

    /** Steuerbefehle an die aktuelle Windows-Mediensitzung (SMTC), siehe {@link #control(Command)}. */
    public enum Command {
        PLAY_PAUSE("PLAYPAUSE"), NEXT("NEXT"), PREVIOUS("PREV");

        final String token;

        Command(String token) {
            this.token = token;
        }
    }

    private static final java.util.concurrent.ExecutorService CONTROL_EXECUTOR =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SpotifyControlThread");
            t.setDaemon(true);
            return t;
        });
    /** Optimistisch gesetzter Play-Zustand nach einem Klick; veraltete Statuszeilen überschreiben ihn bis dahin nicht. */
    private static volatile Boolean pendingPlaying = null;
    private static volatile long pendingPlayingUntilMs = 0;
    /** Nach Weiter/Zurück bleibt die Position bei 0, bis der Titel wechselt oder die Frist abläuft. */
    private static volatile long pendingSkipUntilMs = 0;

    private static Process trackerProcess = null;
    private static Thread readerThread = null;
    private static boolean running = false;
    private static Path coverPath = null;
    private static Path scriptPath = null;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(SpotifyService::stop));
    }

    public static float getInterpolatedPosition() {
        if (isPlaying && hasMedia && duration > 0) {
            float elapsed = (System.currentTimeMillis() - lastUpdateMs) / 1000.0f;
            return Math.min(duration, position + elapsed);
        }
        return position;
    }

    public static synchronized void start() {
        if (running && trackerProcess != null && trackerProcess.isAlive()) return;

        try {
            Path baseDir = BameClientConfig.BASE_CONFIG_DIR;
            Files.createDirectories(baseDir);
            scriptPath = baseDir.resolve("spotify_tracker.ps1");
            coverPath = baseDir.resolve("spotify_cover.png");

            writeScriptFile(scriptPath);

            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-ExecutionPolicy", "Bypass",
                "-NoProfile",
                "-File", scriptPath.toAbsolutePath().toString(),
                coverPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            trackerProcess = pb.start();
            running = true;

            readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(trackerProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("MEDIA|")) {
                            parseMediaLine(line);
                        } else if (line.startsWith("CTRL|") && line.toLowerCase(java.util.Locale.ROOT).endsWith("|false")) {
                            pendingPlaying = null;
                            showToast("Die aktuelle Medienquelle unterstützt diese Steuerung nicht");
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    running = false;
                }
            }, "SpotifyTrackerThread");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (Exception e) {
            running = false;
        }
    }

    /**
     * Sendet Play/Pause, Weiter oder Zurück an die aktuelle Windows-Mediensitzung (dieselbe, die das HUD anzeigt).
     * Der Befehl geht asynchron über stdin an den laufenden PowerShell-Tracker; der Zustand wird sofort
     * optimistisch angepasst und vom Tracker direkt danach neu gemeldet. Blockiert den Aufrufer nie.
     */
    public static void control(Command cmd) {
        long now = System.currentTimeMillis();
        if (cmd == Command.PLAY_PAUSE) {
            position = getInterpolatedPosition();
            lastUpdateMs = now;
            boolean target = !isPlaying;
            isPlaying = target;
            pendingPlaying = target;
            pendingPlayingUntilMs = now + 1500;
        } else {
            position = 0f;
            lastUpdateMs = now;
            pendingSkipUntilMs = now + 1500;
        }
        CONTROL_EXECUTOR.execute(() -> {
            try {
                start();
                Process p = currentProcess();
                if (p == null || !p.isAlive()) throw new IOException("tracker not running");
                OutputStream out = p.getOutputStream();
                out.write((cmd.token + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                pendingPlaying = null;
                showToast("Mediensteuerung nicht verfügbar");
            }
        });
    }

    private static synchronized Process currentProcess() {
        return trackerProcess;
    }

    private static void showToast(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> com.bame.client.notification.NotificationManager.showInfo("Spotify", message));
        }
    }

    public static synchronized void stop() {
        running = false;
        if (trackerProcess != null) {
            try {
                trackerProcess.destroyForcibly();
            } catch (Exception ignored) {}
            trackerProcess = null;
        }
    }

    private static void parseMediaLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length >= 8) {
            boolean playing = Boolean.parseBoolean(parts[1]);
            float pos = 0f;
            float dur = 0f;
            try {
                pos = Float.parseFloat(parts[2].replace(",", "."));
            } catch (Exception ignored) {}
            try {
                dur = Float.parseFloat(parts[3].replace(",", "."));
            } catch (Exception ignored) {}

            String t = parts[4];
            String a = parts[5];
            String alb = parts[6];
            boolean coverChanged = parts[7].equals("1");

            boolean active = (!t.isEmpty() || dur > 0);
            boolean titleChanged = !t.equals(title) || !a.equals(artist);
            long nowMs = System.currentTimeMillis();
            Boolean pending = pendingPlaying;
            if (pending != null) {
                if (playing == pending || nowMs > pendingPlayingUntilMs) pendingPlaying = null;
                else playing = pending; // Statuszeile von vor dem Klick
            }
            if (titleChanged || nowMs > pendingSkipUntilMs) pendingSkipUntilMs = 0;
            boolean skipPending = pendingSkipUntilMs != 0;
            boolean playStateChanged = (isPlaying != playing);

            isPlaying = playing;
            duration = dur;
            title = t;
            artist = a;
            album = alb;
            hasMedia = active;

            long now = System.currentTimeMillis();
            if (skipPending) {
                // Position bleibt nach Weiter/Zurück optimistisch bei 0, bis der neue Titel gemeldet wird
            } else if (titleChanged || playStateChanged || !playing) {
                position = pos;
                lastUpdateMs = now;
            } else {
                float currentEstimate = getInterpolatedPosition();
                float drift = Math.abs(pos - currentEstimate);
                if (drift > 2.0f) {
                    position = pos;
                    lastUpdateMs = now;
                } else if (drift > 0.4f) {
                    position = Math.max(position, pos);
                    lastUpdateMs = now;
                }
            }

            if (!active) {
                hasCoverTexture = false;
            } else if (titleChanged || coverChanged || (!hasCoverTexture && coverPath != null && Files.exists(coverPath))) {
                coverDirty = true;
            }
        }
    }

    public static void checkTextureUpdate() {
        if (!coverDirty) return;
        coverDirty = false;

        if (coverPath == null || !Files.exists(coverPath)) return;

        try {
            byte[] bytes = Files.readAllBytes(coverPath);
            if (bytes.length > 50) {
                try (InputStream in = new ByteArrayInputStream(bytes)) {
                    NativeImage image = NativeImage.read(in);
                    if (image != null) {
                        coverWidth = image.getWidth();
                        coverHeight = image.getHeight();
                        coverColor = averageColor(image);
                        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "spotify_cover", image);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(COVER_ID, tex);
                        hasCoverTexture = true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Durchschnittsfarbe des Covers (Stichproben auf einem Raster, ARGB deckend). */
    private static int averageColor(NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int step = Math.max(1, Math.min(w, h) / 24);
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = image.getColorArgb(x, y);
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0xFF1DB954;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    private static void writeScriptFile(Path path) throws IOException {
        String script = "$ErrorActionPreference = 'SilentlyContinue'\n" +
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime\n" +
            "$asStreamForRead = ([System.IO.WindowsRuntimeStreamExtensions].GetMethods() | ? { $_.Name -eq 'AsStreamForRead' })[0]\n" +
            "$asTaskOp = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\n" +
            "\n" +
            "Function Await($WinRtTask, $ResultType) {\n" +
            "    $asTask = $asTaskOp.MakeGenericMethod($ResultType)\n" +
            "    $netTask = $asTask.Invoke($null, @($WinRtTask))\n" +
            "    $netTask.Wait(500) | Out-Null\n" +
            "    $netTask.Result\n" +
            "}\n" +
            "\n" +
            "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null\n" +
            "$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n" +
            "\n" +
            "$lastTrackKey = \"\"\n" +
            "$coverPath = $args[0]\n" +
            "\n" +
            "# Steuerbefehle (PLAYPAUSE, NEXT, PREV) kommen zeilenweise ueber stdin; asynchron lesen, damit die Schleife weiterlaeuft\n" +
            "$stdin = New-Object System.IO.StreamReader([Console]::OpenStandardInput())\n" +
            "$script:cmdTask = $stdin.ReadLineAsync()\n" +
            "\n" +
            "Function Invoke-Commands {\n" +
            "    $did = $false\n" +
            "    while ($script:cmdTask -and $script:cmdTask.IsCompleted) {\n" +
            "        $cmd = $null\n" +
            "        if (-not $script:cmdTask.IsFaulted) { $cmd = $script:cmdTask.Result }\n" +
            "        if ($cmd -eq $null) { exit }\n" +
            "        $script:cmdTask = $stdin.ReadLineAsync()\n" +
            "        $cmd = $cmd.Trim()\n" +
            "        $ok = $false\n" +
            "        $s = $manager.GetCurrentSession()\n" +
            "        if ($s) {\n" +
            "            switch ($cmd) {\n" +
            "                'PLAYPAUSE' { $ok = Await ($s.TryTogglePlayPauseAsync()) ([bool]) }\n" +
            "                'NEXT' { $ok = Await ($s.TrySkipNextAsync()) ([bool]) }\n" +
            "                'PREV' { $ok = Await ($s.TrySkipPreviousAsync()) ([bool]) }\n" +
            "            }\n" +
            "        }\n" +
            "        [Console]::Out.WriteLine(\"CTRL|$cmd|$ok\")\n" +
            "        [Console]::Out.Flush()\n" +
            "        $did = $true\n" +
            "    }\n" +
            "    return $did\n" +
            "}\n" +
            "\n" +
            "while ($true) {\n" +
            "    try {\n" +
            "        $session = $manager.GetCurrentSession()\n" +
            "        if ($session) {\n" +
            "            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n" +
            "            $timeline = $session.GetTimelineProperties()\n" +
            "            $info = $session.GetPlaybackInfo()\n" +
            "            $status = $info.PlaybackStatus.ToString()\n" +
            "            $isPlaying = if ($status -eq \"Playing\") { \"true\" } else { \"false\" }\n" +
            "\n" +
            "            $now = [DateTimeOffset]::Now\n" +
            "            $pos = 0.0\n" +
            "            $dur = 0.0\n" +
            "            if ($timeline) {\n" +
            "                if ($timeline.EndTime) { $dur = [Math]::Round($timeline.EndTime.TotalSeconds, 1) }\n" +
            "                if ($timeline.Position) {\n" +
            "                    $p = $timeline.Position.TotalSeconds\n" +
            "                    if ($isPlaying -eq \"true\" -and $timeline.LastUpdatedTime -and $timeline.LastUpdatedTime.Year -gt 2000) {\n" +
            "                        $elapsed = ($now - $timeline.LastUpdatedTime).TotalSeconds\n" +
            "                        if ($elapsed -ge 0 -and $elapsed -lt 3600) {\n" +
            "                            $p = $p + $elapsed\n" +
            "                        }\n" +
            "                    }\n" +
            "                    if ($dur -gt 0 -and $p -gt $dur) { $p = $dur }\n" +
            "                    $pos = [Math]::Round($p, 1)\n" +
            "                }\n" +
            "            }\n" +
            "\n" +
            "            $t = if ($props.Title) { $props.Title } else { \"\" }\n" +
            "            $art = if ($props.Artist) { $props.Artist } else { \"\" }\n" +
            "            $alb = if ($props.AlbumTitle) { $props.AlbumTitle } else { \"\" }\n" +
            "\n" +
            "            $trackKey = \"$t|$art|$alb\"\n" +
            "            $coverChanged = \"0\"\n" +
            "            if ($trackKey -ne $lastTrackKey) {\n" +
            "                $lastTrackKey = $trackKey\n" +
            "                if ($props.Thumbnail) {\n" +
            "                    try {\n" +
            "                        $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n" +
            "                        $converted = $asStreamForRead.Invoke($null, @($stream))\n" +
            "                        $tempPath = \"$coverPath.tmp\"\n" +
            "                        $fileStream = [System.IO.File]::Create($tempPath)\n" +
            "                        $converted.CopyTo($fileStream)\n" +
            "                        $fileStream.Close()\n" +
            "                        $converted.Close()\n" +
            "                        [System.IO.File]::Copy($tempPath, $coverPath, $true)\n" +
            "                        [System.IO.File]::Delete($tempPath)\n" +
            "                        $coverChanged = \"1\"\n" +
            "                    } catch {}\n" +
            "                }\n" +
            "            }\n" +
            "\n" +
            "            $cleanTitle = $t.Replace(\"|\", \"-\")\n" +
            "            $cleanArtist = $art.Replace(\"|\", \"-\")\n" +
            "            $cleanAlbum = $alb.Replace(\"|\", \"-\")\n" +
            "            [Console]::Out.WriteLine(\"MEDIA|$isPlaying|$pos|$dur|$cleanTitle|$cleanArtist|$cleanAlbum|$coverChanged\")\n" +
            "            [Console]::Out.Flush()\n" +
            "        } else {\n" +
            "            [Console]::Out.WriteLine(\"MEDIA|false|0|0||||0\")\n" +
            "            [Console]::Out.Flush()\n" +
            "        }\n" +
            "    } catch {\n" +
            "        [Console]::Out.WriteLine(\"MEDIA|false|0|0||||0\")\n" +
            "        [Console]::Out.Flush()\n" +
            "    }\n" +
            "    for ($i = 0; $i -lt 10; $i++) {\n" +
            "        if (Invoke-Commands) { Start-Sleep -Milliseconds 150; break }\n" +
            "        Start-Sleep -Milliseconds 50\n" +
            "    }\n" +
            "}\n";

        Files.writeString(path, script, StandardCharsets.UTF_8);
    }
}

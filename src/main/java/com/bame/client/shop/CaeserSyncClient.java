package com.bame.client.shop;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cosmetic-Sync über den WebSocket {@code /v1/sync} des Caeser-Backends (ersetzt den öffentlichen MQTT-Broker).
 * <ul>
 *   <li>Erste Nachricht ist immer {@code hello} mit Token und Instanz-ID (das Token steht nie in der URL).</li>
 *   <li>Reconnect mit Backoff 1 s, 2 s, 5 s, 10 s, danach 30 s; {@code ping} alle 25 s.</li>
 *   <li>Eingehende Nachrichten werden auf dem Listener-Thread nur geparst und per {@code MinecraftClient.execute}
 *       (nicht blockierend) an den {@link CaeserPlayerSyncManager} übergeben.</li>
 * </ul>
 */
public final class CaeserSyncClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserSync");

    private static final long[] BACKOFF_SECONDS = {1, 2, 5, 10, 30};
    private static final int MAX_MESSAGE_CHARS = 64 * 1024;
    private static final long PING_SECONDS = 25;
    private static final int MAX_AUTH_FAILURES = 3;

    private static final Object LOCK = new Object();
    private static WebSocket socket;               // LOCK
    private static boolean connecting;             // LOCK
    private static int generation;                 // LOCK
    private static int backoffIndex;               // LOCK
    private static int authFailures;               // LOCK
    private static boolean wanted;                 // LOCK
    private static ScheduledFuture<?> reconnectTask; // LOCK
    private static ScheduledFuture<?> pingTask;      // LOCK
    private static CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null); // LOCK

    private static volatile boolean connected;     // nach "welcome"
    private static volatile String welcomeUuid;

    private CaeserSyncClient() {}

    /** {@code true}, sobald der Server die Verbindung mit {@code welcome} bestätigt hat. */
    public static boolean isConnected() {
        return connected;
    }

    /** UUID (32 Hex), mit der der Server uns kennt, oder {@code null}. */
    public static String welcomeUuid() {
        return connected ? welcomeUuid : null;
    }

    /** Verbindung (neu) aufbauen. Eine bestehende Verbindung wird geschlossen; es folgt ein neues {@code hello}. */
    public static void reconnect() {
        synchronized (LOCK) {
            wanted = true;
            closeLocked();
        }
        connectNow();
    }

    /** Verbindet, falls noch keine Verbindung besteht bzw. aufgebaut wird (z. B. beim Server-Beitritt). */
    public static void ensureConnected() {
        if (!CaeserApi.isConfigured() || !CaeserAuth.canLogin()) return;
        synchronized (LOCK) {
            wanted = true;
            if (socket != null || connecting || (reconnectTask != null && !reconnectTask.isDone())) return;
        }
        connectNow();
    }

    /** Verbindung schließen und nicht automatisch neu verbinden (bis {@link #reconnect()}). */
    public static void disconnect() {
        synchronized (LOCK) {
            wanted = false;
            closeLocked();
        }
    }

    /** Nach einem Account-Wechsel: Verbindung sofort trennen; nach der neuen Anmeldung folgt {@link #reconnect()}. */
    public static void onAccountChanged() {
        synchronized (LOCK) {
            authFailures = 0;
            backoffIndex = 0;
            wanted = true;
            closeLocked();
        }
    }

    /** Schickt eine Nachricht, sofern verbunden (nach {@code welcome}). */
    public static boolean send(JsonObject message) {
        if (!connected || message == null) return false;
        synchronized (LOCK) {
            if (socket == null) return false;
            enqueueLocked(socket, message.toString());
        }
        return true;
    }

    // ==========================================
    // Verbindungsaufbau
    // ==========================================

    private static void connectNow() {
        if (!CaeserApi.isConfigured() || !CaeserAuth.canLogin()) return;
        String token = CaeserAuth.currentToken();
        if (token == null) {
            // Nach erfolgreicher Anmeldung ruft CaeserAuth selbst reconnect() auf.
            CaeserAuth.login(false).whenComplete((t, err) -> {
                if (err != null) scheduleReconnect(-1);
            });
            return;
        }
        URI uri = CaeserApi.syncUri();
        final int gen;
        synchronized (LOCK) {
            if (!wanted || socket != null || connecting) return;
            if (authFailures >= MAX_AUTH_FAILURES) return;
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
                reconnectTask = null;
            }
            connecting = true;
            gen = ++generation;
        }
        try {
            CaeserApi.http().newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .header("User-Agent", CaeserApi.userAgent())
                    .buildAsync(uri, new Listener(gen))
                    .whenComplete((ws, err) -> onBuilt(gen, ws, err, token));
        } catch (Throwable t) {
            synchronized (LOCK) {
                if (gen == generation) connecting = false;
            }
            scheduleReconnect(gen);
        }
    }

    private static void onBuilt(int gen, WebSocket ws, Throwable err, String token) {
        synchronized (LOCK) {
            if (gen != generation) {
                if (ws != null) ws.abort();
                return;
            }
            connecting = false;
            if (err != null || ws == null) {
                LOGGER.debug("Sync connect failed: {}", err != null ? err.getClass().getSimpleName() : "null");
            } else {
                socket = ws;
                sendChain = CompletableFuture.completedFuture(null);
                JsonObject hello = new JsonObject();
                hello.addProperty("type", "hello");
                hello.addProperty("token", token);
                hello.addProperty("instanceId", CaeserPlayerSyncManager.LOCAL_INSTANCE_ID);
                enqueueLocked(ws, hello.toString()); // immer die erste Nachricht
                startPingLocked(gen);
                return;
            }
        }
        scheduleReconnect(gen);
    }

    private static void scheduleReconnect(int gen) {
        synchronized (LOCK) {
            if (gen >= 0 && gen != generation) return;
            if (!wanted || socket != null || connecting) return;
            if (reconnectTask != null && !reconnectTask.isDone()) return;
            long delay = BACKOFF_SECONDS[Math.min(backoffIndex, BACKOFF_SECONDS.length - 1)];
            backoffIndex++;
            reconnectTask = CaeserApi.SCHEDULER.schedule(CaeserSyncClient::connectNow, delay, TimeUnit.SECONDS);
        }
    }

    private static void startPingLocked(int gen) {
        if (pingTask != null) pingTask.cancel(false);
        pingTask = CaeserApi.SCHEDULER.scheduleAtFixedRate(() -> {
            synchronized (LOCK) {
                if (gen != generation || socket == null) return;
                enqueueLocked(socket, "{\"type\":\"ping\"}");
            }
        }, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
    }

    private static void closeLocked() {
        generation++;
        connecting = false;
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
        WebSocket ws = socket;
        socket = null;
        boolean wasConnected = connected;
        connected = false;
        welcomeUuid = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "").whenComplete((w, e) -> ws.abort());
            } catch (Throwable t) {
                ws.abort();
            }
        }
        if (wasConnected) {
            MinecraftClient.getInstance().execute(CaeserPlayerSyncManager::onSyncDisconnected);
        }
    }

    /** Nachrichten nacheinander senden: java.net.http.WebSocket erlaubt keine überlappenden sendText-Aufrufe. */
    private static void enqueueLocked(WebSocket ws, String text) {
        sendChain = sendChain
                .handle((v, e) -> null)
                .thenCompose(v -> ws.sendText(text, true))
                .exceptionally(e -> null);
    }

    private static void onClosed(int gen, int code, Throwable error) {
        // 4001: kein (gültiges) hello, 4003: Spieler gelöscht, 4008: zu viele Verbindungen pro UUID
        boolean authProblem = code == 4001 || code == 4003 || code == 4401 || code == 1008;
        boolean slowDown = code == 4003 || code == 4008;
        boolean wasConnected;
        synchronized (LOCK) {
            if (gen != generation) return;
            generation++;
            if (slowDown) backoffIndex = BACKOFF_SECONDS.length - 1; // nicht hämmern: gleich maximaler Abstand
            if (pingTask != null) {
                pingTask.cancel(false);
                pingTask = null;
            }
            socket = null;
            connecting = false;
            wasConnected = connected;
            connected = false;
            welcomeUuid = null;
            if (authProblem) authFailures++;
        }
        LOGGER.debug("Sync connection closed (code {}, {})", code, error != null ? error.getClass().getSimpleName() : "-");
        if (authProblem) CaeserAuth.invalidate();
        if (wasConnected) {
            MinecraftClient.getInstance().execute(CaeserPlayerSyncManager::onSyncDisconnected);
        }
        scheduleReconnect(-1);
    }

    // ==========================================
    // Empfangen
    // ==========================================

    private static void handleMessage(int gen, String text) {
        JsonObject msg;
        try {
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) return;
            msg = el.getAsJsonObject();
        } catch (Throwable t) {
            return;
        }
        String type = str(msg, "type");
        if (type == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (type) {
            case "welcome" -> {
                String uuid = CaeserApi.uuid32(str(msg, "uuid"));
                synchronized (LOCK) {
                    if (gen != generation) return;
                    backoffIndex = 0;
                    authFailures = 0;
                    welcomeUuid = uuid;
                    connected = true;
                }
                LOGGER.info("Caeser sync connected");
                mc.execute(CaeserPlayerSyncManager::onSyncWelcome);
            }
            case "state" -> {
                String uuid = CaeserApi.uuid32(str(msg, "uuid"));
                String name = str(msg, "name");
                JsonObject state = msg.has("state") && msg.get("state").isJsonObject() ? msg.getAsJsonObject("state") : null;
                if (uuid == null || state == null) return;
                mc.execute(() -> {
                    if (isCurrent(gen)) CaeserPlayerSyncManager.applyServerState(uuid, name, state);
                });
            }
            case "emote" -> {
                String uuid = CaeserApi.uuid32(str(msg, "uuid"));
                String name = str(msg, "name");
                String emote = str(msg, "emote");
                String action = str(msg, "action");
                long time = msg.has("time") && msg.get("time").isJsonPrimitive() ? safeLong(msg.get("time")) : 0L;
                long receivedAt = System.currentTimeMillis();
                if (uuid == null) return;
                mc.execute(() -> {
                    if (isCurrent(gen)) CaeserPlayerSyncManager.applyServerEmote(uuid, name, emote, action, time, receivedAt);
                });
            }
            case "leave" -> {
                String uuid = CaeserApi.uuid32(str(msg, "uuid"));
                if (uuid == null) return;
                mc.execute(() -> CaeserPlayerSyncManager.applyServerLeave(uuid));
            }
            case "killfx" -> {
                // Kill-Effekt eines beobachteten Spielers (nur Welt-Teil); unbekannte Felder werden ignoriert
                String uuid = CaeserApi.uuid32(str(msg, "uuid"));
                String name = str(msg, "name");
                String effect = str(msg, "effect");
                String victim = str(msg, "victimName");
                double x = num(msg, "x"), y = num(msg, "y"), z = num(msg, "z");
                if (effect == null || Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return;
                mc.execute(() -> {
                    if (isCurrent(gen)) com.bame.client.killfx.KillEffects.onRemote(uuid, name, effect, x, y, z, victim);
                });
            }
            case "error" -> {
                String code = str(msg, "code");
                LOGGER.debug("Sync server error: {}", code);
                if (code != null) {
                    String c = code.toUpperCase(java.util.Locale.ROOT);
                    if (c.contains("AUTH") || c.contains("TOKEN") || c.contains("UNAUTHORIZED")) {
                        CaeserAuth.invalidate();
                    }
                }
            }
            default -> { /* pong und Unbekanntes */ }
        }
    }

    /** Zahl aus der Nachricht, sonst NaN. */
    private static double num(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && o.get(key).isJsonPrimitive() && o.getAsJsonPrimitive(key).isNumber()) {
                return o.get(key).getAsDouble();
            }
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    private static boolean isCurrent(int gen) {
        synchronized (LOCK) {
            return gen == generation && connected;
        }
    }

    private static long safeLong(JsonElement el) {
        try {
            return el.getAsLong();
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) return null;
        return o.get(key).getAsString();
    }

    /** Listener je Verbindung. Setzt Teil-Frames ({@code last == false}) korrekt zusammen und begrenzt die Größe. */
    private static final class Listener implements WebSocket.Listener {
        private final int gen;
        private final StringBuilder buffer = new StringBuilder();
        private boolean overflow;

        Listener(int gen) {
            this.gen = gen;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                if (!overflow) {
                    if (buffer.length() + data.length() > MAX_MESSAGE_CHARS) {
                        overflow = true;
                        buffer.setLength(0);
                    } else {
                        buffer.append(data);
                    }
                }
                if (last) {
                    if (!overflow && buffer.length() > 0) {
                        handleMessage(gen, buffer.toString());
                    }
                    buffer.setLength(0);
                    overflow = false;
                }
            } catch (Throwable t) {
                LOGGER.debug("Sync message handling failed: {}", t.getClass().getSimpleName());
            } finally {
                webSocket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1); // Binärdaten werden nicht verwendet
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            onClosed(gen, statusCode, null);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            onClosed(gen, -1, error);
        }
    }
}

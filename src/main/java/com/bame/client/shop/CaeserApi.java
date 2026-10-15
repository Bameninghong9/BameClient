package com.bame.client.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP-Zugang zum Caeser-Backend. Alle Aufrufe laufen asynchron ({@link HttpClient#sendAsync}); Ergebnisse werden
 * als {@link CompletableFuture} geliefert und von den Aufrufern per {@code MinecraftClient.execute(...)} auf den
 * Haupt-Thread gebracht.
 * <p>
 * Tokens werden nie geloggt: Es werden höchstens Pfad, Statuscode und Fehlercode protokolliert.
 */
public final class CaeserApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserApi");

    public static final String MSG_UNREACHABLE = "Caeser-Server nicht erreichbar.";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BODY_CHARS = 512 * 1024;

    private static final AtomicInteger THREAD_IDS = new AtomicInteger();

    /** Für blockierende Arbeit (Mojang-joinServer, Cache-Datei). Daemon-Threads, damit das Spiel normal beendet. */
    static final ExecutorService WORKER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Caeser-Shop-" + THREAD_IDS.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    /** Für Zeitsteuerung (Reconnect, Ping). */
    static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Caeser-Sync-Timer");
        t.setDaemon(true);
        return t;
    });

    /** Eigener Pool für HttpClient/WebSocket-Rückrufe, damit blockierende Arbeit im WORKER sie nie aufhält. */
    private static final ExecutorService HTTP_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Caeser-Http-" + THREAD_IDS.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .executor(HTTP_EXECUTOR)
            .build();

    private static final String BASE_URL = resolveBaseUrl();
    private static final String USER_AGENT = "CaeserClient/" + modVersion();

    private CaeserApi() {}

    // ==========================================
    // Konfiguration
    // ==========================================

    /** {@code true}, wenn eine gültige, nicht-Platzhalter-Basis-URL eingetragen ist. */
    public static boolean isConfigured() {
        return BASE_URL != null;
    }

    public static String baseUrl() {
        return BASE_URL;
    }

    public static String userAgent() {
        return USER_AGENT;
    }

    static HttpClient http() {
        return HTTP;
    }

    /** {@code <baseUrl>/v1/sync} mit {@code https → wss} bzw. {@code http → ws}. */
    public static URI syncUri() {
        if (BASE_URL == null) return null;
        String ws = BASE_URL.startsWith("https://")
                ? "wss://" + BASE_URL.substring("https://".length())
                : "ws://" + BASE_URL.substring("http://".length());
        return URI.create(ws + "/v1/sync");
    }

    private static String resolveBaseUrl() {
        String raw = null;
        try {
            String prop = System.getProperty("caeser.api.url");
            if (prop != null && !prop.isBlank()) raw = prop.trim();
        } catch (Throwable ignored) {}
        if (raw == null) {
            try (InputStream in = CaeserApi.class.getResourceAsStream("/caeser-api.properties")) {
                if (in != null) {
                    Properties p = new Properties();
                    p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                    String v = p.getProperty("baseUrl");
                    if (v != null && !v.isBlank()) raw = v.trim();
                }
            } catch (Throwable t) {
                LOGGER.warn("Could not read caeser-api.properties ({})", t.getClass().getSimpleName());
            }
        }
        String url = validateBaseUrl(raw);
        if (url == null) {
            LOGGER.info("Caeser backend not configured - shop, unlock sync and cosmetic sync are disabled.");
        } else {
            LOGGER.info("Caeser backend: {}", url);
        }
        return url;
    }

    /** Nur https, außer http://127.0.0.1:port bzw. http://localhost:port. Platzhalter gelten als nicht eingerichtet. */
    static String validateBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        try {
            URI uri = URI.create(s);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            if (host.isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
                LOGGER.warn("Ignoring invalid Caeser backend URL");
                return null;
            }
            if (host.equals("example.com") || host.contains(".example.") || host.startsWith("example.")
                    || host.contains("<") || host.contains("your-") || host.contains("dein-")) {
                return null; // Platzhalter
            }
            if ("https".equals(scheme)) {
                return s;
            }
            if ("http".equals(scheme) && ("127.0.0.1".equals(host) || "localhost".equals(host)) && uri.getPort() > 0) {
                return s;
            }
            LOGGER.warn("Ignoring Caeser backend URL: only https (or http://127.0.0.1:<port> / http://localhost:<port>) is allowed");
        } catch (Throwable t) {
            LOGGER.warn("Ignoring invalid Caeser backend URL");
        }
        return null;
    }

    private static String modVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("bameclient")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("dev");
        } catch (Throwable t) {
            return "dev";
        }
    }

    // ==========================================
    // Anfragen
    // ==========================================

    /** Fehler einer API-Anfrage mit deutscher Meldung. {@code status == 0} bedeutet Netzwerkfehler. */
    public static final class ApiException extends RuntimeException {
        public final int status;
        public final String code;
        public final JsonObject body;

        public ApiException(int status, String code, String message, JsonObject body) {
            super(message, null, false, false);
            this.status = status;
            this.code = code;
            this.body = body;
        }

        public boolean isNetworkError() {
            return status == 0;
        }

        /** Deutsche Meldung für den Spieler. */
        public String userMessage() {
            return getMessage();
        }
    }

    /** Entpackt {@link CompletionException}s und wandelt alles andere in einen Netzwerkfehler um. */
    public static ApiException unwrap(Throwable t) {
        Throwable cur = t;
        while ((cur instanceof CompletionException || cur instanceof java.util.concurrent.ExecutionException) && cur.getCause() != null) {
            cur = cur.getCause();
        }
        if (cur instanceof ApiException api) return api;
        return new ApiException(0, "NETWORK", MSG_UNREACHABLE, null);
    }

    public static CompletableFuture<JsonObject> get(String path, String bearer) {
        return send("GET", path, null, bearer);
    }

    public static CompletableFuture<JsonObject> post(String path, JsonObject body, String bearer) {
        return send("POST", path, body != null ? body : new JsonObject(), bearer);
    }

    /**
     * Lädt eine Binärdatei (z. B. ein Cape-PNG) mit höchstens {@code maxBytes} Bytes. Die ganze Anfrage wird nach
     * {@link #TIMEOUT} abgebrochen. Ohne Bearer-Token, wenn {@code bearer == null}. Wirft nie synchron.
     * Liefert bei 2xx die Bytes, sonst eine {@link ApiException} (Status 0 bei Netzwerkfehler, Code {@code TOO_LARGE}).
     */
    public static CompletableFuture<byte[]> getBytes(String path, String bearer, int maxBytes) {
        if (BASE_URL == null) {
            return CompletableFuture.failedFuture(new ApiException(0, "NOT_CONFIGURED", MSG_UNREACHABLE, null));
        }
        final HttpRequest request;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/png")
                    .GET();
            if (bearer != null && !bearer.isEmpty()) {
                b.header("Authorization", "Bearer " + bearer);
            }
            request = b.build();
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(new ApiException(0, "NETWORK", MSG_UNREACHABLE, null));
        }
        final CompletableFuture<HttpResponse<byte[]>> sent;
        try {
            sent = HTTP.sendAsync(request, info -> {
                long len = info.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (info.statusCode() < 200 || info.statusCode() >= 300 || len > maxBytes) {
                    return HttpResponse.BodySubscribers.replacing(null);
                }
                return new LimitedBodySubscriber(maxBytes);
            });
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(new ApiException(0, "NETWORK", MSG_UNREACHABLE, null));
        }
        // Gesamtzeit begrenzen (das Anfrage-Timeout gilt nur bis zu den Headern)
        SCHEDULER.schedule(() -> sent.cancel(true), TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        return sent.handle((resp, err) -> {
            if (err != null || resp == null) {
                LOGGER.debug("Caeser API GET {} failed: {}", path, err != null ? err.getClass().getSimpleName() : "null");
                throw new ApiException(0, "NETWORK", MSG_UNREACHABLE, null);
            }
            int status = resp.statusCode();
            if (status < 200 || status >= 300) {
                LOGGER.debug("Caeser API GET {} -> {}", path, status);
                throw new ApiException(status, "HTTP_" + status, fallbackMessage(status), null);
            }
            byte[] body = resp.body();
            if (body == null) {
                throw new ApiException(status, "TOO_LARGE", "Datei zu groß.", null);
            }
            return body;
        });
    }

    /** Sammelt höchstens {@code max} Bytes; bei Überschreitung wird abgebrochen und {@code null} geliefert. */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private java.util.concurrent.Flow.Subscription subscription;

        LimitedBodySubscriber(int max) {
            this.max = max;
        }

        @Override
        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
            subscription = s;
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(java.util.List<java.nio.ByteBuffer> items) {
            if (result.isDone()) return;
            for (java.nio.ByteBuffer buf : items) {
                int n = buf.remaining();
                if (out.size() + n > max) {
                    result.complete(null);
                    subscription.cancel();
                    return;
                }
                byte[] chunk = new byte[n];
                buf.get(chunk);
                out.write(chunk, 0, n);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(out.toByteArray());
        }
    }

    /**
     * Schickt eine Anfrage. Liefert bei 2xx das JSON-Objekt der Antwort, sonst eine {@link ApiException}.
     * Wirft nie synchron.
     */
    public static CompletableFuture<JsonObject> send(String method, String path, JsonObject body, String bearer) {
        if (BASE_URL == null) {
            return CompletableFuture.failedFuture(new ApiException(0, "NOT_CONFIGURED", MSG_UNREACHABLE, null));
        }
        final HttpRequest request;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json");
            if (bearer != null && !bearer.isEmpty()) {
                b.header("Authorization", "Bearer " + bearer);
            }
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            request = b.build();
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(new ApiException(0, "NETWORK", MSG_UNREACHABLE, null));
        }

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((resp, err) -> {
                    if (err != null || resp == null) {
                        LOGGER.debug("Caeser API {} {} failed: {}", method, path, err != null ? err.getClass().getSimpleName() : "null");
                        throw new ApiException(0, "NETWORK", MSG_UNREACHABLE, null);
                    }
                    int status = resp.statusCode();
                    JsonObject json = parseObject(resp.body());
                    if (status >= 200 && status < 300) {
                        if (json == null) {
                            throw new ApiException(status, "BAD_RESPONSE", "Ungültige Antwort vom Caeser-Server.", null);
                        }
                        return json;
                    }
                    String code = json != null && json.has("code") && json.get("code").isJsonPrimitive()
                            ? json.get("code").getAsString() : ("HTTP_" + status);
                    String msg = json != null && json.has("error") && json.get("error").isJsonPrimitive()
                            ? json.get("error").getAsString() : null;
                    if (msg == null || msg.isBlank()) msg = fallbackMessage(status);
                    LOGGER.debug("Caeser API {} {} -> {} {}", method, path, status, code);
                    throw new ApiException(status, code, msg, json);
                });
    }

    private static JsonObject parseObject(String body) {
        if (body == null || body.isEmpty() || body.length() > MAX_BODY_CHARS) return null;
        try {
            JsonElement el = JsonParser.parseString(body);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String fallbackMessage(int status) {
        return switch (status) {
            case 400 -> "Ungültige Anfrage.";
            case 401 -> "Anmeldung beim Caeser-Server abgelaufen.";
            case 402 -> "Nicht genug Jetons.";
            case 403 -> "Keine Berechtigung.";
            case 404 -> "Nicht gefunden.";
            case 409 -> "Das besitzt du bereits.";
            case 410 -> "Anmeldung abgelaufen, bitte erneut versuchen.";
            case 429 -> "Zu viele Anfragen. Bitte warte kurz.";
            default -> status >= 500 ? "Der Caeser-Server hat gerade ein Problem." : MSG_UNREACHABLE;
        };
    }

    // ==========================================
    // Hilfen für IDs
    // ==========================================

    /** UUID als 32 Hex-Zeichen ohne Bindestriche in Kleinbuchstaben, {@code null} bei ungültigem Wert. */
    public static String uuid32(UUID uuid) {
        // Läuft pro Frame und Spieler (Tab-Liste, Cosmetics-Renderer): direkt aus den Bits, eine Allokation statt drei
        return com.bame.client.util.FastIds.uuid32(uuid);
    }

    /** Normalisiert eine UUID-Zeichenkette (mit oder ohne Bindestriche) auf 32 Hex-Zeichen, sonst {@code null}. */
    public static String uuid32(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("-", "").toLowerCase(Locale.ROOT);
        if (s.length() != 32) return null;
        for (int i = 0; i < 32; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return null;
        }
        return s;
    }

    public static UUID parseUuid32(String hex32) {
        String s = uuid32(hex32);
        if (s == null) return null;
        try {
            return new UUID(Long.parseUnsignedLong(s.substring(0, 16), 16), Long.parseUnsignedLong(s.substring(16), 16));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Item-ID im Backend-Format (GROSS). Legacy-Werte {@code self}/{@code custom} gehören zu MiniMe Sit. */
    public static String itemId(String modId) {
        if (modId == null) return null;
        String s = modId.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || "none".equals(s)) return null;
        if ("self".equals(s) || "custom".equals(s)) s = "minime_sit";
        return s.toUpperCase(Locale.ROOT);
    }
}

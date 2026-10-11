package com.bame.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lokale "Account-Brücke" zum Caeser Client Launcher.
 * <p>
 * Der Launcher setzt beim Spielstart {@code CAESER_BRIDGE_URL} und {@code CAESER_BRIDGE_TOKEN}.
 * Alle Aufrufe laufen asynchron; Ergebnisse werden per {@link MinecraftClient#execute(Runnable)} auf den
 * Haupt-Thread zurückgegeben. Das Bridge-Token und Access-Tokens werden nie geloggt oder gespeichert.
 */
public final class LauncherBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserAccountBridge");

    public static final String MSG_UNREACHABLE = "Der Caeser Client Launcher ist nicht erreichbar.";
    public static final String MSG_NOT_AVAILABLE = "Account-Wechsel geht nur, wenn Minecraft über den Caeser Client Launcher gestartet wurde.";
    public static final String MSG_LOGIN_RUNNING = "Es läuft bereits eine Anmeldung.";
    private static final String MSG_INVALID_RESPONSE = "Ungültige Antwort vom Caeser Client Launcher.";

    private static final Pattern URL_PATTERN = Pattern.compile("^http://127\\.0\\.0\\.1:([0-9]{1,5})$");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String BASE_URL;
    private static final String TOKEN;

    static {
        String url = null;
        String token = null;
        try {
            String rawUrl = System.getenv("CAESER_BRIDGE_URL");
            String rawToken = System.getenv("CAESER_BRIDGE_TOKEN");
            if (rawUrl != null && rawToken != null) {
                Matcher m = URL_PATTERN.matcher(rawUrl);
                if (m.matches() && TOKEN_PATTERN.matcher(rawToken).matches()) {
                    int port = Integer.parseInt(m.group(1));
                    if (port >= 1 && port <= 65535) {
                        url = rawUrl;
                        token = rawToken;
                    }
                }
            }
        } catch (Throwable ignored) {}
        BASE_URL = url;
        TOKEN = token;
        if (BASE_URL == null) {
            LOGGER.info("Caeser account bridge not available (game was not started by the Caeser Client Launcher).");
        }
    }

    private static volatile HttpClient httpClient;
    /** null = noch unbekannt, sonst Ergebnis der letzten Anfrage an die Bridge. */
    private static volatile Boolean lastReachable = null;

    private LauncherBridge() {}

    public record AccountInfo(String id, String name, boolean active) {}

    public record LoginState(boolean running, String error, String lastAddedId) {}

    public record AccountList(List<AccountInfo> accounts, LoginState login) {}

    /**
     * Antwort von {@code POST /v1/accounts/switch}. Enthält das Access-Token und bleibt deshalb nur im Speicher;
     * {@link #toString()} gibt bewusst kein Token aus.
     */
    static final class SwitchResult {
        final String id;
        final String name;
        final String accessToken;
        final String xuid;
        final String clientId;

        SwitchResult(String id, String name, String accessToken, String xuid, String clientId) {
            this.id = id;
            this.name = name;
            this.accessToken = accessToken;
            this.xuid = xuid;
            this.clientId = clientId;
        }

        @Override
        public String toString() {
            return "SwitchResult[id=" + id + ", name=" + name + "]";
        }
    }

    /** Fehler mit einer Meldung, die dem Spieler direkt angezeigt werden darf. */
    private static final class BridgeException extends RuntimeException {
        BridgeException(String message) {
            super(message, null, false, false);
        }
    }

    public static boolean isAvailable() {
        return BASE_URL != null && TOKEN != null;
    }

    /** true = Bridge hat zuletzt geantwortet, false = nicht erreichbar/nicht verfügbar, null = noch unbekannt. */
    public static Boolean getLastReachable() {
        if (!isAvailable()) return Boolean.FALSE;
        return lastReachable;
    }

    /** Prüft im Hintergrund, ob die Bridge antwortet (für den Status-Punkt am Knopf). */
    public static void refreshReachability() {
        if (!isAvailable()) return;
        fetchAccounts(list -> {}, err -> {});
    }

    public static void fetchAccounts(Consumer<AccountList> onSuccess, Consumer<String> onError) {
        if (!isAvailable()) {
            deliver(() -> onError.accept(MSG_NOT_AVAILABLE));
            return;
        }
        send(request("/v1/accounts").GET().build(), (status, body) -> {
            if (status != 200) throw new BridgeException(errorMessage(status, body));
            return parseAccounts(body);
        }, onSuccess, onError);
    }

    /**
     * Holt beim Launcher die Anmeldedaten für {@code id} und übergibt sie sofort an den {@link SessionSwitcher}.
     * Das Ergebnis (inkl. Access-Token) wird nirgends gespeichert.
     *
     * @param onSuccess erhält den Namen des neuen Accounts
     */
    public static void switchTo(String id, Consumer<String> onSuccess, Consumer<String> onError) {
        if (!isAvailable()) {
            deliver(() -> onError.accept(MSG_NOT_AVAILABLE));
            return;
        }
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            deliver(() -> onError.accept(MSG_INVALID_RESPONSE));
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("id", id);
        HttpRequest req = request("/v1/accounts/switch")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        send(req, (status, body) -> {
            if (status != 200) throw new BridgeException(errorMessage(status, body));
            return parseSwitch(body);
        }, result -> SessionSwitcher.switchSession(result, onSuccess, onError), onError);
    }

    /** Startet im Launcher die Microsoft-Anmeldung für einen neuen Account. */
    public static void startLogin(Runnable onStarted, Consumer<String> onError) {
        if (!isAvailable()) {
            deliver(() -> onError.accept(MSG_NOT_AVAILABLE));
            return;
        }
        HttpRequest req = request("/v1/accounts/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build();
        send(req, (status, body) -> {
            if (status == 409) throw new BridgeException(MSG_LOGIN_RUNNING);
            if (status != 202 && status != 200) throw new BridgeException(errorMessage(status, body));
            return Boolean.TRUE;
        }, ok -> onStarted.run(), onError);
    }

    // ------------------------------------------------------------------
    // Intern
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface ResponseParser<T> {
        T parse(int status, String body);
    }

    private static HttpClient client() {
        HttpClient c = httpClient;
        if (c == null) {
            synchronized (LauncherBridge.class) {
                c = httpClient;
                if (c == null) {
                    c = HttpClient.newBuilder()
                            .connectTimeout(TIMEOUT)
                            .proxy(HttpClient.Builder.NO_PROXY)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .version(HttpClient.Version.HTTP_1_1)
                            .build();
                    httpClient = c;
                }
            }
        }
        return c;
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + TOKEN)
                .header("Accept", "application/json");
    }

    private static <T> void send(HttpRequest req, ResponseParser<T> parser, Consumer<T> onSuccess, Consumer<String> onError) {
        try {
            client().sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .handle((resp, err) -> {
                        if (err != null) {
                            lastReachable = Boolean.FALSE;
                            Throwable cause = unwrap(err);
                            LOGGER.debug("Account bridge request failed: {}", cause.getClass().getSimpleName());
                            deliver(() -> onError.accept(MSG_UNREACHABLE));
                            return null;
                        }
                        lastReachable = Boolean.TRUE;
                        T result;
                        try {
                            result = parser.parse(resp.statusCode(), resp.body());
                        } catch (BridgeException e) {
                            String msg = e.getMessage();
                            deliver(() -> onError.accept(msg));
                            return null;
                        } catch (Throwable t) {
                            deliver(() -> onError.accept(MSG_INVALID_RESPONSE));
                            return null;
                        }
                        deliver(() -> onSuccess.accept(result));
                        return null;
                    });
        } catch (Throwable t) {
            lastReachable = Boolean.FALSE;
            deliver(() -> onError.accept(MSG_UNREACHABLE));
        }
    }

    private static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static void deliver(Runnable r) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                LOGGER.warn("Account bridge callback failed: {}", t.getClass().getSimpleName());
            }
        });
    }

    /** Meldung aus {@code { "error": "..." }}, sonst eine allgemeine deutsche Meldung. */
    private static String errorMessage(int status, String body) {
        try {
            if (body != null && !body.isBlank()) {
                JsonElement el = JsonParser.parseString(body);
                if (el.isJsonObject()) {
                    String msg = getString(el.getAsJsonObject(), "error");
                    if (msg != null && !msg.isBlank()) return sanitize(msg);
                }
            }
        } catch (Throwable ignored) {}
        if (status == 401 || status == 403) return "Der Launcher hat die Anfrage abgelehnt.";
        if (status == 404) return "Dieser Account ist im Launcher nicht mehr gespeichert.";
        if (status == 429) return "Bitte warte kurz und versuche es erneut.";
        if (status == 502) return "Der Account konnte nicht angemeldet werden.";
        return "Der Launcher hat mit einem Fehler geantwortet (HTTP " + status + ").";
    }

    private static String sanitize(String msg) {
        String clean = msg.replaceAll("[\\p{Cntrl}§]", " ").trim();
        return clean.length() > 200 ? clean.substring(0, 200) + "…" : clean;
    }

    private static AccountList parseAccounts(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<AccountInfo> accounts = new ArrayList<>();
        if (root.has("accounts") && root.get("accounts").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("accounts");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String id = normalizeId(getString(o, "id"));
                String name = getString(o, "name");
                if (id == null || name == null || name.isBlank()) continue;
                boolean active = o.has("active") && o.get("active").isJsonPrimitive() && o.get("active").getAsBoolean();
                accounts.add(new AccountInfo(id, sanitize(name), active));
            }
        }
        LoginState login = new LoginState(false, null, null);
        if (root.has("login") && root.get("login").isJsonObject()) {
            JsonObject l = root.getAsJsonObject("login");
            boolean running = l.has("running") && l.get("running").isJsonPrimitive() && l.get("running").getAsBoolean();
            String error = getString(l, "error");
            String lastAdded = normalizeId(getString(l, "lastAddedId"));
            login = new LoginState(running, error != null && !error.isBlank() ? sanitize(error) : null, lastAdded);
        }
        return new AccountList(Collections.unmodifiableList(accounts), login);
    }

    private static SwitchResult parseSwitch(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String id = normalizeId(getString(root, "id"));
        String name = getString(root, "name");
        String token = getString(root, "accessToken");
        if (id == null || name == null || name.isBlank() || token == null || token.isBlank()) {
            throw new BridgeException(MSG_INVALID_RESPONSE);
        }
        return new SwitchResult(id, name.trim(), token, blankToNull(getString(root, "xuid")), blankToNull(getString(root, "clientId")));
    }

    private static String normalizeId(String id) {
        if (id == null) return null;
        String clean = id.replace("-", "").trim().toLowerCase(Locale.ROOT);
        return ID_PATTERN.matcher(clean).matches() ? clean : null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String getString(JsonObject o, String key) {
        if (o == null || !o.has(key)) return null;
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return null;
        return el.getAsString();
    }

    /** Wandelt eine UUID ohne Bindestriche (32 Hex-Zeichen) in eine {@link java.util.UUID}. */
    static java.util.UUID parseUuid(String id) {
        String n = normalizeId(id);
        if (n == null) throw new IllegalArgumentException("invalid id");
        return new java.util.UUID(Long.parseUnsignedLong(n.substring(0, 16), 16), Long.parseUnsignedLong(n.substring(16), 16));
    }
}

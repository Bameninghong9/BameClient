package com.bame.client.emotes;

/**
 * Regeln für das Echo des eigenen Emotes vom Sync-Server (ohne Minecraft-Klassen, im campfireTest geprüft).
 * <p>
 * Der Sync-Server schickt eine {@code emote}-Nachricht nicht nur an die Beobachter, sondern auch an alle Verbindungen
 * des Spielers selbst. So kennt auch der eigene Client die maßgebliche Serverzeit seines Emotes, mit der alle anderen
 * Clients ihren Zufall (z. B. Lagerfeuer: essen oder verbrennen) berechnen. Vorher konnte der eigene Client die Zeit nur
 * schätzen (um ungefähr die Laufzeit daneben).
 * <p>
 * Ein Echo gehört nur dann zum laufenden lokalen Emote, wenn es dasselbe Emote ist (prüft der Aufrufer), kurz nach dem
 * lokalen Start empfangen wurde und – falls der Uhrenversatz zum Server schon geschätzt ist – seine Serverzeit zur
 * geschätzten Serverzeit des lokalen Starts passt. Verspätete Echos eines früheren Starts werden so ignoriert.
 */
public final class EmoteEchoLogic {

    private EmoteEchoLogic() {}

    /** Später als so lange nach dem lokalen Start empfangene Echos gehören nicht (mehr) zu diesem Start (ms). */
    public static final long MAX_AGE_MS = 5_000L;
    /**
     * Erlaubte Abweichung der Serverzeit von der Schätzung: Die Schätzung liegt eher etwas zu früh (Versatz = kleinstes
     * beobachtetes Empfangen − Serverzeit), das PLAY kommt nach der Laufzeit und ggf. nach dem Sende-Limit (bis ca.
     * 1,2 s) an. Ein früherer Start desselben Emotes liegt dagegen vor der Schätzung.
     */
    public static final long EARLY_SLACK_MS = 300L;
    public static final long LATE_SLACK_MS = 3_000L;

    /**
     * Gehört ein Echo (gleiches Emote) zum lokalen Start?
     *
     * @param localStartMs          lokaler Start des laufenden Emotes (ms, lokale Uhr)
     * @param receivedAtMs          Empfangszeit des Echos (lokale Uhr)
     * @param serverTime            Zeitstempel des Echos (Serveruhr)
     * @param clockKnown            ist der Uhrenversatz zum Server schon geschätzt?
     * @param estimatedServerStart  lokaler Start in Serverzeit umgerechnet (nur mit {@code clockKnown})
     */
    public static boolean accepts(long localStartMs, long receivedAtMs, long serverTime, boolean clockKnown,
                                  long estimatedServerStart) {
        if (localStartMs <= 0L || serverTime <= 0L) return false;
        long age = receivedAtMs - localStartMs;
        // Vor dem Start empfangen: Echo eines früheren PLAY
        if (age < 0L || age > MAX_AGE_MS) return false;
        if (!clockKnown) return true;
        long d = serverTime - estimatedServerStart;
        return d >= -EARLY_SLACK_MS && d <= LATE_SLACK_MS;
    }
}

package com.bame.client.cosmetics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Lightweight, zero-dependency MQTT 3.1.1 client for global peer-to-peer sync.
 * Connects over standard TCP to public broker (broker.hivemq.com:1883) on a background daemon thread.
 */
public class CaeserMqttClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserMqtt");

    private final String host;
    private final int port;
    private final String clientId;
    private final String topic;
    private final BiConsumer<String, String> messageHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final BlockingQueue<OutgoingMessage> outgoingQueue = new LinkedBlockingQueue<>(200);

    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private Thread workerThread;

    private static class OutgoingMessage {
        final String topic;
        final String payload;

        OutgoingMessage(String topic, String payload) {
            this.topic = topic;
            this.payload = payload;
        }
    }

    public CaeserMqttClient(String host, int port, String clientId, String topic, BiConsumer<String, String> messageHandler) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.topic = topic;
        this.messageHandler = messageHandler;
    }

    public synchronized void start() {
        if (running.getAndSet(true)) return;

        workerThread = new Thread(this::runLoop, "Caeser-Mqtt-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public synchronized void stop() {
        running.set(false);
        connected.set(false);
        closeSocket();
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void publish(String subTopic, String payload) {
        if (!running.get() || payload == null) return;
        outgoingQueue.offer(new OutgoingMessage(subTopic, payload));
    }

    private void runLoop() {
        while (running.get()) {
            try {
                connectAndHandle();
            } catch (Throwable t) {
                if (running.get()) {
                    LOGGER.debug("MQTT connection closed/failed: {}", t.getMessage());
                }
            }

            connected.set(false);
            closeSocket();

            if (running.get()) {
                try {
                    Thread.sleep(6000L);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void connectAndHandle() throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 8000);
        socket.setSoTimeout(35000); // 35s read timeout (keepalive ping is every 25s)
        out = socket.getOutputStream();
        in = socket.getInputStream();

        // 1. CONNECT packet
        ByteArrayOutputStream connPayload = new ByteArrayOutputStream();
        writeMqttString(connPayload, "MQTT");
        connPayload.write(0x04); // Protocol level (3.1.1)
        connPayload.write(0x02); // Clean session
        connPayload.write(0x00); // Keepalive MSB (60s)
        connPayload.write(0x3C); // Keepalive LSB

        String effectiveId = (clientId != null && !clientId.isBlank()) ? clientId : ("Caeser_" + System.currentTimeMillis());
        writeMqttString(connPayload, effectiveId);

        byte[] connBytes = connPayload.toByteArray();
        out.write(0x10);
        writeVarLength(out, connBytes.length);
        out.write(connBytes);
        out.flush();

        // Read CONNACK
        int respHeader = in.read();
        if (respHeader == -1) throw new EOFException("EOF on CONNACK");
        int respLen = readVarLength(in);
        byte[] connAckData = in.readNBytes(respLen);
        if (connAckData.length < 2 || connAckData[1] != 0) {
            throw new IOException("Broker rejected connection, code=" + (connAckData.length >= 2 ? connAckData[1] : -1));
        }

        // 2. SUBSCRIBE packet
        ByteArrayOutputStream subPayload = new ByteArrayOutputStream();
        subPayload.write(0x00); subPayload.write(0x01); // Packet ID = 1
        writeMqttString(subPayload, topic);
        subPayload.write(0x00); // QoS 0

        byte[] subBytes = subPayload.toByteArray();
        out.write(0x82);
        writeVarLength(out, subBytes.length);
        out.write(subBytes);
        out.flush();

        // Read SUBACK
        int subAckHeader = in.read();
        if (subAckHeader == -1) throw new EOFException("EOF on SUBACK");
        int subAckLen = readVarLength(in);
        in.readNBytes(subAckLen);

        connected.set(true);
        LOGGER.info("Connected & subscribed to Caeser Client peer sync on {}", host);

        // Start writer thread for outgoing messages
        Thread writerThread = new Thread(this::writerLoop, "Caeser-Mqtt-Writer");
        writerThread.setDaemon(true);
        writerThread.start();

        long lastPing = System.currentTimeMillis();

        // Main reader loop
        while (running.get() && socket.isConnected() && !socket.isClosed()) {
            int packetHeader;
            try {
                packetHeader = in.read();
            } catch (java.net.SocketTimeoutException e) {
                // Send keepalive PINGREQ
                sendPing();
                lastPing = System.currentTimeMillis();
                continue;
            }

            if (packetHeader == -1) throw new EOFException("Broker closed stream");
            int packetLen = readVarLength(in);
            byte[] packetData = in.readNBytes(packetLen);

            int packetType = (packetHeader >> 4) & 0x0F;
            if (packetType == 3) { // PUBLISH
                handleIncomingPublish(packetData);
            } else if (packetType == 13) { // PINGRESP
                // Heartbeat OK
            }

            if (System.currentTimeMillis() - lastPing > 25000L) {
                sendPing();
                lastPing = System.currentTimeMillis();
            }
        }
    }

    private void handleIncomingPublish(byte[] data) {
        if (data.length < 2) return;
        try {
            int topicLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            if (data.length < 2 + topicLen) return;
            String msgTopic = new String(data, 2, topicLen, StandardCharsets.UTF_8);
            String payload = new String(data, 2 + topicLen, data.length - (2 + topicLen), StandardCharsets.UTF_8);

            if (messageHandler != null) {
                messageHandler.accept(msgTopic, payload);
            }
        } catch (Throwable t) {
            LOGGER.debug("Error handling MQTT publish: {}", t.getMessage());
        }
    }

    private void writerLoop() {
        while (running.get() && connected.get()) {
            try {
                OutgoingMessage msg = outgoingQueue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (msg != null && out != null) {
                    ByteArrayOutputStream pubPayload = new ByteArrayOutputStream();
                    writeMqttString(pubPayload, msg.topic);
                    pubPayload.write(msg.payload.getBytes(StandardCharsets.UTF_8));

                    byte[] pubBytes = pubPayload.toByteArray();
                    synchronized (out) {
                        out.write(0x30); // PUBLISH QoS 0
                        writeVarLength(out, pubBytes.length);
                        out.write(pubBytes);
                        out.flush();
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                LOGGER.debug("Error sending MQTT message: {}", t.getMessage());
                break;
            }
        }
    }

    private void sendPing() {
        try {
            if (out != null) {
                synchronized (out) {
                    out.write(0xC0); // PINGREQ
                    out.write(0x00);
                    out.flush();
                }
            }
        } catch (Throwable ignored) {}
    }

    private void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Throwable ignored) {}
        socket = null;
        out = null;
        in = null;
    }

    private static void writeVarLength(OutputStream out, int length) throws IOException {
        do {
            int digit = length % 128;
            length /= 128;
            if (length > 0) digit |= 0x80;
            out.write(digit);
        } while (length > 0);
    }

    private static int readVarLength(InputStream in) throws IOException {
        int multiplier = 1;
        int value = 0;
        int digit;
        do {
            digit = in.read();
            if (digit == -1) throw new EOFException("EOF in var length");
            value += (digit & 127) * multiplier;
            multiplier *= 128;
        } while ((digit & 128) != 0);
        return value;
    }

    private static void writeMqttString(OutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes.length & 0xFF);
        out.write(bytes);
    }
}

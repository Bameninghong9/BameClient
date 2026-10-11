package com.bame.client.cosmetics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-speed local peer-to-peer sync via UDP Multicast (239.255.77.88:24567).
 * Allows multiple Minecraft instances running on the same PC or local network
 * to exchange cosmetics and emotes with sub-millisecond latency and zero broker dependency.
 */
public class CaeserLocalPeerSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserLocalPeerSync");

    private static final String MULTICAST_GROUP = "239.255.77.88";
    private static final int PORT = 24567;

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static MulticastSocket socket;
    private static InetAddress groupAddress;
    private static Consumer<String> onMessageReceived;

    public static synchronized void start(Consumer<String> consumer) {
        if (running.getAndSet(true)) return;
        onMessageReceived = consumer;

        Thread listenerThread = new Thread(CaeserLocalPeerSync::listenLoop, "Caeser-LocalSync-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public static synchronized void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Throwable ignored) {}
            socket = null;
        }
    }

    public static void broadcast(String payload) {
        if (payload == null || payload.isBlank()) return;

        // Run send in background thread so caller (e.g. render tick / GUI) is never blocked
        new Thread(() -> {
            try {
                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                if (groupAddress == null) {
                    groupAddress = InetAddress.getByName(MULTICAST_GROUP);
                }

                DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, PORT);
                if (socket != null && !socket.isClosed()) {
                    socket.send(packet);
                } else {
                    try (MulticastSocket temp = new MulticastSocket()) {
                        temp.send(packet);
                    }
                }

                // Also send directly to localhost 127.0.0.1 for guaranteed same-PC delivery
                try (MulticastSocket loopback = new MulticastSocket()) {
                    DatagramPacket loopPacket = new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), PORT);
                    loopback.send(loopPacket);
                } catch (Throwable ignored) {}

            } catch (Throwable t) {
                LOGGER.debug("Local peer broadcast failed: {}", t.getMessage());
            }
        }, "Caeser-LocalSync-Sender").start();
    }

    private static void listenLoop() {
        while (running.get()) {
            try {
                groupAddress = InetAddress.getByName(MULTICAST_GROUP);
                socket = new MulticastSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(PORT));
                socket.joinGroup(groupAddress);

                byte[] buf = new byte[8192];
                while (running.get() && !socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    if (onMessageReceived != null) {
                        try {
                            onMessageReceived.accept(message);
                        } catch (Throwable t) {
                            LOGGER.debug("Error handling local sync packet: {}", t.getMessage());
                        }
                    }
                }
            } catch (Throwable t) {
                if (running.get()) {
                    LOGGER.debug("Local sync listener restarted: {}", t.getMessage());
                }
            } finally {
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (Throwable ignored) {}
                }
            }

            if (running.get()) {
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}

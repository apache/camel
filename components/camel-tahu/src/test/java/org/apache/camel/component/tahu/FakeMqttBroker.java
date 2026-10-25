/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.tahu;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal in-process MQTT 3.1.1 broker for tests: it accepts every connection and subscription, acknowledges every
 * publish and records the topics published to it. It does not route messages to subscribers.
 */
final class FakeMqttBroker implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final List<String> publishedTopics = new CopyOnWriteArrayList<>();
    private final AtomicInteger connects = new AtomicInteger();
    private volatile boolean running = true;

    FakeMqttBroker() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::accept, "fake-mqtt-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    List<String> getPublishedTopics() {
        return publishedTopics;
    }

    int getConnects() {
        return connects.get();
    }

    private void accept() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                sockets.add(socket);
                Thread handler = new Thread(() -> handle(socket), "fake-mqtt-connection");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            while (running) {
                int header = in.read();
                if (header < 0) {
                    return;
                }
                byte[] body = new byte[readRemainingLength(in)];
                in.readFully(body);
                int type = header >> 4;
                switch (type) {
                    case 1 -> {
                        connects.incrementAndGet();
                        write(out, 0x20, 0, 0);
                    }
                    case 3 -> {
                        int qos = (header >> 1) & 3;
                        int topicLength = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
                        publishedTopics.add(new String(body, 2, topicLength, StandardCharsets.UTF_8));
                        if (qos == 1) {
                            write(out, 0x40, body[2 + topicLength], body[3 + topicLength]);
                        } else if (qos == 2) {
                            write(out, 0x50, body[2 + topicLength], body[3 + topicLength]);
                        }
                    }
                    case 6 -> write(out, 0x70, body[0], body[1]);
                    case 8 -> {
                        // SUBACK: the packet id and the requested QoS of every topic filter
                        ByteArrayOutputStream granted = new ByteArrayOutputStream();
                        int i = 2;
                        while (i < body.length) {
                            int length = ((body[i] & 0xff) << 8) | (body[i + 1] & 0xff);
                            i += 2 + length;
                            granted.write(body[i++]);
                        }
                        byte[] qos = granted.toByteArray();
                        byte[] suback = new byte[2 + qos.length];
                        suback[0] = body[0];
                        suback[1] = body[1];
                        System.arraycopy(qos, 0, suback, 2, qos.length);
                        writePacket(out, 0x90, suback);
                    }
                    case 10 -> write(out, 0xB0, body[0], body[1]);
                    case 12 -> writePacket(out, 0xD0, new byte[0]);
                    case 14 -> {
                        return;
                    }
                    default -> {
                        // nothing to answer
                    }
                }
            }
        } catch (IOException e) {
            // the client went away
        } finally {
            sockets.remove(socket);
        }
    }

    private static int readRemainingLength(DataInputStream in) throws IOException {
        int value = 0;
        int multiplier = 1;
        int digit;
        do {
            digit = in.readUnsignedByte();
            value += (digit & 127) * multiplier;
            multiplier *= 128;
        } while ((digit & 128) != 0);
        return value;
    }

    private static void write(OutputStream out, int header, int b1, int b2) throws IOException {
        writePacket(out, header, new byte[] { (byte) b1, (byte) b2 });
    }

    private static synchronized void writePacket(OutputStream out, int header, byte[] body) throws IOException {
        out.write(header);
        out.write(body.length);
        out.write(body);
        out.flush();
    }

    @Override
    public void close() throws IOException {
        running = false;
        serverSocket.close();
        for (Socket socket : sockets) {
            socket.close();
        }
    }
}

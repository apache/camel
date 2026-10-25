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
package org.apache.camel.component.milo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A TCP relay to the server, whose connections a test can drop or silence while it keeps listening, and which can be
 * down while the client tries to connect.
 */
final class TcpRelay {

    private volatile ServerSocket listener;

    private final List<Socket> sockets = new ArrayList<>();

    // the sockets whose data is dropped instead of relayed
    private final Set<Socket> silenced = ConcurrentHashMap.newKeySet();

    void start(final int port, final int serverPort) throws IOException {
        final ServerSocket listener = new ServerSocket(port);
        this.listener = listener;
        final Thread acceptor = new Thread(() -> {
            while (!listener.isClosed()) {
                Socket client = null;
                try {
                    client = listener.accept();
                    final Socket server = new Socket("localhost", serverPort);
                    synchronized (sockets) {
                        sockets.add(client);
                        sockets.add(server);
                    }
                    pump(client, server);
                    pump(server, client);
                } catch (final IOException e) {
                    // the listener was closed, or the server refused: nothing to relay
                    if (client != null) {
                        close(client);
                    }
                }
            }
        }, "relay-" + port);
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void pump(final Socket from, final Socket to) {
        final Thread thread = new Thread(() -> {
            final byte[] buffer = new byte[8192];
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (!silenced.contains(from)) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                }
            } catch (final IOException e) {
                // the connection was dropped
            } finally {
                close(from);
                close(to);
            }
        }, "relay-pump");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Drops what the current connections carry in both directions, without closing them: the link goes silent, as when
     * the network drops it without a reset, until the connections are dropped. New connections are relayed.
     */
    void silence() {
        synchronized (sockets) {
            silenced.addAll(sockets);
        }
    }

    void dropConnections() {
        synchronized (sockets) {
            sockets.forEach(TcpRelay::close);
            sockets.clear();
            silenced.clear();
        }
    }

    void stop() {
        final ServerSocket listener = this.listener;
        this.listener = null;
        if (listener != null) {
            try {
                listener.close();
            } catch (final IOException e) {
                // nothing left to close
            }
        }
        dropConnections();
    }

    private static void close(final Socket socket) {
        try {
            socket.close();
        } catch (final IOException e) {
            // already closed
        }
    }
}

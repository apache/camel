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
package org.apache.camel.component.chatscript;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A ChatScript server that records every message (user, bot and text, the three null terminated strings of the
 * ChatScript protocol) and answers one line per connection: the greeting for an empty text, an empty line for
 * {@code :reset}, and {@code You said <text>} otherwise.
 */
final class FakeChatScriptServer implements AutoCloseable {

    static final String HOST = "127.0.0.1";

    private final ServerSocket serverSocket;
    private final BlockingQueue<List<String>> messages = new LinkedBlockingQueue<>();

    FakeChatScriptServer() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getByName(HOST));
        Thread thread = new Thread(this::serve, "fake-chatscript-server");
        thread.setDaemon(true);
        thread.start();
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * The next message the server received, as user, bot and text, or null when none arrives within 10 seconds.
     */
    List<String> nextMessage() throws InterruptedException {
        return messages.poll(10, TimeUnit.SECONDS);
    }

    boolean hasNoMoreMessages() {
        return messages.isEmpty();
    }

    private void serve() {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                String line = in.readLine();
                if (line == null) {
                    continue;
                }
                String[] fields = line.split("\0", -1);
                List<String> message = List.of(fields[0], fields[1], fields[2]);
                messages.add(message);
                out.println(reply(message));
            } catch (IOException e) {
                // the server socket was closed
            }
        }
    }

    private static String reply(List<String> message) {
        String text = message.get(2);
        if (text.isEmpty()) {
            return "Welcome " + message.get(0);
        }
        if (":reset".equals(text)) {
            return "";
        }
        return "You said " + text;
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }
}

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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.AvailablePortFinder.Port;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Isolated
class TuiWebServerTest {

    private TuiWebServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void startBindsToLoopbackAndAcceptsTcpConnections() throws Exception {
        try (Port reserved = AvailablePortFinder.find()) {
            server = newServer(reserved.getPort());

            server.start();

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", reserved.getPort()), 2000);
                assertThat(socket.isConnected()).isTrue();
            }
        }
    }

    @Test
    void stopClosesTheListeningPort() throws Exception {
        try (Port reserved = AvailablePortFinder.find()) {
            server = newServer(reserved.getPort());
            server.start();

            server.stop();

            assertThatThrownBy(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", reserved.getPort()), 2000);
                }
            }).isInstanceOf(IOException.class);
        }
    }

    @Test
    void acceptsAWebSocketHandshakeOnTheWsEndpoint() throws Exception {
        try (Port reserved = AvailablePortFinder.find()) {
            server = newServer(reserved.getPort());
            server.start();

            HttpClient client = HttpClient.newHttpClient();
            CompletableFuture<WebSocket> handshake = client.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + reserved.getPort() + "/ws"), new WebSocket.Listener() {
                    });

            WebSocket webSocket = handshake.get(5, TimeUnit.SECONDS);
            assertThat(webSocket).isNotNull();
            webSocket.abort();
        }
    }

    private static TuiWebServer newServer(int port) {
        return new TuiWebServer(
                port, new CamelJBangMain(), Thread.currentThread().getContextClassLoader(), null, 200,
                "dark");
    }
}

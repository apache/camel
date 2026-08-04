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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    @Test
    void servesTheCustomIndexPageWithVendoredAssets() throws Exception {
        try (Port reserved = AvailablePortFinder.find()) {
            server = newServer(reserved.getPort());
            server.start();

            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + reserved.getPort();

            HttpResponse<String> index = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(index.statusCode()).isEqualTo(200);
            assertThat(index.headers().firstValue("content-type")).hasValueSatisfying(v -> assertThat(v).contains("text/html"));
            assertThat(index.body()).contains("Apache Camel").contains("/vendor/xterm.js");

            HttpResponse<String> xtermJs = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/vendor/xterm.js")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(xtermJs.statusCode()).isEqualTo(200);
            assertThat(xtermJs.headers().firstValue("content-type"))
                    .hasValueSatisfying(v -> assertThat(v).contains("javascript"));

            HttpResponse<String> xtermCss = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/vendor/xterm.css")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(xtermCss.statusCode()).isEqualTo(200);
            assertThat(xtermCss.headers().firstValue("content-type"))
                    .hasValueSatisfying(v -> assertThat(v).contains("text/css"));

            HttpResponse<String> fitAddon = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/vendor/xterm-addon-fit.js")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(fitAddon.statusCode()).isEqualTo(200);

            HttpResponse<byte[]> logo = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/images/camel-logo.png")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(logo.statusCode()).isEqualTo(200);
            assertThat(logo.body()).hasSizeGreaterThan(0);
        }
    }

    private static TuiWebServer newServer(int port) {
        return new TuiWebServer(
                port, new CamelJBangMain(), Thread.currentThread().getContextClassLoader(), null, 200,
                "dark");
    }
}

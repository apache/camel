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
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.AvailablePortFinder.Port;
import org.apache.camel.util.json.JsonObject;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A browser session of {@code camel tui --web} runs on a JLine terminal fed from the WebSocket (CAMEL-25581).
 */
class WebTerminalTest {

    // characters of 2, 3 and 4 bytes in UTF-8
    private static final String WIDE = "h\u00e9\u20ac\uD83D\uDE00";

    private TuiWebServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    /** The page: talks the protocol of index.html, and keeps what the server sends. */
    private static final class Page implements WebSocket.Listener {
        final StringBuffer received = new StringBuffer();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            received.append(data);
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closed.completeExceptionally(error);
        }
    }

    private TuiWebServer start(int port, Consumer<Terminal> session) throws IOException {
        server = new TuiWebServer(port, new CamelJBangMain(), getClass().getClassLoader(), null, 200, "dark", session);
        server.start();
        return server;
    }

    private static WebSocket connect(int port, Page page) throws Exception {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), page)
                .get(5, TimeUnit.SECONDS);
    }

    private static String message(String action, Object... keysAndValues) {
        JsonObject json = new JsonObject();
        json.put("action", action);
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            json.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return json.toJson();
    }

    @Test
    void aSessionRunsOnAJLineTerminalFedFromTheSocket() throws Exception {
        List<Size> sizes = new CopyOnWriteArrayList<>();
        CountDownLatch inputEnded = new CountDownLatch(1);
        Consumer<Terminal> session = terminal -> {
            try {
                sizes.add(terminal.getSize());
                terminal.handle(Terminal.Signal.WINCH, signal -> sizes.add(terminal.getSize()));
                terminal.enterRawMode();
                terminal.writer().print("ready");
                terminal.writer().flush();
                StringBuilder line = new StringBuilder();
                int c;
                while ((c = terminal.reader().read()) != -1 && c != '\r') {
                    line.append((char) c);
                }
                terminal.writer().print("you typed: " + line);
                terminal.writer().flush();
                // the session runs until the page goes away: the end of the input
                while (terminal.reader().read() != -1) {
                    // nothing more to read
                }
                inputEnded.countDown();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        try (Port reserved = AvailablePortFinder.find()) {
            start(reserved.getPort(), session);
            Page page = new Page();
            WebSocket socket = connect(reserved.getPort(), page);

            socket.sendText(message("init", "cols", 100, "rows", 30), true).get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> page.received.toString().contains("ready"));
            assertThat(sizes).first().isEqualTo(new Size(100, 30));

            socket.sendText(message("read", "data", WIDE + "\r"), true).get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(page.received.toString()).contains("you typed: " + WIDE));

            socket.sendText(message("resize", "cols", 120, "rows", 40), true).get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> sizes.contains(new Size(120, 40)));

            // closing the page ends the input of the terminal, and so the session
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
            assertThat(inputEnded.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void theSessionEndingClosesTheSocket() throws Exception {
        try (Port reserved = AvailablePortFinder.find()) {
            start(reserved.getPort(), terminal -> {
            });
            Page page = new Page();
            WebSocket socket = connect(reserved.getPort(), page);

            socket.sendText(message("init", "cols", 80, "rows", 24), true).get(5, TimeUnit.SECONDS);

            assertThat(page.closed.get(5, TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void theSizeIsBounded() {
        JsonObject huge = new JsonObject();
        huge.put("cols", 100000);
        huge.put("rows", -5);

        assertThat(WebTerminal.size(huge)).isEqualTo(new Size(WebTerminal.MAX_COLUMNS, 1));
        assertThat(WebTerminal.size(new JsonObject())).isEqualTo(new Size(80, 24));
    }

    @Test
    void theOutputNeverSplitsACharacter() {
        byte[] bytes = WIDE.getBytes(StandardCharsets.UTF_8);
        for (int cut = 0; cut <= bytes.length; cut++) {
            StringBuilder frames = new StringBuilder();
            WebTerminal.FrameOutput out = new WebTerminal.FrameOutput(frames::append);
            out.write(bytes, 0, cut);
            out.flush();
            out.write(bytes, cut, bytes.length - cut);
            out.flush();
            assertThat(frames.toString()).as("cut after byte %d", cut).isEqualTo(WIDE);
        }
    }

    @Test
    void thePageIsServedFromTheClassPathAndNothingElse() throws Exception {
        assertThat(WebPageHandler.load("/index.html")).isNotNull();
        assertThat(WebPageHandler.load("/vendor/xterm.js")).isNotNull();
        assertThat(WebPageHandler.load("/../../META-INF/MANIFEST.MF")).isNull();
        assertThat(WebPageHandler.load("/vendor/../index.html")).isNull();
        assertThat(WebPageHandler.load("index.html")).isNull();
        try (Port reserved = AvailablePortFinder.find()) {
            start(reserved.getPort(), terminal -> {
            });
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + reserved.getPort();

            HttpResponse<String> missing = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/vendor/missing.js")).build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> post = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/index.html"))
                            .POST(HttpRequest.BodyPublishers.ofString("x")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(post.statusCode()).isEqualTo(405);
        }
    }
}

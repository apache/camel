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
package org.apache.camel.component.as2.api;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.util.IOHelper;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client keeps a connection in its pool only when the partner's response allows it: a connection the partner closes
 * must not be leased again, or the next send writes on a closed socket (CAMEL-26110).
 */
class AS2ClientConnectionReuseTest {

    private static final int SENDS = 3;

    private ExecutorService executor;
    private ServerSocket serverSocket;
    private final AtomicInteger connections = new AtomicInteger();

    @AfterEach
    void stopPartner() {
        IOHelper.close(serverSocket);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void connectionClosedByThePartnerIsNotReused() throws Exception {
        startPartner("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 2\r\n\r\nok", true);

        assertEachSendSucceeds();
        // the sends follow each other within the pool's 2s stale check: each needs a new connection
        assertEquals(SENDS, connections.get());
    }

    @Test
    void http10ConnectionWithoutKeepAliveIsNotReused() throws Exception {
        startPartner("HTTP/1.0 200 OK\r\nContent-Length: 2\r\n\r\nok", true);

        assertEachSendSucceeds();
        assertEquals(SENDS, connections.get());
    }

    @Test
    void keptAliveConnectionIsReused() throws Exception {
        startPartner("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok", false);

        assertEachSendSucceeds();
        assertEquals(1, connections.get());
    }

    private void assertEachSendSucceeds() throws Exception {
        AS2ClientConnection clientConnection = new AS2ClientConnection(
                "1.1", "Camel AS2 Client Endpoint", "example.org", "localhost", serverSocket.getLocalPort(),
                Duration.ofSeconds(5), Duration.ofSeconds(5), 5, Duration.ofMinutes(15), null, null);
        for (int i = 0; i < SENDS; i++) {
            BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
            request.setEntity(new StringEntity("EDI message", ContentType.TEXT_PLAIN));
            ClassicHttpResponse response = (ClassicHttpResponse) clientConnection.send(request, as2Context());
            assertEquals(200, response.getCode(), "send " + i);
            // read the response like AS2ClientManager does, so a reused connection starts clean
            assertEquals("ok", EntityUtils.toString(response.getEntity()), "send " + i);
        }
    }

    private static HttpCoreContext as2Context() {
        HttpCoreContext context = HttpCoreContext.create();
        context.setAttribute(AS2ClientManager.FROM, "mrAS@example.org");
        context.setAttribute(AS2ClientManager.AS2_FROM, "878051556");
        context.setAttribute(AS2ClientManager.AS2_TO, "878051556");
        context.setAttribute(AS2ClientManager.SUBJECT, "Test Case");
        return context;
    }

    /**
     * A partner that gives every request the same response, and closes the connection after it when told to.
     */
    private void startPartner(String response, boolean closeAfterResponse) throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        executor = Executors.newCachedThreadPool();
        executor.execute(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.execute(() -> answer(socket, response, closeAfterResponse));
                } catch (IOException e) {
                    // closed
                }
            }
        });
    }

    private void answer(Socket socket, String response, boolean closeAfterResponse) {
        try (socket) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            boolean firstRequest = true;
            do {
                long contentLength = -1;
                String line = readLine(in);
                if (line == null) {
                    // the client's startup check connects without sending a request
                    return;
                }
                if (firstRequest) {
                    connections.incrementAndGet();
                    firstRequest = false;
                }
                for (line = readLine(in); line != null && !line.isEmpty(); line = readLine(in)) {
                    if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                        contentLength = Long.parseLong(line.substring(line.indexOf(':') + 1).trim());
                    }
                }
                in.skipNBytes(Math.max(contentLength, 0));
                out.write(response.getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
            } while (!closeAfterResponse);
        } catch (IOException e) {
            // connection closed
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') {
                line.write(b);
            }
        }
        return b == -1 && line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
    }
}

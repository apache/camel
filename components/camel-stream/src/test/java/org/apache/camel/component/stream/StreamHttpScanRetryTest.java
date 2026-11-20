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
package org.apache.camel.component.stream;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream:http consumer with scanStream and retry keeps reading when the HTTP server cannot be reached or the
 * connection is lost for a while, instead of ending its reading thread while the route stays started.
 */
class StreamHttpScanRetryTest extends CamelTestSupport {

    // the least time between two requests when the one before failed, a bit less than the 1 s the consumer waits
    private static final long FAILURE_DELAY_MILLIS = 900;

    private final AtomicInteger requests = new AtomicInteger();
    private final List<Long> requestTimes = new CopyOnWriteArrayList<>();
    private final AtomicInteger handledFailures = new AtomicInteger();
    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();
    private ServerSocket server;

    @AfterEach
    void stopServer() throws Exception {
        // stop the consumer first, so that it does not find the server gone while it is still running
        context.stop();
        if (server != null) {
            server.close();
        }
        serverThread.shutdownNow();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testServerNotAvailableWhenReopened() throws Exception {
        // the first request is answered, the second fails (the server is down), the next ones are answered again
        serve(List.of("a\n", "", "b\n"));
        MockEndpoint mock = expectFirstBodies("a", "b");

        startRoute("");

        mock.assertIsSatisfied();
        assertFirstBodies(mock, "a", "b");
    }

    @Test
    void testServerNotAvailableWhenStarted() throws Exception {
        serve(List.of("", "a\n"));
        MockEndpoint mock = expectFirstBodies("a");

        startRoute("");

        mock.assertIsSatisfied();
        assertFirstBodies(mock, "a");
        // the stream that could not be opened at the start is not asked again right away
        assertWaitedAfterFailures(2);
    }

    @Test
    void testConnectionLostWhileReading() throws Exception {
        // the connection of the first answer is reset before the length it announced was sent: reading it fails
        serve(List.of("a\n|cut", "b\n"));
        MockEndpoint answeredAgain = getMockEndpoint("mock:b");
        answeredAgain.expectedMinimumMessageCount(1);

        startRoute("");

        answeredAgain.assertIsSatisfied();
    }

    @Test
    void testConnectionLostOnEveryRequest() throws Exception {
        // every answer is a 200 whose connection is reset before any of the body was sent: every open succeeds and
        // every read fails, and the scan does not end up in a loop of requests, each one reported
        serve(List.of("|cut"));

        startRoute("");

        awaitRequests(4);
        // the first failure in a row goes to the exception handler, the next ones are logged at DEBUG
        assertEquals(1, handledFailures.get());
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter());
        assertWaitedAfterFailures(4);
    }

    @Test
    void testConnectionLostOnEveryRequestRawMode() throws Exception {
        // in raw mode the whole body is read at once: the line sent before the reset is lost with it
        serve(List.of("a\n|cut"));

        startRoute("&readLine=false");

        awaitRequests(4);
        assertEquals(1, handledFailures.get());
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter());
        assertWaitedAfterFailures(4);
    }

    @Test
    void testConnectionLostAfterDataOnEveryRequest() throws Exception {
        // every answer sends a line before its connection is reset: each line is routed, and the scan still waits
        // after each failure (as a line was read, the failure after it is reported again)
        serve(List.of("a\n|cut"));
        MockEndpoint mock = expectFirstBodies("a", "a", "a");

        startRoute("");

        assertWaitedAfterFailures(4);
        mock.assertIsSatisfied();
        assertFirstBodies(mock, "a", "a", "a");
    }

    @Test
    void testServerNotAvailableWhenReopenedRawMode() throws Exception {
        serve(List.of("a\n", "", "b\n"));
        MockEndpoint mock = expectFirstBodies("a\n", "b\n");

        startRoute("&readLine=false");

        mock.assertIsSatisfied();
        assertFirstBodies(mock, "a\n", "b\n");
    }

    /**
     * An HTTP server that answers the requests with the given answers in turn, the last one for all the requests after,
     * and closes each connection after its answer: an empty answer is the status 503 (opening the stream fails), and an
     * answer that ends with "|cut" announces a longer body than it sends before the connection is reset (the connection
     * is lost while reading).
     */
    private void serve(List<String> answers) throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        serverThread.execute(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    readRequest(socket.getInputStream());
                    requestTimes.add(System.nanoTime());
                    String answer = answers.get(Math.min(requests.getAndIncrement(), answers.size() - 1));
                    socket.getOutputStream().write(response(answer));
                    socket.getOutputStream().flush();
                    if (answer.endsWith("|cut")) {
                        // give the client a moment to read what was sent (it sends nothing more, so the read times
                        // out), then reset the connection (lost, not ended)
                        socket.setSoTimeout(200);
                        try {
                            socket.getInputStream().read();
                        } catch (SocketTimeoutException e) {
                            // the client is waiting for the rest of the body
                        }
                        socket.setSoLinger(true, 0);
                    }
                } catch (IOException e) {
                    // closed by the test
                }
            }
        });
    }

    private static void readRequest(InputStream in) throws IOException {
        // up to the empty line that ends the request head (a GET has no body)
        int matched = 0;
        int b;
        while (matched < 4 && (b = in.read()) != -1) {
            matched = b == "\r\n\r\n".charAt(matched) ? matched + 1 : (b == '\r' ? 1 : 0);
        }
    }

    private static byte[] response(String answer) {
        if (answer.isEmpty()) {
            return "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII);
        }
        boolean cut = answer.endsWith("|cut");
        String body = answer.replace("|cut", "");
        int length = body.length() + (cut ? 100 : 0);
        return ("HTTP/1.1 200 OK\r\nContent-Length: " + length + "\r\nConnection: close\r\n\r\n" + body)
                .getBytes(StandardCharsets.US_ASCII);
    }

    private MockEndpoint expectFirstBodies(String... bodies) {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // with retry, the stream is opened again at its end, and the last answer is read again and again
        mock.expectedMinimumMessageCount(bodies.length);
        return mock;
    }

    private static void assertFirstBodies(MockEndpoint mock, String... bodies) {
        for (int i = 0; i < bodies.length; i++) {
            assertEquals(bodies[i], mock.getExchanges().get(i).getIn().getBody(String.class));
        }
    }

    /**
     * Waits for the given number of requests, each one after a request that failed, and checks that the consumer waited
     * about a second before each of them instead of asking the server again right away.
     */
    private void assertWaitedAfterFailures(int count) {
        awaitRequests(count);
        for (int i = 1; i < count; i++) {
            long millis = TimeUnit.NANOSECONDS.toMillis(requestTimes.get(i) - requestTimes.get(i - 1));
            assertTrue(millis >= FAILURE_DELAY_MILLIS, "request " + (i + 1) + " came " + millis + " ms after the one before");
        }
    }

    private void awaitRequests(int count) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> requestTimes.size() >= count);
    }

    private void startRoute(String options) throws Exception {
        String url = "http://127.0.0.1:" + server.getLocalPort() + "/data.txt";
        context.getRegistry().bind("countingExceptionHandler", new CountingExceptionHandler());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("stream:http?scanStream=true&retry=true&scanStreamDelay=50&httpUrl=" + url + options
                     + "&exceptionHandler=#countingExceptionHandler")
                        .to("mock:result")
                        .filter(body().isEqualTo("b")).to("mock:b");
            }
        });
        context.start();
    }

    /**
     * Counts the failures the consumer reports to its exception handler.
     */
    private class CountingExceptionHandler implements ExceptionHandler {

        @Override
        public void handleException(Throwable exception) {
            handledFailures.incrementAndGet();
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handledFailures.incrementAndGet();
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            handledFailures.incrementAndGet();
        }
    }
}

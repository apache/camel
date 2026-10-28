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
package org.apache.camel.component.slack;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.slack.api.model.Message;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A route that is suspended while the consumer routes the messages of a poll, and resumed: the messages of that poll
 * that were not routed must be routed after the resume. The consumer has moved its timestamp past them, so the
 * following polls do not read them again.
 */
class SlackConsumerSuspendedBatchTest extends CamelTestSupport {

    static final String ROUTE_ID = "slack";

    private final List<String> channel = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private final AtomicInteger historyRequests = new AtomicInteger();
    private boolean naturalOrder;

    final List<String> routed = new CopyOnWriteArrayList<>();
    // the text of the message whose processor suspends the route (once)
    volatile String suspendAtMessage;
    // suspend the whole CamelContext instead of the route
    private volatile boolean suspendContext;
    private final AtomicBoolean suspendRequested = new AtomicBoolean();
    private final AtomicReference<Exception> suspendFailure = new AtomicReference<>();
    private volatile Thread suspender;

    @Override
    protected void doPreSetup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/conversations.list", this::conversationsList);
        server.createContext("/api/conversations.history", this::conversationsHistory);
        server.start();
    }

    @AfterEach
    void stopServer() {
        // stop polling before the server goes away
        context.stop();
        if (server != null) {
            server.stop(0);
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void messagesNotRoutedByASuspendingConsumerAreRoutedAfterTheResume() throws Exception {
        startRouteAndReceiveFirstMessage();
        suspendAtMessage = "m4";

        // the most recent message is routed first
        postBurst();
        awaitRouteSuspended();
        assertEquals(List.of("m5", "m4"), routed, "the suspend ends the batch after the message that is being routed");

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("m5", "m4", "m3", "m2"), routed, "messages routed after the resume"));
        assertNothingElseIsRouted(List.of("m5", "m4", "m3", "m2"));
    }

    @Test
    void messagesNotRoutedByASuspendingConsumerAreRoutedAfterTheResumeInNaturalOrder() throws Exception {
        naturalOrder = true;
        startRouteAndReceiveFirstMessage();
        suspendAtMessage = "m3";

        postBurst();
        awaitRouteSuspended();
        assertEquals(List.of("m2", "m3"), routed, "the suspend ends the batch after the message that is being routed");

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("m2", "m3", "m4", "m5"), routed, "messages routed after the resume"));
        assertNothingElseIsRouted(List.of("m2", "m3", "m4", "m5"));
    }

    @Test
    void messagesPostedWhileSuspendedAreRoutedWithTheOnesThatWereNotRouted() throws Exception {
        startRouteAndReceiveFirstMessage();
        suspendAtMessage = "m5";

        postBurst();
        awaitRouteSuspended();
        assertEquals(List.of("m5"), routed);
        channel.add("m6");

        context.getRouteController().resumeRoute(ROUTE_ID);
        // the most recent first: the new message, then the ones that were left
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("m5", "m6", "m4", "m3", "m2"), routed, "messages routed after the resume"));
        assertNothingElseIsRouted(List.of("m5", "m6", "m4", "m3", "m2"));
    }

    @Test
    void messagesNotRoutedWhenTheCamelContextIsSuspendedAreRoutedAfterTheResume() throws Exception {
        suspendContext = true;
        startRouteAndReceiveFirstMessage();
        suspendAtMessage = "m4";

        postBurst();
        awaitRouteSuspended();
        assertEquals(List.of("m5", "m4"), routed, "the suspend ends the batch after the message that is being routed");

        // the same consumer is resumed
        context.resume();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("m5", "m4", "m3", "m2"), routed, "messages routed after the resume"));
        assertNothingElseIsRouted(List.of("m5", "m4", "m3", "m2"));
    }

    void startRouteAndReceiveFirstMessage() throws Exception {
        channel.add("m1");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("slack://general?token=RAW(xoxp-test)&serverUrl=http://localhost:%d&maxResults=10&delay=20"
                      + "&naturalOrder=%s",
                        server.getAddress().getPort(), naturalOrder)
                        .routeId(ROUTE_ID)
                        .process(exchange -> {
                            String text = exchange.getMessage().getBody(Message.class).getText();
                            if (text.equals(suspendAtMessage)) {
                                suspendRouteDuringThePoll();
                            }
                            routed.add(text);
                        });
            }
        });
        context.start();

        // the first poll reads the last message of the history
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(List.of("m1"), routed));
        routed.clear();
    }

    void postBurst() {
        // added at once, so that no poll sees only a part of the burst
        channel.addAll(List.of("m2", "m3", "m4", "m5"));
    }

    private void assertNothingElseIsRouted(List<String> expected) {
        // a few more polls: nothing is routed twice
        int before = historyRequests.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> historyRequests.get() >= before + 3);
        assertEquals(expected, routed);
    }

    /**
     * Called by the thread that polls: suspends the route from another thread, as a route must not suspend itself, and
     * returns when the suspend has reached the consumer (it no longer routes the following messages of the batch).
     */
    private void suspendRouteDuringThePoll() {
        if (!suspendRequested.compareAndSet(false, true)) {
            return;
        }
        SlackConsumer consumer = (SlackConsumer) context.getRoute(ROUTE_ID).getConsumer();
        suspender = new Thread(() -> {
            try {
                if (suspendContext) {
                    context.suspend();
                } else {
                    context.getRouteController().suspendRoute(ROUTE_ID);
                }
            } catch (Exception e) {
                suspendFailure.set(e);
            }
        }, "suspend-route");
        suspender.start();

        CountDownLatch batchEnded = new CountDownLatch(1);
        Thread watcher = new Thread(() -> {
            await().atMost(20, TimeUnit.SECONDS).until(() -> !consumer.isBatchAllowed());
            batchEnded.countDown();
        }, "watch-consumer");
        watcher.start();

        // an interrupt of the thread that polls must not end this wait
        boolean interrupted = false;
        boolean ended = false;
        long remaining = TimeUnit.SECONDS.toNanos(30);
        long deadline = System.nanoTime() + remaining;
        while (!ended && remaining > 0) {
            try {
                ended = batchEnded.await(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            remaining = deadline - System.nanoTime();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    void awaitRouteSuspended() throws InterruptedException {
        await().atMost(20, TimeUnit.SECONDS).until(() -> suspender != null);
        suspender.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(suspender.isAlive(), "the route suspend has returned");
        assertNull(suspendFailure.get());
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus(ROUTE_ID));
    }

    private void conversationsList(HttpExchange http) throws IOException {
        respond(http, "{\"ok\":true,\"channels\":[{\"id\":\"C1\",\"name\":\"general\"}],"
                      + "\"response_metadata\":{\"next_cursor\":\"\"}}");
    }

    /**
     * Answers as Slack does: the messages after {@code oldest} (exclusive), the most recent first, {@code limit} at a
     * time.
     */
    private void conversationsHistory(HttpExchange http) throws IOException {
        historyRequests.incrementAndGet();
        Map<String, String> params = form(http);
        String oldest = params.get("oldest");
        int limit = Integer.parseInt(params.get("limit"));

        List<Integer> range = new ArrayList<>();
        for (int i = 0; i < channel.size(); i++) {
            if (oldest == null || new BigDecimal(ts(i)).compareTo(new BigDecimal(oldest)) > 0) {
                range.add(i);
            }
        }
        range.sort(Comparator.reverseOrder());
        List<Integer> page = range.subList(0, Math.min(limit, range.size()));

        String messages = page.stream()
                .map(i -> "{\"type\":\"message\",\"text\":\"" + channel.get(i) + "\",\"ts\":\"" + ts(i) + "\"}")
                .collect(Collectors.joining(","));
        respond(http, "{\"ok\":true,\"messages\":[" + messages + "],\"has_more\":false,"
                      + "\"response_metadata\":{\"next_cursor\":\"\"}}");
    }

    private static String ts(int index) {
        return (1700000000 + index) + ".000100";
    }

    private static Map<String, String> form(HttpExchange http) throws IOException {
        Map<String, String> answer = new HashMap<>();
        String body = new String(http.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                answer.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return answer;
    }

    private static void respond(HttpExchange http, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        http.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = http.getResponseBody()) {
            os.write(bytes);
        }
    }
}

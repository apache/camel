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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.slack.api.model.Message;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer must not skip messages when more new messages than {@code maxResults} were posted between two polls:
 * conversations.history returns the most recent messages of the range first, and the older ones on the following pages
 * (has_more and response_metadata.next_cursor).
 */
class SlackConsumerHasMoreTest extends CamelTestSupport {

    private final List<String> channel = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private final AtomicInteger rateLimitedRequests = new AtomicInteger();
    private boolean naturalOrder;
    private volatile boolean rateLimitFollowingPages;
    private final List<String> cursors = new CopyOnWriteArrayList<>();
    private final AtomicReference<Runnable> beforeNextFollowingPage = new AtomicReference<>();
    private final CountDownLatch suspendedPollDone = new CountDownLatch(1);
    private volatile boolean suspendedWhilePaging;

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
    void consumesEveryMessageOfABurstLargerThanMaxResults() throws Exception {
        MockEndpoint mock = startRouteAndReceiveFirstMessage();

        // five messages are posted before the next poll, more than maxResults=2
        postBurst();

        mock.expectedMessageCount(5);
        mock.setAssertPeriod(500);
        mock.assertIsSatisfied();
        assertEquals(List.of("m6", "m5", "m4", "m3", "m2"), texts(mock),
                "every new message must be routed once, the most recent first");
    }

    @Test
    void consumesEveryMessageOfABurstInNaturalOrder() throws Exception {
        naturalOrder = true;
        MockEndpoint mock = startRouteAndReceiveFirstMessage();

        postBurst();

        mock.expectedMessageCount(5);
        mock.setAssertPeriod(500);
        mock.assertIsSatisfied();
        assertEquals(List.of("m2", "m3", "m4", "m5", "m6"), texts(mock),
                "every new message must be routed once, the oldest first");
    }

    @Test
    void routesTheMostRecentMessagesWhenAFollowingPageIsRateLimited() throws Exception {
        // apps that Slack limits to one conversations.history request per minute get HTTP 429 for the second page
        rateLimitFollowingPages = true;
        MockEndpoint mock = startRouteAndReceiveFirstMessage();

        postBurst();

        // as when only one page is read: the most recent maxResults messages are routed, once, and the poll does not
        // fail (a failed poll would read the whole range again at every following poll)
        mock.expectedMessageCount(2);
        mock.setAssertPeriod(500);
        mock.assertIsSatisfied();
        assertEquals(List.of("m6", "m5"), texts(mock));
        assertTrue(rateLimitedRequests.get() > 0, "the following page must have been requested");
    }

    @Test
    void stopsPagingWhenSuspendedAndReadsTheMessagesAgainAfterResume() throws Exception {
        MockEndpoint mock = startRouteAndReceiveFirstMessage();
        SlackConsumer consumer = (SlackConsumer) context.getRoute("slack").getConsumer();
        // the consumer is suspended while it waits for the second of the three pages
        beforeNextFollowingPage.set(() -> {
            consumer.suspend();
            suspendedWhilePaging = true;
        });

        postBurst();

        assertTrue(suspendedPollDone.await(20, TimeUnit.SECONDS), "the poll that was reading the pages did not end");
        assertEquals(List.of("2"), cursors, "the third page must not be requested once the consumer is suspended");
        assertEquals(0, mock.getReceivedCounter(), "a part of the burst was routed, so its older messages are skipped");

        mock.expectedMessageCount(5);
        mock.setAssertPeriod(500);
        consumer.resume();

        mock.assertIsSatisfied();
        assertEquals(List.of("m6", "m5", "m4", "m3", "m2"), texts(mock),
                "every message of the burst must be routed once after the resume");
        assertEquals(List.of("2", "2", "4"), cursors);
    }

    private MockEndpoint startRouteAndReceiveFirstMessage() throws Exception {
        channel.add("m1");
        // tells when the poll during which the route was suspended has ended
        context.getRegistry().bind("pollEnded", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                if (suspendedWhilePaging) {
                    suspendedPollDone.countDown();
                }
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                return false;
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("slack://general?token=RAW(xoxp-test)&serverUrl=http://localhost:%d&maxResults=2&delay=100"
                      + "&naturalOrder=%s&pollStrategy=#pollEnded",
                        server.getAddress().getPort(), naturalOrder)
                        .routeId("slack")
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        // the first poll reads the last message of the history
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
        assertEquals(List.of("m1"), texts(mock));
        mock.reset();
        return mock;
    }

    private void postBurst() {
        // added at once, so that no poll sees only a part of the burst
        channel.addAll(List.of("m2", "m3", "m4", "m5", "m6"));
    }

    private static List<String> texts(MockEndpoint mock) {
        List<String> answer = new ArrayList<>();
        for (Exchange exchange : mock.getReceivedExchanges()) {
            answer.add(exchange.getMessage().getBody(Message.class).getText());
        }
        return answer;
    }

    private void conversationsList(HttpExchange http) throws IOException {
        respond(http, "{\"ok\":true,\"channels\":[{\"id\":\"C1\",\"name\":\"general\"}],"
                      + "\"response_metadata\":{\"next_cursor\":\"\"}}");
    }

    /**
     * Answers as Slack does: the messages after {@code oldest} (exclusive), the most recent first, {@code limit} at a
     * time, with has_more and a next_cursor when there are more.
     */
    private void conversationsHistory(HttpExchange http) throws IOException {
        Map<String, String> params = form(http);
        String oldest = params.get("oldest");
        int limit = Integer.parseInt(params.get("limit"));
        if (rateLimitFollowingPages && params.get("cursor") != null) {
            rateLimitedRequests.incrementAndGet();
            http.getResponseHeaders().add("Retry-After", "60");
            respond(http, 429, "{\"ok\":false,\"error\":\"ratelimited\"}");
            return;
        }
        if (params.get("cursor") != null) {
            cursors.add(params.get("cursor"));
            Runnable task = beforeNextFollowingPage.getAndSet(null);
            if (task != null) {
                task.run();
            }
        }
        int offset = params.get("cursor") != null ? Integer.parseInt(params.get("cursor")) : 0;

        List<Integer> range = new ArrayList<>();
        for (int i = 0; i < channel.size(); i++) {
            if (oldest == null || new BigDecimal(ts(i)).compareTo(new BigDecimal(oldest)) > 0) {
                range.add(i);
            }
        }
        range.sort(Comparator.reverseOrder());
        List<Integer> page = range.subList(Math.min(offset, range.size()), Math.min(offset + limit, range.size()));
        boolean hasMore = offset + limit < range.size();

        String messages = page.stream()
                .map(i -> "{\"type\":\"message\",\"text\":\"" + channel.get(i) + "\",\"ts\":\"" + ts(i) + "\"}")
                .collect(Collectors.joining(","));
        respond(http, "{\"ok\":true,\"messages\":[" + messages + "],\"has_more\":" + hasMore
                      + ",\"response_metadata\":{\"next_cursor\":\"" + (hasMore ? offset + limit : "") + "\"}}");
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
        respond(http, 200, json);
    }

    private static void respond(HttpExchange http, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        http.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = http.getResponseBody()) {
            os.write(bytes);
        }
    }
}

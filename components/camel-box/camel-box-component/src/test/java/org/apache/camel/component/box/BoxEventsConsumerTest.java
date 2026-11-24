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
package org.apache.camel.component.box;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxAPIRequest;
import com.box.sdk.BoxAPIResponse;
import com.box.sdk.BoxEvent;
import com.box.sdk.BoxJSONResponse;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.internal.BoxConnectionHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The events consumer routes the events of the Box event stream and nothing else (the polls of the API consumer do not
 * route exchanges of their own), and listens again when the event stream of the Box SDK fails (Box cannot be reached
 * for a while): the SDK stops the stream for good on such a failure.
 * <p>
 * The Box connection answers from memory, as the service does: the long poll URL from OPTIONS /events, new_change on
 * the long poll while there are events after the stream position, and the events after a stream position with the next
 * position. A request that fails throws the exception the SDK throws for a network error.
 */
class BoxEventsConsumerTest {

    private static final String REALTIME_URL = "https://realtime.box.test/subscribe?channel=1";

    /** The events of the account, in order: the stream position is the index in this list. */
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    /** The number of requests for events that fail before the next one is answered. */
    private final AtomicInteger failingEventRequests = new AtomicInteger();
    private final List<String> eventRequestPositions = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch firstEventRouted = new CountDownLatch(1);

    private CamelContext context;
    private MockedStatic<BoxConnectionHelper> helper;

    @BeforeEach
    void setUp() {
        BoxAPIConnection connection = new BoxAPIConnection("test-token");
        connection.setRequestInterceptor(this::respond);
        helper = Mockito.mockStatic(BoxConnectionHelper.class);
        helper.when(() -> BoxConnectionHelper.createConnection(Mockito.any())).thenReturn(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (context != null) {
                context.stop();
            }
        } finally {
            helper.close();
        }
    }

    @Test
    void routesNothingWithoutEvents() throws Exception {
        MockEndpoint mock = createRoute();
        mock.expectedMessageCount(0);
        // several polls of the consumer (delay 500 ms)
        mock.setAssertPeriod(2000);
        context.start();

        mock.assertIsSatisfied();
    }

    @Test
    void routesEachEventOnce() throws Exception {
        events.add("event-0");
        MockEndpoint mock = createRoute();
        mock.expectedBodiesReceived("event-0");
        mock.setResultWaitTime(10000);
        // several polls of the consumer (delay 500 ms)
        mock.setAssertPeriod(2000);
        context.start();

        mock.assertIsSatisfied();
    }

    @Test
    void listensAgainWhenTheEventStreamFails() throws Exception {
        events.add("event-0");
        // Box cannot be reached when the consumer asks for the first events
        failingEventRequests.set(1);

        MockEndpoint mock = createRoute();
        mock.expectedBodiesReceived("event-0");
        mock.setResultWaitTime(10000);
        context.start();

        mock.assertIsSatisfied();
    }

    @Test
    void continuesAfterTheLastEventsReceived() throws Exception {
        events.add("event-0");
        MockEndpoint mock = createRoute();
        mock.expectedBodiesReceived("event-0", "event-1");
        mock.setResultWaitTime(10000);
        context.start();

        assertTrue(firstEventRouted.await(10, TimeUnit.SECONDS));
        // Box cannot be reached when the next event comes
        failingEventRequests.set(2);
        events.add("event-1");

        mock.assertIsSatisfied();
        // the stream is opened again at the position after the events received, not at the start
        assertEquals("1", eventRequestPositions.get(eventRequestPositions.size() - 1));
    }

    /**
     * Creates the CamelContext with the route of the consumer; the test sets the expectations of the mock endpoint it
     * returns, then starts the context.
     */
    private MockEndpoint createRoute() throws Exception {
        BoxConfiguration configuration = new BoxConfiguration();
        configuration.setUserName("camel@apache.org");
        configuration.setUserPassword("p4ssw0rd");
        configuration.setClientId("camel-client-id");
        configuration.setClientSecret("camel-client-secret");
        configuration.setAuthenticationType("STANDARD_AUTHENTICATION");
        BoxComponent component = new BoxComponent();
        component.setConfiguration(configuration);

        context = new DefaultCamelContext();
        context.addComponent("box", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("box:events/listen?startingPosition=0")
                        // the id of the event as body, and any other body as it is
                        .process(e -> {
                            BoxEvent event = e.getMessage().getBody(BoxEvent.class);
                            if (event != null) {
                                e.getMessage().setBody(event.getID());
                            }
                        })
                        .to("mock:events")
                        .process(e -> firstEventRouted.countDown());
            }
        });
        return context.getEndpoint("mock:events", MockEndpoint.class);
    }

    private BoxAPIResponse respond(BoxAPIRequest request) {
        String url = request.getUrl().toString();
        if ("OPTIONS".equals(request.getMethod())) {
            JsonObject entry = new JsonObject().add("type", "realtime_server").add("url", REALTIME_URL)
                    .add("ttl", "10").add("max_retries", "10").add("retry_timeout", 610);
            return json(request, new JsonObject().add("chunk_size", 1).add("entries", new JsonArray().add(entry)));
        }
        long position = Long.parseLong(parameter(url, "stream_position"));
        if (url.startsWith(REALTIME_URL)) {
            if (position < events.size()) {
                return json(request, new JsonObject().add("message", "new_change"));
            }
            // a long poll waits for a change: answer after a while that there was none
            try {
                new CountDownLatch(1).await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return json(request, new JsonObject().add("message", "reconnect"));
        }
        eventRequestPositions.add(String.valueOf(position));
        if (failingEventRequests.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new BoxAPIException(
                    "Couldn't connect to the Box API due to a network error. Request\n" + url,
                    new IOException("Connection refused"));
        }
        JsonArray entries = new JsonArray();
        int next;
        synchronized (events) {
            next = events.size();
            for (int i = (int) position; i < next; i++) {
                entries.add(new JsonObject().add("type", "event").add("event_id", events.get(i))
                        .add("event_type", "ITEM_UPLOAD").add("created_at", "2026-01-01T00:00:00-00:00"));
            }
        }
        return json(request, new JsonObject().add("chunk_size", entries.size())
                .add("next_stream_position", next).add("entries", entries));
    }

    private static BoxJSONResponse json(BoxAPIRequest request, JsonObject body) {
        return new BoxJSONResponse(200, request.getMethod(), request.getUrl().toString(), Map.of(), body);
    }

    private static String parameter(String url, String name) {
        for (String parameter : url.substring(url.indexOf('?') + 1).split("&")) {
            if (parameter.startsWith(name + "=")) {
                return parameter.substring(name.length() + 1);
            }
        }
        throw new IllegalArgumentException("No " + name + " in " + url);
    }
}

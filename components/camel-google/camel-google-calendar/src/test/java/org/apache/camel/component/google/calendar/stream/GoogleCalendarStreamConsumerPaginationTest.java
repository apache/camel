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
package org.apache.camel.component.google.calendar.stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.calendar.Calendar;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCalendarStreamConsumerPaginationTest {

    private DefaultCamelContext context;
    private final List<GenericUrl> requests = new ArrayList<>();
    private final List<String> delivered = new ArrayList<>();
    private final List<String> responses = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void pollsPastAnAlreadyDeliveredBoundaryEvent() throws Exception {
        responses.add(page("a", "2023-11-14T22:13:20Z", null));
        responses.add(page("a", "2023-11-14T22:13:20Z", "second"));
        responses.add(page("b", "2023-11-14T22:13:25Z", null));
        GoogleCalendarStreamConsumer consumer = consumer(false);

        assertEquals(1, consumer.poll());
        assertEquals(1, consumer.poll());

        assertEquals(List.of("a", "b"), delivered);
        assertEquals(3, requests.size());
        assertEquals("second", requests.get(2).getFirst("pageToken"));
        assertEquals(requests.get(1).getFirst("updatedMin"), requests.get(2).getFirst("updatedMin"));
        assertEquals("1", requests.get(2).getFirst("maxResults"));
    }

    @Test
    void pollsAllEventsWithTheSameUpdateTime() throws Exception {
        responses.add(page("a", "2023-11-14T22:13:20Z", "second"));
        responses.add(page("b", "2023-11-14T22:13:20Z", null));
        responses.add(page("a", "2023-11-14T22:13:20Z", "second"));
        responses.add(page("b", "2023-11-14T22:13:20Z", null));
        GoogleCalendarStreamConsumer consumer = consumer(false);

        assertEquals(2, consumer.poll());
        assertEquals(0, consumer.poll());
        assertEquals(List.of("a", "b"), delivered);
    }

    @Test
    void followsAPageTokenEvenWhenThePageHasNoItems() throws Exception {
        responses.add("{\"nextPageToken\":\"second\"}");
        responses.add(page("b", "2023-11-14T22:13:20Z", null));

        assertEquals(1, consumer(false).poll());
        assertEquals(List.of("b"), delivered);
    }

    @Test
    void aFailedLaterPageDoesNotAdvanceTheCursor() throws Exception {
        responses.add(page("a", "2023-11-14T22:13:20Z", "second"));
        responses.add(null);
        GoogleCalendarStreamConsumer consumer = consumer(false);

        assertThrows(IOException.class, consumer::poll);
        assertNull(consumer.getLastUpdate());
        assertTrue(delivered.isEmpty());
    }

    @Test
    void syncFlowKeepsOnePagePerPollAndUsesTheSyncToken() throws Exception {
        responses.add(page("a", "2023-11-14T22:13:20Z", "second"));
        responses.add("{\"nextSyncToken\":\"sync\",\"items\":[]}");
        responses.add("{\"nextSyncToken\":\"sync2\",\"items\":[]}");
        GoogleCalendarStreamConsumer consumer = consumer(true);

        assertEquals(1, consumer.poll());
        assertEquals(1, requests.size());
        assertEquals(0, consumer.poll());
        assertEquals("second", requests.get(1).getFirst("pageToken"));
        assertEquals(0, consumer.poll());
        assertEquals("sync", requests.get(2).getFirst("syncToken"));
        assertNull(requests.get(2).getFirst("pageToken"));
    }

    private GoogleCalendarStreamConsumer consumer(boolean syncFlow) {
        GoogleCalendarStreamEndpoint endpoint = context.getEndpoint(
                "google-calendar-stream://events?consumeFromNow=false&considerLastUpdate=" + !syncFlow
                                                                    + "&syncFlow=" + syncFlow
                                                                    + "&maxResults=1&clientId=id&clientSecret=secret",
                GoogleCalendarStreamEndpoint.class);
        Calendar client = new Calendar.Builder(new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest(url) {
                    @Override
                    public LowLevelHttpResponse execute() throws IOException {
                        requests.add(new GenericUrl(url));
                        String response = responses.removeFirst();
                        if (response == null) {
                            throw new IOException("Cannot read the next page");
                        }
                        return new MockLowLevelHttpResponse().setContentType("application/json").setContent(response);
                    }
                };
            }
        }, GsonFactory.getDefaultInstance(), null).setApplicationName("test").build();
        return new GoogleCalendarStreamConsumer(endpoint, exchange -> {
        }) {
            @Override
            protected Calendar getClient() {
                return client;
            }

            @Override
            public int processBatch(Queue<Object> exchanges) {
                int count = exchanges.size();
                for (Object value : exchanges) {
                    Exchange exchange = (Exchange) value;
                    delivered.add(exchange.getIn().getHeader(GoogleCalendarStreamConstants.EVENT_ID, String.class));
                }
                return count;
            }
        };
    }

    private static String page(String id, String updated, String nextToken) {
        return "{\"items\":[{\"id\":\"" + id + "\",\"updated\":\"" + updated + "\"}]"
               + (nextToken == null ? "" : ",\"nextPageToken\":\"" + nextToken + "\"") + "}";
    }
}

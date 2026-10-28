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
package org.apache.camel.component.aws.cloudtrail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.Event;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsRequest;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CloudTrail can make an event visible to LookupEvents after a newer event was already returned. The consumer must
 * still route such a late event, exactly once, with a late-event lookup every lookBackInterval, while its normal polls
 * send the same requests as before. Time is driven by the test, polls are called directly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloudtrailConsumerLateEventTest {

    private static final Instant T = Instant.parse("2099-01-01T00:00:00Z");

    @Mock
    private CloudTrailClient cloudTrailClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    // the events the fake service currently makes visible to LookupEvents
    private final List<Event> visible = new ArrayList<>();
    private final List<LookupEventsRequest> requests = new ArrayList<>();
    private final List<String> routed = new ArrayList<>();
    private Instant clock = T;
    // when set, the late-event lookup (the request with an endTime) fails with it
    private RuntimeException lookBackFailure;
    private CloudtrailConfiguration configuration;
    private CloudtrailEndpoint endpoint;
    private CloudtrailConsumer consumer;

    private static Event event(String id, Instant time) {
        return Event.builder().eventId(id).eventName("name").eventSource("src").username("user")
                .eventTime(time).cloudTrailEvent("{}").build();
    }

    @BeforeEach
    void setup() throws Exception {
        CloudtrailComponent component = new CloudtrailComponent(context);
        component.start();
        configuration = new CloudtrailConfiguration();
        configuration.setCloudTrailClient(cloudTrailClient);
        endpoint = new CloudtrailEndpoint("aws-cloudtrail://test", configuration, component);
        endpoint.start();

        // LookupEvents: startTime and endTime are inclusive, newest event first, maxResults events per page
        when(cloudTrailClient.lookupEvents(any(LookupEventsRequest.class))).thenAnswer(inv -> {
            LookupEventsRequest request = inv.getArgument(0);
            requests.add(request);
            if (lookBackFailure != null && request.endTime() != null) {
                throw lookBackFailure;
            }
            List<Event> matching = new ArrayList<>();
            for (Event e : visible) {
                if ((request.startTime() == null || !e.eventTime().isBefore(request.startTime()))
                        && (request.endTime() == null || !e.eventTime().isAfter(request.endTime()))) {
                    matching.add(e);
                }
            }
            matching.sort(Comparator.comparing(Event::eventTime).reversed());
            int from = request.nextToken() == null ? 0 : Integer.parseInt(request.nextToken());
            int to = Math.min(from + request.maxResults(), matching.size());
            return LookupEventsResponse.builder().events(matching.subList(from, to))
                    .nextToken(to < matching.size() ? String.valueOf(to) : null).build();
        });
        when(processor.process(any(Exchange.class), any(AsyncCallback.class))).thenAnswer(inv -> {
            Exchange exchange = inv.getArgument(0);
            routed.add(exchange.getMessage().getHeader(CloudtrailConstants.EVENT_ID, String.class));
            return false;
        });
    }

    private void startConsumer() {
        consumer = new CloudtrailConsumer(endpoint, processor) {
            @Override
            protected Instant now() {
                return clock;
            }
        };
        consumer.start();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.stop();
        }
        context.stop();
    }

    // polls and returns the number of LookupEvents requests the poll sent
    private int pollRequests() throws Exception {
        int before = requests.size();
        consumer.poll();
        return requests.size() - before;
    }

    @Test
    void lateEventWithEarlierEventTimeIsRoutedOnceByTheLookBack() throws Exception {
        startConsumer();
        visible.add(event("newer", T.plusSeconds(20)));
        assertEquals(1, consumer.poll());

        // an API call made before "newer" becomes visible only now
        visible.add(event("late", T.plusSeconds(10)));
        clock = T.plusSeconds(30);
        assertEquals(0, consumer.poll(), "the normal poll starts at the newest event time");

        clock = T.plusSeconds(60);
        assertEquals(1, consumer.poll(), "the late-event lookup routes the late event");
        assertEquals(0, consumer.poll(), "nothing is routed twice");
        clock = T.plusSeconds(120);
        assertEquals(0, consumer.poll(), "nothing is routed twice");

        assertEquals(List.of("newer", "late"), routed);
    }

    @Test
    void normalPollsSendTheSameRequestsAsBefore() throws Exception {
        configuration.setMaxResults(2);
        startConsumer();
        visible.add(event("a", T.plusSeconds(1)));
        visible.add(event("b", T.plusSeconds(2)));
        visible.add(event("c", T.plusSeconds(3)));

        // between two late-event lookups each poll sends only the lookup from the newest event time, all pages
        assertEquals(2, pollRequests());
        assertEquals(T, requests.get(0).startTime());
        assertNull(requests.get(0).nextToken());
        assertEquals(T, requests.get(1).startTime());
        assertEquals("2", requests.get(1).nextToken());
        clock = T.plusSeconds(59);
        for (int i = 0; i < 5; i++) {
            assertEquals(1, pollRequests());
            assertEquals(T.plusSeconds(3), requests.get(requests.size() - 1).startTime());
        }
        for (LookupEventsRequest request : requests) {
            assertNull(request.endTime());
        }
        assertEquals(List.of("c", "b", "a"), routed);
    }

    @Test
    void lookBackSendsOnePagePerPollAndDoesNotRouteAgain() throws Exception {
        configuration.setMaxResults(2);
        startConsumer();
        visible.add(event("before-start", T.minusSeconds(30)));
        for (int i = 1; i <= 5; i++) {
            visible.add(event("e" + i, T.plusSeconds(i)));
        }
        assertEquals(3, pollRequests());
        assertEquals(5, routed.size());

        // the late-event lookup reads [start, newest event time]: 5 events, 3 pages, one per poll
        clock = T.plusSeconds(60);
        for (int page = 0; page < 3; page++) {
            assertEquals(2, pollRequests(), "the normal lookup and one page of the late-event lookup");
            LookupEventsRequest lookBack = requests.get(requests.size() - 1);
            assertEquals(T, lookBack.startTime(), "never before the consumer start");
            assertEquals(T.plusSeconds(5), lookBack.endTime());
        }
        assertEquals(1, pollRequests(), "the late-event lookup is done until lookBackInterval has passed");
        assertEquals(5, routed.size(), "already routed events are not routed again");
        assertEquals(5, consumer.getRoutedEventIdsSize());

        // once the window has passed the routed events, their ids are dropped and no late-event lookup is sent
        clock = T.plusSeconds(60 + 600);
        assertEquals(1, pollRequests());
        assertEquals(0, consumer.getRoutedEventIdsSize());
        clock = T.plusSeconds(60 + 600 + 60);
        assertEquals(1, pollRequests());
        assertEquals(5, routed.size());
    }

    @Test
    void failedLookBackDoesNotLoseTheEventsOfThePoll() throws Exception {
        startConsumer();
        visible.add(event("a", T.plusSeconds(1)));
        assertEquals(1, consumer.poll());

        visible.add(event("late", T));
        visible.add(event("b", T.plusSeconds(2)));
        lookBackFailure = new IllegalStateException("throttled");
        clock = T.plusSeconds(60);
        assertEquals(1, consumer.poll(), "the event of the normal lookup is routed");
        lookBackFailure = null;
        assertEquals(0, consumer.poll(), "the late-event lookup waits for the next lookBackInterval");
        clock = T.plusSeconds(120);
        assertEquals(1, consumer.poll());

        assertEquals(List.of("a", "b", "late"), routed);
    }

    @Test
    void lookBackWindowZeroBehavesAsBefore() throws Exception {
        configuration.setLookBackWindow(0);
        startConsumer();
        visible.add(event("newer", T.plusSeconds(20)));
        assertEquals(1, pollRequests());
        visible.add(event("late", T.plusSeconds(10)));
        for (int minute = 1; minute <= 3; minute++) {
            clock = T.plusSeconds(60L * minute);
            assertEquals(1, pollRequests());
        }
        for (LookupEventsRequest request : requests.subList(1, requests.size())) {
            assertEquals(T.plusSeconds(20), request.startTime());
            assertNull(request.endTime());
        }
        assertEquals(List.of("newer"), routed, "a late event is not routed, as before");
        assertEquals(0, consumer.getRoutedEventIdsSize());
    }

    @Test
    void lookBackIntervalOfZeroOrLessIsRejected() {
        for (long interval : new long[] { 0, -1 }) {
            configuration.setLookBackInterval(interval);
            Exception e = assertThrows(Exception.class, this::startConsumer);
            IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class,
                    e instanceof IllegalArgumentException ? e : e.getCause());
            assertTrue(cause.getMessage().contains("lookBackInterval"), cause.getMessage());
            consumer = null;
        }
    }

    @Test
    void negativeLookBackWindowIsRejected() {
        configuration.setLookBackWindow(-1);
        Exception e = assertThrows(Exception.class, this::startConsumer);
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class,
                e instanceof IllegalArgumentException ? e : e.getCause());
        assertTrue(cause.getMessage().contains("lookBackWindow"), cause.getMessage());
        consumer = null;
    }

    @Test
    void lookBackIntervalIsNotCheckedWhenTheLookBackIsDisabled() throws Exception {
        configuration.setLookBackWindow(0);
        configuration.setLookBackInterval(0);
        startConsumer();
        visible.add(event("newer", T.plusSeconds(20)));
        assertEquals(1, pollRequests());
        assertEquals(List.of("newer"), routed);
    }
}

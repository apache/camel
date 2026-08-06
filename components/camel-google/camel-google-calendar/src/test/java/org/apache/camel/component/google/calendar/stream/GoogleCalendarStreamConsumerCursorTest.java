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

import java.util.List;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how the stream consumer moves its {@code updatedMin} cursor between polls. The calendar filter is inclusive,
 * so the boundary events come back on the next poll and have to be recognised as already delivered, without skipping
 * anything modified within the same instant.
 */
class GoogleCalendarStreamConsumerCursorTest {

    private static final long T1 = 1_700_000_000_000L;
    private static final long T2 = T1 + 5_000L;

    private DefaultCamelContext context;
    private GoogleCalendarStreamConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        GoogleCalendarStreamEndpoint endpoint = context.getEndpoint(
                "google-calendar-stream://events?considerLastUpdate=true&clientId=id&clientSecret=secret",
                GoogleCalendarStreamEndpoint.class);
        consumer = new GoogleCalendarStreamConsumer(endpoint, exchange -> {
        });
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private static Event event(String id, long updated) {
        return new Event().setId(id).setUpdated(new DateTime(updated));
    }

    private static List<String> ids(List<Event> events) {
        return events.stream().map(Event::getId).toList();
    }

    @Test
    void theBoundaryEventIsNotDeliveredTwice() {
        assertThat(ids(consumer.selectUndeliveredAndMoveCursor(List.of(event("a", T1), event("b", T2)))))
                .containsExactly("a", "b");
        assertThat(consumer.getLastUpdate().getValue()).isEqualTo(T2);

        // updatedMin is inclusive, so the next poll gets the newest event back
        assertThat(consumer.selectUndeliveredAndMoveCursor(List.of(event("b", T2)))).isEmpty();
    }

    @Test
    void anEventModifiedInTheSameInstantIsStillDelivered() {
        assertThat(ids(consumer.selectUndeliveredAndMoveCursor(List.of(event("a", T2))))).containsExactly("a");

        // "b" carries exactly the cursor timestamp but was never delivered: bumping the cursor past that
        // instant, as adding a second did, would drop it
        assertThat(ids(consumer.selectUndeliveredAndMoveCursor(List.of(event("a", T2), event("b", T2)))))
                .containsExactly("b");
        assertThat(consumer.getLastUpdate().getValue()).isEqualTo(T2);

        assertThat(consumer.selectUndeliveredAndMoveCursor(List.of(event("a", T2), event("b", T2)))).isEmpty();
    }

    @Test
    void anEmptyPollDoesNotMoveTheCursor() {
        consumer.selectUndeliveredAndMoveCursor(List.of(event("a", T1)));
        assertThat(consumer.getLastUpdate().getValue()).isEqualTo(T1);

        // nothing new to report: the cursor has to stay where it was, moving it to "now" would skip
        // everything modified in between
        assertThat(consumer.selectUndeliveredAndMoveCursor(List.of())).isEmpty();
        assertThat(consumer.getLastUpdate().getValue()).isEqualTo(T1);

        assertThat(ids(consumer.selectUndeliveredAndMoveCursor(List.of(event("b", T2))))).containsExactly("b");
        assertThat(consumer.getLastUpdate().getValue()).isEqualTo(T2);
    }

    @Test
    void eventsWithoutAnUpdateTimeDoNotMoveTheCursor() {
        assertThat(ids(consumer.selectUndeliveredAndMoveCursor(List.of(new Event().setId("a")))))
                .containsExactly("a");
        assertThat(consumer.getLastUpdate()).isNull();
    }
}

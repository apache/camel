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
package org.apache.camel.component.atom;

import java.util.ArrayList;
import java.util.List;

import com.apptasticsoftware.rssreader.Item;
import org.apache.camel.Consumer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The consumer is idempotent by default: it routes each entry of a feed once, with its default options. No entry may be
 * skipped because of the order of the feed or because another entry has the same date.
 */
@DisabledOnOs(OS.AIX)
class AtomEntryDefaultIdempotencyTest extends CamelTestSupport {

    // the published dates are in the order first to fourth, but the first entry was updated after all the others
    private static final String EDITED_FEED
            = "atom:file:src/test/data/edited-feed.atom?splitEntries=true&throttleEntries=false&startScheduler=false";

    // three entries with the same published and updated date
    private static final String SAME_DATE_FEED
            = "atom:file:src/test/data/same-date-feed.atom?splitEntries=true&throttleEntries=false&startScheduler=false";

    @Test
    void entriesNewerThanAnEditedOlderEntryAreRouted() throws Exception {
        List<List<String>> polls = pollTwice(EDITED_FEED);

        assertEquals(List.of("second", "third, no updated date", "fourth", "first, edited last"), polls.get(0));
        // the entries were routed, and are not routed again
        assertEquals(List.of(), polls.get(1));
    }

    @Test
    void entriesWithTheSameDateAreAllRouted() throws Exception {
        List<List<String>> polls = pollTwice(SAME_DATE_FEED);

        assertEquals(3, polls.get(0).size(), "All the entries must be routed: " + polls.get(0));
        assertEquals(List.of(), polls.get(1));
    }

    @Test
    void entriesInTheOrderOfTheFeedWithoutIdempotency() throws Exception {
        List<List<String>> polls = pollTwice(EDITED_FEED + "&idempotent=false");

        // without the idempotent consumer the natural order of the feed reader is kept, oldest published first
        List<String> expected = List.of("first, edited last", "second", "third, no updated date", "fourth");
        assertEquals(expected, polls.get(0));
        assertEquals(expected, polls.get(1));
    }

    /**
     * Runs two polls of one consumer of the given endpoint (its scheduler is not started) and returns the titles of the
     * entries that each poll routes.
     */
    private List<List<String>> pollTwice(String uri) throws Exception {
        List<String> routed = new ArrayList<>();
        Consumer consumer = context.getEndpoint(uri)
                .createConsumer(exchange -> routed.add(exchange.getIn().getBody(Item.class).getTitle().orElseThrow()));
        consumer.start();
        try {
            ((Runnable) consumer).run();
            List<String> first = new ArrayList<>(routed);
            routed.clear();
            ((Runnable) consumer).run();
            return List.of(first, new ArrayList<>(routed));
        } finally {
            consumer.stop();
        }
    }
}

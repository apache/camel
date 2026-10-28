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

import java.time.ZonedDateTime;
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
 * With sortEntries the entries of a feed must be routed from the oldest to the newest one, by their updated date (and
 * by their published date when they have no updated date). The default idempotent strategy compares the same date and
 * skips an entry that is older than the one routed before it, so any other order loses entries.
 */
@DisabledOnOs(OS.AIX)
class AtomEntrySortTest extends CamelTestSupport {

    // the feed has 10 entries, which are not in the order of their dates
    private static final String FEED
            = "atom:file:src/test/data/unsortedfeed.atom?splitEntries=true&throttleEntries=false&startScheduler=false";

    // the published dates are in the order first to fourth, but the first entry was updated after all the others
    private static final String EDITED_FEED
            = "atom:file:src/test/data/edited-feed.atom?splitEntries=true&throttleEntries=false&startScheduler=false";

    @Test
    void sortedEntriesAreAllRoutedOldestFirst() throws Exception {
        List<ZonedDateTime> routed = pollOnce(FEED + "&sortEntries=true");

        assertEquals(10, routed.size(), "All the entries of the feed must be routed");
        assertEquals(sorted(routed), routed);
    }

    @Test
    void sortedEntriesAreRoutedOldestFirstWithoutIdempotency() throws Exception {
        List<ZonedDateTime> routed = pollOnce(FEED + "&sortEntries=true&idempotent=false");

        assertEquals(10, routed.size());
        assertEquals(sorted(routed), routed);
    }

    @Test
    void entriesAreAllRoutedWithoutSortEntries() throws Exception {
        List<ZonedDateTime> routed = pollOnce(FEED);

        assertEquals(10, routed.size());
        assertEquals(sorted(routed), routed);
    }

    @Test
    void sortedEntriesAreRoutedByUpdatedDateWhenAnOlderEntryWasEdited() throws Exception {
        List<String> routed = pollTitlesOnce(EDITED_FEED + "&sortEntries=true");

        // the entry published first was updated last, and the third entry has only a published date
        assertEquals(List.of("second", "third, no updated date", "fourth", "first, edited last"), routed);
    }

    @Test
    void sortedEntriesAreRoutedByUpdatedDateWithoutIdempotency() throws Exception {
        List<String> routed = pollTitlesOnce(EDITED_FEED + "&sortEntries=true&idempotent=false");

        assertEquals(List.of("second", "third, no updated date", "fourth", "first, edited last"), routed);
    }

    private List<String> pollTitlesOnce(String uri) throws Exception {
        return pollItemsOnce(uri).stream().map(item -> item.getTitle().orElseThrow()).toList();
    }

    /**
     * Returns the updated dates of the entries that one poll of the given endpoint routes.
     */
    private List<ZonedDateTime> pollOnce(String uri) throws Exception {
        return pollItemsOnce(uri).stream().map(item -> item.getUpdatedAsZonedDateTime().orElseThrow()).toList();
    }

    /**
     * Runs one poll of a consumer of the given endpoint (its scheduler is not started) and returns the routed entries.
     */
    private List<Item> pollItemsOnce(String uri) throws Exception {
        List<Item> routed = new ArrayList<>();
        Consumer consumer = context.getEndpoint(uri)
                .createConsumer(exchange -> routed.add(exchange.getIn().getBody(Item.class)));
        consumer.start();
        try {
            ((Runnable) consumer).run();
        } finally {
            consumer.stop();
        }
        return routed;
    }

    private static List<ZonedDateTime> sorted(List<ZonedDateTime> dates) {
        List<ZonedDateTime> answer = new ArrayList<>(dates);
        answer.sort(null);
        return answer;
    }
}

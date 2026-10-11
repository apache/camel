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
 * With sortEntries the entries of a feed must be routed from the oldest to the newest one, as camel-rss does. The
 * default idempotent strategy skips an entry that is older than the one routed before it, so any other order loses
 * entries.
 */
@DisabledOnOs(OS.AIX)
class AtomEntrySortTest extends CamelTestSupport {

    // the feed has 10 entries, which are not in the order of their dates
    private static final String FEED
            = "atom:file:src/test/data/unsortedfeed.atom?splitEntries=true&throttleEntries=false&startScheduler=false";

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

    /**
     * Runs one poll of a consumer of the given endpoint (its scheduler is not started) and returns the dates of the
     * routed entries.
     */
    private List<ZonedDateTime> pollOnce(String uri) throws Exception {
        List<ZonedDateTime> routed = new ArrayList<>();
        Consumer consumer = context.getEndpoint(uri).createConsumer(
                exchange -> routed.add(exchange.getIn().getBody(Item.class).getUpdatedZonedDateTime().orElseThrow()));
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

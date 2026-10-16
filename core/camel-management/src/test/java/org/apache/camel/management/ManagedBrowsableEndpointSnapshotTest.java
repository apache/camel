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
package org.apache.camel.management;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.browse.BrowseEndpoint;
import org.apache.camel.management.mbean.ManagedBrowsableEndpoint;
import org.apache.camel.spi.BrowsableEndpoint.BrowseStatus;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The readers of a browse endpoint must read one snapshot of its list: with a browse limit a producer adds the new
 * exchange and then removes the oldest, so a reader that calls size() and then get(index) can find the list shorter
 * than the size it read.
 */
public class ManagedBrowsableEndpointSnapshotTest extends ContextTestSupport {

    private static final int LIMIT = 3;

    private GatedList list;
    private BrowseEndpoint endpoint;

    @BeforeEach
    void createEndpoint() throws Exception {
        endpoint = new BrowseEndpoint("browse:gated", context.getComponent("browse")) {
            @Override
            protected List<Exchange> createExchangeList() {
                list = new GatedList();
                return list;
            }
        };
        endpoint.setName("gated");
        endpoint.setBrowseLimit(LIMIT);
        context.addEndpoint("browse:gated", endpoint);

        send("A", 1);
        send("B", 2);
        send("C", 3);
    }

    @Test
    void testBrowseMessageBodyOfLastWhileOldestIsRemoved() throws Exception {
        ManagedBrowsableEndpoint mbean = new ManagedBrowsableEndpoint(endpoint);

        // the reader sees A, B, C and D and asks for D, and the producer removes A before the reader gets it
        String body = readWhileProducerMakesRoom(1, () -> mbean.browseMessageBody(LIMIT));

        assertEquals("D", body);
    }

    @Test
    void testBrowseStatusWhileOldestIsRemoved() throws Exception {
        // the reader gets the first (A), and the producer removes A before the reader gets the last
        BrowseStatus status = readWhileProducerMakesRoom(2, () -> endpoint.getBrowseStatus(0));

        // size, first and last all come from the same list: A, B, C and D
        assertEquals(new BrowseStatus(LIMIT + 1, 1, 4), status);
    }

    @Test
    void testBrowseRangeWhileOldestIsRemoved() throws Exception {
        ManagedBrowsableEndpoint mbean = new ManagedBrowsableEndpoint(endpoint);

        // the reader sees four exchanges, and the producer removes A before the reader gets the first
        String json = readWhileProducerMakesRoom(1, () -> mbean.browseRangeMessagesAsJSon(0, Integer.MAX_VALUE, true));

        JsonArray arr = (JsonArray) Jsoner.deserialize(json);
        assertEquals(LIMIT + 1, arr.size(), json);
        assertTrue(json.contains("\"A\"") && json.contains("\"D\""), json);
    }

    /**
     * Sends D from another thread and holds that producer after it added D (so the list has one more than the limit)
     * until the reader calls get(index) for the given time; the producer then removes the oldest before that get runs.
     * A reader that reads one snapshot does not call get(index), and the producer completes after the reader.
     */
    private <T> T readWhileProducerMakesRoom(int trimBeforeGet, Callable<T> reader) throws Exception {
        Thread producer = new Thread(() -> send("D", 4), "browse-producer");
        list.trimBeforeGet.set(trimBeforeGet);
        list.producer = producer;
        list.holdAfterAdd = true;
        producer.start();
        assertTrue(list.added.await(20, TimeUnit.SECONDS), "producer did not add");
        assertEquals(LIMIT + 1, list.size());
        try {
            return reader.call();
        } finally {
            list.trim.countDown();
            producer.join(20000);
            assertFalse(producer.isAlive(), "producer did not complete");
            assertEquals(LIMIT, endpoint.getExchanges().size());
        }
    }

    private void send(String body, long timestamp) {
        template.sendBodyAndHeader(endpoint, body, Exchange.MESSAGE_TIMESTAMP, timestamp);
    }

    /**
     * The list of the endpoint, which can hold the producer after an add and let it continue at the n-th get(index).
     */
    private static final class GatedList extends CopyOnWriteArrayList<Exchange> {
        private final CountDownLatch added = new CountDownLatch(1);
        private final CountDownLatch trim = new CountDownLatch(1);
        private final AtomicInteger trimBeforeGet = new AtomicInteger();
        private volatile boolean holdAfterAdd;
        private volatile Thread producer;

        @Override
        public boolean add(Exchange exchange) {
            boolean answer = super.add(exchange);
            if (holdAfterAdd) {
                holdAfterAdd = false;
                added.countDown();
                try {
                    if (!trim.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("producer was not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return answer;
        }

        @Override
        public Exchange get(int index) {
            if (trimBeforeGet.get() > 0 && trimBeforeGet.decrementAndGet() == 0) {
                // let the producer remove the oldest, and wait until it did
                trim.countDown();
                try {
                    producer.join(20000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                if (producer.isAlive()) {
                    throw new IllegalStateException("producer did not complete");
                }
            }
            return super.get(index);
        }
    }
}

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
package org.apache.camel.component.browse;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With browseLimit the endpoint must keep at most that many exchanges in memory, a list handed out to a browser must
 * stay usable when more exchanges arrive, and producers that send at the same time must not break the endpoint.
 */
public class BrowseLimitMemoryTest extends ContextTestSupport {

    private static final int LIMIT = 10;
    private static final int TOTAL = 1000;

    @Test
    public void testOldExchangesAreReleased() throws Exception {
        AtomicReference<List<Exchange>> store = new AtomicReference<>();
        BrowseEndpoint endpoint = new BrowseEndpoint("browse:store", context.getComponent("browse")) {
            @Override
            protected List<Exchange> createExchangeList() {
                // the list that holds the exchanges in memory
                List<Exchange> answer = super.createExchangeList();
                store.set(answer);
                return answer;
            }
        };
        endpoint.setName("store");
        endpoint.setBrowseLimit(LIMIT);
        context.addEndpoint("browse:store", endpoint);

        for (int i = 0; i < TOTAL; i++) {
            template.sendBody(endpoint, "msg-" + i);
        }

        // what a browser sees: the last ten
        List<Exchange> browsed = endpoint.getExchanges();
        assertEquals(LIMIT, browsed.size());
        assertEquals("msg-" + (TOTAL - LIMIT), browsed.get(0).getMessage().getBody());
        assertEquals("msg-" + (TOTAL - 1), browsed.get(LIMIT - 1).getMessage().getBody());

        // what is kept in memory: not more than the limit
        assertEquals(LIMIT, store.get().size(), "exchanges kept in memory with browseLimit=" + LIMIT);
    }

    @Test
    public void testBrowsedListStaysUsableWhenMoreExchangesArrive() throws Exception {
        String uri = "browse:live?browseLimit=2";
        template.sendBody(uri, "one");
        template.sendBody(uri, "two");
        template.sendBody(uri, "three");

        BrowseEndpoint endpoint = context.getEndpoint(uri, BrowseEndpoint.class);
        List<Exchange> browsed = endpoint.getExchanges();
        assertEquals(2, browsed.size());

        // a new exchange arrives while the browser still holds the list
        template.sendBody(uri, "four");

        assertEquals(2, browsed.size());
        assertEquals("three", browsed.get(0).getMessage().getBody());
        assertEquals("four", browsed.get(1).getMessage().getBody());
    }

    @Test
    public void testSecondProducerWhileFirstMakesRoom() throws Exception {
        // producer A is held at the moment it asks the list for a sub list (the step that makes room), and producer B
        // sends meanwhile: B either completes, or waits for A when the endpoint serializes the two
        Queue<String> failures = new ConcurrentLinkedQueue<>();
        AtomicReference<Thread> second = new AtomicReference<>();
        CountDownLatch secondDone = new CountDownLatch(1);
        AtomicReference<Runnable> onSubList = new AtomicReference<>();

        BrowseEndpoint endpoint = hookedEndpoint("pair", 2, null, onSubList);
        Producer producer = endpoint.createProducer();
        producer.process(exchange(endpoint, "one"));
        producer.process(exchange(endpoint, "two"));

        Exchange a = exchange(endpoint, "A");
        Exchange b = exchange(endpoint, "B");
        Exchange c = exchange(endpoint, "C");
        second.set(new Thread(() -> {
            try {
                producer.process(b);
            } catch (Throwable e) {
                failures.add("producer B: " + e);
            } finally {
                secondDone.countDown();
            }
        }, "browse-producer-B"));

        onSubList.set(() -> {
            // only the first sub list is held
            onSubList.set(null);
            Thread t = second.get();
            t.start();
            await().atMost(20, TimeUnit.SECONDS)
                    .until(() -> secondDone.getCount() == 0 || t.getState() == Thread.State.WAITING);
        });
        try {
            producer.process(a);
        } catch (Throwable e) {
            failures.add("producer A: " + e);
        }
        assertTrue(secondDone.await(20, TimeUnit.SECONDS), "producer B did not complete");

        // the endpoint must still accept exchanges and be readable afterwards
        try {
            producer.process(c);
        } catch (Throwable e) {
            failures.add("producer C, after A and B: " + e);
        }
        try {
            assertEquals(2, endpoint.getExchanges().size());
            assertEquals("C", endpoint.getExchanges().get(1).getMessage().getBody());
        } catch (Throwable e) {
            failures.add("browsing, after A, B and C: " + e);
        }
        assertTrue(failures.isEmpty(), "failures: " + failures);
    }

    @Test
    public void testManyProducerThreads() throws Exception {
        int threads = 4;
        int perThread = 250;
        AtomicReference<List<Exchange>> store = new AtomicReference<>();
        BrowseEndpoint endpoint = new BrowseEndpoint("browse:many", context.getComponent("browse")) {
            @Override
            protected List<Exchange> createExchangeList() {
                List<Exchange> answer = super.createExchangeList();
                store.set(answer);
                return answer;
            }
        };
        endpoint.setName("many");
        endpoint.setBrowseLimit(LIMIT);
        context.addEndpoint("browse:many", endpoint);
        Producer producer = endpoint.createProducer();

        Queue<String> failures = new ConcurrentLinkedQueue<>();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> producers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            List<Exchange> mine = new ArrayList<>();
            for (int i = 0; i < perThread; i++) {
                mine.add(exchange(endpoint, t + "-" + i));
            }
            Thread thread = new Thread(() -> {
                try {
                    if (!go.await(20, TimeUnit.SECONDS)) {
                        failures.add("not started");
                        return;
                    }
                    for (Exchange exchange : mine) {
                        producer.process(exchange);
                    }
                } catch (Throwable e) {
                    failures.add(e.toString());
                }
            }, "browse-producer-" + t);
            producers.add(thread);
            thread.start();
        }
        go.countDown();
        for (Thread thread : producers) {
            thread.join(20000);
            assertFalse(thread.isAlive(), thread.getName() + " did not complete");
        }

        assertTrue(failures.isEmpty(), "failures: " + failures);
        assertEquals(LIMIT, endpoint.getExchanges().size());
        assertEquals(LIMIT, store.get().size(), "exchanges kept in memory with browseLimit=" + LIMIT);
    }

    @Test
    public void testReaderDoesNotSeeTheEndpointEmptyWithLimitOne() throws Exception {
        // a reader that looks while a producer is inside the list (before the add, and before the oldest is removed)
        List<Integer> seen = new ArrayList<>();
        AtomicReference<Runnable> look = new AtomicReference<>();
        BrowseEndpoint endpoint = hookedEndpoint("single", 1, look, look);
        Producer producer = endpoint.createProducer();
        producer.process(exchange(endpoint, "one"));

        look.set(() -> seen.add(endpoint.getBrowseStatus(0).size()));
        producer.process(exchange(endpoint, "two"));
        producer.process(exchange(endpoint, "three"));
        look.set(null);

        assertFalse(seen.isEmpty());
        assertFalse(seen.contains(0), "sizes seen by a reader while exchanges arrive: " + seen);
        assertEquals(1, endpoint.getExchanges().size());
        assertEquals("three", endpoint.getExchanges().get(0).getMessage().getBody());
    }

    private static Exchange exchange(BrowseEndpoint endpoint, String body) {
        Exchange answer = endpoint.createExchange();
        answer.getMessage().setBody(body);
        return answer;
    }

    private BrowseEndpoint hookedEndpoint(
            String name, int limit, AtomicReference<Runnable> beforeAdd, AtomicReference<Runnable> afterSubList)
            throws Exception {
        BrowseEndpoint endpoint = new BrowseEndpoint("browse:" + name, context.getComponent("browse")) {
            @Override
            protected List<Exchange> createExchangeList() {
                return new HookedList(super.createExchangeList(), beforeAdd, afterSubList);
            }
        };
        endpoint.setName(name);
        endpoint.setBrowseLimit(limit);
        context.addEndpoint("browse:" + name, endpoint);
        return endpoint;
    }

    /**
     * Delegates to the list of the endpoint (and to its sub lists) and lets a test run code just before an add and just
     * after a sub list was created. It does not change what the list does.
     */
    private static final class HookedList extends AbstractList<Exchange> {
        private final List<Exchange> delegate;
        private final AtomicReference<Runnable> beforeAdd;
        private final AtomicReference<Runnable> afterSubList;

        HookedList(List<Exchange> delegate, AtomicReference<Runnable> beforeAdd, AtomicReference<Runnable> afterSubList) {
            this.delegate = delegate;
            this.beforeAdd = beforeAdd;
            this.afterSubList = afterSubList;
        }

        private static void run(AtomicReference<Runnable> hook) {
            Runnable task = hook != null ? hook.get() : null;
            if (task != null) {
                task.run();
            }
        }

        @Override
        public Exchange get(int index) {
            return delegate.get(index);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean add(Exchange exchange) {
            run(beforeAdd);
            return delegate.add(exchange);
        }

        @Override
        public Exchange remove(int index) {
            return delegate.remove(index);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public List<Exchange> subList(int fromIndex, int toIndex) {
            List<Exchange> answer = new HookedList(delegate.subList(fromIndex, toIndex), beforeAdd, afterSubList);
            run(afterSubList);
            return answer;
        }
    }
}

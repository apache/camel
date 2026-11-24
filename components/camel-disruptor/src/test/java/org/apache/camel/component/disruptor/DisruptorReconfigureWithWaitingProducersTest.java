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
package org.apache.camel.component.disruptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Producers that wait for a free slot on the full ring buffer of a disruptor while a consumer is added (which replaces
 * the ring buffer) publish on the old ring buffer. Their exchanges must be processed (and not left on the old ring
 * buffer after its event handlers were halted), and the reconfiguration must not wait for them forever.
 */
class DisruptorReconfigureWithWaitingProducersTest extends CamelTestSupport {

    // a ring buffer of one slot: each exchange the slow consumer finishes empties it while producers wait for the slot
    private static final String SLOW = "disruptor:slow?multipleConsumers=true&size=1";
    private static final int SLOW_WAITING = 8;
    private static final int NO_CONSUMER_SIZE = 4;
    private static final String NO_CONSUMER = "disruptor:idle?size=" + NO_CONSUMER_SIZE;
    private static final String SMALL = "disruptor:small?multipleConsumers=true&size=2";

    private final CountDownLatch releaseSlowConsumer = new CountDownLatch(1);
    private final CountDownLatch releaseFirstConsumer = new CountDownLatch(1);
    private final AtomicBoolean firstExchange = new AtomicBoolean(true);
    private final List<Thread> producers = new ArrayList<>();
    private final List<AtomicReference<Throwable>> failures = new ArrayList<>();
    // thrown, once, when the CamelContext is asked for the thread that publishes the rest of a backlog
    private final AtomicReference<Error> backlogThreadError = new AtomicReference<>();

    @AfterEach
    void release() {
        releaseSlowConsumer.countDown();
        releaseFirstConsumer.countDown();
        // a producer that waits forever for its reply (on a failure) must not keep the test running
        producers.forEach(Thread::interrupt);
    }

    @Test
    void producersWaitingOnTheFullRingBufferWhileAConsumerIsAddedAreNotLost() throws Exception {
        MockEndpoint slow = getMockEndpoint("mock:slow");
        MockEndpoint added = getMockEndpoint("mock:added");
        List<String> bodies = fillAndWait(SLOW, 1, SLOW_WAITING, false);
        bodies.add("after");
        slow.expectedBodiesReceivedInAnyOrder(bodies);
        added.expectedBodiesReceived("after");

        // the consumer of the slow route is busy and the ring buffer is full: a second consumer is added, which waits
        // for the old ring buffer to be drained
        Thread reconfiguration = addRouteInBackground(SLOW, "mock:added");
        DisruptorReference reference = context.getEndpoint(SLOW, DisruptorEndpoint.class).getDisruptor();
        await().atMost(20, TimeUnit.SECONDS).until(reference::hasNullReference);

        // the slow consumer frees the slots of the old ring buffer, which the waiting producers take
        releaseSlowConsumer.countDown();

        awaitEnded(reconfiguration);
        producers.forEach(this::awaitEnded);
        template.sendBody(SLOW, "after");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @Test
    void requestReplyProducersWaitingOnTheFullRingBufferWhileAConsumerIsAddedGetTheirReply() throws Exception {
        List<String> bodies = fillAndWait(SLOW, 1, SLOW_WAITING, true);
        getMockEndpoint("mock:slow").expectedBodiesReceivedInAnyOrder(bodies);

        Thread reconfiguration = addRouteInBackground(SLOW, "mock:added");
        DisruptorReference reference = context.getEndpoint(SLOW, DisruptorEndpoint.class).getDisruptor();
        await().atMost(20, TimeUnit.SECONDS).until(reference::hasNullReference);
        releaseSlowConsumer.countDown();

        // the request/reply producers wait without timeout: each must get the reply of the slow route
        awaitEnded(reconfiguration);
        producers.forEach(this::awaitEnded);
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @Test
    void producersWaitingOnTheFullRingBufferOfAQueueWithoutConsumerAreNotLostWhenTheFirstConsumerStarts()
            throws Exception {
        MockEndpoint first = getMockEndpoint("mock:first");
        List<String> bodies = fillAndWait(NO_CONSUMER, NO_CONSUMER_SIZE, NO_CONSUMER_SIZE, false);
        // the exchanges sent before the consumer started, the ones that waited for a free slot after them
        first.expectedBodiesReceivedInAnyOrder(bodies);

        // the queue has no consumer, so nothing frees a slot until the consumer starts: the reconfiguration must not
        // wait for the producers that wait for a slot on the old ring buffer forever
        Thread reconfiguration = addRouteInBackground(NO_CONSUMER, "mock:first");
        awaitEnded(reconfiguration);
        producers.forEach(this::awaitEnded);

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        // the backlog is delivered first, in the order it was sent
        for (int i = 0; i < NO_CONSUMER_SIZE; i++) {
            assertEquals("backlog-" + i, first.getReceivedExchanges().get(i).getMessage().getBody());
        }
    }

    @Test
    void consumerThatTakesTheLockOfItsQueueWhileTheRestOfTheBacklogIsPublishedDoesNotBlockItsStart() throws Exception {
        MockEndpoint first = getMockEndpoint("mock:first");
        // more exchanges than the ring buffer holds wait for the first consumer
        List<String> bodies = fillAndWait(SMALL, 2, 2, false);
        first.expectedBodiesReceivedInAnyOrder(bodies);

        // on its first exchange, the consumer creates an endpoint of its own queue with other options (as a toD would),
        // which takes the lock of the disruptor: the start of its route must not wait for this consumer with the lock
        // held, which would block both for good
        Thread reconfiguration = addRouteInBackground(new RouteBuilder() {
            @Override
            public void configure() {
                from(SMALL).routeId("added").process(e -> {
                    if (firstExchange.getAndSet(false)) {
                        e.getContext().getEndpoint(SMALL + "&timeout=4321");
                    }
                }).to("mock:first");
            }
        });
        awaitEnded(reconfiguration);
        producers.forEach(this::awaitEnded);

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        // the backlog that fits the ring buffer is delivered first, in the order it was sent
        assertEquals("backlog-0", first.getReceivedExchanges().get(0).getMessage().getBody());
        assertEquals("backlog-1", first.getReceivedExchanges().get(1).getMessage().getBody());
    }

    @Test
    void consumerAddedWhileTheRestOfTheBacklogIsPublishedGetsTheRestOnce() throws Exception {
        MockEndpoint first = getMockEndpoint("mock:first");
        MockEndpoint second = getMockEndpoint("mock:second");
        List<String> bodies = fillAndWait(SMALL, 2, 4, false);
        first.expectedBodiesReceivedInAnyOrder(bodies);

        // the first consumer holds the first exchange: the ring buffer stays full with the backlog, and the thread that
        // publishes the rest of it waits for a free slot
        Thread reconfiguration = addRouteInBackground(new RouteBuilder() {
            @Override
            public void configure() {
                from(SMALL).routeId("first").process(e -> releaseFirstConsumer.await(20, TimeUnit.SECONDS))
                        .to("mock:first");
            }
        });
        awaitEnded(reconfiguration);
        await().atMost(20, TimeUnit.SECONDS).until(() -> Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.getName().contains("DisruptorBacklog") && isWaitingForFreeSlot(t)));

        // a second consumer is added meanwhile: the reconfiguration waits for that thread, which publishes on the old
        // ring buffer once the first consumer goes on, and then the rest on the new one
        Thread secondReconfiguration = addRouteInBackground(new RouteBuilder() {
            @Override
            public void configure() {
                from(SMALL).routeId("second").to("mock:second");
            }
        });
        DisruptorReference reference = context.getEndpoint(SMALL, DisruptorEndpoint.class).getDisruptor();
        await().atMost(20, TimeUnit.SECONDS).until(reference::hasNullReference);
        releaseFirstConsumer.countDown();
        awaitEnded(secondReconfiguration);
        producers.forEach(this::awaitEnded);

        // each exchange once, in the order of the buffer: the backlog and one more on the old ring buffer, the 3 others
        // on the new one, which the second consumer gets too
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        List<Object> received = first.getReceivedExchanges().stream().map(e -> e.getMessage().getBody()).toList();
        assertEquals(List.of("backlog-0", "backlog-1"), received.subList(0, 2));
        await().atMost(20, TimeUnit.SECONDS).until(() -> second.getReceivedCounter() >= 3);
        assertEquals(received.subList(3, 6),
                second.getReceivedExchanges().stream().map(e -> e.getMessage().getBody()).toList());
    }

    @Test
    void restOfTheBacklogStaysBufferedWhenItsThreadCannotBeStarted() throws Exception {
        MockEndpoint first = getMockEndpoint("mock:first");
        List<String> bodies = fillAndWait(SMALL, 2, 4, false);
        first.expectedBodiesReceivedInAnyOrder(bodies);

        // the first consumer route is added, but the thread that would publish what does not fit the ring buffer
        // cannot be started (as when no native thread can be created): the route starts, the consumer gets what fits
        // the ring buffer, and the rest stays buffered
        backlogThreadError.set(new CannotStartThreadError());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(SMALL).routeId("first").to("mock:first");
            }
        });
        producers.forEach(this::awaitEnded);
        await().atMost(20, TimeUnit.SECONDS).until(() -> first.getReceivedCounter() == 2);
        assertNull(backlogThreadError.get());

        // the next reconfiguration publishes what fits its ring buffer and starts the thread for the rest (if the
        // failure had left the thread marked as running, no thread would publish the last 2)
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(SMALL).routeId("second").to("mock:second");
            }
        });
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        List<Object> received = first.getReceivedExchanges().stream().map(e -> e.getMessage().getBody()).toList();
        assertEquals(List.of("backlog-0", "backlog-1"), received.subList(0, 2));
    }

    /**
     * Fills the ring buffer of the endpoint (of the given size), then starts the given number of producers, and waits
     * until they all wait for a free slot. Returns the bodies of all the exchanges sent.
     */
    private List<String> fillAndWait(String uri, int size, int waitingProducers, boolean requestReply) {
        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String body = "backlog-" + i;
            if (requestReply) {
                producers.add(startProducer("backlog-producer-" + i, uri + "&timeout=0", body, true));
            } else {
                template.sendBody(uri, body);
            }
            bodies.add(body);
        }
        if (requestReply) {
            // the slow consumer holds the first exchange, the others fill the ring buffer
            await().atMost(20, TimeUnit.SECONDS)
                    .until(() -> getMockEndpoint("mock:slow").getReceivedCounter() == 0 && remainingCapacity(uri) == 0);
        }
        List<Thread> waiting = new ArrayList<>();
        for (int i = 0; i < waitingProducers; i++) {
            String body = "waiting-" + i;
            Thread producer = startProducer("waiting-producer-" + i, uri + (requestReply ? "&timeout=0" : ""), body,
                    requestReply);
            waiting.add(producer);
            producers.add(producer);
            bodies.add(body);
        }
        await().atMost(20, TimeUnit.SECONDS).until(() -> waiting.stream().allMatch(this::isWaitingForFreeSlot));
        return bodies;
    }

    private long remainingCapacity(String uri) throws Exception {
        return context.getEndpoint(uri, DisruptorEndpoint.class).getDisruptor().getRemainingCapacity();
    }

    private Thread startProducer(String name, String uri, String body, boolean requestReply) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                if (requestReply) {
                    Object reply = template.requestBody(uri, body);
                    if (!body.equals(reply)) {
                        failure.set(new AssertionError("Unexpected reply " + reply + " to " + body));
                    }
                } else {
                    template.sendBody(uri, body);
                }
            } catch (Exception e) {
                failure.set(e);
            }
        }, name);
        producer.setDaemon(true);
        producer.start();
        failures.add(failure);
        return producer;
    }

    private boolean isWaitingForFreeSlot(Thread thread) {
        for (StackTraceElement element : thread.getStackTrace()) {
            // MultiProducerSequencer.next() parks until a slot is free
            if ("next".equals(element.getMethodName()) && element.getClassName().startsWith("com.lmax.disruptor.")
                    && element.getClassName().endsWith("Sequencer")) {
                return true;
            }
        }
        return false;
    }

    private Thread addRouteInBackground(String uri, String to) {
        return addRouteInBackground(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("added").to(to);
            }
        });
    }

    private Thread addRouteInBackground(RouteBuilder routes) {
        Thread reconfiguration = new Thread(() -> {
            try {
                context.addRoutes(routes);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, "add-consumer");
        reconfiguration.setDaemon(true);
        reconfiguration.start();
        return reconfiguration;
    }

    private void awaitEnded(Thread thread) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> !thread.isAlive());
        assertFalse(thread.isAlive(), thread.getName() + " is still running");
        for (AtomicReference<Throwable> failure : failures) {
            assertNull(failure.get());
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setExecutorServiceManager(new DefaultExecutorServiceManager(camelContext) {
            @Override
            public Thread newThread(String name, Runnable runnable) {
                if (name.startsWith("DisruptorBacklog")) {
                    Error error = backlogThreadError.getAndSet(null);
                    if (error != null) {
                        throw error;
                    }
                }
                return super.newThread(name, runnable);
            }
        });
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(SLOW).routeId("slow")
                        .process(e -> releaseSlowConsumer.await(20, TimeUnit.SECONDS))
                        .to("mock:slow");
            }
        };
    }

    /**
     * An error that the thread that publishes the rest of a backlog cannot be started with.
     */
    private static final class CannotStartThreadError extends Error {
        private static final long serialVersionUID = 1L;

        CannotStartThreadError() {
            super("cannot start the thread");
        }
    }
}

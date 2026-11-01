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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.disruptor.vm.DisruptorVmComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CamelContexts of a JVM that use the same disruptor-vm queue share one ring buffer, also when they create or stop
 * their endpoints of the queue at the same time.
 * <p/>
 * In this package rather than in the vm package, to compare the disruptors of the endpoints
 * ({@link DisruptorEndpoint#getDisruptor()} is package-private).
 */
class DisruptorVmSharedQueueReferenceTest {

    private final List<CamelContext> contexts = new ArrayList<>();

    @AfterEach
    void stopContexts() {
        for (CamelContext context : contexts) {
            context.stop();
        }
    }

    @Test
    void queueOfAnEndpointCreatedBeforeItsContextStartsIsKeptWhenTheOtherContextStops() throws Exception {
        String uri = "disruptor-vm:keptQueue";
        CamelContext first = newContext(new DisruptorVmComponent());
        first.addRoutes(producerRoute("direct:first", uri));
        first.start();

        CamelContext second = newContext(new DisruptorVmComponent());
        second.addRoutes(consumerRoute(uri));
        // the second CamelContext resolves the endpoint of the queue before it is started, as when it is starting
        DisruptorEndpoint secondEndpoint = second.getEndpoint(uri, DisruptorEndpoint.class);

        // the only started endpoint of the queue goes away, then another CamelContext uses the queue
        first.stop();
        CamelContext third = newContext(new DisruptorVmComponent());
        third.addRoutes(producerRoute("direct:third", uri));
        third.start();
        second.start();

        DisruptorEndpoint thirdEndpoint = third.getEndpoint(uri, DisruptorEndpoint.class);
        assertSame(secondEndpoint.getDisruptor(), thirdEndpoint.getDisruptor(),
                "the producer and the consumer of the queue must use the same disruptor");

        MockEndpoint mock = second.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("hello");
        third.createProducerTemplate().sendBody("direct:third", "hello");
        mock.setResultWaitTime(5000);
        mock.assertIsSatisfied();
    }

    @Test
    void queueOfAnEndpointBeingCreatedIsKeptWhenTheOtherContextStops() throws Exception {
        String uri = "disruptor-vm:creatingQueue";
        CamelContext first = newContext(new DisruptorVmComponent());
        first.addRoutes(consumerRoute(uri));
        first.start();

        // the second CamelContext creates its endpoint of the queue, and pauses before the endpoint is initialized
        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CamelContext second = newContext(new DisruptorVmComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                    throws Exception {
                Endpoint answer = super.createEndpoint(uri, remaining, parameters);
                created.countDown();
                assertTrue(resume.await(5, TimeUnit.SECONDS), "the test must resume the creation");
                return answer;
            }
        });
        second.addRoutes(producerRoute("direct:second", uri));
        EndpointLookup secondLookup = new EndpointLookup(second, uri);
        secondLookup.start();
        assertTrue(created.await(5, TimeUnit.SECONDS), "the second CamelContext must create its endpoint");

        // meanwhile the CamelContext of the only endpoint of the queue stops, and another one starts consuming from it
        first.stop();
        resume.countDown();
        DisruptorEndpoint secondEndpoint = secondLookup.endpoint();
        CamelContext third = newContext(new DisruptorVmComponent());
        third.addRoutes(consumerRoute(uri));
        third.start();
        second.start();

        DisruptorEndpoint thirdEndpoint = third.getEndpoint(uri, DisruptorEndpoint.class);
        assertSame(secondEndpoint.getDisruptor(), thirdEndpoint.getDisruptor(),
                "the producer and the consumer of the queue must use the same disruptor");

        MockEndpoint mock = third.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("hello");
        second.createProducerTemplate().sendBody("direct:second", "hello");
        mock.setResultWaitTime(5000);
        mock.assertIsSatisfied();
    }

    @Test
    void endpointBeingCreatedUsesTheQueueThatAnotherContextCreatedMeanwhile() throws Exception {
        String uri = "disruptor-vm:recreatedQueue";
        CamelContext first = newContext(new DisruptorVmComponent());
        first.addRoutes(consumerRoute(uri));
        first.start();

        // the second CamelContext creates its endpoint of the queue, and pauses before the endpoint is initialized
        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CamelContext second = newContext(new DisruptorVmComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                    throws Exception {
                Endpoint answer = super.createEndpoint(uri, remaining, parameters);
                created.countDown();
                assertTrue(resume.await(5, TimeUnit.SECONDS), "the test must resume the creation");
                return answer;
            }
        });
        second.addRoutes(producerRoute("direct:second", uri));
        EndpointLookup secondLookup = new EndpointLookup(second, uri);
        secondLookup.start();
        assertTrue(created.await(5, TimeUnit.SECONDS), "the second CamelContext must create its endpoint");

        // meanwhile the CamelContext of the only endpoint of the queue stops, and another one creates the queue again
        first.stop();
        CamelContext third = newContext(new DisruptorVmComponent());
        third.addRoutes(consumerRoute(uri));
        third.start();
        resume.countDown();
        DisruptorEndpoint secondEndpoint = secondLookup.endpoint();
        second.start();

        DisruptorEndpoint thirdEndpoint = third.getEndpoint(uri, DisruptorEndpoint.class);
        assertSame(thirdEndpoint.getDisruptor(), secondEndpoint.getDisruptor(),
                "the producer and the consumer of the queue must use the same disruptor");

        MockEndpoint mock = third.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("hello");
        mock.setResultWaitTime(5000);
        second.createProducerTemplate().sendBody("direct:second", "hello");
        mock.assertIsSatisfied();
    }

    @Test
    void endpointsCreatedConcurrentlyInTwoContextsShareTheQueue() throws Exception {
        String uri = "disruptor-vm:concurrentQueue";
        CountDownLatch firstLookedUp = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();

        // the first component pauses after it found no queue in the map, until the second CamelContext has got its
        // endpoint, or until it waits for a lock (that the first one holds)
        AtomicBoolean paused = new AtomicBoolean();
        CamelContext first = newContext(new HookedVmComponent(() -> {
            if (paused.compareAndSet(false, true)) {
                firstLookedUp.countDown();
                awaitDoneOrParked(secondThread);
            }
        }));
        first.addRoutes(producerRoute("direct:first", uri));
        CamelContext second = newContext(new DisruptorVmComponent());
        second.addRoutes(consumerRoute(uri));

        EndpointLookup firstLookup = new EndpointLookup(first, uri);
        firstLookup.start();
        assertTrue(firstLookedUp.await(5, TimeUnit.SECONDS), "the first CamelContext must look up the queue");
        EndpointLookup secondLookup = new EndpointLookup(second, uri);
        secondThread.set(secondLookup);
        secondLookup.start();

        assertSame(firstLookup.endpoint().getDisruptor(), secondLookup.endpoint().getDisruptor(),
                "the producer and the consumer of the queue must use the same disruptor");

        first.start();
        second.start();
        MockEndpoint mock = second.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("hello");
        mock.setResultWaitTime(5000);
        first.createProducerTemplate().sendBody("direct:first", "hello");
        mock.assertIsSatisfied();
    }

    @Test
    void queueOfAnEndpointThatIsStartedAgainIsKeptWhenTheOtherContextStops() throws Exception {
        String uri = "disruptor-vm:restartedQueue";
        CamelContext first = newContext(new DisruptorVmComponent());
        first.addRoutes(consumerRoute(uri, "consumer"));
        first.addRoutes(producerRoute("direct:first", uri));
        first.start();
        CamelContext second = newContext(new DisruptorVmComponent());
        second.addRoutes(producerRoute("direct:second", uri));
        second.start();

        // the endpoint of the first CamelContext is shut down with its consumer route (its producer route still uses
        // it) and started again when the route is added again
        DisruptorEndpoint firstEndpoint = first.getEndpoint(uri, DisruptorEndpoint.class);
        first.getRouteController().stopRoute("consumer");
        first.removeRoute("consumer");
        first.addRoutes(consumerRoute(uri, "consumer"));
        assertSame(firstEndpoint, first.getEndpoint(uri, DisruptorEndpoint.class));

        second.stop();
        CamelContext third = newContext(new DisruptorVmComponent());
        third.addRoutes(producerRoute("direct:third", uri));
        third.start();

        DisruptorEndpoint thirdEndpoint = third.getEndpoint(uri, DisruptorEndpoint.class);
        assertSame(firstEndpoint.getDisruptor(), thirdEndpoint.getDisruptor(),
                "the producer and the consumer of the queue must use the same disruptor");

        MockEndpoint mock = first.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("hello");
        mock.setResultWaitTime(5000);
        third.createProducerTemplate().sendBody("direct:third", "hello");
        mock.assertIsSatisfied();
    }

    @Test
    void queueIsDroppedWhenItsEndpointsAreRemovedAndTheContextsStop() throws Exception {
        String uri = "disruptor-vm:droppedQueue";
        CamelContext first = newContext(new DisruptorVmComponent());
        first.addRoutes(producerRoute("direct:first", uri));
        first.start();
        // removing the route stops its endpoint, which is not shut down
        first.getRouteController().stopRoute("producer-direct:first");
        first.removeRoute("producer-direct:first");
        first.stop();

        // a producer CamelContext that starts before the consumer CamelContext uses a new queue, which keeps the message
        CamelContext second = newContext(new DisruptorVmComponent());
        second.addRoutes(producerRoute("direct:second", uri + "?waitForTaskToComplete=Never"));
        second.start();
        second.createProducerTemplate().sendBody("direct:second", "hello");
        CamelContext third = newContext(new DisruptorVmComponent());
        third.addRoutes(consumerRoute(uri));
        third.start();

        MockEndpoint mock = third.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("hello");
        mock.setResultWaitTime(5000);
        mock.assertIsSatisfied();
    }

    private CamelContext newContext(DisruptorVmComponent component) {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("disruptor-vm", component);
        contexts.add(context);
        return context;
    }

    private static RouteBuilder producerRoute(String from, String uri) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("producer-" + from).to(uri);
            }
        };
    }

    private static RouteBuilder consumerRoute(String uri) {
        return consumerRoute(uri, "consumer-" + uri);
    }

    private static RouteBuilder consumerRoute(String uri, String routeId) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId(routeId).to("mock:result");
            }
        };
    }

    /**
     * Waits until the given thread has ended, or until it waits for a lock (the caller holds it).
     */
    private static void awaitDoneOrParked(AtomicReference<Thread> thread) {
        await().atMost(10, TimeUnit.SECONDS).pollInterval(5, TimeUnit.MILLISECONDS).until(() -> {
            Thread t = thread.get();
            return t != null && (t.getState() == Thread.State.TERMINATED
                    || LockSupport.getBlocker(t) instanceof AbstractQueuedSynchronizer);
        });
    }

    /**
     * Gets an endpoint in a thread of its own.
     */
    private static final class EndpointLookup extends Thread {
        private final CamelContext context;
        private final String uri;
        private final AtomicReference<DisruptorEndpoint> endpoint = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        EndpointLookup(CamelContext context, String uri) {
            this.context = context;
            this.uri = uri;
        }

        @Override
        public void run() {
            try {
                endpoint.set(context.getEndpoint(uri, DisruptorEndpoint.class));
            } catch (Throwable e) {
                failure.set(e);
            }
        }

        DisruptorEndpoint endpoint() throws InterruptedException {
            join(10000);
            assertFalse(isAlive(), "the endpoint of " + uri + " must be created");
            assertNull(failure.get(), () -> "the endpoint of " + uri + " cannot be created: " + failure.get());
            return endpoint.get();
        }
    }

    /**
     * A disruptor-vm component whose view of the shared queue map runs the given hook after a lookup that finds no
     * queue (getOrCreateDisruptor looks up the key, then puts the queue it created).
     */
    private static final class HookedVmComponent extends DisruptorVmComponent {
        private final Runnable afterMissingGet;

        HookedVmComponent(Runnable afterMissingGet) {
            this.afterMissingGet = afterMissingGet;
        }

        @Override
        public Map<String, DisruptorReference> getDisruptors() {
            Map<String, DisruptorReference> shared = super.getDisruptors();
            return new AbstractMap<>() {
                @Override
                public Set<Entry<String, DisruptorReference>> entrySet() {
                    return shared.entrySet();
                }

                @Override
                public DisruptorReference get(Object key) {
                    DisruptorReference answer = shared.get(key);
                    if (answer == null) {
                        afterMissingGet.run();
                    }
                    return answer;
                }

                @Override
                public DisruptorReference put(String key, DisruptorReference value) {
                    return shared.put(key, value);
                }

                @Override
                public DisruptorReference putIfAbsent(String key, DisruptorReference value) {
                    return shared.putIfAbsent(key, value);
                }
            };
        }
    }
}

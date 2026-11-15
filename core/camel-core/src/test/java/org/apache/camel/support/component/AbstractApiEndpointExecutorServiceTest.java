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
package org.apache.camel.support.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.component.AbstractApiProducerTest.TestApiComponent;
import org.apache.camel.support.component.AbstractApiProducerTest.TestApiEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The thread pool of an API endpoint belongs to the CamelContext of the endpoint: stopping another CamelContext does
 * not shut down the pool that the producers of this CamelContext use, and an endpoint that is used again after its
 * CamelContext was started again gets a new pool.
 */
class AbstractApiEndpointExecutorServiceTest {

    private CamelContext context1;
    private CamelContext context2;

    @AfterEach
    void stopContexts() {
        if (context1 != null) {
            context1.stop();
        }
        if (context2 != null) {
            context2.stop();
        }
    }

    @Test
    void testStoppingAnotherCamelContextDoesNotStopThePool() throws Exception {
        context1 = new DefaultCamelContext();
        context1.start();
        context2 = new DefaultCamelContext();
        context2.start();

        TestApiEndpoint endpoint1 = createEndpoint(context1);
        TestApiEndpoint endpoint2 = createEndpoint(context2);

        // both CamelContexts call the API
        assertGreeting(context1, endpoint1);
        assertGreeting(context2, endpoint2);

        context1.stop();

        // the second CamelContext is still running, so its producer must still work
        assertGreeting(context2, endpoint2);
    }

    @Test
    void testStoppingAnotherCamelContextDoesNotInterruptItsCalls() throws Exception {
        context1 = new DefaultCamelContext();
        // do not wait long for the tasks still running in the pools when the CamelContext stops
        context1.getExecutorServiceManager().setShutdownAwaitTermination(100);
        context1.start();
        context2 = new DefaultCamelContext();
        context2.start();

        BlockingApiEndpoint endpoint1 = new BlockingApiEndpoint(new TestApiComponent(context1));
        BlockingApiEndpoint endpoint2 = new BlockingApiEndpoint(new TestApiComponent(context2));
        assertGreeting(context1, endpoint1);

        // a call of the second CamelContext is running when the first CamelContext stops
        endpoint2.block = new CountDownLatch(1);
        try (ProducerTemplate template = context2.createProducerTemplate()) {
            CompletableFuture<Exchange> future = template.asyncSend(endpoint2, e -> e.getMessage().setBody("World"));
            assertTrue(endpoint2.running.await(10, TimeUnit.SECONDS), "The call should be running");

            try {
                context1.stop();
            } finally {
                endpoint2.block.countDown();
            }
            assertCompleted(future);
        }
    }

    @Test
    void testEndpointKeptAcrossARestartCanStillCallTheApi() throws Exception {
        context1 = new DefaultCamelContext();
        // a lost call stays in flight, do not wait long for it when stopping
        context1.getShutdownStrategy().setTimeout(1);
        TestApiEndpoint endpoint = createEndpoint(context1);
        // the route uses the endpoint object, which it keeps when the CamelContext is started again
        context1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(endpoint);
            }
        });
        context1.start();
        assertRouteGreeting(context1);

        // the pool of the endpoint is shut down when the CamelContext stops
        context1.stop();
        context1.start();

        assertRouteGreeting(context1);
    }

    @Test
    void testOnePoolPerCamelContextWhenFirstCallsRace() throws Exception {
        context1 = new DefaultCamelContext();
        context1.start();
        context2 = new DefaultCamelContext();
        context2.start();

        List<TestApiEndpoint> endpoints = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            endpoints.add(createEndpoint(context1));
            endpoints.add(createEndpoint(context2));
        }

        // the endpoints of both CamelContexts look up their pool at the same time
        ExecutorService threads = Executors.newFixedThreadPool(endpoints.size());
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<ExecutorService>> pools = new ArrayList<>();
            for (TestApiEndpoint endpoint : endpoints) {
                pools.add(threads.submit(() -> {
                    go.await();
                    return endpoint.getExecutorService();
                }));
            }
            go.countDown();

            Set<ExecutorService> pools1 = new HashSet<>();
            Set<ExecutorService> pools2 = new HashSet<>();
            for (int i = 0; i < endpoints.size(); i++) {
                ExecutorService pool = pools.get(i).get(10, TimeUnit.SECONDS);
                if (endpoints.get(i).getCamelContext() == context1) {
                    pools1.add(pool);
                } else {
                    pools2.add(pool);
                }
            }
            assertEquals(1, pools1.size(), "The endpoints of a CamelContext should share one pool");
            assertEquals(1, pools2.size(), "The endpoints of a CamelContext should share one pool");
            assertNotSame(pools1.iterator().next(), pools2.iterator().next(),
                    "Each CamelContext should have its own pool");
        } finally {
            threads.shutdownNow();
            threads.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static TestApiEndpoint createEndpoint(CamelContext context) throws Exception {
        TestApiComponent component = new TestApiComponent(context);
        TestApiEndpoint endpoint = new TestApiEndpoint("test-api:greetMe", component, "greetMe");
        endpoint.setInBody("name");
        endpoint.configureProperties(new HashMap<>());
        return endpoint;
    }

    private static void assertGreeting(CamelContext context, TestApiEndpoint endpoint) throws Exception {
        try (ProducerTemplate template = context.createProducerTemplate()) {
            // a call that is lost never completes, so do not wait for good
            assertCompleted(template.asyncSend(endpoint, e -> e.getMessage().setBody("World")));
        }
    }

    private static void assertRouteGreeting(CamelContext context) throws Exception {
        try (ProducerTemplate template = context.createProducerTemplate()) {
            assertCompleted(template.asyncSend("direct:start", e -> e.getMessage().setBody("World")));
        }
    }

    private static void assertCompleted(CompletableFuture<Exchange> future) throws Exception {
        Exchange out;
        try {
            out = future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("The call did not complete", e);
        }
        assertNull(out.getException(), "Should not fail: " + out.getException());
        assertEquals("Greetings World", out.getMessage().getBody());
    }

    /**
     * An API endpoint whose calls wait until they are released.
     */
    static class BlockingApiEndpoint extends TestApiEndpoint {

        final CountDownLatch running = new CountDownLatch(1);
        volatile CountDownLatch block;

        BlockingApiEndpoint(Component component) throws Exception {
            super("test-api:greetMe", component, "greetMe");
            setInBody("name");
            configureProperties(new HashMap<>());
        }

        @Override
        public Producer createProducer() {
            return new AbstractApiProducer<>(this, getPropertiesHelper()) {
                @Override
                protected Object doInvokeMethod(ApiMethod method, Map<String, Object> properties) {
                    CountDownLatch latch = block;
                    if (latch != null) {
                        running.countDown();
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeCamelException("The call was interrupted", e);
                        }
                    }
                    return super.doInvokeMethod(method, properties);
                }
            };
        }
    }
}

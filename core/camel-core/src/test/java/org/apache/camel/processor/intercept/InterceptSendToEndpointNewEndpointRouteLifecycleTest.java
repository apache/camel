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
package org.apache.camel.processor.intercept;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultEndpointRegistry;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.util.function.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An endpoint that is created (such as by a toD) while a route with interceptSendToEndpoint starts or stops must get
 * the interceptors of the routes that are running when it is done.
 * <p/>
 * The test pauses the thread that creates the endpoint after the endpoint strategies ran, and before the endpoint is
 * added to the endpoint registry, and starts or stops the route meanwhile.
 */
public class InterceptSendToEndpointNewEndpointRouteLifecycleTest extends ContextTestSupport {

    // the uri of the endpoint to pause before it is put in the registry (once)
    private final AtomicReference<String> pauseUri = new AtomicReference<>();
    private final CountDownLatch arrived = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = new DefaultCamelContext(createCamelRegistry()) {
            // the pause is in the registry, as that put is the exact point after all the endpoint strategies ran
            // (another endpoint strategy would not do, as the strategies are not kept in order)
            @Override
            protected EndpointRegistry createEndpointRegistry(Map<NormalizedEndpointUri, Endpoint> endpoints) {
                return new DefaultEndpointRegistry(getCamelContextReference(), endpoints) {
                    @Override
                    public Endpoint put(NormalizedEndpointUri key, Endpoint endpoint) {
                        String uri = pauseUri.get();
                        if (uri != null && uri.equals(key.getUri()) && pauseUri.compareAndSet(uri, null)) {
                            // the endpoint strategies have run, the endpoint is not in the registry yet
                            arrived.countDown();
                            try {
                                release.await(20, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        return super.put(key, endpoint);
                    }
                };
            }
        };
        answer.setLoadTypeConverters(isLoadTypeConverters());
        return answer;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        release.countDown();
        executor.shutdownNow();
        executor.awaitTermination(20, TimeUnit.SECONDS);
        super.tearDown();
    }

    private void addRoutes(boolean skip) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (skip) {
                    interceptSendToEndpoint("mock:dyn*").skipSendToOriginalEndpoint().to("mock:intercepted");
                } else {
                    interceptSendToEndpoint("mock:dyn*").to("mock:intercepted");
                }
                from("direct:start").routeId("start").toD("mock:${header.name}");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // a route without interceptSendToEndpoint
                from("direct:other").routeId("other").toD("mock:${header.name}");
            }
        });
    }

    /**
     * Sends to direct:other, which creates the endpoint, and pauses that thread before the endpoint is added to the
     * registry while the action runs. The send must succeed (it is not sent from the route that starts or stops).
     */
    private void createEndpointDuring(String name, ThrowingRunnable<Exception> action) throws Exception {
        pauseUri.set("mock://" + name);
        Future<Exchange> send = executor.submit(() -> template.send("direct:other", e -> {
            e.getMessage().setBody("Hello");
            e.getMessage().setHeader("name", name);
        }));
        assertTrue(arrived.await(20, TimeUnit.SECONDS), "the endpoint was not created");
        action.run();
        release.countDown();
        Exchange out = send.get(20, TimeUnit.SECONDS);
        assertNull(out.getException(), "the send that created the endpoint failed");
    }

    private void resetMocks(String name) {
        getMockEndpoint("mock:intercepted").reset();
        getMockEndpoint("mock:" + name).reset();
    }

    @Test
    public void testEndpointCreatedWhileRouteStarts() throws Exception {
        addRoutes(false);
        context.start();
        context.getRouteController().stopRoute("start");

        createEndpointDuring("dyn1", () -> context.getRouteController().startRoute("start"));

        // the route is running, so it intercepts the endpoint
        resetMocks("dyn1");
        getMockEndpoint("mock:intercepted").expectedMessageCount(1);
        getMockEndpoint("mock:dyn1").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Bye", "name", "dyn1");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEndpointCreatedWhileRouteStops() throws Exception {
        addRoutes(true);
        context.start();

        createEndpointDuring("dyn2", () -> context.getRouteController().stopRoute("start"));

        // the route is stopped, so the endpoint is no longer intercepted (and no longer skipped)
        resetMocks("dyn2");
        getMockEndpoint("mock:intercepted").expectedMessageCount(0);
        getMockEndpoint("mock:dyn2").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:other", "Bye", "name", "dyn2");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEndpointCreatedWhileRouteIsRemoved() throws Exception {
        addRoutes(false);
        context.start();

        createEndpointDuring("dyn3", () -> {
            context.getRouteController().stopRoute("start");
            context.removeRoute("start");
        });

        // the route is added again: its interceptor is used once (not also the one of the removed route)
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("mock:dyn*").to("mock:intercepted");
                from("direct:start").routeId("start").toD("mock:${header.name}");
            }
        });
        resetMocks("dyn3");
        getMockEndpoint("mock:intercepted").expectedMessageCount(1);
        getMockEndpoint("mock:dyn3").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Bye", "name", "dyn3");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEndpointCreatedBeforeRouteStarts() throws Exception {
        // control: the same steps one after the other
        addRoutes(false);
        context.start();
        context.getRouteController().stopRoute("start");
        template.sendBodyAndHeader("direct:other", "Hello", "name", "dyn4");
        context.getRouteController().startRoute("start");

        resetMocks("dyn4");
        getMockEndpoint("mock:intercepted").expectedMessageCount(1);
        getMockEndpoint("mock:dyn4").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Bye", "name", "dyn4");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEndpointCreatedBeforeRouteStops() throws Exception {
        // control: the same steps one after the other
        addRoutes(true);
        context.start();
        template.sendBodyAndHeader("direct:other", "Hello", "name", "dyn5");
        context.getRouteController().stopRoute("start");

        resetMocks("dyn5");
        getMockEndpoint("mock:intercepted").expectedMessageCount(0);
        getMockEndpoint("mock:dyn5").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:other", "Bye", "name", "dyn5");
        assertMockEndpointsSatisfied();
    }

}

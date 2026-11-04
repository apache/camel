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
package org.apache.camel.impl.engine;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that Split and Multicast EIPs do not start child producers during doInit(), which would bypass the
 * SupervisingRouteController retry logic and fail the entire context when a producer cannot connect yet.
 * <p>
 * CAMEL-25823: Split and Multicast start their child producers before CamelContext starts, so with the supervising
 * route controller a producer that cannot start yet fails the whole context.
 */
class DefaultSupervisingRouteControllerMulticastSplitTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(25);
        src.setBackOffMaxAttempts(10);
        src.setInitialDelay(10);
        src.setThreadPoolSize(2);
        return context;
    }

    @Test
    void testSplitWithFlakyProducer() throws Exception {
        FlakyComponent component = new FlakyComponent(2);
        context.addComponent("flaky", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:split")
                        .routeId("split-route")
                        .split(body())
                        .to("flaky:target");
            }
        });

        // Must not throw - supervising route controller should retry
        context.start();

        // Route should eventually reach Started state after retries
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertEquals(ServiceStatus.Started,
                        context.getRouteController().getRouteStatus("split-route")));

        // Verify the producer was retried (failCount=2 means 3 start attempts total)
        FlakyEndpoint endpoint = (FlakyEndpoint) context.getEndpoint("flaky:target");
        assertEquals(3, endpoint.startAttempts.get(), "Expected 3 start attempts (2 failures + 1 success)");

        // Verify that a message can actually be processed once the route is started
        template.sendBody("direct:split", "a,b,c");
        assertEquals(3, endpoint.processCount.get(), "Expected 3 process calls for 3 split tokens");
    }

    @Test
    void testMulticastWithFlakyProducer() throws Exception {
        FlakyComponent component = new FlakyComponent(2);
        context.addComponent("flaky", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:multicast")
                        .routeId("multicast-route")
                        .multicast()
                        .to("flaky:target");
            }
        });

        // Must not throw - supervising route controller should retry
        context.start();

        // Route should eventually reach Started state after retries
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertEquals(ServiceStatus.Started,
                        context.getRouteController().getRouteStatus("multicast-route")));

        // Verify the producer was retried (failCount=2 means 3 start attempts total)
        FlakyEndpoint endpoint = (FlakyEndpoint) context.getEndpoint("flaky:target");
        assertEquals(3, endpoint.startAttempts.get(), "Expected 3 start attempts (2 failures + 1 success)");

        // Verify that a message can actually be processed once the route is started
        template.sendBody("direct:multicast", "hello");
        assertEquals(1, endpoint.processCount.get(), "Expected 1 process call for multicast to single target");
    }

    /**
     * A component whose producer fails its first {@code failCount} doStart() calls with an exception, simulating a
     * broker that is temporarily unavailable. The failure count is tracked on the endpoint (shared across producer
     * instances) so retries eventually succeed even when a new producer is created on each restart attempt.
     */
    static class FlakyComponent extends DefaultComponent {

        private final int failCount;

        FlakyComponent(int failCount) {
            this.failCount = failCount;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new FlakyEndpoint(uri, this, failCount);
        }
    }

    static class FlakyEndpoint extends DefaultEndpoint {

        final AtomicInteger startAttempts = new AtomicInteger();
        final AtomicInteger processCount = new AtomicInteger();
        private final int failCount;

        FlakyEndpoint(String uri, Component component, int failCount) {
            super(uri, component);
            this.failCount = failCount;
        }

        @Override
        public Producer createProducer() {
            return new FlakyProducer(this);
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor);
        }

        boolean shouldFail() {
            return startAttempts.incrementAndGet() <= failCount;
        }
    }

    static class FlakyProducer extends DefaultProducer {

        FlakyProducer(FlakyEndpoint endpoint) {
            super(endpoint);
        }

        @Override
        protected void doStart() throws Exception {
            FlakyEndpoint ep = (FlakyEndpoint) getEndpoint();
            if (ep.shouldFail()) {
                throw new IllegalStateException("Cannot connect");
            }
        }

        @Override
        public void process(Exchange exchange) {
            FlakyEndpoint ep = (FlakyEndpoint) getEndpoint();
            ep.processCount.incrementAndGet();
        }
    }
}

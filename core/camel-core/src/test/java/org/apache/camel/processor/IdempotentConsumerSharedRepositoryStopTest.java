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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping a route must not stop (and thereby clear) an idempotent repository that other routes still use.
 */
class IdempotentConsumerSharedRepositoryStopTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testStopRouteKeepsSharedMemoryRepository() throws Exception {
        assertStopRouteKeepsSharedRepository(MemoryIdempotentRepository.memoryIdempotentRepository(200));
    }

    @Test
    void testStopRouteKeepsSharedKeyValueRepository() throws Exception {
        assertStopRouteKeepsSharedRepository(new KeyValueIdempotentRepository());
    }

    @Test
    void testStopRouteKeepsAutoDiscoveredKeyValueRepository() throws Exception {
        // no repositories configured, so both idempotent consumers and the aggregator use this store
        MemoryKeyValueRepository store = new MemoryKeyValueRepository();
        context.getRegistry().bind("store", store);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").idempotentConsumer(header("messageId")).to("mock:a");
                from("direct:b").routeId("b").idempotentConsumer(header("messageId")).to("mock:b");
                from("direct:agg").routeId("agg")
                        .aggregate(header("group"), (oldExchange, newExchange) -> {
                            if (oldExchange == null) {
                                return newExchange;
                            }
                            oldExchange.getMessage().setBody(oldExchange.getMessage().getBody(String.class) + "+"
                                                             + newExchange.getMessage().getBody(String.class));
                            return oldExchange;
                        }).completionSize(2)
                        .to("mock:agg");
            }
        });
        context.start();

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedBodiesReceived("one");
        MockEndpoint agg = getMockEndpoint("mock:agg");
        agg.expectedBodiesReceived("A+B");

        template.sendBodyAndHeader("direct:a", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:agg", "A", "group", "g1");

        context.getRouteController().stopRoute("b");
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("a"));

        template.sendBodyAndHeader("direct:a", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:agg", "B", "group", "g1");

        // the aggregator must still have the group with A, and route a must still know id 1
        agg.assertIsSatisfied();
        a.assertIsSatisfied();
    }

    @Test
    void testStopCacheRouteKeepsAutoDiscoveredKeyValueRepository() throws Exception {
        // no repositories configured, so the idempotent consumer and the cache EIP use this store
        MemoryKeyValueRepository store = new MemoryKeyValueRepository();
        context.getRegistry().bind("store", store);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").idempotentConsumer(header("messageId")).to("mock:a");
                from("direct:cache").routeId("cache")
                        .cache(header("key"))
                            .to("mock:service")
                        .end();
            }
        });
        context.start();

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedBodiesReceived("one");

        template.sendBodyAndHeader("direct:a", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:cache", "value", "key", "K");

        context.getRouteController().stopRoute("cache");
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("a"));

        // route a must still know id 1
        template.sendBodyAndHeader("direct:a", "one", "messageId", "1");
        a.assertIsSatisfied();
    }

    @Test
    void testRestartRouteKeepsRepository() throws Exception {
        KeyValueIdempotentRepository repo = new KeyValueIdempotentRepository();
        addRoute("a", repo);
        context.start();

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedBodiesReceived("one");

        template.sendBodyAndHeader("direct:a", "one", "messageId", "1");
        context.getRouteController().stopRoute("a");
        assertTrue(repo.contains("1"), "Stopping the route must not clear the repository");
        context.getRouteController().startRoute("a");
        template.sendBodyAndHeader("direct:a", "one", "messageId", "1");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testRemoveRouteStopsRepository() throws Exception {
        KeyValueIdempotentRepository repo = new KeyValueIdempotentRepository();
        addRoute("a", repo);
        context.start();
        assertTrue(repo.isStarted());

        context.getRouteController().stopRoute("a");
        assertTrue(repo.isStarted(), "Stopping the route must not stop the repository");

        // removing the route stops the repository, and removes it from CamelContext
        assertTrue(context.removeRoute("a"));
        assertFalse(repo.isStarted());
        assertFalse(context.hasService(repo));
    }

    @Test
    void testStopCamelContextStopsRepository() throws Exception {
        KeyValueIdempotentRepository repo = new KeyValueIdempotentRepository();
        addRoute("a", repo);
        context.start();
        assertTrue(repo.isStarted());

        context.stop();
        assertFalse(repo.isStarted());
    }

    private void assertStopRouteKeepsSharedRepository(IdempotentRepository repo) throws Exception {
        addRoute("a", repo);
        addRoute("b", repo);
        context.start();

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedBodiesReceived("one");

        template.sendBodyAndHeader("direct:a", "one", "messageId", "1");
        Exchange out = template.send("direct:a", e -> {
            e.getIn().setHeader("messageId", "1");
            e.getIn().setBody("one");
        });
        assertEquals(Boolean.TRUE, out.getProperty(Exchange.DUPLICATE_MESSAGE));

        // stopping route b must not stop the repository that route a still uses
        context.getRouteController().stopRoute("b");
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("a"));
        assertTrue(repo.contains("1"), "The repository shared with route a must not be cleared");
        assertTrue(((ServiceSupport) repo).isStarted(), "The repository shared with route a must not be stopped");

        template.sendBodyAndHeader("direct:a", "one", "messageId", "1");

        assertMockEndpointsSatisfied();
    }

    private void addRoute(String id, IdempotentRepository repo) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + id).routeId(id)
                        .idempotentConsumer(header("messageId")).idempotentRepository(repo)
                        .to("mock:" + id);
            }
        });
    }
}

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
package org.apache.camel.impl;

import java.util.Map;

import org.apache.camel.AsyncProducer;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Endpoints are shared by the producer caches (resolved from the endpoint registry), so evicting the producer of an
 * endpoint from one producer cache must not stop the endpoint while another producer cache still uses it.
 */
class ProducerCacheEvictSharedEndpointTest extends ContextTestSupport {

    @Test
    void testEvictDoesNotStopEndpointOfOtherRoute() {
        // route b keeps its producer of seda:shared in its producer cache
        template.sendBodyAndHeader("direct:b", "B", "uri", "seda:shared");
        Endpoint shared = context.hasEndpoint("seda:shared");

        // route a caches one producer, so this evicts its producer of seda:shared (cleaned up on release)
        template.sendBodyAndHeader("direct:a", "A", "uri", "seda:shared");
        template.sendBodyAndHeader("direct:a", "A", "uri", "seda:other");

        assertTrue(ServiceHelper.isStarted(shared), "seda:shared is still used by route b and must not be stopped");
        assertSame(shared, context.hasEndpoint("seda:shared"), "seda:shared should still be registered");
    }

    @Test
    void testEvictDoesNotStopEndpointOfProducerTemplate() throws Exception {
        context.addComponent("closing", new ClosingComponent());
        Endpoint shared = context.getEndpoint("closing:shared");
        ProducerTemplate other = context.createProducerTemplate();
        try {
            other.sendBody(shared, "Hello");

            // route a caches one producer, so this evicts its producer of closing:shared (cleaned up on release)
            template.sendBodyAndHeader("direct:a", "A", "uri", "closing:shared");
            template.sendBodyAndHeader("direct:a", "A", "uri", "closing:other");

            // fails if the endpoint was stopped (as with an endpoint that closes its client when stopped)
            Exchange out = other.send(shared, e -> e.getMessage().setBody("World"));
            assertNull(out.getException(), "The template should still be able to send to closing:shared");
            assertTrue(ServiceHelper.isStarted(shared), "closing:shared is still used by the template and must not be stopped");
        } finally {
            other.stop();
        }
    }

    @Test
    void testEvictStopsEndpointNotInUseWhenPoolIsNotStoppedOnEviction() throws Exception {
        context.addComponent("closing", new ClosingComponent());
        context.addComponent("multi", new MultipleProducersComponent());
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 2);
        cache.start();
        try {
            Endpoint dynamic = context.getEndpoint("closing:dynamic");
            cache.releaseProducer(dynamic, cache.acquireProducer(dynamic));

            // two producers of a non-singleton endpoint (one pool) evict the producer of closing:dynamic, and as the
            // cache still has no more pools than its capacity, the evicted pool is only stopped by the cleanup
            Endpoint multi = context.getEndpoint("multi:foo");
            AsyncProducer first = cache.acquireProducer(multi);
            AsyncProducer second = cache.acquireProducer(multi);
            cache.releaseProducer(multi, first);
            cache.releaseProducer(multi, second);

            // the cleanup of the evicted singleton pool runs when a singleton producer is acquired
            Endpoint other = context.getEndpoint("closing:other");
            cache.releaseProducer(other, cache.acquireProducer(other));

            assertFalse(ServiceHelper.isStarted(dynamic),
                    "the dynamic endpoint not in use should be stopped to free resources");
            assertNull(context.hasEndpoint("closing:dynamic"), "the dynamic endpoint not in use should be removed");
        } finally {
            cache.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").toD("${header.uri}", 1);
                from("direct:b").toD("${header.uri}");
            }
        };
    }

    private static final class ClosingComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new ClosingEndpoint(uri, this);
        }
    }

    private static final class MultipleProducersComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new ClosingEndpoint(uri, this) {
                @Override
                public boolean isSingletonProducer() {
                    return false;
                }
            };
        }
    }

    private static class ClosingEndpoint extends DefaultEndpoint {
        ClosingEndpoint(String uri, DefaultComponent component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    if (!ServiceHelper.isStarted(getEndpoint())) {
                        throw new IllegalStateException("Endpoint is stopped: " + getEndpoint());
                    }
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }
}

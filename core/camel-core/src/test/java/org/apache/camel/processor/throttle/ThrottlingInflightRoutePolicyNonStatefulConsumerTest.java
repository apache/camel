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
package org.apache.camel.processor.throttle;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.throttling.ThrottlingInflightRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link ThrottlingInflightRoutePolicy} must still throttle a consumer that is not a {@link StatefulService}: it is
 * stopped when too many exchanges are inflight, and started again when they have completed.
 */
class ThrottlingInflightRoutePolicyNonStatefulConsumerTest extends ContextTestSupport {

    private final ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();

    @Test
    void testThrottlesConsumerThatIsNotStatefulService() throws Exception {
        Route route = context.getRoute("foo");
        PlainConsumer consumer = (PlainConsumer) route.getConsumer();
        assertFalse(route.getConsumer() instanceof StatefulService);
        assertTrue(consumer.running);
        assertEquals(0, consumer.stops.get());
        int startsBefore = consumer.starts.get();

        // an exchange completes while more exchanges than the maximum are inflight
        InflightRepository inflight = context.getInflightRepository();
        Exchange first = new DefaultExchange(context);
        Exchange second = new DefaultExchange(context);
        inflight.add(first, "foo");
        inflight.add(second, "foo");
        policy.onExchangeDone(route, first);

        assertEquals(1, consumer.stops.get(), "The policy should stop the consumer when too many exchanges are inflight");
        assertFalse(consumer.running);

        // and then the inflight exchanges complete
        inflight.remove(first, "foo");
        inflight.remove(second, "foo");
        policy.onExchangeDone(route, second);

        assertEquals(startsBefore + 1, consumer.starts.get(), "The policy should start the consumer it stopped");
        assertTrue(consumer.running);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addEndpoint("plain", new DefaultEndpoint() {
            @Override
            public Producer createProducer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                return new PlainConsumer(this, processor);
            }

            @Override
            protected String createEndpointUri() {
                return "plain";
            }

            @Override
            public boolean isSingleton() {
                return true;
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                policy.setMaxInflightExchanges(1);

                from("plain").routeId("foo").routePolicy(policy)
                        .to("mock:result");
            }
        };
    }

    /**
     * A consumer that implements only {@link Consumer}, and so is neither a {@link StatefulService} nor
     * {@link org.apache.camel.Suspendable}.
     */
    private static final class PlainConsumer implements Consumer {

        private final Endpoint endpoint;
        private final Processor processor;
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();
        private volatile boolean running;

        private PlainConsumer(Endpoint endpoint, Processor processor) {
            this.endpoint = endpoint;
            this.processor = processor;
        }

        @Override
        public void start() {
            starts.incrementAndGet();
            running = true;
        }

        @Override
        public void stop() {
            stops.incrementAndGet();
            running = false;
        }

        @Override
        public Endpoint getEndpoint() {
            return endpoint;
        }

        @Override
        public Processor getProcessor() {
            return processor;
        }

        @Override
        public Exchange createExchange(boolean autoRelease) {
            return endpoint.createExchange();
        }

        @Override
        public void releaseExchange(Exchange exchange, boolean autoRelease) {
            // noop
        }
    }
}

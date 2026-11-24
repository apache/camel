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
package org.apache.camel.processor.async;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routing slip must not copy the exchange of a step that continues asynchronously, as another thread may be
 * processing it (the copy resets the stream cache of the body that the step may be reading).
 */
public class AsyncEndpointRoutingSlipNoCopyInFlightTest extends ContextTestSupport {

    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicInteger resetsInFlight = new AtomicInteger();
    private final AtomicReference<Runnable> completion = new AtomicReference<>();

    @Test
    public void testNoCopyWhileStepInFlight() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        ByteArrayInputStreamCache body = new ByteArrayInputStreamCache(new ByteArrayInputStream("Hello".getBytes())) {
            @Override
            public void reset() {
                if (inFlight.get()) {
                    resetsInFlight.incrementAndGet();
                }
                super.reset();
            }
        };

        Exchange exchange = context.getEndpoint("direct:start").createExchange();
        exchange.getIn().setBody(body);
        CountDownLatch done = new CountDownLatch(1);

        // when process returns the step continues asynchronously, and the routing slip has handed it over
        AsyncProcessor processor = (AsyncProcessor) context.getRoute("start").getConsumer().getProcessor();
        boolean sync = processor.process(exchange, doneSync -> done.countDown());
        assertFalse(sync, "The step should continue asynchronously");
        assertNotNull(completion.get(), "The step should be in flight");

        completion.get().run();
        assertTrue(done.await(10, TimeUnit.SECONDS), "The exchange should complete");

        assertMockEndpointsSatisfied();
        assertEquals(0, resetsInFlight.get(), "The exchange of the step in flight should not be copied");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("hold", new HoldComponent());

                // no error handler, so the step is called directly and continues on another thread
                errorHandler(noErrorHandler());

                from("direct:start").routeId("start").routingSlip(constant("hold:step,mock:result"));
            }
        };
    }

    private final class HoldComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    return new DefaultAsyncProducer(this) {
                        @Override
                        public boolean process(Exchange exchange, AsyncCallback callback) {
                            inFlight.set(true);
                            completion.set(() -> new Thread(() -> {
                                inFlight.set(false);
                                callback.done(false);
                            }, "HoldStep").start());
                            return false;
                        }
                    };
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}

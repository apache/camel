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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A prototype endpoint (cacheSize=-1) of a routing slip, and its producer, are stopped after use, also when the step
 * completes asynchronously.
 */
public class RoutingSlipPrototypeAsyncTest extends ContextTestSupport {

    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger stopped = new AtomicInteger();
    private final AtomicInteger endpointStopped = new AtomicInteger();

    @Test
    public void testPrototypeEndpointStoppedAfterAsyncStep() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello", "slip", "async:a");

        assertMockEndpointsSatisfied();
        assertEquals(1, started.get());
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, stopped.get()));
        // the prototype endpoint itself must also be stopped, as it was only used once
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, endpointStopped.get()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.addComponent("async", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new DefaultEndpoint(uri, this) {
                    @Override
                    public Producer createProducer() {
                        return new DefaultAsyncProducer(this) {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                CompletableFuture.runAsync(() -> callback.done(false));
                                return false;
                            }

                            @Override
                            protected void doStart() {
                                started.incrementAndGet();
                            }

                            @Override
                            protected void doStop() {
                                stopped.incrementAndGet();
                            }
                        };
                    }

                    @Override
                    protected void doStop() throws Exception {
                        endpointStopped.incrementAndGet();
                        super.doStop();
                    }

                    @Override
                    public Consumer createConsumer(Processor processor) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        });

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routingSlip(header("slip")).cacheSize(-1).end().to("mock:result");
            }
        };
    }
}

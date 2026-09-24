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
package org.apache.camel.processor.enricher;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The on completions registered on the resource exchange (for example to close a response stream) run when the enrich
 * fails, both when the resource fails and when the aggregation fails.
 */
public class EnricherResourceCompletionTest extends ContextTestSupport {

    private final AtomicInteger completions = new AtomicInteger();

    @Test
    public void testResourceFailed() {
        assertThrows(Exception.class, () -> template.requestBody("direct:resourceFails", "Hello"));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, completions.get()));
    }

    @Test
    public void testAggregationFailed() {
        assertThrows(Exception.class, () -> template.requestBody("direct:aggregationFails", "Hello"));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, completions.get()));
    }

    @Test
    public void testSuccess() {
        template.requestBody("direct:ok", "Hello");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, completions.get()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // a producer that registers an on completion on the exchange it is given (such as a producer that must close
        // a response stream when the exchange is done), and fails when its path is "failing"
        context.addComponent("resource", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new DefaultEndpoint(uri, this) {
                    @Override
                    public Producer createProducer() {
                        return new DefaultProducer(this) {
                            @Override
                            public void process(Exchange exchange) {
                                addCompletion(exchange);
                                if ("failing".equals(remaining)) {
                                    throw new IllegalArgumentException("Forced");
                                }
                                exchange.getMessage().setBody("Resource");
                            }
                        };
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
                from("direct:resourceFails").enrich("resource:failing");
                from("direct:aggregationFails").enrich("resource:ok", new FailingStrategy());
                from("direct:ok").enrich("resource:ok");
            }
        };
    }

    private void addCompletion(Exchange exchange) {
        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                completions.incrementAndGet();
            }
        });
    }

    private static class FailingStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            throw new IllegalArgumentException("Forced");
        }
    }
}

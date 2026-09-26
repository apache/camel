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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When a recipient cannot be resolved, the producers already acquired for the recipients before it must be released.
 */
public class RecipientListInvalidEndpointReleaseTest extends ContextTestSupport {

    private final Map<String, AtomicInteger> started = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> stopped = new ConcurrentHashMap<>();

    @Test
    public void testProducersReleasedWhenLaterRecipientIsInvalid() {
        assertThrows(Exception.class, () -> template.sendBody("direct:start", "Hello"));

        assertEquals(1, count(started, "a"));
        assertEquals(1, count(stopped, "a"), "the producer of the prototype recipient should be released and stopped");
    }

    @Test
    public void testCachedProducerReleasedWhenLaterRecipientIsInvalid() {
        assertThrows(Exception.class, () -> template.sendBody("direct:cached", "Hello"));
        assertThrows(Exception.class, () -> template.sendBody("direct:cached", "World"));

        // the producer is not a singleton, so it is pooled: when released after the first message,
        // the second message acquires it from the pool again, instead of creating a new producer
        assertEquals(1, count(started, "c"), "the pooled producer should be released and reused");
    }

    private static int count(Map<String, AtomicInteger> counters, String key) {
        AtomicInteger counter = counters.get(key);
        return counter != null ? counter.get() : 0;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.addComponent("track", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new DefaultEndpoint(uri, this) {
                    @Override
                    public Producer createProducer() {
                        return new DefaultProducer(this) {
                            @Override
                            public void process(Exchange exchange) {
                                // noop
                            }

                            @Override
                            protected void doStart() {
                                started.computeIfAbsent(remaining, k -> new AtomicInteger()).incrementAndGet();
                            }

                            @Override
                            protected void doStop() {
                                stopped.computeIfAbsent(remaining, k -> new AtomicInteger()).incrementAndGet();
                            }
                        };
                    }

                    @Override
                    public boolean isSingletonProducer() {
                        return false;
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
                from("direct:start").recipientList(constant("track:a,unknownxyz:b")).cacheSize(-1);

                from("direct:cached").recipientList(constant("track:c,unknownxyz:d"));
            }
        };
    }
}

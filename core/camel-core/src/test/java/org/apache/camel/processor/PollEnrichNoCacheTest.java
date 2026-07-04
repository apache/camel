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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PollingConsumerSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PollEnrichNoCacheTest extends ContextTestSupport {

    @Test
    public void testNoCache() throws Exception {
        final AtomicInteger stopped = new AtomicInteger();
        context.addComponent("pollAssert", stopCountingComponent(stopped));

        assertEquals(1, context.getEndpointRegistry().size());

        sendBody("foo", "seda:x");
        sendBody("foo", "seda:y");
        sendBody("foo", "seda:z");
        sendBody("bar", "seda:x");
        sendBody("bar", "seda:y");
        sendBody("bar", "seda:z");

        // make sure its using an empty consumer cache as the cache is disabled
        List<Processor> list = getProcessors("foo");
        PollEnricher ep = (PollEnricher) list.get(0);
        assertNotNull(ep);
        assertEquals(-1, ep.getCacheSize());

        // check no additional endpoints added as cache was disabled
        assertEquals(1, context.getEndpointRegistry().size());

        // now send again and create endpoints
        template.sendBody("seda:x", "x");
        template.sendBody("seda:y", "y");
        template.sendBody("seda:z", "z");

        assertEquals(4, context.getEndpointRegistry().size());

        sendBody("foo", "seda:x");
        sendBody("foo", "seda:y");
        sendBody("foo", "seda:z");
        sendBody("bar", "seda:x");
        sendBody("bar", "seda:y");
        sendBody("bar", "seda:z");

        // should not register as new endpoint so we keep at 4
        sendBody("dummy", "seda:dummy");

        assertMockEndpointsSatisfied();

        assertEquals(4, context.getEndpointRegistry().size());

        // also verify that cacheSize(-1) means consumers are not retained
        sendBody("poll-one", "pollAssert:one");
        sendBody("poll-two", "pollAssert:two");

        assertEquals(2, stopped.get());
    }

    protected void sendBody(String body, String uri) {
        template.sendBodyAndHeader("direct:a", body, "myHeader", uri);
    }

    /**
     * A test-only component whose polling consumers increment the given counter in
     * {@link PollingConsumerSupport#doStop()}, allowing the test to verify whether the consumer cache retains or
     * discards consumers after use.
     */
    private static DefaultComponent stopCountingComponent(AtomicInteger stopped) {
        return new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new DefaultEndpoint(uri, this) {
                    @Override
                    public Producer createProducer() {
                        return null;
                    }

                    @Override
                    public Consumer createConsumer(Processor processor) {
                        return null;
                    }

                    @Override
                    public PollingConsumer createPollingConsumer() {
                        return new PollingConsumerSupport(this) {
                            @Override
                            public Exchange receive() {
                                Exchange ex = getEndpoint().createExchange();
                                ex.getIn().setBody(remaining);
                                return ex;
                            }

                            @Override
                            public Exchange receive(long timeout) {
                                return receive();
                            }

                            @Override
                            public Exchange receiveNoWait() {
                                return receive();
                            }

                            @Override
                            protected void doStop() {
                                stopped.incrementAndGet();
                            }
                        };
                    }
                };
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a")
                        .pollEnrich().header("myHeader").timeout(0).cacheSize(-1).end().id("foo");
            }
        };

    }

}

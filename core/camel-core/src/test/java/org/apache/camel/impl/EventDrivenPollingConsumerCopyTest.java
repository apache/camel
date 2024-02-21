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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventDrivenPollingConsumerCopyTest extends ContextTestSupport {

    private final AtomicBoolean done = new AtomicBoolean();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testCopy() throws Exception {
        PollingConsumer pc = context.getEndpoint("direct:foo?pollingConsumerCopy=true").createPollingConsumer();
        pc.start();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getUnitOfWork().addSynchronization(new SynchronizationAdapter() {
                                    @Override
                                    public void onDone(Exchange exchange) {
                                        done.set(true);
                                    }
                                });
                            }
                        })
                        .to("direct:foo")
                        .to("mock:result");
            }
        });
        context.start();

        // should be 0 inflight
        assertEquals(0, context.getInflightRepository().size());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        Exchange original = getMockEndpoint("mock:result").getExchanges().get(0);
        assertNotNull(original);
        assertFalse(done.get(), "UoW should be handed over");

        Exchange polled = pc.receive(1000);
        assertNotNull(polled);
        assertEquals("Hello World", polled.getMessage().getBody());
        assertNotEquals(polled.getExchangeId(), original.getExchangeId());

        // should be 1 inflight
        assertEquals(1, context.getInflightRepository().size());

        // done uow
        polled.getUnitOfWork().done(polled);
        assertTrue(done.get(), "UoW should be done now");

        // should be 0 inflight
        assertEquals(0, context.getInflightRepository().size());

        pc.stop();
        context.stop();
    }

}

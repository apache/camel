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
package org.apache.camel.component.timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With the pooled exchange factory, a timer route whose processor throws a VirtualMachineError fails the exchange
 * (CAMEL-27515): the exchange completes and is given back to the pool, as nothing of the route runs on it later, and
 * the timer stops firing.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-27515">CAMEL-27515</a>
 */
class TimerPooledExchangeVirtualMachineErrorTest extends ContextTestSupport {

    private static final String ROUTE_ID = "route-pooled";

    private final AtomicInteger fired = new AtomicInteger();
    private final AtomicInteger after = new AtomicInteger();

    @Test
    void exchangeCompletesAndTheTimerStopsAfterVirtualMachineError() {
        TimerConsumer consumer = (TimerConsumer) context.getRoute(ROUTE_ID).getConsumer();

        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getFatalError() != null && !consumer.isPolling());

        // the failed exchange completed, and no later step of the route can use it
        assertEquals(0, context.getInflightRepository().size());
        assertEquals(1, fired.get());
        assertEquals(0, after.get());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getCamelContextExtension().setExchangeFactory(new PooledExchangeFactory());
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:pooled?period=50&synchronous=true")
                        .routeId(ROUTE_ID)
                        .process(exchange -> {
                            fired.incrementAndGet();
                            throw new StackOverflowError("simulated StackOverflowError in a pooled timer route");
                        })
                        .process(exchange -> after.incrementAndGet());
            }
        };
    }
}

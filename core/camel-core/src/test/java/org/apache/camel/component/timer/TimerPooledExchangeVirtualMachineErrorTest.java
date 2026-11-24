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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With the pooled exchange factory, a timer route whose processor throws a VirtualMachineError must not give the
 * exchange back to the pool: the rest of the route may still run on it (CAMEL-27515), and releasing it would reset it.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-27515">CAMEL-27515</a>
 */
class TimerPooledExchangeVirtualMachineErrorTest extends ContextTestSupport {

    private static final String ROUTE_ID = "route-pooled";

    private final AtomicReference<Exchange> failed = new AtomicReference<>();

    @Test
    void exchangeIsNotReleasedAfterVirtualMachineError() {
        TimerConsumer consumer = (TimerConsumer) context.getRoute(ROUTE_ID).getConsumer();

        // the timer task has ended once the processor has thrown and the consumer is no longer polling
        await().atMost(10, TimeUnit.SECONDS).until(() -> failed.get() != null && !consumer.isPolling());

        // a released pooled exchange is reset, and would have lost its header
        assertEquals("kept", failed.get().getMessage().getHeader("marker"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getCamelContextExtension().setExchangeFactory(new PooledExchangeFactory());
        return camelContext;
    }

    @Override
    protected int getShutdownTimeout() {
        // the exchange that failed with a VirtualMachineError stays inflight (CAMEL-27515)
        return 1;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:pooled?period=50&synchronous=true")
                        .routeId(ROUTE_ID)
                        .process(exchange -> {
                            exchange.getMessage().setHeader("marker", "kept");
                            failed.set(exchange);
                            throw new StackOverflowError("simulated StackOverflowError in a pooled timer route");
                        });
            }
        };
    }
}

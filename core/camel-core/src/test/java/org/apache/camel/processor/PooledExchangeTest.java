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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class PooledExchangeTest extends ContextTestSupport {

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicReference<Exchange> ref = new AtomicReference<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.adapt(ExtendedCamelContext.class).setExchangeFactory(new PooledExchangeFactory());
        return context;
    }

    @Test
    public void testSameExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedPropertyValuesReceivedInAnyOrder("myprop", 1, 3);
        mock.expectedHeaderValuesReceivedInAnyOrder("myheader", 2, 4);
        mock.message(0).header("first").isEqualTo(true);
        mock.message(1).header("first").isNull();

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?period=1&delay=1&repeatCount=2").noAutoStartup()
                        .setProperty("myprop", counter::incrementAndGet)
                        .setHeader("myheader", counter::incrementAndGet)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // should be same exchange instance as its pooled
                                Exchange old = ref.get();
                                if (old == null) {
                                    ref.set(exchange);
                                    exchange.getMessage().setHeader("first", true);
                                } else {
                                    assertSame(old, exchange);
                                }
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}

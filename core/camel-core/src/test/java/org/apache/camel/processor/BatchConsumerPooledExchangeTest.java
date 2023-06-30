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

import java.util.concurrent.TimeUnit;
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
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.apache.camel.spi.PooledObjectFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BatchConsumerPooledExchangeTest extends ContextTestSupport {

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicReference<Exchange> ref = new AtomicReference<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();

        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        ecc.getExchangeFactory().setStatisticsEnabled(true);
        ecc.getProcessorExchangeFactory().setStatisticsEnabled(true);

        return camelContext;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        template.sendBodyAndHeader(fileUri(), "aaa", Exchange.FILE_NAME, "aaa.BatchConsumerPooledExchangeTest.txt");
        template.sendBodyAndHeader(fileUri(), "bbb", Exchange.FILE_NAME, "bbb.BatchConsumerPooledExchangeTest.txt");
        template.sendBodyAndHeader(fileUri(), "ccc", Exchange.FILE_NAME, "ccc.BatchConsumerPooledExchangeTest.txt");
    }

    @Test
    public void testNotSameExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.expectedPropertyValuesReceivedInAnyOrder("myprop", 1, 3, 5);
        mock.expectedHeaderValuesReceivedInAnyOrder("myheader", 2, 4, 6);
        mock.message(0).header("first").isEqualTo(true);
        mock.message(1).header("first").isNull();
        mock.message(2).header("first").isNull();

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();

        Awaitility.waitAtMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            PooledObjectFactory.Statistics stat
                    = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
            assertEquals(1, stat.getCreatedCounter());
            assertEquals(2, stat.getAcquiredCounter());
            assertEquals(3, stat.getReleasedCounter());
            assertEquals(0, stat.getDiscardedCounter());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // maxMessagesPerPoll=1 to force polling 3 times to use pooled exchanges
                from(fileUri("?initialDelay=0&delay=10&maxMessagesPerPoll=1")).noAutoStartup()
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

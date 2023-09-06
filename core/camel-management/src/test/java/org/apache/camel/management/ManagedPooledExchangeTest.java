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
package org.apache.camel.management;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_SERVICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisabledOnOs(OS.AIX)
public class ManagedPooledExchangeTest extends ManagementTestSupport {

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicReference<Exchange> ref = new AtomicReference<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PooledExchangeFactory pef = new PooledExchangeFactory();
        pef.setStatisticsEnabled(true);
        pef.setCapacity(123);
        context.getCamelContextExtension().setExchangeFactory(pef);

        return context;
    }

    @Test
    public void testSameExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.expectedPropertyValuesReceivedInAnyOrder("myprop", 1, 3, 5);
        mock.expectedHeaderValuesReceivedInAnyOrder("myheader", 2, 4, 6);
        mock.message(0).header("first").isEqualTo(true);
        mock.message(1).header("first").isNull();
        mock.message(2).header("first").isNull();

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // get the object name for the delayer
        ObjectName on
                = getCamelObjectName(TYPE_SERVICE, "DefaultExchangeFactoryManager");

        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);

        Integer con = (Integer) mbeanServer.getAttribute(on, "ConsumerCounter");
        assertEquals(1, con.intValue());

        Integer cap = (Integer) mbeanServer.getAttribute(on, "Capacity");
        assertEquals(123, cap.intValue());

        Awaitility.await().untilAsserted(() -> {
            Long num = (Long) mbeanServer.getAttribute(on, "TotalCreated");
            assertEquals(1, num.intValue());

            num = (Long) mbeanServer.getAttribute(on, "TotalAcquired");
            assertEquals(2, num.intValue());

            num = (Long) mbeanServer.getAttribute(on, "TotalReleased");
            assertEquals(3, num.intValue());

            num = (Long) mbeanServer.getAttribute(on, "TotalDiscarded");
            assertEquals(0, num.intValue());

            Integer num2 = (Integer) mbeanServer.getAttribute(on, "TotalPooled");
            assertEquals(1, num2.intValue());
        });

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?period=1&delay=1&repeatCount=3").noAutoStartup()
                        .setProperty("myprop", counter::incrementAndGet)
                        .setHeader("myheader", counter::incrementAndGet)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // should be same exchange instance as its pooled
                                synchronized (this) {
                                    Exchange old = ref.get();
                                    if (old == null) {
                                        ref.set(exchange);
                                        exchange.getMessage().setHeader("first", true);
                                    } else {
                                        assertSame(old, exchange);
                                    }
                                }
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}

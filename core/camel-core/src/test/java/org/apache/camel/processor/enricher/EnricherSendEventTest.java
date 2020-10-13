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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultManagementStrategy;
import org.apache.camel.processor.async.MyAsyncComponent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnricherSendEventTest extends ContextTestSupport {
    private MyEventNotifier en = new MyEventNotifier();

    @Test
    public void testAsyncEnricher() throws Exception {

        template.sendBody("direct:start1", "test");
        assertEquals(3, en.exchangeSendingEvent.get(), "Get a wrong sending event number");
        assertEquals(3, en.exchangeSentEvent.get(), "Get a wrong sent event number");
    }

    @Test
    public void testSyncEnricher() throws Exception {
        template.sendBody("direct:start2", "test");
        assertEquals(3, en.exchangeSendingEvent.get(), "Get a wrong sending event number");
        assertEquals(3, en.exchangeSentEvent.get(), "Get a wrong sent event number");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ShutdownStrategy shutdownStrategy = camelContext.getShutdownStrategy();
        camelContext.addComponent("async", new MyAsyncComponent());

        shutdownStrategy.setTimeout(1000);
        shutdownStrategy.setTimeUnit(TimeUnit.MILLISECONDS);
        shutdownStrategy.setShutdownNowOnTimeout(true);

        ManagementStrategy managementStrategy = new DefaultManagementStrategy();
        managementStrategy.addEventNotifier(en);

        camelContext.setManagementStrategy(managementStrategy);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start1")
                        // using the async utility component to ensure that the
                        // async routing engine kicks in
                        .enrich("async:out?reply=Reply").to("mock:result");

                from("direct:start2").enrich("direct:result").to("mock:result");

                from("direct:result").setBody(constant("result"));
            }
        };
    }

    static class MyEventNotifier extends EventNotifierSupport {

        AtomicInteger exchangeSendingEvent = new AtomicInteger();
        AtomicInteger exchangeSentEvent = new AtomicInteger();

        @Override
        public void notify(CamelEvent event) throws Exception {

            if (event instanceof ExchangeSendingEvent) {
                exchangeSendingEvent.incrementAndGet();
            } else if (event instanceof ExchangeSentEvent) {
                exchangeSentEvent.incrementAndGet();
            }
        }
    }

}

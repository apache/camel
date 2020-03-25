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
package org.apache.camel.processor.routingslip;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.Test;

public class RoutingSlipEventNotifierTest extends ContextTestSupport {

    private MyEventNotifier notifier = new MyEventNotifier();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(notifier);
        return context;
    }

    @Test
    public void testRoutingSlipEventNotifier() throws Exception {
        getMockEndpoint("mock:x").expectedMessageCount(1);
        getMockEndpoint("mock:y").expectedMessageCount(1);
        getMockEndpoint("mock:z").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "myHeader", "mock:x,mock:y,mock:z");

        assertMockEndpointsSatisfied();

        assertEquals("Should have 5 sending events", 5, notifier.getSending());
        assertEquals("Should have 5 sent events", 5, notifier.getSent());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").routingSlip(header("myHeader")).to("mock:end");
            }
        };
    }

    private final class MyEventNotifier extends EventNotifierSupport {

        private int sending;
        private int sent;

        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event instanceof ExchangeSendingEvent) {
                log.info("Sending: {}", event);
                sending++;
            } else {
                log.info("Sent: {}", event);
                sent++;
            }
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof ExchangeSendingEvent || event instanceof ExchangeSentEvent;
        }

        public int getSending() {
            return sending;
        }

        public int getSent() {
            return sent;
        }
    }
}

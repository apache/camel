/**
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

import java.util.EventObject;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

public class DynamicRouterEventNotifierTest extends ContextTestSupport {

    private MyEventNotifier notifier = new MyEventNotifier();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(notifier);
        return context;
    }

    public void testDynamicRouterEventNotifier() throws Exception {
        getMockEndpoint("mock:x").expectedMessageCount(1);
        getMockEndpoint("mock:y").expectedMessageCount(1);
        getMockEndpoint("mock:z").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals("Should have 5 sending events", 5, notifier.getSending());
        assertEquals("Should have 5 sent events", 5, notifier.getSent());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").dynamicRouter(method(DynamicRouterEventNotifierTest.class, "slip")).to("mock:end");
            }
        };
    }

    public String slip(@Body String body, @Header(Exchange.SLIP_ENDPOINT) String previous) {
        if (previous == null) {
            return "mock:x";
        } else if ("mock://x".equals(previous)) {
            return "mock:y";
        } else if ("mock://y".equals(previous)) {
            return "mock:z";
        }

        // no more so return null
        return null;
    }

    private final class MyEventNotifier extends EventNotifierSupport {

        private int sending;
        private int sent;

        @Override
        public void notify(EventObject event) throws Exception {
            if (event instanceof ExchangeSendingEvent) {
                log.info("Sending: {}", event);
                sending++;
            } else {
                sent++;
            }
        }

        @Override
        public boolean isEnabled(EventObject event) {
            return event instanceof ExchangeSendingEvent || event instanceof ExchangeSentEvent;
        }

        @Override
        protected void doStart() throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }

        public int getSending() {
            return sending;
        }

        public int getSent() {
            return sent;
        }
    }
}

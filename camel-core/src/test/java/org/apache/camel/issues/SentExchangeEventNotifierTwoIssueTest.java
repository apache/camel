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
package org.apache.camel.issues;

import java.util.EventObject;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

public class SentExchangeEventNotifierTwoIssueTest extends ContextTestSupport {

    private MyNotifier notifier = new MyNotifier();

    private class MyNotifier extends EventNotifierSupport {

        private int counter;

        @Override
        public void notify(EventObject event) throws Exception {
            counter++;
        }

        @Override
        public boolean isEnabled(EventObject event) {
            return event instanceof ExchangeSentEvent;
        }

        public int getCounter() {
            return counter;
        }

        public void reset() {
            counter = 0;
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(notifier);
        return context;
    }

    public void testExchangeSentNotifier() throws Exception {
        notifier.reset();

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("I was here", out);

        assertEquals(2, notifier.getCounter());
    }

    public void testExchangeSentNotifierExchange() throws Exception {
        notifier.reset();

        Exchange out = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });
        assertEquals("I was here", out.getIn().getBody());

        assertEquals(2, notifier.getCounter());
    }

    public void testExchangeSentNotifierManualExchange() throws Exception {
        notifier.reset();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");

        template.send("direct:start", exchange);
        assertEquals("I was here", exchange.getIn().getBody());

        assertEquals(2, notifier.getCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setBody("I was here");
                        }
                    }).to("mock:result");
            }
        };
    }
}

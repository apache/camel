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
package org.apache.camel.processor.async;

import java.util.EventObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

/**
 * @version 
 */
public class AsyncEndpointEventNotifierTest extends ContextTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicLong time = new AtomicLong();

    public void testAsyncEndpointEventNotifier() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        assertMockEndpointsSatisfied();

        assertTrue("Should count down", latch.await(10, TimeUnit.SECONDS));

        long delta = time.get();
        log.info("ExchangeEventSent took ms: " + delta);
        assertTrue("Should take about 250 millis sec, was: " + delta, delta > 200);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(EventObject event) throws Exception {
                try {
                    ExchangeSentEvent sent = (ExchangeSentEvent) event;
                    time.set(sent.getTimeTaken());
                } finally {
                    latch.countDown();
                }
            }

            public boolean isEnabled(EventObject event) {
                // we only want the async endpoint
                if (event instanceof ExchangeSentEvent) {
                    ExchangeSentEvent sent = (ExchangeSentEvent) event;
                    return sent.getEndpoint().getEndpointUri().startsWith("async");
                }
                return false;
            }

            @Override
            protected void doStart() throws Exception {
            }

            @Override
            protected void doStop() throws Exception {
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                        .to("mock:before")
                        .to("async:bye:camel?delay=250")
                        .to("mock:result");
            }
        };
    }

}
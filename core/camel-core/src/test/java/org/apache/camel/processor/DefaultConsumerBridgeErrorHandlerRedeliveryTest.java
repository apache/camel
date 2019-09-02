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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class DefaultConsumerBridgeErrorHandlerRedeliveryTest extends DefaultConsumerBridgeErrorHandlerTest {

    protected final AtomicInteger redeliverCounter = new AtomicInteger();

    @Override
    @Test
    public void testDefaultConsumerBridgeErrorHandler() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello World");
        getMockEndpoint("mock:dead").expectedBodiesReceived("Cannot process");

        latch.countDown();

        assertMockEndpointsSatisfied();

        // should not attempt redelivery as we must be exhausted when bridging
        // the error handler
        assertEquals(0, redeliverCounter.get());

        Exception cause = getMockEndpoint("mock:dead").getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);
        assertEquals("Simulated", cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // register our custom component
                getContext().addComponent("my", new MyComponent());

                // configure exception clause
                onException(Exception.class).maximumRedeliveries(3).onRedelivery(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        redeliverCounter.incrementAndGet();
                    }
                })
                    // setting delay to zero is just to make unit testing faster
                    .redeliveryDelay(0).handled(true).to("mock:dead");

                // configure the consumer to bridge with the Camel error
                // handler,
                // so the above error handler will trigger if exceptions also
                // occurs inside the consumer
                from("my:foo?bridgeErrorHandler=true").to("log:foo").to("mock:result");
            }
        };
    }

}

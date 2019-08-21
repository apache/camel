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
package org.apache.camel.component.seda;

import java.util.concurrent.Future;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

/**
 * The new Async API version of doing async routing based on the old
 * AsyncProcessor API In the old SedaAsyncProcessorTest a seda endpoint was
 * needed to really turn it into async. This is not needed by the new API so we
 * send it using direct instead.
 */
public class SedaAsyncProducerTest extends ContextTestSupport {

    private String route = "";

    @Test
    public void testAsyncProducer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // using the new async API we can fire a real async message
        Future<String> future = template.asyncRequestBody("direct:start", "Hello World", String.class);

        // I should happen before mock
        route = route + "send";

        assertMockEndpointsSatisfied();

        assertEquals("Send should occur before processor", "sendprocess", route);

        // and get the response with the future handle
        String response = future.get();
        assertEquals("Bye World", response);
    }

    @Test
    public void testAsyncProducerWait() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // using the new async API we can fire a real async message
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");
        exchange.setPattern(ExchangePattern.InOut);
        exchange.setProperty(Exchange.ASYNC_WAIT, WaitForTaskToComplete.IfReplyExpected);
        template.send("direct:start", exchange);

        // I should not happen before mock
        route = route + "send";

        assertMockEndpointsSatisfied();

        assertEquals("Send should occur before processor", "processsend", route);

        String response = exchange.getMessage().getBody(String.class);
        assertEquals("Bye World", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("direct:start").delay(100).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        route = route + "process";
                        // set the response
                        exchange.getMessage().setBody("Bye World");
                    }
                }).to("mock:result");

            }
        };
    }
}

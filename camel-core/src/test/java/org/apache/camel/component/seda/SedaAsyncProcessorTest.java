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
package org.apache.camel.component.seda;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Async processor with seda to simulate the caller thread is not blocking while the
 * exchange is processed and we get callbacks when the exchange is complete.
 *
 * @version $Revision$
 */
public class SedaAsyncProcessorTest extends ContextTestSupport {

    private CountDownLatch latchAsync = new CountDownLatch(1);
    private boolean doneSync;
    private String route = "";

    public void testAsyncWithSeda() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Endpoint endpoint = context.getEndpoint("seda:start");
        Producer producer = endpoint.createProducer();

        Exchange exchange = producer.createExchange(ExchangePattern.InOut);
        exchange.getIn().setBody("Hello World");

        // seda producer is async also (but this is ugly to need to cast)
        AsyncProcessor async = (AsyncProcessor) producer;

        boolean sync = async.process(exchange, new AsyncCallback() {
            public void done(boolean sync) {
                // we expect 2 callbacks
                // the first is when we have finished sending to the seda producer
                if (sync) {
                    doneSync = true;
                }
                // and the async should occur when the mock endpoint is done
                if (!sync) {
                    latchAsync.countDown();
                }
            }
        });

        // I should happen before mock
        route = route + "send";

        assertMockEndpointsSatisfied();

        assertEquals("Send should occur before processor", "sendprocess", route);
        assertTrue("Sync done should have occured", doneSync);

        // TODO: The AsyncProcessor does not work as expected
        // wait at most 2 seconds
        boolean zero = latchAsync.await(2, TimeUnit.SECONDS);
        // assertTrue("Async done should have occured", zero);

        // how to get the response?
        String response = exchange.getOut().getBody(String.class);
        // assertEquals("Bye World", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("seda:start").delay(100)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            route = route + "process";
                            // set the response
                            exchange.getOut().setBody("Bye World");
                        }
                    })
                    .to("mock:result");

            }
        };
    }
}

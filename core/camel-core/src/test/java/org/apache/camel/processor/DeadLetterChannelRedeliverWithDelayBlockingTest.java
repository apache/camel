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

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test to verify that using DLC with redelivery and delays with blocking
 * threads. As threads comes cheap these days in the modern JVM its no biggie.
 * And for transactions you should use the same thread anyway.
 */
public class DeadLetterChannelRedeliverWithDelayBlockingTest extends ContextTestSupport {

    private static int counter;

    @Test
    public void testRedeliverWithDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");

        // we expect message 2 to arrive before 1 as message 1 is in trouble
        // and must be redelivered 2 times before succeed
        mock.expectedMinimumMessageCount(2);
        mock.expectedBodiesReceived("Message 2", "Message 1");
        mock.expectedHeaderReceived("foo", "bar");
        // the first is not redelivered
        mock.message(0).header(Exchange.REDELIVERED).isNull();
        // but the 2nd is
        mock.message(1).header(Exchange.REDELIVERED).isEqualTo(true);

        // use executors to simulate two different clients sending
        // a request to Camel
        Callable<?> task1 = Executors.callable(new Runnable() {
            public void run() {
                template.sendBody("direct:start", "Message 1");
            }
        });

        Callable<?> task2 = Executors.callable(new Runnable() {
            public void run() {
                template.sendBody("direct:start", "Message 2");
            }
        });

        Executors.newCachedThreadPool().submit(task1);
        // give task 1 a head start, even though it comes last
        Thread.sleep(100);
        Executors.newCachedThreadPool().submit(task2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(250).maximumRedeliveries(3).logStackTrace(false));

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if ("Message 1".equals(body) && counter++ < 2) {
                            throw new IllegalArgumentException("Damn");
                        }
                        exchange.getIn().setHeader("foo", "bar");
                    }
                }).to("mock:result");
            }
        };
    }
}

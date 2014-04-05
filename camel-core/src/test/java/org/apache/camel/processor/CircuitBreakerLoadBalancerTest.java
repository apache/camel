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
package org.apache.camel.processor;

import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import static org.apache.camel.component.mock.MockEndpoint.expectsMessageCount;

public class CircuitBreakerLoadBalancerTest extends ContextTestSupport {

    private static class MyExceptionProcessor extends RuntimeException {
    }

    private MockEndpoint result;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        result = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").loadBalance()
                    .circuitBreaker(2, 1000L, MyExceptionProcessor.class)
                        .to("mock:result");
            }
        };
    }

    public void testClosedCircuitPassesMessages() throws Exception {
        expectsMessageCount(3, result);
        sendMessage("direct:start", "message one");
        sendMessage("direct:start", "message two");
        sendMessage("direct:start", "message three");
        assertMockEndpointsSatisfied();
    }

    public void testFailedMessagesOpenCircuitToPreventMessageThree() throws Exception {
        expectsMessageCount(2, result);

        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new MyExceptionProcessor());
            }
        });

        Exchange exchangeOne = sendMessage("direct:start", "message one");
        Exchange exchangeTwo = sendMessage("direct:start", "message two");
        Exchange exchangeThree = sendMessage("direct:start", "message three");
        assertMockEndpointsSatisfied();

        assertTrue(exchangeOne.getException() instanceof MyExceptionProcessor);
        assertTrue(exchangeTwo.getException() instanceof MyExceptionProcessor);
        assertTrue(exchangeThree.getException() instanceof RejectedExecutionException);
    }

    public void testHalfOpenCircuitClosesAfterTimeout() throws Exception {
        expectsMessageCount(2, result);
        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new MyExceptionProcessor());
            }
        });

        sendMessage("direct:start", "message one");
        sendMessage("direct:start", "message two");
        sendMessage("direct:start", "message three");
        assertMockEndpointsSatisfied();

        result.reset();
        expectsMessageCount(1, result);

        Thread.sleep(1000);
        sendMessage("direct:start", "message four");
        assertMockEndpointsSatisfied();
    }

    protected Exchange sendMessage(final String endpoint, final Object body) throws Exception {
        return template.send(endpoint, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(body);
            }
        });
    }
}

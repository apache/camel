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

    private static class MyCustomException extends RuntimeException {

        private static final long serialVersionUID = 1L;
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
                    .circuitBreaker(2, 1000L, MyCustomException.class)
                        .to("mock:result");
                from("direct:start-async").loadBalance()
                .circuitBreaker(2, 1000L, MyCustomException.class)
                    .threads(1).to("mock:result");
            }
        };
    }

    public void testClosedCircuitPassesMessagesSync() throws Exception {
        String endpoint = "direct:start";
        closedCircuitPassesMessages(endpoint);
    }
    
    public void testClosedCircuitPassesMessagesAsync() throws Exception {
        String endpoint = "direct:start-async";
        closedCircuitPassesMessages(endpoint);
    }

    private void closedCircuitPassesMessages(String endpoint) throws InterruptedException, Exception {
        expectsMessageCount(3, result);
        sendMessage(endpoint, "message one");
        sendMessage(endpoint, "message two");
        sendMessage(endpoint, "message three");
        assertMockEndpointsSatisfied();
    }

    public void testFailedMessagesOpenCircuitToPreventMessageThreeSync() throws Exception {
        String endpoint = "direct:start";
        failedMessagesOpenCircuitToPreventMessageThree(endpoint);
    }
    
    public void testFailedMessagesOpenCircuitToPreventMessageThreeAsync() throws Exception {
        String endpoint = "direct:start-async";
        failedMessagesOpenCircuitToPreventMessageThree(endpoint);
    }

    private void failedMessagesOpenCircuitToPreventMessageThree(String endpoint) throws InterruptedException, Exception {
        expectsMessageCount(2, result);

        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new MyCustomException());
            }
        });

        Exchange exchangeOne = sendMessage(endpoint, "message one");
        Exchange exchangeTwo = sendMessage(endpoint, "message two");
        Exchange exchangeThree = sendMessage(endpoint, "message three");
        assertMockEndpointsSatisfied();

        assertTrue(exchangeOne.getException() instanceof MyCustomException);
        assertTrue(exchangeTwo.getException() instanceof MyCustomException);
        assertTrue(exchangeThree.getException() instanceof RejectedExecutionException);
    }
    
    public void testHalfOpenAfterTimeoutSync() throws Exception {
        String endpoint = "direct:start";
        halfOpenAfterTimeout(endpoint);
    }
    
    public void testHalfOpenAfterTimeoutAsync() throws Exception {
        String endpoint = "direct:start-async";
        halfOpenAfterTimeout(endpoint);
    }
    
    private void halfOpenAfterTimeout(String endpoint) throws InterruptedException, Exception {
        expectsMessageCount(2, result);

        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new MyCustomException());
            }
        });

        Exchange exchangeOne = sendMessage(endpoint, "message one");
        Exchange exchangeTwo = sendMessage(endpoint, "message two");
        Exchange exchangeThree = sendMessage(endpoint, "message three");
        Exchange exchangeFour = sendMessage(endpoint, "message four");
        assertMockEndpointsSatisfied();
        Thread.sleep(1000);
        result.reset();
        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new MyCustomException());
            }
        });
        expectsMessageCount(1, result);
        Exchange exchangeFive = sendMessage(endpoint, "message five");
        Exchange exchangeSix = sendMessage(endpoint, "message six");
        assertMockEndpointsSatisfied();
        
        assertTrue(exchangeOne.getException() instanceof MyCustomException);
        assertTrue(exchangeTwo.getException() instanceof MyCustomException);
        assertTrue(exchangeThree.getException() instanceof RejectedExecutionException);
        assertTrue(exchangeFour.getException() instanceof RejectedExecutionException);
        assertTrue(exchangeFive.getException() instanceof MyCustomException);
        assertTrue(exchangeSix.getException() instanceof RejectedExecutionException);
    }
    
    public void testHalfOpenToCloseTransitionSync() throws Exception {
        String endpoint = "direct:start";
        halfOpenToCloseTransition(endpoint);
    }
    
    public void testHalfOpenToCloseTransitionAsync() throws Exception {
        String endpoint = "direct:start-async";
        halfOpenToCloseTransition(endpoint);
    }

    private void halfOpenToCloseTransition(String endpoint) throws Exception {
        expectsMessageCount(2, result);

        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new MyCustomException());
            }
        });

        Exchange exchangeOne = sendMessage(endpoint, "message one");
        Exchange exchangeTwo = sendMessage(endpoint, "message two");
        Exchange exchangeThree = sendMessage(endpoint, "message three");
        assertMockEndpointsSatisfied();
        Thread.sleep(1000);
        result.reset();
        
        expectsMessageCount(2, result);
        Exchange exchangeFour = sendMessage(endpoint, "message four");
        Exchange exchangeFive = sendMessage(endpoint, "message five");
        assertMockEndpointsSatisfied();
        
        assertTrue(exchangeOne.getException() instanceof MyCustomException);
        assertTrue(exchangeTwo.getException() instanceof MyCustomException);
        assertTrue(exchangeThree.getException() instanceof RejectedExecutionException);
        assertTrue(exchangeFour.getException() == null);
        assertTrue(exchangeFive.getException() == null);
    }

    public void testHalfOpenCircuitClosesAfterTimeoutSync() throws Exception {
        String endpoint = "direct:start";
        halfOpenCircuitClosesAfterTimeout(endpoint);
    }
    
    public void testHalfOpenCircuitClosesAfterTimeoutAsync() throws Exception {
        String endpoint = "direct:start-async";
        halfOpenCircuitClosesAfterTimeout(endpoint);
    }

    private void halfOpenCircuitClosesAfterTimeout(String endpoint) throws InterruptedException, Exception {
        expectsMessageCount(2, result);
        result.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new MyCustomException());
            }
        });

        sendMessage(endpoint, "message one");
        sendMessage(endpoint, "message two");
        sendMessage(endpoint, "message three");
        assertMockEndpointsSatisfied();

        result.reset();
        expectsMessageCount(1, result);

        Thread.sleep(1000);
        sendMessage(endpoint, "message four");
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

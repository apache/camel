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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.Before;
import org.junit.Test;

public class IdempotentConsumerAsyncTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200)).threads().to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceivedInAnyOrder("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailedExchangesNotAddedDeadLetterChannel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false));

                from("direct:start").idempotentConsumer(header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200)).threads().process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("messageId", String.class);
                        if (id.equals("2")) {
                            throw new IllegalArgumentException("Damn I cannot handle id 2");
                        }
                    }
                }).to("mock:result");
            }
        });
        context.start();

        // we send in 2 messages with id 2 that fails
        getMockEndpoint("mock:error").expectedMessageCount(2);
        resultEndpoint.expectedBodiesReceivedInAnyOrder("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailedExchangesNotAdded() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200)).threads().process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("messageId", String.class);
                        if (id.equals("2")) {
                            throw new IllegalArgumentException("Damn I cannot handle id 2");
                        }
                    }
                }).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceivedInAnyOrder("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    protected void sendMessage(final Object messageId, final Object body) throws Exception {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });

        // must sleep a little as the route is async and we can be to fast
        Thread.sleep(50);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

}

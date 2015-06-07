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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;

/**
 * @version 
 */
public class IdempotentConsumerTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(
                        header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200)
                ).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    public void testNotSkiDuplicate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                IdempotentRepository<String> repo = MemoryIdempotentRepository.memoryIdempotentRepository(200);

                from("direct:start")
                    .idempotentConsumer(header("messageId")).messageIdRepository(repo).skipDuplicate(false)
                    .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "two", "one", "two", "one", "three");
        resultEndpoint.message(0).exchangeProperty(Exchange.DUPLICATE_MESSAGE).isNull();
        resultEndpoint.message(1).exchangeProperty(Exchange.DUPLICATE_MESSAGE).isNull();
        resultEndpoint.message(2).exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(Boolean.TRUE);
        resultEndpoint.message(3).exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(Boolean.TRUE);
        resultEndpoint.message(4).exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(Boolean.TRUE);
        resultEndpoint.message(5).exchangeProperty(Exchange.DUPLICATE_MESSAGE).isNull();

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    public void testNotSkiDuplicateWithFilter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                IdempotentRepository<String> repo = MemoryIdempotentRepository.memoryIdempotentRepository(200);

                // START SNIPPET: e1
                from("direct:start")
                    // instruct idempotent consumer to not skip duplicates as we will filter then our self
                    .idempotentConsumer(header("messageId")).messageIdRepository(repo).skipDuplicate(false)
                    .filter(property(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                        // filter out duplicate messages by sending them to someplace else and then stop
                        .to("mock:duplicate")
                        .stop()
                    .end()
                    // and here we process only new messages (no duplicates)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        getMockEndpoint("mock:duplicate").expectedBodiesReceived("one", "two", "one");
        getMockEndpoint("mock:duplicate").allMessages().exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(Boolean.TRUE);

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    public void testFailedExchangesNotAddedDeadLetterChannel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false));

                from("direct:start").idempotentConsumer(
                        header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200)
                ).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("messageId", String.class);
                        if (id.equals("2")) {
                            throw new IllegalArgumentException("Damm I cannot handle id 2");
                        }
                    }
                }).to("mock:result");
            }
        });
        context.start();

        // we send in 2 messages with id 2 that fails
        getMockEndpoint("mock:error").expectedMessageCount(2);
        resultEndpoint.expectedBodiesReceived("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    public void testFailedExchangesNotAddedDeadLetterChannelNotHandled() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false));

                from("direct:start").idempotentConsumer(
                    header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200)
                ).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("messageId", String.class);
                        if (id.equals("2")) {
                            throw new IllegalArgumentException("Damm I cannot handle id 2");
                        }
                    }
                }).to("mock:result");
            }
        });
        context.start();

        // we send in 2 messages with id 2 that fails
        getMockEndpoint("mock:error").expectedMessageCount(2);
        resultEndpoint.expectedBodiesReceived("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    public void testFailedExchangesNotAdded() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use default error handler
                errorHandler(defaultErrorHandler());

                from("direct:start").idempotentConsumer(
                        header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200)
                ).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("messageId", String.class);
                        if (id.equals("2")) {
                            throw new IllegalArgumentException("Damm I cannot handle id 2");
                        }
                    }
                }).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    protected void sendMessage(final Object messageId, final Object body) {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

}

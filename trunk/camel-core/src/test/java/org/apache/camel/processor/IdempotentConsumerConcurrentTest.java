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

/**
 * Concurreny test for idempotent consumer
 *
 * @version 
 */
public class IdempotentConsumerConcurrentTest extends ContextTestSupport {
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
                from("direct:start").idempotentConsumer(header("messageId"),
                        MemoryIdempotentRepository.memoryIdempotentRepository(200)).to("mock:result");
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

    public void testFailedExchangesNotAddedDeadLetterChannel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false));

                from("direct:start").idempotentConsumer(header("messageId"),
                        MemoryIdempotentRepository.memoryIdempotentRepository(200))
                        .process(new Processor() {
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
                from("direct:start").idempotentConsumer(header("messageId"),
                        MemoryIdempotentRepository.memoryIdempotentRepository(200))
                        .process(new Processor() {
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

    /**
     * A multithreaded test for IdempotentConsumer filter
     */
    public void testThreadedIdempotentConsumer() throws Exception {
        final int loopCount = 100;
        final int threadCount = 10;

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(header("messageId"),
                        MemoryIdempotentRepository.memoryIdempotentRepository(200))
                        .delay(1).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.reset();
        resultEndpoint.expectedMessageCount(loopCount);

        final boolean failedFlag[] = new boolean[1];
        failedFlag[0] = false;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[threadIndex] = new Thread() {
                @Override
                public void run() {
                    try {
                        for (int j = 0; j < loopCount; j++) {
                            sendMessage("" + j, "multithreadedTest" + j);
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        failedFlag[0] = true;
                    }
                }
            };
            threads[i].start();
        }
        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }
        assertFalse("At least one thread threw an exception", failedFlag[0]);

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

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
 * @version
 */
public class IdempotentConsumerRemoveOnFailureTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    public void testFailedExchangesNotRemoved() throws Exception {
        resultEndpoint.expectedBodiesReceived("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200))
                        // in case of a failure we still want the message to be regarded as a duplicate, so we set the option to false
                        .removeOnFailure(false)
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String id = exchange.getIn().getHeader("messageId", String.class);
                                if (id.equals("2")) {
                                    throw new IllegalArgumentException("Damn I cannot handle id 2");
                                }
                            }
                        }).to("mock:result");
            }
        };
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
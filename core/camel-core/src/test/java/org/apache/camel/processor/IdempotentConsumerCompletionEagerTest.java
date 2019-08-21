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
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.Before;
import org.junit.Test;

public class IdempotentConsumerCompletionEagerTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;
    protected MockEndpoint a;
    protected MockEndpoint b;
    protected MockEndpoint dead;
    protected IdempotentRepository repo;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testCompletionEager() throws Exception {
        repo = MemoryIdempotentRepository.memoryIdempotentRepository(200);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").idempotentConsumer(header("messageId"), repo).completionEager(true).to("log:a", "mock:a").to("log:b", "mock:b").end()
                    .filter(simple("${header.messageId} == '2'")).throwException(new IllegalArgumentException("Forced")).end().to("log:result", "mock:result");
            }
        });
        context.start();

        // we are on block only scope as "two" was success in the block, and
        // then "two" failed afterwards does not matter
        // the idempotent consumer will not receive "two" again
        a.expectedBodiesReceived("one", "two", "three");
        b.expectedBodiesReceived("one", "two", "three");
        dead.expectedBodiesReceived("two", "two");
        resultEndpoint.expectedBodiesReceived("one", "one", "one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNotCompletionEager() throws Exception {
        repo = MemoryIdempotentRepository.memoryIdempotentRepository(200);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").idempotentConsumer(header("messageId"), repo).completionEager(false).to("log:a", "mock:a").to("log:b", "mock:b").end()
                    .filter(simple("${header.messageId} == '2'")).throwException(new IllegalArgumentException("Forced")).end().to("log:result", "mock:result");
            }
        });
        context.start();

        // we are on completion scope so the "two" will rollback and therefore
        // the idempotent consumer receives those again
        a.expectedBodiesReceived("one", "two", "two", "three");
        b.expectedBodiesReceived("one", "two", "two", "three");
        dead.expectedBodiesReceived("two", "two");
        resultEndpoint.expectedBodiesReceived("one", "one", "one", "three");

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
    @Before
    public void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
        a = getMockEndpoint("mock:a");
        b = getMockEndpoint("mock:b");
        dead = getMockEndpoint("mock:dead");
    }

}

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
package org.apache.camel.processor.async;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

/**
 * @version 
 */
public class AsyncEndpointIdempotentConsumerTest extends ContextTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;

    public void testAsyncEndpoint() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("A", "B", "C");

        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedBodiesReceived("Bye Camel", "Bye Camel");
        after.message(0).header("myId").isEqualTo(123);
        after.message(1).header("myId").isEqualTo(456);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Bye Camel", "Bye Camel");
        result.message(0).header("myId").isEqualTo(123);
        result.message(1).header("myId").isEqualTo(456);

        template.sendBodyAndHeader("direct:start", "A", "myId", 123);
        template.sendBodyAndHeader("direct:start", "B", "myId", 123);
        template.sendBodyAndHeader("direct:start", "C", "myId", 456);

        assertMockEndpointsSatisfied();

        assertFalse("Should use different threads", beforeThreadName.equalsIgnoreCase(afterThreadName));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                        .to("mock:before")
                        .to("log:before")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                beforeThreadName = Thread.currentThread().getName();
                            }
                        })
                        .idempotentConsumer(header("myId"), MemoryIdempotentRepository.memoryIdempotentRepository(200))
                        .to("async:bye:camel")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                afterThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("log:after")
                        .to("mock:after")
                        .to("mock:result");
            }
        };
    }

}
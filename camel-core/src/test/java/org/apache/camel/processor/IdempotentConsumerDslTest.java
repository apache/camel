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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

public class IdempotentConsumerDslTest extends ContextTestSupport {

    public void testDuplicateMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("one", "two", "three");

        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "three", "messageId", "3");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .idempotentConsumer()
                        .message(m -> m.getHeader("messageId"))
                        .messageIdRepository(MemoryIdempotentRepository.memoryIdempotentRepository(200))
                    .to("mock:result");
            }
        };
    }
}

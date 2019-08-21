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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.Test;

public class IdempotentConsumerRepoExceptionTest extends ContextTestSupport {

    private IdempotentRepository myRepo = new MyRepo();

    @Test
    public void testRepoException() throws Exception {
        getMockEndpoint("mock:dead").expectedBodiesReceived("nineninenine");
        getMockEndpoint("mock:result").expectedBodiesReceived("one", "two", "three");

        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "nineninenine", "messageId", "999");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "three", "messageId", "3");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").idempotentConsumer(header("messageId"), myRepo).to("mock:result");

            }
        };
    }

    private class MyRepo extends MemoryIdempotentRepository {
        @Override
        public boolean add(String key) {
            if ("999".equals(key)) {
                throw new IllegalArgumentException("Forced");
            }
            return super.add(key);
        }
    }
}

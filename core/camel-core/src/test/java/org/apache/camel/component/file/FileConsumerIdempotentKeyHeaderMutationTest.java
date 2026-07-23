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
package org.apache.camel.component.file;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * Test that mutating CamelFileName in a route does not break idempotent key evaluation at completion time. Before the
 * fix, rollback would remove the wrong key (computed from mutated headers), leaving the eagerly-added poll-time key
 * orphaned so the file was never retried.
 */
public class FileConsumerIdempotentKeyHeaderMutationTest extends ContextTestSupport {

    private static final String TEST_FILE_NAME = "report-" + UUID.randomUUID() + ".txt";
    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    public void testIdempotentKeyUnaffectedByHeaderMutation() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fileUri("?idempotent=true&idempotentKey=${file:onlyname}-${file:size}"
                             + "&initialDelay=0&delay=10"))
                        .setHeader(Exchange.FILE_NAME, constant("output.txt"))
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                if (attempts.incrementAndGet() == 1) {
                                    throw new IllegalArgumentException("Simulated failure on first attempt");
                                }
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}

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

import java.nio.file.Files;
import java.time.Duration;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConsumerMoveFailureTest extends ContextTestSupport {

    @Test
    public void testMoveFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri(), "Kaboom", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    assertTrue(Files.exists(testFile(".camel/hello.txt")), "hello.txt should have been moved to .camel/");
                    assertEquals("Hello World", Files.readString(testFile(".camel/hello.txt")));
                    assertTrue(Files.exists(testFile("error/bye-error.txt")), "bye.txt should have been moved to error/");
                    assertEquals("Kaboom", Files.readString(testFile("error/bye-error.txt")));
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=100&delay=100&moveFailed=error/${file:name.noext}-error.txt"))
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                String body = exchange.getIn().getBody(String.class);
                                if ("Kaboom".equals(body)) {
                                    throw new IllegalArgumentException("Forced");
                                }
                            }
                        }).convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}

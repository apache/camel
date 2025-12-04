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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FileConsumerPreMoveNoopTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "hello." + UUID.randomUUID() + ".txt";

    @Test
    public void testPreMoveNoop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        assertTrue(Files.exists(testFile("work/" + TEST_FILE_NAME)), "Pre move file should exist");
    }

    @Test
    public void testPreMoveNoopSameFileTwice() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesWaitTime();

        // reset and drop the same file again
        mock.reset();
        oneExchangeDone.reset();
        mock.expectedBodiesReceived("Hello Again World");

        template.sendBodyAndHeader(fileUri(), "Hello Again World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesWaitTime();

        assertTrue(Files.exists(testFile("work/" + TEST_FILE_NAME)), "Pre move file should exist");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?preMove=work&noop=true&idempotent=false&initialDelay=0&delay=10"))
                        .process(new MyPreMoveCheckerProcessor())
                        .to("mock:result");
            }
        };
    }

    public class MyPreMoveCheckerProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {
            assertTrue(Files.exists(testFile("work/" + TEST_FILE_NAME)), "Pre move file should exist");
        }
    }
}

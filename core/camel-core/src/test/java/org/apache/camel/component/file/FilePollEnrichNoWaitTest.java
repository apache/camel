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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class FilePollEnrichNoWaitTest extends ContextTestSupport {

    @BeforeEach
    public void sendMessage() {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
    }

    @Test
    public void testFilePollEnrichNoWait() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedFileExists(testFile("done/hello.txt"));

        oneExchangeDone.matchesWaitTime();
        mock.assertIsSatisfied(Duration.ofSeconds(2).toMillis());

        // file should be moved
        assertFalse(Files.exists(testFile("hello.txt")), "File should have been moved");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?delay=0&period=10").routeId("foo").log("Trigger timer foo")
                        // use 0 as timeout for no wait
                        .pollEnrich(fileUri("?initialDelay=0&delay=10&move=done"), 0)
                        .convertBodyTo(String.class).filter(body().isNull()).stop().end()
                        .log("Polled filed ${file:name}").to("mock:result");
            }
        };
    }
}

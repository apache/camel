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
import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test for consuming the same filename only.
 */
public class FileConsumeFilesAndDeleteTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME_1 = "report" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_2 = "report2" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_3 = "report2008" + UUID.randomUUID() + ".txt";

    @Test
    public void testConsumeAndDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, TEST_FILE_NAME_2);
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME_1);
        template.sendBodyAndHeader(fileUri() + "/2008", "2008 Report", Exchange.FILE_NAME, TEST_FILE_NAME_3);

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        // file should not exists
        assertFalse(Files.exists(testFile(TEST_FILE_NAME_1)), "File should been deleted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&fileName=" + TEST_FILE_NAME_1 + "&delete=true"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}

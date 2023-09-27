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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test for consuming hidden dirs.
 */
public class FileConsumeHiddenDirsTest extends ContextTestSupport {

    @Test
    public void testConsumeHiddenDirs() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Report 123", "Report 456");

        template.sendBodyAndHeader(fileUri(".hidden"), "Report 123", Exchange.FILE_NAME, "report1.txt");
        template.sendBodyAndHeader(fileUri("obvious"), "Report 456", Exchange.FILE_NAME, "report2.txt");

        assertMockEndpointsSatisfied();

        Awaitility.await().untilAsserted(() -> {
            // file should be deleted
            assertFalse(Files.exists(testFile(".hidden/report1.txt")), "File should been deleted");
            assertFalse(Files.exists(testFile("obvious/report2.txt")), "File should been deleted");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10&delete=true&includeHiddenDirs=true&recursive=true"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}

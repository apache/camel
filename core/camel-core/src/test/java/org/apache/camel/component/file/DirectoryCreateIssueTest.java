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
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryCreateIssueTest extends ContextTestSupport {

    private final int numFiles = 10;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String[] destinations = new String[numFiles];
                for (int i = 0; i < numFiles; i++) {
                    destinations[i] = "direct:file" + i;

                    from("direct:file" + i).setHeader(Exchange.FILE_NAME, constant("file" + i + ".txt"))
                            .to(fileUri("a/b/c/d/e/f/g/h/?fileExist=Override&noop=true"), "mock:result");
                }

                from("seda:testFileCreatedAsDir").to(destinations);
            }
        };
    }

    @Test
    public void testFileCreatedAsDir() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(numFiles);
        template.send("seda:testFileCreatedAsDir", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("Contents of test file");
            }
        });
        assertMockEndpointsSatisfied();

        // wait a little while for the files to settle down
        Awaitility.await().pollDelay(50, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            for (int i = 0; i < numFiles; i++) {
                assertTrue(Files.isRegularFile(testFile("a/b/c/d/e/f/g/h/file" + i + ".txt")));
            }
        });
    }

}

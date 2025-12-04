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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Isolated
public class FileConcurrentWriteAppendSameFileTest extends ContextTestSupport {
    private static final String APPENDED_TEXT_STATUS_OK = ":Status=OK";
    private static final String TEST_FILE_NAME = "input" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_RESULT = "result" + UUID.randomUUID() + ".txt";
    private static final Logger LOG = LoggerFactory.getLogger(FileConcurrentWriteAppendSameFileTest.class);

    private final int size = 100;
    private String data;

    @BeforeEach
    void setUpData() {
        // create file with many lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("Line ").append(i).append(LS);
        }

        data = sb.toString();
    }

    @Test
    public void testConcurrentAppend() throws Exception {
        template.sendBodyAndHeader(fileUri(), data, Exchange.FILE_NAME, TEST_FILE_NAME);

        // start route
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(size);
        mock.expectsNoDuplicates(body());
        mock.setResultWaitTime(30000);

        context.getRouteController().startRoute("foo");

        // we need to wait a bit for our slow CI server to make sure the entire
        // file is written on disc
        List<String> expectedLines = data.lines()
                .map(line -> {
                    return line + APPENDED_TEXT_STATUS_OK;
                })
                .collect(Collectors.toList());
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            List<String> actualLines = Files.readString(testFile("outbox/" + TEST_FILE_NAME_RESULT))
                    .lines()
                    .collect(Collectors.toList());
            assertThat(actualLines).containsExactlyInAnyOrderElementsOf(expectedLines);
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10"))
                        .routeId("foo")
                        .autoStartup(false)
                        .split(body().tokenize(LS))
                        .parallelProcessing()
                        .streaming()
                        .setBody(body().append(APPENDED_TEXT_STATUS_OK).append(LS))
                        .to(fileUri("outbox?fileExist=Append&fileName=" + TEST_FILE_NAME_RESULT))
                        .to("mock:result")
                        .end();
            }
        };
    }
}

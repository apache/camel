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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Isolated
public class FileConcurrentWriteAppendSameFileTest extends ContextTestSupport {
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

    private boolean fileIsOk() {
        final Path path = testFile("outbox/result.txt");
        if (Files.exists(path)) {
            try {
                final long expectedSize = 1790;

                return Files.size(path) == expectedSize;
            } catch (IOException e) {
                LOG.error("IOException: {}", e.getMessage(), e);
            }
        }

        return false;
    }

    @Test
    public void testConcurrentAppend() throws Exception {
        template.sendBodyAndHeader(fileUri(), data, Exchange.FILE_NAME, "input.txt");

        // start route
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(size);
        mock.expectsNoDuplicates(body());
        mock.setResultWaitTime(30000);

        context.getRouteController().startRoute("foo");

        // we need to wait a bit for our slow CI server to make sure the entire
        // file is written on disc
        Awaitility.await().atMost(500, TimeUnit.MILLISECONDS).until(this::fileIsOk);

        assertMockEndpointsSatisfied();

        // check the file has correct number of lines
        String txt = new String(Files.readAllBytes(testFile("outbox/result.txt")));
        assertNotNull(txt);

        String[] lines = txt.split(LS);
        assertEquals(size, lines.length, "Should be " + size + " lines");

        // should be unique
        Set<String> rows = new LinkedHashSet<>(Arrays.asList(lines));
        assertEquals(size, rows.size(), "Should be " + size + " unique lines");

        log.info(txt);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10")).routeId("foo").noAutoStartup()
                        .split(body().tokenize(LS)).parallelProcessing().streaming()
                        .setBody(body().append(":Status=OK").append(LS))
                        .to(fileUri("outbox?fileExist=Append&fileName=result.txt")).to("mock:result").end();
            }
        };
    }
}

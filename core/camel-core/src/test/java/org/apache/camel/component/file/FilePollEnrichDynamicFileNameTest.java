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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Poll enrich with a dynamic fileName returns the content of the file named by each message.
 */
public class FilePollEnrichDynamicFileNameTest extends ContextTestSupport {

    private static final int ROUNDS = 20;
    private static final int FILES = 40;
    private static final int THREADS = 20;

    @Test
    public void testPollEnrichDynamicFileName() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<String> mismatches = new ArrayList<>();
        try {
            for (int round = 0; round < ROUNDS; round++) {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < FILES; i++) {
                    String name = "r" + round + "-f" + i + ".txt";
                    Files.writeString(testDirectory().resolve(name), "content of " + name);
                    names.add(name);
                }

                List<Callable<String>> calls = new ArrayList<>();
                for (String name : names) {
                    calls.add(() -> {
                        String body = template.requestBodyAndHeader("direct:start", null, "docName", name, String.class);
                        return ("content of " + name).equals(body) ? null : name + " received: " + body;
                    });
                }
                for (Future<String> future : executor.invokeAll(calls)) {
                    String mismatch = future.get();
                    if (mismatch != null) {
                        mismatches.add(mismatch);
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(List.of(), mismatches);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .pollEnrich().simple(fileUri("?noop=true&fileName=${header.docName}")).timeout(5000)
                        .convertBodyTo(String.class);
            }
        };
    }
}

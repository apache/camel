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

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileConsumerThreadsInProgressIssueTest extends ContextTestSupport {

    private final Map<String, Integer> duplicate = new HashMap<>();
    private final SampleProcessor processor = new SampleProcessor(duplicate);

    private int number = 2000;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?sortBy=file:name&delay=10&synchronous=false")).routeId("myRoute")
                        .noAutoStartup().threads(1, 10).maxQueueSize(0)
                        .convertBodyTo(String.class).process(processor).to("log:done", "mock:done");
            }
        };
    }

    @Test
    public void testFileConsumerThreadsInProgressIssue() throws Exception {
        // give longer timeout for stopping
        context.getShutdownStrategy().setTimeout(180);

        MockEndpoint mock = getMockEndpoint("mock:done");
        mock.expectedMessageCount(number);
        mock.expectsNoDuplicates(body());

        createManyFiles(number);

        context.getRouteController().startRoute("myRoute");

        mock.setResultWaitTime(180 * 1000);
        mock.assertIsSatisfied();

        context.stop();

        int found = 0;
        log.info("=====================");
        log.info("Printing duplicates");
        for (Map.Entry<String, Integer> ent : duplicate.entrySet()) {
            Integer count = ent.getValue();
            if (count > 1) {
                found++;
                log.info("{} :: {}", ent.getKey(), count);
            }
        }

        assertEquals(0, found, "Should not contain duplicates");
    }

    private void createManyFiles(int number) throws Exception {
        Path dir = testDirectory();
        for (int i = 0; i < number; i++) {
            String fileNamesSuffix = String.format("%04d", i);
            String pad = String.format("%04d%n", i);
            try (Writer writer = Files.newBufferedWriter(dir.resolve("newFile-" + fileNamesSuffix))) {
                writer.write(pad);
            }
        }
    }

    private class SampleProcessor implements Processor {
        private Map<String, Integer> duplicate;

        public SampleProcessor(Map<String, Integer> duplicate) {
            this.duplicate = duplicate;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            Integer integer = duplicate.get(exchange.getExchangeId());
            if (integer == null) {
                duplicate.put(exchange.getExchangeId(), 1);
            } else {
                integer++;
                duplicate.put(exchange.getExchangeId(), integer);
            }
            log.info("Process called for-{}", exchange.getExchangeId());
            Thread.sleep(20);
        }

    }
}

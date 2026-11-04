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
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for CAMEL-25778: a file consumer can route a file twice when the exchange is committed on a different
 * thread.
 * <p/>
 * The race: the poll thread lists the directory; another thread commits (moves the file to .camel and removes it from
 * the in-progress repository); the poll thread's in-progress check now passes and routes the already-moved file a
 * second time. The fix adds a post-add existence check inside {@code FileConsumer.isValidFile()}.
 * <p/>
 * This test is timing-sensitive: reproduction depends on the poll thread and the commit thread interleaving at the
 * right moment. It serves as a regression guard that catches the race when timing cooperates, but may not reproduce it
 * on every run.
 */
class FileConsumerAsyncCommitRaceTest extends ContextTestSupport {

    /** Counts how many times each file name was processed. */
    private final ConcurrentMap<String, AtomicInteger> processedCount = new ConcurrentHashMap<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // delay=5ms to poll fast; threads() hands exchange to another
                // thread, so the commit happens off the poll thread — exactly the
                // scenario that triggers the CAMEL-25778 race condition.
                // maxQueueSize(0) uses a SynchronousQueue: the poll thread blocks
                // until a worker picks up the exchange, maximising thread interleaving.
                from(fileUri("?sortBy=file:name&delay=5&synchronous=false"))
                        .routeId("myRoute")
                        .autoStartup(false)
                        .threads(1, 10)
                        .maxQueueSize(0)
                        .process(new CountingProcessor(processedCount))
                        .to("mock:done");
            }
        };
    }

    @Test
    void testNoFilesRoutedTwiceWithAsyncCommit() throws Exception {
        int fileCount = 50;
        MockEndpoint mock = getMockEndpoint("mock:done");
        mock.expectedMessageCount(fileCount);
        mock.setResultWaitTime(60_000);

        createFiles(fileCount);
        context.getRouteController().startRoute("myRoute");

        mock.assertIsSatisfied();

        // Verify that no file was processed more than once
        int duplicates = 0;
        for (var entry : processedCount.entrySet()) {
            int count = entry.getValue().get();
            if (count > 1) {
                duplicates++;
                log.warn("File routed {} times: {}", count, entry.getKey());
            }
        }
        assertEquals(0, duplicates, "No file should be routed more than once (CAMEL-25778)");
    }

    private void createFiles(int count) throws Exception {
        Path dir = testDirectory();
        for (int i = 0; i < count; i++) {
            Files.writeString(dir.resolve(String.format("file-%04d.txt", i)), String.format("content-%04d", i));
        }
    }

    private static class CountingProcessor implements Processor {
        private final ConcurrentMap<String, AtomicInteger> processedCount;

        CountingProcessor(ConcurrentMap<String, AtomicInteger> processedCount) {
            this.processedCount = processedCount;
        }

        @Override
        public void process(Exchange exchange) {
            String fileName = exchange.getMessage().getHeader(FileConstants.FILE_NAME, String.class);
            processedCount.computeIfAbsent(fileName, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }
}

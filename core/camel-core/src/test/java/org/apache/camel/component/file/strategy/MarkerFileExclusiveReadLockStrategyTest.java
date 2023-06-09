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
package org.apache.camel.component.file.strategy;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the MarkerFileExclusiveReadLockStrategy in a multi-threaded scenario.
 */
@Isolated
public class MarkerFileExclusiveReadLockStrategyTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MarkerFileExclusiveReadLockStrategyTest.class);
    private static final int NUMBER_OF_THREADS = 5;
    private AtomicInteger numberOfFilesProcessed = new AtomicInteger();
    private CountDownLatch latch = new CountDownLatch(2);

    @Test
    public void testMultithreadedLocking() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedFileExists(testFile("out/file1.dat"));
        mock.expectedFileExists(testFile("out/file2.dat"));

        writeFiles();

        assertMockEndpointsSatisfied();

        String content = new String(Files.readAllBytes(testFile("out/file1.dat")));
        String[] lines = content.split(LS);
        assertEquals(20, lines.length);
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }

        content = new String(Files.readAllBytes(testFile("out/file2.dat")));
        lines = content.split(LS);
        assertEquals(20, lines.length);
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Did not process the messages within 10 seconds");

        assertFileDoesNotExists(testFile("in/file1.dat.camelLock"));
        assertFileDoesNotExists(testFile("in/file2.dat.camelLock"));

        assertFileDoesNotExists(testFile("in/file1.dat"));
        assertFileDoesNotExists(testFile("in/file2.dat"));

        assertEquals(2, this.numberOfFilesProcessed.get());
    }

    private void writeFiles() throws Exception {
        LOG.debug("Writing files...");

        try (OutputStream fos = Files.newOutputStream(testFile("in/file1.dat"));
             OutputStream fos2 = Files.newOutputStream(testFile("in/file2.dat"))) {
            for (int i = 0; i < 20; i++) {
                fos.write(("Line " + i + LS).getBytes());
                fos2.write(("Line " + i + LS).getBytes());
                LOG.debug("Writing line {}", i);
            }
            fos.flush();
            fos2.flush();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("in?readLock=markerFile&initialDelay=0&delay=10")).onCompletion()
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                numberOfFilesProcessed.addAndGet(1);
                                latch.countDown();
                            }
                        }).end().threads(NUMBER_OF_THREADS).to(fileUri("out"), "mock:result");
            }
        };
    }

    private static void assertFileDoesNotExists(Path file) {
        assertFalse(Files.exists(file),
                "File " + file + " should not exist, it should have been deleted after being processed");
    }

}

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

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests the MarkerFileExclusiveReadLockStrategy in a multi-threaded scenario.
 */
public class MarkerFileExclusiveReadLockStrategyTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MarkerFileExclusiveReadLockStrategyTest.class);
    private static final int NUMBER_OF_THREADS = 5;
    private AtomicInteger numberOfFilesProcessed = new AtomicInteger(0);

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/marker/");
        createDirectory("target/data/marker/in");
        super.setUp();
    }

    @Test
    public void testMultithreadedLocking() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedFileExists("target/data/marker/out/file1.dat");
        mock.expectedFileExists("target/data/marker/out/file2.dat");

        writeFiles();

        assertMockEndpointsSatisfied();

        String content = context.getTypeConverter().convertTo(String.class, new File("target/data/marker/out/file1.dat"));
        String[] lines = content.split(LS);
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }

        content = context.getTypeConverter().convertTo(String.class, new File("target/data/marker/out/file2.dat"));
        lines = content.split(LS);
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }

        waitUntilCompleted();

        assertFileDoesNotExists("target/data/marker/in/file1.dat.camelLock");
        assertFileDoesNotExists("target/data/marker/in/file2.dat.camelLock");

        assertFileDoesNotExists("target/data/marker/in/file1.dat");
        assertFileDoesNotExists("target/data/marker/in/file2.dat");

        assertEquals(2, this.numberOfFilesProcessed.get());
    }

    private void writeFiles() throws Exception {
        LOG.debug("Writing files...");

        FileOutputStream fos = new FileOutputStream("target/data/marker/in/file1.dat");
        FileOutputStream fos2 = new FileOutputStream("target/data/marker/in/file2.dat");
        for (int i = 0; i < 20; i++) {
            fos.write(("Line " + i + LS).getBytes());
            fos2.write(("Line " + i + LS).getBytes());
            LOG.debug("Writing line " + i);
        }

        fos.flush();
        fos.close();
        fos2.flush();
        fos2.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/marker/in?readLock=markerFile&initialDelay=0&delay=10").onCompletion().process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        numberOfFilesProcessed.addAndGet(1);
                    }
                }).end().threads(NUMBER_OF_THREADS).to("file:target/data/marker/out", "mock:result");
            }
        };
    }

    private void waitUntilCompleted() {
        while (this.numberOfFilesProcessed.get() < 2) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private static void assertFileDoesNotExists(String filename) {
        File file = new File(filename);
        assertFalse("File " + filename + " should not exist, it should have been deleted after being processed", file.exists());
    }

}

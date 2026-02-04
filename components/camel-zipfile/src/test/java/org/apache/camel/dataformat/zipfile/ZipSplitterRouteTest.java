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
package org.apache.camel.dataformat.zipfile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class ZipSplitterRouteTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ZipIterator.class);

    private static final File testDirectory = new File("test/in");

    private final AtomicInteger memoryMbBefore = new AtomicInteger(0);
    private final AtomicInteger memoryMbInside = new AtomicInteger(0);

    @Test
    public void testSplitter() throws InterruptedException, IOException {
        File srcFile = new File("src/test/resources/org/apache/camel/dataformat/zipfile/data/resources.zip");
        File testFile = new File(testDirectory, srcFile.getName());

        FileUtils.copyFile(srcFile, testFile);

        MockEndpoint processZipEntry = getMockEndpoint("mock:processZipEntry");
        processZipEntry.expectedBodiesReceivedInAnyOrder("chau", "hi", "hola", "another_chiau", "another_hi");
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test that ZipSplitter doesn't read the whole files in the zip file into memory when Spool is Enabled in the
     * Stream Caching Strategy
     */
    @Test
    public void testSplitterLargeFileWithSpoolEnabled() throws InterruptedException, IOException {
        File testFile = new File(testDirectory, "large1.zip");

        int diff = testSplitterLargeFile(testFile);

        assertThat("Memory spike detected! " + diff + "MB increased.", diff, lessThan(10));
    }

    /**
     * Test that ZipSplitter read the whole files in the zip file into memory when Spool is Disabled in the Stream
     * Caching Strategy
     */
    @Test
    public void testSplitterLargeFileWithoutSpoolEnabled() throws InterruptedException, IOException {
        File testFile = new File(testDirectory, "large2.zip");

        int diff = testSplitterLargeFile(testFile);

        assertThat("Memory spike detected! " + diff + "MB increased.", diff, greaterThan(10));
    }

    private int testSplitterLargeFile(File testFile) throws IOException, FileNotFoundException, InterruptedException {
        String expectedBody = null;

        System.out.println("Generating 50MB test file...");
        try (OutputStream os = new FileOutputStream(testFile);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            zos.putNextEntry(new ZipEntry("test.txt"));
            byte[] chunk = new byte[1024 * 1024];
            Arrays.fill(chunk, (byte) 'A');

            expectedBody = new String(chunk, 0, 20);
            for (int i = 1; i <= 50; i++) {
                zos.write(chunk);
            }
            zos.closeEntry();
            zos.flush();
        }

        MockEndpoint processZipEntry = getMockEndpoint("mock:processZipEntry");
        processZipEntry.expectedBodiesReceivedInAnyOrder(expectedBody);
        MockEndpoint.assertIsSatisfied(context);

        int before = memoryMbBefore.get();
        int inside = memoryMbInside.get();
        int diff = inside - before;

        LOG.info("Memory before {}MB, inside {}MB & diff {}MB", before, inside, diff);

        return diff;
    }

    @Override
    protected void setupResources() {
        if (testDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(testDirectory);
            } catch (IOException e) {
                LOG.warn("Failed to delete test directory: " + testDirectory, e);
            }
        }

        if (!testDirectory.mkdirs()) {
            LOG.warn("Failed to create test directory: {}", testDirectory);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        if ("testSplitterLargeFileWithSpoolEnabled()".equals(contextManagerExtension.getCurrentTestName())) {
            StreamCachingStrategy streamCachingStrategy = context.getStreamCachingStrategy();
            streamCachingStrategy.setSpoolEnabled(true);
        }

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Unzip file and Split it according to FileEntry
                from("file:test/in")
                        .process(exchange -> captureMemory(memoryMbBefore, "BEFORE"))
                        .log("Start processing big file: ${header.CamelFileName}")
                        .split(new ZipSplitter()).streaming()
                        .setBody().message(message -> { // Convert up to 20 bytes of body to string
                            try {
                                InputStream is = message.getBody(InputStream.class);
                                byte buf[] = new byte[20];
                                int bytesRead = is.read(buf);
                                captureMemory(memoryMbInside, "INSIDE");
                                return new String(buf, 0, bytesRead);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to convert body to String", e);
                            }
                        }).to("mock:processZipEntry")
                        .to("log:entry")
                        .end()
                        .log("Done processing big file: ${header.CamelFileName}");
            }
        };
    }

    private void captureMemory(AtomicInteger storage, String logPrefix) {
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        storage.set((int) used);
        LOG.info("{}: {}MB", logPrefix, used);
    }
}

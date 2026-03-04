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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZipSplitterRouteTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ZipIterator.class);

    @TempDir
    static File testDirectory;

    private final AtomicInteger memoryMbBefore = new AtomicInteger(0);
    private final AtomicInteger maxMemoryMbInside = new AtomicInteger(0);

    private static File largeTestFile = null;
    private static Integer largeTestFileSizeMb = null;
    private static String expectedLargeTestFileBody = null;

    private File testSpoolDirectory = null;
    private Set<String> testSpoolDirectoryFileNamesInSplit = new HashSet<>();

    @Test
    @Order(1)
    public void testSplitter() throws InterruptedException, IOException {
        File srcFile = new File("src/test/resources/org/apache/camel/dataformat/zipfile/data/resources.zip");
        File testFile = new File(testDirectory, "in/" + srcFile.getName());

        FileUtils.copyFile(srcFile, testFile);

        MockEndpoint processZipEntry = getMockEndpoint("mock:processZipEntry");
        processZipEntry.expectedBodiesReceivedInAnyOrder("chau", "hi", "hola", "another_chiau", "another_hi");
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test that ZipSplitter read the whole files in the zip file into memory when Spool is Disabled in the Stream
     * Caching Strategy
     */
    @Test
    @Order(2)
    public void testSplitterLargeFileWithoutSpoolEnabled() throws InterruptedException, IOException {
        File testFile = new File(testDirectory, "in/large1.zip");

        memoryMbBefore.set(0);
        maxMemoryMbInside.set(0);

        FileUtils.copyFile(largeTestFile, testFile);

        int diff = testSplitterLargeFile(testFile);

        assertThat("Memory spike NOT detected! " + diff + "MB increased.", diff, greaterThan(largeTestFileSizeMb));
    }

    /**
     * Test that ZipSplitter doesn't read the whole files in the zip file into memory when Spool is Enabled in the
     * Stream Caching Strategy
     */
    @Test
    @Order(3)
    public void testSplitterLargeFileWithSpoolEnabled() throws InterruptedException, IOException {
        File testFile = new File(testDirectory, "in/large2.zip");
        MockEndpoint onCompletion = getMockEndpoint("mock:onCompletion");

        onCompletion.expectedMessageCount(1);

        memoryMbBefore.set(0);
        maxMemoryMbInside.set(0);

        FileUtils.copyFile(largeTestFile, testFile);

        testSpoolDirectoryFileNamesInSplit.clear();

        int diff = testSplitterLargeFile(testFile);

        assertThat("Memory spike detected! " + diff + "MB increased.", diff, lessThan(largeTestFileSizeMb));

        assertThat("No files in SpoolDirectory during split: " + testSpoolDirectory.getPath(),
                testSpoolDirectoryFileNamesInSplit.size(), greaterThan(0));
        assertEquals(0, testSpoolDirectory.list().length,
                "Files in SpoolDirectory after test: " + testSpoolDirectory.getPath());
    }

    private int testSplitterLargeFile(File testFile) throws IOException, FileNotFoundException, InterruptedException {
        MockEndpoint processZipEntry = getMockEndpoint("mock:processZipEntry");
        processZipEntry.expectedBodiesReceivedInAnyOrder(expectedLargeTestFileBody);
        MockEndpoint.assertIsSatisfied(context);

        int before = memoryMbBefore.get();
        int inside = maxMemoryMbInside.get();
        int diff = inside - before;

        LOG.info("Memory before {}MB, inside {}MB & diff {}MB", before, inside, diff);

        return diff;
    }

    @Override
    protected void setupResources() {
        largeTestFile = new File(testDirectory, "large.zip");

        try (OutputStream os = new FileOutputStream(largeTestFile);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            zos.putNextEntry(new ZipEntry("test.txt"));
            byte[] chunk = new byte[5 * 1024];
            int bytesWritten = 0;
            Arrays.fill(chunk, (byte) 'A');

            expectedLargeTestFileBody = new String(chunk, 0, 20);
            for (int i = 1; i <= 1024; i++) {
                zos.write(chunk);
                bytesWritten += chunk.length;
            }
            zos.closeEntry();
            zos.flush();

            largeTestFileSizeMb = bytesWritten / 1024 / 1024;
            LOG.info("Generated large test file with entry of size {}KB", bytesWritten / 1024);
        } catch (IOException e) {
            throw new RuntimeException("Generate large test file", e);
        }

    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        if ("testSplitterLargeFileWithSpoolEnabled()".equals(contextManagerExtension.getCurrentTestName())) {
            StreamCachingStrategy streamCachingStrategy = context.getStreamCachingStrategy();
            streamCachingStrategy.setSpoolEnabled(true);

            testSpoolDirectory = new File(testDirectory, "spool");
            testSpoolDirectory.mkdir();
            streamCachingStrategy.setSpoolDirectory(testSpoolDirectory);
        }

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Unzip file and Split it according to FileEntry
                from("file:"+testDirectory.getPath()+"/in")
                	.onCompletion()
                		.to("mock:onCompletion")
                	.end()
                    .process(exchange -> captureMemory(memoryMbBefore, "BEFORE"))
                    .log("Start processing big file: ${header.CamelFileName}")
                    .split(new ZipSplitter()).streaming()
                    .setBody().message(message -> { // Convert up to 20 bytes of body to string
                        try {
                            InputStream is = message.getBody(InputStream.class);
                                byte buf[] = new byte[20];
                                int bytesRead = is.read(buf);
                                if (20 == bytesRead) { // No need to compare memory for all small files i zip file of "testSplitter()"
                                    captureMemory(maxMemoryMbInside, "INSIDE");
                                }
                                return new String(buf, 0, bytesRead);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to convert body to String", e);
                            } finally {
                                if (null != testSpoolDirectory && testSpoolDirectory.exists()) {
                                    for (String fileName : testSpoolDirectory.list()) {
                                        testSpoolDirectoryFileNamesInSplit.add(fileName);
                                    }
                                }
                            }
                        }).to("mock:processZipEntry")
                        .to("log:entry")
                        .end()
                        .log("Done processing big file: ${header.CamelFileName}");
            }
        };
    }

    private void captureMemory(AtomicInteger storage, String logPrefix) {
        try {
            System.gc();
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // Ignore
        }

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        storage.set(Math.max((int) used, storage.get()));
        LOG.info("{}: {}MB", logPrefix, used);
    }
}

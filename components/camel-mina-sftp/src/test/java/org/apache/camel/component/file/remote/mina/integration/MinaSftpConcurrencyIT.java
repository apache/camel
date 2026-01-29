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
package org.apache.camel.component.file.remote.mina.integration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP concurrency and thread safety.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
@Tag("not-parallel")
public class MinaSftpConcurrencyIT extends MinaSftpServerTestSupport {

    private static final Logger log = LoggerFactory.getLogger(MinaSftpConcurrencyIT.class);

    private static final int THREAD_COUNT = 5;
    private static final int FILES_PER_THREAD = 10;

    private String ftpRootDir;
    private ExecutorService executor;

    @BeforeEach
    public void doPostSetup() {
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
        executor = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @AfterEach
    public void doPostTearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private String baseUri() {
        return "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
               + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";
    }

    @Test
    @Timeout(120)
    public void testConcurrentProducersSameDirectory() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Throwable> errors = new ArrayList<>();

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int f = 0; f < FILES_PER_THREAD; f++) {
                        String fileName = "thread" + threadId + "-file" + f + ".txt";
                        String content = "Content from thread " + threadId + " file " + f;
                        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion
        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "Threads should complete within timeout");

        // Log any errors for debugging
        if (!errors.isEmpty()) {
            for (Throwable e : errors) {
                log.error("Error during concurrent upload: {}", e.getMessage(), e);
            }
        }

        // Verify results
        assertEquals(0, errorCount.get(), "No errors should occur during concurrent uploads");
        assertEquals(THREAD_COUNT * FILES_PER_THREAD, successCount.get(),
                "All files should be uploaded successfully");

        // Verify all files exist with correct content
        for (int t = 0; t < THREAD_COUNT; t++) {
            for (int f = 0; f < FILES_PER_THREAD; f++) {
                File file = ftpFile("thread" + t + "-file" + f + ".txt").toFile();
                assertTrue(file.exists(), "File should exist: " + file.getName());
                String expectedContent = "Content from thread " + t + " file " + f;
                String actualContent = context.getTypeConverter().convertTo(String.class, file);
                assertEquals(expectedContent, actualContent, "Content should match for " + file.getName());
            }
        }
    }

    @Test
    @Timeout(120)
    public void testConcurrentProducersDifferentDirectories() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread writes to its own subdirectory
                    String subDir = "subdir" + threadId;
                    for (int f = 0; f < FILES_PER_THREAD; f++) {
                        String fileName = subDir + "/file" + f + ".txt";
                        String content = "Content from thread " + threadId + " file " + f;
                        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Error in thread {}: {}", threadId, e.getMessage(), e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "Threads should complete within timeout");

        assertEquals(0, errorCount.get(), "No errors should occur");
        assertEquals(THREAD_COUNT * FILES_PER_THREAD, successCount.get());

        // Verify all subdirectories and files exist
        for (int t = 0; t < THREAD_COUNT; t++) {
            File subDir = ftpFile("subdir" + t).toFile();
            assertTrue(subDir.exists() && subDir.isDirectory(),
                    "Subdirectory should exist: subdir" + t);
            for (int f = 0; f < FILES_PER_THREAD; f++) {
                File file = ftpFile("subdir" + t + "/file" + f + ".txt").toFile();
                assertTrue(file.exists(), "File should exist: " + file.getPath());
            }
        }
    }

    @Test
    @Timeout(120)
    public void testConcurrentProducersWithTempFiles() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Use temp file naming to ensure atomic writes
        String uri = baseUri() + "&tempFileName=${file:name}.tmp";

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int f = 0; f < FILES_PER_THREAD; f++) {
                        String fileName = "temp-thread" + threadId + "-file" + f + ".txt";
                        String content = "Temp content from thread " + threadId + " file " + f;
                        template.sendBodyAndHeader(uri, content, Exchange.FILE_NAME, fileName);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Error in thread {}: {}", threadId, e.getMessage(), e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "Threads should complete within timeout");

        assertEquals(0, errorCount.get(), "No errors should occur with temp files");
        assertEquals(THREAD_COUNT * FILES_PER_THREAD, successCount.get());

        // Verify all files exist (temp files should be renamed)
        for (int t = 0; t < THREAD_COUNT; t++) {
            for (int f = 0; f < FILES_PER_THREAD; f++) {
                File file = ftpFile("temp-thread" + t + "-file" + f + ".txt").toFile();
                assertTrue(file.exists(), "File should exist (temp should be renamed): " + file.getName());
                // Verify no .tmp files remain
                File tempFile = ftpFile("temp-thread" + t + "-file" + f + ".txt.tmp").toFile();
                assertTrue(!tempFile.exists(), "Temp file should not remain: " + tempFile.getName());
            }
        }
    }

    @Test
    @Timeout(60)
    public void testRapidConnectionReuse() throws Exception {
        // Perform many rapid sequential operations to verify connection handling
        int operationCount = 50;

        for (int i = 0; i < operationCount; i++) {
            String fileName = "rapid-" + i + ".txt";
            String content = "Rapid content " + i;
            template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);
        }

        // Verify all files exist
        for (int i = 0; i < operationCount; i++) {
            File file = ftpFile("rapid-" + i + ".txt").toFile();
            assertTrue(file.exists(), "File should exist: " + file.getName());
        }
    }

    @Test
    @Timeout(120)
    public void testConcurrentReadAndWrite() throws Exception {
        // Pre-create some files for reading
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader(baseUri(), "Initial content " + i, Exchange.FILE_NAME, "read-" + i + ".txt");
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger writeSuccess = new AtomicInteger(0);
        AtomicInteger readSuccess = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Writer thread
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 20; i++) {
                    template.sendBodyAndHeader(baseUri(), "Write content " + i,
                            Exchange.FILE_NAME, "write-" + i + ".txt");
                    writeSuccess.incrementAndGet();
                    Thread.sleep(50); // Small delay between writes
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("Write error: {}", e.getMessage(), e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Reader thread (reads from the FTP using file component for simplicity)
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    File file = ftpFile("read-" + i + ".txt").toFile();
                    if (file.exists()) {
                        String content = context.getTypeConverter().convertTo(String.class, file);
                        if (content != null && content.startsWith("Initial content")) {
                            readSuccess.incrementAndGet();
                        }
                    }
                    Thread.sleep(100); // Small delay between reads
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("Read error: {}", e.getMessage(), e);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "Operations should complete within timeout");

        assertEquals(0, errorCount.get(), "No errors should occur during concurrent read/write");
        assertEquals(20, writeSuccess.get(), "All writes should succeed");
        assertEquals(10, readSuccess.get(), "All reads should succeed");
    }
}

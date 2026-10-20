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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file producer writes one file name at a time, so that two exchanges that append to the same file do not mix their
 * content. That must still hold when the producer has written many other files while one write was going on.
 */
class FileProducerSameFileLockTest {

    @TempDir
    Path dir;

    private CamelContext context;
    private Producer producer;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        // the context is not started: camel-file is tested without the simple language here
        context = new DefaultCamelContext();
        Endpoint endpoint = context.getEndpoint("file:" + dir.toAbsolutePath() + "?fileExist=Append&bufferSize=4");
        producer = endpoint.createProducer();
        producer.start();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (producer != null) {
            producer.stop();
        }
        context.stop();
    }

    @Test
    void testSameFileIsWrittenOneAtATime() throws Exception {
        assertSecondWriterWaits(10);
    }

    @Test
    void testSameFileIsWrittenOneAtATimeAfterManyOtherFiles() throws Exception {
        // more file names than the 100 the producer used to keep a lock for
        assertSecondWriterWaits(150);
    }

    private void assertSecondWriterWaits(int otherFiles) throws Exception {
        CountDownLatch firstPartWritten = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // a slow writer: it appends AAAA, waits, then appends AAAA again
        Future<Exchange> slow = executor.submit(() -> send("same.txt", new SlowStream(firstPartWritten, release)));
        assertTrue(firstPartWritten.await(10, TimeUnit.SECONDS));

        // meanwhile the producer writes other files
        for (int i = 0; i < otherFiles; i++) {
            Exchange other = send("other-" + i + ".txt", "x");
            assertNull(other.getException());
        }

        // a second writer of the same file must wait until the slow writer is done: it either parks on a lock (the
        // lock of the file name) or, without the lock, completes its write
        FutureTask<Exchange> second = new FutureTask<>(() -> send("same.txt", "BBBB"));
        Thread secondWriter = new Thread(second, "second-writer");
        secondWriter.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!second.isDone() && LockSupport.getBlocker(secondWriter) == null && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        boolean secondDoneWhileFirstWriting = second.isDone();
        assertTrue(secondDoneWhileFirstWriting || LockSupport.getBlocker(secondWriter) != null,
                "the second write of same.txt neither waits nor completes");

        release.countDown();
        assertNull(slow.get(10, TimeUnit.SECONDS).getException());
        assertNull(second.get(10, TimeUnit.SECONDS).getException());
        assertEquals("AAAAAAAABBBB", Files.readString(dir.resolve("same.txt")),
                "the two appends to same.txt are mixed");
        assertFalse(secondDoneWhileFirstWriting, "the second write of same.txt ran while the first one was writing");
    }

    private Exchange send(String fileName, Object body) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.FILE_NAME, fileName);
        exchange.getIn().setBody(body);
        producer.process(exchange);
        return exchange;
    }

    private static final class SlowStream extends InputStream {
        private final CountDownLatch firstPartWritten;
        private final CountDownLatch release;
        private int reads;

        SlowStream(CountDownLatch firstPartWritten, CountDownLatch release) {
            this.firstPartWritten = firstPartWritten;
            this.release = release;
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(byte[] b) {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            reads++;
            if (reads == 2) {
                // the first AAAA has been handed to the file; hold the write here
                firstPartWritten.countDown();
                try {
                    if (!release.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            if (reads > 2) {
                return -1;
            }
            byte[] data = "AAAA".getBytes(StandardCharsets.US_ASCII);
            int n = Math.min(len, data.length);
            System.arraycopy(data, 0, b, off, n);
            return n;
        }
    }
}

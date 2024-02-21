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

package org.apache.camel.component.wal;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class LogWriterRollOverUpdateAsyncBase extends LogTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(LogWriterRollOverUpdateAsyncBase.class);
    protected BlockingQueue<EntryInfo.CachedEntryInfo> entryInfos;
    protected CountDownLatch latch = new CountDownLatch(1);
    LogWriter logWriter;
    File reportFile;

    @BeforeEach
    void setup() throws IOException {
        reportFile = new File(testDir, "test.data");

        logWriter = new LogWriter(reportFile, new DefaultLogSupervisor(100), 100);

    }

    private void markRecordsAsCommitted() {
        for (int i = 0; i < RECORD_COUNT; i++) {

            try {
                LOG.trace("Updating ...");
                final EntryInfo.CachedEntryInfo entryInfo = entryInfos.take();

                logWriter.updateState(entryInfo, LogEntry.EntryState.PROCESSED);
            } catch (IOException | InterruptedException e) {
                LOG.error("Failed to update state: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    abstract void asyncGenerate();

    @AfterEach
    void tearDown() throws IOException {
        logWriter.flush();
        logWriter.close();
        entryInfos.clear();
    }

    protected void runTest(int queueCapacity) throws IOException, InterruptedException {
        entryInfos = new ArrayBlockingQueue<>(queueCapacity);

        final ExecutorService executorService = Executors.newFixedThreadPool(2);

        final Future<?> generateTask = executorService.submit(this::asyncGenerate);

        final Future<?> updateTask = executorService.submit(this::markRecordsAsCommitted);

        Assertions.assertTrue(latch.await(1, TimeUnit.MINUTES), "Failed to generate records within 1 minute");

        try (LogReader reader = new LogReader(reportFile, (int) RECORD_COUNT * 100)) {

            Header fileHeader = reader.getHeader();
            assertEquals(Header.FORMAT_NAME, fileHeader.getFormatName().trim());
            assertEquals(Header.CURRENT_FILE_VERSION, fileHeader.getFileVersion());

            int count = 0;
            PersistedLogEntry entry = reader.readEntry();
            while (entry != null) {
                LOG.debug("Read state: {}", entry.getEntryState());
                assertEquals(LogEntry.EntryState.PROCESSED, entry.getEntryState());
                assertEquals(0, entry.getKeyMetadata());
                assertEquals(0, entry.getValueMetadata());

                String key = new String(entry.getKey());
                LOG.debug("Read record: {}", key);
                Assertions.assertTrue(key.startsWith("record-"));

                ByteBuffer buffer = ByteBuffer.wrap(entry.getValue());

                Assertions.assertTrue(buffer.getLong() > 0);

                count++;

                entry = reader.readEntry();
            }

            Assertions.assertEquals(100, count, "The number of records don't match");
        }
    }

}

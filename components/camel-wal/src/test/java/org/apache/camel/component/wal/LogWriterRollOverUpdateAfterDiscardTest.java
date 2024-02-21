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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogWriterRollOverUpdateAfterDiscardTest extends LogTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(LogWriterRollOverUpdateAfterDiscardTest.class);

    LogWriter logWriter;
    File reportFile;
    final List<EntryInfo.CachedEntryInfo> entryInfos = new ArrayList<>();

    @BeforeEach
    void setup() throws IOException, ExecutionException, InterruptedException {
        reportFile = new File(testDir, "test.data");

        logWriter = new LogWriter(reportFile, new DefaultLogSupervisor(100), 100);

        generateDataFilePredictable(entryInfos::add, logWriter);

        logWriter.flush();

        Executors.newSingleThreadExecutor().submit(this::markRecordsAsCommitted).get();
    }

    private void markRecordsAsCommitted() {
        for (EntryInfo.CachedEntryInfo entryInfo : entryInfos) {
            try {
                logWriter.updateState(entryInfo, LogEntry.EntryState.PROCESSED);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        logWriter.flush();
        logWriter.close();
    }

    @Test
    void testReadWriteUpdateRecordsWithRollOver() throws IOException {
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

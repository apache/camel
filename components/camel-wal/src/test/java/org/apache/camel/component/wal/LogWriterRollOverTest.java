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
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogWriterRollOverTest extends LogTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(LogWriterRollOverTest.class);

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 100, 3599, 3600 })
    public void testReadWriteRecordsWithRollOver(int maxRecordCount) throws IOException {
        readWriteTest(maxRecordCount, maxRecordCount);
    }

    @Test
    public void testReadWriteRecordsWithRollOverDoesNotExceedSize() throws IOException {
        int maxRecordCount = (int) RECORD_COUNT + 1;
        readWriteTest((int) RECORD_COUNT, maxRecordCount);
    }

    private void readWriteTest(int expectedRecordCount, int maxRecordCount) throws IOException {
        File reportFile = new File(testDir, "test.data");

        try (LogWriter logWriter = new LogWriter(reportFile, new DefaultLogSupervisor(100), maxRecordCount)) {
            Assertions.assertDoesNotThrow(() -> generateDataFilePredictable(null, logWriter));
        }

        long total = TimeUnit.HOURS.toSeconds(1);
        try (LogReader reader = new LogReader(reportFile, (int) total * 100)) {

            Header fileHeader = reader.getHeader();
            assertEquals(Header.FORMAT_NAME, fileHeader.getFormatName().trim());
            assertEquals(Header.CURRENT_FILE_VERSION, fileHeader.getFileVersion());

            int count = 0;
            PersistedLogEntry entry = reader.readEntry();
            while (entry != null) {
                assertEquals(LogEntry.EntryState.NEW, entry.getEntryState());
                assertEquals(0, entry.getKeyMetadata());
                assertEquals(0, entry.getValueMetadata());

                String key = new String(entry.getKey());
                LOG.debug("Received record: {}", key);
                Assertions.assertTrue(key.startsWith("record-"));

                ByteBuffer buffer = ByteBuffer.wrap(entry.getValue());

                Assertions.assertTrue(buffer.getLong() > 0);

                count++;

                entry = reader.readEntry();
            }

            Assertions.assertEquals(expectedRecordCount, count, "The number of records don't match");
        }
    }
}

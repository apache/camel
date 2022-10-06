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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogWriterTest extends LogTestBase {

    @Test
    public void testReadWriteRecords() throws IOException {
        final List<Instant> values = Assertions.assertDoesNotThrow(() -> generateDataFilePredictable());
        File reportFile = new File(testDir, "test.data");
        Assumptions.assumeTrue(reportFile.exists());

        long total = TimeUnit.HOURS.toSeconds(1);
        try (LogReader reader = new LogReader(reportFile, (int) total * 100)) {

            Header fileHeader = reader.getHeader();
            assertEquals(Header.FORMAT_NAME, fileHeader.getFormatName().trim());
            assertEquals(Header.CURRENT_FILE_VERSION, fileHeader.getFileVersion());

            int count = 0;
            PersistedLogEntry entry = reader.readEntry();
            while (entry != null) {
                if (entry != null) {
                    assertEquals(LogEntry.EntryState.NEW, entry.getEntryState());
                    assertEquals(0, entry.getKeyMetadata());
                    assertEquals(0, entry.getValueMetadata());

                    String key = new String(entry.getKey());
                    Assertions.assertEquals("record-" + count, key);

                    ByteBuffer buffer = ByteBuffer.wrap(entry.getValue());
                    Assertions.assertEquals(values.get(count).toEpochMilli(), buffer.getLong());

                    count++;
                }

                entry = reader.readEntry();
            }

            Assertions.assertEquals(TimeUnit.HOURS.toSeconds(1), count, "The number of records don't match");
        }
    }

}

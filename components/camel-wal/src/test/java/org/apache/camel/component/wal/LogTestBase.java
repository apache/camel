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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogTestBase {
    protected static final long RECORD_COUNT = TimeUnit.HOURS.toSeconds(1);
    private static final Logger LOG = LoggerFactory.getLogger(LogTestBase.class);

    @TempDir
    protected File testDir;

    protected static LogEntry createNewLogEntry(List<Instant> values, int i) {
        String keyData = "record-" + i;
        ByteBuffer value = ByteBuffer.allocate(Long.BYTES);
        Instant now = Instant.now();
        value.putLong(now.toEpochMilli());

        if (values != null) {
            values.add(now);
        }

        LogEntry entry = new LogEntry(
                LogEntry.EntryState.NEW, 0,
                keyData.getBytes(), 0, value.array());
        return entry;
    }

    protected List<Instant> generateDataFilePredictable(
            Consumer<EntryInfo.CachedEntryInfo> offsetConsumer, LogWriter logWriter, long total)
            throws IOException {
        List<Instant> values = new ArrayList<>();

        LOG.debug("Number of records to write: {}", total);
        for (int i = 0; i < total; i++) {
            LogEntry entry = createNewLogEntry(values, i);

            final EntryInfo.CachedEntryInfo entryInfo = logWriter.append(entry);
            if (offsetConsumer != null) {
                offsetConsumer.accept(entryInfo);
            }
        }

        return values;
    }

    protected List<Instant> generateDataFilePredictable(Consumer<EntryInfo.CachedEntryInfo> offsetConsumer, LogWriter logWriter)
            throws IOException {
        return generateDataFilePredictable(offsetConsumer, logWriter, RECORD_COUNT);
    }

    protected List<Instant> generateDataFilePredictable(Consumer<EntryInfo.CachedEntryInfo> offsetConsumer) throws IOException {
        File reportFile = new File(testDir, "test.data");
        final DefaultLogSupervisor scheduledFlushPolicy = new DefaultLogSupervisor(100);
        try (LogWriter logWriter = new LogWriter(reportFile, scheduledFlushPolicy)) {
            return generateDataFilePredictable(offsetConsumer, logWriter);
        }
    }

    protected List<Instant> generateDataFilePredictable() throws IOException {
        return generateDataFilePredictable(null);
    }

}

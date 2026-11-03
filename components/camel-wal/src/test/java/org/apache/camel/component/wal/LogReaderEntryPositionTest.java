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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The reader gives each entry the position where its record starts in the file, so that
 * {@link LogWriter#updateState(PersistedLogEntry, LogEntry.EntryState)} writes the new state over that record. The
 * records have different sizes, and the reader's buffer is small, so that it reads the file in several parts.
 */
class LogReaderEntryPositionTest {

    @TempDir
    File testDir;

    private static LogEntry entry(String key) {
        return new LogEntry(
                LogEntry.EntryState.NEW, 0, key.getBytes(StandardCharsets.UTF_8), 0,
                ("offset-of-" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static List<PersistedLogEntry> readAll(File file, int capacity) throws Exception {
        List<PersistedLogEntry> entries = new ArrayList<>();
        try (LogReader reader = new LogReader(file, capacity)) {
            PersistedLogEntry entry;
            while ((entry = reader.readEntry()) != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static String describe(List<PersistedLogEntry> entries) {
        List<String> answer = new ArrayList<>();
        for (PersistedLogEntry entry : entries) {
            String key = new String(entry.getKey(), StandardCharsets.UTF_8);
            assertEquals("offset-of-" + key, new String(entry.getValue(), StandardCharsets.UTF_8),
                    "value of record " + key + " is corrupted");
            answer.add(key + "=" + entry.getEntryState());
        }
        return answer.toString();
    }

    @Test
    void entriesHaveThePositionOfTheirRecord() throws Exception {
        File file = new File(testDir, "wal.data");
        List<Long> positions = new ArrayList<>();
        try (LogWriter writer = new LogWriter(file, new DefaultLogSupervisor(100_000))) {
            for (String key : List.of("a", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cc", "dddddddddddddddd", "e")) {
                positions.add(writer.append(entry(key)).getPosition());
            }
        }

        // 64 bytes: the buffer holds one or two of the shorter records at a time, and the 94 bytes record of "bbb..."
        // spans two reads of the buffer
        List<PersistedLogEntry> entries = readAll(file, 64);
        List<Long> read = new ArrayList<>();
        for (PersistedLogEntry entry : entries) {
            read.add(entry.getEntryInfo().getPosition());
        }
        assertEquals(positions, read);
    }

    @Test
    void updateOfAReadEntryChangesThatRecordOnly() throws Exception {
        File file = new File(testDir, "wal.data");
        try (LogWriter writer = new LogWriter(file, new DefaultLogSupervisor(100_000))) {
            for (String key : List.of("a", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cc", "dddddddddddddddd")) {
                writer.append(entry(key));
            }
            writer.flush();

            List<PersistedLogEntry> entries = readAll(file, 64);
            writer.updateState(entries.get(1), LogEntry.EntryState.PROCESSED);
            writer.updateState(entries.get(2), LogEntry.EntryState.FAILED);
            writer.flush();
        }

        assertEquals("[a=NEW, bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=PROCESSED, cc=FAILED, dddddddddddddddd=NEW]",
                describe(readAll(file, LogReader.DEFAULT_CAPACITY)));
    }
}

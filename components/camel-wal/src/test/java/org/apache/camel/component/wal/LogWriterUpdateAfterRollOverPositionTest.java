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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * After a roll-over, the entries of the previous layer that are still in the in-memory transaction log are written
 * again after the new ones. An update of such an entry must change the state of that entry only, wherever it is in the
 * file now, and the file must stay readable. The records have different sizes (keys of different lengths), as in real
 * use.
 */
class LogWriterUpdateAfterRollOverPositionTest {

    @TempDir
    File testDir;

    private static LogEntry entry(String key) {
        return new LogEntry(
                LogEntry.EntryState.NEW, 0, key.getBytes(StandardCharsets.UTF_8), 0,
                ("offset-of-" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, LogEntry.EntryState> readAll(File file) throws Exception {
        Map<String, LogEntry.EntryState> answer = new LinkedHashMap<>();
        try (LogReader reader = new LogReader(file)) {
            PersistedLogEntry e = reader.readEntry();
            while (e != null) {
                String key = new String(e.getKey(), StandardCharsets.UTF_8);
                assertEquals("offset-of-" + key, new String(e.getValue(), StandardCharsets.UTF_8),
                        "value of record " + key + " is corrupted");
                answer.put(key, e.getEntryState());
                e = reader.readEntry();
            }
        }
        return answer;
    }

    @Test
    void updateOfPreviousLayerEntryAfterRollOverKeepsFileConsistent() throws Exception {
        File file = new File(testDir, "wal.data");
        List<EntryInfo.CachedEntryInfo> infos = new ArrayList<>();
        try (LogWriter writer = new LogWriter(file, new DefaultLogSupervisor(100_000), 3)) {
            infos.add(writer.append(entry("a")));
            infos.add(writer.append(entry("bb")));
            infos.add(writer.append(entry("ccc")));
            // roll-over: "a" is replaced by a longer record, "bb" and "ccc" are written again after it
            infos.add(writer.append(entry("dddddddddddddddd")));

            // "ccc" (layer 0, index 2) is still in the transaction log, its processing completes now
            writer.updateState(infos.get(2), LogEntry.EntryState.PROCESSED);
            writer.flush();
        }

        assertEquals("{dddddddddddddddd=NEW, bb=NEW, ccc=PROCESSED}", readAll(file).toString());
    }

    @Test
    void updateOfTheNextEntryToReplaceAfterRollOverIsKept() throws Exception {
        File file = new File(testDir, "wal.data");
        List<EntryInfo.CachedEntryInfo> infos = new ArrayList<>();
        try (LogWriter writer = new LogWriter(file, new DefaultLogSupervisor(100_000), 3)) {
            infos.add(writer.append(entry("aa")));
            infos.add(writer.append(entry("bb")));
            infos.add(writer.append(entry("cc")));
            infos.add(writer.append(entry("dd")));

            // "bb" (layer 0, index 1) is the next entry the roll-over replaces, it is still in the transaction log
            writer.updateState(infos.get(1), LogEntry.EntryState.PROCESSED);
            writer.flush();
        }

        assertEquals("{dd=NEW, bb=PROCESSED, cc=NEW}", readAll(file).toString());
    }

    @Test
    void appendOfAShorterRecordAfterRollOverKeepsFileReadable() throws Exception {
        File file = new File(testDir, "wal.data");
        try (LogWriter writer = new LogWriter(file, new DefaultLogSupervisor(100_000), 3)) {
            writer.append(entry("aaaa"));
            writer.append(entry("bbbbbbbbbbbb"));
            writer.append(entry("cc"));
            // roll-over: "d" replaces "aaaa"
            writer.append(entry("d"));
            // "e" replaces the longer "bbbbbbbbbbbb", so the file gets shorter
            writer.append(entry("e"));
            writer.flush();
        }

        assertEquals("{d=NEW, e=NEW, cc=NEW}", readAll(file).toString());
    }

    @Test
    void updateOfEntryTwoLayersBehindDoesNotTouchNewerEntry() throws Exception {
        File file = new File(testDir, "wal.data");
        List<EntryInfo.CachedEntryInfo> infos = new ArrayList<>();
        try (LogWriter writer = new LogWriter(file, new DefaultLogSupervisor(100_000), 3)) {
            for (String key : List.of("a", "b", "c", "d", "e", "f", "g")) {
                infos.add(writer.append(entry(key)));
            }

            // "c" (layer 0, index 2) was replaced by "f" (layer 1, index 2): its late update must be discarded
            writer.updateState(infos.get(2), LogEntry.EntryState.PROCESSED);
            writer.flush();
        }

        assertEquals("{g=NEW, e=NEW, f=NEW}", readAll(file).toString());
    }
}

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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransactionLogTest {

    private static LogEntry entry(String key) {
        return new LogEntry(LogEntry.EntryState.NEW, 0, key.getBytes(StandardCharsets.UTF_8), 0, new byte[] { 1 });
    }

    private static TransactionLog.EntryContainer[] add(TransactionLog log, int count) {
        TransactionLog.EntryContainer[] containers = new TransactionLog.EntryContainer[count];
        for (int i = 0; i < count; i++) {
            containers[i] = log.add(entry("record-" + i));
        }
        return containers;
    }

    @DisplayName("Tests that can update records on the same layer")
    @Test
    void testCanUpdateSameLayer() {
        TransactionLog log = new TransactionLog(10);
        TransactionLog.EntryContainer[] containers = add(log, 3);

        assertSame(containers[0], log.update(containers[0].layerInfo, LogEntry.EntryState.PROCESSED));
        assertSame(containers[2], log.update(containers[2].layerInfo, LogEntry.EntryState.FAILED));
    }

    @DisplayName("Tests that prevent updating a record that has been rolled-over")
    @Test
    void testCannotUpdateRolledOverRecord() {
        TransactionLog log = new TransactionLog(3);
        TransactionLog.EntryContainer[] containers = add(log, 5);

        // records 3 and 4 (layer 1) replaced records 0 and 1 (layer 0)
        assertNull(log.update(containers[0].layerInfo, LogEntry.EntryState.PROCESSED));
        assertNull(log.update(containers[1].layerInfo, LogEntry.EntryState.PROCESSED));

        // two layers behind: record 7 (layer 2) replaced record 4 (layer 1), which had replaced record 1 (layer 0)
        TransactionLog.EntryContainer[] next = add(log, 3);
        assertNull(log.update(containers[1].layerInfo, LogEntry.EntryState.PROCESSED));
        assertNull(log.update(containers[4].layerInfo, LogEntry.EntryState.PROCESSED));
        assertSame(next[2], log.update(next[2].layerInfo, LogEntry.EntryState.PROCESSED));
    }

    @DisplayName("Tests that can update records after a roll-over has started")
    @Test
    void testCanUpdateDifferentLayerWithRollOver() {
        TransactionLog log = new TransactionLog(3);
        TransactionLog.EntryContainer[] containers = add(log, 4);

        // record 3 (layer 1) replaced record 0; record 1, the next to replace, and record 2 are still there
        assertSame(containers[1], log.update(containers[1].layerInfo, LogEntry.EntryState.PROCESSED));
        assertSame(containers[2], log.update(containers[2].layerInfo, LogEntry.EntryState.PROCESSED));
        assertSame(containers[3], log.update(containers[3].layerInfo, LogEntry.EntryState.PROCESSED));
    }

    @DisplayName("Tests that a record of another transaction log is not updated in this one")
    @Test
    void testCannotUpdateRecordOfAnotherLog() {
        // as after a restart of the route: the writer of the previous start had its own transaction log
        TransactionLog previous = new TransactionLog(10);
        TransactionLog.EntryContainer[] stale = add(previous, 2);
        TransactionLog log = new TransactionLog(10);
        TransactionLog.EntryContainer[] containers = add(log, 2);

        assertNull(log.update(stale[1].layerInfo, LogEntry.EntryState.PROCESSED));
        assertEquals(LogEntry.EntryState.NEW, containers[1].logEntry.getEntryState());
    }
}

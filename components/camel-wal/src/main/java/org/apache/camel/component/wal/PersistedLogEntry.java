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

/**
 * This class represents a log entry that has been persisted to disk
 */
class PersistedLogEntry extends LogEntry {
    private final EntryInfo entryInfo;

    public PersistedLogEntry(EntryInfo entryInfo, EntryState entryState, int keyMetadata, byte[] key, int valueMetadata,
                             byte[] value) {
        super(entryState, keyMetadata, key, valueMetadata, value);

        this.entryInfo = entryInfo;
    }

    public EntryInfo getEntryInfo() {
        return entryInfo;
    }
}

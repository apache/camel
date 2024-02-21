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
 * Represents a single entry in the log of transactions
 */
class LogEntry {
    public enum EntryState {
        IGNORED(-1),
        NEW(1),
        PROCESSED(10),
        FAILED(20);

        private final int code;

        EntryState(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static EntryState fromInt(int value) {
            switch (value) {
                case -1:
                    return IGNORED;
                case 1:
                    return NEW;
                case 10:
                    return PROCESSED;
                case 20:
                    return FAILED;
                default:
                    throw new IllegalArgumentException("Invalid state with value " + value);
            }
        }

    }

    private EntryState entryState;
    private int keyMetadata;
    private final byte[] key;
    private int valueMetadata;
    private final byte[] value;

    LogEntry(EntryState entryState, int keyMetadata, byte[] key, int valueMetadata, byte[] value) {
        this.entryState = entryState;
        this.keyMetadata = keyMetadata;
        this.key = key;
        this.valueMetadata = valueMetadata;
        this.value = value;
    }

    public EntryState getEntryState() {
        return entryState;
    }

    public int getKeyMetadata() {
        return keyMetadata;
    }

    public void setKeyMetadata(int keyMetadata) {
        this.keyMetadata = keyMetadata;
    }

    public int getValueMetadata() {
        return valueMetadata;
    }

    public void setValueMetadata(int valueMetadata) {
        this.valueMetadata = valueMetadata;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getValue() {
        return value;
    }

    public void setEntryState(EntryState entryState) {
        this.entryState = entryState;
    }

    /**
     * Returns the size of this record (sum of the sizes of: entry state, key metadata, key length, key, value metadata,
     * value length and value)
     *
     * @return the size in bytes
     */
    public int size() {
        return size(key, value);
    }

    /**
     * Returns the size of a record (sum of the sizes of: entry state, key metadata, key length, key, value metadata,
     * value length and value)
     *
     * @param  key   the entry key
     * @param  value the entry value
     * @return       the size in bytes
     */
    public static int size(byte[] key, byte[] value) {
        return Integer.BYTES + Integer.BYTES + Integer.BYTES + key.length + Integer.BYTES + Integer.BYTES + value.length;
    }
}

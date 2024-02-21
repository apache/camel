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

import java.util.Arrays;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This represents an in-memory transaction log. It's the source from where the log entries are saved to the channel
 * and, subsequently, flushed to disk. It is backed by a layered circular-buffer (i.e.: a regular circular buffer that
 * increments a layer index for every loop iteration). The entries are rolled-over whenever the capacity reach its
 * maximum value and updates to those rolled-over records will be silently discarded (even if in-error). The layer
 * information is not persisted to disk as it is relevant only for determining whether to update or discard records.
 */
class TransactionLog {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionLog.class);

    /**
     * Contains the layer information for log entries
     */
    public static class LayerInfo {
        private final int index;
        private final int layer;
        private final boolean isRollingOver;

        public LayerInfo(int index, int layer, boolean isRollingOver) {
            this.index = index;
            this.layer = layer;
            this.isRollingOver = isRollingOver;
        }

        public int getIndex() {
            return index;
        }

        public int getLayer() {
            return layer;
        }

        public boolean isRollingOver() {
            return isRollingOver;
        }

        @Override
        public String toString() {
            return "LayerInfo{" +
                   "index=" + index +
                   ", layer=" + layer +
                   ", isRollingOver=" + isRollingOver +
                   '}';
        }
    }

    /**
     * A container for an in-memory entry that can be used to determine the layer where the record is as well as obtain
     * it's entry.
     */
    static class EntryContainer {
        LayerInfo layerInfo;
        LogEntry logEntry;

        public EntryContainer(LayerInfo layerInfo, LogEntry logEntry) {
            this.layerInfo = layerInfo;
            this.logEntry = logEntry;
        }
    }

    private final int maxCapacity;
    private final EntryContainer[] logEntries;

    private int currentIndex;
    private int currentLayer;

    /**
     * Creates a new transaction log with the given capacity
     *
     * @param capacity the capacity of the circular buffer that backs this in-memory transaction log
     */
    public TransactionLog(int capacity) {
        this.maxCapacity = capacity;
        logEntries = new EntryContainer[capacity];
    }

    /**
     * Adds a new entry to the log
     *
     * @param  logEntry the entry to add
     * @return          The information about the layer in the buffer where the entry was added
     */
    public LayerInfo add(LogEntry logEntry) {
        boolean rollingOver = false;
        if (currentIndex >= maxCapacity) {
            currentLayer++;
            currentIndex = 0;
            rollingOver = true;
        }

        final EntryContainer entryContainer
                = new EntryContainer(new LayerInfo(currentIndex, currentLayer, rollingOver), logEntry);
        logEntries[currentIndex] = entryContainer;
        currentIndex++;

        return entryContainer.layerInfo;
    }

    /**
     * Given the information about an entry, it determines whether it can be updated or not
     *
     * @param  transactionLogLayer the layer in the transaction log
     * @param  transactionLogIndex the index in the transaction log
     * @param  entryInfoLayer      the layer for the entry
     * @param  entryInfoIndex      the index for the entry
     * @return                     true if it can be updated or false otherwise
     */
    static boolean canUpdate(int transactionLogLayer, int transactionLogIndex, int entryInfoLayer, int entryInfoIndex) {
        if (transactionLogLayer == entryInfoLayer) {
            // Must make sure to not update beyond the current index
            if (transactionLogIndex >= entryInfoIndex) {
                return true;
            }
        }

        if (transactionLogLayer > entryInfoLayer) {
            if (transactionLogIndex < entryInfoIndex) {
                return true;
            }
        }

        return false;
    }

    /**
     * Given the layer information for an entry, it determines if it can be updated via
     * {@link TransactionLog#canUpdate(int, int, int, int)}
     *
     * @param  layerInfo the layer information
     * @return           true if it can be updated or false otherwise
     */
    private boolean canUpdate(LayerInfo layerInfo) {
        return canUpdate(currentLayer, currentIndex, layerInfo.getLayer(), layerInfo.getIndex());
    }

    /**
     * Tries to update an entry in the in-memory transaction log
     *
     * @param  layerInfo the layer information for the entry
     * @param  state     the state to update the entry to
     * @return           the updated entry or null if the record was rolled-over and discarded
     */
    public LogEntry update(LayerInfo layerInfo, LogEntry.EntryState state) {
        if (layerInfo == null) {
            if (state != LogEntry.EntryState.PROCESSED) {
                LOG.warn(
                        "Discarded an unprocessed record because the layer information is not available: it may have been rolled over");
            }

            return null;
        }

        if (canUpdate(layerInfo)) {
            LOG.debug("Updating record with layer: {}", layerInfo);
            EntryContainer container = logEntries[layerInfo.getIndex()];

            container.logEntry.setEntryState(state);

            return container.logEntry;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Discarded a record because it has been rolled-over. Record layer: {}", layerInfo);
            }

            if (state == LogEntry.EntryState.FAILED) {
                LOG.warn(
                        "An update of failed record was discarded because it has been rolled over (it may have taken too long to report back)");
            }
        }

        return null;
    }

    /**
     * Returns a sequential stream of the entries
     *
     * @return a sequential stream of the entries
     */
    public Stream<EntryContainer> stream() {
        return Arrays.stream(logEntries);
    }

    /**
     * Gets the current layer
     *
     * @return the current layer
     */
    public int currentLayer() {
        return currentLayer;
    }
}

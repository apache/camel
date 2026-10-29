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
        final LayerInfo layerInfo;
        final LogEntry logEntry;
        /**
         * The current position of the entry in the file: a roll-over writes the entries of the previous layer again,
         * after the newer ones
         */
        long position = -1;

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
     * @return          The container of the entry, with the information about the layer in the buffer where the entry
     *                  was added
     */
    public EntryContainer add(LogEntry logEntry) {
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

        return entryContainer;
    }

    /**
     * Determines whether the entry with the given layer information can be updated: its slot in the buffer still holds
     * it, so no entry of a newer layer has replaced it. The layer information of an entry of another transaction log,
     * such as the log of a writer that was closed when the route stopped, is never in a slot of this one.
     *
     * @param  layerInfo the layer information of the entry, as {@link #add(LogEntry)} created it
     * @return           true if it can be updated or false otherwise
     */
    private boolean canUpdate(LayerInfo layerInfo) {
        final int index = layerInfo.getIndex();
        if (index < 0 || index >= logEntries.length) {
            return false;
        }
        final EntryContainer container = logEntries[index];
        return container != null && container.layerInfo == layerInfo;
    }

    /**
     * Tries to update an entry in the in-memory transaction log
     *
     * @param  layerInfo the layer information for the entry
     * @param  state     the state to update the entry to
     * @return           the container of the updated entry or null if the record was rolled-over and discarded (or
     *                   belongs to another transaction log, such as the log of a writer closed when the route stopped)
     */
    public EntryContainer update(LayerInfo layerInfo, LogEntry.EntryState state) {
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

            return container;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Discarded a record because it has been rolled-over, or it is not in this log. Record layer: {}",
                        layerInfo);
            }

            if (state == LogEntry.EntryState.FAILED) {
                LOG.warn(
                        "An update of failed record was discarded because it has been rolled over (it may have taken too long to report back), or because it belongs to the log of a previous start of the route");
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

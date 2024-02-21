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
 * Contains information about a log entry
 */
public class EntryInfo {
    private final long position;

    private EntryInfo(long position) {
        this.position = position;
    }

    public long getPosition() {
        return position;
    }

    /**
     * Contains information about a log entry that is hot on the cache
     */
    public static class CachedEntryInfo extends EntryInfo {
        private final TransactionLog.LayerInfo layerInfo;

        CachedEntryInfo(long position, TransactionLog.LayerInfo layerInfo) {
            super(position);
            this.layerInfo = layerInfo;
        }

        public TransactionLog.LayerInfo getLayerInfo() {
            return layerInfo;
        }

    }

    /**
     * Creates a new entry info instance for entries persisted at the given position
     *
     * @param  position the position of the entry
     * @return          a new entry info
     */
    public static EntryInfo createForPersisted(long position) {
        return new EntryInfo(position);
    }

    /**
     * Creates a new entry info instance for entries cached at the given position and layer
     *
     * @param  position  the position of the entry
     * @param  layerInfo the layer on the transaction cache
     * @return           a new entry info
     */
    public static CachedEntryInfo createForCached(long position, TransactionLog.LayerInfo layerInfo) {
        return new CachedEntryInfo(position, layerInfo);
    }
}

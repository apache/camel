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
package org.apache.camel.support;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks message body and headers size statistics per endpoint key.
 *
 * @since 4.21
 */
public class EndpointSizeStatistics {

    private final Map<String, SizeStats> map;
    private final Lock lock = new ReentrantLock();

    public EndpointSizeStatistics(int maxCapacity) {
        this.map = LRUCacheFactory.newLRUCache(16, maxCapacity, false);
    }

    public void onHit(String key, long bodySize, long headersSize) {
        lock.lock();
        try {
            map.compute(key, (k, current) -> {
                if (current == null) {
                    current = new SizeStats();
                }
                if (bodySize >= 0) {
                    current.bodyCount++;
                    current.totalBodySize += bodySize;
                    if (bodySize < current.minBodySize || current.bodyCount == 1) {
                        current.minBodySize = bodySize;
                    }
                    if (bodySize > current.maxBodySize) {
                        current.maxBodySize = bodySize;
                    }
                }
                if (headersSize >= 0) {
                    current.headersCount++;
                    current.totalHeadersSize += headersSize;
                    if (headersSize < current.minHeadersSize || current.headersCount == 1) {
                        current.minHeadersSize = headersSize;
                    }
                    if (headersSize > current.maxHeadersSize) {
                        current.maxHeadersSize = headersSize;
                    }
                }
                return current;
            });
        } finally {
            lock.unlock();
        }
    }

    public SizeStats getStats(String key) {
        lock.lock();
        try {
            return map.get(key);
        } finally {
            lock.unlock();
        }
    }

    public void remove(String key) {
        lock.lock();
        try {
            map.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            map.clear();
        } finally {
            lock.unlock();
        }
    }

    public static class SizeStats {
        long minBodySize;
        long maxBodySize;
        long totalBodySize;
        long bodyCount;
        long minHeadersSize;
        long maxHeadersSize;
        long totalHeadersSize;
        long headersCount;

        public long getMinBodySize() {
            return bodyCount > 0 ? minBodySize : -1;
        }

        public long getMaxBodySize() {
            return bodyCount > 0 ? maxBodySize : -1;
        }

        public long getMeanBodySize() {
            return bodyCount > 0 ? totalBodySize / bodyCount : -1;
        }

        public long getMinHeadersSize() {
            return headersCount > 0 ? minHeadersSize : -1;
        }

        public long getMaxHeadersSize() {
            return headersCount > 0 ? maxHeadersSize : -1;
        }

        public long getMeanHeadersSize() {
            return headersCount > 0 ? totalHeadersSize / headersCount : -1;
        }
    }
}

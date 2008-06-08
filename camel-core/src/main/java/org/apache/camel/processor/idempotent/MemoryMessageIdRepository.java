/**
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
package org.apache.camel.processor.idempotent;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.util.LRUCache;

/**
 * A memory based implementation of {@link MessageIdRepository}. Care should be
 * taken to use a suitable underlying {@link Map} to avoid this class being a
 * memory leak
 * 
 * @version $Revision$
 */
public class MemoryMessageIdRepository implements MessageIdRepository {
    private final Map cache;

    public MemoryMessageIdRepository(Map set) {
        this.cache = set;
    }

    /**
     * Creates a new MemoryMessageIdRepository with a memory based repository.
     * <b>Warning</b> this method should only really be used for testing as it
     * will involve keeping all message IDs in RAM.
     */
    public static MessageIdRepository memoryMessageIdRepository() {
        return memoryMessageIdRepository(new HashMap());
    }

    /**
     * Creates a new MemoryMessageIdRepository with a memory based repository.
     * <b>Warning</b> this method should only really be used for testing as it
     * will involve keeping all message IDs in RAM.
     */
    public static MessageIdRepository memoryMessageIdRepository(int cacheSize) {
        return memoryMessageIdRepository(new LRUCache(cacheSize));
    }

    /**
     * Creates a new MemoryMessageIdRepository using the given {@link Map} to
     * use to store the processed Message ID objects. Warning be careful of the
     * implementation of Map you use as if you are not careful it could be a
     * memory leak.
     */
    public static MessageIdRepository memoryMessageIdRepository(Map cache) {
        return new MemoryMessageIdRepository(cache);
    }

    public boolean contains(String messageId) {
        synchronized (cache) {
            if (cache.containsKey(messageId)) {
                return true;
            } else {
                cache.put(messageId, messageId);
                return false;
            }
        }
    }
}

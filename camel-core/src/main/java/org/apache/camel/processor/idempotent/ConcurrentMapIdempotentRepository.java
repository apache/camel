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

import java.util.concurrent.ConcurrentMap;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;

/**
 * A {@link ConcurrentMap}-based implementation of {@link IdempotentRepository}.
 * <p/>
 * Use this class to interface onto your favourite data-fabric.
 */
@ManagedResource(description = "ConcurrentMap based idempotent repository")
public class ConcurrentMapIdempotentRepository extends ServiceSupport implements IdempotentRepository<String> {
    private final ConcurrentMap<String, Object> cache;

    public ConcurrentMapIdempotentRepository(ConcurrentMap<String, Object> cache) {
        this.cache = cache;
    }

    @ManagedOperation(description = "Adds the key to the store")
    @Override
    public boolean add(String key) {
        return cache.putIfAbsent(key, key) == null;
    }

    @ManagedOperation(description = "Does the store contain the given key")
    @Override
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    @ManagedOperation(description = "Remove the key from the store")
    @Override
    public boolean remove(String key) {
        return cache.remove(key) != null;
    }

    @Override
    public boolean confirm(String key) {
        // noop
        return true;
    }

    @ManagedOperation(description = "Clear the store")
    @Override
    public void clear() {
        cache.clear();
    }

    public ConcurrentMap<String, Object> getCache() {
        return cache;
    }

    @ManagedAttribute(description = "The current cache size")
    public int getCacheSize() {
        return cache.size();
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
    }
}

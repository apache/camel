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
package org.apache.camel.impl.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.support.service.ServiceSupport;

/**
 * This {@link MemoryStateRepository} class is a memory-based implementation of a {@link StateRepository}.
 */
@ManagedResource(description = "Memory based state repository")
public class MemoryStateRepository extends ServiceSupport implements StateRepository<String, String> {

    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    @Override
    @ManagedOperation(description = "Adds the value of the given key to the store")
    public void setState(String key, String value) {
        cache.put(key, value);
    }

    @Override
    @ManagedOperation(description = "Gets the value of the given key from store")
    public String getState(String key) {
        return cache.get(key);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        cache.clear();
    }
}

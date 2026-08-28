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
package org.apache.camel.component.hazelcast;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link KeyValueRepository} implementation backed by a Hazelcast {@link IMap} with native per-entry TTL support.
 * <p/>
 * Hazelcast natively supports per-entry TTL via {@link IMap#put(Object, Object, long, TimeUnit)} and
 * {@link IMap#putIfAbsent(Object, Object, long, TimeUnit)}, making it a natural fit for the {@code KeyValueRepository}
 * contract without requiring wrapper objects or lazy eviction.
 * <p/>
 * This single implementation can serve as idempotent repository, aggregation repository, and state store via the
 * adapters in {@code camel-support} ({@code KeyValueIdempotentRepository} and {@code KeyValueAggregationRepository}).
 * <p/>
 * If no external {@link HazelcastInstance} is provided, a local instance is created on start and shut down on stop.
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A Hazelcast-based KeyValueRepository with native per-entry TTL support.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Hazelcast based key-value repository")
public class HazelcastKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    private boolean useLocalHzInstance;
    private IMap<String, Object> map;

    @Metadata(description = "Name of the Hazelcast map to use", defaultValue = "HazelcastKeyValueRepository")
    private String mapName = HazelcastKeyValueRepository.class.getSimpleName();

    @Metadata(description = "To use an existing Hazelcast instance instead of creating a local one")
    private HazelcastInstance hazelcastInstance;

    /**
     * Creates a new Hazelcast-backed key-value repository with default settings (local instance, default map name).
     */
    public HazelcastKeyValueRepository() {
    }

    /**
     * Creates a new Hazelcast-backed key-value repository using the given Hazelcast instance.
     */
    public HazelcastKeyValueRepository(HazelcastInstance hazelcastInstance) {
        this(hazelcastInstance, HazelcastKeyValueRepository.class.getSimpleName());
    }

    /**
     * Creates a new Hazelcast-backed key-value repository using the given Hazelcast instance and map name.
     */
    public HazelcastKeyValueRepository(HazelcastInstance hazelcastInstance, String mapName) {
        this.hazelcastInstance = hazelcastInstance;
        this.mapName = mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    @ManagedAttribute(description = "The Hazelcast map name")
    public String getMapName() {
        return mapName;
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    @ManagedOperation(description = "Get value by key")
    public Object get(String key) {
        return map.get(key);
    }

    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public Object put(String key, Object value, Duration ttl) {
        if (hasPositiveTtl(ttl)) {
            return map.put(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
        }
        return map.put(key, value);
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public Object delete(String key) {
        return map.remove(key);
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        return map.containsKey(key);
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(map.keySet());
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        map.clear();
    }

    @Override
    public Object putIfAbsent(String key, Object value, Duration ttl) {
        if (hasPositiveTtl(ttl)) {
            return map.putIfAbsent(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
        }
        return map.putIfAbsent(key, value);
    }

    @Override
    public boolean replace(String key, Object expectedOldValue, Object newValue, Duration ttl) {
        if (!hasPositiveTtl(ttl)) {
            return map.replace(key, expectedOldValue, newValue);
        }
        // IMap.replace(K, V, V) does not support TTL, so use distributed lock for atomicity
        map.lock(key);
        try {
            Object current = map.get(key);
            if (!Objects.equals(current, expectedOldValue)) {
                return false;
            }
            map.put(key, newValue, ttl.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } finally {
            map.unlock(key);
        }
    }

    @Override
    public boolean delete(String key, Object expectedValue) {
        return map.remove(key, expectedValue);
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        return map.size();
    }

    private static boolean hasPositiveTtl(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    @Override
    protected void doStart() throws Exception {
        if (hazelcastInstance == null) {
            Config cfg = new XmlConfigBuilder().build();
            cfg.setProperty("hazelcast.version.check.enabled", "false");
            HazelcastSerializationFilterHelper.applyDefault(cfg);
            hazelcastInstance = Hazelcast.newHazelcastInstance(cfg);
            useLocalHzInstance = true;
        } else {
            ObjectHelper.notNull(hazelcastInstance, "hazelcastInstance");
        }
        map = hazelcastInstance.getMap(mapName);
    }

    @Override
    protected void doStop() throws Exception {
        if (useLocalHzInstance && hazelcastInstance != null) {
            hazelcastInstance.getLifecycleService().shutdown();
            hazelcastInstance = null;
        }
    }
}

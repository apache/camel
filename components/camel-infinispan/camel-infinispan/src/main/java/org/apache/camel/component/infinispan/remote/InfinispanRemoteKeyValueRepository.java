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
package org.apache.camel.component.infinispan.remote;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.KeyValueRepositoryHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.MetadataValue;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jspecify.annotations.Nullable;

import static org.apache.camel.component.infinispan.remote.InfinispanRemoteUtil.getCacheWithFlags;

/**
 * A {@link KeyValueRepository} implementation backed by a remote Infinispan server via the HotRod client protocol.
 * <p/>
 * TTL is mapped from {@link Duration} to Infinispan's native lifespan via
 * {@code BasicCache.put(key, value, lifespan, TimeUnit.MILLISECONDS)}. The cache is configured with
 * {@link Flag#FORCE_RETURN_VALUE} to ensure that {@code put} and {@code remove} operations return previous values as
 * required by the {@link KeyValueRepository} contract.
 * <p/>
 * Values are serialized through {@link KeyValueRepositoryHelper} (plain Java serialization) and stored as raw
 * {@code byte[]} in the cache. This ensures a consistent serialization format across all persistent
 * {@link KeyValueRepository} implementations, avoids coupling to Infinispan's marshaller configuration, and eliminates
 * the need for ProtoStream schema registration for complex value types.
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A KeyValueRepository backed by remote Infinispan (HotRod client).",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Infinispan Remote based key-value repository")
public class InfinispanRemoteKeyValueRepository extends ServiceSupport implements KeyValueRepository, CamelContextAware {

    private CamelContext camelContext;
    private Supplier<RemoteCache<String, byte[]>> cache;
    private InfinispanRemoteManager manager;

    @Metadata(description = "Name of cache", required = true)
    private String cacheName;
    @Metadata(description = "Configuration for remote Infinispan")
    private InfinispanRemoteConfiguration configuration;

    public InfinispanRemoteKeyValueRepository() {
    }

    /**
     * Creates a new Infinispan remote key-value repository for the given cache name.
     *
     * @param cacheName the name of the Infinispan cache to use
     */
    public InfinispanRemoteKeyValueRepository(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    @ManagedOperation(description = "Get value by key")
    public @Nullable Object get(String key) {
        byte[] bytes = cache.get().get(key);
        return bytes != null ? KeyValueRepositoryHelper.deserialize(bytes) : null;
    }

    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public @Nullable Object put(String key, Object value, Duration ttl) {
        byte[] serialized = KeyValueRepositoryHelper.serialize(value);
        byte[] previous;
        if (hasPositiveTtl(ttl)) {
            previous = cache.get().put(key, serialized, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            previous = cache.get().put(key, serialized);
        }
        return previous != null ? KeyValueRepositoryHelper.deserialize(previous) : null;
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public @Nullable Object delete(String key) {
        byte[] bytes = cache.get().remove(key);
        return bytes != null ? KeyValueRepositoryHelper.deserialize(bytes) : null;
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        return cache.get().containsKey(key);
    }

    @Override
    public Set<String> keys() {
        return cache.get().keySet();
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        cache.get().clear();
    }

    @Override
    public @Nullable Object putIfAbsent(String key, Object value, Duration ttl) {
        byte[] serialized = KeyValueRepositoryHelper.serialize(value);
        byte[] existing;
        if (hasPositiveTtl(ttl)) {
            existing = cache.get().putIfAbsent(key, serialized, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            existing = cache.get().putIfAbsent(key, serialized);
        }
        return existing != null ? KeyValueRepositoryHelper.deserialize(existing) : null;
    }

    /**
     * Atomically replaces the value for the given key using Infinispan's version-based optimistic locking.
     * <p/>
     * Because values are stored as serialized {@code byte[]}, Infinispan's value-based {@code replace(K, V, V)} cannot
     * compare entries reliably through the marshaller. Instead, this method uses {@code getWithMetadata} to read the
     * current value and its version, compares the deserialized value at the Java object level, and then calls
     * {@code replaceWithVersion} to perform an atomic swap guarded by the entry version.
     */
    @Override
    public boolean replace(String key, Object expectedOldValue, Object newValue, Duration ttl) {
        MetadataValue<byte[]> metadata = cache.get().getWithMetadata(key);
        if (metadata == null) {
            return false;
        }
        Object currentObj = KeyValueRepositoryHelper.deserialize(metadata.getValue());
        if (!Objects.equals(currentObj, expectedOldValue)) {
            return false;
        }
        byte[] newBytes = KeyValueRepositoryHelper.serialize(newValue);
        if (hasPositiveTtl(ttl)) {
            return cache.get().replaceWithVersion(key, newBytes, metadata.getVersion(),
                    ttl.toMillis(), TimeUnit.MILLISECONDS, -1, TimeUnit.MILLISECONDS);
        }
        return cache.get().replaceWithVersion(key, newBytes, metadata.getVersion());
    }

    /**
     * Atomically removes the entry for the given key using Infinispan's version-based optimistic locking.
     * <p/>
     * Similar to {@link #replace}, this uses {@code getWithMetadata} and {@code removeWithVersion} to avoid relying on
     * marshaller-level value comparison for serialized {@code byte[]} entries.
     */
    @Override
    public boolean delete(String key, Object expectedValue) {
        MetadataValue<byte[]> metadata = cache.get().getWithMetadata(key);
        if (metadata == null) {
            return false;
        }
        Object currentObj = KeyValueRepositoryHelper.deserialize(metadata.getValue());
        if (!Objects.equals(currentObj, expectedValue)) {
            return false;
        }
        return cache.get().removeWithVersion(key, metadata.getVersion());
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        return cache.get().size();
    }

    // ---- Configuration accessors ----

    @ManagedAttribute(description = "The cache name")
    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public InfinispanRemoteConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanRemoteConfiguration configuration) {
        this.configuration = configuration;
    }

    public RemoteCacheManager getCacheContainer() {
        return configuration != null ? configuration.getCacheContainer() : null;
    }

    public void setCacheContainer(RemoteCacheManager cacheContainer) {
        if (this.configuration == null) {
            this.configuration = new InfinispanRemoteConfiguration();
        }
        this.configuration.setCacheContainer(cacheContainer);
    }

    public InfinispanRemoteManager getManager() {
        return manager;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // ---- Lifecycle ----

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(cacheName, "cacheName", this);

        if (this.configuration == null) {
            this.configuration = new InfinispanRemoteConfiguration();
        }

        this.manager = new InfinispanRemoteManager(camelContext, configuration);
        this.cache = Suppliers.memorize(
                () -> getCacheWithFlags(manager, cacheName, Flag.FORCE_RETURN_VALUE));
        ServiceHelper.startService(manager);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopAndShutdownService(manager);
    }

    // ---- Internal ----

    private static boolean hasPositiveTtl(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}

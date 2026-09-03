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
package org.apache.camel.component.redis;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.KeyValueRepositoryHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.jspecify.annotations.Nullable;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.config.Config;

/**
 * A {@link KeyValueRepository} implementation backed by Redis using the Redisson client.
 * <p/>
 * Keys are namespaced under a configurable prefix ({@link #keyPrefix}) to avoid collisions with other data in the same
 * Redis instance. Values are serialized through {@link KeyValueRepositoryHelper} (plain Java serialization) and stored
 * as raw {@code byte[]} using Redisson's {@link ByteArrayCodec}. This ensures a consistent serialization format across
 * all persistent {@link KeyValueRepository} implementations and avoids coupling to Redisson's built-in codec.
 * <p/>
 * TTL is mapped from milliseconds to Redis native key expiry via
 * {@code RBucket.set(value, ttl, TimeUnit.MILLISECONDS)}. Atomic {@link #putIfAbsent} is supported via
 * {@code RBucket.setIfAbsent}.
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A KeyValueRepository backed by Redis (Redisson client).",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Redis based key-value repository")
public class RedisKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    private boolean shutdownRedisson;

    @Metadata(label = "advanced", description = "To use an existing Redisson client to connect to Redis server")
    private RedissonClient redisson;
    @Metadata(description = "URL to remote Redis server (host:port)", required = true)
    private String endpoint;
    @Metadata(description = "Key prefix used to namespace entries in Redis", defaultValue = "camel-kvr:")
    private String keyPrefix = "camel-kvr:";

    public RedisKeyValueRepository() {
    }

    /**
     * Creates a new Redis key-value repository connecting to the given endpoint.
     *
     * @param endpoint the Redis server address in {@code host:port} format
     */
    public RedisKeyValueRepository(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Creates a new Redis key-value repository connecting to the given endpoint with a custom key prefix.
     *
     * @param endpoint  the Redis server address in {@code host:port} format
     * @param keyPrefix the prefix to prepend to all keys stored in Redis
     */
    public RedisKeyValueRepository(String endpoint, String keyPrefix) {
        this.endpoint = endpoint;
        this.keyPrefix = keyPrefix;
    }

    @Override
    @ManagedOperation(description = "Get value by key")
    public @Nullable Object get(String key) {
        RBucket<byte[]> bucket = redisson.getBucket(toRedisKey(key), ByteArrayCodec.INSTANCE);
        byte[] bytes = bucket.get();
        return bytes != null ? KeyValueRepositoryHelper.deserialize(bytes) : null;
    }

    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public @Nullable Object put(String key, Object value, Duration ttl) {
        RBucket<byte[]> bucket = redisson.getBucket(toRedisKey(key), ByteArrayCodec.INSTANCE);
        byte[] serialized = KeyValueRepositoryHelper.serialize(value);
        byte[] previous;
        if (hasPositiveTtl(ttl)) {
            previous = bucket.getAndSet(serialized, ttl);
        } else {
            previous = bucket.getAndSet(serialized);
        }
        return previous != null ? KeyValueRepositoryHelper.deserialize(previous) : null;
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public @Nullable Object delete(String key) {
        RBucket<byte[]> bucket = redisson.getBucket(toRedisKey(key), ByteArrayCodec.INSTANCE);
        byte[] bytes = bucket.getAndDelete();
        return bytes != null ? KeyValueRepositoryHelper.deserialize(bytes) : null;
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        RBucket<byte[]> bucket = redisson.getBucket(toRedisKey(key), ByteArrayCodec.INSTANCE);
        return bucket.isExists();
    }

    @Override
    public Set<String> keys() {
        RKeys rKeys = redisson.getKeys();
        int prefixLen = keyPrefix.length();
        return rKeys.getKeysStream(KeysScanOptions.defaults().pattern(toRedisKey("*")))
                .map(k -> k.substring(prefixLen))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        RKeys rKeys = redisson.getKeys();
        String pattern = toRedisKey("*");
        rKeys.deleteByPattern(pattern);
    }

    @Override
    public @Nullable Object putIfAbsent(String key, Object value, Duration ttl) {
        RBucket<byte[]> bucket = redisson.getBucket(toRedisKey(key), ByteArrayCodec.INSTANCE);
        byte[] serialized = KeyValueRepositoryHelper.serialize(value);
        boolean wasSet;
        if (hasPositiveTtl(ttl)) {
            wasSet = bucket.setIfAbsent(serialized, ttl);
        } else {
            wasSet = bucket.setIfAbsent(serialized);
        }
        if (wasSet) {
            return null;
        }
        // Key already existed; return the current value
        byte[] existing = bucket.get();
        return existing != null ? KeyValueRepositoryHelper.deserialize(existing) : null;
    }

    /**
     * Atomically replaces the value for a key if the current value matches the expected one.
     * <p/>
     * <b>Note:</b> Redisson does not expose an atomic CAS-and-set-TTL primitive. When a TTL is specified, the
     * replacement is performed in two steps: {@code compareAndSet} followed by {@code expire}. There is a brief window
     * between the two calls where the new value exists without its TTL applied. A crash in that window would leave the
     * entry without expiry. For callers requiring stricter consistency guarantees (e.g. idempotent repositories), be
     * aware of this non-atomic TTL application.
     */
    @Override
    public boolean replace(String key, Object expectedOldValue, Object newValue, Duration ttl) {
        RBucket<byte[]> bucket = redisson.getBucket(toRedisKey(key), ByteArrayCodec.INSTANCE);
        byte[] expectedBytes = KeyValueRepositoryHelper.serialize(expectedOldValue);
        byte[] newBytes = KeyValueRepositoryHelper.serialize(newValue);
        boolean swapped = bucket.compareAndSet(expectedBytes, newBytes);
        if (swapped && hasPositiveTtl(ttl)) {
            bucket.expire(ttl);
        }
        return swapped;
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        RKeys rKeys = redisson.getKeys();
        String[] matchedKeys = rKeys.getKeysStream(KeysScanOptions.defaults().pattern(toRedisKey("*")))
                .toArray(String[]::new);
        if (matchedKeys.length == 0) {
            return 0;
        }
        return (int) rKeys.countExists(matchedKeys);
    }

    // ---- Configuration accessors ----

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @ManagedAttribute(description = "Key prefix used for namespacing")
    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public RedissonClient getRedisson() {
        return redisson;
    }

    public void setRedisson(RedissonClient redisson) {
        this.redisson = redisson;
    }

    // ---- Lifecycle ----

    @Override
    protected void doInit() throws Exception {
        if (redisson == null) {
            StringHelper.notEmpty(endpoint, "endpoint");
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (redisson == null) {
            Config config = new Config();
            config.useSingleServer().setAddress(String.format("redis://%s", endpoint));
            redisson = Redisson.create(config);
            shutdownRedisson = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (redisson != null && shutdownRedisson) {
            redisson.shutdown();
            redisson = null;
        }
    }

    // ---- Internal ----

    private static boolean hasPositiveTtl(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private String toRedisKey(String key) {
        return keyPrefix + key;
    }
}

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
package org.apache.camel.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory to create {@link LRUCache} instances.
 */
public final class LRUCacheFactory {

    private static final Logger LOG = LoggerFactory.getLogger(LRUCacheFactory.class);

    private static final AtomicBoolean INIT = new AtomicBoolean();

    private static final boolean USE_SIMPLE_CACHE;

    private LRUCacheFactory() {
    }

    static {
        USE_SIMPLE_CACHE = "true".equalsIgnoreCase(System.getProperty("CamelSimpleLRUCacheFactory", "false"));
        if (!USE_SIMPLE_CACHE) {
            boolean warmUp = "true".equalsIgnoreCase(System.getProperty("CamelWarmUpLRUCacheFactory", "true"));
            if (warmUp) {
                // warm-up LRUCache which happens in a background test, which can speedup starting Camel
                // as the warm-up can run concurrently with starting up Camel and the runtime container Camel may be running inside
                warmUp();
            }
        }
    }


    /**
     * Warm-up the LRUCache to startup Apache Camel faster.
     */
    public static void warmUp() {
        // create a dummy map in a separate thread to warm-up the Caffeine cache concurrently
        // while Camel is starting up. This allows us to overall startup Camel a bit faster
        // as Caffeine takes 150+ millis to initialize.
        if (INIT.compareAndSet(false, true)) {
            // only need to init Caffeine once in the JVM/classloader
            Runnable task = () -> {
                StopWatch watch = new StopWatch();
                LOG.debug("Warming up LRUCache ...");
                newLRUCache(16);
                LOG.debug("Warming up LRUCache complete in {} millis", watch.taken());
            };

            String threadName = ThreadHelper.resolveThreadName(null, "LRUCacheFactory");

            Thread thread = new Thread(task, threadName);
            thread.start();
        }
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUCache(int maximumCacheSize) {
        LOG.trace("Creating LRUCache with maximumCacheSize: {}", maximumCacheSize);
        if (USE_SIMPLE_CACHE) {
            return new SimpleLRUCache<>(maximumCacheSize);
        }
        return new LRUCache<>(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUCache(int maximumCacheSize, Consumer<V> onEvict) {
        LOG.trace("Creating LRUCache with maximumCacheSize: {}", maximumCacheSize);
        if (USE_SIMPLE_CACHE) {
            return new SimpleLRUCache<>(16, maximumCacheSize, onEvict);
        }
        return new LRUCache<>(16, maximumCacheSize, onEvict, false, false, false);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUCache(int initialCapacity, int maximumCacheSize) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}", initialCapacity, maximumCacheSize);
        if (USE_SIMPLE_CACHE) {
            return new SimpleLRUCache<>(initialCapacity, maximumCacheSize);
        }
        return new LRUCache<>(initialCapacity, maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize,load factor and ordering mode.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity, maximumCacheSize, stopOnEviction);
        if (USE_SIMPLE_CACHE) {
            return new SimpleLRUCache<>(initialCapacity, maximumCacheSize, stopOnEviction);
        }
        return new LRUCache<>(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUSoftCache(int maximumCacheSize) {
        LOG.trace("Creating LRUSoftCache with maximumCacheSize: {}", maximumCacheSize);
        if (USE_SIMPLE_CACHE) {
            return new SimpleLRUCache<>(maximumCacheSize);
        }
        return new LRUSoftCache<>(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUWeakCache(int maximumCacheSize) {
        LOG.trace("Creating LRUWeakCache with maximumCacheSize: {}", maximumCacheSize);
        if (USE_SIMPLE_CACHE) {
            return new SimpleLRUCache<>(maximumCacheSize);
        }
        return new LRUWeakCache<>(maximumCacheSize);
    }

    private static class SimpleLRUCache<K, V> extends LinkedHashMap<K, V> {

        static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private final int maximumCacheSize;
        private final Consumer<V> evict;

        public SimpleLRUCache(int maximumCacheSize) {
            this(16, maximumCacheSize, maximumCacheSize > 0);
        }

        public SimpleLRUCache(int initialCapacity, int maximumCacheSize) {
            this(initialCapacity, maximumCacheSize, maximumCacheSize > 0);
        }

        public SimpleLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
            this(initialCapacity, maximumCacheSize, stopOnEviction ? SimpleLRUCache::doStop : SimpleLRUCache::doNothing);
        }

        public SimpleLRUCache(int initialCapacity, int maximumCacheSize, Consumer<V> evicted) {
            super(initialCapacity, DEFAULT_LOAD_FACTOR, true);
            this.maximumCacheSize = maximumCacheSize;
            this.evict = Objects.requireNonNull(evicted);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            if (size() >= maximumCacheSize) {
                V value = eldest.getValue();
                evict.accept(value);
                return true;
            }
            return false;
        }

        static <V> void doNothing(V value) {
        }

        static <V> void doStop(V value) {
            try {
                // stop service as its evicted from cache
                ServiceHelper.stopService(value);
            } catch (Exception e) {
                LOG.warn("Error stopping service: " + value + ". This exception will be ignored.", e);
            }
        }
    }

}

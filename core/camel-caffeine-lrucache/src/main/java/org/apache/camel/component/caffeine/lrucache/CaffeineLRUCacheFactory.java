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
package org.apache.camel.component.caffeine.lrucache;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory to create {@link CaffeineLRUCache} instances.
 */
@JdkService(LRUCacheFactory.FACTORY)
public final class CaffeineLRUCacheFactory extends LRUCacheFactory {

    private static final Logger LOG = LoggerFactory.getLogger(CaffeineLRUCacheFactory.class);

    private static final AtomicBoolean INIT = new AtomicBoolean();

    static {
        boolean warmUp = "true".equalsIgnoreCase(System.getProperty("CamelWarmUpLRUCacheFactory", "true"));
        if (warmUp) {
            // warm-up LRUCache which happens in a background thread, which can speedup starting Camel
            // as the warm-up can run concurrently with starting up Camel and the runtime container Camel may be running inside
            warmUp();
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
                new CaffeineLRUCache<>(16);
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
    @Override
    public <K, V> Map<K, V> createLRUCache(int maximumCacheSize) {
        LOG.trace("Creating LRUCache with maximumCacheSize: {}", maximumCacheSize);
        return new CaffeineLRUCache<>(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int maximumCacheSize, Consumer<V> onEvict) {
        LOG.trace("Creating LRUCache with maximumCacheSize: {}", maximumCacheSize);
        return new CaffeineLRUCache<>(16, maximumCacheSize, onEvict, false, false, false);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}", initialCapacity, maximumCacheSize);
        return new CaffeineLRUCache<>(initialCapacity, maximumCacheSize);
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
    @Override
    public <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity, maximumCacheSize, stopOnEviction);
        return new CaffeineLRUCache<>(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int maximumCacheSize) {
        LOG.trace("Creating LRUSoftCache with maximumCacheSize: {}", maximumCacheSize);
        return new CaffeineLRUSoftCache<>(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize) {
        LOG.trace("Creating LRUSoftCache with initialCapacity: {}, maximumCacheSize: {}", initialCapacity, maximumCacheSize);
        return new CaffeineLRUSoftCache<>(initialCapacity, maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUSoftCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity, maximumCacheSize, stopOnEviction);
        return new CaffeineLRUSoftCache<>(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUWeakCache(int maximumCacheSize) {
        LOG.trace("Creating LRUWeakCache with maximumCacheSize: {}", maximumCacheSize);
        return new CaffeineLRUWeakCache<>(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize) {
        LOG.trace("Creating LRUWeakCache with initialCapacity: {}, maximumCacheSize: {}", initialCapacity, maximumCacheSize);
        return new CaffeineLRUWeakCache<>(initialCapacity, maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUWeakCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity, maximumCacheSize, stopOnEviction);
        return new CaffeineLRUWeakCache<>(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    @Override
    public String toString() {
        return "camel-caffeine-lrucache";
    }
}

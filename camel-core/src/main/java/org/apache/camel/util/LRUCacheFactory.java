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
package org.apache.camel.util;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory to create {@link LRUCache} instances.
 */
public final class LRUCacheFactory {

    private static final Logger LOG = LoggerFactory.getLogger(LRUCacheFactory.class);

    private static final AtomicBoolean INIT = new AtomicBoolean();

    private LRUCacheFactory() {
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
    public static LRUCache newLRUCache(int maximumCacheSize) {
        LOG.trace("Creating LRUCache with maximumCacheSize: {}", maximumCacheSize);
        return new LRUCache(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static LRUCache newLRUCache(int initialCapacity, int maximumCacheSize) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}", initialCapacity, maximumCacheSize);
        return new LRUCache(initialCapacity, maximumCacheSize);
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
    public static LRUCache newLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity, maximumCacheSize, stopOnEviction);
        return new LRUCache(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static LRUSoftCache newLRUSoftCache(int maximumCacheSize) {
        LOG.trace("Creating LRUSoftCache with maximumCacheSize: {}", maximumCacheSize);
        return new LRUSoftCache(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static LRUWeakCache newLRUWeakCache(int maximumCacheSize) {
        LOG.trace("Creating LRUWeakCache with maximumCacheSize: {}", maximumCacheSize);
        return new LRUWeakCache(maximumCacheSize);
    }
}

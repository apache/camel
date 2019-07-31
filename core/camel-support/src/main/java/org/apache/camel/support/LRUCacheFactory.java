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

import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory to create {@link LRUCache} instances.
 */
public abstract class LRUCacheFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(LRUCacheFactory.class);

    private static volatile LRUCacheFactory instance;
    
    public static LRUCacheFactory getInstance() {
        if (instance == null) {
            synchronized (LRUCacheFactory.class) {
                if (instance == null) {
                    instance = createLRUCacheFactory();
                }
            }
        }
        return instance;
    }

    private static LRUCacheFactory createLRUCacheFactory() {
        try {
            ClassLoader classLoader = LRUCacheFactory.class.getClassLoader();
            URL url = classLoader.getResource("META-INF/services/org/apache/camel/lru-cache-factory");
            if (url != null) {
                Properties props = new Properties();
                try (InputStream is = url.openStream()) {
                    props.load(is);
                }
                String clazzName = props.getProperty("class");
                Class<?> clazz = classLoader.loadClass(clazzName);
                Object factory = clazz.getDeclaredConstructor().newInstance();
                return (LRUCacheFactory) factory;
            }
        } catch (Throwable t) {
            LOGGER.warn("Error creating LRUCacheFactory", t);
        }
        return new DefaultLRUCacheFactory();
    }
    
    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUCache(int maximumCacheSize) {
        return getInstance().createLRUCache(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUCache(int maximumCacheSize, Consumer<V> onEvict) {
        return getInstance().createLRUCache(maximumCacheSize, onEvict);
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
        return getInstance().createLRUCache(initialCapacity, maximumCacheSize);
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
        return getInstance().createLRUCache(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUSoftCache(int maximumCacheSize) {
        return getInstance().createLRUSoftCache(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUSoftCache(int initialCapacity, int maximumCacheSize) {
        return getInstance().createLRUSoftCache(initialCapacity, maximumCacheSize);
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
    public static <K, V> Map<K, V> newLRUSoftCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        return getInstance().createLRUSoftCache(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUWeakCache(int maximumCacheSize) {
        return getInstance().createLRUWeakCache(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public static <K, V> Map<K, V> newLRUWeakCache(int initialCapacity, int maximumCacheSize) {
        return getInstance().createLRUWeakCache(initialCapacity, maximumCacheSize);
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
    public static <K, V> Map<K, V> newLRUWeakCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        return getInstance().createLRUWeakCache(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUCache(int maximumCacheSize);

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUCache(int maximumCacheSize, Consumer<V> onEvict);

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize);

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize,load factor and ordering mode.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction);

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUSoftCache(int maximumCacheSize);

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize);

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction);

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUWeakCache(int maximumCacheSize);

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize);

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public abstract <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction);

}

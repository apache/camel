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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.StaticService;
import org.apache.camel.spi.RouteController;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Base implementation for {@link org.apache.camel.spi.TransformerRegistry}, {@link org.apache.camel.spi.ValidatorRegistry}
 * and {@link org.apache.camel.spi.EndpointRegistry}.
 */
public class AbstractDynamicRegistry<K, V> extends AbstractMap<K, V>  implements StaticService {

    protected final ExtendedCamelContext context;
    protected final RouteController routeController;
    protected final int maxCacheSize;
    protected final Map<K, V> dynamicMap;
    protected final Map<K, V> staticMap;

    public AbstractDynamicRegistry(CamelContext context, int maxCacheSize) {
        this.context = (ExtendedCamelContext) context;
        this.routeController = context.getRouteController();
        this.maxCacheSize = maxCacheSize;
        // do not stop on eviction, as the transformer may still be in use
        this.dynamicMap = LRUCacheFactory.newLRUCache(this.maxCacheSize, this.maxCacheSize, false);
        // static map to hold transformers we do not want to be evicted
        this.staticMap = new ConcurrentHashMap<>();
    }

    @Override
    public void start() {
        if (dynamicMap instanceof LRUCache) {
            ((LRUCache) dynamicMap).resetStatistics();
        }
    }

    @Override
    public V get(Object o) {
        // try static map first
        V answer = staticMap.get(o);
        if (answer == null) {
            answer = dynamicMap.get(o);
            if (answer != null && (context.isSetupRoutes() || routeController.isStartingRoutes())) {
                dynamicMap.remove(o);
                staticMap.put((K) o, answer);
            }
        }
        return answer;
    }

    @Override
    public V put(K key, V transformer) {
        // at first we must see if the key already exists and then replace it back, so it stays the same spot
        V answer = staticMap.remove(key);
        if (answer != null) {
            // replace existing
            staticMap.put(key, transformer);
            return answer;
        }

        answer = dynamicMap.remove(key);
        if (answer != null) {
            // replace existing
            dynamicMap.put(key, transformer);
            return answer;
        }

        // we want transformers to be static if they are part of setting up or starting routes
        if (context.isSetupRoutes() || routeController.isStartingRoutes()) {
            answer = staticMap.put(key, transformer);
        } else {
            answer = dynamicMap.put(key, transformer);
        }

        return answer;
    }

    @Override
    public boolean containsKey(Object o) {
        return staticMap.containsKey(o) || dynamicMap.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return staticMap.containsValue(o) || dynamicMap.containsValue(o);
    }

    @Override
    public int size() {
        return staticMap.size() + dynamicMap.size();
    }

    public int staticSize() {
        return staticMap.size();
    }

    public int dynamicSize() {
        return dynamicMap.size();
    }

    @Override
    public boolean isEmpty() {
        return staticMap.isEmpty() && dynamicMap.isEmpty();
    }

    @Override
    public V remove(Object o) {
        V answer = staticMap.remove(o);
        if (answer == null) {
            answer = dynamicMap.remove(o);
        }
        return answer;
    }

    @Override
    public void clear() {
        staticMap.clear();
        dynamicMap.clear();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Entry<K, V>>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                return new CompoundIterator<>(Arrays.asList(
                        staticMap.entrySet().iterator(), dynamicMap.entrySet().iterator()
                ));
            }

            @Override
            public int size() {
                return staticMap.size() + dynamicMap.size();
            }
        };
    }

    public int getMaximumCacheSize() {
        return maxCacheSize;
    }

    /**
     * Purges the cache
     */
    public void purge() {
        // only purge the dynamic part
        dynamicMap.clear();
    }

    public void cleanUp() {
        if (dynamicMap instanceof LRUCache) {
            ((LRUCache) dynamicMap).cleanUp();
        }
    }

    public boolean isStatic(K key) {
        return staticMap.containsKey(key);
    }

    public boolean isDynamic(K key) {
        return dynamicMap.containsKey(key);
    }

    @Override
    public void stop() {
        ServiceHelper.stopService(staticMap.values(), dynamicMap.values());
        purge();
    }

    @Override
    public String toString() {
        return "Registry for " + context.getName() + ", capacity: " + maxCacheSize;
    }

}

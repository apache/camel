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
package org.apache.camel.impl;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.CompoundIterator;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUCacheFactory;
import org.apache.camel.util.ServiceHelper;

/**
 * Default implementation of {@link org.apache.camel.spi.EndpointRegistry}
 */
public class DefaultEndpointRegistry extends AbstractMap<EndpointKey, Endpoint> implements EndpointRegistry<EndpointKey> {
    private static final long serialVersionUID = 1L;
    private Map<EndpointKey, Endpoint> dynamicMap;
    private Map<EndpointKey, Endpoint> staticMap;
    private final CamelContext context;
    private int maxCacheSize;

    public DefaultEndpointRegistry(CamelContext context) {
        this.maxCacheSize = CamelContextHelper.getMaximumEndpointCacheSize(context);
        // do not stop on eviction, as the validator may still be in use
        this.dynamicMap = LRUCacheFactory.newLRUCache(maxCacheSize, maxCacheSize, false);
        // static map to hold endpoints we do not want to be evicted
        this.staticMap = new ConcurrentHashMap<>();
        this.context = context;
    }

    public DefaultEndpointRegistry(CamelContext context, Map<EndpointKey, Endpoint> endpoints) {
        this(context);
        putAll(endpoints);
    }

    @Override
    public void start() throws Exception {
        if (dynamicMap instanceof LRUCache) {
            ((LRUCache) dynamicMap).resetStatistics();
        }
    }

    @Override
    public Endpoint get(Object o) {
        // try static map first
        Endpoint answer = staticMap.get(o);
        if (answer == null) {
            answer = dynamicMap.get(o);
            if (answer != null && (context.isSetupRoutes() || context.getRouteController().isStartingRoutes())) {
                dynamicMap.remove(o);
                staticMap.put((EndpointKey) o, answer);
            }
        }
        return answer;
    }

    @Override
    public Endpoint put(EndpointKey key, Endpoint endpoint) {
        // at first we must see if the key already exists and then replace it back, so it stays the same spot
        Endpoint answer = staticMap.remove(key);
        if (answer != null) {
            // replace existing
            staticMap.put(key, endpoint);
            return answer;
        }

        answer = dynamicMap.remove(key);
        if (answer != null) {
            // replace existing
            dynamicMap.put(key, endpoint);
            return answer;
        }

        // we want endpoints to be static if they are part of setting up or starting routes
        if (context.isSetupRoutes() || context.getRouteController().isStartingRoutes()) {
            answer = staticMap.put(key, endpoint);
        } else {
            answer = dynamicMap.put(key, endpoint);
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

    @Override
    public int dynamicSize() {
        return dynamicMap.size();
    }

    @Override
    public boolean isEmpty() {
        return staticMap.isEmpty() && dynamicMap.isEmpty();
    }

    @Override
    public Endpoint remove(Object o) {
        Endpoint answer = staticMap.remove(o);
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
    public Set<Entry<EndpointKey, Endpoint>> entrySet() {
        return new AbstractSet<Entry<EndpointKey, Endpoint>>() {
            @Override
            public Iterator<Entry<EndpointKey, Endpoint>> iterator() {
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

    @Override
    public int getMaximumCacheSize() {
        return maxCacheSize;
    }

    /**
     * Purges the cache
     */
    @Override
    public void purge() {
        // only purge the dynamic part
        dynamicMap.clear();
    }

    @Override
    public void cleanUp() {
        if (dynamicMap instanceof LRUCache) {
            ((LRUCache) dynamicMap).cleanUp();
        }
    }

    @Override
    public boolean isStatic(String key) {
        return staticMap.containsKey(new EndpointKey(key));
    }

    @Override
    public boolean isDynamic(String key) {
        return dynamicMap.containsKey(new EndpointKey(key));
    }

    @Override
    public void stop() throws Exception {
        ServiceHelper.stopService(staticMap.values());
        ServiceHelper.stopService(values());
        purge();
    }

    @Override
    public String toString() {
        return "EndpointRegistry for " + context.getName() + ", capacity: " + maxCacheSize;
    }
}

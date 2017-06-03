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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ServiceHelper;

/**
 * Default implementation of {@link org.apache.camel.spi.EndpointRegistry}
 */
public class DefaultEndpointRegistry extends LRUCache<EndpointKey, Endpoint> implements EndpointRegistry<EndpointKey> {
    private static final long serialVersionUID = 1L;
    private ConcurrentMap<EndpointKey, Endpoint> staticMap;
    private final CamelContext context;

    public DefaultEndpointRegistry(CamelContext context) {
        // do not stop on eviction, as the endpoint may still be in use
        super(CamelContextHelper.getMaximumEndpointCacheSize(context), CamelContextHelper.getMaximumEndpointCacheSize(context), false);
        // static map to hold endpoints we do not want to be evicted
        this.staticMap = new ConcurrentHashMap<EndpointKey, Endpoint>();
        this.context = context;
    }

    public DefaultEndpointRegistry(CamelContext context, Map<EndpointKey, Endpoint> endpoints) {
        this(context);
        putAll(endpoints);
    }

    @Override
    public void start() throws Exception {
        resetStatistics();
    }

    @Override
    public Endpoint get(Object o) {
        // try static map first
        Endpoint answer = staticMap.get(o);
        if (answer == null) {
            answer = super.get(o);
        } else {
            hits.increment();
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

        answer = super.remove(key);
        if (answer != null) {
            // replace existing
            super.put(key, endpoint);
            return answer;
        }

        // we want endpoints to be static if they are part of setting up or starting routes
        if (context.isSetupRoutes() || context.isStartingRoutes()) {
            answer = staticMap.put(key, endpoint);
        } else {
            answer = super.put(key, endpoint);
        }

        return answer;
    }

    @Override
    public void putAll(Map<? extends EndpointKey, ? extends Endpoint> map) {
        // need to use put instead of putAll to ensure the entries gets added to either static or dynamic map
        for (Map.Entry<? extends EndpointKey, ? extends Endpoint> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean containsKey(Object o) {
        return staticMap.containsKey(o) || super.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return staticMap.containsValue(o) || super.containsValue(o);
    }

    @Override
    public int size() {
        return staticMap.size() + super.size();
    }

    public int staticSize() {
        return staticMap.size();
    }

    @Override
    public int dynamicSize() {
        return super.size();
    }

    @Override
    public boolean isEmpty() {
        return staticMap.isEmpty() && super.isEmpty();
    }

    @Override
    public Endpoint remove(Object o) {
        Endpoint answer = staticMap.remove(o);
        if (answer == null) {
            answer = super.remove(o);
        }
        return answer;
    }

    @Override
    public void clear() {
        staticMap.clear();
        super.clear();
    }

    @Override
    public Set<EndpointKey> keySet() {
        Set<EndpointKey> answer = new LinkedHashSet<EndpointKey>();
        answer.addAll(staticMap.keySet());
        answer.addAll(super.keySet());
        return answer;
    }

    @Override
    public Collection<Endpoint> values() {
        Collection<Endpoint> answer = new ArrayList<Endpoint>();
        answer.addAll(staticMap.values());
        answer.addAll(super.values());
        return answer;
    }

    @Override
    public Set<Entry<EndpointKey, Endpoint>> entrySet() {
        Set<Entry<EndpointKey, Endpoint>> answer = new LinkedHashSet<Entry<EndpointKey, Endpoint>>();
        answer.addAll(staticMap.entrySet());
        answer.addAll(super.entrySet());
        return answer;
    }

    @Override
    public int getMaximumCacheSize() {
        return super.getMaxCacheSize();
    }

    /**
     * Purges the cache
     */
    @Override
    public void purge() {
        // only purge the dynamic part
        super.clear();
    }

    @Override
    public boolean isStatic(String key) {
        return staticMap.containsKey(new EndpointKey(key));
    }

    @Override
    public boolean isDynamic(String key) {
        return super.containsKey(new EndpointKey(key));
    }

    @Override
    public void stop() throws Exception {
        ServiceHelper.stopServices(staticMap.values());
        ServiceHelper.stopServices(values());
        purge();
    }

    @Override
    public String toString() {
        return "EndpointRegistry for " + context.getName() + ", capacity: " + getMaxCacheSize();
    }
}

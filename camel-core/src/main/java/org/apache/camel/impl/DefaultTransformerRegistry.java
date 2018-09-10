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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.CompoundIterator;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUCacheFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Default implementation of {@link org.apache.camel.spi.TransformerRegistry}.
 */
public class DefaultTransformerRegistry extends AbstractMap<TransformerKey, Transformer> implements TransformerRegistry<TransformerKey> {

    private static final long serialVersionUID = 1L;
    private Map<TransformerKey, Transformer> dynamicMap;
    private Map<TransformerKey, Transformer> staticMap;
    private Map<TransformerKey, TransformerKey> aliasMap;
    private final CamelContext context;
    private int maxCacheSize;

    public DefaultTransformerRegistry(CamelContext context) throws Exception {
        this(context, new ArrayList<>());
    }

    public DefaultTransformerRegistry(CamelContext context, List<TransformerDefinition> definitions) throws Exception {
        this.maxCacheSize = CamelContextHelper.getMaximumTransformerCacheSize(context);
        // do not stop on eviction, as the transformer may still be in use
        this.dynamicMap = LRUCacheFactory.newLRUCache(maxCacheSize, maxCacheSize, false);
        // static map to hold transformers we do not want to be evicted
        this.staticMap = new ConcurrentHashMap<>();
        this.aliasMap = new ConcurrentHashMap<>();
        this.context = context;
        
        for (TransformerDefinition def : definitions) {
            Transformer transformer = def.createTransformer(context);
            context.addService(transformer);
            put(createKey(def), transformer);
        }
    }

    @Override
    public Transformer resolveTransformer(TransformerKey key) {
        if (ObjectHelper.isEmpty(key.getScheme()) && key.getTo() == null) {
            return null;
        }
        
        // try exact match
        Transformer answer = get(aliasMap.containsKey(key) ? aliasMap.get(key) : key);
        if (answer != null || ObjectHelper.isNotEmpty(key.getScheme())) {
            return answer;
        }
        
        // try wildcard match for next - add an alias if matched
        TransformerKey alias = null;
        if (key.getFrom() != null && ObjectHelper.isNotEmpty(key.getFrom().getName())) {
            alias = new TransformerKey(new DataType(key.getFrom().getModel()), key.getTo());
            answer = get(alias);
        }
        if (answer == null && ObjectHelper.isNotEmpty(key.getTo().getName())) {
            alias = new TransformerKey(key.getFrom(), new DataType(key.getTo().getModel()));
            answer = get(alias);
        }
        if (answer == null && key.getFrom() != null && ObjectHelper.isNotEmpty(key.getFrom().getName())
            && ObjectHelper.isNotEmpty(key.getTo().getName())) {
            alias = new TransformerKey(new DataType(key.getFrom().getModel()), new DataType(key.getTo().getModel()));
            answer = get(alias);
        }
        if (answer == null && key.getFrom() != null) {
            alias = new TransformerKey(key.getFrom().getModel());
            answer = get(alias);
        }
        if (answer == null) {
            alias = new TransformerKey(key.getTo().getModel());
            answer = get(alias);
        }
        if (answer != null) {
            aliasMap.put(key, alias);
        }
        
        return answer;
    }

    @Override
    public void start() throws Exception {
        if (dynamicMap instanceof LRUCache) {
            ((LRUCache) dynamicMap).resetStatistics();
        }
    }

    @Override
    public Transformer get(Object o) {
        // try static map first
        Transformer answer = staticMap.get(o);
        if (answer == null) {
            answer = dynamicMap.get(o);
        }
        return answer;
    }

    @Override
    public Transformer put(TransformerKey key, Transformer transformer) {
        // at first we must see if the key already exists and then replace it back, so it stays the same spot
        Transformer answer = staticMap.remove(key);
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
        if (context.isSetupRoutes() || context.getRouteController().isStartingRoutes()) {
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

    @Override
    public int dynamicSize() {
        return dynamicMap.size();
    }

    @Override
    public boolean isEmpty() {
        return staticMap.isEmpty() && dynamicMap.isEmpty();
    }

    @Override
    public Transformer remove(Object o) {
        Transformer answer = staticMap.remove(o);
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
    public Set<Entry<TransformerKey, Transformer>> entrySet() {
        return new AbstractSet<Entry<TransformerKey, Transformer>>() {
            @Override
            public Iterator<Entry<TransformerKey, Transformer>> iterator() {
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
        super.clear();
    }

    @Override
    public void cleanUp() {
        if (dynamicMap instanceof LRUCache) {
            ((LRUCache) dynamicMap).cleanUp();
        }
    }

    @Override
    public boolean isStatic(String scheme) {
        return staticMap.containsKey(new TransformerKey(scheme));
    }

    @Override
    public boolean isStatic(DataType from, DataType to) {
        return staticMap.containsKey(new TransformerKey(from, to));
    }

    @Override
    public boolean isDynamic(String scheme) {
        return dynamicMap.containsKey(new TransformerKey(scheme));
    }

    @Override
    public boolean isDynamic(DataType from, DataType to) {
        return dynamicMap.containsKey(new TransformerKey(from, to));
    }

    @Override
    public void stop() throws Exception {
        ServiceHelper.stopServices(staticMap.values());
        ServiceHelper.stopServices(dynamicMap.values());
        purge();
    }

    @Override
    public String toString() {
        return "TransformerRegistry for " + context.getName() + ", capacity: " + maxCacheSize;
    }

    private TransformerKey createKey(TransformerDefinition def) {
        return ObjectHelper.isNotEmpty(def.getScheme()) ? new TransformerKey(def.getScheme())
            : new TransformerKey(new DataType(def.getFromType()), new DataType(def.getToType()));
    }

}

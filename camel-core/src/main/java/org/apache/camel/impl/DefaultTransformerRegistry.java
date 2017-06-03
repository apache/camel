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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Default implementation of {@link org.apache.camel.spi.TransformerRegistry}.
 */
public class DefaultTransformerRegistry extends LRUCache<TransformerKey, Transformer> implements TransformerRegistry<TransformerKey> {
    private static final long serialVersionUID = 1L;
    private ConcurrentMap<TransformerKey, Transformer> staticMap;
    private ConcurrentMap<TransformerKey, TransformerKey> aliasMap;
    private final CamelContext context;

    public DefaultTransformerRegistry(CamelContext context) throws Exception {
        this(context, new ArrayList<>());
    }

    public DefaultTransformerRegistry(CamelContext context, List<TransformerDefinition> definitions) throws Exception {
        // do not stop on eviction, as the transformer may still be in use
        super(CamelContextHelper.getMaximumTransformerCacheSize(context), CamelContextHelper.getMaximumTransformerCacheSize(context), false);
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
        resetStatistics();
    }

    @Override
    public Transformer get(Object o) {
        // try static map first
        Transformer answer = staticMap.get(o);
        if (answer == null) {
            answer = super.get(o);
        } else {
            hits.increment();
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

        answer = super.remove(key);
        if (answer != null) {
            // replace existing
            super.put(key, transformer);
            return answer;
        }

        // we want transformers to be static if they are part of setting up or starting routes
        if (context.isSetupRoutes() || context.isStartingRoutes()) {
            answer = staticMap.put(key, transformer);
        } else {
            answer = super.put(key, transformer);
        }

        return answer;
    }

    @Override
    public void putAll(Map<? extends TransformerKey, ? extends Transformer> map) {
        // need to use put instead of putAll to ensure the entries gets added to either static or dynamic map
        for (Map.Entry<? extends TransformerKey, ? extends Transformer> entry : map.entrySet()) {
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
    public Transformer remove(Object o) {
        Transformer answer = staticMap.remove(o);
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
    public Set<TransformerKey> keySet() {
        Set<TransformerKey> answer = new LinkedHashSet<>();
        answer.addAll(staticMap.keySet());
        answer.addAll(super.keySet());
        return answer;
    }

    @Override
    public Collection<Transformer> values() {
        Collection<Transformer> answer = new ArrayList<>();
        answer.addAll(staticMap.values());
        answer.addAll(super.values());
        return answer;
    }

    @Override
    public Set<Entry<TransformerKey, Transformer>> entrySet() {
        Set<Entry<TransformerKey, Transformer>> answer = new LinkedHashSet<>();
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
    public boolean isStatic(String scheme) {
        return staticMap.containsKey(new TransformerKey(scheme));
    }

    @Override
    public boolean isStatic(DataType from, DataType to) {
        return staticMap.containsKey(new TransformerKey(from, to));
    }

    @Override
    public boolean isDynamic(String scheme) {
        return super.containsKey(new TransformerKey(scheme));
    }

    @Override
    public boolean isDynamic(DataType from, DataType to) {
        return super.containsKey(new TransformerKey(from, to));
    }

    @Override
    public void stop() throws Exception {
        ServiceHelper.stopServices(staticMap.values());
        ServiceHelper.stopServices(values());
        purge();
    }

    @Override
    public String toString() {
        return "TransformerRegistry for " + context.getName() + ", capacity: " + getMaxCacheSize();
    }

    private TransformerKey createKey(TransformerDefinition def) {
        return ObjectHelper.isNotEmpty(def.getScheme()) ? new TransformerKey(def.getScheme())
            : new TransformerKey(new DataType(def.getFromType()), new DataType(def.getToType()));
    }

}

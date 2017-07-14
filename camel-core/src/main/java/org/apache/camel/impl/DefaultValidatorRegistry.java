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
import org.apache.camel.impl.validator.ValidatorKey;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Default implementation of {@link org.apache.camel.spi.ValidatorRegistry}.
 */
public class DefaultValidatorRegistry extends LRUCache<ValidatorKey, Validator> implements ValidatorRegistry<ValidatorKey> {
    private static final long serialVersionUID = 1L;
    private ConcurrentMap<ValidatorKey, Validator> staticMap;
    private final CamelContext context;

    public DefaultValidatorRegistry(CamelContext context) throws Exception {
        this(context, new ArrayList<>());
    }

    public DefaultValidatorRegistry(CamelContext context, List<ValidatorDefinition> definitions) throws Exception {
        // do not stop on eviction, as the validator may still be in use
        super(CamelContextHelper.getMaximumValidatorCacheSize(context), CamelContextHelper.getMaximumValidatorCacheSize(context), false);
        // static map to hold validator we do not want to be evicted
        this.staticMap = new ConcurrentHashMap<>();
        this.context = context;
        
        for (ValidatorDefinition def : definitions) {
            Validator validator = def.createValidator(context);
            context.addService(validator);
            put(new ValidatorKey(new DataType(def.getType())), validator);
        }
    }

    public Validator resolveValidator(ValidatorKey key) {
        Validator answer = get(key);
        if (answer == null && ObjectHelper.isNotEmpty(key.getType().getName())) {
            answer = get(new ValidatorKey(new DataType(key.getType().getModel())));
        }
        return answer;
    }

    @Override
    public void start() throws Exception {
        resetStatistics();
    }

    @Override
    public Validator get(Object o) {
        // try static map first
        Validator answer = staticMap.get(o);
        if (answer == null) {
            answer = super.get(o);
        } else {
            hits.increment();
        }
        return answer;
    }

    @Override
    public Validator put(ValidatorKey key, Validator validator) {
        // at first we must see if the key already exists and then replace it back, so it stays the same spot
        Validator answer = staticMap.remove(key);
        if (answer != null) {
            // replace existing
            staticMap.put(key, validator);
            return answer;
        }

        answer = super.remove(key);
        if (answer != null) {
            // replace existing
            super.put(key, validator);
            return answer;
        }

        // we want validators to be static if they are part of setting up or starting routes
        if (context.isSetupRoutes() || context.isStartingRoutes()) {
            answer = staticMap.put(key, validator);
        } else {
            answer = super.put(key, validator);
        }

        return answer;
    }

    @Override
    public void putAll(Map<? extends ValidatorKey, ? extends Validator> map) {
        // need to use put instead of putAll to ensure the entries gets added to either static or dynamic map
        for (Map.Entry<? extends ValidatorKey, ? extends Validator> entry : map.entrySet()) {
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
    public Validator remove(Object o) {
        Validator answer = staticMap.remove(o);
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
    public Set<ValidatorKey> keySet() {
        Set<ValidatorKey> answer = new LinkedHashSet<>();
        answer.addAll(staticMap.keySet());
        answer.addAll(super.keySet());
        return answer;
    }

    @Override
    public Collection<Validator> values() {
        Collection<Validator> answer = new ArrayList<>();
        answer.addAll(staticMap.values());
        answer.addAll(super.values());
        return answer;
    }

    @Override
    public Set<Entry<ValidatorKey, Validator>> entrySet() {
        Set<Entry<ValidatorKey, Validator>> answer = new LinkedHashSet<>();
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
    public boolean isStatic(DataType type) {
        return staticMap.containsKey(new ValidatorKey(type));
    }

    @Override
    public boolean isDynamic(DataType type) {
        return super.containsKey(new ValidatorKey(type));
    }

    @Override
    public void stop() throws Exception {
        ServiceHelper.stopServices(staticMap.values());
        ServiceHelper.stopServices(values());
        purge();
    }

    @Override
    public String toString() {
        return "ValidatorRegistry for " + context.getName() + ", capacity: " + getMaxCacheSize();
    }

}

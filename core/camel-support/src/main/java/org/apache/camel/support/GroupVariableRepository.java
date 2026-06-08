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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StreamCache;
import org.apache.camel.StreamCacheException;
import org.apache.camel.spi.BrowsableVariableRepository;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.VariableRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;

/**
 * Group {@link VariableRepository} which stores variables in-memory per named group.
 * <p>
 * Variables are scoped by group name using the syntax {@code groupId:variableName}. This allows sharing variables
 * across a subset of routes — wider than per-route but narrower than global scope.
 *
 * @since 4.21
 */
public final class GroupVariableRepository extends ServiceSupport implements BrowsableVariableRepository, CamelContextAware {

    private final Map<String, Map<String, Object>> groups = new ConcurrentHashMap<>();
    private CamelContext camelContext;
    private StreamCachingStrategy strategy;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Object getVariable(String name) {
        String id = StringHelper.before(name, ":");
        String key = StringHelper.after(name, ":");
        if (id == null || key == null) {
            throw new IllegalArgumentException("Name must be groupId:name syntax");
        }
        Object answer = null;
        Map<String, Object> variables = groups.get(id);
        if (variables != null) {
            answer = variables.get(key);
        }
        if (answer instanceof StreamCache sc) {
            // reset so the cache is ready to be used as a variable
            sc.reset();
        }
        return answer;
    }

    @Override
    public void setVariable(String name, Object value) {
        String id = StringHelper.before(name, ":");
        String key = StringHelper.after(name, ":");
        if (id == null || key == null) {
            throw new IllegalArgumentException("Name must be groupId:name syntax");
        }

        if (value != null && strategy != null) {
            StreamCache sc = convertToStreamCache(value);
            if (sc != null) {
                value = sc;
            }
        }
        if (value != null) {
            Map<String, Object> variables = groups.computeIfAbsent(id, s -> new ConcurrentHashMap<>(8));
            // avoid the NullPointException
            variables.put(key, value);
        } else {
            // if the value is null, we just remove the key from the map
            Map<String, Object> variables = groups.get(id);
            if (variables != null) {
                variables.remove(key);
            }
        }
    }

    public boolean hasVariables() {
        for (var vars : groups.values()) {
            if (!vars.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        int size = 0;
        for (var vars : groups.values()) {
            size += vars.size();
        }
        return size;
    }

    public Stream<String> names() {
        List<String> answer = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : groups.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> values = entry.getValue();
            for (var e : values.entrySet()) {
                answer.add(id + ":" + e.getKey());
            }
        }
        return answer.stream();
    }

    public Map<String, Object> getVariables() {
        Map<String, Object> answer = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : groups.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> values = entry.getValue();
            for (var e : values.entrySet()) {
                answer.put(id + ":" + e.getKey(), e.getValue());
            }
        }
        return answer;
    }

    /**
     * Gets the ids of all groups that currently have variables.
     */
    public Set<String> getGroupIds() {
        return Collections.unmodifiableSet(groups.keySet());
    }

    public void clear() {
        groups.clear();
    }

    @Override
    public String getId() {
        return "group";
    }

    @Override
    public Object removeVariable(String name) {
        String id = StringHelper.before(name, ":");
        String key = StringHelper.after(name, ":");
        if (id == null || key == null) {
            throw new IllegalArgumentException("Name must be groupId:name syntax");
        }

        Map<String, Object> variables = groups.get(id);
        if (variables != null) {
            if ("*".equals(key)) {
                variables.clear();
                groups.remove(id);
                return null;
            } else {
                return variables.remove(key);
            }
        }
        return null;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (camelContext != null && camelContext.isStreamCaching()) {
            strategy = camelContext.getStreamCachingStrategy();
        }
    }

    private StreamCache convertToStreamCache(Object body) {
        // check if body is already cached
        if (body == null) {
            return null;
        } else if (body instanceof StreamCache sc) {
            // reset so the cache is ready to be used before processing
            sc.reset();
            return sc;
        }
        return tryStreamCache(body);
    }

    private StreamCache tryStreamCache(Object body) {
        try {
            // cache the body and if we could do that replace it as the new body
            return strategy.cache(body);
        } catch (Exception e) {
            throw new StreamCacheException(body, e);
        }
    }

}

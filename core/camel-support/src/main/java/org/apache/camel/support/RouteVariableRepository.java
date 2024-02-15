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
import java.util.List;
import java.util.Map;
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
 * Route {@link VariableRepository} which stores variables in-memory per route.
 */
public final class RouteVariableRepository extends ServiceSupport implements BrowsableVariableRepository, CamelContextAware {

    private final Map<String, Map<String, Object>> routes = new ConcurrentHashMap<>();
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
            throw new IllegalArgumentException("Name must be routeId:name syntax");
        }
        Object answer = null;
        Map<String, Object> variables = routes.get(id);
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
            throw new IllegalArgumentException("Name must be routeId:name syntax");
        }

        if (value != null && strategy != null) {
            StreamCache sc = convertToStreamCache(value);
            if (sc != null) {
                value = sc;
            }
        }
        if (value != null) {
            Map<String, Object> variables = routes.computeIfAbsent(id, s -> new ConcurrentHashMap<>(8));
            // avoid the NullPointException
            variables.put(key, value);
        } else {
            // if the value is null, we just remove the key from the map
            Map<String, Object> variables = routes.get(id);
            if (variables != null) {
                variables.remove(key);
            }
        }
    }

    public boolean hasVariables() {
        for (var vars : routes.values()) {
            if (!vars.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        int size = 0;
        for (var vars : routes.values()) {
            size += vars.size();
        }
        return size;
    }

    public Stream<String> names() {
        List<String> answer = new ArrayList<>();
        for (var id : routes.keySet()) {
            var values = routes.get(id);
            for (var e : values.entrySet()) {
                answer.add(id + ":" + e.getKey());
            }
        }
        return answer.stream();
    }

    public Map<String, Object> getVariables() {
        Map<String, Object> answer = new ConcurrentHashMap<>();
        for (var id : routes.keySet()) {
            var values = routes.get(id);
            for (var e : values.entrySet()) {
                answer.put(id + ":" + e.getKey(), e.getValue());
            }
        }
        return answer;
    }

    public void clear() {
        routes.clear();
    }

    @Override
    public String getId() {
        return "route";
    }

    @Override
    public Object removeVariable(String name) {
        String id = StringHelper.before(name, ":");
        String key = StringHelper.after(name, ":");
        if (id == null || key == null) {
            throw new IllegalArgumentException("Name must be routeId:name syntax");
        }

        Map<String, Object> variables = routes.get(id);
        if (variables != null) {
            if ("*".equals(key)) {
                variables.clear();
                routes.remove(id);
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

    protected StreamCache convertToStreamCache(Object body) {
        // check if body is already cached
        if (body == null) {
            return null;
        } else if (body instanceof StreamCache) {
            StreamCache sc = (StreamCache) body;
            // reset so the cache is ready to be used before processing
            sc.reset();
            return sc;
        }
        return tryStreamCache(body);
    }

    protected StreamCache tryStreamCache(Object body) {
        try {
            // cache the body and if we could do that replace it as the new body
            return strategy.cache(body);
        } catch (Exception e) {
            throw new StreamCacheException(body, e);
        }
    }

}

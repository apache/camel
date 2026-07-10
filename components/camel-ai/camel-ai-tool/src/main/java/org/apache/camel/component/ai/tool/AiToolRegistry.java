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
package org.apache.camel.component.ai.tool;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;

/**
 * CamelContext-scoped registry mapping tags to {@link AiToolSpec} instances. AI components (LangChain4j, Spring AI)
 * read from this registry to discover tools registered via the {@code ai-tool} consumer endpoint.
 * <p>
 * Each {@link CamelContext} gets its own registry instance, registered as a context plugin. Use
 * {@link #getOrCreate(CamelContext)} to obtain the instance for a given context.
 * <p>
 * Replaces the duplicated {@code CamelToolExecutorCache} singletons from {@code camel-langchain4j-tools} and
 * {@code camel-spring-ai-tools}.
 *
 * @since 4.22
 */
public final class AiToolRegistry {

    private static final ReentrantLock FACTORY_LOCK = new ReentrantLock();

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Set<AiToolSpec>> tools;
    private final Set<AiToolSpec> defaultTools;

    AiToolRegistry() {
        tools = new HashMap<>();
        defaultTools = new LinkedHashSet<>();
    }

    /**
     * Returns the {@link AiToolRegistry} for the given {@link CamelContext}, creating and registering one as a context
     * plugin if it does not yet exist.
     */
    public static AiToolRegistry getOrCreate(CamelContext context) {
        FACTORY_LOCK.lock();
        try {
            AiToolRegistry registry = context.getCamelContextExtension()
                    .getContextPlugin(AiToolRegistry.class);
            if (registry == null) {
                registry = new AiToolRegistry();
                context.getCamelContextExtension()
                        .addContextPlugin(AiToolRegistry.class, registry);
            }
            return registry;
        } finally {
            FACTORY_LOCK.unlock();
        }
    }

    public void put(String tag, AiToolSpec spec) {
        lock.lock();
        try {
            Set<AiToolSpec> set = tools.computeIfAbsent(tag, k -> new LinkedHashSet<>());
            for (AiToolSpec existing : set) {
                if (existing.getName().equals(spec.getName()) && existing != spec) {
                    throw new IllegalArgumentException(
                            "Duplicate tool name '" + spec.getName() + "' under tag '" + tag
                                                       + "': tool names must be unique per tag");
                }
            }
            set.add(spec);
        } finally {
            lock.unlock();
        }
    }

    public void remove(String tag, AiToolSpec spec) {
        lock.lock();
        try {
            Set<AiToolSpec> set = tools.get(tag);
            if (set != null) {
                set.remove(spec);
                if (set.isEmpty()) {
                    tools.remove(tag);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void putDefault(AiToolSpec spec) {
        lock.lock();
        try {
            for (AiToolSpec existing : defaultTools) {
                if (existing.getName().equals(spec.getName()) && existing != spec) {
                    throw new IllegalArgumentException(
                            "Duplicate tool name '" + spec.getName()
                                                       + "' in the default pool: tool names must be unique");
                }
            }
            defaultTools.add(spec);
        } finally {
            lock.unlock();
        }
    }

    public void removeDefault(AiToolSpec spec) {
        lock.lock();
        try {
            defaultTools.remove(spec);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns tools registered for a specific tag, merged with the default pool (tools with no tags).
     */
    public Set<AiToolSpec> getToolsByTag(String tag) {
        lock.lock();
        try {
            Set<AiToolSpec> result = new LinkedHashSet<>(defaultTools);
            Set<AiToolSpec> tagTools = tools.get(tag);
            if (tagTools != null) {
                result.addAll(tagTools);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all tools across all tags and the default pool.
     */
    public Set<AiToolSpec> getAllTools() {
        lock.lock();
        try {
            Set<AiToolSpec> result = new LinkedHashSet<>(defaultTools);
            for (Set<AiToolSpec> tagTools : tools.values()) {
                result.addAll(tagTools);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Set<AiToolSpec>> getTools() {
        lock.lock();
        try {
            Map<String, Set<AiToolSpec>> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Set<AiToolSpec>> entry : tools.entrySet()) {
                snapshot.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            return Map.copyOf(snapshot);
        } finally {
            lock.unlock();
        }
    }

    public Set<AiToolSpec> getDefaultTools() {
        lock.lock();
        try {
            return new LinkedHashSet<>(defaultTools);
        } finally {
            lock.unlock();
        }
    }
}

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CamelContext-scoped registry mapping tags to {@link AiToolSpec} instances. AI components (LangChain4j, Spring AI)
 * read from this registry to discover tools registered via the {@code ai-tool} consumer endpoint.
 * <p>
 * Each {@link CamelContext} gets its own registry instance, registered as a context plugin. Use
 * {@link #getOrCreate(CamelContext)} to obtain the instance for a given context.
 * <p>
 * Adapters that need to react to tools appearing or disappearing (e.g. to push MCP {@code tools/list_changed}
 * notifications) can register an {@link AiToolRegistryListener} instead of polling.
 * <p>
 * Replaces the duplicated {@code CamelToolExecutorCache} singletons from {@code camel-langchain4j-tools} and
 * {@code camel-spring-ai-tools}.
 *
 * @since 4.22
 */
public final class AiToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolRegistry.class);

    private static final ReentrantLock FACTORY_LOCK = new ReentrantLock();

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Set<AiToolSpec>> tools;
    private final Set<AiToolSpec> defaultTools;
    private final List<AiToolRegistryListener> listeners = new CopyOnWriteArrayList<>();

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
        boolean added;
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
            added = set.add(spec);
        } finally {
            lock.unlock();
        }
        if (added) {
            notifyRegistered(tag, spec);
        }
    }

    public void remove(String tag, AiToolSpec spec) {
        boolean removed = false;
        lock.lock();
        try {
            Set<AiToolSpec> set = tools.get(tag);
            if (set != null) {
                removed = set.remove(spec);
                if (set.isEmpty()) {
                    tools.remove(tag);
                }
            }
        } finally {
            lock.unlock();
        }
        if (removed) {
            notifyDeregistered(tag, spec);
        }
    }

    public void putDefault(AiToolSpec spec) {
        boolean added;
        lock.lock();
        try {
            for (AiToolSpec existing : defaultTools) {
                if (existing.getName().equals(spec.getName()) && existing != spec) {
                    throw new IllegalArgumentException(
                            "Duplicate tool name '" + spec.getName()
                                                       + "' in the default pool: tool names must be unique");
                }
            }
            added = defaultTools.add(spec);
        } finally {
            lock.unlock();
        }
        if (added) {
            notifyRegistered(null, spec);
        }
    }

    public void removeDefault(AiToolSpec spec) {
        boolean removed;
        lock.lock();
        try {
            removed = defaultTools.remove(spec);
        } finally {
            lock.unlock();
        }
        if (removed) {
            notifyDeregistered(null, spec);
        }
    }

    /**
     * Adds a listener notified on tool registration changes. See {@link AiToolRegistryListener} for the callback
     * contract and the subscribe-then-snapshot idiom to observe current state without missing events.
     */
    public void addListener(AiToolRegistryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AiToolRegistryListener listener) {
        listeners.remove(listener);
    }

    private void notifyRegistered(String tag, AiToolSpec spec) {
        for (AiToolRegistryListener listener : listeners) {
            try {
                listener.toolRegistered(tag, spec);
            } catch (Exception e) {
                LOG.warn("AiToolRegistryListener {} failed on toolRegistered for tool '{}': {}",
                        listener, spec.getName(), e.getMessage(), e);
            }
        }
    }

    private void notifyDeregistered(String tag, AiToolSpec spec) {
        for (AiToolRegistryListener listener : listeners) {
            try {
                listener.toolDeregistered(tag, spec);
            } catch (Exception e) {
                LOG.warn("AiToolRegistryListener {} failed on toolDeregistered for tool '{}': {}",
                        listener, spec.getName(), e.getMessage(), e);
            }
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

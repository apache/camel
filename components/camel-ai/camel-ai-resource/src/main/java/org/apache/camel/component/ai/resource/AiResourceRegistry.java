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
package org.apache.camel.component.ai.resource;

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
 * CamelContext-scoped registry mapping tags to {@link AiResourceSpec} instances. Adapters that expose Camel routes as
 * AI resources (today {@code camel-mcp-server}) read from this registry to discover resources registered via the
 * {@code ai-resource} consumer endpoint.
 * <p>
 * Each {@link CamelContext} gets its own registry instance, registered as a context plugin. Use
 * {@link #getOrCreate(CamelContext)} to obtain the instance for a given context.
 * <p>
 * Adapters that need to react to resources appearing or disappearing (e.g. to push MCP {@code resources/list_changed}
 * notifications) can register an {@link AiResourceRegistryListener} instead of polling.
 *
 * @since 4.23
 */
public final class AiResourceRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AiResourceRegistry.class);

    private static final ReentrantLock FACTORY_LOCK = new ReentrantLock();

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Set<AiResourceSpec>> resources = new HashMap<>();
    private final Set<AiResourceSpec> defaultResources = new LinkedHashSet<>();
    private final List<AiResourceRegistryListener> listeners = new CopyOnWriteArrayList<>();

    AiResourceRegistry() {
    }

    /**
     * Returns the {@link AiResourceRegistry} for the given {@link CamelContext}, creating and registering one as a
     * context plugin if it does not yet exist.
     */
    public static AiResourceRegistry getOrCreate(CamelContext context) {
        FACTORY_LOCK.lock();
        try {
            AiResourceRegistry registry = context.getCamelContextExtension()
                    .getContextPlugin(AiResourceRegistry.class);
            if (registry == null) {
                registry = new AiResourceRegistry();
                context.getCamelContextExtension()
                        .addContextPlugin(AiResourceRegistry.class, registry);
            }
            return registry;
        } finally {
            FACTORY_LOCK.unlock();
        }
    }

    public void put(String tag, AiResourceSpec spec) {
        boolean added;
        lock.lock();
        try {
            Set<AiResourceSpec> set = resources.computeIfAbsent(tag, k -> new LinkedHashSet<>());
            for (AiResourceSpec existing : set) {
                if (existing.getUri().equals(spec.getUri()) && existing != spec) {
                    throw new IllegalArgumentException(
                            "Duplicate resource uri '" + spec.getUri() + "' under tag '" + tag
                                                       + "': resource uris must be unique per tag");
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

    public void remove(String tag, AiResourceSpec spec) {
        boolean removed = false;
        lock.lock();
        try {
            Set<AiResourceSpec> set = resources.get(tag);
            if (set != null) {
                removed = set.remove(spec);
                if (set.isEmpty()) {
                    resources.remove(tag);
                }
            }
        } finally {
            lock.unlock();
        }
        if (removed) {
            notifyDeregistered(tag, spec);
        }
    }

    public void putDefault(AiResourceSpec spec) {
        boolean added;
        lock.lock();
        try {
            for (AiResourceSpec existing : defaultResources) {
                if (existing.getUri().equals(spec.getUri()) && existing != spec) {
                    throw new IllegalArgumentException(
                            "Duplicate resource uri '" + spec.getUri()
                                                       + "' in the default pool: resource uris must be unique");
                }
            }
            added = defaultResources.add(spec);
        } finally {
            lock.unlock();
        }
        if (added) {
            notifyRegistered(null, spec);
        }
    }

    public void removeDefault(AiResourceSpec spec) {
        boolean removed;
        lock.lock();
        try {
            removed = defaultResources.remove(spec);
        } finally {
            lock.unlock();
        }
        if (removed) {
            notifyDeregistered(null, spec);
        }
    }

    /**
     * Adds a listener notified on resource registration changes. See {@link AiResourceRegistryListener} for the
     * callback contract; add the listener before reading a snapshot to observe current state without missing events.
     */
    public void addListener(AiResourceRegistryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AiResourceRegistryListener listener) {
        listeners.remove(listener);
    }

    private void notifyRegistered(String tag, AiResourceSpec spec) {
        for (AiResourceRegistryListener listener : listeners) {
            try {
                listener.resourceRegistered(tag, spec);
            } catch (Exception e) {
                LOG.warn("AiResourceRegistryListener {} failed on resourceRegistered for resource '{}': {}",
                        listener, spec.getUri(), e.getMessage(), e);
            }
        }
    }

    private void notifyDeregistered(String tag, AiResourceSpec spec) {
        for (AiResourceRegistryListener listener : listeners) {
            try {
                listener.resourceDeregistered(tag, spec);
            } catch (Exception e) {
                LOG.warn("AiResourceRegistryListener {} failed on resourceDeregistered for resource '{}': {}",
                        listener, spec.getUri(), e.getMessage(), e);
            }
        }
    }

    /**
     * Returns resources registered for a specific tag, merged with the default pool (resources with no tags).
     */
    public Set<AiResourceSpec> getResourcesByTag(String tag) {
        lock.lock();
        try {
            Set<AiResourceSpec> result = new LinkedHashSet<>(defaultResources);
            Set<AiResourceSpec> tagged = resources.get(tag);
            if (tagged != null) {
                result.addAll(tagged);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all resources across all tags and the default pool.
     */
    public Set<AiResourceSpec> getAllResources() {
        lock.lock();
        try {
            Set<AiResourceSpec> result = new LinkedHashSet<>(defaultResources);
            for (Set<AiResourceSpec> tagged : resources.values()) {
                result.addAll(tagged);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Set<AiResourceSpec>> getResources() {
        lock.lock();
        try {
            Map<String, Set<AiResourceSpec>> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Set<AiResourceSpec>> entry : resources.entrySet()) {
                snapshot.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            return Map.copyOf(snapshot);
        } finally {
            lock.unlock();
        }
    }

    public Set<AiResourceSpec> getDefaultResources() {
        lock.lock();
        try {
            return new LinkedHashSet<>(defaultResources);
        } finally {
            lock.unlock();
        }
    }
}

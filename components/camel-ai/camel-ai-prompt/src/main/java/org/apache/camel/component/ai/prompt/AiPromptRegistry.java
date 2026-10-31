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
package org.apache.camel.component.ai.prompt;

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
 * CamelContext-scoped registry mapping tags to prompt specifications.
 *
 * @since 4.24
 */
public final class AiPromptRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AiPromptRegistry.class);
    private static final ReentrantLock FACTORY_LOCK = new ReentrantLock();

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Set<AiPromptSpec>> prompts = new HashMap<>();
    private final Set<AiPromptSpec> defaultPrompts = new LinkedHashSet<>();
    private final List<AiPromptRegistryListener> listeners = new CopyOnWriteArrayList<>();

    AiPromptRegistry() {
    }

    public static AiPromptRegistry getOrCreate(CamelContext context) {
        FACTORY_LOCK.lock();
        try {
            AiPromptRegistry registry = context.getCamelContextExtension().getContextPlugin(AiPromptRegistry.class);
            if (registry == null) {
                registry = new AiPromptRegistry();
                context.getCamelContextExtension().addContextPlugin(AiPromptRegistry.class, registry);
            }
            return registry;
        } finally {
            FACTORY_LOCK.unlock();
        }
    }

    public void put(String tag, AiPromptSpec spec) {
        putAll(new String[] { tag }, spec);
    }

    /**
     * Registers a prompt under all supplied tags as one atomic registry operation. No tag is modified when a duplicate
     * prompt name is found in any target tag.
     */
    void putAll(String[] tags, AiPromptSpec spec) {
        Set<String> uniqueTags = new LinkedHashSet<>(List.of(tags));
        List<String> addedTags = new java.util.ArrayList<>();

        lock.lock();
        try {
            // Validate every target tag before changing the registry.
            for (String tag : uniqueTags) {
                Set<AiPromptSpec> set = prompts.get(tag);
                if (set == null) {
                    continue;
                }
                for (AiPromptSpec existing : set) {
                    if (existing.getName().equals(spec.getName()) && existing != spec) {
                        throw new IllegalArgumentException(
                                "Duplicate prompt name '" + spec.getName() + "' under tag '" + tag + "'");
                    }
                }
            }

            // All validation passed; apply the complete registration.
            for (String tag : uniqueTags) {
                Set<AiPromptSpec> set = prompts.computeIfAbsent(tag, ignored -> new LinkedHashSet<>());
                if (set.add(spec)) {
                    addedTags.add(tag);
                }
            }
        } finally {
            lock.unlock();
        }

        // Notify listeners only after the registry contains the complete set.
        for (String tag : addedTags) {
            notifyRegistered(tag, spec);
        }
    }

    public void remove(String tag, AiPromptSpec spec) {
        boolean removed = false;
        lock.lock();
        try {
            Set<AiPromptSpec> set = prompts.get(tag);
            if (set != null) {
                removed = set.remove(spec);
                if (set.isEmpty()) {
                    prompts.remove(tag);
                }
            }
        } finally {
            lock.unlock();
        }
        if (removed) {
            notifyDeregistered(tag, spec);
        }
    }

    public void putDefault(AiPromptSpec spec) {
        boolean added;
        lock.lock();
        try {
            for (AiPromptSpec existing : defaultPrompts) {
                if (existing.getName().equals(spec.getName()) && existing != spec) {
                    throw new IllegalArgumentException(
                            "Duplicate prompt name '" + spec.getName() + "' in default pool");
                }
            }
            added = defaultPrompts.add(spec);
        } finally {
            lock.unlock();
        }
        if (added) {
            notifyRegistered(null, spec);
        }
    }

    public void removeDefault(AiPromptSpec spec) {
        boolean removed;
        lock.lock();
        try {
            removed = defaultPrompts.remove(spec);
        } finally {
            lock.unlock();
        }
        if (removed) {
            notifyDeregistered(null, spec);
        }
    }

    public void addListener(AiPromptRegistryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AiPromptRegistryListener listener) {
        listeners.remove(listener);
    }

    private void notifyRegistered(String tag, AiPromptSpec spec) {
        for (AiPromptRegistryListener listener : listeners) {
            try {
                listener.promptRegistered(tag, spec);
            } catch (Exception e) {
                LOG.warn("AiPromptRegistryListener {} failed on promptRegistered for '{}': {}", listener,
                        spec.getName(), e.getMessage(), e);
            }
        }
    }

    private void notifyDeregistered(String tag, AiPromptSpec spec) {
        for (AiPromptRegistryListener listener : listeners) {
            try {
                listener.promptDeregistered(tag, spec);
            } catch (Exception e) {
                LOG.warn("AiPromptRegistryListener {} failed on promptDeregistered for '{}': {}", listener,
                        spec.getName(), e.getMessage(), e);
            }
        }
    }

    public Set<AiPromptSpec> getPromptsByTag(String tag) {
        lock.lock();
        try {
            Set<AiPromptSpec> result = new LinkedHashSet<>(defaultPrompts);
            Set<AiPromptSpec> tagged = prompts.get(tag);
            if (tagged != null) {
                result.addAll(tagged);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    public Set<AiPromptSpec> getAllPrompts() {
        lock.lock();
        try {
            Set<AiPromptSpec> result = new LinkedHashSet<>(defaultPrompts);
            prompts.values().forEach(result::addAll);
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a defensive snapshot of tagged prompts. The default pool is intentionally not included in this map.
     */
    public Map<String, Set<AiPromptSpec>> getPrompts() {
        lock.lock();
        try {
            Map<String, Set<AiPromptSpec>> snapshot = new LinkedHashMap<>();
            prompts.forEach((tag, specs) -> snapshot.put(tag, new LinkedHashSet<>(specs)));
            return Map.copyOf(snapshot);
        } finally {
            lock.unlock();
        }
    }

    public Set<AiPromptSpec> getDefaultPrompts() {
        lock.lock();
        try {
            return new LinkedHashSet<>(defaultPrompts);
        } finally {
            lock.unlock();
        }
    }
}

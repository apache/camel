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
package org.apache.camel.component.ai.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * Replaces the duplicated {@code CamelToolExecutorCache} singletons from {@code camel-langchain4j-tools} and
 * {@code camel-spring-ai-tools}.
 *
 * @since 4.22
 */
public final class AiToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolRegistry.class);

    private final Map<String, Set<AiToolSpec>> tools;
    private final Set<AiToolSpec> defaultTools;

    AiToolRegistry() {
        tools = new ConcurrentHashMap<>();
        defaultTools = Collections.synchronizedSet(new LinkedHashSet<>());
    }

    /**
     * Returns the {@link AiToolRegistry} for the given {@link CamelContext}, creating and registering one as a context
     * plugin if it does not yet exist.
     */
    public static AiToolRegistry getOrCreate(CamelContext context) {
        synchronized (context) {
            AiToolRegistry registry = context.getCamelContextExtension()
                    .getContextPlugin(AiToolRegistry.class);
            if (registry == null) {
                registry = new AiToolRegistry();
                context.getCamelContextExtension()
                        .addContextPlugin(AiToolRegistry.class, registry);
            }
            return registry;
        }
    }

    public void put(String tag, AiToolSpec spec) {
        tools.compute(tag, (k, set) -> {
            if (set == null) {
                set = Collections.synchronizedSet(new LinkedHashSet<>());
            }
            synchronized (set) {
                for (AiToolSpec existing : set) {
                    if (existing.getName().equals(spec.getName()) && existing != spec) {
                        LOG.warn("Duplicate toolName '{}' under tag '{}' -- the LLM adapter will see both "
                                 + "and may not be able to distinguish them",
                                spec.getName(), tag);
                        break;
                    }
                }
                set.add(spec);
            }
            return set;
        });
    }

    public void remove(String tag, AiToolSpec spec) {
        tools.compute(tag, (k, set) -> {
            if (set != null) {
                synchronized (set) {
                    set.remove(spec);
                    if (set.isEmpty()) {
                        return null;
                    }
                }
            }
            return set;
        });
    }

    public void putDefault(AiToolSpec spec) {
        synchronized (defaultTools) {
            for (AiToolSpec existing : defaultTools) {
                if (existing.getName().equals(spec.getName()) && existing != spec) {
                    LOG.warn("Duplicate toolName '{}' in the default pool -- the LLM adapter will see both "
                             + "and may not be able to distinguish them",
                            spec.getName());
                    break;
                }
            }
            defaultTools.add(spec);
        }
    }

    public void removeDefault(AiToolSpec spec) {
        defaultTools.remove(spec);
    }

    /**
     * Returns tools registered for a specific tag, merged with the default pool (tools with no tags).
     */
    public Set<AiToolSpec> getToolsByTag(String tag) {
        Set<AiToolSpec> result;
        synchronized (defaultTools) {
            result = new LinkedHashSet<>(defaultTools);
        }
        Set<AiToolSpec> tagTools = tools.get(tag);
        if (tagTools != null) {
            synchronized (tagTools) {
                result.addAll(tagTools);
            }
        }
        return result;
    }

    /**
     * Returns all tools across all tags and the default pool.
     */
    public Set<AiToolSpec> getAllTools() {
        Set<AiToolSpec> result;
        synchronized (defaultTools) {
            result = new LinkedHashSet<>(defaultTools);
        }
        for (Set<AiToolSpec> tagTools : tools.values()) {
            synchronized (tagTools) {
                result.addAll(tagTools);
            }
        }
        return result;
    }

    public Map<String, Set<AiToolSpec>> getTools() {
        Map<String, Set<AiToolSpec>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Set<AiToolSpec>> entry : tools.entrySet()) {
            synchronized (entry.getValue()) {
                snapshot.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public Set<AiToolSpec> getDefaultTools() {
        synchronized (defaultTools) {
            return new LinkedHashSet<>(defaultTools);
        }
    }
}

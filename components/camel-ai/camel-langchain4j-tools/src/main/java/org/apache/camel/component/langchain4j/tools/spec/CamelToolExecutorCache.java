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
package org.apache.camel.component.langchain4j.tools.spec;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches Tools Specification and Consumer route reference by the chatId, so that different chats can have different
 * Tool implementation. Also maintains a separate cache for searchable (non-exposed) tools.
 */
public final class CamelToolExecutorCache {

    private Map<String, Set<CamelToolSpecification>> tools;
    private Map<String, Set<CamelToolSpecification>> searchableTools;

    private CamelToolExecutorCache() {
        tools = new ConcurrentHashMap<>();
        searchableTools = new ConcurrentHashMap<>();
    }

    private static final class SingletonHolder {
        private static final CamelToolExecutorCache INSTANCE = new CamelToolExecutorCache();
    }

    public static CamelToolExecutorCache getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public void put(String chatId, CamelToolSpecification specification) {
        if (tools.get(chatId) != null) {
            tools.get(chatId).add(specification);
        } else {
            Set<CamelToolSpecification> camelToolSpecifications = new LinkedHashSet<>();
            camelToolSpecifications.add(specification);
            tools.put(chatId, camelToolSpecifications);
        }
    }

    public void putSearchable(String chatId, CamelToolSpecification specification) {
        if (searchableTools.get(chatId) != null) {
            searchableTools.get(chatId).add(specification);
        } else {
            Set<CamelToolSpecification> camelToolSpecifications = new LinkedHashSet<>();
            camelToolSpecifications.add(specification);
            searchableTools.put(chatId, camelToolSpecifications);
        }
    }

    /**
     * Removes a specific tool specification from the exposed tools cache
     *
     * @param chatId        the chat/tag identifier
     * @param specification the tool specification to remove
     */
    public void remove(String chatId, CamelToolSpecification specification) {
        Set<CamelToolSpecification> toolsForTag = tools.get(chatId);
        if (toolsForTag != null) {
            toolsForTag.remove(specification);
            if (toolsForTag.isEmpty()) {
                tools.remove(chatId);
            }
        }
    }

    /**
     * Removes a specific tool specification from the searchable tools cache
     *
     * @param chatId        the chat/tag identifier
     * @param specification the tool specification to remove
     */
    public void removeSearchable(String chatId, CamelToolSpecification specification) {
        Set<CamelToolSpecification> toolsForTag = searchableTools.get(chatId);
        if (toolsForTag != null) {
            toolsForTag.remove(specification);
            if (toolsForTag.isEmpty()) {
                searchableTools.remove(chatId);
            }
        }
    }

    public Map<String, Set<CamelToolSpecification>> getTools() {
        return tools;
    }

    public Map<String, Set<CamelToolSpecification>> getSearchableTools() {
        return searchableTools;
    }

    public boolean hasSearchableTools() {
        return !searchableTools.isEmpty();
    }
}

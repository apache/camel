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
package org.apache.camel.component.springai.tools.spec;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches Tools Specification and Consumer route reference by the chatId, so that different chats can have different
 * Tool implementation
 */
public final class CamelToolExecutorCache {

    private Map<String, Set<CamelToolSpecification>> tools;

    private CamelToolExecutorCache() {
        tools = new ConcurrentHashMap<>();
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

    public Map<String, Set<CamelToolSpecification>> getTools() {
        return tools;
    }
}

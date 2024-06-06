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
package org.apache.camel.component.langchain4j.chat.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches Tools Specification and Consumer route reference by the chatId, so that different chats can have different
 * Tool implementation
 */
public final class CamelToolExecutorCache {

    private static CamelToolExecutorCache INSTANCE;
    private Map<String, List<CamelToolSpecification>> tools;

    private CamelToolExecutorCache() {
        tools = new ConcurrentHashMap<>();
    }

    public synchronized static CamelToolExecutorCache getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CamelToolExecutorCache();
        }

        return INSTANCE;
    }

    public void put(String chatId, CamelToolSpecification specification) {
        if (tools.get(chatId) != null) {
            tools.get(chatId).add(specification);
        } else {
            List<CamelToolSpecification> camelToolSpecifications = new ArrayList<>();
            camelToolSpecifications.add(specification);
            tools.put(chatId, camelToolSpecifications);
        }
    }

    public Map<String, List<CamelToolSpecification>> getTools() {
        return tools;
    }

}

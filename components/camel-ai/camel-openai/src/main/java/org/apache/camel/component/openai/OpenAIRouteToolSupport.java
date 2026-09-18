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
package org.apache.camel.component.openai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolRegistry;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.util.ObjectHelper;

/**
 * Loads route-based tools from the shared {@link AiToolRegistry} for OpenAI agentic loops.
 */
final class OpenAIRouteToolSupport {

    private OpenAIRouteToolSupport() {
    }

    static Map<String, AiToolSpec> discoverRouteTools(CamelContext camelContext, String tags) {
        if (ObjectHelper.isEmpty(tags)) {
            return Map.of();
        }

        AiToolRegistry registry = AiToolRegistry.getOrCreate(camelContext);
        Map<String, AiToolSpec> toolsByName = new LinkedHashMap<>();
        for (String tag : AiToolParameterHelper.splitTags(tags)) {
            for (AiToolSpec spec : registry.getToolsByTag(tag.trim())) {
                toolsByName.putIfAbsent(spec.getName(), spec);
            }
        }
        return Map.copyOf(toolsByName);
    }

    static Set<String> returnDirectToolNames(Map<String, AiToolSpec> routeTools) {
        return routeTools.entrySet().stream()
                .filter(entry -> isReturnDirect(entry.getValue().getAnnotations()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    static boolean isReturnDirect(AiToolAnnotations annotations) {
        return annotations != null && annotations.isReturnDirect();
    }

    static List<ChatCompletionFunctionTool> toOpenAiTools(Map<String, AiToolSpec> routeTools) {
        return routeTools.values().stream()
                .map(AiToolSpecToOpenAI::convert)
                .toList();
    }
}

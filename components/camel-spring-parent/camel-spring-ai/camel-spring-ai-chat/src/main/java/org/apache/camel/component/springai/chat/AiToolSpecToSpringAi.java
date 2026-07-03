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
package org.apache.camel.component.springai.chat;

import java.util.Map;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.component.ai.tool.AiToolExecutor;
import org.apache.camel.component.ai.tool.AiToolResult;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * Converts a framework-agnostic {@link AiToolSpec} into a Spring AI {@link ToolCallback}. Delegates tool execution to
 * {@link AiToolExecutor} so that argument filtering, required-parameter validation, and route invocation are handled
 * uniformly across all AI framework adapters.
 */
final class AiToolSpecToSpringAi {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolSpecToSpringAi.class);

    private AiToolSpecToSpringAi() {
    }

    static ToolCallback toToolCallback(AiToolSpec spec) {
        Function<Map<String, Object>, String> function = args -> {
            Exchange toolExchange = spec.getConsumer().getEndpoint().createExchange();
            try {
                AiToolResult result = AiToolExecutor.execute(spec, args, toolExchange);
                if (result instanceof AiToolResult.Success success) {
                    return success.value();
                } else if (result instanceof AiToolResult.ArgumentError argErr) {
                    return "Tool execution failed: " + argErr.message();
                } else if (result instanceof AiToolResult.ExecutionError execErr) {
                    LOG.warn("Tool '{}' execution failed: {}", spec.getName(), execErr.message(), execErr.cause());
                    return "Tool execution failed";
                }
                return "Tool execution failed";
            } finally {
                spec.getConsumer().releaseExchange(toolExchange, false);
            }
        };

        FunctionToolCallback.Builder builder = FunctionToolCallback
                .builder(spec.getName(), function)
                .description(spec.getDescription())
                .inputType(Map.class);

        if (spec.getParametersJsonSchema() != null) {
            builder.inputSchema(spec.getParametersJsonSchema());
        }

        return builder.build();
    }
}

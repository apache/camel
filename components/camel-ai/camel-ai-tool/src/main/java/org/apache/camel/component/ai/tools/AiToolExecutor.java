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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Framework-agnostic executor for Camel route tools. Handles the common logic of resolving the route processor from the
 * tool's consumer, populating an {@link Exchange} with tool arguments, invoking the route, and returning the result.
 * <p>
 * AI framework adapters (LangChain4j, Spring AI, OpenAI) only need to parse their native argument format into a
 * {@code Map<String, Object>} and call this executor — they do not need to know how routes are resolved or invoked.
 * <p>
 * Returns an {@link AiToolResult} that classifies the outcome without deciding error handling policy. Framework
 * adapters inspect the result type and decide whether to return the error message as a string to the LLM, rethrow the
 * cause so framework-level error handlers fire, or sanitize the message before returning it.
 * <p>
 * This is an internal support class used by Camel AI framework adapters and is not intended for direct use by end
 * users.
 *
 * @since 4.22
 */
public final class AiToolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolExecutor.class);

    private AiToolExecutor() {
    }

    /**
     * Executes a Camel route tool by resolving the route processor from the spec's consumer, populating the exchange
     * with the provided arguments, and invoking the route.
     * <p>
     * Arguments are validated against the tool's declared parameters (undeclared arguments are warned about but not
     * rejected, required arguments are checked) and then wrapped in an {@link AiToolArguments} object set as an
     * exchange variable under {@link AiTool#TOOL_ARGUMENTS}. This avoids polluting the exchange header namespace and
     * eliminates any risk of colliding with internal Camel headers.
     * <p>
     * The calling adapter owns the exchange lifecycle: it must create the exchange before calling this method and
     * release it afterwards (via {@code consumer.releaseExchange()}) in a try-finally block.
     * <p>
     * All errors — validation failures and route execution errors — are caught and returned as typed
     * {@link AiToolResult} variants rather than propagated. Framework adapters inspect the result type and decide how
     * to handle errors (return to LLM, rethrow, sanitize).
     *
     * @param  spec      the tool specification containing the consumer and declared parameters
     * @param  arguments the tool arguments as a name-value map; each framework adapter is responsible for parsing its
     *                   native format (JSON string, Map, etc.) into this map before calling
     * @param  exchange  the Camel exchange to populate with arguments and execute
     * @return           an {@link AiToolResult} classifying the outcome; never null
     */
    public static AiToolResult execute(AiToolSpec spec, Map<String, Object> arguments, Exchange exchange) {
        String toolName = spec.getName();

        DefaultConsumer consumer = spec.getConsumer();
        if (consumer == null) {
            IllegalStateException cause = new IllegalStateException(
                    String.format("No consumer available for tool '%s'", toolName));
            return new AiToolResult.ExecutionError(cause.getMessage(), cause);
        }

        Processor routeProcessor = consumer.getProcessor();
        if (routeProcessor == null) {
            IllegalStateException cause = new IllegalStateException(
                    String.format("No route processor available for tool '%s'", toolName));
            return new AiToolResult.ExecutionError(cause.getMessage(), cause);
        }

        LOG.debug("Executing Camel route tool: '{}'", toolName);

        // Warn about undeclared arguments but do not reject them -- LLMs frequently
        // hallucinate extra parameters and rejecting them would break valid tool calls.
        if (arguments != null && !arguments.isEmpty() && !spec.getParameterDefs().isEmpty()) {
            Set<String> declaredParams = spec.getParameterDefs().keySet();

            for (String name : arguments.keySet()) {
                if (!declaredParams.contains(name)) {
                    LOG.warn("Undeclared tool argument '{}' for tool '{}' -- the LLM sent a parameter "
                             + "that is not declared in the tool specification; ignoring it",
                            name, toolName);
                }
            }
        }

        for (Map.Entry<String, AiToolParameterHelper.ParameterDef> entry : spec.getParameterDefs().entrySet()) {
            if (entry.getValue().isRequired()
                    && (arguments == null || !arguments.containsKey(entry.getKey()))) {
                LOG.warn("Missing required argument '{}' for tool '{}' -- the LLM did not send "
                         + "a parameter that is declared as required in the tool specification",
                        entry.getKey(), toolName);
                IllegalArgumentException cause = new IllegalArgumentException(
                        String.format("Missing required argument '%s' for tool '%s'", entry.getKey(), toolName));
                return new AiToolResult.ArgumentError(cause.getMessage(), cause);
            }
        }

        // Defensive copy so callers cannot mutate arguments during route execution
        Map<String, Object> argsCopy = arguments != null ? new HashMap<>(arguments) : Map.of();
        exchange.setVariable(AiTool.TOOL_ARGUMENTS, new AiToolArguments(toolName, argsCopy));

        // Execute the route
        try {
            routeProcessor.process(exchange);
        } catch (Exception e) {
            LOG.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
            return new AiToolResult.ExecutionError(
                    String.format("Error executing tool '%s': %s", toolName, e.getMessage()), e);
        }

        if (exchange.getException() != null) {
            Exception routeError = exchange.getException();
            LOG.error("Error executing tool '{}': {}", toolName, routeError.getMessage(), routeError);
            return new AiToolResult.ExecutionError(
                    String.format("Error executing tool '%s': %s", toolName, routeError.getMessage()), routeError);
        }

        String result = exchange.getMessage().getBody(String.class);
        LOG.debug("Tool '{}' execution completed successfully", toolName);
        return new AiToolResult.Success(result != null ? result : "No result");
    }
}

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelAuthorizationException;
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
     * Arguments are validated against the tool's declared parameters (undeclared arguments are filtered out with a
     * warning, required arguments are checked). Each argument is set as an individual exchange header so that route
     * expressions like {@code ${header.city}} and SQL bindings work naturally. Argument names that start with
     * {@code camel} or {@code org.apache.camel.} (case-insensitive) are rejected to prevent collision with internal
     * Camel headers (following the same pattern as the A2A component).
     * <p>
     * The calling adapter obtains the exchange from {@link #createToolExchange(Exchange)} and passes it in. That copy
     * is not a pooled consumer exchange, so the adapter does not release it afterwards (there is no
     * {@code releaseExchange()} to call).
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

        // Use the authorization-guarded processor when the consumer applied an AuthorizationPolicy, so the guard runs
        // before the route body; falls back to the plain route processor otherwise.
        Processor routeProcessor = consumer instanceof AiToolConsumer atc ? atc.getToolProcessor() : consumer.getProcessor();
        if (routeProcessor == null) {
            IllegalStateException cause = new IllegalStateException(
                    String.format("No route processor available for tool '%s'", toolName));
            return new AiToolResult.ExecutionError(cause.getMessage(), cause);
        }

        LOG.debug("Executing Camel route tool: '{}'", toolName);

        // Defensive copy so callers cannot mutate arguments during route execution
        Map<String, Object> argsCopy = arguments != null ? new HashMap<>(arguments) : new HashMap<>();

        // Filter out undeclared arguments -- LLMs frequently hallucinate extra parameters
        // and the generated schema advertises additionalProperties: false.
        // A tool that declares no parameters accepts none, so an empty declaration must filter
        // everything rather than let every argument through.
        if (!argsCopy.isEmpty()) {
            Set<String> declaredParams = spec.getDeclaredArgumentNames();

            argsCopy.keySet().removeIf(name -> {
                if (!declaredParams.contains(name)) {
                    LOG.warn("Undeclared tool argument '{}' for tool '{}' -- the LLM sent a parameter "
                             + "that is not declared in the tool specification; filtering it out",
                            name, toolName);
                    return true;
                }
                return false;
            });
        }

        for (String requiredName : spec.getRequiredArgumentNames()) {
            if (!argsCopy.containsKey(requiredName)) {
                LOG.warn("Missing required argument '{}' for tool '{}' -- the LLM did not send "
                         + "a parameter that is declared as required in the tool specification",
                        requiredName, toolName);
                IllegalArgumentException cause = new IllegalArgumentException(
                        String.format("Missing required argument '%s' for tool '%s'", requiredName, toolName));
                return new AiToolResult.ArgumentError(cause.getMessage(), cause);
            }
        }
        // Set each argument as an exchange header so route expressions (${header.city})
        // and SQL bindings work naturally. Filter out Camel-internal names to prevent
        // header-namespace injection (CVE-2025-27636 family).
        for (Map.Entry<String, Object> entry : argsCopy.entrySet()) {
            String name = entry.getKey();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("camel") || lower.startsWith("org.apache.camel.")) {
                LOG.warn("Rejecting tool argument '{}' for tool '{}' -- argument names starting with "
                         + "'Camel' or 'org.apache.camel.' are reserved for internal use",
                        name, toolName);
                continue;
            }
            exchange.getMessage().setHeader(name, entry.getValue());
        }

        // Execute the route
        try {
            routeProcessor.process(exchange);

            if (exchange.getException() != null) {
                Exception routeError = exchange.getException();
                AiToolResult denied = authorizationDenied(toolName, routeError);
                if (denied != null) {
                    return denied;
                }
                LOG.error("Error executing tool '{}': {}", toolName, routeError.getMessage(), routeError);
                return new AiToolResult.ExecutionError(
                        String.format("Error executing tool '%s': %s", toolName, routeError.getMessage()), routeError);
            }

            String result = exchange.getMessage().getBody(String.class);
            LOG.debug("Tool '{}' execution completed successfully", toolName);
            return buildSuccessResult(spec, exchange, result);
        } catch (Exception e) {
            AiToolResult denied = authorizationDenied(toolName, e);
            if (denied != null) {
                return denied;
            }
            LOG.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
            return new AiToolResult.ExecutionError(
                    String.format("Error executing tool '%s': %s", toolName, e.getMessage()), e);
        }
    }

    /**
     * Classifies an error from route execution as an authorization denial when a {@link CamelAuthorizationException} is
     * present in its cause chain (the route's {@link org.apache.camel.spi.AuthorizationPolicy} rejected the call).
     * Returns a caller-safe {@link AiToolResult.AuthorizationDenied} refusal that does not leak the policy's internal
     * message, or {@code null} when the error is not an authorization denial.
     */
    private static AiToolResult authorizationDenied(String toolName, Throwable error) {
        CamelAuthorizationException denial = findAuthorizationException(error);
        if (denial == null) {
            return null;
        }
        LOG.warn("Tool '{}' call denied by authorization policy: {}", toolName, denial.getMessage());
        return new AiToolResult.AuthorizationDenied(
                String.format("Access denied: not authorized to call tool '%s'", toolName), denial);
    }

    private static CamelAuthorizationException findAuthorizationException(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof CamelAuthorizationException cae) {
                return cae;
            }
        }
        return null;
    }

    private static AiToolResult buildSuccessResult(AiToolSpec spec, Exchange exchange, String stringBody) {
        String outputSchema = spec.getOutputJsonSchema();
        if (outputSchema == null || outputSchema.isBlank()) {
            return new AiToolResult.Success(stringBody != null ? stringBody : "No result");
        }

        Object body = exchange.getMessage().getBody();
        try {
            Object structured = AiToolParameterHelper.parseStructuredOutput(body);
            String text = AiToolParameterHelper.structuredContentToText(structured, body);
            return new AiToolResult.Success(text, structured);
        } catch (IllegalArgumentException e) {
            return new AiToolResult.ExecutionError(
                    String.format("Error executing tool '%s': %s", spec.getName(), e.getMessage()), e);
        }
    }

    /**
     * Builds the exchange used to invoke a route tool from the calling (agent) exchange. The caller's <em>context</em>
     * is carried over - exchange properties (most importantly the authenticated caller's identity, so a tool route can
     * be guarded on {@code exchangeProperty.subject} and the model cannot forge it) and variables - but the tool route
     * is given a <em>clean message</em>: it receives only its own tool arguments (set as headers by
     * {@link #execute(AiToolSpec, Map, Exchange)}), not the caller's body or inbound headers, and a tool that sets no
     * body returns {@code No result} rather than echoing the caller's body back to the model.
     * <p>
     * The tool exchange runs in its <em>own</em> unit of work and with its own exchange id - it does not share the
     * caller's. This keeps each tool call independent: the tool route's own {@code onCompletion}, error handler and
     * {@code useOriginalMessage()} apply to the tool call (not to the caller), parallel tool calls in one batch get
     * distinct ids, and an error handler cannot restore the caller's message into the result. Changes the tool makes
     * are isolated to this copy and do not leak back into the calling exchange. Every route-tool runtime
     * (langchain4j-agent, openai, spring-ai-chat) builds the tool exchange this way, so an authorization check on an
     * exchange property behaves identically across them (CAMEL-24832, CAMEL-23944).
     *
     * @param  callingExchange the exchange driving the agent
     * @return                 an isolated copy with its own unit of work and id, carrying the caller's properties and
     *                         variables but a clean message, to pass to {@link #execute(AiToolSpec, Map, Exchange)}
     */
    public static Exchange createToolExchange(Exchange callingExchange) {
        // copy() carries the caller's context (properties and variables). The tool route must then run in its OWN unit
        // of work and with its own exchange id, NOT the caller's: sharing the caller's UnitOfWork would stop the tool
        // route's onCompletion from firing, give every parallel tool call the caller's exchange id, and -- through an
        // error handler's useOriginalMessage() -- restore the caller's body and headers into the result, undoing the
        // clean message. So detach the unit of work, then wipe the message so the tool route starts from its own
        // arguments only.
        Exchange toolExchange = callingExchange.copy();
        toolExchange.getExchangeExtension().setUnitOfWork(null);
        toolExchange.getMessage().setBody(null);
        toolExchange.getMessage().getHeaders().clear();
        return toolExchange;
    }
}

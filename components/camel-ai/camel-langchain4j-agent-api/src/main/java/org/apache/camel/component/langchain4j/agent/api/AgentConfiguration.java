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

package org.apache.camel.component.langchain4j.agent.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for AI agents in the Apache Camel LangChain4j integration.
 *
 * <p>
 * This class encapsulates all the configuration parameters needed to create and configure AI agents, including chat
 * models, memory providers, RAG components, and security guardrails. It provides a fluent builder-style API for easy
 * configuration setup.
 * </p>
 *
 * <p>
 * Supported configuration options include:
 * </p>
 * <ul>
 * <li><strong>Chat Model:</strong> The underlying LLM for processing conversations</li>
 * <li><strong>Memory Provider:</strong> For maintaining conversation history in stateful agents</li>
 * <li><strong>Retrieval Augmentor:</strong> For RAG (Retrieval-Augmented Generation) capabilities</li>
 * <li><strong>Input Guardrails:</strong> Security filters applied to incoming messages (class-based or
 * instance-based)</li>
 * <li><strong>Output Guardrails:</strong> Security filters applied to agent responses (class-based or
 * instance-based)</li>
 * <li><strong>Guardrail Configuration:</strong> Retry policies and other guardrail behavior settings</li>
 * <li><strong>Custom Tools:</strong> Custom LangChain4j tools with @Tool annotations</li>
 * <li><strong>MCP Clients:</strong> Model Context Protocol clients for external tool integration</li>
 * <li><strong>MCP Tool Filters:</strong> Filters for controlling which MCP tools are available</li>
 * <li><strong>Tool Execution Hooks:</strong> Callbacks invoked before and after each tool execution</li>
 * </ul>
 *
 * @since 4.9.0
 */
public class AgentConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(AgentConfiguration.class);

    private ChatModel chatModel;
    private ChatMemoryProvider chatMemoryProvider;
    private RetrievalAugmentor retrievalAugmentor;
    private List<Class<?>> inputGuardrailClasses;
    private List<Class<?>> outputGuardrailClasses;
    private List<Object> customTools; // Custom LangChain4j tools
    private List<McpClient> mcpClients; // MCP clients for external tool integration
    private BiPredicate<McpClient, ToolSpecification> mcpToolProviderFilter; // Filter for MCP tools
    private int maxToolCallingRoundTrips; // Max number of tool-calling round trips (0 = not set)
    private Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy;
    private ToolExecutionErrorHandler toolExecutionErrorHandler;
    private ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private Boolean compensateOnToolErrors;
    private List<InputGuardrail> inputGuardrails;
    private List<OutputGuardrail> outputGuardrails;
    private InputGuardrailsConfig inputGuardrailsConfig;
    private OutputGuardrailsConfig outputGuardrailsConfig;
    private Consumer<BeforeToolExecution> beforeToolExecution;
    private Consumer<ToolExecution> afterToolExecution;
    private Consumer<AiServices<?>> aiServicesCustomizer;

    /**
     * Gets the configured chat model.
     *
     * @return the chat model instance, or {@code null} if not configured
     */
    public ChatModel getChatModel() {
        return chatModel;
    }

    /**
     * Sets the chat model for this agent configuration.
     *
     * @param  chatModel the LangChain4j chat model to use for AI interactions
     * @return           this configuration instance for method chaining
     */
    public AgentConfiguration withChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
        return this;
    }

    /**
     * Gets the configured chat memory provider.
     *
     * @return the chat memory provider instance, or {@code null} if not configured
     */
    public ChatMemoryProvider getChatMemoryProvider() {
        return chatMemoryProvider;
    }

    /**
     * Sets the chat memory provider for stateful agent conversations.
     *
     * @param  chatMemoryProvider the memory provider for maintaining conversation history
     * @return                    this configuration instance for method chaining
     */
    public AgentConfiguration withChatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
        return this;
    }

    /**
     * Gets the configured retrieval augmentor for RAG (Retrieval-Augmented Generation) support.
     *
     * @return the retrieval augmentor instance, or {@code null} if not configured
     */
    public RetrievalAugmentor getRetrievalAugmentor() {
        return retrievalAugmentor;
    }

    /**
     * Sets the retrieval augmentor for enabling RAG (Retrieval-Augmented Generation) capabilities.
     *
     * @param  retrievalAugmentor the retrieval augmentor to enhance responses with external knowledge
     * @return                    this configuration instance for method chaining
     */
    public AgentConfiguration withRetrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
        this.retrievalAugmentor = retrievalAugmentor;
        return this;
    }

    /**
     * Gets the configured input guardrail classes for security filtering.
     *
     * @return the list of input guardrail classes, or {@code null} if not configured
     */
    public List<Class<?>> getInputGuardrailClasses() {
        return inputGuardrailClasses;
    }

    /**
     * Sets input guardrail classes from a comma-separated string of class names.
     *
     * @param  inputGuardrailClasses comma-separated list of fully qualified class names
     * @return                       this configuration instance for method chaining
     * @see                          #parseGuardrailClasses(String)
     */
    public AgentConfiguration withInputGuardrailClassesList(String inputGuardrailClasses) {
        return withInputGuardrailClasses(parseGuardrailClasses(inputGuardrailClasses));
    }

    /**
     * Sets input guardrail classes from an array of class names.
     *
     * @param  inputGuardrailClasses array of fully qualified class names
     * @return                       this configuration instance for method chaining
     * @see                          #parseGuardrailClasses(String[])
     */
    public AgentConfiguration withInputGuardrailClassesArray(String[] inputGuardrailClasses) {
        return withInputGuardrailClasses(parseGuardrailClasses(inputGuardrailClasses));
    }

    /**
     * Sets input guardrail classes for security filtering of incoming messages.
     *
     * @param  inputGuardrailClasses list of guardrail classes to apply to user inputs
     * @return                       this configuration instance for method chaining
     */
    public AgentConfiguration withInputGuardrailClasses(List<Class<?>> inputGuardrailClasses) {
        this.inputGuardrailClasses = inputGuardrailClasses;
        return this;
    }

    /**
     * Gets the configured output guardrail classes for security filtering.
     *
     * @return the list of output guardrail classes, or {@code null} if not configured
     */
    public List<Class<?>> getOutputGuardrailClasses() {
        return outputGuardrailClasses;
    }

    /**
     * Sets output guardrail classes from a comma-separated string of class names.
     *
     * @param  outputGuardrailClasses comma-separated list of fully qualified class names
     * @return                        this configuration instance for method chaining
     * @see                           #parseGuardrailClasses(String)
     */
    public AgentConfiguration withOutputGuardrailClassesList(String outputGuardrailClasses) {
        return withOutputGuardrailClasses(parseGuardrailClasses(outputGuardrailClasses));
    }

    /**
     * Sets output guardrail classes from an array of class names.
     *
     * @param  outputGuardrailClasses array of fully qualified class names
     * @return                        this configuration instance for method chaining
     * @see                           #parseGuardrailClasses(String[])
     */
    public AgentConfiguration withOutputGuardrailClassesArray(String[] outputGuardrailClasses) {
        return withOutputGuardrailClasses(parseGuardrailClasses(outputGuardrailClasses));
    }

    /**
     * Sets output guardrail classes for security filtering of agent responses.
     *
     * @param  outputGuardrailClasses list of guardrail classes to apply to agent outputs
     * @return                        this configuration instance for method chaining
     */
    public AgentConfiguration withOutputGuardrailClasses(List<Class<?>> outputGuardrailClasses) {
        this.outputGuardrailClasses = outputGuardrailClasses;
        return this;
    }

    /**
     * Parses comma-separated guardrail class names into a list of loaded classes.
     *
     * <p>
     * This utility method takes a string containing comma-separated fully qualified class names and attempts to load
     * each class using reflection. Classes that cannot be loaded are logged as warnings and excluded from the result.
     * </p>
     *
     * <p>
     * Example usage:
     * </p>
     *
     * <pre>{@code
     * List<Class<?>> classes = AgentConfiguration.parseGuardrailClasses(
     *         "com.example.InputFilter,com.example.OutputValidator");
     * }</pre>
     *
     * @param  guardrailClassNames comma-separated class names, may be {@code null} or empty
     * @return                     a list of successfully loaded classes; empty list if input is {@code null}, empty, or
     *                             if no classes could be loaded
     */
    public static List<Class<?>> parseGuardrailClasses(String guardrailClassNames) {
        if (guardrailClassNames == null || guardrailClassNames.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return parseGuardrailClasses(guardrailClassNames.split(","));
    }

    /**
     * Parses an array of guardrail class names into a list of loaded classes.
     *
     * <p>
     * This utility method takes an array of fully qualified class names and attempts to load each class using
     * reflection. Classes that cannot be loaded are logged as warnings and excluded from the result.
     * </p>
     *
     * <p>
     * Example usage:
     * </p>
     *
     * <pre>{@code
     * String[] classNames = { "java.lang.String", "java.util.List" };
     * List<Class<?>> classes = AgentConfiguration.parseGuardrailClasses(classNames);
     * }</pre>
     *
     * @param  guardrailClassNames array of fully qualified class names, may be {@code null}
     * @return                     a list of successfully loaded classes; empty list if input is {@code null} or if no
     *                             classes could be loaded
     */
    public static List<Class<?>> parseGuardrailClasses(String[] guardrailClassNames) {
        if (guardrailClassNames == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(guardrailClassNames)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(AgentConfiguration::loadGuardrailClass)
                .filter(clazz -> clazz != null)
                .collect(Collectors.toList());
    }

    /**
     * Gets the configured custom tools.
     *
     * @return the custom tools list, or {@code null} if not configured
     */
    public List<Object> getCustomTools() {
        return customTools;
    }

    /**
     * Sets the custom tools for this agent configuration.
     *
     * @param  customTools the list of tool instances with @Tool methods
     * @return             this configuration instance for method chaining
     */
    public AgentConfiguration withCustomTools(List<Object> customTools) {
        this.customTools = customTools;
        return this;
    }

    /**
     * Gets the configured MCP clients for external tool integration.
     *
     * @return the list of MCP clients, or {@code null} if not configured
     */
    public List<McpClient> getMcpClients() {
        return mcpClients;
    }

    /**
     * Sets the MCP clients for external tool integration.
     *
     * @param  mcpClients the list of MCP clients to connect to external tools
     * @return            this configuration instance for method chaining
     */
    public AgentConfiguration withMcpClients(List<McpClient> mcpClients) {
        this.mcpClients = mcpClients;
        return this;
    }

    /**
     * Sets a single MCP client for external tool integration.
     *
     * @param  mcpClient the MCP client to connect to external tools
     * @return           this configuration instance for method chaining
     */
    public AgentConfiguration withMcpClient(McpClient mcpClient) {
        this.mcpClients = Arrays.asList(mcpClient);
        return this;
    }

    /**
     * Gets the configured MCP tool provider filter.
     *
     * @return the MCP tool provider filter, or {@code null} if not configured
     */
    public BiPredicate<McpClient, ToolSpecification> getMcpToolProviderFilter() {
        return mcpToolProviderFilter;
    }

    /**
     * Sets the MCP tool provider filter for controlling which MCP tools are available.
     *
     * @param  mcpToolProviderFilter the filter predicate that determines which MCP tools to include
     * @return                       this configuration instance for method chaining
     */
    public AgentConfiguration withMcpToolProviderFilter(BiPredicate<McpClient, ToolSpecification> mcpToolProviderFilter) {
        this.mcpToolProviderFilter = mcpToolProviderFilter;
        return this;
    }

    /**
     * Gets the maximum number of tool-calling round trips allowed.
     *
     * @return the maximum round trips, or 0 if not configured
     */
    public int getMaxToolCallingRoundTrips() {
        return maxToolCallingRoundTrips;
    }

    /**
     * Sets the maximum number of tool-calling round trips the LLM can make per request. This limits how many times the
     * model can invoke tools before a final response must be produced.
     *
     * @param  maxToolCallingRoundTrips the maximum number of round trips (must be positive)
     * @return                          this configuration instance for method chaining
     */
    public AgentConfiguration withMaxToolCallingRoundTrips(int maxToolCallingRoundTrips) {
        this.maxToolCallingRoundTrips = maxToolCallingRoundTrips;
        return this;
    }

    /**
     * Gets the strategy for handling hallucinated (non-existent) tool names.
     *
     * @return the hallucinated tool name strategy, or {@code null} if not configured
     */
    public Function<ToolExecutionRequest, ToolExecutionResultMessage> getHallucinatedToolNameStrategy() {
        return hallucinatedToolNameStrategy;
    }

    /**
     * Sets the strategy for handling cases where the LLM hallucinates a tool name that does not exist. The function
     * receives the invalid tool execution request and returns a result message to send back to the LLM.
     *
     * @param  hallucinatedToolNameStrategy the strategy function for handling hallucinated tool names
     * @return                              this configuration instance for method chaining
     */
    public AgentConfiguration withHallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy) {
        this.hallucinatedToolNameStrategy = hallucinatedToolNameStrategy;
        return this;
    }

    /**
     * Gets the error handler for tool execution failures.
     *
     * @return the tool execution error handler, or {@code null} if not configured
     */
    public ToolExecutionErrorHandler getToolExecutionErrorHandler() {
        return toolExecutionErrorHandler;
    }

    /**
     * Sets the error handler invoked when a tool execution throws an exception.
     *
     * @param  toolExecutionErrorHandler the handler for tool execution errors
     * @return                           this configuration instance for method chaining
     */
    public AgentConfiguration withToolExecutionErrorHandler(ToolExecutionErrorHandler toolExecutionErrorHandler) {
        this.toolExecutionErrorHandler = toolExecutionErrorHandler;
        return this;
    }

    /**
     * Gets the error handler for tool argument parsing failures.
     *
     * @return the tool arguments error handler, or {@code null} if not configured
     */
    public ToolArgumentsErrorHandler getToolArgumentsErrorHandler() {
        return toolArgumentsErrorHandler;
    }

    /**
     * Sets the error handler invoked when tool arguments cannot be parsed or validated.
     *
     * @param  toolArgumentsErrorHandler the handler for tool argument errors
     * @return                           this configuration instance for method chaining
     */
    public AgentConfiguration withToolArgumentsErrorHandler(ToolArgumentsErrorHandler toolArgumentsErrorHandler) {
        this.toolArgumentsErrorHandler = toolArgumentsErrorHandler;
        return this;
    }

    /**
     * Gets whether tool error compensation is enabled.
     *
     * @return {@code true} if enabled, {@code false} if disabled, or {@code null} if not configured
     */
    public Boolean getCompensateOnToolErrors() {
        return compensateOnToolErrors;
    }

    /**
     * Sets whether the agent should attempt to compensate when tool execution errors occur by sending the error back to
     * the LLM for recovery.
     *
     * @param  compensateOnToolErrors {@code true} to enable compensation, {@code false} to disable
     * @return                        this configuration instance for method chaining
     */
    public AgentConfiguration withCompensateOnToolErrors(Boolean compensateOnToolErrors) {
        this.compensateOnToolErrors = compensateOnToolErrors;
        return this;
    }

    /**
     * Gets the configured input guardrail instances for security filtering.
     *
     * <p>
     * Unlike {@link #getInputGuardrailClasses()}, which returns classes to be instantiated by LangChain4j via no-arg
     * constructor, this returns pre-instantiated guardrail objects that can carry configuration, injected
     * collaborators, or a {@code CamelContext} reference.
     * </p>
     *
     * @return the list of input guardrail instances, or {@code null} if not configured
     */
    public List<InputGuardrail> getInputGuardrails() {
        return inputGuardrails;
    }

    /**
     * Sets pre-instantiated input guardrail instances for security filtering of incoming messages.
     *
     * <p>
     * Use this method when guardrails need configuration (thresholds, wordlists, vault-sourced patterns) or injected
     * collaborators from a DI container (Spring, Quarkus CDI). Both guardrail classes and instances can be set; they
     * are additive on the AiServices builder.
     * </p>
     *
     * @param  inputGuardrails list of guardrail instances to apply to user inputs
     * @return                 this configuration instance for method chaining
     * @see                    #withInputGuardrailClasses(List)
     */
    public AgentConfiguration withInputGuardrails(List<InputGuardrail> inputGuardrails) {
        this.inputGuardrails = inputGuardrails;
        return this;
    }

    /**
     * Gets the configured output guardrail instances for security filtering.
     *
     * <p>
     * Unlike {@link #getOutputGuardrailClasses()}, which returns classes to be instantiated by LangChain4j via no-arg
     * constructor, this returns pre-instantiated guardrail objects that can carry configuration, injected
     * collaborators, or a {@code CamelContext} reference.
     * </p>
     *
     * @return the list of output guardrail instances, or {@code null} if not configured
     */
    public List<OutputGuardrail> getOutputGuardrails() {
        return outputGuardrails;
    }

    /**
     * Sets pre-instantiated output guardrail instances for security filtering of agent responses.
     *
     * <p>
     * Use this method when guardrails need configuration or injected collaborators from a DI container. Both guardrail
     * classes and instances can be set; they are additive on the AiServices builder.
     * </p>
     *
     * @param  outputGuardrails list of guardrail instances to apply to agent outputs
     * @return                  this configuration instance for method chaining
     * @see                     #withOutputGuardrailClasses(List)
     */
    public AgentConfiguration withOutputGuardrails(List<OutputGuardrail> outputGuardrails) {
        this.outputGuardrails = outputGuardrails;
        return this;
    }

    /**
     * Gets the configuration for input guardrails (e.g., max retries).
     *
     * @return the input guardrails configuration, or {@code null} if not configured
     */
    public InputGuardrailsConfig getInputGuardrailsConfig() {
        return inputGuardrailsConfig;
    }

    /**
     * Sets the configuration for input guardrails. This controls behavior such as retry policies when an input
     * guardrail rejects a message.
     *
     * @param  inputGuardrailsConfig the configuration for input guardrails
     * @return                       this configuration instance for method chaining
     */
    public AgentConfiguration withInputGuardrailsConfig(InputGuardrailsConfig inputGuardrailsConfig) {
        this.inputGuardrailsConfig = inputGuardrailsConfig;
        return this;
    }

    /**
     * Gets the configuration for output guardrails (e.g., max retries).
     *
     * @return the output guardrails configuration, or {@code null} if not configured
     */
    public OutputGuardrailsConfig getOutputGuardrailsConfig() {
        return outputGuardrailsConfig;
    }

    /**
     * Sets the configuration for output guardrails. This controls behavior such as retry policies when an output
     * guardrail rejects a response.
     *
     * @param  outputGuardrailsConfig the configuration for output guardrails
     * @return                        this configuration instance for method chaining
     */
    public AgentConfiguration withOutputGuardrailsConfig(OutputGuardrailsConfig outputGuardrailsConfig) {
        this.outputGuardrailsConfig = outputGuardrailsConfig;
        return this;
    }

    /**
     * Gets the callback invoked before each tool execution.
     *
     * @return the before-tool-execution consumer, or {@code null} if not configured
     */
    public Consumer<BeforeToolExecution> getBeforeToolExecution() {
        return beforeToolExecution;
    }

    /**
     * Sets a callback that is invoked before each tool execution. This is the natural hook for per-tool-call logging,
     * Camel events, Micrometer metrics and OpenTelemetry spans.
     *
     * <p>
     * <strong>Note:</strong> The {@link BeforeToolExecution} type is marked {@code @Experimental} in LangChain4j — its
     * API may change in future versions.
     * </p>
     *
     * @param  beforeToolExecution the consumer to invoke before each tool execution
     * @return                     this configuration instance for method chaining
     */
    public AgentConfiguration withBeforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecution) {
        this.beforeToolExecution = beforeToolExecution;
        return this;
    }

    /**
     * Gets the callback invoked after each tool execution.
     *
     * @return the after-tool-execution consumer, or {@code null} if not configured
     */
    public Consumer<ToolExecution> getAfterToolExecution() {
        return afterToolExecution;
    }

    /**
     * Sets a callback that is invoked after each tool execution. This is the natural hook for per-tool-call logging,
     * Camel events, Micrometer metrics and OpenTelemetry spans.
     *
     * <p>
     * <strong>Note:</strong> The {@link ToolExecution} type is marked {@code @Experimental} in LangChain4j — its API
     * may change in future versions.
     * </p>
     *
     * @param  afterToolExecution the consumer to invoke after each tool execution
     * @return                    this configuration instance for method chaining
     */
    public AgentConfiguration withAfterToolExecution(Consumer<ToolExecution> afterToolExecution) {
        this.afterToolExecution = afterToolExecution;
        return this;
    }

    /**
     * Gets the custom AiServices builder customizer.
     *
     * @return the customizer, or {@code null} if not configured
     */
    public Consumer<AiServices<?>> getAiServicesCustomizer() {
        return aiServicesCustomizer;
    }

    /**
     * Sets a customizer callback that is invoked on the LangChain4j {@link AiServices} builder after all standard
     * configuration has been applied but before {@code build()} is called. This provides an escape hatch for
     * configuring any AiServices builder option that is not directly exposed on this configuration class.
     *
     * <p>
     * <strong>Warning:</strong> A customizer that calls {@code chatRequestTransformer(...)} will silently replace the
     * transformer installed by {@code configureBuilder()} for {@code responseFormat}, breaking
     * {@code jsonSchema}/{@code outputClass} structured output. If you need to set both a custom transformer and
     * structured output, compose both transformers into a single {@code UnaryOperator<ChatRequest>} and set it via the
     * customizer.
     * </p>
     *
     * <p>
     * The following AiServices builder options are deliberately not exposed as first-class fields and should be
     * configured via this escape hatch when needed:
     * </p>
     * <ul>
     * <li>{@code toolSearchStrategy} — overlaps with the component's own tool-search mechanism</li>
     * <li>{@code storeRetrievedContentInChatMemory} — niche RAG-memory flag</li>
     * <li>{@code AiServiceListener} — users can attach {@code ChatModelListener}s to the ChatModel bean directly</li>
     * </ul>
     *
     * @param  aiServicesCustomizer the customizer to apply to the AiServices builder
     * @return                      this configuration instance for method chaining
     */
    public AgentConfiguration withAiServicesCustomizer(Consumer<AiServices<?>> aiServicesCustomizer) {
        this.aiServicesCustomizer = aiServicesCustomizer;
        return this;
    }

    /**
     * Loads a guardrail class by its fully qualified name using reflection.
     *
     * <p>
     * This method attempts to load the specified class using {@link Class#forName(String)}. If the class cannot be
     * found, a warning is logged and {@code null} is returned.
     * </p>
     *
     * @param  className the fully qualified class name to load
     * @return           the loaded {@link Class} object, or {@code null} if the class could not be found
     */
    protected static Class<?> loadGuardrailClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOG.warn("Failed to load guardrail class: {}", className, e);
            return null;
        }
    }
}

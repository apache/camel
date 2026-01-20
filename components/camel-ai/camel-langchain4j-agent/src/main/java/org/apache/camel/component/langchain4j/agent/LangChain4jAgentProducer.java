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
package org.apache.camel.component.langchain4j.agent;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentFactory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LangChain4jAgentProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jAgentProducer.class);

    private final LangChain4jAgentEndpoint endpoint;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentFactory agentFactory;
    private Agent agent;

    public LangChain4jAgentProducer(LangChain4jAgentEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (endpoint.getConfiguration().getAgent() != null) {
            agent = endpoint.getConfiguration().getAgent();
        } else {
            agent = endpoint.getCamelContext().getRegistry().lookupByNameAndType(endpoint.getAgentId(), Agent.class);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object messagePayload = exchange.getIn().getBody();
        ObjectHelper.notNull(messagePayload, "body");

        // tags for Camel Routes as Tools
        String tags = endpoint.getConfiguration().getTags();

        if (agentFactory != null) {
            agent = agentFactory.createAgent(exchange);
        }

        AiAgentBody<?> aiAgentBody = agent.processBody(messagePayload, exchange);

        ToolProvider toolProvider = createCamelToolProvider(tags, exchange);
        String response = agent.chat(aiAgentBody, toolProvider);
        exchange.getMessage().setBody(response);
    }

    /**
     * We create our own Tool Provider
     *
     * @param tags
     * @param exchange
     */
    private ToolProvider createCamelToolProvider(String tags, Exchange exchange) {
        ToolProvider toolProvider = null;
        if (tags != null && !tags.trim().isEmpty()) {
            // Discover tools from Camel LangChain4j Tools routes
            Map<String, CamelToolSpecification> availableTools = discoverToolsByName(tags);

            if (!availableTools.isEmpty()) {
                LOG.debug("Creating AI Service with {} tools for tags: {}", availableTools.size(), tags);

                // Create dynamic tool provider that returns Camel routes as tools
                toolProvider = createCamelToolProvider(availableTools, exchange);

            } else {
                LOG.debug("No tools found for tags: {}, using simple AI Service", tags);
            }
        }
        return toolProvider;
    }

    /**
     * Create a dynamic tool provider that returns all Camel route as LangChain4j tools. This uses LangChain4j's
     * ToolProvider API for dynamic tool registration.
     */
    private ToolProvider createCamelToolProvider(Map<String, CamelToolSpecification> availableTools, Exchange exchange) {
        return (ToolProviderRequest toolProviderRequest) -> {
            // Build the tool provider result with all available Camel tools
            ToolProviderResult.Builder resultBuilder = ToolProviderResult.builder();

            for (Map.Entry<String, CamelToolSpecification> entry : availableTools.entrySet()) {
                String toolName = entry.getKey();
                CamelToolSpecification camelToolSpec = entry.getValue();

                // Get the existing ToolSpecification from CamelToolSpecification
                ToolSpecification toolSpecification = camelToolSpec.getToolSpecification();

                // Create a functional tool executor for this specific Camel route
                ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
                    LOG.info("Executing Camel route tool: '{}' with arguments: {}", toolName, toolExecutionRequest.arguments());

                    try {
                        // Parse JSON arguments if provided
                        String arguments = toolExecutionRequest.arguments();
                        if (arguments != null && !arguments.trim().isEmpty()) {
                            JsonNode jsonNode = objectMapper.readValue(arguments, JsonNode.class);
                            jsonNode.fieldNames()
                                    .forEachRemaining(name -> exchange.getMessage().setHeader(name, jsonNode.get(name)));
                        }

                        // Set the tool name as a header for route identification
                        exchange.getMessage().setHeader("CamelToolName", toolName);

                        // Execute the consumer route
                        camelToolSpec.getConsumer().getProcessor().process(exchange);

                        // Return the result
                        String result = exchange.getIn().getBody(String.class);
                        LOG.info("Tool '{}' execution completed successfully", toolName);
                        return result != null ? result : "No result";

                    } catch (Exception e) {
                        LOG.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
                        return String.format("Error executing tool '%s': %s", toolName, e.getMessage());
                    }
                };

                // Add this tool to the result
                resultBuilder.add(toolSpecification, toolExecutor);

                LOG.info("Added dynamic tool: '{}' - {}", toolSpecification.name(), toolSpecification.description());
            }

            return resultBuilder.build();
        };
    }

    /**
     * Discover Camel routes by tags and create a map of tool specifications by name.
     */
    private Map<String, CamelToolSpecification> discoverToolsByName(String tags) {
        final CamelToolExecutorCache toolCache = CamelToolExecutorCache.getInstance();
        final Map<String, Set<CamelToolSpecification>> tools = toolCache.getTools();
        final String[] tagArray = ToolsTagsHelper.splitTags(tags);

        final Map<String, CamelToolSpecification> toolsByName = Arrays.stream(tagArray)
                .flatMap(tag -> tools.entrySet().stream()
                        .filter(entry -> entry.getKey().equals(tag))
                        .flatMap(entry -> entry.getValue().stream()))
                .collect(Collectors.toMap(
                        camelToolSpec -> camelToolSpec.getToolSpecification().name(),
                        camelToolSpec -> camelToolSpec,
                        (existing, replacement) -> existing // Keep first if duplicate names
                ));

        LOG.info("Discovered {} unique tools for tags: {}", toolsByName.size(), tags);
        return toolsByName;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        agentFactory = endpoint.getConfiguration().getAgentFactory();
        if (agentFactory != null) {
            agentFactory.setCamelContext(this.endpoint.getCamelContext());
        }
    }
}

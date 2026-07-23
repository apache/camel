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
package org.apache.camel.component.langchain4j.agent.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * Minimal in-memory {@link McpClient} for unit tests.
 */
public final class StubMcpClient implements McpClient {

    private final String key;
    private final List<ToolSpecification> tools;

    public StubMcpClient(String key, List<ToolSpecification> tools) {
        this.key = key;
        this.tools = List.copyOf(tools);
    }

    public StubMcpClient(String key, ToolSpecification... tools) {
        this(key, List.of(tools));
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public List<ToolSpecification> listTools() {
        return tools;
    }

    @Override
    public List<ToolSpecification> listTools(InvocationContext invocationContext) {
        return listTools();
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest) {
        throw new UnsupportedOperationException("Not needed for filter tests");
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest, InvocationContext invocationContext) {
        throw new UnsupportedOperationException("Not needed for filter tests");
    }

    @Override
    public List<McpResource> listResources() {
        return Collections.emptyList();
    }

    @Override
    public List<McpResource> listResources(InvocationContext invocationContext) {
        return Collections.emptyList();
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates() {
        return Collections.emptyList();
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates(InvocationContext invocationContext) {
        return Collections.emptyList();
    }

    @Override
    public McpReadResourceResult readResource(String uri) {
        throw new UnsupportedOperationException("Not needed for filter tests");
    }

    @Override
    public McpReadResourceResult readResource(String uri, InvocationContext invocationContext) {
        throw new UnsupportedOperationException("Not needed for filter tests");
    }

    @Override
    public void subscribeToResource(String uri) {
        throw new UnsupportedOperationException("Not needed for filter tests");
    }

    @Override
    public void unsubscribeFromResource(String uri) {
        throw new UnsupportedOperationException("Not needed for filter tests");
    }

    @Override
    public List<McpPrompt> listPrompts() {
        return Collections.emptyList();
    }

    @Override
    public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        throw new UnsupportedOperationException("Not needed for filter tests");
    }

    @Override
    public void checkHealth() {
        // no-op
    }

    @Override
    public void setRoots(List<McpRoot> roots) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}

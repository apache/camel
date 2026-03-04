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
package org.apache.camel.component.langchain4j.agent.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BiPredicate;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for LangChain4j Agent component with MCP (Model Context Protocol) tools.
 *
 * This test uses a simple MCP server (filesystem) to demonstrate MCP tool integration. The filesystem MCP server
 * provides basic file operations like read_file, write_file, etc.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentMcpToolsIT extends CamelTestSupport {

    private static final String TEST_FILE_CONTENT = "Hello from MCP filesystem tool!";

    @TempDir
    Path tempDir;

    private String testFilePath;
    private String tempDirPath;

    protected ChatModel chatModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = ModelHelper.loadChatModel(OLLAMA);

        // Initialize tempDirPath - use toRealPath() to resolve symlinks (e.g., /var -> /private/var on macOS)
        // This is needed because the MCP filesystem server resolves symlinks internally
        tempDirPath = tempDir.toRealPath().toString();

        // Create test file directly
        try {
            Path testFile = tempDir.resolve("camel-mcp-test.txt");
            Files.write(testFile, TEST_FILE_CONTENT.getBytes());
            testFilePath = testFile.toRealPath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test file", e);
        }
    }

    @Test
    void testAgentAvailableMcpTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "What tools do you have available? List them.", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.toLowerCase().contains("read"),
                "Response should indicate the possibility of reading a file but was: " + response);
        assertFalse(response.toLowerCase().contains("edit"),
                "Response should indicate the possibility of editing a file because edit_file is not part of the filtered MCP tools but was: "
                                                             + response);
    }

    @Test
    void testListDirectoryMcpTool() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "Use your available tools to tell me What are the files and directories in " + tempDirPath + " directory ?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.toLowerCase().contains("camel-mcp-test.txt"),
                "Response should contain our file but was: " + response);
    }

    @Test
    void testReadFileMcpTool() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "Use your available tools to tell me what is the content of the file at " + testFilePath + " ?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.toLowerCase().contains(TEST_FILE_CONTENT.toLowerCase()),
                "Response should contain the content of our file but was: " + response);
    }

    private McpClient createMcpClient() {
        // Create MCP transport for filesystem server
        McpTransport filesystemTransport = new StdioMcpTransport.Builder()
                .command(Arrays.asList("npx", "-y", "@modelcontextprotocol/server-filesystem", tempDirPath))
                .logEvents(true)
                .build();

        // Create MCP client for filesystem
        return new DefaultMcpClient.Builder()
                .transport(filesystemTransport)
                .build();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        // Create MCP client
        McpClient filesystemClient = createMcpClient();

        // Create security filter to only allow read operations
        BiPredicate<McpClient, ToolSpecification> securityFilter = (client, toolSpec) -> {
            String toolName = toolSpec.name().toLowerCase();
            // Only allow read operations for safety
            return toolName.contains("search") || toolName.contains("read") || toolName.contains("list")
                    || toolName.contains("get");
        };

        // Create agent configuration with MCP clients and filter
        AgentConfiguration config = new AgentConfiguration()
                .withChatModel(chatModel)
                .withMcpClients(Arrays.asList(filesystemClient))
                .withMcpToolProviderFilter(securityFilter);

        // Create agent
        Agent agent = new AgentWithoutMemory(config);

        // Register agent in Camel context
        this.context.getRegistry().bind("mcpToolsAgent", agent);
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route with MCP tools
                from("direct:chat")
                        .to("langchain4j-agent:assistant?agent=#mcpToolsAgent")
                        .to("mock:agent-response");
            }
        };

    }
}

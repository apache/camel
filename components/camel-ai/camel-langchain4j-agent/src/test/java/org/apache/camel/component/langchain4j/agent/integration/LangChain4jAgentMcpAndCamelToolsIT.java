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

import java.util.Arrays;
import java.util.Map;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingService;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingServiceFactory;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the LangChain4j Agent component verifying that internal Camel route tools (via
 * camel-langchain4j-tools with tags) and external MCP tools can coexist together in a single agent.
 *
 * <p>
 * This test demonstrates two MCP configuration approaches working simultaneously:
 * <ul>
 * <li><strong>Endpoint URI configuration</strong>: the mcp/everything server (Streamable HTTP) is configured
 * declaratively via {@code mcpServer.everything.transportType=http&mcpServer.everything.url=...}</li>
 * <li><strong>Bean reference configuration</strong>: the mcp/time server (Docker stdio) is configured as a pre-built
 * {@link McpClient} bean and referenced via {@code mcpClients=#timeMcpClient}</li>
 * </ul>
 *
 * <p>
 * Additionally, Camel route tools (user database and weather service) are configured via the {@code tags} parameter.
 * All tool sources are composed via {@code CompositeToolProvider}.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentMcpAndCamelToolsIT extends CamelTestSupport {

    private static final String USER_DATABASE = """
            {"id": "42", "name": "Alice Johnson", "role": "Engineer"}
            """;

    private static final String WEATHER_INFO = "cloudy, 18C";

    protected ChatModel chatModel;
    private McpClient timeClient;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @RegisterExtension
    static McpEverythingService MCP_EVERYTHING = McpEverythingServiceFactory.createService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = ModelHelper.loadChatModel(OLLAMA);

        // MCP Time Server via Docker stdio transport - configured as pre-built McpClient bean
        timeClient = new DefaultMcpClient.Builder()
                .key("time")
                .transport(new StdioMcpTransport.Builder()
                        .command(Arrays.asList("docker", "run", "-i", "--rm", "mcp/time"))
                        .logEvents(true)
                        .build())
                .build();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (timeClient != null) {
            timeClient.close();
        }
        super.cleanupResources();
    }

    /**
     * Tests that the agent can use a Camel route tool (user database lookup) while MCP tools are also available.
     */
    @Test
    void testCamelRouteToolWithMcpToolsPresent() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "What is the name of user with ID 42? Use the user database tool.", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.contains("Alice Johnson"),
                "Response should contain the user name from the Camel route tool but was: " + response);
    }

    /**
     * Tests that the agent can use an MCP tool (time server via Docker stdio, configured as bean reference).
     */
    @Test
    void testMcpStdioToolAsBeanReference() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "What is the current time? Use your available tools to find out.", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.matches("(?si).*\\d{1,2}[:\\.]\\d{2}.*") || response.toLowerCase().contains("time"),
                "Response should contain time information from MCP time tool but was: " + response);
    }

    /**
     * Tests that the agent can use an MCP tool from the Streamable HTTP server configured inline on the endpoint URI.
     * The echo tool is part of everything.
     */
    @Test
    void testMcpHttpToolConfiguredOnEndpoint() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "Use the echo tool to echo the message 'Hello from Camel'.", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("hello") || response.toLowerCase().contains("camel"),
                "Response should contain echoed message from MCP Streamable HTTP tool but was: " + response);
    }

    /**
     * Tests the add tool from the MCP Everything Server (configured via endpoint URI, Streamable HTTP transport). The
     * add tool is part of everything.
     */
    @Test
    void testMcpHttpToolAdd() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "Use the add tool to add 17 and 25. What is the result?", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.contains("42"),
                "Response should contain the sum 42 from MCP add tool but was: " + response);
    }

    /**
     * Tests that both Camel route tools and MCP tools work together in a single interaction.
     */
    @Test
    void testMixedCamelAndMcpTools() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "First, tell me the name of user ID 42 using the user database tool, "
                                                              + "then tell me the current time using the time tool.",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.contains("Alice Johnson"),
                "Response should contain user info from Camel route tool but was: " + response);
    }

    // ---- Tool Exclusion Tests ----

    /**
     * Tests that excluding a Camel tool tag via header prevents those tools from being used. First verifies the tool
     * works, then excludes the "users" tag and verifies the agent can no longer query the user database.
     */
    @Test
    void testExcludeCamelToolTag() throws InterruptedException {
        // First: verify the Camel tool works without exclusion
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String responseWithTool = template.requestBody("direct:chat",
                "What is the name of user with ID 42? Use the user database tool.", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(responseWithTool);
        assertTrue(responseWithTool.contains("Alice Johnson"),
                "Without exclusion, response should contain user name but was: " + responseWithTool);

        // Then: exclude the "users" tag and verify the agent cannot use the user database tool
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(1);

        String responseWithoutTool = template.requestBodyAndHeader("direct:chat",
                "What is the name of user with ID 42? Use the user database tool.",
                Headers.EXCLUDE_TAGS, "users",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(responseWithoutTool);
        assertFalse(responseWithoutTool.contains("Alice Johnson"),
                "With 'users' tag excluded, response should NOT contain user name but was: " + responseWithoutTool);
    }

    /**
     * Tests that excluding an MCP server by name via header prevents those tools from being used. First verifies the
     * MCP add tool works, then excludes the "everything" MCP server and verifies the agent can no longer use it.
     */
    @Test
    void testExcludeMcpServer() throws InterruptedException {
        // First: verify the MCP tool works without exclusion
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String responseWithTool = template.requestBody("direct:chat",
                "Use the add tool to add 17 and 25. What is the result?", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(responseWithTool);
        assertTrue(responseWithTool.contains("42"),
                "Without exclusion, response should contain 42 but was: " + responseWithTool);

        // Then: exclude the "everything" MCP server and verify the add tool is no longer available
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(1);

        String responseWithoutTool = template.requestBodyAndHeader("direct:chat",
                "Use the add tool to add 17 and 25. What is the result?",
                Headers.EXCLUDE_MCP_SERVERS, "everything",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(responseWithoutTool);
        assertFalse(responseWithoutTool.contains("42"),
                "With 'everything' MCP server excluded, response should NOT contain 42 but was: " + responseWithoutTool);
    }

    /**
     * Tests that excluding both Camel tool tags and MCP servers simultaneously works. The agent should have no tools
     * available and respond based only on its own knowledge.
     */
    @Test
    void testExcludeBothCamelTagsAndMcpServers() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        // Exclude all Camel tool tags and all MCP servers
        String response = template.requestBodyAndHeaders("direct:chat",
                "What tools do you have available? List them all.",
                Map.of(
                        Headers.EXCLUDE_TAGS, "users,weather",
                        Headers.EXCLUDE_MCP_SERVERS, "everything,time"),
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        // With all tools excluded, the agent should not be able to use any tools
        String lowerResponse = response.toLowerCase();
        assertTrue(lowerResponse.contains("no tool") || lowerResponse.contains("don't have")
                || lowerResponse.contains("do not have") || lowerResponse.contains("not available")
                || lowerResponse.contains("cannot") || !lowerResponse.contains("add")
                || !lowerResponse.contains("echo"),
                "With all tools excluded, agent should indicate no tools are available but was: " + response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Create agent with no tools at the agent level
        AgentConfiguration config = new AgentConfiguration()
                .withChatModel(chatModel);
        Agent agent = new AgentWithoutMemory(config);

        // Register agent and the pre-built MCP client bean in the registry
        this.context.getRegistry().bind("myAgent", agent);
        this.context.getRegistry().bind("timeMcpClient", timeClient);

        // Get the MCP Everything Server Streamable HTTP URL from the test-infra service
        String everythingUrl = MCP_EVERYTHING.url();

        return new RouteBuilder() {
            @Override
            public void configure() {
                // Agent route combining 3 tool sources:
                // 1. Camel route tools via tags (users, weather)
                // 2. MCP Everything Server via endpoint URI config (mcpServer.everything.*)
                // 3. MCP Time Server via pre-built bean reference (mcpClients=#timeMcpClient)
                from("direct:chat")
                        .toF("langchain4j-agent:assistant?agent=#myAgent&tags=users,weather"
                             + "&mcpServer.everything.transportType=http"
                             + "&mcpServer.everything.url=%s"
                             + "&mcpClients=#timeMcpClient",
                                everythingUrl)
                        .to("mock:response");

                // Camel route tools (internal tools via camel-langchain4j-tools)
                from("langchain4j-tools:userDb?tags=users"
                     + "&description=Query user database by user ID"
                     + "&parameter.userId=string")
                        .setBody(constant(USER_DATABASE));

                from("langchain4j-tools:weatherService?tags=weather"
                     + "&description=Get current weather for a location"
                     + "&parameter.location=string")
                        .setBody(constant("{\"weather\": \"" + WEATHER_INFO + "\", \"location\": \"Current Location\"}"));
            }
        };
    }
}

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
package org.apache.camel.component.mcp.server.vertx.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.openai.OpenAIComponent;
import org.apache.camel.component.openai.OpenAIConstants;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end agentic integration test: the exact scenario from CAMEL-24308. This Camel application exposes its own
 * {@code ai-tool} routes as MCP tools through the camel-mcp-server Vert.x engine, and an LLM (Ollama via the
 * camel-openai component) discovers and calls them over MCP streamable HTTP with automatic tool execution.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
class McpServerOpenAIAgentIT extends CamelTestSupport {

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    private final int mcpPort = AvailablePortFinder.getNextAvailable();

    private String apiKey;
    private String baseUrl;
    private String model;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        baseUrl = OLLAMA.baseUrlV1();
        model = OLLAMA.modelName();
        apiKey = OLLAMA.apiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "dummy";
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        OpenAIComponent component = new OpenAIComponent();
        if (ObjectHelper.isNotEmpty(apiKey)) {
            component.setApiKey(apiKey);
        }
        if (ObjectHelper.isNotEmpty(model)) {
            component.setModel(model);
        }
        if (ObjectHelper.isNotEmpty(baseUrl)) {
            component.setBaseUrl(baseUrl);
        }
        camelContext.addComponent("openai", component);

        // this application serves its own MCP endpoint on the Vert.x platform HTTP server
        VertxPlatformHttpServerConfiguration serverConfiguration = new VertxPlatformHttpServerConfiguration();
        serverConfiguration.setBindPort(mcpPort);
        camelContext.addService(new VertxPlatformHttpServer(serverConfiguration));

        McpServerConfiguration mcpConfiguration = new McpServerConfiguration();
        mcpConfiguration.setTags("agent");
        camelContext.addService(new McpServerBridge(mcpConfiguration));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the tools this application exposes over MCP
                from("ai-tool:get_weather?tags=agent&description=Get the current weather for a city"
                     + "&parameter.city=string&parameter.city.description=The city name"
                     + "&parameter.city.required=true")
                        .to("mock:weather-called")
                        .setBody(simple("Sunny in ${header.city}, 21 degrees celsius"));
            }
        };
    }

    /**
     * The agent route is added once the context is fully started: the openai producer initializes its MCP client
     * eagerly on route warm-up, which happens before deferred services (the platform HTTP server and the bridge) have
     * started when the route is part of the initial context.
     */
    private void addAgentRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:agent")
                        .toF("openai:chat-completion"
                             + "?mcpServer.camelTools.transportType=streamableHttp"
                             + "&mcpServer.camelTools.url=http://localhost:%d/mcp",
                                mcpPort)
                        .to("mock:response");
            }
        });
    }

    @Test
    void testLlmCallsCamelRouteToolOverMcp() throws Exception {
        addAgentRoute();

        MockEndpoint weatherCalled = getMockEndpoint("mock:weather-called");
        weatherCalled.expectedMinimumMessageCount(1);
        MockEndpoint response = getMockEndpoint("mock:response");
        response.expectedMessageCount(1);

        Exchange result = template.request("direct:agent",
                e -> e.getIn().setBody("Use the get_weather tool to find the current weather in Paris."));

        MockEndpoint.assertIsSatisfied(context);

        // the ai-tool route was really invoked through MCP
        assertThat(weatherCalled.getExchanges().get(0).getIn().getHeader("city", String.class))
                .isEqualToIgnoringCase("Paris");

        // the agentic loop executed at least one MCP tool call
        Integer iterations = result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class);
        assertThat(iterations).isNotNull().isGreaterThanOrEqualTo(1);

        // the tool result made it back into the LLM answer
        String answer = result.getMessage().getBody(String.class);
        assertThat(answer).isNotNull();
        assertThat(answer.toLowerCase()).containsAnyOf("sunny", "21");
    }
}

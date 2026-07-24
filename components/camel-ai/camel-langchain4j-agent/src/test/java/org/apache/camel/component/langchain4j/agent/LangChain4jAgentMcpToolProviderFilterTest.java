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

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.support.StubMcpClient;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproducer and regression tests for CAMEL-23948: endpoint-level MCP clients must honor
 * {@link AgentConfiguration#withMcpToolProviderFilter(java.util.function.BiPredicate)} the same way as MCP clients
 * configured directly on the agent configuration.
 */
class LangChain4jAgentMcpToolProviderFilterTest extends CamelTestSupport {

    private static final ToolProviderRequest TOOL_REQUEST = new ToolProviderRequest("test", UserMessage.from("hello"));

    private static final ToolSpecification WEATHER_TOOL = ToolSpecification.builder()
            .name("get_weather")
            .description("Returns weather for a city")
            .build();

    private static final ToolSpecification INVENTORY_TOOL = ToolSpecification.builder()
            .name("check_inventory")
            .description("Returns stock levels")
            .build();

    private final AtomicReference<ToolProvider> capturedToolProvider = new AtomicReference<>();

    @BeforeEach
    void resetCapturedToolProvider() {
        capturedToolProvider.set(null);
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:reject-all").to("langchain4j-agent:reject-all");
                from("direct:selective").to("langchain4j-agent:selective");
                from("direct:no-filter").to("langchain4j-agent:no-filter");
                from("direct:registry-agent").to("langchain4j-agent:registry-agent");
            }
        };
    }

    @Test
    void shouldRejectAllEndpointMcpToolsWhenFilterConfiguredOnAgentConfiguration() {
        configureEndpoint("langchain4j-agent:reject-all",
                new AgentConfiguration().withMcpToolProviderFilter((client, tool) -> false),
                List.of(new StubMcpClient("weather-server", WEATHER_TOOL)),
                capturingAgent());
        context.start();

        template.sendBody("direct:reject-all", new AiAgentBody<>("hello"));

        assertThat(capturedToolProvider.get()).isNotNull();
        assertThat(toolNames(capturedToolProvider.get())).isEmpty();
    }

    @Test
    void shouldApplySelectiveFilterToEndpointMcpClients() {
        configureEndpoint("langchain4j-agent:selective",
                new AgentConfiguration().withMcpToolProviderFilter((client, tool) -> "get_weather".equals(tool.name())),
                List.of(new StubMcpClient("inventory-server", WEATHER_TOOL, INVENTORY_TOOL)),
                capturingAgent());
        context.start();

        template.sendBody("direct:selective", new AiAgentBody<>("hello"));

        assertThat(toolNames(capturedToolProvider.get()))
                .containsExactly("get_weather");
    }

    @Test
    void shouldExposeAllEndpointMcpToolsWhenNoFilterConfigured() {
        configureEndpoint("langchain4j-agent:no-filter",
                new AgentConfiguration(),
                List.of(new StubMcpClient("inventory-server", WEATHER_TOOL, INVENTORY_TOOL)),
                capturingAgent());
        context.start();

        template.sendBody("direct:no-filter", new AiAgentBody<>("hello"));

        assertThat(toolNames(capturedToolProvider.get()))
                .containsExactlyInAnyOrder("get_weather", "check_inventory");
    }

    @Test
    void shouldApplyFilterFromRegistryAgentWhenEndpointHasMcpClients() {
        AgentConfiguration registryConfig = new AgentConfiguration()
                .withMcpToolProviderFilter((client, tool) -> false);
        Agent registryAgent = new AgentWithoutMemory(registryConfig) {
            @Override
            public Result<String> chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
                capturedToolProvider.set(toolProvider);
                return Result.<String> builder().content("ok").build();
            }
        };

        configureEndpoint("langchain4j-agent:registry-agent", null,
                List.of(new StubMcpClient("weather-server", WEATHER_TOOL)), registryAgent);
        context.start();

        template.sendBody("direct:registry-agent", new AiAgentBody<>("hello"));

        assertThat(toolNames(capturedToolProvider.get())).isEmpty();
    }

    private Agent capturingAgent() {
        return new AgentWithoutMemory(new AgentConfiguration()) {
            @Override
            public Result<String> chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
                capturedToolProvider.set(toolProvider);
                return Result.<String> builder().content("ok").build();
            }
        };
    }

    private void configureEndpoint(
            String endpointUri, AgentConfiguration agentConfiguration, List<McpClient> mcpClients, Agent agent) {
        LangChain4jAgentEndpoint endpoint = context.getEndpoint(endpointUri, LangChain4jAgentEndpoint.class);
        endpoint.getConfiguration().setAgentConfiguration(agentConfiguration);
        endpoint.getConfiguration().setMcpClients(mcpClients);
        endpoint.getConfiguration().setAgent(agent);
    }

    private static Set<String> toolNames(ToolProvider toolProvider) {
        assertThat(toolProvider).isNotNull();
        ToolProviderResult result = toolProvider.provideTools(TOOL_REQUEST);
        return result.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
    }
}

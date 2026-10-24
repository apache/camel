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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.component.langchain4j.agent.support.StubMcpClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tool search visibility for route and MCP tools.
 * <p>
 * A user-supplied dynamic {@code ToolProvider} registered outside Camel's composite is not covered here. Camel's own
 * route and MCP providers are non-dynamic, so LangChain4j expands them before search. A future dynamic provider would
 * be refreshed after search and would not automatically join this catalogue.
 */
class LangChain4jAgentToolSearchTest extends CamelTestSupport {

    private final RecordingChatModel chatModel = new RecordingChatModel();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:invoice?tags=erp&searchable=true&description=billing-document-invoice")
                        .setBody(constant("invoice-ok"));
                from("ai-tool:ping?tags=erp&description=health-check")
                        .setBody(constant("pong"));
                from("ai-tool:archive?tags=archive&searchable=true&description=archived-billing-document")
                        .setBody(constant("archived"));

                from("direct:search")
                        .to("langchain4j-agent:ops?agentConfiguration=#cfg&tags=erp,archive&toolSearch=true"
                            + "&toolSearchMaxResults=5&mcpClients=#weather");
                from("direct:plain")
                        .to("langchain4j-agent:plain?agentConfiguration=#cfg&tags=erp");
                from("direct:plainWithBeanSearchStrategy")
                        .to("langchain4j-agent:plainWithBeanSearchStrategy?agentConfiguration=#cfgWithSearchStrategy&tags=erp");
                from("direct:reserved-mcp")
                        .to("langchain4j-agent:reserved?agentConfiguration=#cfg&toolSearch=true&mcpClients=#reserved");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("cfg", new AgentConfiguration().withChatModel(chatModel));
        registry.bind("cfgWithSearchStrategy", new AgentConfiguration()
                .withChatModel(chatModel)
                .withToolSearchStrategy(new SimpleToolSearchStrategy()));
        registry.bind("weather", new StubMcpClient(
                "weather", ToolSpecification.builder()
                        .name("get_weather")
                        .description("weather forecast")
                        .build()));
        registry.bind("reserved", new StubMcpClient(
                "reserved", ToolSpecification.builder()
                        .name(AiToolSearch.TOOL_NAME)
                        .description("conflicts with the synthetic search tool")
                        .build()));
    }

    @Test
    void visibleRouteToolStaysAndSearchableRouteAndMcpToolsAppearAfterSearch() {
        String response = template.requestBody("direct:search", new AiAgentBody<>("find a tool"), String.class);

        assertThat(response).isEqualTo("done");
        assertThat(chatModel.turns).hasSize(2);
        assertThat(chatModel.turns.get(0)).contains("ping", AiToolSearch.TOOL_NAME);
        assertThat(chatModel.turns.get(0)).doesNotContain("invoice", "get_weather", "archive");
        assertThat(chatModel.turns.get(1)).contains("invoice", "ping", AiToolSearch.TOOL_NAME);
        assertThat(chatModel.turns.get(1)).doesNotContain("get_weather");
    }

    @Test
    void tagFilterStillRemovesToolsBeforeSearch() {
        chatModel.searchTerms = "billing";
        template.requestBodyAndHeader("direct:search", new AiAgentBody<>("filter"),
                Headers.EXCLUDE_TAGS, "archive", String.class);

        assertThat(chatModel.turns.get(0)).doesNotContain("archive");
        assertThat(chatModel.turns.get(1)).doesNotContain("archive");
        assertThat(chatModel.turns.get(1)).contains("invoice");
    }

    @Test
    void toolSearchOffIgnoresTheSearchableFlag() {
        chatModel.replyImmediately = true;
        template.requestBody("direct:plain", new AiAgentBody<>("plain"), String.class);

        assertThat(chatModel.turns).singleElement().satisfies(names -> {
            assertThat(names).contains("invoice", "ping");
            assertThat(names).doesNotContain(AiToolSearch.TOOL_NAME);
        });
    }

    @Test
    void mcpToolCannotUseTheReservedSearchName() {
        assertThatThrownBy(
                () -> template.requestBody("direct:reserved-mcp", new AiAgentBody<>("trigger"), String.class))
                .hasStackTraceContaining(
                        "Tool name '" + AiToolSearch.TOOL_NAME + "' is reserved while toolSearch=true");
    }

    @Test
    void agentConfigurationSearchStrategyDoesNotOverrideEndpointOption() {
        chatModel.turns.clear();
        chatModel.replyImmediately = true;

        template.requestBody(
                "direct:plainWithBeanSearchStrategy",
                new AiAgentBody<>("plain"),
                String.class);

        assertThat(chatModel.turns).singleElement().satisfies(names -> {
            assertThat(names).contains("invoice", "ping");
            assertThat(names).doesNotContain(
                    AiToolSearch.TOOL_NAME, "tool_search_tool");
        });
    }

    @Test
    void vectorWithoutAnEmbeddingModelFailsWhenTheAgentIsCreated() {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            ctx.getRegistry().bind("cfg", new AgentConfiguration().withChatModel(chatModel));
            ctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:vector")
                            .to("langchain4j-agent:vector?agentConfiguration=#cfg&toolSearch=true&toolSearchStrategy=vector");
                }
            });

            assertThatThrownBy(ctx::start)
                    .hasMessageContaining("does not fall back");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class RecordingChatModel implements ChatModel {
        private final List<List<String>> turns = new CopyOnWriteArrayList<>();
        private volatile String searchTerms = "invoice";
        private volatile boolean replyImmediately;

        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<String> names = request.toolSpecifications() == null
                    ? List.of()
                    : request.toolSpecifications().stream().map(ToolSpecification::name).toList();
            turns.add(new ArrayList<>(names));
            if (replyImmediately || turns.size() > 1) {
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
            ToolExecutionRequest search = ToolExecutionRequest.builder()
                    .id("search-1")
                    .name(AiToolSearch.TOOL_NAME)
                    .arguments("{\"terms\":[\"" + searchTerms + "\"]}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(search)).build())
                    .build();
        }
    }
}

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
package org.apache.camel.dsl.yaml;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.camel.component.ai.tool.AiToolEndpoint;
import org.apache.camel.component.langchain4j.agent.LangChain4jAgentEndpoint;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the maintainer tool-search route through the YAML DSL so {@code parameters} are bound by the generated URI
 * factory. This test lives next to the YAML loader because a test dependency from camel-langchain4j-agent back to
 * camel-yaml-dsl cycles through camel-allcomponents and camel-catalog.
 */
class LangChain4jAgentToolSearchYamlTest {

    @Test
    void yamlParametersBindToolSearchAndSearchable() throws Exception {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("yaml-ok")).build();
            }
        };
        String yaml = """
                - route:
                    from:
                      uri: ai-tool:invoice
                      parameters:
                        tags: erp
                        searchable: true
                        description: billing document
                      steps:
                        - setBody:
                            constant: invoice-ok
                - route:
                    from:
                      uri: direct:ask
                      steps:
                        - to:
                            uri: langchain4j-agent:ops
                            parameters:
                              agentConfiguration: "#cfg"
                              tags: erp
                              toolSearch: true
                              toolSearchMaxResults: 5
                """;

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("cfg", new AgentConfiguration().withChatModel(chatModel));
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("routes.yaml", yaml));
            context.start();

            LangChain4jAgentEndpoint endpoint = context.getEndpoints().stream()
                    .filter(LangChain4jAgentEndpoint.class::isInstance)
                    .map(LangChain4jAgentEndpoint.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(endpoint.getConfiguration().isToolSearch()).isTrue();
            assertThat(endpoint.getConfiguration().getToolSearchMaxResults()).isEqualTo(5);

            AiToolEndpoint tool = context.getEndpoints().stream()
                    .filter(AiToolEndpoint.class::isInstance)
                    .map(AiToolEndpoint.class::cast)
                    .filter(candidate -> candidate.getEndpointUri().contains("invoice"))
                    .findFirst()
                    .orElseThrow();
            assertThat(tool.getConfiguration().isSearchable()).isTrue();

            AiToolEndpoint fromJava = context.getEndpoint(
                    "ai-tool:ledger?tags=erp&searchable=true", AiToolEndpoint.class);
            assertThat(fromJava.getConfiguration().isSearchable()).isTrue();

            String response = context.createProducerTemplate()
                    .requestBody("direct:ask", new AiAgentBody<>("hello"), String.class);
            assertThat(response).isEqualTo("yaml-ok");
        }
    }
}

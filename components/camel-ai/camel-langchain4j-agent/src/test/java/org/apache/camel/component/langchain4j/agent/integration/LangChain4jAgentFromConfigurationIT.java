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

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that Camel creates the agent internally from an AgentConfiguration bean, so users no longer need to
 * instantiate AgentWithoutMemory or AgentWithMemory themselves.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentFromConfigurationIT extends CamelTestSupport {

    private static final String TEST_USER_MESSAGE = "What is Apache Camel?";

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Test
    void testAgentCreatedFromConfiguration() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:from-config", TEST_USER_MESSAGE, String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertFalse(response.isBlank());
    }

    @Test
    void testAgentCreatedFromConfigurationWithMemory() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response-memory");
        mockEndpoint.expectedMessageCount(1);

        AiAgentBody body = new AiAgentBody(TEST_USER_MESSAGE, null, "test-session");
        String response = template.requestBody("direct:from-config-with-memory", body, String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertFalse(response.isBlank());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        ChatModel chatModel = ModelHelper.loadChatModel(OLLAMA);

        AgentConfiguration agentConfiguration = new AgentConfiguration()
                .withChatModel(chatModel);

        context.getRegistry().bind("myAgentConfig", agentConfiguration);

        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .build();

        AgentConfiguration agentConfigWithMemory = new AgentConfiguration()
                .withChatModel(chatModel)
                .withChatMemoryProvider(memoryProvider);

        context.getRegistry().bind("myAgentConfigWithMemory", agentConfigWithMemory);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:from-config")
                        .to("langchain4j-agent:test?agentConfiguration=#myAgentConfig")
                        .to("mock:response");

                from("direct:from-config-with-memory")
                        .to("langchain4j-agent:test-memory?agentConfiguration=#myAgentConfigWithMemory")
                        .to("mock:response-memory");
            }
        };
    }
}

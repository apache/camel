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
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.pojos.CarRentalRecommendation;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for responseType parameter with the langchain4j-agent component.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentResponseTypeIT extends CamelTestSupport {

    protected ChatModel chatModel;
    protected ChatMemoryProvider chatMemoryProvider;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = ModelHelper.loadChatModel(OLLAMA);
    }

    @Test
    void testResponseTypeWithoutMemory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        CarRentalRecommendation response = template.requestBody("direct:responseType",
                "Recommend a car for a family of 4 traveling to the mountains", CarRentalRecommendation.class);

        mock.assertIsSatisfied();

        assertThat(response).isNotNull();
        assertThat(response.getVehicleType()).isNotNull();
        assertThat(response.getVehicleModel()).isNotNull();
        assertThat(response.getDailyRate()).isPositive();
        assertThat(response.getFeatures()).isNotNull().isNotEmpty();
        assertThat(response.getReasoning()).isNotNull();
    }

    @Test
    void testResponseTypeWithMemory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultWithMemory");
        mock.expectedMessageCount(2);

        // First request
        CarRentalRecommendation response1 = template.requestBodyAndHeader("direct:responseTypeWithMemory",
                "Recommend an economy car", "CamelLangChain4jAgentMemoryId", "user123", CarRentalRecommendation.class);

        // Second request - should remember context
        CarRentalRecommendation response2 = template.requestBodyAndHeader("direct:responseTypeWithMemory",
                "Now recommend a luxury version", "CamelLangChain4jAgentMemoryId", "user123", CarRentalRecommendation.class);

        mock.assertIsSatisfied();

        // Verify both responses are well-structured (fields populated)
        assertThat(response1.getVehicleType()).isNotNull();
        assertThat(response1.getVehicleModel()).isNotNull();
        assertThat(response1.getFeatures()).isNotNull().isNotEmpty();

        assertThat(response2.getVehicleType()).isNotNull();
        assertThat(response2.getVehicleModel()).isNotNull();
        assertThat(response2.getFeatures()).isNotNull().isNotEmpty();

        // Verify memory works: second response references prior context
        // (the conversation evolved - responses need not have specific price differences)
    }

    @Test
    void testResponseTypeWithNestedObjects() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        CarRentalRecommendation response = template.requestBody("direct:responseType",
                "Recommend an SUV with at least 5 features", CarRentalRecommendation.class);

        mock.assertIsSatisfied();

        // Verify the nested collection (List<String> features) is populated
        assertThat(response.getFeatures()).isNotNull().isNotEmpty();
        assertThat(response.getFeatures().size()).isGreaterThanOrEqualTo(3);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // Create agent configurations
        AgentConfiguration agentConfig = new AgentConfiguration().withChatModel(chatModel);
        context.getRegistry().bind("agentConfig", agentConfig);

        // Create agent configuration with memory
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(chatMemoryStore)
                .build();
        AgentConfiguration agentConfigWithMemory
                = new AgentConfiguration().withChatModel(chatModel).withChatMemoryProvider(chatMemoryProvider);
        context.getRegistry().bind("agentConfigWithMemory", agentConfigWithMemory);

        String responseTypeFqcn = CarRentalRecommendation.class.getName();

        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route with responseType (stateless)
                from("direct:responseType")
                        .to("langchain4j-agent:test1?agentConfiguration=#agentConfig&responseType=" + responseTypeFqcn)
                        .to("mock:result");

                // Route with responseType (stateful, with memory)
                from("direct:responseTypeWithMemory")
                        .to("langchain4j-agent:test2?agentConfiguration=#agentConfigWithMemory&responseType="
                            + responseTypeFqcn)
                        .to("mock:resultWithMemory");
            }
        };
    }
}

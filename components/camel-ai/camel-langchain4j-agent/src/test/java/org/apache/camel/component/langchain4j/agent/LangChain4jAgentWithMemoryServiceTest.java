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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithMemory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.pojos.PersistentChatMemoryStore;
import org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class LangChain4jAgentWithMemoryServiceTest extends BaseLangChain4jAgent {

    @RegisterExtension
    public static OpenAIMock openAIMock = new OpenAIMock()
            .builder()
            .when("Hi! Can you look up user 123 and tell me about our rental policies?")
            .assertRequest(request -> {
                // Both tools are part of the request
                Assertions.assertThat(request).contains("QueryUserDatabaseByUserID", "GetCurrentWeatherInformation");
            })
            .invokeTool("QueryUserDatabaseByUserID")
            .withParam("userId", "123")
            .replyWithToolContent(" " + COMPANY_KNOWLEDGE_BASE)
            .end()
            .when("What's his preferred vehicle type?")
            .assertRequest(request -> {
                // Assert that memory is working as expected
                Assertions.assertThat(request)
                        .contains("Hi! Can you look up user 123 and tell me about our rental policies?");
            })
            .replyWith("SUV")
            .end()
            .when("What's the weather in London?")
            .invokeTool("GetCurrentWeatherInformation")
            .withParam("location", "London")
            .end()
            .build();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatMemoryStore = new PersistentChatMemoryStore();
        chatModel = createChatModel(null, openAIMock.getBaseUrl());
        chatMemoryProvider = createMemoryProvider(chatMemoryStore);
    }

    @Test
    public void testToolThenMemoryThenAnotherTool() throws Exception {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(3);

        AiAgentBody<?> firstRequest = new AiAgentBody<>(
                "Hi! Can you look up user 123 and tell me about our rental policies?", null, MEMORY_ID_SESSION);

        String firstResponse = template.requestBody("direct:complete-agent", firstRequest, String.class);

        assertNotNull(firstResponse, "First response should not be null");
        Assertions.assertThat(firstResponse)
                .contains("John Smith", "Gold")
                .withFailMessage("Response should contain user information from tools");
        Assertions.assertThat(firstResponse)
                .contains("21", "age", "rental")
                .withFailMessage("Response should contain rental policy information from RAG");

        // Second interaction: Follow-up question
        AiAgentBody<?> secondRequest = new AiAgentBody<>("What's his preferred vehicle type?", null, MEMORY_ID_SESSION);

        String secondResponse = template.requestBody("direct:complete-agent", secondRequest, String.class);

        assertNotNull(secondResponse, "Second response should not be null");
        Assertions.assertThat(secondResponse).isEqualTo("SUV");

        // Third interaction: Follow-up weather question
        AiAgentBody<?> thirdRequest = new AiAgentBody<>("What's the weather in London?", null, MEMORY_ID_SESSION);

        String thirdResponse = template.requestBody("direct:complete-agent", thirdRequest, String.class);

        assertNotNull(thirdRequest, "Third response should not be null");
        Assertions.assertThat(thirdResponse).contains(WEATHER_INFO);

        mockEndpoint.assertIsSatisfied();

        // Verify guardrails were called
        assertTrue(TestSuccessInputGuardrail.wasValidated(), "Input guardrail should have been called");

        // Verify memory persistence
        assertTrue(chatMemoryStore.getMemoryCount() > 0, "Memory should be persisted");
        assertFalse(chatMemoryStore.getMessages(MEMORY_ID_SESSION).isEmpty(), "Session memory should contain messages");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withChatMemoryProvider(chatMemoryProvider)
                .withInputGuardrailClassesList(
                        "org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail")
                .withOutputGuardrailClasses(List.of());

        Agent agent = new AgentWithMemory(configuration);

        this.context.getRegistry().bind("agent", agent);

        return new RouteBuilder() {
            public void configure() {
                //  Tools + Memory + Guardrails + RAG
                from("direct:complete-agent")
                        .to("langchain4j-agent:complete?agent=#agent&tags=users,weather")
                        .to("mock:agent-response");

                // Tool routes for function calling
                from("langchain4j-tools:userDb?tags=users&description=Query user database by user ID&parameter.userId=string")
                        .setBody(constant(USER_DATABASE));

                from("langchain4j-tools:weatherService?tags=weather&description=Get current weather information&parameter.location=string")
                        .setBody(constant(
                                "{\"weather\": \"" + WEATHER_INFO + "\", \"location\": \"Current Location\"}"));
            }
        };
    }
}

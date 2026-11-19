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
package org.apache.camel.component.langchain4j.service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.apache.camel.component.langchain4j.service.support.ModelHelper;
import org.apache.camel.component.langchain4j.service.support.ModelTurn;
import org.apache.camel.component.langchain4j.service.support.WeatherTool;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same conformance contract, against a real model served by the Ollama test-infra.
 * <p>
 * The inherited scenarios are already covered in CI by {@code LangChain4jServiceConformanceTest}, against the OpenAI
 * mock server; running them here too is what catches a drift between the stub and a real provider, such as the pinned
 * LangChain4j version no longer talking to a real server. The tests added below cannot be unit tests: each one asserts
 * that a model behaved, not that the component did.
 * <p>
 * The content assertions depend on the pinned test-infra model following instructions at temperature 0. If the default
 * model changes, these are the first assertions to re-check.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
class LangChain4jServiceConformanceIT extends LangChain4jServiceConformanceTestSupport {

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected ChatModel modelAnswering(ModelTurn... turns) {
        // A real model answers as it likes, so the declared turns are only there for the mocked run
        return ModelHelper.loadChatModel(OLLAMA);
    }

    @Override
    protected ChatModel modelCallingTool(
            String prompt, String toolName, String argumentName, String argumentValue, String finalReply) {
        return ModelHelper.loadChatModel(OLLAMA);
    }

    @Override
    protected ChatModel failingModel(String prompt) {
        // A model name the server does not host is how the provider's own failure path gets exercised for real
        return ModelHelper.loadChatModel(OLLAMA, "no-such-model");
    }

    @Override
    protected void assertAnswer(String body, String expectedReply) {
        assertThat(body)
                .as("a real model words its own answer, so the contract is only that one came back")
                .isNotBlank();
    }

    /**
     * Cannot be a unit test: a stub cannot decide to read its system message. This asserts the model did.
     */
    @Test
    void theModelAnswersFromItsSystemMessage() throws Exception {
        context.getRegistry().bind("assistant", AiServices.builder(ContextAssistant.class)
                .chatModel(ModelHelper.loadChatModel(OLLAMA))
                .build());
        startWithRouteTo("langchain4j-service:assistant?method=analyze");

        String body = template.requestBodyAndHeader("direct:start", "What is the secret word?",
                LangChain4jServiceHeaders.VARIABLES, Map.of("context", "The secret word is pineapple."),
                String.class);

        assertThat(body)
                .as("the secret word only exists in the rendered @SystemMessage, so recalling it proves the model "
                    + "received and used it")
                .containsIgnoringCase("pineapple");
    }

    /**
     * Cannot be a unit test: a stub cannot recall anything. This asserts the model read its chat memory.
     */
    @Test
    void theModelRecallsAcrossTurnsOfOneMemoryId() throws Exception {
        context.getRegistry().bind("assistant", AiServices.builder(MemoryAssistant.class)
                .chatModel(ModelHelper.loadChatModel(OLLAMA))
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        template.requestBodyAndHeader("direct:start", "My name is John. Remember it.",
                LangChain4jServiceHeaders.MEMORY_ID, "session-123", String.class);
        String body = template.requestBodyAndHeader("direct:start", "What is my name?",
                LangChain4jServiceHeaders.MEMORY_ID, "session-123", String.class);

        assertThat(body)
                .as("the name was only given on the first turn, so recalling it proves the memory id bound from the "
                    + "header selected the same conversation")
                .containsIgnoringCase("John");
    }

    /**
     * Cannot be a unit test: a stub is told to call the tool. This asserts a model chose to, from plain language.
     */
    @Test
    void theModelElectsToCallATool() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        context.getRegistry().bind("assistant", AiServices.builder(ToolAssistant.class)
                .chatModel(ModelHelper.loadChatModel(OLLAMA))
                .tools(new WeatherTool(invoked))
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        String body = template.requestBody("direct:start",
                "You MUST use the getWeather tool. What's the weather in Paris?", String.class);

        assertThat(invoked)
                .as("no stub forced this: the model read the tool specification and decided to call it")
                .isTrue();
        assertThat(body).isNotBlank();
    }
}

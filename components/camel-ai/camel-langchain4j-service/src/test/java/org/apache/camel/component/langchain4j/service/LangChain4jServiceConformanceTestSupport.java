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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.langchain4j.service.support.ModelTurn;
import org.apache.camel.component.langchain4j.service.support.WeatherTool;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.langchain4j.service.support.ModelTurn.turn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The behaviour the component must show against a real LangChain4j AI Service, whatever is behind the model.
 * <p>
 * Every test here runs twice: once in {@code LangChain4jServiceConformanceTest} against the in-repo OpenAI mock server,
 * which is what makes it run in CI in seconds, and once in {@code LangChain4jServiceConformanceIT} against a real model
 * served by Ollama. A scenario therefore cannot end up covered only by the integration test, which is disabled on
 * GitHub Actions.
 * <p>
 * The services are built with the real {@code AiServices} and {@code AgenticServices} builders on purpose. A
 * hand-rolled proxy cannot stand in here: the proxy shape, the interface set and the place the annotations sit are
 * LangChain4j's, and those are exactly what the component has to cope with. Tests that are about the component's own
 * introspection rather than that contract use a proxy and live in the other test classes.
 * <p>
 * Each scenario describes its exchange with the model as {@link ModelTurn}s rather than building a model itself, so
 * both backends set themselves up from one description. Against the mock the prompt of a turn is matched exactly, which
 * turns it into an assertion that the component bound what the service then rendered. A real model ignores it. Anything
 * sharper than the assertions here belongs in a subclass, or in {@code LangChain4jServicePromptRenderingTest}, which
 * inspects the rendered prompt directly.
 */
public abstract class LangChain4jServiceConformanceTestSupport extends LangChain4jServiceTestSupport {

    protected static final String TOPIC = "a camel crossing the desert";

    protected static final String STORY_PROMPT
            = "Write exactly one short sentence telling a story about " + TOPIC + ". Output only that sentence.";

    /**
     * A model that answers the given turns. Against the mock each prompt is matched exactly; a real model ignores them
     * and answers as it likes, which is why {@link #assertAnswer(String, String)} exists.
     *
     * @param  turns the exchange the AI Service is expected to have with the model
     * @return       the model
     */
    protected abstract ChatModel modelAnswering(ModelTurn... turns);

    /**
     * A model that answers the prompt by calling the named tool once, then replies. A real model decides for itself, so
     * the shared assertion is only that the tool ran.
     *
     * @param  prompt        the user message the AI Service is expected to send
     * @param  toolName      the tool the mocked model must call
     * @param  argumentName  the name of the tool argument it must pass
     * @param  argumentValue its value
     * @param  finalReply    what the mocked model answers once the tool ran
     * @return               the model
     */
    protected abstract ChatModel modelCallingTool(
            String prompt, String toolName, String argumentName, String argumentValue, String finalReply);

    /**
     * A model whose invocation fails with {@link ModelNotFoundException}, the way a provider fails when asked for a
     * model it does not host. Both backends produce it from the provider itself rather than by throwing directly, so
     * the test covers the provider's own mapping of the failure.
     *
     * @param  prompt the user message the AI Service is expected to send
     * @return        the model
     */
    protected abstract ChatModel failingModel(String prompt);

    /**
     * Asserts the body carries the model's answer: an exact match when the model was mocked, something weaker when a
     * real model was free to word it.
     *
     * @param body          the message body the route produced
     * @param expectedReply the reply a mocked model was told to give
     */
    protected abstract void assertAnswer(String body, String expectedReply);

    // AI Service interfaces, in the shapes a user writes them

    public interface Assistant {
        String chat(@UserMessage String text);
    }

    public interface MultiMethodAssistant {
        String chat(@UserMessage String text);

        String analyze(@UserMessage String text);
    }

    public interface ContextAssistant {
        @SystemMessage("Use the following context when answering: {{context}}")
        String analyze(@UserMessage String text, @V("context") String context);
    }

    public interface MemoryAssistant {
        String chat(@UserMessage String text, @MemoryId String memoryId);
    }

    public interface ToolAssistant {
        String chat(@UserMessage String text);
    }

    // Agent interfaces: LangChain4j requires exactly one @Agent method per agent interface

    public interface StoryWriterAgent {
        @Agent(value = "Writes a one-sentence story about a topic", outputKey = "story")
        @UserMessage("Write exactly one short sentence telling a story about {{topic}}. Output only that sentence.")
        String writeStory(@V("topic") String topic);
    }

    public interface StoryRewriterAgent {
        @Agent(value = "Rewrites a story in upper case", outputKey = "loudStory")
        @UserMessage("Rewrite this text in upper case. Output only the rewritten text: {{story}}")
        String rewrite(@V("story") String story);
    }

    public interface StoryFlowAgent {
        @Agent
        String writeLoudStory(@V("topic") String topic);
    }

    @Test
    void singleMethodIsResolvedAndInvoked() throws Exception {
        context.getRegistry().bind("assistant", AiServices.builder(Assistant.class)
                .chatModel(modelAnswering(turn("Say something short", "a one line answer")))
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        String body = template.requestBody("direct:start", "Say something short", String.class);

        assertAnswer(body, "a one line answer");
    }

    @Test
    void namedMethodIsResolvedOnAMultiMethodService() throws Exception {
        context.getRegistry().bind("assistant", AiServices.builder(MultiMethodAssistant.class)
                .chatModel(modelAnswering(turn("Analyse this", "an analysis")))
                .build());
        startWithRouteTo("langchain4j-service:assistant?method=analyze");

        String body = template.requestBody("direct:start", "Analyse this", String.class);

        assertAnswer(body, "an analysis");
    }

    @Test
    void templateVariableReachesTheService() throws Exception {
        context.getRegistry().bind("assistant", AiServices.builder(ContextAssistant.class)
                .chatModel(modelAnswering(turn("What is the secret word?", "pineapple")))
                .build());
        startWithRouteTo("langchain4j-service:assistant?method=analyze");

        String body = template.requestBodyAndHeader("direct:start", "What is the secret word?",
                LangChain4jServiceHeaders.VARIABLES, Map.of("context", "The secret word is pineapple."),
                String.class);

        assertAnswer(body, "pineapple");
    }

    @Test
    void memoryIdKeepsOneConversationPerId() throws Exception {
        // Keeping each memory the provider hands out is what lets the test see which conversation the header chose:
        // the answers cannot show it, since a mocked model replies the same to both ids and a real one is free to
        // word it as it likes
        Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();
        context.getRegistry().bind("assistant", AiServices.builder(MemoryAssistant.class)
                .chatModel(modelAnswering(
                        turn("My name is John.", "noted"),
                        turn("What is my name?", "noted")))
                .chatMemoryProvider(memoryId -> memories.computeIfAbsent(
                        memoryId, id -> MessageWindowChatMemory.withMaxMessages(10)))
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        String first = template.requestBodyAndHeader("direct:start", "My name is John.",
                LangChain4jServiceHeaders.MEMORY_ID, "session-123", String.class);
        String second = template.requestBodyAndHeader("direct:start", "What is my name?",
                LangChain4jServiceHeaders.MEMORY_ID, "session-123", String.class);
        String other = template.requestBodyAndHeader("direct:start", "What is my name?",
                LangChain4jServiceHeaders.MEMORY_ID, "session-456", String.class);

        assertAnswer(first, "noted");
        assertAnswer(second, "noted");
        assertAnswer(other, "noted");

        assertThat(memories)
                .as("the memory id header, and nothing else, chooses the conversation, so the AI Service must have "
                    + "asked for exactly the two ids the route sent")
                .containsOnlyKeys("session-123", "session-456");
        assertThat(memories.get("session-123").messages())
                .as("the two exchanges sent under the same id share one conversation: two user messages and the two "
                    + "answers to them")
                .hasSize(4);
        assertThat(memories.get("session-456").messages())
                .as("a second id starts its own conversation, so it holds its single exchange only and knows nothing "
                    + "of the first one")
                .hasSize(2);
    }

    @Test
    void toolEnabledServiceRunsItsTool() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        String prompt = "You MUST use the getWeather tool. What's the weather in Paris?";
        context.getRegistry().bind("assistant", AiServices.builder(ToolAssistant.class)
                .chatModel(modelCallingTool(prompt, "getWeather", "location", "Paris", "It is sunny in Paris"))
                .tools(new WeatherTool(invoked))
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        String body = template.requestBody("direct:start", prompt, String.class);

        assertThat(invoked)
                .as("the AI Service must run the tool the model asked for, and the component must not get in the way")
                .isTrue();
        assertAnswer(body, "It is sunny in Paris");
    }

    @Test
    void modelFailurePropagatesUnwrapped() throws Exception {
        context.getRegistry().bind("assistant", AiServices.builder(Assistant.class)
                .chatModel(failingModel("Anything"))
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        assertThatThrownBy(() -> template.requestBody("direct:start", "Anything", String.class))
                .as("the provider's own exception must survive the AI Service proxy and the reflective invocation, "
                    + "so an onException clause can match its real type")
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void agentMethodIsResolvedAmongTheFrameworkInterfaces() throws Exception {
        context.getRegistry().bind("storyWriter", AgenticServices.agentBuilder(StoryWriterAgent.class)
                .chatModel(modelAnswering(turn(STORY_PROMPT, "a camel walked on")))
                .build());
        startWithRouteTo("langchain4j-service:storyWriter?method=writeStory");

        // The @Agent method takes only @V parameters, so the body carries nothing
        String body = template.requestBodyAndHeader("direct:start", null,
                LangChain4jServiceHeaders.VARIABLES, Map.of("topic", TOPIC), String.class);

        assertAnswer(body, "a camel walked on");
    }

    @Test
    void agenticWorkflowRunsEndToEnd() throws Exception {
        StoryWriterAgent writer = AgenticServices.agentBuilder(StoryWriterAgent.class)
                .chatModel(modelAnswering(turn(STORY_PROMPT, "a camel walked on")))
                .build();
        StoryRewriterAgent rewriter = AgenticServices.agentBuilder(StoryRewriterAgent.class)
                .chatModel(modelAnswering(
                        turn("Rewrite this text in upper case. Output only the rewritten text: a camel walked on",
                                "A CAMEL WALKED ON")))
                .build();
        context.getRegistry().bind("storyFlow", AgenticServices.sequenceBuilder(StoryFlowAgent.class)
                .subAgents(writer, rewriter)
                .outputKey("loudStory")
                .build());
        startWithRouteTo("langchain4j-service:storyFlow?method=writeLoudStory");

        String body = template.requestBodyAndHeader("direct:start", null,
                LangChain4jServiceHeaders.VARIABLES, Map.of("topic", TOPIC), String.class);

        assertAnswer(body, "A CAMEL WALKED ON");
    }
}

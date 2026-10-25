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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.tool.AiToolConstants;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CAMEL-23952: {@link AgentConfiguration#withExecuteToolsConcurrently()} and exchange-safe Camel route tools.
 */
class LangChain4jAgentExecuteToolsConcurrentlyTest extends CamelTestSupport {

    private static final String TAG = "concurrent-tools";

    private final TwoToolRoundTripChatModel chatModel = new TwoToolRoundTripChatModel();
    private final AtomicInteger chatRound = new AtomicInteger();
    private CountDownLatch bothToolsEntered = new CountDownLatch(2);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxConcurrent = new AtomicInteger();
    private final Map<String, String> toolConversations = new ConcurrentHashMap<>();

    @BeforeEach
    void resetConcurrencyState() {
        chatRound.set(0);
        bothToolsEntered = new CountDownLatch(2);
        maxConcurrent.set(0);
        inFlight.set(0);
        toolConversations.clear();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:agent")
                        .to("langchain4j-agent:test?agentConfiguration=#agentConfig&tags=" + TAG);

                from("direct:agentWithMemory")
                        .to("langchain4j-agent:test?agentConfiguration=#memoryConfig&tags=" + TAG);

                from("ai-tool:slowToolA?tags=" + TAG + "&description=Slow tool A")
                        .process(exchange -> recordConcurrentEntry("A", exchange));

                from("ai-tool:slowToolB?tags=" + TAG + "&description=Slow tool B")
                        .process(exchange -> recordConcurrentEntry("B", exchange));
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("agentConfig", new AgentConfiguration()
                .withChatModel(chatModel)
                .withExecuteToolsConcurrently()
                .withMaxToolCallingRoundTrips(5));
        registry.bind("memoryConfig", new AgentConfiguration()
                .withChatModel(chatModel)
                .withChatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(20))
                .withExecuteToolsConcurrently()
                .withMaxToolCallingRoundTrips(5));
    }

    @Test
    void concurrentToolExecutionRunsBothToolsInParallel() throws Exception {
        String response = template.requestBody("direct:agent",
                new AiAgentBody<>("run tools").withMemoryId("parallel-conversation"), String.class);

        assertThat(response).isEqualTo("done");
        assertThat(bothToolsEntered.await(10, TimeUnit.SECONDS))
                .as("both tools should be invoked")
                .isTrue();
        assertThat(toolConversations).as("memory-less agents must not propagate the default conversation ID").isEmpty();
        assertThat(maxConcurrent.get())
                .as("tools should overlap when executeToolsConcurrently is enabled")
                .isGreaterThanOrEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = { "conversation-123", "default" })
    void concurrentCallsHaveDistinctCallIdsAndShareTheConversationId(String conversationId) {
        String response = template.requestBody("direct:agentWithMemory",
                new AiAgentBody<>("run tools").withMemoryId(conversationId), String.class);

        assertThat(response).isEqualTo("done");
        assertThat(toolConversations).containsExactlyInAnyOrderEntriesOf(
                Map.of("a", conversationId, "b", conversationId));
    }

    @Test
    void producerResolvesCamelManagedExecutorWhenNoneConfigured() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            ChatModel noopModel = new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
                }
            };
            AgentConfiguration config = new AgentConfiguration()
                    .withChatModel(noopModel)
                    .withExecuteToolsConcurrently();
            ctx.getRegistry().bind("cfg", config);

            ctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:x").to("langchain4j-agent:test?agentConfiguration=#cfg");
                }
            });

            ctx.start();

            assertThat(config.getExecuteToolsExecutor())
                    .as("registry AgentConfiguration must not be mutated with a managed executor")
                    .isNull();
        }
    }

    @Test
    void managedExecutorWorksAfterContextStopAndRestart() throws Exception {
        context.stop();
        bindToRegistry(context.getRegistry());
        context.start();

        String response = template.requestBody("direct:agent", new AiAgentBody<>("run tools"), String.class);

        assertThat(response).isEqualTo("done");
    }

    private void recordConcurrentEntry(String label, Exchange exchange) throws InterruptedException {
        String callId = exchange.getMessage().getHeader(AiToolConstants.TOOL_CALL_ID, String.class);
        assertThat(callId).isEqualTo(label.toLowerCase(Locale.ROOT));
        String conversationId = exchange.getMessage().getHeader(AiToolConstants.CONVERSATION_ID, String.class);
        if (conversationId != null) {
            toolConversations.put(callId, conversationId);
        }
        int concurrent = inFlight.incrementAndGet();
        maxConcurrent.updateAndGet(current -> Math.max(current, concurrent));
        bothToolsEntered.countDown();
        assertThat(bothToolsEntered.await(10, TimeUnit.SECONDS))
                .as("both tools should enter before either completes")
                .isTrue();
        inFlight.decrementAndGet();
        exchange.getMessage().setBody(label);
    }

    private final class TwoToolRoundTripChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            if (chatRound.getAndIncrement() == 0) {
                List<ToolExecutionRequest> requests = List.of(
                        ToolExecutionRequest.builder().id("a").name("slowToolA").arguments("{}").build(),
                        ToolExecutionRequest.builder().id("b").name("slowToolB").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(requests).build())
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }
    }
}

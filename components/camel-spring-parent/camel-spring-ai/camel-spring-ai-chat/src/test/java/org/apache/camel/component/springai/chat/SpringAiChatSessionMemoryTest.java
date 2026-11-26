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
package org.apache.camel.component.springai.chat;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Session memory through the {@code sessionService} option: the {@link SpringAiChatConstants#CONVERSATION_ID} header
 * selects the session, and {@code sessionService} is used instead of {@code chatMemory} and
 * {@code chatMemoryVectorStore}. The tests capture the {@link Prompt} handed to the {@link ChatModel}.
 */
@SuppressWarnings("removal")
class SpringAiChatSessionMemoryTest extends CamelTestSupport {

    private static final String TOOL_PREFIX = "Use the tool: ";

    private ChatModel mockChatModel;
    private SessionService sessionService;
    private SessionService customSessionService;
    private ChatMemory chatMemory;
    private VectorStore memoryVectorStore;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        mockChatModel = mock(ChatModel.class);
        when(mockChatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(mockChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            Message last = prompt.getInstructions().get(prompt.getInstructions().size() - 1);
            if (last instanceof UserMessage user && user.getText().startsWith(TOOL_PREFIX)) {
                AssistantMessage toolCall = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1", "function", "getCapital", "{\"country\":\"France\"}")))
                        .build();
                return new ChatResponse(List.of(new Generation(toolCall)));
            }
            AssistantMessage answer = new AssistantMessage("Answer to: " + prompt.getUserMessage().getText());
            return new ChatResponse(List.of(new Generation(answer)));
        });

        sessionService = DefaultSessionService.builder()
                .sessionRepository(InMemorySessionRepository.builder().build())
                .build();
        customSessionService = DefaultSessionService.builder()
                .sessionRepository(InMemorySessionRepository.builder().build())
                .build();
        chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
        memoryVectorStore = mock(VectorStore.class);
    }

    @Test
    void sameConversationIdSendsTheFirstExchangeWithTheSecondRequest() {
        chat("direct:session", "My name is Alice", "user-1");
        chat("direct:session", "What is my name?", "user-1");

        assertThat(prompts()).hasSize(2);
        assertThat(prompts().get(1).getInstructions())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.USER, "My name is Alice"),
                        tuple(MessageType.ASSISTANT, "Answer to: My name is Alice"),
                        tuple(MessageType.USER, "What is my name?"));
        assertThat(sessionService.getMessages("user-1")).hasSize(4);
    }

    @Test
    void differentConversationIdDoesNotSeeTheOtherSession() {
        chat("direct:session", "My name is Alice", "user-1");
        chat("direct:session", "What is my name?", "user-2");

        assertThat(prompts().get(1).getInstructions())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(tuple(MessageType.USER, "What is my name?"));
        assertThat(sessionService.getMessages("user-1")).hasSize(2);
        assertThat(sessionService.getMessages("user-2")).hasSize(2);
    }

    @Test
    void noConversationIdUsesNoMemory() {
        chat("direct:session", "My name is Alice", null);
        chat("direct:session", "What is my name?", null);

        assertThat(prompts().get(1).getInstructions())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(tuple(MessageType.USER, "What is my name?"));
    }

    @Test
    void blankConversationIdFailsTheExchange() {
        Exchange exchange = template().request("direct:session", e -> {
            e.getIn().setBody("My name is Alice");
            e.getIn().setHeader(SpringAiChatConstants.CONVERSATION_ID, " ");
        });

        assertThat(exchange.getException()).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mockChatModel);
    }

    @Test
    void sessionServiceIsUsedInsteadOfChatMemory() {
        chat("direct:sessionAndChatMemory", "My name is Alice", "user-1");
        chat("direct:sessionAndChatMemory", "What is my name?", "user-1");

        assertThat(prompts().get(1).getInstructions()).hasSize(3);
        assertThat(sessionService.getMessages("user-1")).hasSize(4);
        assertThat(chatMemory.get("user-1")).isEmpty();
    }

    /**
     * As in Spring Boot with the session starter: a {@link SessionService} and a {@link ChatMemory} bean are both
     * autowired.
     */
    @Test
    void autowiredSessionServiceIsUsedInsteadOfAutowiredChatMemory() {
        chat("direct:autowired", "My name is Alice", "user-1");
        chat("direct:autowired", "What is my name?", "user-1");

        assertThat(prompts().get(1).getInstructions()).hasSize(3);
        assertThat(sessionService.getMessages("user-1")).hasSize(4);
        assertThat(chatMemory.get("user-1")).isEmpty();
    }

    @Test
    void sessionServiceIsUsedInsteadOfChatMemoryVectorStore() {
        chat("direct:sessionAndVectorStore", "My name is Alice", "user-1");
        chat("direct:sessionAndVectorStore", "What is my name?", "user-1");

        assertThat(prompts().get(1).getInstructions()).hasSize(3);
        assertThat(sessionService.getMessages("user-1")).hasSize(4);
        verifyNoInteractions(memoryVectorStore);
    }

    /**
     * The tool-calling loop runs inside the turn: the tool round is sent once, and the session keeps the tool call and
     * its result.
     */
    @Test
    void toolCallRoundIsKeptInTheSession() {
        chat("direct:sessionWithTools", TOOL_PREFIX + "what is the capital of France?", "user-1");

        assertThat(prompts()).hasSize(2);
        assertThat(prompts().get(1).getInstructions())
                .extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL);
        assertThat(((ToolResponseMessage) prompts().get(1).getInstructions().get(2)).getResponses().get(0).responseData())
                .contains("Paris");
        assertThat(sessionService.getMessages("user-1"))
                .extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT);
    }

    @Test
    void sessionMessageWindowSendsTheNewestTurnsAndTheSessionKeepsEverything() {
        chat("direct:window", "one", "user-1");
        chat("direct:window", "two", "user-1");
        chat("direct:window", "three", "user-1");

        assertThat(prompts().get(2).getInstructions())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.USER, "two"),
                        tuple(MessageType.ASSISTANT, "Answer to: two"),
                        tuple(MessageType.USER, "three"));
        assertThat(sessionService.getMessages("user-1")).hasSize(6);
    }

    /**
     * The newest two messages are a tool result and the reply: the window is extended back to the user message of their
     * turn, so the tool result is never sent without its tool call.
     */
    @Test
    void sessionMessageWindowKeepsAToolRoundWhole() {
        chat("direct:windowWithTools", TOOL_PREFIX + "what is the capital of France?", "user-1");
        chat("direct:windowWithTools", "And of Germany?", "user-1");

        assertThat(prompts()).hasSize(3);
        assertThat(prompts().get(2).getInstructions())
                .extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT,
                        MessageType.USER);
    }

    @Test
    void defaultSessionMessageWindowSendsTheNewest20Messages() {
        for (int i = 1; i <= 12; i++) {
            chat("direct:session", "message " + i, "user-1");
        }

        List<Message> last = prompts().get(11).getInstructions();
        assertThat(last).hasSize(21);
        assertThat(last.get(0).getText()).isEqualTo("message 2");
        assertThat(sessionService.getMessages("user-1")).hasSize(24);
    }

    @Test
    void sessionMessageWindowZeroSendsTheWholeSession() {
        for (int i = 1; i <= 12; i++) {
            chat("direct:wholeSession", "message " + i, "user-1");
        }

        List<Message> last = prompts().get(11).getInstructions();
        assertThat(last).hasSize(23);
        assertThat(last.get(0).getText()).isEqualTo("message 1");
    }

    /**
     * Session advisors of your own, with compaction for example, passed with the advisors option: they get the
     * conversation id, and the autowired sessionService and chatMemory are not used, so nothing is stored twice.
     */
    @Test
    void memoryAdvisorsOfTheAdvisorsOptionGetTheConversationId() {
        chat("direct:customAdvisors", "My name is Alice", "user-1");
        chat("direct:customAdvisors", "What is my name?", "user-1");

        assertThat(prompts().get(1).getInstructions())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.USER, "My name is Alice"),
                        tuple(MessageType.ASSISTANT, "Answer to: My name is Alice"),
                        tuple(MessageType.USER, "What is my name?"));
        assertThat(customSessionService.getMessages("user-1")).hasSize(4);
        assertThat(sessionService.findById("user-1")).isNull();
        assertThat(chatMemory.get("user-1")).isEmpty();
    }

    @Test
    void chatMemoryAloneStillKeepsTheConversation() {
        chat("direct:chatMemory", "My name is Alice", "user-1");
        chat("direct:chatMemory", "What is my name?", "user-1");

        assertThat(prompts().get(1).getInstructions())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.USER, "My name is Alice"),
                        tuple(MessageType.ASSISTANT, "Answer to: My name is Alice"),
                        tuple(MessageType.USER, "What is my name?"));
        assertThat(chatMemory.get("user-1")).hasSize(4);
        assertThat(sessionService.findById("user-1")).isNull();
    }

    private void chat(String uri, String text, String conversationId) {
        String answer = template().requestBodyAndHeader(uri, text, SpringAiChatConstants.CONVERSATION_ID, conversationId,
                String.class);
        assertThat(answer).isEqualTo("Answer to: " + text);
    }

    private List<Prompt> prompts() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(mockChatModel, atLeastOnce()).call(captor.capture());
        return captor.getAllValues();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // one bean of each type, so the endpoints that keep autowiring get them all
                context.getRegistry().bind("chatModel", mockChatModel);
                context.getRegistry().bind("sessionService", sessionService);
                context.getRegistry().bind("chatMemory", chatMemory);

                from("direct:session")
                        .to("spring-ai-chat:session?autowiredEnabled=false&chatModel=#chatModel"
                            + "&sessionService=#sessionService");

                from("direct:sessionAndChatMemory")
                        .to("spring-ai-chat:both?autowiredEnabled=false&chatModel=#chatModel"
                            + "&sessionService=#sessionService&chatMemory=#chatMemory");

                from("direct:autowired")
                        .to("spring-ai-chat:autowired");

                context.getRegistry().bind("tools", Arrays.asList(MethodToolCallbackProvider.builder()
                        .toolObjects(new CapitalTools())
                        .build()
                        .getToolCallbacks()));
                from("direct:sessionWithTools")
                        .to("spring-ai-chat:tools?autowiredEnabled=false&chatModel=#chatModel"
                            + "&sessionService=#sessionService&toolCallbacks=#tools");

                from("direct:windowWithTools")
                        .to("spring-ai-chat:windowTools?autowiredEnabled=false&chatModel=#chatModel"
                            + "&sessionService=#sessionService&toolCallbacks=#tools&sessionMessageWindow=2");

                from("direct:window")
                        .to("spring-ai-chat:window?autowiredEnabled=false&chatModel=#chatModel"
                            + "&sessionService=#sessionService&sessionMessageWindow=2");

                from("direct:wholeSession")
                        .to("spring-ai-chat:whole?autowiredEnabled=false&chatModel=#chatModel"
                            + "&sessionService=#sessionService&sessionMessageWindow=0");

                // autowired: the sessionService and chatMemory beans are set too, and must not be used
                List<Advisor> customSessionAdvisors = SessionAdvisors.builder(customSessionService).build();
                context.getRegistry().bind("customSessionAdvisors", customSessionAdvisors);
                from("direct:customAdvisors")
                        .to("spring-ai-chat:custom?advisors=#customSessionAdvisors");

                from("direct:chatMemory")
                        .to("spring-ai-chat:memory?autowiredEnabled=false&chatModel=#chatModel&chatMemory=#chatMemory");

                // not bound in the registry, where it would be autowired as the RAG vectorStore
                SpringAiChatEndpoint vectorStoreEndpoint = getContext().getEndpoint(
                        "spring-ai-chat:vector?autowiredEnabled=false&chatModel=#chatModel&sessionService=#sessionService",
                        SpringAiChatEndpoint.class);
                vectorStoreEndpoint.getConfiguration().setChatMemoryVectorStore(memoryVectorStore);
                from("direct:sessionAndVectorStore")
                        .to(vectorStoreEndpoint);
            }
        };
    }

    public static class CapitalTools {

        @Tool(description = "Get the capital city of a country")
        public String getCapital(String country) {
            return "The capital of " + country + " is Paris";
        }
    }
}

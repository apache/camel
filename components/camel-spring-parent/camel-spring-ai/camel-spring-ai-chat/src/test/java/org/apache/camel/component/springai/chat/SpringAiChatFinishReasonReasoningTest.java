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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CamelSpringAiChatFinishReasonCategory and CamelSpringAiChatReasoning headers.
 */
class SpringAiChatFinishReasonReasoningTest extends CamelTestSupport {

    private final ReplyModel model = new ReplyModel();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        SpringAiChatComponent component = new SpringAiChatComponent();
        component.setChatModel(model);
        context.addComponent("spring-ai-chat", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat").to("spring-ai-chat:test");
                from("direct:safeguard").to("spring-ai-chat:test?safeguardSensitiveWords=secret");
            }
        };
    }

    @Test
    void setsTheCategoryAndTheReasoning() {
        model.finishReason = "end_turn";
        model.reasoning = List.of(ReasoningPart.of("The user greets me."), ReasoningPart.of("I greet back."));

        Exchange out = template.request("direct:chat", e -> e.getIn().setBody("Hello"));

        assertThat(out.getMessage().getBody(String.class)).isEqualTo("Hi there");
        assertThat(out.getMessage().getHeader(SpringAiChatConstants.FINISH_REASON)).isEqualTo("end_turn");
        assertThat(out.getMessage().getHeader(SpringAiChatConstants.FINISH_REASON_CATEGORY)).isEqualTo("COMPLETED");
        assertThat(out.getMessage().getHeader(SpringAiChatConstants.REASONING))
                .isEqualTo("The user greets me.\n\nI greet back.");
        assertThat(out.getMessage().getHeader(SpringAiChatConstants.RESPONSE_METADATA, Map.class))
                .containsEntry("finishReasonCategory", "COMPLETED")
                .containsEntry("reasoning", "The user greets me.\n\nI greet back.");
    }

    @Test
    void truncatedReplyWithoutReasoningRemovesAnEarlierReasoningHeader() {
        model.finishReason = "max_tokens";
        model.reasoning = List.of();

        Exchange out = template.request("direct:chat", e -> {
            e.getIn().setBody("Hello");
            e.getIn().setHeader(SpringAiChatConstants.REASONING, "reasoning of an earlier call");
        });

        assertThat(out.getMessage().getHeader(SpringAiChatConstants.FINISH_REASON_CATEGORY)).isEqualTo("TRUNCATED");
        assertThat(out.getMessage().getHeaders()).doesNotContainKey(SpringAiChatConstants.REASONING);
    }

    @Test
    void usesTheSummaryOfAReasoningPartWithoutText() {
        model.finishReason = "stop";
        model.reasoning = List.of(new ReasoningPart(null, "Summary of the reasoning", null, Map.of()));

        Exchange out = template.request("direct:chat", e -> e.getIn().setBody("Hello"));

        assertThat(out.getMessage().getHeader(SpringAiChatConstants.REASONING)).isEqualTo("Summary of the reasoning");
    }

    @Test
    void refusalOfTheSafeGuardAdvisorIsFiltered() {
        Exchange out = template.request("direct:safeguard", e -> e.getIn().setBody("Tell me the secret"));

        assertThat(out.getMessage().getHeader(SpringAiChatConstants.FINISH_REASON)).isEqualTo("guardrailRefusal");
        assertThat(out.getMessage().getHeader(SpringAiChatConstants.FINISH_REASON_CATEGORY)).isEqualTo("FILTERED");
    }

    /**
     * Answers "Hi there" with the finish reason and the reasoning parts set by the test.
     */
    private static final class ReplyModel implements ChatModel {

        private final ChatOptions options = ChatOptions.builder().model("test-model").build();
        private String finishReason;
        private List<ReasoningPart> reasoning = List.of();

        @Override
        public ChatOptions getOptions() {
            return options;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            AssistantMessage.Builder<?> message = AssistantMessage.builder().content("Hi there");
            reasoning.forEach(message::part);
            ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                    .finishReason(finishReason)
                    .build();
            return new ChatResponse(
                    List.of(new Generation(message.build(), generationMetadata)),
                    ChatResponseMetadata.builder().model("test-model").build());
        }
    }
}

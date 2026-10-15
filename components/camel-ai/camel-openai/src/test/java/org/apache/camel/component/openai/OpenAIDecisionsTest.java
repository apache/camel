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
package org.apache.camel.component.openai;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputImage;
import com.openai.models.decisions.DecisionInputMessage;
import com.openai.models.decisions.DecisionInputPart;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAIDecisionsTest {
    @Test
    void usesManagedClientWithoutStartingConfiguredMcp() throws Exception {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        DecisionCreateParams request = request();
        Decision answer = Decision.builder().model("gpt-6-luna")
                .addAnswer(Decision.Answer.Predicate.builder().name("test").probability(0.91).build())
                .usage(usage()).build();
        when(client.decisions().create(request)).thenReturn(answer);
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            OpenAIComponent component = new OpenAIComponent();
            component.setCamelContext(context);
            OpenAIConfiguration configuration = new OpenAIConfiguration();
            configuration.setMcpServer(Map.of("unused.transportType", "invalid"));
            OpenAIEndpoint endpoint = new OpenAIEndpoint("openai:decisions", component, configuration) {
                @Override
                protected OpenAIClient createClient() {
                    return client;
                }
            };
            endpoint.setOperation(OpenAIOperations.decisions);
            try {
                assertThat(endpoint.decide(request)).isSameAs(answer);
            } finally {
                endpoint.stop();
            }
        }
        verify(client).close();
    }

    @Test
    void refusesWholeRequest() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        DecisionCreateParams request = request();
        when(client.decisions().create(request)).thenReturn(Decision.builder().model("gpt-6-luna")
                .addAnswer(Decision.Answer.Refusal.builder().name("test").build()).usage(usage()).build());
        assertThatThrownBy(() -> OpenAIDecisionsSupport.execute(client, request))
                .isInstanceOf(OpenAIDecisionRefusalException.class)
                .hasMessage("OpenAI declined to answer decision question at index 0 ('test')");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void identifiesRefusedQuestionInBatch(boolean named) {
        var question = DecisionCreateParams.Question.Predicate.builder().instructions("Is it urgent?");
        if (named) {
            question.name("urgent");
        }
        DecisionCreateParams request = request().toBuilder().addQuestion(question.build()).build();
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        Decision response = Decision.builder().model("gpt-6-luna")
                .addAnswer(Decision.Answer.Predicate.builder().name("test").probability(0.9).build())
                .addAnswer(Decision.Answer.Refusal.builder().name(Optional.empty()).build()).usage(usage()).build();
        when(client.decisions().create(request)).thenReturn(response);
        assertThatThrownBy(() -> OpenAIDecisionsSupport.execute(client, request))
                .isInstanceOf(OpenAIDecisionRefusalException.class)
                .hasMessage("OpenAI declined to answer decision question at index 1" + (named ? " ('urgent')" : ""));
    }

    @Test
    void validatesNativeImageFormsAndCount() {
        var image = DecisionInputPart.ofInputImage(DecisionInputImage.builder()
                .imageUrl("data:image/png;base64,AA==").detail(DecisionInputImage.Detail.HIGH).build());
        var valid = request().toBuilder().inputOfDecisionInputMessages(List.of(
                DecisionInputMessage.builder().contentOfParts(List.of(image)).build())).build();
        OpenAIDecisionsSupport.validateRequest(valid);
        for (String data : List.of("https://example.com/image.png", "data:audio/wav;base64,AA==",
                "data:image/png;base64,invalid!")) {
            var invalid = valid.toBuilder().inputOfDecisionInputMessages(List.of(DecisionInputMessage.builder()
                    .contentOfParts(
                            List.of(DecisionInputPart.ofInputImage(DecisionInputImage.builder().imageUrl(data).build())))
                    .build())).build();
            assertThatThrownBy(() -> OpenAIDecisionsSupport.validateRequest(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(data);
        }
        var excessive = valid.toBuilder().inputOfDecisionInputMessages(List.of(DecisionInputMessage.builder()
                .contentOfParts(Collections.nCopies(129, image)).build())).build();
        assertThatThrownBy(() -> OpenAIDecisionsSupport.validateRequest(excessive)).hasMessageContaining("128 images");
    }

    @Test
    void rejectsInvalidResponseBeforeReturningAnyAnswer() {
        var request = request();
        for (Decision response : List.of(
                Decision.builder().model("gpt-6-luna").answers(List.of()).usage(usage()).build(),
                Decision.builder().model("gpt-6-luna").usage(usage()).addAnswer(Decision.Answer.Predicate.builder()
                        .name("wrong").probability(0.9).build()).build(),
                Decision.builder().model("gpt-6-luna").usage(usage()).addAnswer(Decision.Answer.Predicate.builder()
                        .name("test").probability(Double.NaN).build()).build())) {
            OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
            when(client.decisions().create(request)).thenReturn(response);
            assertThatThrownBy(() -> OpenAIDecisionsSupport.execute(client, request))
                    .isInstanceOf(OpenAIDecisionResponseException.class);
        }
    }

    @Test
    void duplicateQuestionNamesAndOversizedBatchesAreRejected() {
        assertThatThrownBy(() -> OpenAIDecisionsSupport.validateRequest(request().toBuilder()
                .addQuestion(request().questions().get(0)).build())).hasMessageContaining("unique");
        assertThatThrownBy(() -> OpenAIDecisionsSupport.validateRequest(request().toBuilder()
                .questions(Collections.nCopies(201, request().questions().get(0))).build())).hasMessageContaining("200");
    }

    private static DecisionCreateParams request() {
        return DecisionCreateParams.builder().model("gpt-6-luna").input("Evidence")
                .addQuestion(DecisionCreateParams.Question.Predicate.builder()
                        .name("test").instructions("Is it relevant?").build())
                .build();
    }

    private static Decision.Usage usage() {
        return Decision.Usage.builder().inputTokens(1).outputTokens(0).totalTokens(1)
                .inputTokensDetails(Decision.Usage.InputTokensDetails.builder().cachedTokens(0).cacheWriteTokens(0).build())
                .outputTokensDetails(Decision.Usage.OutputTokensDetails.builder().reasoningTokens(0).build()).build();
    }
}

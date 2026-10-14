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
package org.apache.camel.semantic.openai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.openai.client.OpenAIClient;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionCreateParams;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.StreamCache;
import org.apache.camel.component.openai.OpenAIComponent;
import org.apache.camel.component.openai.OpenAIConfiguration;
import org.apache.camel.component.openai.OpenAIDecisionRefusalException;
import org.apache.camel.component.openai.OpenAIDecisionServiceException;
import org.apache.camel.component.openai.OpenAIEndpoint;
import org.apache.camel.component.openai.OpenAIOperations;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticEvaluations;
import org.apache.camel.semantic.SemanticInput;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.semantic.internal.SemanticAuditExtensions;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAIDecisionsSemanticAdapterTest {
    private DefaultCamelContext context;
    private OpenAIDecisionsSemanticAdapter adapter;
    private OpenAIClient client;
    private Function<DecisionCreateParams, Decision> respond;

    @BeforeEach
    void setup() throws Exception {
        context = new DefaultCamelContext();
        client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(client.decisions().create(any(DecisionCreateParams.class))).thenAnswer(call -> respond.apply(call.getArgument(0)));
        OpenAIComponent component = new OpenAIComponent();
        component.setCamelContext(context);
        OpenAIEndpoint endpoint = new OpenAIEndpoint("openai:decisions", component, configuration()) {
            @Override
            protected OpenAIClient createClient() {
                return client;
            }
        };
        endpoint.setOperation(OpenAIOperations.decisions);
        context.addEndpoint("openai:decisions", endpoint);
        adapter = new OpenAIDecisionsSemanticAdapter();
        adapter.setCamelContext(context);
        context.getRegistry().bind("expert", adapter);
        context.start();
    }

    @AfterEach
    void stop() throws Exception {
        context.close();
    }

    @Test
    void preservesBooleanProbabilityAndExchangeBody() {
        var evaluation = evaluation("boolean", Map.of());
        Expression expression = expression(evaluation);
        verify(client.decisions(), never()).create(any(DecisionCreateParams.class));
        respond = request -> response(predicate("question", 0.96));
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("original evidence");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        assertThat(exchange.getMessage().getBody()).isEqualTo("original evidence");
        SemanticResult result = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
        assertThat(result.getProbability()).isEqualTo(0.96);
        assertThat(result.getConfidence()).isNull();
        assertThat(result.getMetadata()).containsEntry("provider", "openai");
    }

    @Test
    void imageBodyFormsReachTheProviderAndStreamsRemainReplayable() {
        Expression expression = expression(evaluation("boolean", Map.of()));
        respond = request -> {
            assertThat(request.input().asDecisionInputMessages().get(0).content().asParts().get(0)
                    .asInputImage().imageUrl()).isEqualTo("data:image/png;base64,AQID");
            return response(predicate("question", 0.9));
        };
        byte[] bytes = new byte[] { 1, 2, 3 };
        for (Object body : List.of(bytes, new ByteArrayInputStream(bytes), "AQID", "data:image/png;base64,AQID")) {
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(body);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "image/png");
            assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
            if (exchange.getMessage().getBody() instanceof StreamCache cache) {
                assertThat(exchange.getMessage().getBody(byte[].class)).containsExactly(bytes);
                cache.reset();
                assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
            } else {
                assertThat(exchange.getMessage().getBody()).isSameAs(body);
            }
        }
        verify(client.decisions(), times(5)).create(any(DecisionCreateParams.class));
    }

    @Test
    void selectedTextHeaderDoesNotInheritTheImageBodyContentType() {
        var evaluation = new SemanticEvaluation("boolean", "expert", "${header.evidence}", Map.of("instructions", "Assess"));
        Expression expression = expression(evaluation);
        respond = request -> {
            assertThat(request.input().asString()).isEqualTo("Selected text");
            return response(predicate("question", 0.9));
        };
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(new byte[] { 1 });
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "image/png");
        exchange.getMessage().setHeader("evidence", "Selected text");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
    }

    @Test
    void appliesThresholdAndUncertaintyExactlyOnce() throws Exception {
        respond = request -> response(predicate("question", 0.5));
        assertThat(adapter.evaluate(evaluation("boolean", Map.of()), "input").getValue()).isEqualTo(true);
        assertThat(adapter.evaluate(evaluation("boolean", Map.of("threshold", 0.6)), "input").getValue()).isEqualTo(false);
        assertThatThrownBy(() -> adapter.evaluate(evaluation("boolean", Map.of("uncertainty", 0.1)), "input"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("uncertainty band");
        var result = adapter.evaluate(evaluation("boolean", Map.of("uncertainty", 0.1, "uncertaintyPolicy", "non-match")),
                "input");
        assertThat(result.getValue()).isEqualTo(false);
        assertThat(result.getProbability()).isEqualTo(0.5);
        assertThatThrownBy(() -> adapter.validate(evaluation("boolean", Map.of("threshold", 0.95, "uncertainty", 0.1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsFractionalScoreAndOrderedRichRubric() throws Exception {
        var evaluation = evaluation("score", Map.of("criteria", List.of("low", "medium", "high"),
                "descriptions", Map.of("medium", "Workaround exists", "high", "Completely blocked")));
        respond = request -> response(Decision.Answer.ofScore(Decision.Answer.Score.builder().name("question").score(1.1)
                .confidence(0.82)
                .addProbability(Decision.Answer.Score.Probability.builder().value(0).label("low").probability(0.1).build())
                .addProbability(Decision.Answer.Score.Probability.builder().value(1).label("medium").probability(0.7).build())
                .addProbability(Decision.Answer.Score.Probability.builder().value(2).label("high").probability(0.2).build())
                .build()));
        var result = adapter.evaluate(evaluation, "input");
        assertThat(result.getValue()).isEqualTo(1.1);
        assertThat(result.getProbabilities()).containsExactlyInAnyOrderEntriesOf(Map.of("0", 0.1, "1", 0.7, "2", 0.2));
        assertThat(result.getConfidence()).isEqualTo(0.82);
        ArgumentCaptor<DecisionCreateParams> capture = ArgumentCaptor.forClass(DecisionCreateParams.class);
        verify(client.decisions()).create(capture.capture());
        assertThat(capture.getValue().questions().get(0).asScore().levels()).extracting(l -> l.label())
                .containsExactly("low", "medium", "high");
        assertThat(capture.getValue().questions().get(0).asScore().levels().get(1).description()).contains("Workaround exists");
        var interpretation
                = SemanticAuditExtensions.capture(evaluation, SemanticCapabilities.from(adapter.getClass()).operation("score"));
        assertThat(interpretation).containsEntry("scoreLevels", List.of("low", "medium", "high"))
                .containsEntry("scoreDescriptions", Map.of("medium", "Workaround exists", "high", "Completely blocked"));
    }

    @Test
    void returnsStringChoiceWithNativeDistribution() throws Exception {
        respond = request -> response(Decision.Answer.ofChoice(Decision.Answer.Choice.builder().name("question")
                .choice("billing").confidence(0.8)
                .addProbability(Decision.Answer.Choice.Probability.builder().value("billing").probability(0.9).build())
                .addProbability(Decision.Answer.Choice.Probability.builder().value("technical").probability(0.1).build())
                .build()));
        var result = adapter.evaluate(
                evaluation("choice", Map.of("criteria", Map.of("billing", "Payments", "technical", "Bugs"))), "input");
        assertThat(result.getValue()).isEqualTo("billing");
        assertThat(result.getProbabilities()).containsEntry("billing", 0.9);
        assertThat(result.getConfidence()).isEqualTo(0.8);
    }

    @Test
    void validatesAllInputsAndParametersBeforeCallingProvider() {
        var evaluation = evaluation("boolean", Map.of());
        for (Object input : List.of(new byte[] { 1 }, Map.of("state", "text"),
                Map.of(SemanticInput.PARTS, List.of(Map.of("type", "audio", "data", "content"))),
                SemanticInput.ofParts(List.of(SemanticInput.image("https://example.com/image.png"))))) {
            assertThatThrownBy(() -> adapter.evaluate(evaluation, input)).isInstanceOf(IllegalArgumentException.class);
        }
        var batch = new LinkedHashMap<String, SemanticEvaluation>();
        batch.put("valid", evaluation);
        batch.put("invalid", evaluation("score", Map.of("criteria", List.of("same", "same"))));
        assertThatThrownBy(() -> adapter.evaluateBatch(batch, "input")).isInstanceOf(IllegalArgumentException.class);
        verify(client.decisions(), never()).create(any(DecisionCreateParams.class));
    }

    @Test
    void sharedMediaBatchPreservesPartOrderAndFailsAtomically() throws Exception {
        var batch = new LinkedHashMap<String, SemanticEvaluation>();
        batch.put("first", evaluation("boolean", Map.of()));
        batch.put("second", evaluation("boolean", Map.of()));
        var input = SemanticInput.ofParts(List.of(SemanticInput.text("Inspect"),
                Map.of("type", "image", "data", "data:image/png;base64,AA==", "detail", "high"),
                SemanticInput.text("and explain")));
        respond = request -> response(predicate("first", 0.9), predicate("second", 0.2));
        assertThat(adapter.evaluateBatch(batch, input)).hasSize(2);
        ArgumentCaptor<DecisionCreateParams> capture = ArgumentCaptor.forClass(DecisionCreateParams.class);
        verify(client.decisions()).create(capture.capture());
        var parts = capture.getValue().input().asDecisionInputMessages().get(0).content().asParts();
        assertThat(parts.get(0).asInputText().text()).isEqualTo("Inspect");
        assertThat(parts.get(1).asInputImage().detail().orElseThrow().asString()).isEqualTo("high");
        assertThat(parts.get(2).asInputText().text()).isEqualTo("and explain");
        respond = request -> response(predicate("first", 0.9),
                Decision.Answer.ofRefusal(Decision.Answer.Refusal.builder().name("second").build()));
        assertThatThrownBy(() -> adapter.evaluateBatch(batch, input)).isInstanceOf(OpenAIDecisionRefusalException.class);
    }

    @Test
    void failedEvaluationClearsPreviousResult() {
        Expression expression = expression(evaluation("boolean", Map.of()));
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("text");
        respond = request -> response(predicate("question", 0.9));
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        respond = request -> response(Decision.Answer.ofRefusal(Decision.Answer.Refusal.builder().name("question").build()));
        assertThatThrownBy(() -> expression.evaluate(exchange, Boolean.class))
                .isInstanceOf(OpenAIDecisionRefusalException.class);
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @Test
    void lazyClientStartupFailureUsesTheDedicatedServiceException() {
        context.addComponent("broken", new OpenAIComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                var endpoint = new OpenAIEndpoint(uri, this, configuration()) {
                    @Override
                    protected OpenAIClient createClient() throws Exception {
                        throw new IOException("Client configuration unavailable");
                    }
                };
                endpoint.setOperation(OpenAIOperations.decisions);
                return endpoint;
            }
        });
        adapter.setEndpointUri("broken:decisions");
        assertThatThrownBy(() -> adapter.evaluate(evaluation("boolean", Map.of()), "text"))
                .isInstanceOf(OpenAIDecisionServiceException.class).hasRootCauseInstanceOf(IOException.class);
        verify(client.decisions(), never()).create(any(DecisionCreateParams.class));
    }

    @Test
    void stoppedContextCannotRestartTheProviderClient() throws Exception {
        context.stop();
        assertThatThrownBy(() -> adapter.evaluate(evaluation("boolean", Map.of()), "text"))
                .isInstanceOf(OpenAIDecisionServiceException.class);
        verify(client.decisions(), never()).create(any(DecisionCreateParams.class));
    }

    @Test
    void directLanguageEvaluationRejectsMalformedImageBeforeInference() {
        var language = (SemanticLanguage) context.resolveLanguage("semantic");
        assertThatThrownBy(() -> language.evaluate(evaluation("boolean", Map.of()), "data:image/png;base64,invalid!"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("base64");
        verify(client.decisions(), never()).create(any(DecisionCreateParams.class));
    }

    @Test
    void requiresAnExplicitModelBeforeInference() {
        context.getEndpoint("openai:decisions", OpenAIEndpoint.class).getConfiguration().setModel(null);
        assertThatThrownBy(() -> adapter.evaluate(evaluation("boolean", Map.of()), "text"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Configure a Decisions model");
        verify(client.decisions(), never()).create(any(DecisionCreateParams.class));
    }

    @Test
    void binaryBodySelectorsRequireTheExplicitBodyImagePath() {
        var evaluation = new SemanticEvaluation("boolean", "expert", "${bodyAs(byte[])}", Map.of("instructions", "Assess"));
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(new byte[] { 1, 2, 3 });
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "image/png");
        assertThatThrownBy(() -> expression(evaluation).evaluate(exchange, Boolean.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported state type");
        verify(client.decisions(), never()).create(any(DecisionCreateParams.class));
    }

    private static OpenAIConfiguration configuration() {
        var configuration = new OpenAIConfiguration();
        configuration.setModel("gpt-6-luna");
        return configuration;
    }

    private Expression expression(SemanticEvaluation evaluation) {
        SemanticEvaluations.get(context).replace("test", Map.of("q", evaluation));
        return context.resolveLanguage("semantic").createExpression("ref:q");
    }

    private static SemanticEvaluation evaluation(String operation, Map<String, Object> parameters) {
        var values = new LinkedHashMap<String, Object>(parameters);
        values.put("instructions", "Assess the evidence");
        return new SemanticEvaluation(operation, "expert", null, values);
    }

    private static Decision.Answer predicate(String name, double probability) {
        return Decision.Answer.ofPredicate(Decision.Answer.Predicate.builder().name(name).probability(probability).build());
    }

    private static Decision response(Decision.Answer... answers) {
        return Decision.builder().model("gpt-6-luna").answers(List.of(answers))
                .usage(Decision.Usage.builder().inputTokens(1).outputTokens(0).totalTokens(1)
                        .inputTokensDetails(
                                Decision.Usage.InputTokensDetails.builder().cachedTokens(0).cacheWriteTokens(0).build())
                        .outputTokensDetails(Decision.Usage.OutputTokensDetails.builder().reasoningTokens(0).build()).build())
                .build();
    }
}

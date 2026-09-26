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
package org.apache.camel.component.typesafeai;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.Expression;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeSafeAiSemanticAdapterTest extends TypeSafeAiTestSupport {
    @Test
    void directEvaluationInitializesTransportWithoutPriorValidation() throws Exception {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", 0.9)));
        TypeSafeAiSemanticAdapter adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        SemanticQuestion question = new SemanticQuestion(
                SemanticQuestion.Type.BOOLEAN, "Classify", null,
                Map.of(), List.of(), 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL);
        assertThat(adapter.evaluate(question, "original").decision(question)).isEqualTo(true);
        assertThat(requests).hasSize(1);
        assertThat(authorization).containsExactly("Bearer test-key");
    }

    @Test
    void rejectsCapabilityLimitsWithoutInitializingTransport() {
        TypeSafeAiSemanticAdapter adapter = new TypeSafeAiSemanticAdapter();
        Map<String, String> criteria = IntStream.range(0, 256).boxed()
                .collect(Collectors.toMap(Object::toString, i -> "Criterion " + i));
        SemanticQuestion choice = new SemanticQuestion(
                SemanticQuestion.Type.CHOICE, "Classify", null,
                criteria, List.of(), 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL);
        SemanticQuestion score = new SemanticQuestion(
                SemanticQuestion.Type.SCORE, "Score", null,
                Map.of(), IntStream.range(0, 11).mapToObj(i -> "Level " + i).toList(), 0.5, 0,
                SemanticQuestion.UncertaintyPolicy.FAIL);
        assertThatThrownBy(() -> adapter.validate(choice)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255 choice criteria");
        assertThatThrownBy(() -> adapter.evaluate(score, "original")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 score levels");
        assertThat(requests).isEmpty();
    }

    private Expression expression(SemanticQuestion.Type type) {
        SemanticQuestion question = new SemanticQuestion(
                type, "Classify", null,
                type == SemanticQuestion.Type.CHOICE ? Map.of("billing", "Payments", "technical", "Bugs") : Map.of(),
                type == SemanticQuestion.Type.SCORE ? List.of("low", "high") : List.of(),
                0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL);
        SemanticQuestions.get(context).replace("test", Map.of("q", question));
        return context.resolveLanguage("semantic").createExpression("ref:q");
    }

    @Test
    void autoDiscoversAndUsesConfiguredComponentWithoutProviderRoute() {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", 0.9)));
        Expression expression = expression(SemanticQuestion.Type.BOOLEAN);
        assertThat(requests).isEmpty();
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("original");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        assertThat(exchange.getMessage().getBody()).isEqualTo("original");
        assertThat(authorization).containsExactly("Bearer test-key");
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().get("state")).isEqualTo("original");
        SemanticResult answer = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
        assertThat(answer.getMetadata()).containsEntry("provider", "typesafe-ai").containsEntry("model", "jev-1.13.0");
        assertThat(answer.getProbability()).isEqualTo(0.9);
        assertThat(answer.getConfidence()).isNull();
    }

    @Test
    void mapsChoiceAndScoreSeparatelyFromConfidence() {
        respond = request -> result(Map.of("question", Map.of("type", "choice", "choice", "technical",
                "confidence", 0.6, "probabilities", Map.of("billing", 0.1, "technical", 0.9))));
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("outage");
        assertThat(expression(SemanticQuestion.Type.CHOICE).evaluate(exchange, String.class)).isEqualTo("technical");
        SemanticResult choice = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
        assertThat(choice.getConfidence()).isEqualTo(0.6);
        assertThat(choice.getProbabilities()).containsEntry("technical", 0.9);
        respond = request -> result(Map.of("question", Map.of("type", "score", "score", 0.7,
                "confidence", 0.4, "probabilities", Map.of("0", 0.3, "1", 0.7), "legend", Map.of("0", "low", "1", "high"))));
        assertThat(expression(SemanticQuestion.Type.SCORE).evaluate(exchange, Double.class)).isEqualTo(0.7);
    }

    @Test
    void providerErrorsClearPreviousResult() {
        Expression expression = expression(SemanticQuestion.Type.BOOLEAN);
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("private-input");
        exchange.setProperty(SemanticLanguage.RESULT, "old");
        status = 503;
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasStackTraceContaining("503")
                .hasMessageNotContaining("private-input");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @Test
    void componentTimeoutBoundsSemanticEvaluation() {
        context.getComponent("typesafe-ai", TypeSafeAiComponent.class).getConfiguration().setRequestTimeout(100);
        holdHeaders = true;
        Expression expression = expression(SemanticQuestion.Type.BOOLEAN);
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("private-input");
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class))
                .hasStackTraceContaining("TimeoutException").hasMessageNotContaining("private-input");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

}

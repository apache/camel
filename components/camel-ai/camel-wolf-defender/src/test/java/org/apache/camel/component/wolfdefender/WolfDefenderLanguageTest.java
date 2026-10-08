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
package org.apache.camel.component.wolfdefender;

import java.util.Map;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticEvaluations;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.wolfdefender.WolfDefenderSemanticAdapterTest.evaluation;
import static org.apache.camel.component.wolfdefender.WolfDefenderSemanticAdapterTest.expert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WolfDefenderLanguageTest {
    @Test
    void explicitAndSoleRegistryExpertPreserveMessageAndProbability() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        when(inference.evaluate(anyString(), anyInt(), any())).thenReturn(0.03);
        var expert = expert(inference);
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", expert);
            context.addService(expert, true, true);
            context.start();
            SemanticEvaluations.get(context).replace("test",
                    Map.of("injection", evaluation("security"), "automatic", evaluation()));
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("original text");
            exchange.getMessage().setHeader("original", "header");
            var language = context.resolveLanguage("semantic");
            assertThat(language.createExpression("ref:injection").evaluate(exchange, Boolean.class)).isFalse();
            assertThat(language.createExpression("ref:automatic").evaluate(exchange, Boolean.class)).isFalse();
            assertThat(exchange.getMessage().getBody()).isEqualTo("original text");
            assertThat(exchange.getMessage().getHeader("original")).isEqualTo("header");
            assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNotNull();
        }
        verify(inference).close();
    }

    @Test
    void simpleUsesTheExpertVerdictWithoutApplyingAnotherThreshold() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        when(inference.evaluate(anyString(), anyInt(), any())).thenReturn(0.6);
        var expert = expert(inference);
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", expert);
            context.addService(expert, true, true);
            SemanticEvaluations.get(context).replace("test", Map.of("injection",
                    new SemanticEvaluation("injection", "security", "${body}", Map.of("threshold", 0.8))));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("original text");
            var simple = context.resolveLanguage("simple");
            var expression = simple.createExpression("${semantic('injection')}");
            expression.init(context);
            assertThat(expression.evaluate(exchange, Boolean.class)).isFalse();
            var result = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
            assertThat(result.getValue()).isEqualTo(false);
            assertThat(result.getProbability()).isEqualTo(0.6);
            var predicate = simple.createPredicate("${semantic('injection')}");
            predicate.init(context);
            assertThat(predicate.matches(exchange)).isFalse();
            assertThat(exchange.getMessage().getBody()).isEqualTo("original text");
        }
    }

    @Test
    void tokenPreflightRejectsTheWholeBatchBeforeAnyExpertInfers() throws Exception {
        var firstInference = mock(WolfDefenderInference.class);
        var secondInference = mock(WolfDefenderInference.class);
        doThrow(new WolfDefenderException("Input exceeds maxTokens=4 including special tokens"))
                .when(secondInference).validateInput(anyString(), anyInt(), any());
        var first = expert(firstInference);
        var second = expert(secondInference);
        second.setMaxTokens(4);
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("first", first);
            context.getRegistry().bind("second", second);
            context.addService(first, true, true);
            context.addService(second, true, true);
            SemanticEvaluations.get(context).replace("test",
                    Map.of("first", evaluation("first"), "second", evaluation("second")));
            context.start();
            var expression = context.resolveLanguage("semantic").createExpression("refs:first,second");
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("hello hello hello attack");
            assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).hasMessageContaining("maxTokens=4");
            assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
        }
        verify(firstInference, never()).evaluate(anyString(), anyInt(), any());
        verify(secondInference, never()).evaluate(anyString(), anyInt(), any());
    }

    @Test
    void batchPreservesKeysInstanceIdentityAndAllOrErrorPublication() throws Exception {
        var firstInference = mock(WolfDefenderInference.class);
        var secondInference = mock(WolfDefenderInference.class);
        when(firstInference.evaluate(anyString(), anyInt(), any())).thenReturn(0.97);
        when(secondInference.evaluate(anyString(), anyInt(), any())).thenReturn(0.03)
                .thenThrow(new IllegalStateException("private input"));
        var first = expert(firstInference);
        var second = expert(secondInference);
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("first", first);
            context.getRegistry().bind("second", second);
            context.addService(first, true, true);
            context.addService(second, true, true);
            context.start();
            SemanticEvaluations.get(context).replace("test",
                    Map.of("injection", evaluation("first"), "other", evaluation("second")));
            var expression = context.resolveLanguage("semantic").createExpression("refs:injection,other");
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThat(expression.evaluate(exchange, Map.class)).containsExactly(
                    Map.entry("injection", true), Map.entry("other", false));
            var failed = new DefaultExchange(context);
            failed.getMessage().setBody("private input");
            assertThatThrownBy(() -> expression.evaluate(failed, Map.class)).hasMessageContaining("Inference failed");
            assertThat(failed.getProperty(SemanticLanguage.RESULT)).isNull();
            assertThat(failed.getProperty(SemanticLanguage.RESULTS)).isNull();
            assertThat(failed.getMessage().getBody()).isEqualTo("private input");
        }
        verify(firstInference).close();
        verify(secondInference).close();
    }

    @Test
    void discoveryValidatesBeforeModelLoadingAndReportsEvaluationAndExpert() throws Exception {
        try (var context = new DefaultCamelContext()) {
            SemanticEvaluations.get(context).replace("test", Map.of("injection", evaluation()));
            // JDK discovery selects the packaged expert, then fails on missing local artifacts.
            assertThatThrownBy(() -> context.resolveLanguage("semantic").createExpression("ref:injection"))
                    .hasStackTraceContaining("Configure modelDirectory");
        }
        try (var context = new DefaultCamelContext()) {
            var expert = new WolfDefenderSemanticAdapter();
            context.getRegistry().bind("security", expert);
            SemanticEvaluations.get(context).replace("test", Map.of("injection", new SemanticEvaluation(
                    "injection", "security", null, Map.of("instructions", "unsupported"))));
            assertThatThrownBy(() -> context.resolveLanguage("semantic").createExpression("ref:injection"))
                    .hasMessageContaining("injection").hasMessageContaining("security")
                    .hasMessageContaining("Unknown parameter 'instructions'");
            assertThat(expert.isStarted()).isFalse();
        }
    }
}

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
package org.apache.camel.semantic;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticLanguageTest {
    @TempDir
    Path directory;
    DefaultCamelContext context;
    SemanticLanguage language;

    @BeforeEach
    void setup() throws Exception {
        CountingAdapter.constructed.set(0);
        CountingAdapter.started.set(0);
        CountingAdapter.stopped.set(0);
        context = new DefaultCamelContext();
        language = new SemanticLanguage();
        language.setCamelContext(context);
        language.setAdapter(CountingAdapter.class.getName());
        context.getRegistry().bind("semantic", language);
        context.start();
        questions(question(SemanticQuestion.Type.BOOLEAN, null, 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL));
    }

    @AfterEach
    void cleanup() throws Exception {
        context.stop();
    }

    static SemanticQuestion question(
            SemanticQuestion.Type type, String state, double threshold, double uncertainty,
            SemanticQuestion.UncertaintyPolicy policy) {
        return new SemanticQuestion(
                type, "Classify this message", state,
                type == SemanticQuestion.Type.CHOICE ? Map.of("billing", "Payment", "technical", "Problem") : Map.of(),
                type == SemanticQuestion.Type.SCORE ? List.of("low", "medium", "high") : List.of(), threshold, uncertainty,
                policy);
    }

    private void questions(SemanticQuestion question) {
        SemanticQuestions.get(context).replace("test", Map.of("q", question));
    }

    @Test
    void createdAdapterIsRegisteredAndManagedExactlyOnce() throws Exception {
        Expression first = language.createExpression("ref:q");
        language.createPredicate("ref:q");
        assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isInstanceOf(CountingAdapter.class);
        assertThat(CountingAdapter.constructed).hasValue(1);
        assertThat(CountingAdapter.started).hasValue(1);
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("original");
        assertThat(first.evaluate(exchange, Boolean.class)).isTrue();
        assertThat(exchange.getMessage().getBody()).isEqualTo("original");
        context.stop();
        assertThat(CountingAdapter.stopped).hasValue(1);
        assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
    }

    @Test
    void explicitBeanRetainsIdentityAndLifecycleOwner() throws Exception {
        CountingAdapter bean = new CountingAdapter();
        context.getRegistry().bind("custom", bean);
        language.setAdapter("#bean:custom");
        language.createExpression("ref:q");
        assertThat(context.getRegistry().lookupByName("custom")).isSameAs(bean);
        assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
        assertThat(CountingAdapter.started).hasValue(0);
        context.stop();
        assertThat(CountingAdapter.stopped).hasValue(0);
    }

    @Test
    void explicitClassSpellingUsesSamePath() {
        language.setAdapter("#class:" + CountingAdapter.class.getName());
        language.createExpression("ref:q");
        assertThat(CountingAdapter.constructed).hasValue(1);
    }

    @Test
    void discoversOneAdapterAndRejectsMissingOrAmbiguousWithoutConstruction() throws Exception {
        language.setAdapter(null);
        discovery("");
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("exactly one");
        discovery(CountingAdapter.class.getName() + "\n" + LabelAdapter.class.getName());
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("explicitly");
        assertThat(CountingAdapter.constructed).hasValue(0);
        discovery(CountingAdapter.class.getName() + "\n# comment\n" + CountingAdapter.class.getName());
        language.createExpression("ref:q");
        assertThat(CountingAdapter.constructed).hasValue(1);
    }

    private void discovery(String declarations) throws Exception {
        Path descriptor = directory.resolve("adapters");
        Files.writeString(descriptor, declarations);
        URL url = descriptor.toUri().toURL();
        context.setClassResolver(new DefaultClassResolver() {
            @Override
            public Enumeration<URL> loadAllResourcesAsURL(String name) {
                return SemanticLanguage.ADAPTER_RESOURCE.equals(name)
                        ? Collections.enumeration(List.of(url)) : super.loadAllResourcesAsURL(name);
            }
        });
    }

    @Test
    void classAndBeanTypeErrorsAndRegistryCollisionDoNotFallBack() {
        language.setAdapter(String.class.getName());
        assertThatThrownBy(() -> language.createExpression("ref:q")).isInstanceOf(RuntimeCamelException.class)
                .hasCauseInstanceOf(ClassCastException.class);
        language.setAdapter("#bean:missing");
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("missing");
        context.getRegistry().bind("wrong", "not an adapter");
        language.setAdapter("#bean:wrong");
        assertThatThrownBy(() -> language.createExpression("ref:q"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not implement SemanticAdapter");
        language.setAdapter(CountingAdapter.class.getName());
        context.getRegistry().bind(SemanticLanguage.ADAPTER_NAME, "occupied");
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("already bound");
        assertThat(CountingAdapter.constructed).hasValue(0);
    }

    @Test
    void initializationFailureUnbindsAndShutsDownOwnedAdapter() {
        language.setAdapter(FailingAdapter.class.getName());
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("start failure");
        assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
        assertThat(CountingAdapter.stopped).hasValue(1);
    }

    @Test
    void questionStateOverridesDefaultAndMissingStateNeverFallsBack() {
        language.setDefaultState("${header.fallback}");
        questions(
                question(SemanticQuestion.Type.BOOLEAN, "${header.selected}", 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL));
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("body");
        exchange.getMessage().setHeader("fallback", "fallback");
        exchange.getMessage().setHeader("selected", "${body}");
        expression.evaluate(exchange, Boolean.class);
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType(SemanticLanguage.ADAPTER_NAME, CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("${body}");
        exchange.getMessage().removeHeader("selected");
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasMessageContaining("Missing selected state");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        questions(question(SemanticQuestion.Type.BOOLEAN, null, 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL));
        expression.evaluate(exchange, Boolean.class);
        assertThat(adapter.state).isEqualTo("fallback");
    }

    @Test
    void invalidSelectorsAndUnknownReferencesFailBeforeEvaluation() {
        assertThatThrownBy(() -> language.createExpression("q")).hasMessageContaining("ref:name");
        assertThatThrownBy(() -> language.createExpression("ref:unknown")).hasMessageContaining("Unknown");
        questions(
                question(SemanticQuestion.Type.BOOLEAN, "${invalidFunction}", 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL));
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("Unknown function: invalidFunction");
    }

    @Test
    void shorthandBeanReferenceRetainsExistingInstance() {
        CountingAdapter bean = new CountingAdapter();
        context.getRegistry().bind("custom", bean);
        language.setAdapter("#custom");
        language.createExpression("ref:q");
        assertThat(CountingAdapter.constructed).hasValue(1);
        assertThat(CountingAdapter.started).hasValue(0);
        assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
    }

    @Test
    void byteStateRequiresExplicitConversionAndPreservesBody() {
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        byte[] body = "invoice".getBytes(StandardCharsets.UTF_8);
        exchange.getMessage().setBody(body);
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class))
                .hasCauseInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported state type");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        questions(question(SemanticQuestion.Type.BOOLEAN, "${bodyAs(String)}", 0.5, 0,
                SemanticQuestion.UncertaintyPolicy.FAIL));
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType(SemanticLanguage.ADAPTER_NAME, CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("invoice");
        assertThat(exchange.getMessage().getBody()).isSameAs(body);
    }

    @Test
    void probabilityPolicyAndFailuresNeverBecomeImplicitNonMatches() {
        Predicate predicate = language.createPredicate("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType(SemanticLanguage.ADAPTER_NAME, CountingAdapter.class);
        adapter.answer = new SemanticResult(null, 0.5, null, null, null);
        assertThat(predicate.matches(exchange)).isTrue();
        questions(question(SemanticQuestion.Type.BOOLEAN, null, 0.5, 0.1, SemanticQuestion.UncertaintyPolicy.FAIL));
        assertThatThrownBy(() -> predicate.matches(exchange)).hasMessageContaining("uncertain");
        questions(question(SemanticQuestion.Type.BOOLEAN, null, 0.5, 0.1, SemanticQuestion.UncertaintyPolicy.NON_MATCH));
        assertThat(predicate.matches(exchange)).isFalse();
        adapter.failure = new IllegalStateException("provider failed");
        assertThatThrownBy(() -> predicate.matches(exchange)).hasMessageContaining("provider failed");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @Test
    void reloadReplacesDefinitionsAndRemovedReferencesFail() {
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        questions(
                question(SemanticQuestion.Type.BOOLEAN, "${header.updated}", 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL));
        exchange.getMessage().setHeader("updated", "new state");
        expression.evaluate(exchange, Boolean.class);
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType(SemanticLanguage.ADAPTER_NAME, CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("new state");
        assertThatThrownBy(
                () -> SemanticQuestions.get(context).replace("other", Map.of("q", SemanticQuestions.get(context).get("q"))))
                .hasMessageContaining("Duplicate");
        SemanticQuestions.get(context).replace("test", Map.of());
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasMessageContaining("Unknown");
    }

    @Test
    void predicateReloadFailureClearsPreviousResult() {
        Predicate predicate = language.createPredicate("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        assertThat(predicate.matches(exchange)).isTrue();
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNotNull();
        questions(question(SemanticQuestion.Type.CHOICE, null, 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL));
        assertThatThrownBy(() -> predicate.matches(exchange)).hasMessageContaining("boolean");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        exchange.setProperty(SemanticLanguage.RESULT, "old");
        SemanticQuestions.get(context).replace("test", Map.of());
        assertThatThrownBy(() -> predicate.matches(exchange)).hasMessageContaining("Unknown");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @Test
    void labelOnlyProviderWorksWithoutInventedProbabilitiesAndRejectsUnsupportedKinds() {
        language.setAdapter(LabelAdapter.class.getName());
        questions(question(SemanticQuestion.Type.CHOICE, null, 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL));
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("invoice");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
        SemanticResult result = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
        assertThat(result.getProbability()).isNull();
        assertThat(result.getProbabilities()).isEmpty();
        assertThat(result.getConfidence()).isNull();
        assertThatThrownBy(() -> language.createPredicate("ref:q")).hasMessageContaining("boolean");
        questions(question(SemanticQuestion.Type.SCORE, null, 0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL));
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasMessageContaining("only choice");
    }

    public static class CountingAdapter extends ServiceSupport implements SemanticAdapter {
        static final AtomicInteger constructed = new AtomicInteger();
        static final AtomicInteger started = new AtomicInteger();
        static final AtomicInteger stopped = new AtomicInteger();
        volatile Object state;
        volatile SemanticResult answer = new SemanticResult(null, 0.9, null, null, Map.of("provider", "test"));
        volatile RuntimeException failure;

        public CountingAdapter() {
            constructed.incrementAndGet();
        }

        @Override
        public void validate(SemanticQuestion question) {
        }

        @Override
        public SemanticResult evaluate(SemanticQuestion question, Object state) {
            this.state = state;
            if (failure != null) {
                throw failure;
            }
            return answer;
        }

        @Override
        protected void doStart() {
            started.incrementAndGet();
        }

        @Override
        protected void doStop() {
            stopped.incrementAndGet();
        }
    }

    public static class FailingAdapter extends CountingAdapter {
        @Override
        protected void doStart() {
            throw new IllegalStateException("start failure");
        }
    }

    public static class LabelAdapter implements SemanticAdapter {
        @Override
        public void validate(SemanticQuestion question) {
            if (question.getType() != SemanticQuestion.Type.CHOICE) {
                throw new IllegalArgumentException("Supports only choice");
            }
        }

        @Override
        public SemanticResult evaluate(SemanticQuestion question, Object state) {
            return new SemanticResult("billing", null, null, null, Map.of("provider", "fixed-classifier"));
        }
    }
}

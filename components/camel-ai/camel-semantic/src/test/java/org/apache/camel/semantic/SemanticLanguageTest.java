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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.impl.engine.DefaultInjector;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        context.init();
        evaluations(evaluation("boolean", null, 0.5, 0, "fail"));
    }

    @AfterEach
    void cleanup() throws Exception {
        context.stop();
    }

    static SemanticEvaluation evaluation(
            String type, String state, double threshold, double uncertainty,
            String policy) {
        return new SemanticEvaluation(type, null, state, switch (type) {
            case "boolean" -> Map.of("instructions", "Classify this message", "threshold", threshold,
                    "uncertainty", uncertainty, "uncertaintyPolicy", policy);
            case "choice" -> Map.of("instructions", "Classify this message", "criteria",
                    Map.of("billing", "Payment", "technical", "Problem"));
            case "score" -> Map.of("instructions", "Classify this message", "criteria", List.of("low", "medium", "high"));
            default -> throw new IllegalArgumentException("Unknown fixture operation");
        });
    }

    private void evaluations(SemanticEvaluation evaluation) {
        SemanticEvaluations.get(context).replace("test", Map.of("q", evaluation));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void warmEvaluationDoesNotAcquireTheLanguageMonitor(boolean registryOwned) throws Exception {
        if (registryOwned) {
            context.getRegistry().bind("registered", new CountingAdapter());
            language.setAdapter("registered");
        }
        context.start();
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        var executor = Executors.newSingleThreadExecutor();
        try {
            synchronized (language) {
                assertThat(executor.submit(() -> expression.evaluate(exchange, Boolean.class)).get(5, TimeUnit.SECONDS))
                        .isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
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
        language.setAdapter("custom");
        language.createExpression("ref:q");
        assertThat(context.getRegistry().lookupByName("custom")).isSameAs(bean);
        assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
        assertThat(CountingAdapter.started).hasValue(0);
        context.stop();
        assertThat(CountingAdapter.stopped).hasValue(0);
    }

    @Test
    void explicitClassIsConstructedOnce() {
        language.setAdapter(CountingAdapter.class.getName());
        language.createExpression("ref:q");
        assertThat(CountingAdapter.constructed).hasValue(1);
    }

    @Test
    void discoversOneAdapterAndRejectsMissingOrAmbiguousWithoutConstruction() throws Exception {
        language.setAdapter(null);
        discovery();
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("exactly one");
        discovery("class=" + CountingAdapter.class.getName(), "class=" + LabelAdapter.class.getName());
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("explicitly");
        assertThat(CountingAdapter.constructed).hasValue(0);
        discovery("class=" + CountingAdapter.class.getName(), "# same provider\nclass: " + CountingAdapter.class.getName());
        language.createExpression("ref:q");
        assertThat(CountingAdapter.constructed).hasValue(1);
        assertThat(CountingAdapter.started).hasValue(1);
        context.stop();
        assertThat(CountingAdapter.stopped).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "# no implementation", "class=", "class=   " })
    void invalidDiscoveryDescriptorFailsBeforeConstruction(String declaration) throws Exception {
        language.setAdapter(null);
        discovery(declaration);
        assertThatThrownBy(() -> language.createExpression("ref:q"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a class property").hasMessageContaining("adapter-0");
        assertThat(CountingAdapter.constructed).hasValue(0);
    }

    @Test
    void discoveredClassIsTypeCheckedBeforeConstruction() throws Exception {
        NotAnAdapter.constructed.set(0);
        language.setAdapter(null);
        discovery("class=" + NotAnAdapter.class.getName());
        assertThatThrownBy(() -> language.createExpression("ref:q"))
                .isInstanceOf(RuntimeCamelException.class).hasCauseInstanceOf(ClassCastException.class);
        assertThat(NotAnAdapter.constructed).hasValue(0);
    }

    @Test
    void factoryFinderCannotSelectADifferentAdvertisedClass() throws Exception {
        language.setAdapter(null);
        discovery("class=" + CountingAdapter.class.getName());
        context.getCamelContextExtension()
                .setDefaultFactoryFinder(new DefaultFactoryFinder(context.getClassResolver(), FactoryFinder.DEFAULT_PATH) {
                    @Override
                    public Optional<Class<?>> findClass(String key) {
                        return Optional.of(FailingAdapter.class);
                    }
                });
        assertThatThrownBy(() -> language.createExpression("ref:q"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match advertised adapter");
        assertThat(CountingAdapter.constructed).hasValue(0);
    }

    @Test
    void discoveryUsesCamelInjectorForConstructorArguments() throws Exception {
        language.setAdapter(null);
        discovery("class=" + InjectedAdapter.class.getName());
        context.setInjector(new DefaultInjector(context) {
            @Override
            public <T> T newInstance(Class<T> type, boolean postProcessBean) {
                return type == InjectedAdapter.class
                        ? type.cast(new InjectedAdapter("injected")) : super.newInstance(type, postProcessBean);
            }
        });
        language.createExpression("ref:q");
        InjectedAdapter instance
                = context.getRegistry().lookupByNameAndType(SemanticLanguage.ADAPTER_NAME, InjectedAdapter.class);
        assertThat(instance.dependency).isEqualTo("injected");
        assertThat(CountingAdapter.constructed).hasValue(1);
        assertThat(CountingAdapter.started).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void explicitSelectionBypassesAmbiguousDiscovery(boolean bean) throws Exception {
        discovery("class=" + CountingAdapter.class.getName(), "class=" + LabelAdapter.class.getName());
        if (bean) {
            context.getRegistry().bind("custom", new CountingAdapter());
            language.setAdapter("custom");
        }
        language.createExpression("ref:q");
        assertThat(CountingAdapter.constructed).hasValue(1);
        assertThat(CountingAdapter.started).hasValue(bean ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void registeredSubclassesSupersedeDiscoveryButDistinctInstancesRemainAmbiguous(boolean distinct) throws Exception {
        language.setAdapter(null);
        discovery("class=" + CountingAdapter.class.getName());
        CountingAdapter bean = new CountingAdapter() {
        };
        context.getRegistry().bind("custom", bean);
        context.getRegistry().bind("alias", distinct ? new CountingAdapter() {
        } : bean);
        if (distinct) {
            assertThatThrownBy(() -> language.createExpression("ref:q"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("available experts: [alias, custom]")
                    .hasMessageNotContaining("java.lang.IllegalArgumentException:");
            assertThat(CountingAdapter.constructed).hasValue(2);
        } else {
            language.createExpression("ref:q");
            assertThat(CountingAdapter.constructed).hasValue(1);
        }
        assertThat(CountingAdapter.started).hasValue(0);
        assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
    }

    private void discovery(String... declarations) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (int i = 0; i < declarations.length; i++) {
            Path descriptor = directory.resolve("adapter-" + i);
            Files.writeString(descriptor, declarations[i]);
            urls.add(descriptor.toUri().toURL());
        }
        var resolver = new DefaultClassResolver() {
            @Override
            public Enumeration<URL> loadAllResourcesAsURL(String name) {
                return SemanticLanguage.ADAPTER_RESOURCE.equals(name)
                        ? Collections.enumeration(urls) : super.loadAllResourcesAsURL(name);
            }

            @Override
            public InputStream loadResourceAsStream(String name) {
                if (SemanticLanguage.ADAPTER_RESOURCE.equals(name)) {
                    try {
                        return urls.isEmpty() ? null : urls.get(0).openStream();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return super.loadResourceAsStream(name);
            }
        };
        context.setClassResolver(resolver);
        context.getCamelContextExtension()
                .setDefaultFactoryFinder(new DefaultFactoryFinder(resolver, FactoryFinder.DEFAULT_PATH));
    }

    @Test
    void classAndBeanTypeErrorsAndRegistryCollisionDoNotFallBack() {
        language.setAdapter(String.class.getName());
        assertThatThrownBy(() -> language.createExpression("ref:q")).isInstanceOf(RuntimeCamelException.class)
                .hasCauseInstanceOf(ClassCastException.class);
        language.setAdapter("missing");
        assertThatThrownBy(() -> language.createExpression("ref:q"))
                .hasMessageContaining("No semantic adapter bean or class found: missing");
        context.getRegistry().bind("wrong", "not an adapter");
        language.setAdapter("wrong");
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
    void evaluationStateOverridesDefaultAndMissingStateNeverFallsBack() {
        language.setDefaultState("${header.fallback}");
        evaluations(
                evaluation("boolean", "${header.selected}", 0.5, 0, "fail"));
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
        evaluations(evaluation("boolean", null, 0.5, 0, "fail"));
        expression.evaluate(exchange, Boolean.class);
        assertThat(adapter.state).isEqualTo("fallback");
    }

    @Test
    void invalidSelectorsAndUnknownReferencesFailBeforeEvaluation() {
        assertThatThrownBy(() -> language.createExpression("q")).hasMessageContaining("ref:name");
        assertThatThrownBy(() -> language.createExpression("ref:unknown")).hasMessageContaining("Unknown");
        evaluations(
                evaluation("boolean", "${invalidFunction}", 0.5, 0, "fail"));
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("Unknown function: invalidFunction");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void placeholderResolvingToEmptyStateRetainsSingleEvaluationBehavior(boolean evaluationState) {
        Properties properties = new Properties();
        properties.setProperty("selected", "");
        context.getPropertiesComponent().setInitialProperties(properties);
        if (evaluationState) {
            evaluations(evaluation("boolean", "{{selected}}", 0.5, 0,
                    "fail"));
        } else {
            language.setDefaultState("{{selected}}");
        }
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("original");
        assertThat(language.createExpression("ref:q").evaluate(exchange, Boolean.class)).isTrue();
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType(SemanticLanguage.ADAPTER_NAME, CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("");
        assertThat(exchange.getMessage().getBody()).isEqualTo("original");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void literalBlankSelectorsStillFailBeforeEvaluation(String selector) {
        language.setDefaultState(selector);
        assertThatThrownBy(() -> language.createExpression("ref:q"))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not be blank");
    }

    @Test
    void registryNameTakesPrecedenceOverClassName() {
        CountingAdapter bean = new CountingAdapter();
        context.getRegistry().bind(CountingAdapter.class.getName(), bean);
        language.setAdapter(CountingAdapter.class.getName());
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
        evaluations(evaluation("boolean", "${bodyAs(String)}", 0.5, 0,
                "fail"));
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
        evaluations(evaluation("boolean", null, 0.5, 0.1, "fail"));
        assertThatThrownBy(() -> predicate.matches(exchange)).hasMessageContaining("uncertain");
        evaluations(evaluation("boolean", null, 0.5, 0.1, "non-match"));
        assertThat(predicate.matches(exchange)).isFalse();
        adapter.failure = new IllegalStateException("provider failed");
        assertThatThrownBy(() -> predicate.matches(exchange)).isExactlyInstanceOf(RuntimeCamelException.class)
                .hasCause(adapter.failure).hasMessageContaining("provider failed");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @Test
    void initializationChecksReplacementsBeforeRegisteringItsValidator() {
        AtomicInteger validations = new AtomicInteger();
        context.getRegistry().bind("changing", new CountingAdapter() {
            @Override
            public void validate(SemanticEvaluation evaluation) {
                if (validations.incrementAndGet() == 1) {
                    evaluations(evaluation("choice", null, 0.5, 0, "fail"));
                }
            }
        });
        language.setAdapter("changing");
        assertThatThrownBy(() -> language.createPredicate("ref:q"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("boolean");
        // A failed initialization must not register a predicate constraint.
        evaluations(evaluation("choice", null, 0.5, 0, "fail"));
        assertThat(language.createExpression("ref:q")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "${header.broken", "{{missing.selector}}" })
    void reloadRejectsInvalidStateSelectorsBeforePublication(String selector) throws Exception {
        context.start();
        Expression expression = language.createExpression("ref:q");
        SemanticEvaluation previous = SemanticEvaluations.get(context).get("q");
        assertThatThrownBy(() -> evaluations(
                evaluation("boolean", selector, 0.5, 0, "fail")))
                .isInstanceOf(RuntimeException.class);
        assertThat(SemanticEvaluations.get(context).get("q")).isSameAs(previous);
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
    }

    @Test
    void reloadReplacesDefinitionsAndRemovedReferencesFail() {
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        evaluations(
                evaluation("boolean", "${header.updated}", 0.5, 0, "fail"));
        exchange.getMessage().setHeader("updated", "new state");
        expression.evaluate(exchange, Boolean.class);
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType(SemanticLanguage.ADAPTER_NAME, CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("new state");
        assertThatThrownBy(
                () -> SemanticEvaluations.get(context).replace("other", Map.of("q", SemanticEvaluations.get(context).get("q"))))
                .hasMessageContaining("Duplicate");
        SemanticEvaluations.get(context).replace("test", Map.of());
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasMessageContaining("Unknown");
    }

    @Test
    void predicateReloadRejectsIncompatibleTypesBeforePublication() {
        Predicate predicate = language.createPredicate("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        assertThat(predicate.matches(exchange)).isTrue();
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNotNull();
        // Registering an expression for the same name must not replace the predicate's validation.
        language.createExpression("ref:q");
        SemanticEvaluation previous = SemanticEvaluations.get(context).get("q");
        assertThatThrownBy(() -> evaluations(
                evaluation("choice", null, 0.5, 0, "fail")))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessageContaining("boolean");
        assertThat(SemanticEvaluations.get(context).get("q")).isSameAs(previous);
        assertThat(predicate.matches(exchange)).isTrue();
        exchange.setProperty(SemanticLanguage.RESULT, "old");
        SemanticEvaluations.get(context).replace("test", Map.of());
        assertThatThrownBy(() -> predicate.matches(exchange))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @Test
    void labelOnlyProviderWorksWithoutInventedProbabilitiesAndRejectsUnsupportedKinds() {
        language.setAdapter(LabelAdapter.class.getName());
        evaluations(evaluation("choice", null, 0.5, 0, "fail"));
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("invoice");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
        SemanticResult result = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
        assertThat(result.getProbability()).isNull();
        assertThat(result.getProbabilities()).isEmpty();
        assertThat(result.getConfidence()).isNull();
        assertThatThrownBy(() -> language.createPredicate("ref:q")).hasMessageContaining("boolean");
        assertThatThrownBy(() -> evaluations(evaluation("score", null, 0.5, 0,
                "fail"))).hasMessageContaining("only choice");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
    }

    public static class CountingAdapter extends TestSemanticAdapter {
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
        public void validate(SemanticEvaluation evaluation) {
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            this.state = state;
            if (failure != null) {
                throw failure;
            }
            return applyPolicy(evaluation, answer);
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

    public static class InjectedAdapter extends CountingAdapter {
        final String dependency;

        public InjectedAdapter(String dependency) {
            this.dependency = dependency;
        }
    }

    public static class NotAnAdapter {
        static final AtomicInteger constructed = new AtomicInteger();

        public NotAnAdapter() {
            constructed.incrementAndGet();
        }
    }

    public static class LabelAdapter extends TestSemanticAdapter {
        @Override
        public void validate(SemanticEvaluation evaluation) {
            if (!"choice".equals(evaluation.getOperation())) {
                throw new IllegalArgumentException("Supports only choice");
            }
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            return new SemanticResult("billing", null, null, null, Map.of("provider", "fixed-classifier"));
        }
    }
}

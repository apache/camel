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
package org.apache.camel.language.semantic;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;

/** Evaluates a named, provider-independent question against selected message state. */
@Language(value = "semantic", modelName = "language")
@Metadata(title = "Semantic Evaluation",
          description = "Evaluate named questions about message content to produce boolean decisions, categories and scores through provider adapters",
          label = "language,ai", firstVersion = "4.23.0")
public class SemanticLanguage extends LanguageSupport {
    public static final String RESULT = "CamelSemanticResult";
    public static final String RESULTS = "CamelSemanticResults";
    public static final String ADAPTER_NAME = "camelSemanticAdapter";
    public static final String ADAPTER_FACTORY = "semantic-adapter";
    public static final String ADAPTER_RESOURCE = FactoryFinder.DEFAULT_PATH + ADAPTER_FACTORY;

    private String adapter;
    private String defaultState = "${body}";
    private SemanticAdapter selectedAdapter;

    public String getAdapter() {
        return adapter;
    }

    /**
     * Adapter registry bean name or fully qualified implementation class name, without a reference prefix. Registry
     * lookup takes precedence over class resolution. Absent selects the sole advertised adapter.
     */
    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public String getDefaultState() {
        return defaultState;
    }

    /** Default Simple state selector for questions without their own selector. Defaults to the body. */
    public void setDefaultState(String defaultState) {
        this.defaultState = defaultState;
    }

    @Override
    public Expression createExpression(String expression) {
        return createEvaluation(expression, false);
    }

    @Override
    public Predicate createPredicate(String expression) {
        return createEvaluation(expression, true);
    }

    public boolean validateExpression(String expression) {
        references(expression);
        return true;
    }

    public boolean validatePredicate(String expression) {
        validateExpression(expression);
        if (expression.startsWith("refs:")) {
            throw new IllegalArgumentException("Semantic batch expressions cannot be predicates");
        }
        return true;
    }

    private List<String> references(String expression) {
        if (expression != null && expression.startsWith("refs:")) {
            List<String> names = Arrays.stream(expression.substring(5).split(",", -1)).map(String::strip).toList();
            if (names.stream().anyMatch(String::isEmpty)) {
                throw new IllegalArgumentException("Semantic batch requires nonblank question names separated by commas");
            }
            if (new HashSet<>(names).size() != names.size()) {
                throw new IllegalArgumentException("Duplicate semantic question reference in batch");
            }
            return names;
        }
        if (expression == null || !expression.startsWith("ref:") || expression.substring(4).isBlank()) {
            throw new IllegalArgumentException("Semantic expression must use ref:name or refs:name1,name2");
        }
        return List.of(expression.substring(4));
    }

    private Evaluation createEvaluation(String expression, boolean predicate) {
        List<String> names = references(expression);
        boolean batch = expression.startsWith("refs:");
        if (predicate && batch) {
            throw new IllegalArgumentException("Semantic batch expressions cannot be predicates");
        }
        Evaluation evaluation = new Evaluation(names, batch, predicate);
        if (getCamelContext() != null) {
            evaluation.init(getCamelContext());
        }
        return evaluation;
    }

    private synchronized SemanticAdapter adapter() {
        if (selectedAdapter != null) {
            return selectedAdapter;
        }
        CamelContext context = getCamelContext();
        String configured = adapter == null ? null : context.resolvePropertyPlaceholders(adapter);
        if (configured != null) {
            if (configured.isBlank() || configured.startsWith("#")) {
                throw new IllegalArgumentException("Semantic adapter must be a bean name or class name without a # prefix");
            }
            Object bean = context.getRegistry().lookupByName(configured);
            if (bean != null) {
                if (!(bean instanceof SemanticAdapter found)) {
                    throw new IllegalArgumentException(
                            "Semantic adapter bean does not implement SemanticAdapter: " + configured);
                }
                selectedAdapter = found;
                return found;
            }
        }
        ManagedAdapter owned = null;
        try {
            Class<?> resolved = configured == null
                    ? discoverAdapter(context) : context.getClassResolver().resolveClass(configured);
            if (resolved == null) {
                throw new IllegalArgumentException("No semantic adapter bean or class found: " + configured);
            }
            Class<? extends SemanticAdapter> type = resolved.asSubclass(SemanticAdapter.class);
            AdapterLock lock = AdapterLock.get(context);
            synchronized (lock.monitor) {
                if (context.getRegistry().lookupByName(ADAPTER_NAME) != null) {
                    throw new IllegalArgumentException("Semantic adapter registry name is already bound: " + ADAPTER_NAME);
                }
                SemanticAdapter instance = context.getInjector().newInstance(type);
                CamelContextAware.trySetCamelContext(instance, context);
                owned = new ManagedAdapter(context, instance, lock);
                context.getRegistry().bind(ADAPTER_NAME, instance);
            }
            context.addService(owned, true, true);
            selectedAdapter = owned.instance;
            return selectedAdapter;
        } catch (Exception failure) {
            if (owned != null) {
                try {
                    context.removeService(owned);
                    ServiceHelper.stopAndShutdownService(owned);
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw RuntimeCamelException.wrapRuntimeCamelException(failure);
        }
    }

    private Class<?> discoverAdapter(CamelContext context) throws IOException {
        // FactoryFinder resolves one descriptor; check all declarations first to avoid classpath-order selection.
        Set<String> candidates = new TreeSet<>();
        Enumeration<URL> resources = context.getClassResolver().loadAllResourcesAsURL(ADAPTER_RESOURCE);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStream input = resource.openStream()) {
                Properties properties = new Properties();
                properties.load(IOHelper.buffered(input));
                String className = properties.getProperty("class");
                if (className == null || className.isBlank()) {
                    throw new IllegalArgumentException("Semantic adapter descriptor requires a class property: " + resource);
                }
                candidates.add(className);
            }
        }
        if (candidates.size() != 1) {
            throw new IllegalArgumentException(
                    "Semantic language requires exactly one advertised adapter; found "
                                               + candidates + ". Configure camel.language.semantic.adapter explicitly");
        }
        Class<?> resolved = context.getCamelContextExtension().getDefaultFactoryFinder().findClass(ADAPTER_FACTORY)
                .orElseThrow(() -> new IllegalArgumentException("Cannot resolve advertised semantic adapter: " + candidates));
        if (!candidates.contains(resolved.getName())) {
            throw new IllegalArgumentException(
                    "Resolved semantic adapter " + resolved.getName() + " does not match advertised adapter: " + candidates);
        }
        return resolved;
    }

    private static final class AdapterLock {
        private static final Object CREATION_LOCK = new Object();
        private final Object monitor = new Object();

        private static AdapterLock get(CamelContext context) {
            synchronized (CREATION_LOCK) {
                AdapterLock lock = context.getCamelContextExtension().getContextPlugin(AdapterLock.class);
                if (lock == null) {
                    lock = new AdapterLock();
                    context.getCamelContextExtension().addContextPlugin(AdapterLock.class, lock);
                }
                return lock;
            }
        }
    }

    private static final class ManagedAdapter extends ServiceSupport {
        private final CamelContext context;
        private final SemanticAdapter instance;
        private final AdapterLock lock;

        private ManagedAdapter(CamelContext context, SemanticAdapter instance, AdapterLock lock) {
            this.context = context;
            this.instance = instance;
            this.lock = lock;
        }

        @Override
        protected void doInit() throws Exception {
            ServiceHelper.initService(instance);
        }

        @Override
        protected void doStart() throws Exception {
            ServiceHelper.startService(instance);
        }

        @Override
        protected void doStop() throws Exception {
            ServiceHelper.stopService(instance);
        }

        @Override
        protected void doShutdown() throws Exception {
            try {
                ServiceHelper.stopAndShutdownService(instance);
            } finally {
                synchronized (lock.monitor) {
                    if (context.getRegistry().lookupByName(ADAPTER_NAME) == instance) {
                        context.getRegistry().unbind(ADAPTER_NAME);
                    }
                }
            }
        }
    }

    private final class Evaluation extends ExpressionAdapter {
        private final List<String> names;
        private final boolean batch;
        private final boolean predicate;
        private volatile Compiled compiled;
        private volatile SemanticQuestions questions;
        private volatile SemanticAdapter provider;

        private Evaluation(List<String> names, boolean batch, boolean predicate) {
            this.names = names;
            this.batch = batch;
            this.predicate = predicate;
        }

        @Override
        public void init(CamelContext context) {
            super.init(context);
            questions = SemanticQuestions.get(context);
            Map<String, SemanticQuestion> selected = questions.get(names);
            if (predicate) {
                requireBoolean(selected.get(names.get(0)));
            }
            provider = adapter();
            compile(selected);
        }

        private void requireBoolean(SemanticQuestion question) {
            if (question.getType() != SemanticQuestion.Type.BOOLEAN) {
                throw new IllegalArgumentException("Semantic predicate requires a boolean question: " + names.get(0));
            }
        }

        private synchronized Compiled compile(Map<String, SemanticQuestion> selected) {
            if (compiled == null || !compiled.questions.equals(selected)) {
                if (predicate) {
                    requireBoolean(selected.get(names.get(0)));
                }
                String selector = null;
                for (var entry : selected.entrySet()) {
                    SemanticQuestion question = entry.getValue();
                    provider.validate(question);
                    String effective = question.getState() != null ? question.getState() : defaultState;
                    if (effective == null || effective.isBlank()) {
                        throw new IllegalArgumentException(
                                "Semantic state selector must not be blank for question: " + entry.getKey());
                    }
                    effective = getCamelContext().resolvePropertyPlaceholders(effective);
                    if (selector != null && !selector.equals(effective)) {
                        throw new IllegalArgumentException(
                                "Semantic batch questions must use the same effective state selector");
                    }
                    selector = effective;
                }
                Expression state = getCamelContext().resolveLanguage("simple").createExpression(selector);
                state.init(getCamelContext());
                compiled = new Compiled(selected, state);
            }
            return compiled;
        }

        @Override
        public Object evaluate(Exchange exchange) {
            return evaluate(exchange, false);
        }

        private Object evaluate(Exchange exchange, boolean asPredicate) {
            exchange.removeProperty(RESULT);
            exchange.removeProperty(RESULTS);
            SemanticQuestion single = null;
            if (asPredicate) {
                if (batch) {
                    throw new IllegalArgumentException("Semantic batch expressions cannot be predicates");
                }
                single = questions.get(names.get(0));
                requireBoolean(single);
            }
            try {
                Compiled current = compiled;
                if (batch) {
                    Map<String, SemanticQuestion> selected = questions.get(names);
                    if (current == null || !current.questions.equals(selected)) {
                        current = compile(selected);
                    }
                } else {
                    String name = names.get(0);
                    if (single == null) {
                        single = questions.get(name);
                    }
                    if (current == null || current.questions.get(name) != single) {
                        current = compile(Map.of(name, single));
                    }
                }
                Object state = current.state.evaluate(exchange, Object.class);
                if (state == null) {
                    throw new IllegalArgumentException("Missing selected state for semantic questions: " + names);
                }
                if (!(state instanceof String || state instanceof Map<?, ?> || state instanceof List<?>)) {
                    throw new IllegalArgumentException(
                            "Unsupported state type for semantic questions: " + names
                                                       + ". Select strings, maps or lists explicitly");
                }
                if (batch) {
                    Map<String, SemanticResult> results = provider.evaluateBatch(current.questions, state);
                    if (results == null || !results.keySet().equals(current.questions.keySet())) {
                        throw new IllegalArgumentException("Semantic batch result names must match question names");
                    }
                    Map<String, Object> decisions = new LinkedHashMap<>();
                    Map<String, SemanticResult> details = new LinkedHashMap<>();
                    for (var entry : current.questions.entrySet()) {
                        SemanticResult result = results.get(entry.getKey());
                        if (result == null) {
                            throw new IllegalArgumentException("Missing result for semantic question: " + entry.getKey());
                        }
                        decisions.put(entry.getKey(), result.decision(entry.getValue()));
                        details.put(entry.getKey(), result);
                    }
                    exchange.setProperty(RESULTS, Collections.unmodifiableMap(details));
                    return Collections.unmodifiableMap(decisions);
                }
                SemanticQuestion question = current.questions.get(names.get(0));
                SemanticResult result = provider.evaluate(question, state);
                if (result == null) {
                    throw new IllegalArgumentException("Missing result for semantic question: " + names.get(0));
                }
                Object decision = result.decision(question);
                exchange.setProperty(RESULT, result);
                return decision;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        @Override
        public boolean matches(Exchange exchange) {
            return (Boolean) evaluate(exchange, true);
        }

        @Override
        public String toString() {
            return "semantic[" + (batch ? "refs:" : "ref:") + String.join(",", names) + "]";
        }
    }

    private record Compiled(Map<String, SemanticQuestion> questions, Expression state) {
    }
}

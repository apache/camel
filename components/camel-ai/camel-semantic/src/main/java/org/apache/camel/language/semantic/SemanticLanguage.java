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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticCapabilities.Operation;
import org.apache.camel.semantic.SemanticExpert.ResultType;
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
          description = "Evaluate named questions about message content to produce boolean decisions, categories, scores and label sets through provider adapters",
          label = "language,ai", firstVersion = "4.23.0")
public class SemanticLanguage extends LanguageSupport {
    public static final String RESULT = "CamelSemanticResult";
    public static final String RESULTS = "CamelSemanticResults";
    public static final String ADAPTER_NAME = "camelSemanticAdapter";
    public static final String ADAPTER_FACTORY = "semantic-adapter";
    public static final String ADAPTER_RESOURCE = FactoryFinder.DEFAULT_PATH + ADAPTER_FACTORY;

    private final ThreadLocal<Boolean> validating = new ThreadLocal<>();
    private String adapter;
    private String defaultExpert;
    private String defaultState = "${body}";
    private SemanticAdapter selectedAdapter;
    private String selectedAdapterName;
    private ManagedAdapter managedAdapter;

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

    public String getDefaultExpert() {
        return defaultExpert;
    }

    /** Default expert registry bean name for declarations without an explicit expert. */
    public void setDefaultExpert(String defaultExpert) {
        this.defaultExpert = defaultExpert;
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
                selectedAdapterName = configured;
                return found;
            }
        }
        ManagedAdapter owned = null;
        try {
            Object candidate = configured == null
                    ? discoverAdapter(context) : context.getClassResolver().resolveClass(configured);
            if (candidate instanceof SemanticAdapter registered) {
                selectedAdapter = registered;
                return registered;
            }
            Class<?> resolved = (Class<?>) candidate;
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
            managedAdapter = owned;
            selectedAdapter = owned.instance;
            selectedAdapterName = type.getName();
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
            if (failure instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw RuntimeCamelException.wrapRuntimeCamelException(failure);
        }
    }

    private synchronized void startAdapter(List<Group> groups) {
        if (Boolean.TRUE.equals(validating.get())) {
            return;
        }
        ManagedAdapter owned = managedAdapter;
        if (owned == null || groups.stream().noneMatch(group -> group.expert.provider == owned.instance)) {
            return;
        }
        try {
            owned.activate();
        } catch (Exception failure) {
            selectedAdapter = null;
            selectedAdapterName = null;
            managedAdapter = null;
            try {
                getCamelContext().removeService(owned);
                ServiceHelper.stopAndShutdownService(owned);
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw RuntimeCamelException.wrapRuntimeCamelException(failure);
        }
    }

    private Object discoverAdapter(CamelContext context) throws IOException {
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
        Map<String, SemanticAdapter> registered = context.getRegistry().findByTypeWithName(SemanticAdapter.class);
        Set<SemanticAdapter> instances = Collections.newSetFromMap(new IdentityHashMap<>());
        instances.addAll(registered.values());
        // A configured instance supersedes discovery of its implementation; aliases are one instance.
        registered.values().forEach(value -> {
            for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
                candidates.remove(type.getName());
            }
        });
        if (candidates.size() + instances.size() != 1) {
            Set<String> names = new TreeSet<>(registered.keySet());
            names.addAll(candidates);
            throw new IllegalArgumentException(
                    "Semantic language requires exactly one eligible expert; available experts: " + names
                                               + ". Specify expert or configure camel.language.semantic.default-expert explicitly");
        }
        if (instances.size() == 1) {
            SemanticAdapter instance = instances.iterator().next();
            // Keep an operator-facing name; aliases have no primary name, so choose deterministically.
            selectedAdapterName = registered.entrySet().stream()
                    .filter(entry -> entry.getValue() == instance)
                    .map(Map.Entry::getKey).min(String::compareTo).orElseThrow();
            return instance;
        }
        Class<?> resolved = context.getCamelContextExtension().getDefaultFactoryFinder().findClass(ADAPTER_FACTORY)
                .orElseThrow(() -> new IllegalArgumentException("Cannot resolve advertised semantic adapter: " + candidates));
        if (!candidates.contains(resolved.getName())) {
            throw new IllegalArgumentException(
                    "Resolved semantic adapter " + resolved.getName() + " does not match advertised adapter: " + candidates);
        }
        return resolved;
    }

    /** Validate declarations, including unused evaluations, without starting managed expert resources. */
    public void validateDeclarations(Map<String, SemanticQuestion> declarations) {
        validationOnly(() -> {
            declarations.forEach((name, question) -> {
                expert(name, question);
                String selector = question.getState() != null ? question.getState() : defaultState;
                if (selector == null || selector.isBlank()) {
                    throw new IllegalArgumentException("Semantic evaluation '" + name + "': state must not be blank");
                }
                Expression state = getCamelContext().resolveLanguage("simple")
                        .createExpression(getCamelContext().resolvePropertyPlaceholders(selector));
                state.init(getCamelContext());
            });
            return null;
        });
    }

    private <T> T validationOnly(Supplier<T> action) {
        Boolean previous = validating.get();
        validating.set(true);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                validating.remove();
            } else {
                validating.set(previous);
            }
        }
    }

    private ResolvedExpert expert(String name, SemanticQuestion question) {
        String reference = question.getExpert() != null ? question.getExpert() : defaultExpert;
        String label = reference != null ? reference : adapter != null ? adapter : "automatic";
        try {
            SemanticAdapter provider;
            if (reference != null) {
                label = getCamelContext().resolvePropertyPlaceholders(reference);
                if (label.isBlank() || label.startsWith("#")) {
                    throw new IllegalArgumentException("Expert must be a registry bean name without a # prefix");
                }
                Object bean = getCamelContext().getRegistry().lookupByName(label);
                if (!(bean instanceof SemanticAdapter found)) {
                    throw new IllegalArgumentException("Unknown expert or bean does not implement SemanticAdapter: " + label);
                }
                provider = found;
            } else {
                synchronized (this) {
                    provider = adapter();
                    label = selectedAdapterName;
                }
            }
            SemanticCapabilities capabilities = SemanticCapabilities.from(provider.getClass());
            capabilities.validate(question);
            provider.validate(question);
            return new ResolvedExpert(provider, capabilities, label);
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                throw new IllegalArgumentException(
                        "Semantic evaluation '" + name + "', expert '" + label + "': " + e.getMessage(), e);
            }
            throw new RuntimeCamelException(
                    "Semantic evaluation '" + name + "', expert '" + label + "': " + e.getMessage(),
                    e instanceof RuntimeCamelException && e.getCause() != null ? e.getCause() : e);
        }
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
        private boolean activated;

        private ManagedAdapter(CamelContext context, SemanticAdapter instance, AdapterLock lock) {
            this.context = context;
            this.instance = instance;
            this.lock = lock;
        }

        private synchronized void activate() throws Exception {
            if (!activated) {
                activated = true;
                ServiceHelper.initService(instance);
                ServiceHelper.startService(instance);
            }
        }

        @Override
        protected synchronized void doStart() throws Exception {
            // Own cleanup immediately, but start provider resources only after declaration validation.
            if (activated) {
                ServiceHelper.startService(instance);
            }
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
        private final Consumer<Map<String, SemanticQuestion>> validator = this::prepare;
        private volatile Compiled compiled;
        private volatile SemanticQuestions questions;

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
            compile(selected);
            if (!Boolean.TRUE.equals(validating.get())) {
                questions.setValidator(names, validator);
            }
        }

        private void requireBoolean(Operation operation) {
            if (operation.getResultType() != ResultType.BOOLEAN) {
                throw new IllegalArgumentException("Semantic predicate requires a boolean question: " + names.get(0));
            }
        }

        private synchronized Compiled compile(Map<String, SemanticQuestion> selected) {
            if (Boolean.TRUE.equals(validating.get())) {
                // Revisit cached nested selectors against the candidate snapshot without publishing compilation state.
                return prepare(selected);
            }
            if (compiled == null || !compiled.questions.equals(selected)) {
                Compiled candidate = prepare(selected);
                startAdapter(candidate.groups);
                compiled = candidate;
            }
            return compiled;
        }

        private Compiled prepare(Map<String, SemanticQuestion> selected) {
            return validationOnly(() -> prepareDeclarations(selected));
        }

        private Compiled prepareDeclarations(Map<String, SemanticQuestion> selected) {
            Map<String, Operation> operations = new LinkedHashMap<>();
            String selector = null;
            Map<SemanticAdapter, Group> byInstance = new IdentityHashMap<>();
            List<Group> groups = new ArrayList<>();
            for (var entry : selected.entrySet()) {
                SemanticQuestion question = entry.getValue();
                ResolvedExpert resolved = expert(entry.getKey(), question);
                Operation operation = resolved.capabilities.operation(question.getOperation());
                operations.put(entry.getKey(), operation);
                if (predicate) {
                    requireBoolean(operation);
                }
                Group group = byInstance.get(resolved.provider);
                if (group == null) {
                    group = new Group(resolved, new LinkedHashMap<>());
                    byInstance.put(resolved.provider, group);
                    groups.add(group);
                }
                group.questions.put(entry.getKey(), question);
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
            return new Compiled(
                    selected, Map.copyOf(operations), state, groups.stream()
                            .map(group -> new Group(group.expert, Collections.unmodifiableMap(group.questions))).toList());
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
                if (asPredicate) {
                    requireBoolean(current.operations.get(names.get(0)));
                }
                startAdapter(current.groups);
                Object state = current.state.evaluate(exchange, Object.class);
                // A state selector can itself evaluate another semantic expression.
                exchange.removeProperty(RESULT);
                exchange.removeProperty(RESULTS);
                if (state == null) {
                    throw new IllegalArgumentException("Missing selected state for semantic questions: " + names);
                }
                if (!(state instanceof String || state instanceof Map<?, ?> || state instanceof List<?>)) {
                    throw new IllegalArgumentException(
                            "Unsupported state type for semantic questions: " + names
                                                       + ". Select strings, maps or lists explicitly");
                }
                // Check every group's input before invoking any provider.
                for (Group group : current.groups) {
                    try {
                        for (var entry : group.questions.entrySet()) {
                            current.operations.get(entry.getKey()).validateInput(state);
                            group.expert.provider.validateInput(entry.getValue(), state);
                        }
                    } catch (IllegalArgumentException invalid) {
                        throw new IllegalArgumentException(
                                "Semantic evaluations " + group.questions.keySet() + ", expert '" + group.expert.reference
                                                           + "': " + invalid.getMessage(),
                                invalid);
                    }
                }
                if (batch) {
                    Map<String, SemanticResult> results = new LinkedHashMap<>();
                    Map<String, Object> decisions = new LinkedHashMap<>();
                    for (Group group : current.groups) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Semantic batch evaluation interrupted");
                        }
                        Map<String, SemanticResult> answers = group.expert.provider.evaluateBatch(group.questions, state);
                        if (answers == null || !answers.keySet().equals(group.questions.keySet())) {
                            throw new IllegalArgumentException("Semantic batch result names must match question names");
                        }
                        for (String name : group.questions.keySet()) {
                            decisions.put(name,
                                    validateResult(name, group.expert, current.operations.get(name), answers.get(name)));
                        }
                        results.putAll(answers);
                    }
                    Map<String, SemanticResult> details = new LinkedHashMap<>();
                    Map<String, Object> ordered = new LinkedHashMap<>();
                    for (String name : current.questions.keySet()) {
                        details.put(name, results.get(name));
                        ordered.put(name, decisions.get(name));
                    }
                    exchange.setProperty(RESULTS, Collections.unmodifiableMap(details));
                    return Collections.unmodifiableMap(ordered);
                }
                SemanticQuestion question = current.questions.get(names.get(0));
                SemanticResult result = current.groups.get(0).expert.provider.evaluate(question, state);
                Object decision = validateResult(names.get(0), current.groups.get(0).expert,
                        current.operations.get(names.get(0)), result);
                exchange.setProperty(RESULT, result);
                return decision;
            } catch (InterruptedException e) {
                exchange.removeProperty(RESULT);
                exchange.removeProperty(RESULTS);
                Thread.currentThread().interrupt();
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            } catch (Exception e) {
                exchange.removeProperty(RESULT);
                exchange.removeProperty(RESULTS);
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        private Object validateResult(String name, ResolvedExpert expert, Operation operation, SemanticResult result) {
            try {
                if (result == null) {
                    throw new IllegalArgumentException("Missing result");
                }
                return operation.validateResult(result);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(
                        "Semantic evaluation '" + name + "', expert '" + expert.reference
                                                   + "': " + invalid.getMessage(),
                        invalid);
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

    private record ResolvedExpert(SemanticAdapter provider, SemanticCapabilities capabilities, String reference) {
    }

    private record Group(ResolvedExpert expert, Map<String, SemanticQuestion> questions) {
    }

    private record Compiled(Map<String, SemanticQuestion> questions, Map<String, Operation> operations,
            Expression state, List<Group> groups) {
    }
}

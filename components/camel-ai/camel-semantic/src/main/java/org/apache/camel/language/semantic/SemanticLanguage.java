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
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticEvaluations;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;

/** Invokes a named, provider-independent evaluation against selected message state. */
@Language(value = "semantic", modelName = "language")
@Metadata(title = "Semantic Evaluation",
          description = "Invoke named evaluations of message content to produce boolean decisions, categories, scores and label sets through provider adapters",
          label = "language,ai", firstVersion = "4.23.0")
public class SemanticLanguage extends LanguageSupport {
    public static final String RESULT = "CamelSemanticResult";
    public static final String RESULTS = "CamelSemanticResults";
    public static final String ADAPTER_NAME = "camelSemanticAdapter";
    public static final String ADAPTER_FACTORY = "semantic-adapter";
    public static final String ADAPTER_RESOURCE = FactoryFinder.DEFAULT_PATH + ADAPTER_FACTORY;

    // Nested Simple functions re-enter the language while compiling state selectors. Keep that scope private to
    // compilation; registry reads and expert callbacks must always observe published declarations.
    private final ThreadLocal<Validation> validation = new ThreadLocal<>();
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

    /** Default Simple state selector for evaluations without their own selector. Defaults to the body. */
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
                throw new IllegalArgumentException("Semantic batch requires nonblank evaluation names separated by commas");
            }
            if (new HashSet<>(names).size() != names.size()) {
                throw new IllegalArgumentException("Duplicate semantic evaluation reference in batch");
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
    public void validateDeclarations(Map<String, SemanticEvaluation> declarations) {
        validateDeclarations(declarations, null);
    }

    /** Validate changed declarations against the complete candidate snapshot without publishing it. */
    public void validateDeclarations(
            Map<String, SemanticEvaluation> declarations, Map<String, SemanticEvaluation> snapshot) {
        validationOnly(snapshot, () -> {
            declarations.forEach((name, evaluation) -> {
                expert(name, evaluation);
                String selector = evaluation.getState() != null ? evaluation.getState() : defaultState;
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

    private <T> T validationOnly(Map<String, SemanticEvaluation> snapshot, Supplier<T> action) {
        Validation previous = validation.get();
        validation.set(snapshot != null || previous == null ? new Validation(snapshot) : previous);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                validation.remove();
            } else {
                validation.set(previous);
            }
        }
    }

    private ResolvedExpert expert(String name, SemanticEvaluation evaluation) {
        Validation previous = validation.get();
        validation.remove();
        try {
            return resolveExpert(name, evaluation);
        } finally {
            if (previous != null) {
                validation.set(previous);
            }
        }
    }

    private ResolvedExpert resolveExpert(String name, SemanticEvaluation evaluation) {
        String reference = evaluation.getExpert() != null ? evaluation.getExpert() : defaultExpert;
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
            capabilities.validate(evaluation);
            provider.validate(evaluation);
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
        private final Consumer<Map<String, SemanticEvaluation>> validator = this::validateReplacement;
        private volatile Compiled compiled;
        private volatile SemanticEvaluations evaluations;

        private Evaluation(List<String> names, boolean batch, boolean predicate) {
            this.names = names;
            this.batch = batch;
            this.predicate = predicate;
        }

        @Override
        public void init(CamelContext context) {
            super.init(context);
            evaluations = SemanticEvaluations.get(context);
            Validation scope = validation.get();
            Map<String, SemanticEvaluation> selected = scope != null && scope.snapshot != null
                    ? select(scope.snapshot, true) : evaluations.get(names);
            compile(selected);
            if (scope == null) {
                evaluations.setValidator(names, validator);
            }
        }

        private void requireBoolean(Operation operation) {
            if (operation.getResultType() != ResultType.BOOLEAN) {
                throw new IllegalArgumentException("Semantic predicate requires a boolean evaluation: " + names.get(0));
            }
        }

        private synchronized Compiled compile(Map<String, SemanticEvaluation> selected) {
            if (validation.get() != null) {
                // Revisit cached nested selectors against the candidate snapshot without publishing compilation state.
                return prepare(selected);
            }
            if (compiled == null || !compiled.evaluations.equals(selected)) {
                Compiled candidate = prepare(selected);
                startAdapter(candidate.groups);
                compiled = candidate;
            }
            return compiled;
        }

        private Compiled prepare(Map<String, SemanticEvaluation> selected) {
            return validationOnly(null, () -> prepareDeclarations(selected));
        }

        private void validateReplacement(Map<String, SemanticEvaluation> snapshot) {
            validationOnly(snapshot, () -> prepareDeclarations(select(snapshot, false)));
        }

        private Map<String, SemanticEvaluation> select(Map<String, SemanticEvaluation> snapshot, boolean required) {
            Map<String, SemanticEvaluation> selected = new LinkedHashMap<>();
            for (String name : names) {
                SemanticEvaluation evaluation = snapshot.get(name);
                // Removed declarations remain removable; their existing expressions fail if evaluated again.
                if (evaluation != null) {
                    selected.put(name, evaluation);
                } else if (required) {
                    throw new IllegalArgumentException("Unknown semantic evaluation: " + name);
                }
            }
            return Collections.unmodifiableMap(selected);
        }

        private Compiled prepareDeclarations(Map<String, SemanticEvaluation> selected) {
            Map<String, Operation> operations = new LinkedHashMap<>();
            String selector = null;
            Map<SemanticAdapter, Group> byInstance = new IdentityHashMap<>();
            List<Group> groups = new ArrayList<>();
            for (var entry : selected.entrySet()) {
                SemanticEvaluation evaluation = entry.getValue();
                ResolvedExpert resolved = expert(entry.getKey(), evaluation);
                Operation operation = resolved.capabilities.operation(evaluation.getOperation());
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
                group.evaluations.put(entry.getKey(), evaluation);
                String effective = evaluation.getState() != null ? evaluation.getState() : defaultState;
                if (effective == null || effective.isBlank()) {
                    throw new IllegalArgumentException(
                            "Semantic state selector must not be blank for evaluation: " + entry.getKey());
                }
                effective = getCamelContext().resolvePropertyPlaceholders(effective);
                if (selector != null && !selector.equals(effective)) {
                    throw new IllegalArgumentException(
                            "Semantic batch evaluations must use the same effective state selector");
                }
                selector = effective;
            }
            Expression state = getCamelContext().resolveLanguage("simple").createExpression(selector);
            state.init(getCamelContext());
            return new Compiled(
                    selected, Map.copyOf(operations), state, groups.stream()
                            .map(group -> new Group(group.expert, Collections.unmodifiableMap(group.evaluations))).toList());
        }

        @Override
        public Object evaluate(Exchange exchange) {
            return evaluate(exchange, false);
        }

        private Object evaluate(Exchange exchange, boolean asPredicate) {
            exchange.removeProperty(RESULT);
            exchange.removeProperty(RESULTS);
            SemanticEvaluation single = null;
            if (asPredicate) {
                if (batch) {
                    throw new IllegalArgumentException("Semantic batch expressions cannot be predicates");
                }
                single = evaluations.get(names.get(0));
            }
            try {
                Compiled current = compiled;
                if (batch) {
                    Map<String, SemanticEvaluation> selected = evaluations.get(names);
                    if (current == null || !current.evaluations.equals(selected)) {
                        current = compile(selected);
                    }
                } else {
                    String name = names.get(0);
                    if (single == null) {
                        single = evaluations.get(name);
                    }
                    if (current == null || current.evaluations.get(name) != single) {
                        current = compile(Map.of(name, single));
                    }
                }
                if (asPredicate) {
                    requireBoolean(current.operations.get(names.get(0)));
                }
                Object state = current.state.evaluate(exchange, Object.class);
                // A state selector can itself evaluate another semantic expression.
                exchange.removeProperty(RESULT);
                exchange.removeProperty(RESULTS);
                if (state == null) {
                    throw new IllegalArgumentException("Missing selected state for semantic evaluations: " + names);
                }
                if (!(state instanceof String || state instanceof Map<?, ?> || state instanceof List<?>)) {
                    throw new IllegalArgumentException(
                            "Unsupported state type for semantic evaluations: " + names
                                                       + ". Select strings, maps or lists explicitly");
                }
                // Check every group's input before invoking any provider.
                for (Group group : current.groups) {
                    try {
                        for (var entry : group.evaluations.entrySet()) {
                            current.operations.get(entry.getKey()).validateInput(state);
                            group.expert.provider.validateInput(entry.getValue(), state);
                        }
                    } catch (IllegalArgumentException invalid) {
                        throw new IllegalArgumentException(
                                "Semantic evaluations " + group.evaluations.keySet() + ", expert '" + group.expert.reference
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
                        Map<String, SemanticResult> answers = group.expert.provider.evaluateBatch(group.evaluations, state);
                        if (answers == null || !answers.keySet().equals(group.evaluations.keySet())) {
                            throw new IllegalArgumentException("Semantic batch result names must match evaluation names");
                        }
                        for (String name : group.evaluations.keySet()) {
                            decisions.put(name,
                                    validateResult(name, group.expert, current.operations.get(name), answers.get(name)));
                        }
                        results.putAll(answers);
                    }
                    Map<String, SemanticResult> details = new LinkedHashMap<>();
                    Map<String, Object> ordered = new LinkedHashMap<>();
                    for (String name : current.evaluations.keySet()) {
                        details.put(name, results.get(name));
                        ordered.put(name, decisions.get(name));
                    }
                    exchange.setProperty(RESULTS, Collections.unmodifiableMap(details));
                    return Collections.unmodifiableMap(ordered);
                }
                SemanticEvaluation evaluation = current.evaluations.get(names.get(0));
                SemanticResult result = current.groups.get(0).expert.provider.evaluate(evaluation, state);
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

    private record Validation(Map<String, SemanticEvaluation> snapshot) {
    }

    private record ResolvedExpert(SemanticAdapter provider, SemanticCapabilities capabilities, String reference) {
    }

    private record Group(ResolvedExpert expert, Map<String, SemanticEvaluation> evaluations) {
    }

    private record Compiled(Map<String, SemanticEvaluation> evaluations, Map<String, Operation> operations,
            Expression state, List<Group> groups) {
    }
}

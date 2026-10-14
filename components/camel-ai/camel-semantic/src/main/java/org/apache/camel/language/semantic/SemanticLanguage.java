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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
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
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageSupport;

/** Invokes a named, provider-independent evaluation against selected message state. */
@Language(value = "semantic", modelName = "language")
@Metadata(title = "Semantic Evaluation",
          description = "Invoke named evaluations of message content to produce boolean decisions, categories, scores and label sets through provider adapters",
          label = "language,ai", firstVersion = "4.23.0")
public class SemanticLanguage extends LanguageSupport {
    public static final String RESULT = "CamelSemanticResult";
    public static final String RESULTS = "CamelSemanticResults";
    // Nested Simple functions re-enter the language while compiling state selectors. Keep that scope private to
    // compilation; registry reads and expert callbacks must always observe published declarations.
    private final ThreadLocal<Validation> validation = new ThreadLocal<>();
    private String defaultState = "${body}";

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

    /**
     * Evaluate already-selected input through an expert without publishing a declaration or executing a route. The
     * declaration's state selector is not used. Contract, provider input and result validation still apply.
     */
    public SemanticResult evaluate(SemanticEvaluation evaluation, Object input) throws Exception {
        return evaluate(evaluation, input, null);
    }

    /** Internal console bridge carrying a request identity; applications use the two-argument evaluation method. */
    public SemanticResult evaluate(SemanticEvaluation evaluation, Object input, SemanticAuditService.Request request)
            throws Exception {
        SemanticAuditService audit = SemanticAuditService.get(getCamelContext());
        SemanticAuditService.Invocation invocation = null;
        String reason = "invalid_declaration";
        try {
            ResolvedExpert resolved = expert("direct", evaluation);
            Operation operation = resolved.capabilities.operation(evaluation.getOperation());
            invocation = audit.begin(null, null, evaluation, operation, resolved.reference, resolved.capabilities.getProvider(),
                    null, request);
            reason = "invalid_input";
            invocation.captureInput(input);
            operation.validateInput(input);
            resolved.provider.validateInput(evaluation, input);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Semantic evaluation interrupted");
            }
            reason = "provider_error";
            SemanticResult result = resolved.provider.evaluate(evaluation, input);
            reason = "invalid_result";
            operation.validateResult(result);
            invocation.complete("success", "evaluation_completed", result);
            return result;
        } catch (Exception | AssertionError failure) {
            if (invocation == null) {
                invocation = audit.begin(null, null, evaluation, null, auditExpert(evaluation), null, null, request);
                invocation.captureInput(input);
            }
            invocation.complete("failed", failure instanceof InterruptedException ? "interrupted" : reason, null);
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw failure;
        }
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

    /** Read a named expert's static contract without evaluating or validating a declaration. */
    public SemanticCapabilities getExpertCapabilities(String expert) {
        return describeExpert(expert).capabilities();
    }

    /** The selected registry reference and its static contract. */
    public record ExpertMetadata(String reference, SemanticCapabilities capabilities) {
    }

    /** Resolve an explicitly named registry expert for inspection without invoking it. */
    public ExpertMetadata describeExpert(String expert) {
        String reference = expertReference(expert);
        return new ExpertMetadata(reference, SemanticCapabilities.from(expertBean(reference).getClass()));
    }

    private String expertReference(String expert) {
        if (expert == null || expert.isBlank()) {
            throw new IllegalArgumentException("Evaluation expert is required");
        }
        String reference = getCamelContext().resolvePropertyPlaceholders(expert);
        if (reference.isBlank() || reference.startsWith("#")) {
            throw new IllegalArgumentException("Expert must be a registry bean name without a # prefix");
        }
        return reference;
    }

    private SemanticAdapter expertBean(String reference) {
        Object bean = getCamelContext().getRegistry().lookupByName(reference);
        if (!(bean instanceof SemanticAdapter expert)) {
            throw new IllegalArgumentException("Unknown expert or bean does not implement SemanticAdapter: " + reference);
        }
        return expert;
    }

    /** Validate declarations, including unused evaluations, without invoking expert evaluations. */
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
        // An explicit snapshot overrides the scope; nested calls without one inherit the outer validation scope.
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
        String label = evaluation.getExpert();
        try {
            label = expertReference(label);
            SemanticAdapter provider = expertBean(label);
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

    private String auditExpert(SemanticEvaluation evaluation) {
        if (evaluation == null) {
            return null;
        }
        String reference = evaluation.getExpert();
        try {
            return getCamelContext().resolvePropertyPlaceholders(reference);
        } catch (RuntimeException unresolved) {
            return null;
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
            SemanticAuditService audit = SemanticAuditService.get(getCamelContext());
            Map<String, SemanticAuditService.Invocation> invocations = new LinkedHashMap<>();
            List<SemanticAuditService.Invocation> active = new ArrayList<>();
            String failureReason = "invalid_declaration";
            Map<String, SemanticEvaluation> selected = Map.of();
            exchange.removeProperty(SemanticAuditService.REFERENCES);
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
                    selected = evaluations.get(names);
                    if (current == null || !current.evaluations.equals(selected)) {
                        current = compile(selected);
                    }
                } else {
                    String name = names.get(0);
                    if (single == null) {
                        single = evaluations.get(name);
                    }
                    selected = Map.of(name, single);
                    if (current == null || current.evaluations.get(name) != single) {
                        current = compile(selected);
                    }
                }
                if (asPredicate) {
                    requireBoolean(current.operations.get(names.get(0)));
                }
                String batchId = batch ? getCamelContext().getUuidGenerator().generateUuid() : null;
                for (Group group : current.groups) {
                    for (var entry : group.evaluations.entrySet()) {
                        String reference = auditExpert(entry.getValue());
                        invocations.put(entry.getKey(), audit.begin(exchange, entry.getKey(), entry.getValue(),
                                current.operations.get(entry.getKey()), reference, group.expert.capabilities.getProvider(),
                                batchId, null));
                    }
                }
                active.addAll(invocations.values());
                failureReason = "invalid_input";
                Object state = current.state.evaluate(exchange, Object.class);
                invocations.values().forEach(invocation -> invocation.captureInput(state));
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
                setAuditReferences(exchange, invocations);
                // Check every group's input before invoking any provider.
                for (Group group : current.groups) {
                    try {
                        for (var entry : group.evaluations.entrySet()) {
                            active.clear();
                            active.add(invocations.get(entry.getKey()));
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
                active.clear();
                failureReason = "provider_error";
                if (batch) {
                    Map<String, SemanticResult> results = new LinkedHashMap<>();
                    Map<String, Object> decisions = new LinkedHashMap<>();
                    for (Group group : current.groups) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Semantic batch evaluation interrupted");
                        }
                        active.clear();
                        group.evaluations.keySet().forEach(name -> active.add(invocations.get(name)));
                        failureReason = "provider_error";
                        Map<String, SemanticResult> answers = group.expert.provider.evaluateBatch(group.evaluations, state);
                        failureReason = "invalid_result";
                        if (answers == null || !answers.keySet().equals(group.evaluations.keySet())) {
                            throw new IllegalArgumentException("Semantic batch result names must match evaluation names");
                        }
                        IllegalArgumentException invalidResult = null;
                        for (String name : group.evaluations.keySet()) {
                            try {
                                decisions.put(name,
                                        validateResult(name, group.expert, current.operations.get(name), answers.get(name)));
                                invocations.get(name).complete("success", "evaluation_completed", answers.get(name));
                            } catch (IllegalArgumentException invalid) {
                                invocations.get(name).complete("failed", "invalid_result", null);
                                if (invalidResult == null) {
                                    invalidResult = invalid;
                                }
                            }
                        }
                        if (invalidResult != null) {
                            throw invalidResult;
                        }
                        active.clear();
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
                active.add(invocations.get(names.get(0)));
                SemanticResult result = current.groups.get(0).expert.provider.evaluate(evaluation, state);
                failureReason = "invalid_result";
                Object decision = validateResult(names.get(0), current.groups.get(0).expert,
                        current.operations.get(names.get(0)), result);
                invocations.get(names.get(0)).complete("success", "evaluation_completed", result);
                exchange.setProperty(RESULT, result);
                return decision;
            } catch (InterruptedException e) {
                finishFailed(invocations, active, "interrupted");
                exchange.removeProperty(RESULT);
                exchange.removeProperty(RESULTS);
                Thread.currentThread().interrupt();
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            } catch (Exception | AssertionError e) {
                if (invocations.isEmpty()) {
                    for (String name : names) {
                        SemanticEvaluation declaration = selected.get(name);
                        SemanticAuditService.Invocation rejected = audit.begin(exchange, name, declaration, null,
                                auditExpert(declaration), null, null, null);
                        invocations.put(name, rejected);
                        rejected.complete("failed", failureReason, null);
                    }
                }
                finishFailed(invocations, active, failureReason);
                exchange.removeProperty(RESULT);
                exchange.removeProperty(RESULTS);
                if (e instanceof AssertionError error) {
                    throw error;
                }
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            } finally {
                setAuditReferences(exchange, invocations);
            }
        }

        private void setAuditReferences(Exchange exchange, Map<String, SemanticAuditService.Invocation> invocations) {
            List<String> references = invocations.values().stream()
                    .map(SemanticAuditService.Invocation::getEventId).filter(Objects::nonNull).toList();
            if (references.isEmpty()) {
                exchange.removeProperty(SemanticAuditService.REFERENCES);
            } else {
                exchange.setProperty(SemanticAuditService.REFERENCES, references);
            }
        }

        private void finishFailed(
                Map<String, SemanticAuditService.Invocation> invocations, List<SemanticAuditService.Invocation> active,
                String reason) {
            for (SemanticAuditService.Invocation invocation : invocations.values()) {
                invocation.complete(active.contains(invocation) ? "failed" : "not_executed",
                        active.contains(invocation) ? reason : "evaluation_aborted", null);
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

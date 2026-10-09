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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.spi.Resource;

/** Context-local named evaluations, replaced atomically per source when a route resource is reloaded. */
public final class SemanticEvaluations {
    private static final Object CREATION_LOCK = new Object();
    private final CamelContext context;
    private final Map<String, Map<String, SemanticEvaluation>> sources = new HashMap<>();
    private final Map<String, Resource> resources = new HashMap<>();
    private volatile Map<String, SemanticEvaluation> evaluations = Map.of();
    private final Map<Consumer<Map<String, SemanticEvaluation>>, List<String>> validators = new WeakHashMap<>();
    // Accessed only under this registry's monitor, including reentrant validator registration.
    private PendingReplacement pendingReplacement;

    private SemanticEvaluations(CamelContext context) {
        this.context = context;
    }

    /** Validate every declaration, including evaluations not referenced by a route. */
    public void validate() {
        if (!evaluations.isEmpty()) {
            ((SemanticLanguage) context.resolveLanguage("semantic")).validateDeclarations(evaluations);
        }
    }

    /**
     * Validate current declarations and weakly track a callback owned by its initialized expression. The callback
     * receives the complete immutable snapshot so nested references can be validated against the same replacement.
     * Registrations made during replacement validation must also accept the pending replacement.
     */
    public synchronized void setValidator(List<String> names, Consumer<Map<String, SemanticEvaluation>> validator) {
        // A replacement may have occurred between the expression's initial compilation and registration.
        get(names);
        // Reentrant registration first checks published definitions; the replacement is not visible until it succeeds.
        validator.accept(evaluations);
        if (pendingReplacement != null && !Collections.disjoint(names, pendingReplacement.changedNames)) {
            validator.accept(pendingReplacement.snapshot);
        }
        validators.put(validator, List.copyOf(names));
    }

    public static SemanticEvaluations get(CamelContext context) {
        var extension = context.getCamelContextExtension();
        SemanticEvaluations answer = extension.getContextPlugin(SemanticEvaluations.class);
        if (answer == null) {
            synchronized (CREATION_LOCK) {
                answer = extension.getContextPlugin(SemanticEvaluations.class);
                if (answer == null) {
                    answer = new SemanticEvaluations(context);
                    extension.addContextPlugin(SemanticEvaluations.class, answer);
                }
            }
        }
        return answer;
    }

    /**
     * Replace all definitions from one source; an empty map removes obsolete declarations. The Java fluent helper
     * reserves {@code java:} followed by the resource location (or a generated key for embedded builders). XML and YAML
     * declarations use the resource location as their source key. On a started context, new and changed declarations
     * are validated before publication, including unused evaluations. A validation failure preserves the previous
     * source.
     */
    public synchronized void replace(String source, Map<String, SemanticEvaluation> definitions) {
        Map<String, SemanticEvaluation> replacement = new HashMap<>();
        sources.forEach((location, entries) -> {
            if (!location.equals(source)) {
                replacement.putAll(entries);
            }
        });
        definitions.forEach((name, evaluation) -> {
            if (name == null || name.isBlank() || evaluation == null) {
                throw new IllegalArgumentException("Semantic evaluation requires a name and definition");
            }
            if (replacement.putIfAbsent(name, evaluation) != null) {
                throw new IllegalArgumentException("Duplicate semantic evaluation: " + name);
            }
        });
        Map<String, SemanticEvaluation> snapshot = Map.copyOf(replacement);
        PendingReplacement previous = pendingReplacement;
        pendingReplacement = new PendingReplacement(snapshot, Set.copyOf(definitions.keySet()));
        try {
            if (context.isStarted() && !definitions.isEmpty()) {
                ((SemanticLanguage) context.resolveLanguage("semantic")).validateDeclarations(definitions, snapshot);
            }
            // Expert callbacks may initialize expressions and register additional validators reentrantly.
            new LinkedHashMap<>(validators).forEach((validator, names) -> {
                if (!Collections.disjoint(names, definitions.keySet())) {
                    validator.accept(snapshot);
                }
            });
        } finally {
            pendingReplacement = previous;
        }
        if (definitions.isEmpty()) {
            sources.remove(source);
        } else {
            sources.put(source, Map.copyOf(definitions));
        }
        resources.remove(source);
        evaluations = snapshot;
    }

    /** Track a route resource so deleted files can be discarded before development-mode reload. */
    public synchronized void replace(Resource source, Map<String, SemanticEvaluation> definitions) {
        replace(source.getLocation(), source, definitions);
    }

    synchronized void replace(String location, Resource source, Map<String, SemanticEvaluation> definitions) {
        removeDeletedResources();
        replace(location, definitions);
        if (source != null && !definitions.isEmpty() && "file".equals(source.getScheme())) {
            resources.put(location, source);
        }
    }

    synchronized void removeDeletedResources() {
        resources.entrySet().stream().filter(entry -> !entry.getValue().exists()).map(Map.Entry::getKey).toList()
                .forEach(location -> replace(location, Map.of()));
    }

    synchronized void remove(String source) {
        if (sources.containsKey(source)) {
            replace(source, Map.of());
        }
    }

    /** All definitions in the immutable published snapshot, for runtime inspection. */
    public Map<String, SemanticEvaluation> snapshot() {
        return evaluations;
    }

    /** Whether any named evaluations have been registered. */
    public boolean isEmpty() {
        return evaluations.isEmpty();
    }

    /**
     * Resolve a named evaluation from the published snapshot, including when called during replacement validation.
     * Candidate definitions become visible only after validation succeeds.
     */
    public SemanticEvaluation get(String name) {
        SemanticEvaluation evaluation = evaluations.get(name);
        if (evaluation == null) {
            throw new IllegalArgumentException("Unknown semantic evaluation: " + name);
        }
        return evaluation;
    }

    /**
     * Resolve all requested names from one immutable published snapshot, preserving reference order. This also reads
     * the published snapshot during replacement validation; candidate definitions become visible only after validation
     * succeeds.
     */
    public Map<String, SemanticEvaluation> get(List<String> names) {
        Map<String, SemanticEvaluation> snapshot = evaluations;
        Map<String, SemanticEvaluation> selected = new LinkedHashMap<>();
        for (String name : names) {
            SemanticEvaluation evaluation = snapshot.get(name);
            if (evaluation == null) {
                throw new IllegalArgumentException("Unknown semantic evaluation: " + name);
            }
            selected.put(name, evaluation);
        }
        return Collections.unmodifiableMap(selected);
    }

    private record PendingReplacement(Map<String, SemanticEvaluation> snapshot, Set<String> changedNames) {
    }

}

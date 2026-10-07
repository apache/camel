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
    private final ThreadLocal<Map<String, SemanticEvaluation>> candidate = new ThreadLocal<>();
    private final Map<Consumer<Map<String, SemanticEvaluation>>, List<String>> validators = new WeakHashMap<>();

    private SemanticEvaluations(CamelContext context) {
        this.context = context;
    }

    /** Validate every declaration, including evaluations not referenced by a route. */
    public void validate() {
        if (!evaluations.isEmpty()) {
            ((SemanticLanguage) context.resolveLanguage("semantic")).validateDeclarations(evaluations);
        }
    }

    /** Validate current declarations and weakly track a callback owned by its initialized expression. */
    public synchronized void setValidator(List<String> names, Consumer<Map<String, SemanticEvaluation>> validator) {
        // A replacement may have occurred between the expression's initial compilation and registration.
        validator.accept(get(names));
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
        Map<String, SemanticEvaluation> previous = candidate.get();
        candidate.set(Map.copyOf(replacement));
        try {
            if (context.isStarted() && !definitions.isEmpty()) {
                ((SemanticLanguage) context.resolveLanguage("semantic")).validateDeclarations(definitions);
            }
            validators.forEach((validator, names) -> {
                if (!Collections.disjoint(names, definitions.keySet())) {
                    Map<String, SemanticEvaluation> selected = new LinkedHashMap<>();
                    names.forEach(name -> {
                        SemanticEvaluation evaluation = replacement.get(name);
                        // Removed declarations remain removable; their existing expressions fail if evaluated again.
                        if (evaluation != null) {
                            selected.put(name, evaluation);
                        }
                    });
                    validator.accept(Collections.unmodifiableMap(selected));
                }
            });
        } finally {
            if (previous == null) {
                candidate.remove();
            } else {
                candidate.set(previous);
            }
        }
        if (definitions.isEmpty()) {
            sources.remove(source);
        } else {
            sources.put(source, Map.copyOf(definitions));
        }
        resources.remove(source);
        evaluations = Map.copyOf(replacement);
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

    /** Whether any named evaluations have been registered. */
    public boolean isEmpty() {
        return evaluations.isEmpty();
    }

    public SemanticEvaluation get(String name) {
        SemanticEvaluation evaluation = snapshot().get(name);
        if (evaluation == null) {
            throw new IllegalArgumentException("Unknown semantic evaluation: " + name);
        }
        return evaluation;
    }

    /** Resolve all requested names from one immutable snapshot, preserving reference order. */
    public Map<String, SemanticEvaluation> get(List<String> names) {
        Map<String, SemanticEvaluation> snapshot = snapshot();
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

    private Map<String, SemanticEvaluation> snapshot() {
        Map<String, SemanticEvaluation> validation = candidate.get();
        return validation != null ? validation : evaluations;
    }

}

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

/** Context-local named questions, replaced atomically per source when a route resource is reloaded. */
public final class SemanticQuestions {
    private static final Object CREATION_LOCK = new Object();
    private final CamelContext context;
    private final Map<String, Map<String, SemanticQuestion>> sources = new HashMap<>();
    private final Map<String, Resource> resources = new HashMap<>();
    private volatile Map<String, SemanticQuestion> questions = Map.of();
    private final ThreadLocal<Map<String, SemanticQuestion>> candidate = new ThreadLocal<>();
    private final Map<Consumer<Map<String, SemanticQuestion>>, List<String>> validators = new WeakHashMap<>();

    private SemanticQuestions(CamelContext context) {
        this.context = context;
    }

    /** Validate every declaration, including evaluations not referenced by a route. */
    public void validate() {
        if (!questions.isEmpty()) {
            ((SemanticLanguage) context.resolveLanguage("semantic")).validateDeclarations(questions);
        }
    }

    /** Validate current declarations and weakly track a callback owned by its initialized expression. */
    public synchronized void setValidator(List<String> names, Consumer<Map<String, SemanticQuestion>> validator) {
        // A replacement may have occurred between the expression's initial compilation and registration.
        validator.accept(get(names));
        validators.put(validator, List.copyOf(names));
    }

    public static SemanticQuestions get(CamelContext context) {
        synchronized (CREATION_LOCK) {
            SemanticQuestions answer = context.getCamelContextExtension().getContextPlugin(SemanticQuestions.class);
            if (answer == null) {
                answer = new SemanticQuestions(context);
                context.getCamelContextExtension().addContextPlugin(SemanticQuestions.class, answer);
            }
            return answer;
        }
    }

    /**
     * Replace all definitions from one source; an empty map removes obsolete declarations. The Java fluent helper
     * reserves {@code java:} followed by the resource location (or a generated key for embedded builders). XML and YAML
     * declarations use the resource location as their source key. On a started context, new and changed declarations
     * are validated before publication, including unused evaluations. A validation failure preserves the previous
     * source.
     */
    public synchronized void replace(String source, Map<String, SemanticQuestion> definitions) {
        Map<String, SemanticQuestion> replacement = new HashMap<>();
        sources.forEach((location, entries) -> {
            if (!location.equals(source)) {
                replacement.putAll(entries);
            }
        });
        definitions.forEach((name, question) -> {
            if (name == null || name.isBlank() || question == null) {
                throw new IllegalArgumentException("Semantic question requires a name and definition");
            }
            if (replacement.putIfAbsent(name, question) != null) {
                throw new IllegalArgumentException("Duplicate semantic question: " + name);
            }
        });
        Map<String, SemanticQuestion> previous = candidate.get();
        candidate.set(Map.copyOf(replacement));
        try {
            if (context.isStarted() && !definitions.isEmpty()) {
                ((SemanticLanguage) context.resolveLanguage("semantic")).validateDeclarations(definitions);
            }
            validators.forEach((validator, names) -> {
                if (!Collections.disjoint(names, definitions.keySet())) {
                    Map<String, SemanticQuestion> selected = new LinkedHashMap<>();
                    names.forEach(name -> {
                        SemanticQuestion question = replacement.get(name);
                        // Removed declarations remain removable; their existing expressions fail if evaluated again.
                        if (question != null) {
                            selected.put(name, question);
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
        questions = Map.copyOf(replacement);
    }

    /** Track a route resource so deleted files can be discarded before development-mode reload. */
    public synchronized void replace(Resource source, Map<String, SemanticQuestion> definitions) {
        replace(source.getLocation(), source, definitions);
    }

    synchronized void replace(String location, Resource source, Map<String, SemanticQuestion> definitions) {
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

    /** Whether any named questions have been registered. */
    public boolean isEmpty() {
        return questions.isEmpty();
    }

    public SemanticQuestion get(String name) {
        SemanticQuestion question = snapshot().get(name);
        if (question == null) {
            throw new IllegalArgumentException("Unknown semantic question: " + name);
        }
        return question;
    }

    /** Resolve all requested names from one immutable snapshot, preserving reference order. */
    public Map<String, SemanticQuestion> get(List<String> names) {
        Map<String, SemanticQuestion> snapshot = snapshot();
        Map<String, SemanticQuestion> selected = new LinkedHashMap<>();
        for (String name : names) {
            SemanticQuestion question = snapshot.get(name);
            if (question == null) {
                throw new IllegalArgumentException("Unknown semantic question: " + name);
            }
            selected.put(name, question);
        }
        return Collections.unmodifiableMap(selected);
    }

    private Map<String, SemanticQuestion> snapshot() {
        Map<String, SemanticQuestion> validation = candidate.get();
        return validation != null ? validation : questions;
    }

}

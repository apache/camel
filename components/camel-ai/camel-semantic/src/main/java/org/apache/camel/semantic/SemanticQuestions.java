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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;

/** Context-local named questions, replaced atomically per source when a route resource is reloaded. */
public final class SemanticQuestions {
    private static final Object CREATION_LOCK = new Object();
    private final Map<String, Map<String, SemanticQuestion>> sources = new HashMap<>();
    private final Map<String, Resource> resources = new HashMap<>();
    private volatile Map<String, SemanticQuestion> questions = Map.of();

    public static SemanticQuestions get(CamelContext context) {
        synchronized (CREATION_LOCK) {
            SemanticQuestions answer = context.getCamelContextExtension().getContextPlugin(SemanticQuestions.class);
            if (answer == null) {
                answer = new SemanticQuestions();
                context.getCamelContextExtension().addContextPlugin(SemanticQuestions.class, answer);
            }
            return answer;
        }
    }

    /**
     * Replace all definitions from one source; an empty map removes obsolete declarations. Java/XML declarations
     * reserve the {@code model:} prefix. Resource declarations use {@code model:} followed by
     * {@link Resource#getLocation()}, while embedded Java builders use unique generated source keys.
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

    public SemanticQuestion get(String name) {
        SemanticQuestion question = questions.get(name);
        if (question == null) {
            throw new IllegalArgumentException("Unknown semantic question: " + name);
        }
        return question;
    }

    /** Resolve all requested names from one immutable snapshot, preserving reference order. */
    public Map<String, SemanticQuestion> get(List<String> names) {
        Map<String, SemanticQuestion> snapshot = questions;
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
}

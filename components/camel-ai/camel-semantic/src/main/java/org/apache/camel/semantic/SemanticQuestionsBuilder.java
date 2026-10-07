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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteBuilderLifecycleStrategy;
import org.apache.camel.spi.Resource;

/** Fluent declarations for use inside an ordinary {@link RouteBuilder#configure()}. */
public final class SemanticQuestionsBuilder {
    private static final String LIFECYCLE = SemanticQuestionsBuilder.class.getName();
    private String expert;
    private String state;
    private final CamelContext context;
    private final RouteBuilder builder;
    private final Resource resource;
    private final String source;
    private final Map<String, SemanticQuestionBuilder> questions = new LinkedHashMap<>();

    private SemanticQuestionsBuilder(RouteBuilder builder) {
        this(builder.getContext(), builder.getResource(), builder.getResource() == null
                ? "java:" + builder.getContext().getUuidGenerator().generateUuid()
                : "java:" + builder.getResource().getLocation(),
             builder);
    }

    SemanticQuestionsBuilder(CamelContext context, Resource resource, String source, RouteBuilder builder) {
        this.context = context;
        this.resource = resource;
        this.source = source;
        this.builder = builder;
    }

    /** Start one group of declarations, then call {@link #register()} before using its references. */
    public static SemanticQuestionsBuilder semanticQuestions(RouteBuilder builder) {
        return new SemanticQuestionsBuilder(builder);
    }

    public SemanticQuestionsBuilder expert(String expert) {
        this.expert = expert;
        return this;
    }

    public SemanticQuestionsBuilder state(String state) {
        this.state = state;
        return this;
    }

    String getExpert() {
        return expert;
    }

    String getState() {
        return state;
    }

    public SemanticQuestionBuilder evaluation(String name) {
        return question(name);
    }

    /** Add a named question. Names must be unique across the context. */
    public SemanticQuestionBuilder question(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Semantic question requires a nonblank name");
        }
        SemanticQuestionBuilder question = new SemanticQuestionBuilder(this);
        if (questions.putIfAbsent(name, question) != null) {
            throw new IllegalArgumentException("Duplicate semantic question: " + name);
        }
        return question;
    }

    /** Validate all declarations and atomically replace this source's questions. An empty group removes them. */
    public void register() {
        Map<String, SemanticQuestion> definitions = new LinkedHashMap<>();
        questions.forEach((name, question) -> {
            try {
                definitions.put(name, question.build(context));
            } catch (IllegalArgumentException e) {
                String expert = question.getExpert() != null ? question.getExpert() : "default/automatic";
                throw new IllegalArgumentException(
                        "Invalid semantic question '" + name + "': " + e.getMessage() + " (expert '" + expert + "')", e);
            }
        });
        SemanticQuestions registry = SemanticQuestions.get(context);
        synchronized (registry) {
            registry.replace(source, resource, definitions);
            DeclarationsLifecycle lifecycle = context.getRegistry().lookupByNameAndType(LIFECYCLE, DeclarationsLifecycle.class);
            if (lifecycle == null) {
                lifecycle = new DeclarationsLifecycle(registry);
                context.getRegistry().bind(LIFECYCLE, lifecycle);
            }
            if (builder != null) {
                lifecycle.registered.add(builder);
            }
        }
    }

    // A resource may remove the helper entirely on reload. The existing builder lifecycle detects that case.
    private static final class DeclarationsLifecycle implements RouteBuilderLifecycleStrategy {
        private final SemanticQuestions questions;
        private final Set<RouteBuilder> registered = Collections.newSetFromMap(new WeakHashMap<>());

        private DeclarationsLifecycle(SemanticQuestions questions) {
            this.questions = questions;
        }

        @Override
        public void afterConfigure(RouteBuilder builder) {
            synchronized (questions) {
                questions.removeDeletedResources();
                if (!registered.remove(builder) && builder.getResource() != null) {
                    questions.remove("java:" + builder.getResource().getLocation());
                }
            }
        }
    }
}

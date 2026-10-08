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
public final class SemanticEvaluationsBuilder {
    private static final String LIFECYCLE = SemanticEvaluationsBuilder.class.getName();
    private String expert;
    private String state;
    private final CamelContext context;
    private final RouteBuilder builder;
    private final Resource resource;
    private final String source;
    private final Map<String, SemanticEvaluationBuilder> evaluations = new LinkedHashMap<>();

    private SemanticEvaluationsBuilder(RouteBuilder builder) {
        this(builder.getContext(), builder.getResource(), builder.getResource() == null
                ? "java:" + builder.getContext().getUuidGenerator().generateUuid()
                : "java:" + builder.getResource().getLocation(),
             builder);
    }

    SemanticEvaluationsBuilder(CamelContext context, Resource resource, String source, RouteBuilder builder) {
        this.context = context;
        this.resource = resource;
        this.source = source;
        this.builder = builder;
    }

    /** Start one group of declarations, then call {@link #register()} before using its references. */
    public static SemanticEvaluationsBuilder semanticEvaluations(RouteBuilder builder) {
        return new SemanticEvaluationsBuilder(builder);
    }

    public SemanticEvaluationsBuilder expert(String expert) {
        this.expert = expert;
        return this;
    }

    public SemanticEvaluationsBuilder state(String state) {
        this.state = state;
        return this;
    }

    String getExpert() {
        return expert;
    }

    String getState() {
        return state;
    }

    /** Add a named evaluation. Names must be unique across the context. */
    public SemanticEvaluationBuilder evaluation(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Semantic evaluation requires a nonblank name");
        }
        SemanticEvaluationBuilder evaluation = new SemanticEvaluationBuilder(this);
        if (evaluations.putIfAbsent(name, evaluation) != null) {
            throw new IllegalArgumentException("Duplicate semantic evaluation: " + name);
        }
        return evaluation;
    }

    /** Validate all declarations and atomically replace this source's evaluations. An empty group removes them. */
    public void register() {
        Map<String, SemanticEvaluation> definitions = new LinkedHashMap<>();
        evaluations.forEach((name, evaluation) -> {
            try {
                definitions.put(name, evaluation.build(context));
            } catch (IllegalArgumentException e) {
                String expert = evaluation.getExpert() != null ? evaluation.getExpert() : "default/automatic";
                throw new IllegalArgumentException(
                        "Invalid semantic evaluation '" + name + "': " + e.getMessage() + " (expert '" + expert + "')", e);
            }
        });
        SemanticEvaluations registry = SemanticEvaluations.get(context);
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
        private final SemanticEvaluations evaluations;
        private final Set<RouteBuilder> registered = Collections.newSetFromMap(new WeakHashMap<>());

        private DeclarationsLifecycle(SemanticEvaluations evaluations) {
            this.evaluations = evaluations;
        }

        @Override
        public void afterConfigure(RouteBuilder builder) {
            synchronized (evaluations) {
                evaluations.removeDeletedResources();
                if (!registered.remove(builder) && builder.getResource() != null) {
                    evaluations.remove("java:" + builder.getResource().getLocation());
                }
            }
        }
    }
}

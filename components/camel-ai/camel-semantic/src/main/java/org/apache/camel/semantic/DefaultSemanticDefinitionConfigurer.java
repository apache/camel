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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.app.SemanticDefinition;
import org.apache.camel.model.app.SemanticQuestionDefinition;
import org.apache.camel.model.spi.SemanticDefinitionConfigurer;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.StringHelper;

/** Converts Java/XML declarations to the same immutable questions used by YAML and programmatic registration. */
public class DefaultSemanticDefinitionConfigurer implements SemanticDefinitionConfigurer {
    @Override
    public void configure(CamelContext context, Resource resource, String source, SemanticDefinition definition) {
        Map<String, SemanticQuestion> questions = new LinkedHashMap<>();
        if (definition != null) {
            for (SemanticQuestionDefinition question : definition.getQuestions()) {
                String name = question.getName();
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("Semantic question requires a nonblank name");
                }
                if (questions.containsKey(name)) {
                    throw new IllegalArgumentException("Duplicate semantic question: " + name);
                }
                try {
                    questions.put(name, question(context, question));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid semantic question '" + name + "': " + e.getMessage(), e);
                }
            }
        }
        // A YAML loader can register its own declarations from the same resource before its RouteBuilder runs.
        SemanticQuestions.get(context).replace("model:" + source, resource, questions);
    }

    @Override
    public SemanticDefinition getDefinition(CamelContext context) {
        SemanticQuestions questions = context.getCamelContextExtension().getContextPlugin(SemanticQuestions.class);
        if (questions == null) {
            return null;
        }
        SemanticDefinition definition = new SemanticDefinition();
        questions.snapshot().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            SemanticQuestion question = entry.getValue();
            SemanticQuestionDefinition target = definition.question(entry.getKey())
                    .type(question.getType().name().toLowerCase(Locale.ROOT))
                    .instructions(question.getInstructions()).state(question.getState());
            question.getCriteria().forEach(target::criterion);
            question.getLevels().forEach(target::level);
            if (question.getType() == SemanticQuestion.Type.BOOLEAN) {
                if (question.getThreshold() != 0.5) {
                    target.threshold(question.getThreshold());
                }
                if (question.getUncertainty() != 0) {
                    target.uncertainty(question.getUncertainty());
                }
                if (question.getUncertaintyPolicy() != SemanticQuestion.UncertaintyPolicy.FAIL) {
                    target.uncertaintyPolicy(question.getUncertaintyPolicy().name().toLowerCase(Locale.ROOT).replace('_', '-'));
                }
            }
        });
        return definition;
    }

    private static SemanticQuestion question(CamelContext context, SemanticQuestionDefinition definition) {
        if (definition.getType() == null) {
            throw new IllegalArgumentException("Question type is required");
        }
        SemanticQuestion.Type type = enumeration(definition.getType(), SemanticQuestion.Type.class);
        if (type != SemanticQuestion.Type.BOOLEAN
                && (definition.getThreshold() != null || definition.getUncertainty() != null
                        || definition.getUncertaintyPolicy() != null)) {
            throw new IllegalArgumentException("Threshold and uncertainty policy require a boolean question");
        }
        Map<String, String> criteria = new LinkedHashMap<>();
        for (PropertyDefinition criterion : definition.getCriteria()) {
            if (criteria.containsKey(criterion.getKey())) {
                throw new IllegalArgumentException("Duplicate semantic criterion: " + criterion.getKey());
            }
            criteria.put(criterion.getKey(), criterion.getValue());
        }
        return new SemanticQuestion(
                type, definition.getInstructions(), definition.getState(), criteria,
                definition.getLevels(),
                definition.getThreshold() == null ? 0.5 : parseDouble(context, definition.getThreshold(), "threshold"),
                definition.getUncertainty() == null ? 0 : parseDouble(context, definition.getUncertainty(), "uncertainty"),
                definition.getUncertaintyPolicy() == null
                        ? SemanticQuestion.UncertaintyPolicy.FAIL
                        : enumeration(definition.getUncertaintyPolicy(), SemanticQuestion.UncertaintyPolicy.class));
    }

    private static double parseDouble(CamelContext context, String value, String field) {
        try {
            return Double.parseDouble(context.resolvePropertyPlaceholders(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a valid number: " + value, e);
        }
    }

    private static <T extends Enum<T>> T enumeration(String value, Class<T> type) {
        String normalized = StringHelper.asEnumConstantValue(value);
        for (T constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value) || constant.name().equalsIgnoreCase(normalized)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Invalid " + type.getSimpleName() + ": " + value);
    }
}

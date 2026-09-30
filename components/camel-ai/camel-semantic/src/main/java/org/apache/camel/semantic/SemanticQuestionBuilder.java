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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.util.StringHelper;

/** Fluent definition of a named semantic question. */
public final class SemanticQuestionBuilder {
    private final SemanticQuestionsBuilder parent;
    private final Map<String, String> criteria = new LinkedHashMap<>();
    private final List<String> levels = new ArrayList<>();
    private String type;
    private String instructions;
    private String state;
    private String threshold;
    private String uncertainty;
    private String uncertaintyPolicy;

    SemanticQuestionBuilder(SemanticQuestionsBuilder parent) {
        this.parent = parent;
    }

    /** The question type: boolean, choice or score. */
    public SemanticQuestionBuilder type(String type) {
        this.type = type;
        return this;
    }

    /** The instructions sent to the provider. */
    public SemanticQuestionBuilder instructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    /** A Simple expression selecting the state to evaluate; defaults to the message body. */
    public SemanticQuestionBuilder state(String state) {
        this.state = state;
        return this;
    }

    /** Add a named choice, or a true/false criterion for a boolean question. */
    public SemanticQuestionBuilder criterion(String name, String description) {
        if (criteria.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate semantic criterion: " + name);
        }
        criteria.put(name, description);
        return this;
    }

    /** Add an ordered score level. */
    public SemanticQuestionBuilder level(String level) {
        levels.add(level);
        return this;
    }

    /** Boolean decision threshold; defaults to 0.5. */
    public SemanticQuestionBuilder threshold(double threshold) {
        return threshold(Double.toString(threshold));
    }

    /** Boolean decision threshold, optionally using property placeholders. */
    public SemanticQuestionBuilder threshold(String threshold) {
        this.threshold = threshold;
        return this;
    }

    /** Boolean uncertainty band; defaults to zero. */
    public SemanticQuestionBuilder uncertainty(double uncertainty) {
        return uncertainty(Double.toString(uncertainty));
    }

    /** Boolean uncertainty band, optionally using property placeholders. */
    public SemanticQuestionBuilder uncertainty(String uncertainty) {
        this.uncertainty = uncertainty;
        return this;
    }

    /** Boolean uncertainty policy: fail (default) or non-match. */
    public SemanticQuestionBuilder uncertaintyPolicy(String uncertaintyPolicy) {
        this.uncertaintyPolicy = uncertaintyPolicy;
        return this;
    }

    /** Return to the group to add another question. */
    public SemanticQuestionsBuilder end() {
        return parent;
    }

    /** Validate and register the entire group. */
    public void register() {
        parent.register();
    }

    SemanticQuestion build(CamelContext context) {
        if (type == null) {
            throw new IllegalArgumentException("Question type is required");
        }
        SemanticQuestion.Type questionType = enumeration(type, SemanticQuestion.Type.class);
        if (questionType != SemanticQuestion.Type.BOOLEAN
                && (threshold != null || uncertainty != null || uncertaintyPolicy != null)) {
            throw new IllegalArgumentException("Threshold and uncertainty policy require a boolean question");
        }
        return new SemanticQuestion(
                questionType, instructions, state, criteria, levels,
                threshold == null ? 0.5 : parseDouble(context, threshold, "threshold"),
                uncertainty == null ? 0 : parseDouble(context, uncertainty, "uncertainty"),
                uncertaintyPolicy == null
                        ? SemanticQuestion.UncertaintyPolicy.FAIL
                        : enumeration(uncertaintyPolicy, SemanticQuestion.UncertaintyPolicy.class));
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

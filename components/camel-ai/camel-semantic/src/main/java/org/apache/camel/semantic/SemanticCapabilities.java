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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.Instructions;
import org.apache.camel.semantic.SemanticExpert.ResultType;

/**
 * Immutable capabilities of a configured expert. Unknown legacy capabilities are not a claim of support. Providers may
 * override their static annotation with effective capabilities for the configured model. Probability/confidence flags
 * describe availability, not guarantees that every response includes optional metadata.
 */
public final class SemanticCapabilities {
    private final boolean known;
    private final String name;
    private final String description;
    private final String provider;
    private final String artifactId;
    private final Set<InputType> inputTypes;
    private final Set<ResultType> resultTypes;
    private final Instructions instructions;
    private final boolean callerDefinedCriteria;
    private final boolean booleanProbability;
    private final boolean choiceProbabilities;
    private final Set<ResultType> confidenceTypes;
    private final String probabilityMeaning;
    private final String confidenceMeaning;
    private final String trueMeaning;
    private final int maxChoices;
    private final int maxScoreLevels;

    private SemanticCapabilities(boolean known, Builder builder) {
        this.known = known;
        this.name = builder.name;
        this.description = builder.description;
        this.provider = builder.provider;
        this.artifactId = builder.artifactId;
        this.inputTypes = immutableSet(InputType.class, builder.inputTypes);
        this.resultTypes = immutableSet(ResultType.class, builder.resultTypes);
        this.instructions = Objects.requireNonNull(builder.instructions, "Instruction support is required");
        this.callerDefinedCriteria = builder.callerDefinedCriteria;
        this.booleanProbability = builder.booleanProbability;
        this.choiceProbabilities = builder.choiceProbabilities;
        this.confidenceTypes = immutableSet(ResultType.class, builder.confidenceTypes);
        this.probabilityMeaning = builder.probabilityMeaning;
        this.confidenceMeaning = builder.confidenceMeaning;
        this.trueMeaning = builder.trueMeaning;
        this.maxChoices = builder.maxChoices;
        this.maxScoreLevels = builder.maxScoreLevels;
    }

    private static <E extends Enum<E>> Set<E> immutableSet(Class<E> type, Set<E> values) {
        EnumSet<E> copy = EnumSet.noneOf(type);
        copy.addAll(values);
        return Collections.unmodifiableSet(copy);
    }

    /** Build explicit capabilities using named attributes. Empty input/result sets claim no support. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start an explicit capability declaration from this descriptor, for example to narrow a configured model's limits.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static SemanticCapabilities unknown() {
        return new SemanticCapabilities(false, new Builder());
    }

    /** Reads static capabilities without loading a model or transport. */
    public static SemanticCapabilities from(SemanticExpert expert) {
        if (expert == null) {
            return unknown();
        }
        return builder()
                .name(expert.name())
                .description(expert.description())
                .provider(expert.provider())
                .artifactId(expert.artifactId())
                .inputTypes(expert.inputTypes())
                .resultTypes(expert.resultTypes())
                .instructions(expert.instructions())
                .callerDefinedCriteria(expert.callerDefinedCriteria())
                .booleanProbability(expert.booleanProbability())
                .choiceProbabilities(expert.choiceProbabilities())
                .confidenceTypes(expert.confidenceTypes())
                .probabilityMeaning(expert.probabilityMeaning())
                .confidenceMeaning(expert.confidenceMeaning())
                .trueMeaning(expert.trueMeaning())
                .maxChoices(expert.maxChoices())
                .maxScoreLevels(expert.maxScoreLevels())
                .build();
    }

    public boolean isKnown() {
        return known;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getProvider() {
        return provider;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public Set<InputType> getInputTypes() {
        return inputTypes;
    }

    public Set<ResultType> getResultTypes() {
        return resultTypes;
    }

    public Instructions getInstructions() {
        return instructions;
    }

    public boolean isCallerDefinedCriteria() {
        return callerDefinedCriteria;
    }

    public boolean isBooleanProbability() {
        return booleanProbability;
    }

    public boolean isChoiceProbabilities() {
        return choiceProbabilities;
    }

    public Set<ResultType> getConfidenceTypes() {
        return confidenceTypes;
    }

    public String getProbabilityMeaning() {
        return probabilityMeaning;
    }

    public String getConfidenceMeaning() {
        return confidenceMeaning;
    }

    public String getTrueMeaning() {
        return trueMeaning;
    }

    public int getMaxChoices() {
        return maxChoices;
    }

    public int getMaxScoreLevels() {
        return maxScoreLevels;
    }

    /** Validate the common contract before the provider checks any model-specific restrictions. */
    public void validate(SemanticQuestion question) {
        if ((!known || instructions == Instructions.REQUIRED) && question.getInstructions() == null) {
            throw new IllegalArgumentException("Question instructions are required");
        }
        if (!known) {
            return;
        }
        if (!resultTypes.contains(ResultType.valueOf(question.getType().name()))) {
            throw new IllegalArgumentException(
                    "Requires " + question.getType() + "; supports " + resultTypes + ": " + description);
        }
        if (instructions == Instructions.UNSUPPORTED && question.getInstructions() != null) {
            throw new IllegalArgumentException("Instructions are unsupported by this fixed expert");
        }
        if (!callerDefinedCriteria && (!question.getCriteria().isEmpty() || !question.getLevels().isEmpty())) {
            throw new IllegalArgumentException("Caller-defined criteria and score levels are unsupported by this fixed expert");
        }
        if (question.getType() == SemanticQuestion.Type.CHOICE && maxChoices > 0
                && question.getCriteria().size() > maxChoices) {
            throw new IllegalArgumentException("Supports at most " + maxChoices + " choice criteria");
        }
        if (question.getType() == SemanticQuestion.Type.SCORE && maxScoreLevels > 0
                && question.getLevels().size() > maxScoreLevels) {
            throw new IllegalArgumentException("Supports at most " + maxScoreLevels + " score levels");
        }
        if (question.getType() == SemanticQuestion.Type.BOOLEAN && !booleanProbability
                && (question.getThreshold() != 0.5 || question.getUncertainty() != 0
                        || question.getUncertaintyPolicy() != SemanticQuestion.UncertaintyPolicy.FAIL)) {
            throw new IllegalArgumentException("Boolean decision policy requires positive-class probability support");
        }
    }

    /** Input shape can only be checked once the state selector has been evaluated. */
    public void validateInput(Object state) {
        if (known && !(state instanceof String && inputTypes.contains(InputType.TEXT)
                || (state instanceof Map<?, ?> || state instanceof List<?>) && inputTypes.contains(InputType.STRUCTURED))) {
            throw new IllegalArgumentException("Unsupported selected state; accepts " + inputTypes + " input");
        }
    }

    /** Named construction of an immutable, explicitly supported contract. */
    public static final class Builder {
        private String name;
        private String description;
        private String provider;
        private String artifactId;
        private Set<InputType> inputTypes = Set.of();
        private Set<ResultType> resultTypes = Set.of();
        private Instructions instructions = Instructions.REQUIRED;
        private boolean callerDefinedCriteria;
        private boolean booleanProbability;
        private boolean choiceProbabilities;
        private Set<ResultType> confidenceTypes = Set.of();
        private String probabilityMeaning;
        private String confidenceMeaning;
        private String trueMeaning;
        private int maxChoices;
        private int maxScoreLevels;

        private Builder() {
        }

        private Builder(SemanticCapabilities capabilities) {
            this.name = capabilities.name;
            this.description = capabilities.description;
            this.provider = capabilities.provider;
            this.artifactId = capabilities.artifactId;
            this.inputTypes = capabilities.inputTypes;
            this.resultTypes = capabilities.resultTypes;
            this.instructions = capabilities.instructions;
            this.callerDefinedCriteria = capabilities.callerDefinedCriteria;
            this.booleanProbability = capabilities.booleanProbability;
            this.choiceProbabilities = capabilities.choiceProbabilities;
            this.confidenceTypes = capabilities.confidenceTypes;
            this.probabilityMeaning = capabilities.probabilityMeaning;
            this.confidenceMeaning = capabilities.confidenceMeaning;
            this.trueMeaning = capabilities.trueMeaning;
            this.maxChoices = capabilities.maxChoices;
            this.maxScoreLevels = capabilities.maxScoreLevels;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder inputTypes(InputType... inputTypes) {
            this.inputTypes = EnumSet.noneOf(InputType.class);
            Collections.addAll(this.inputTypes, inputTypes);
            return this;
        }

        public Builder resultTypes(ResultType... resultTypes) {
            this.resultTypes = EnumSet.noneOf(ResultType.class);
            Collections.addAll(this.resultTypes, resultTypes);
            return this;
        }

        public Builder instructions(Instructions instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder callerDefinedCriteria(boolean callerDefinedCriteria) {
            this.callerDefinedCriteria = callerDefinedCriteria;
            return this;
        }

        public Builder booleanProbability(boolean booleanProbability) {
            this.booleanProbability = booleanProbability;
            return this;
        }

        public Builder choiceProbabilities(boolean choiceProbabilities) {
            this.choiceProbabilities = choiceProbabilities;
            return this;
        }

        public Builder confidenceTypes(ResultType... confidenceTypes) {
            this.confidenceTypes = EnumSet.noneOf(ResultType.class);
            Collections.addAll(this.confidenceTypes, confidenceTypes);
            return this;
        }

        public Builder probabilityMeaning(String probabilityMeaning) {
            this.probabilityMeaning = probabilityMeaning;
            return this;
        }

        public Builder confidenceMeaning(String confidenceMeaning) {
            this.confidenceMeaning = confidenceMeaning;
            return this;
        }

        public Builder trueMeaning(String trueMeaning) {
            this.trueMeaning = trueMeaning;
            return this;
        }

        public Builder maxChoices(int maxChoices) {
            this.maxChoices = maxChoices;
            return this;
        }

        public Builder maxScoreLevels(int maxScoreLevels) {
            this.maxScoreLevels = maxScoreLevels;
            return this;
        }

        public SemanticCapabilities build() {
            return new SemanticCapabilities(true, this);
        }
    }
}

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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;

/** Immutable static expert contract. It is read from the class, never supplied by a configured instance. */
public final class SemanticCapabilities {
    private static final ClassValue<SemanticCapabilities> CONTRACTS = new ClassValue<>() {
        @Override
        protected SemanticCapabilities computeValue(Class<?> type) {
            return from(type.getAnnotation(SemanticExpert.class));
        }
    };
    private final String name;
    private final String description;
    private final String provider;
    private final String artifactId;
    private final Map<String, Operation> operations;

    private SemanticCapabilities(SemanticExpert expert) {
        if (expert == null) {
            throw new IllegalArgumentException("Semantic adapter requires a @SemanticExpert contract");
        }
        name = required(expert.name(), "Expert name");
        description = required(expert.description(), "Expert description");
        provider = required(expert.provider(), "Expert provider");
        artifactId = required(expert.artifactId(), "Expert artifactId");
        Map<String, Operation> values = new LinkedHashMap<>();
        for (SemanticOperation operation : expert.operations()) {
            Operation contract = new Operation(operation);
            if (values.putIfAbsent(contract.getName(), contract) != null) {
                throw new IllegalArgumentException("Duplicate semantic operation: " + contract.getName());
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Semantic expert must declare operations");
        }
        operations = Collections.unmodifiableMap(values);
    }

    public static SemanticCapabilities from(Class<?> expertClass) {
        return CONTRACTS.get(expertClass);
    }

    public static SemanticCapabilities from(SemanticExpert expert) {
        return new SemanticCapabilities(expert);
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

    public Map<String, Operation> getOperations() {
        return operations;
    }

    public Operation operation(String name) {
        Operation result = operations.get(name);
        if (result == null) {
            throw new IllegalArgumentException(
                    "Unknown operation '" + name + "'; supported operations: " + operations.keySet());
        }
        return result;
    }

    public void validate(SemanticEvaluation evaluation) {
        operation(evaluation.getOperation()).validate(evaluation.getParameters());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static boolean withinBounds(Number number, double minimum, double maximum) {
        if (minimum == Double.POSITIVE_INFINITY || maximum == Double.NEGATIVE_INFINITY) {
            return false;
        }
        try {
            // Preserve decimal precision, including values just outside a declared boundary.
            BigDecimal value = new BigDecimal(number.toString());
            return (minimum == Double.NEGATIVE_INFINITY || value.compareTo(BigDecimal.valueOf(minimum)) >= 0)
                    && (maximum == Double.POSITIVE_INFINITY || value.compareTo(BigDecimal.valueOf(maximum)) <= 0);
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    /** Operation metadata and common validation, independent of any expert instance. */
    public static final class Operation {
        private final SemanticOperation declaration;
        private final Set<InputType> inputTypes;
        private final Set<String> labels;
        private final Map<String, Parameter> parameters;

        private Operation(SemanticOperation declaration) {
            this.declaration = declaration;
            required(declaration.name(), "Operation name");
            required(declaration.description(), "Operation description");
            required(declaration.inputRequirements(), "Operation input requirements");
            required(declaration.resultMeaning(), "Operation result meaning");
            inputTypes = Set.copyOf(List.of(declaration.inputTypes()));
            labels = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(declaration.labels())));
            if (inputTypes.isEmpty() || Double.isNaN(declaration.minimum()) || Double.isNaN(declaration.maximum())
                    || declaration.minimum() > declaration.maximum() || labels.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Invalid operation contract: " + declaration.name());
            }
            if (declaration.probability() || declaration.probabilities()) {
                required(declaration.probabilityMeaning(), "Probability meaning");
            }
            if (declaration.confidence()) {
                required(declaration.confidenceMeaning(), "Confidence meaning");
            }
            Map<String, Parameter> values = new LinkedHashMap<>();
            for (SemanticParameter parameter : declaration.parameters()) {
                Parameter contract = new Parameter(parameter);
                if (values.putIfAbsent(contract.getName(), contract) != null) {
                    throw new IllegalArgumentException("Duplicate parameter: " + contract.getName());
                }
            }
            parameters = Collections.unmodifiableMap(values);
            if (!declaration.scoreLevelsParameter().isEmpty()) {
                Parameter levels = parameters.get(declaration.scoreLevelsParameter());
                if (declaration.resultType() != ResultType.SCORE || levels == null
                        || levels.getType() != List.class || levels.getItemType() != String.class) {
                    throw new IllegalArgumentException(
                            "Score levels require a SCORE operation and a declared List<String> parameter: "
                                                       + declaration.name());
                }
            }
        }

        public String getName() {
            return declaration.name();
        }

        public String getDescription() {
            return declaration.description();
        }

        public Set<InputType> getInputTypes() {
            return inputTypes;
        }

        public String getInputRequirements() {
            return declaration.inputRequirements();
        }

        public Map<String, Parameter> getParameters() {
            return parameters;
        }

        public ResultType getResultType() {
            return declaration.resultType();
        }

        public String getResultMeaning() {
            return declaration.resultMeaning();
        }

        public Set<String> getLabels() {
            return labels;
        }

        /** The parameter defining zero-based score levels, or empty when the expert declares no relationship. */
        public String getScoreLevelsParameter() {
            return declaration.scoreLevelsParameter();
        }

        public double getMinimum() {
            return declaration.minimum();
        }

        public double getMaximum() {
            return declaration.maximum();
        }

        public boolean isProbability() {
            return declaration.probability();
        }

        public boolean isProbabilities() {
            return declaration.probabilities();
        }

        public String getProbabilityMeaning() {
            return declaration.probabilityMeaning();
        }

        public boolean isConfidence() {
            return declaration.confidence();
        }

        public String getConfidenceMeaning() {
            return declaration.confidenceMeaning();
        }

        public void validate(Map<String, Object> values) {
            for (String name : values.keySet()) {
                if (!parameters.containsKey(name)) {
                    throw new IllegalArgumentException("Unknown parameter '" + name + "' for operation '" + getName() + "'");
                }
            }
            parameters.forEach((name, parameter) -> parameter.validate(values.get(name), values.containsKey(name)));
        }

        public void validateInput(Object state) {
            if (!(state instanceof String && inputTypes.contains(InputType.TEXT)
                    || (state instanceof Map<?, ?> || state instanceof List<?>) && inputTypes.contains(InputType.STRUCTURED))) {
                throw new IllegalArgumentException("Unsupported selected state; accepts " + inputTypes + " input");
            }
        }

        /** Validate the typed answer; decision policy has already been applied by the expert. */
        public Object validateResult(SemanticResult result) {
            if (result == null) {
                throw new IllegalArgumentException("Missing semantic result");
            }
            Object value = result.getValue();
            boolean valid = switch (getResultType()) {
                case BOOLEAN -> value instanceof Boolean;
                case CHOICE -> value instanceof String text && !text.isBlank() && (labels.isEmpty() || labels.contains(text));
                case SCORE -> value instanceof Number number && withinBounds(number, getMinimum(), getMaximum());
                case CLASSIFICATION ->
                    value instanceof Set<?> set && set.stream().allMatch(label -> label instanceof String text
                            && !text.isBlank() && (labels.isEmpty() || labels.contains(text)));
            };
            if (!valid) {
                throw new IllegalArgumentException("Semantic result does not match " + getResultType() + " contract");
            }
            if (result.getProbability() != null && !isProbability()
                    || !result.getProbabilities().isEmpty() && !isProbabilities()
                    || result.getConfidence() != null && !isConfidence()) {
                throw new IllegalArgumentException("Semantic result contains undeclared probability or confidence information");
            }
            if (!labels.isEmpty() && !labels.containsAll(result.getProbabilities().keySet())) {
                throw new IllegalArgumentException("Semantic result probabilities contain unknown labels");
            }
            return value;
        }
    }

    /** Immutable parameter metadata. Values are checked without coercion or inclusion in diagnostics. */
    public static final class Parameter {
        private final SemanticParameter declaration;
        private final Set<String> values;

        private Parameter(SemanticParameter declaration) {
            this.declaration = declaration;
            Set<Class<?>> types = Set.of(String.class, Boolean.class, Number.class, Map.class, List.class);
            if (!types.contains(declaration.type())
                    || declaration.itemType() != Object.class && !types.contains(declaration.itemType())) {
                throw new IllegalArgumentException(
                        "Parameter types must be String, Boolean, Number, Map or List; use Number for numeric values");
            }
            if (declaration.integer() && declaration.type() != Number.class) {
                throw new IllegalArgumentException("Integer constraint requires a Number parameter");
            }
            required(declaration.name(), "Parameter name");
            required(declaration.description(), "Parameter description");
            if (!declaration.required()) {
                required(declaration.omission(), "Parameter omission behaviour");
            }
            if (Double.isNaN(declaration.minimum()) || Double.isNaN(declaration.maximum())
                    || declaration.minimum() > declaration.maximum()
                    || declaration.minSize() < 0 || declaration.minSize() > declaration.maxSize()) {
                throw new IllegalArgumentException("Invalid constraints for parameter '" + declaration.name() + "'");
            }
            values = Set.copyOf(List.of(declaration.values()));
            if (!values.isEmpty() && declaration.type() != String.class) {
                throw new IllegalArgumentException("Allowed values require a String parameter");
            }
        }

        public String getName() {
            return declaration.name();
        }

        public String getDescription() {
            return declaration.description();
        }

        public Class<?> getType() {
            return declaration.type();
        }

        public boolean isInteger() {
            return declaration.integer();
        }

        public Class<?> getItemType() {
            return declaration.itemType();
        }

        public boolean isRequired() {
            return declaration.required();
        }

        public String getOmission() {
            return declaration.omission();
        }

        public double getMinimum() {
            return declaration.minimum();
        }

        public double getMaximum() {
            return declaration.maximum();
        }

        public int getMinSize() {
            return declaration.minSize();
        }

        public int getMaxSize() {
            return declaration.maxSize();
        }

        public Set<String> getValues() {
            return values;
        }

        private void validate(Object value, boolean present) {
            if (!present) {
                if (isRequired()) {
                    throw invalid("is required");
                }
                return;
            }
            if (value == null) {
                throw invalid("must not be null; omit it to use the expert's omission behavior");
            }
            if (!getType().isInstance(value)) {
                throw invalid("must be " + getType().getSimpleName());
            }
            if (value instanceof Number number && !withinBounds(number, getMinimum(), getMaximum())) {
                throw invalid("is outside its numeric constraints");
            }
            if (isInteger() && new BigDecimal(value.toString()).stripTrailingZeros().scale() > 0) {
                throw invalid("must be an integer");
            }
            int size = -1;
            if (value instanceof String text) {
                size = text.strip().length();
            } else if (value instanceof Map<?, ?> map) {
                size = map.size();
                map.values().forEach(this::validateItem);
            } else if (value instanceof List<?> list) {
                size = list.size();
                list.forEach(this::validateItem);
            }
            if (size >= 0 && (size < getMinSize() || size > getMaxSize())) {
                throw invalid("is outside its size constraints");
            }
            if (!values.isEmpty() && !values.contains(value)) {
                throw invalid("is not an allowed value");
            }
        }

        private void validateItem(Object value) {
            if (getItemType() != Object.class && !getItemType().isInstance(value)) {
                throw invalid("items must be " + getItemType().getSimpleName());
            }
        }

        private IllegalArgumentException invalid(String reason) {
            return new IllegalArgumentException("Parameter '" + getName() + "' " + reason);
        }
    }
}

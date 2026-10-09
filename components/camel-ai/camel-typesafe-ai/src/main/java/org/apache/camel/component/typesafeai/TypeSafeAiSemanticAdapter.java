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
package org.apache.camel.component.typesafeai;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticExpert;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.semantic.SemanticOperation;
import org.apache.camel.semantic.SemanticParameter;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.json.JsonObject;

/** Maps semantic evaluations to TypeSafe AI using the component's configured, managed transport. */
@JdkService("semantic-adapter")
@SemanticExpert(name = "typesafe-ai", provider = "typesafe-ai", artifactId = "camel-typesafe-ai",
                description = "Instruction-driven decisions using TypeSafe AI",
                operations = {
                        @SemanticOperation(name = "boolean", description = "Instruction-driven boolean evaluation",
                                           inputTypes = { InputType.TEXT, InputType.STRUCTURED },
                                           inputRequirements = "Text or structured application state",
                                           resultType = ResultType.BOOLEAN,
                                           resultMeaning = "True means the supplied instructions and criteria hold",
                                           probability = true,
                                           probabilityMeaning = "Probability that the supplied instructions and criteria hold",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Instructions to evaluate against the state",
                                                                      required = true, minSize = 1),
                                                   @SemanticParameter(name = "criteria",
                                                                      description = "Descriptions of true and false",
                                                                      type = Map.class, itemType = String.class,
                                                                      omission = "Use the instructions"),
                                                   @SemanticParameter(name = "threshold",
                                                                      description = "Inclusive positive probability threshold",
                                                                      type = Number.class, minimum = 0, maximum = 1,
                                                                      omission = "Use 0.5"),
                                                   @SemanticParameter(name = "uncertainty",
                                                                      description = "Half-width of the uncertainty band around the threshold",
                                                                      type = Number.class, minimum = 0, maximum = 1,
                                                                      omission = "No uncertainty band"),
                                                   @SemanticParameter(name = "uncertaintyPolicy",
                                                                      description = "Behaviour inside the uncertainty band",
                                                                      values = { "fail", "non-match" },
                                                                      omission = "Fail the evaluation") }),
                        @SemanticOperation(name = "choice", description = "Instruction-driven choice evaluation",
                                           inputTypes = { InputType.TEXT, InputType.STRUCTURED },
                                           inputRequirements = "Text or structured application state",
                                           resultType = ResultType.CHOICE, resultMeaning = "One of the supplied category keys",
                                           probabilities = true, probabilityMeaning = "Probability of each supplied category",
                                           confidence = true,
                                           confidenceMeaning = "Optional confidence reported by the provider",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Instructions to evaluate against the state",
                                                                      required = true, minSize = 1),
                                                   @SemanticParameter(name = "criteria", description = "Named categories",
                                                                      type = Map.class, itemType = String.class,
                                                                      required = true, minSize = 1, maxSize = 255) }),
                        @SemanticOperation(name = "score", description = "Instruction-driven score evaluation",
                                           inputTypes = { InputType.TEXT, InputType.STRUCTURED },
                                           inputRequirements = "Text or structured application state",
                                           resultType = ResultType.SCORE,
                                           resultMeaning = "A score from zero to the number of supplied levels minus one",
                                           minimum = 0, maximum = 9, scoreLevelsParameter = "criteria", probabilities = true,
                                           probabilityMeaning = "Probability of each supplied score level",
                                           confidence = true,
                                           confidenceMeaning = "Optional confidence reported by the provider",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Instructions to evaluate against the state",
                                                                      required = true, minSize = 1),
                                                   @SemanticParameter(name = "criteria", description = "Ordered score levels",
                                                                      type = List.class, itemType = String.class,
                                                                      required = true, minSize = 1, maxSize = 10) }) })
public class TypeSafeAiSemanticAdapter implements SemanticAdapter, CamelContextAware {
    private CamelContext camelContext;
    private volatile TypeSafeAiEndpoint endpoint;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void validate(SemanticEvaluation question) {
        SemanticCapabilities.from(getClass()).validate(question);
        if (criteria(question).entrySet().stream().anyMatch(entry -> entry.getKey().isBlank() || entry.getValue().isBlank())
                || levels(question).stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Parameter 'criteria' requires nonblank names and descriptions");
        }
        if ("boolean".equals(question.getOperation())) {
            if (criteria(question).keySet().stream().anyMatch(key -> !key.equals("true") && !key.equals("false"))) {
                throw new IllegalArgumentException("Parameter 'criteria' requires true or false keys");
            }
            double threshold = threshold(question);
            double uncertainty = uncertainty(question);
            if (threshold - uncertainty < 0 || threshold + uncertainty > 1) {
                throw new IllegalArgumentException("Parameters 'threshold' and 'uncertainty' must keep the band within [0,1]");
            }
        }
    }

    private TypeSafeAiEndpoint endpoint() {
        if (endpoint == null) {
            synchronized (this) {
                if (endpoint == null) {
                    if (camelContext == null) {
                        throw new IllegalStateException("TypeSafe AI semantic adapter requires a CamelContext");
                    }
                    endpoint = camelContext.getEndpoint("typesafe-ai:semantic", TypeSafeAiEndpoint.class);
                }
            }
        }
        return endpoint;
    }

    @Override
    public SemanticResult evaluate(SemanticEvaluation question, Object state) throws Exception {
        return evaluateBatch(Map.of("question", question), state).get("question");
    }

    @Override
    public Map<String, SemanticResult> evaluateBatch(Map<String, SemanticEvaluation> questions, Object state) throws Exception {
        Map<String, Object> definitions = new LinkedHashMap<>();
        questions.forEach((name, question) -> definitions.put(name, definition(question)));
        JsonObject response = endpoint().evaluate(Map.of("state", state, "questions", definitions));
        Map<String, SemanticResult> results = new LinkedHashMap<>();
        questions.forEach((name, question) -> results.put(name,
                result(question, response.getJsonObject("answers").getJsonObject(name), response)));
        return results;
    }

    private Map<String, Object> definition(SemanticEvaluation question) {
        validate(question);
        Map<String, Object> definition = new HashMap<>();
        definition.put("instructions", question.getParameters().get("instructions"));
        definition.put("type", type(question));
        if ("score".equals(question.getOperation())) {
            definition.put("criteria", levels(question));
        } else if (!criteria(question).isEmpty()) {
            definition.put("criteria", criteria(question));
        }
        return definition;
    }

    private String type(SemanticEvaluation question) {
        return switch (question.getOperation()) {
            case "boolean" -> "noul";
            case "choice" -> "choice";
            case "score" -> "score";
            default -> throw new IllegalArgumentException("Unsupported TypeSafe AI operation");
        };
    }

    private SemanticResult result(SemanticEvaluation question, JsonObject answer, JsonObject response) {
        Map<String, Double> probabilities = new HashMap<>();
        if (answer.get("probabilities") instanceof Map<?, ?> values) {
            values.forEach((key, value) -> probabilities.put((String) key, ((Number) value).doubleValue()));
        }
        Double probability = "boolean".equals(question.getOperation()) ? answer.getDouble("noul") : null;
        Object value = switch (question.getOperation()) {
            case "boolean" -> booleanDecision(question, probability);
            case "score" -> answer.getDouble("score");
            default -> answer.get(type(question));
        };
        SemanticResult result = new SemanticResult(
                value,
                probability,
                probabilities, answer.get("confidence") == null ? null : answer.getDouble("confidence"),
                Map.of("provider", "typesafe-ai", "model", response.get("model"), "usage", response.get("usage")));
        SemanticCapabilities.from(getClass()).operation(question.getOperation()).validateResult(result);
        if ("choice".equals(question.getOperation())
                && (!criteria(question).containsKey(value)
                        || !probabilities.isEmpty() && !probabilities.keySet().equals(criteria(question).keySet()))) {
            throw new IllegalArgumentException("TypeSafe AI choice result must match the supplied criteria");
        }
        if ("score".equals(question.getOperation())
                && ((Number) value).doubleValue() > levels(question).size() - 1) {
            throw new IllegalArgumentException("TypeSafe AI score result must be within the supplied levels");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> criteria(SemanticEvaluation evaluation) {
        return evaluation.getParameters().get("criteria") instanceof Map<?, ?> map
                ? (Map<String, String>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> levels(SemanticEvaluation evaluation) {
        return evaluation.getParameters().get("criteria") instanceof List<?> list
                ? (List<String>) list : List.of();
    }

    private static double threshold(SemanticEvaluation question) {
        return ((Number) question.getParameters().getOrDefault("threshold", 0.5)).doubleValue();
    }

    private static double uncertainty(SemanticEvaluation question) {
        return ((Number) question.getParameters().getOrDefault("uncertainty", 0.0)).doubleValue();
    }

    private static boolean booleanDecision(SemanticEvaluation question, double probability) {
        double threshold = threshold(question);
        double uncertainty = uncertainty(question);
        if (uncertainty > 0 && probability >= threshold - uncertainty && probability <= threshold + uncertainty) {
            if (!"non-match".equals(question.getParameters().get("uncertaintyPolicy"))) {
                throw new IllegalStateException("Semantic boolean decision is uncertain");
            }
            return false;
        }
        return probability >= threshold;
    }
}

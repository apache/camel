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
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticExpert;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.Instructions;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.json.JsonObject;

/** Maps common questions to TypeSafe AI using the component's configured, managed transport. */
@JdkService("semantic-adapter")
@SemanticExpert(name = "typesafe-ai", provider = "typesafe-ai", artifactId = "camel-typesafe-ai",
                description = "Instruction-driven boolean decisions, choices and rubric scores using TypeSafe AI",
                inputTypes = { InputType.TEXT, InputType.STRUCTURED },
                resultTypes = { ResultType.BOOLEAN, ResultType.CHOICE, ResultType.SCORE },
                instructions = Instructions.REQUIRED, callerDefinedCriteria = true,
                booleanProbability = true, choiceProbabilities = true,
                confidenceTypes = { ResultType.BOOLEAN, ResultType.CHOICE, ResultType.SCORE },
                probabilityMeaning = "Boolean: probability of true; choice: probability of each supplied category",
                confidenceMeaning = "Optional provider-reported confidence; distinct from the normalized decision",
                trueMeaning = "The supplied boolean instructions and criteria hold for the selected state",
                maxChoices = 255, maxScoreLevels = 10)
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
    public void validate(SemanticQuestion question) {
        capabilities().validate(question);
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
    public SemanticResult evaluate(SemanticQuestion question, Object state) throws Exception {
        return evaluateBatch(Map.of("question", question), state).get("question");
    }

    @Override
    public Map<String, SemanticResult> evaluateBatch(Map<String, SemanticQuestion> questions, Object state) throws Exception {
        Map<String, Object> definitions = new LinkedHashMap<>();
        questions.forEach((name, question) -> definitions.put(name, definition(question)));
        JsonObject response = endpoint().evaluate(Map.of("state", state, "questions", definitions));
        Map<String, SemanticResult> results = new LinkedHashMap<>();
        questions.forEach((name, question) -> results.put(name,
                result(question, response.getJsonObject("answers").getJsonObject(name), response)));
        return results;
    }

    private Map<String, Object> definition(SemanticQuestion question) {
        validate(question);
        Map<String, Object> definition = new HashMap<>();
        definition.put("instructions", question.getInstructions());
        definition.put("type", type(question));
        if (question.getType() == SemanticQuestion.Type.SCORE) {
            definition.put("criteria", question.getLevels());
        } else if (!question.getCriteria().isEmpty()) {
            definition.put("criteria", question.getCriteria());
        }
        return definition;
    }

    private String type(SemanticQuestion question) {
        return switch (question.getType()) {
            case BOOLEAN -> "noul";
            case CHOICE -> "choice";
            case SCORE -> "score";
        };
    }

    private SemanticResult result(SemanticQuestion question, JsonObject answer, JsonObject response) {
        Map<String, Double> probabilities = new HashMap<>();
        if (answer.get("probabilities") instanceof Map<?, ?> values) {
            values.forEach((key, value) -> probabilities.put((String) key, ((Number) value).doubleValue()));
        }
        return new SemanticResult(
                question.getType() == SemanticQuestion.Type.BOOLEAN ? null : answer.get(type(question)),
                question.getType() == SemanticQuestion.Type.BOOLEAN ? answer.getDouble("noul") : null,
                probabilities, answer.get("confidence") == null ? null : answer.getDouble("confidence"),
                Map.of("provider", "typesafe-ai", "model", response.get("model"), "usage", response.get("usage")));
    }
}

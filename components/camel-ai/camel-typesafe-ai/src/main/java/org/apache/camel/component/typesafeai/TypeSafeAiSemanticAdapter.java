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
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.util.json.JsonObject;

/** Maps common questions to TypeSafe AI using the component's configured, managed transport. */
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
        if (question.getType() == SemanticQuestion.Type.CHOICE && question.getCriteria().size() > 255
                || question.getType() == SemanticQuestion.Type.SCORE && question.getLevels().size() > 10) {
            throw new IllegalArgumentException("TypeSafe AI supports at most 255 choice criteria or 10 score levels");
        }
        if (endpoint == null) {
            synchronized (this) {
                if (endpoint == null) {
                    endpoint = camelContext.getEndpoint("typesafe-ai:semantic", TypeSafeAiEndpoint.class);
                }
            }
        }
    }

    @Override
    public SemanticResult evaluate(SemanticQuestion question, Object state) throws Exception {
        Map<String, Object> definition = new HashMap<>();
        definition.put("instructions", question.getInstructions());
        String type = switch (question.getType()) {
            case BOOLEAN -> "noul";
            case CHOICE -> "choice";
            case SCORE -> "score";
        };
        definition.put("type", type);
        if (question.getType() == SemanticQuestion.Type.SCORE) {
            definition.put("criteria", question.getLevels());
        } else if (!question.getCriteria().isEmpty()) {
            definition.put("criteria", question.getCriteria());
        }
        JsonObject response = endpoint.evaluate(Map.of("state", state, "questions", Map.of("question", definition)));
        JsonObject answer = response.getJsonObject("answers").getJsonObject("question");
        Map<String, Double> probabilities = new HashMap<>();
        if (answer.get("probabilities") instanceof Map<?, ?> values) {
            values.forEach((key, value) -> probabilities.put((String) key, ((Number) value).doubleValue()));
        }
        return new SemanticResult(
                question.getType() == SemanticQuestion.Type.BOOLEAN ? null : answer.get(type),
                question.getType() == SemanticQuestion.Type.BOOLEAN ? answer.getDouble("noul") : null,
                probabilities, answer.get("confidence") == null ? null : answer.getDouble("confidence"),
                Map.of("provider", "typesafe-ai", "model", response.get("model"), "usage", response.get("usage")));
    }
}

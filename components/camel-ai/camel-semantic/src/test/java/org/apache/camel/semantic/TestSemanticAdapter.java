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

import java.util.List;
import java.util.Map;

import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.support.service.ServiceSupport;

@SemanticExpert(name = "fixture", provider = "test", artifactId = "test",
                description = "Deterministic instruction-driven test expert",
                operations = {
                        @SemanticOperation(name = "boolean", description = "Instruction-driven boolean evaluation",
                                           inputTypes = { InputType.TEXT, InputType.STRUCTURED },
                                           inputRequirements = "Text or structured application state",
                                           resultType = ResultType.BOOLEAN,
                                           resultMeaning = "True means the supplied instructions and criteria hold",
                                           probability = true,
                                           probabilityMeaning = "Probability that the supplied instructions and criteria hold",
                                           confidence = true,
                                           confidenceMeaning = "Optional confidence reported by the provider",
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
                                           minimum = 0, maximum = 9,
                                           confidence = true,
                                           confidenceMeaning = "Optional confidence reported by the provider",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Instructions to evaluate against the state",
                                                                      required = true, minSize = 1),
                                                   @SemanticParameter(name = "criteria", description = "Ordered score levels",
                                                                      type = List.class, itemType = String.class,
                                                                      required = true, minSize = 1, maxSize = 10) }) })
abstract class TestSemanticAdapter extends ServiceSupport implements SemanticAdapter {
    @Override
    public void validate(SemanticEvaluation evaluation) {
        SemanticCapabilities.from(getClass()).validate(evaluation);
    }

    // This fixture models an expert that owns its probability policy.
    static SemanticResult applyPolicy(SemanticEvaluation evaluation, SemanticResult result) {
        if (!"boolean".equals(evaluation.getOperation()) || result.getProbability() == null) {
            return result;
        }
        double probability = result.getProbability();
        double threshold = ((Number) evaluation.getParameters().getOrDefault("threshold", 0.5)).doubleValue();
        double uncertainty = ((Number) evaluation.getParameters().getOrDefault("uncertainty", 0.0)).doubleValue();
        boolean value = probability >= threshold;
        if (uncertainty > 0 && probability >= threshold - uncertainty && probability <= threshold + uncertainty) {
            if (!"non-match".equals(evaluation.getParameters().get("uncertaintyPolicy"))) {
                throw new IllegalStateException("Semantic boolean decision is uncertain");
            }
            value = false;
        }
        return new SemanticResult(value, probability, result.getProbabilities(), result.getConfidence(), result.getMetadata());
    }
}

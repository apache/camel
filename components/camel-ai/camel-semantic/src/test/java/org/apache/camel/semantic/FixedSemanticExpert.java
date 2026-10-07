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
import java.util.List;
import java.util.Map;

import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;

@SemanticExpert(name = "fixed", description = "Fixed detection", provider = "test", artifactId = "test",
                operations = @SemanticOperation(name = "boolean", description = "Detect injection",
                                                inputTypes = InputType.TEXT, inputRequirements = "A text message",
                                                resultType = ResultType.BOOLEAN,
                                                resultMeaning = "True means injection detected",
                                                probability = true, probabilityMeaning = "Probability of injection",
                                                parameters = {
                                                        @SemanticParameter(name = "threshold",
                                                                           description = "Detection threshold",
                                                                           type = Number.class,
                                                                           minimum = 0, maximum = 1, omission = "Use 0.5"),
                                                        @SemanticParameter(name = "uncertainty",
                                                                           description = "Uncertainty band",
                                                                           type = Number.class,
                                                                           minimum = 0, maximum = 1, omission = "No band"),
                                                        @SemanticParameter(name = "uncertaintyPolicy",
                                                                           description = "Uncertainty policy",
                                                                           values = { "fail", "non-match" },
                                                                           omission = "Fail") }))
class FixedSemanticExpert implements SemanticAdapter {
    double probability = 0.9;
    boolean fail;
    int calls;
    final List<List<String>> batches = new ArrayList<>();
    final List<Object> states = new ArrayList<>();

    @Override
    public void validate(SemanticQuestion question) {
        SemanticCapabilities.from(getClass()).validate(question);
    }

    @Override
    public SemanticResult evaluate(SemanticQuestion question, Object state) {
        calls++;
        states.add(state);
        if (fail) {
            throw new IllegalStateException("provider unavailable");
        }
        return TestSemanticAdapter.applyPolicy(question,
                new SemanticResult(null, probability, null, null, Map.of("provider", "fixed")));
    }

    @Override
    public Map<String, SemanticResult> evaluateBatch(Map<String, SemanticQuestion> questions, Object state)
            throws Exception {
        batches.add(List.copyOf(questions.keySet()));
        return SemanticAdapter.super.evaluateBatch(questions, state);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FixedSemanticExpert;
    }

    @Override
    public int hashCode() {
        return 1;
    }
}

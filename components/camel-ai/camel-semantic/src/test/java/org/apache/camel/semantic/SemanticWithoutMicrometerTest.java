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

import java.util.Map;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticWithoutMicrometerTest {
    @Test
    void evaluationWorksWithoutMicrometerOnTheModuleClasspath() throws Exception {
        assertThatThrownBy(() -> Class.forName("io.micrometer.core.instrument.MeterRegistry"))
                .isInstanceOf(ClassNotFoundException.class);
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getRegistry().bind("expert", new BooleanExpert());
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            assertThat(language.evaluate(new SemanticEvaluation("containsText", "expert", null, Map.of()), "hello")
                    .getValue()).isEqualTo(true);
        }
    }

    @SemanticExpert(name = "text", description = "Checks for nonempty text", provider = "local", artifactId = "test",
                    operations = @SemanticOperation(name = "containsText", description = "Has text",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.BOOLEAN, resultMeaning = "Contains text"))
    static class BooleanExpert implements SemanticAdapter {
        @Override
        public void validate(SemanticEvaluation evaluation) {
            SemanticCapabilities.from(getClass()).validate(evaluation);
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            return new SemanticResult(!state.toString().isBlank(), null, Map.of(), null, Map.of());
        }
    }
}

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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticPropertiesTest {
    @ParameterizedTest
    @ValueSource(strings = { "classifier", "{{expert.name}}" })
    void camelMainBindsDefaultStateAndExplicitExpertPlaceholders(String expert) throws Exception {
        Main main = new Main();
        SemanticLanguageTest.LabelAdapter bean = new SemanticLanguageTest.LabelAdapter();
        main.bind("classifier", bean);
        main.addProperty("expert.name", "classifier");
        main.addProperty("camel.language.semantic.default-state", "${header.selected}");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            public void configure() {
                SemanticEvaluations.get(getContext()).replace("test", Map.of("q",
                        new SemanticEvaluation(
                                "choice", expert, null, Map.of("instructions", "Which department?",
                                        "criteria", Map.of("billing", "Payments", "technical", "Bugs")))));
                from("direct:start").setHeader("answer").language("semantic", "ref:q");
            }
        });
        try {
            main.start();
            try (var template = main.getCamelContext().createProducerTemplate()) {
                var result = template.request("direct:start", exchange -> {
                    exchange.getMessage().setBody("original");
                    exchange.getMessage().setHeader("selected", "invoice");
                });
                assertThat(result.getException()).isNull();
                assertThat(result.getMessage().getHeader("answer")).isEqualTo("billing");
                var missing = template.request("direct:start", exchange -> exchange.getMessage().setBody("original"));
                assertThat(missing.getException()).hasMessageContaining("Missing selected state");
            }
        } finally {
            main.stop();
        }
    }

}

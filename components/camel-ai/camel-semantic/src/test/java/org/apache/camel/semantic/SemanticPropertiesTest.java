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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticPropertiesTest {
    @ParameterizedTest
    @ValueSource(strings = { "bean", "class", "plain" })
    void camelMainBindsAdapterAndDefaultState(String selection) throws Exception {
        Main main = new Main();
        SemanticLanguageTest.LabelAdapter bean = new SemanticLanguageTest.LabelAdapter();
        main.bind("classifier", bean);
        String adapter = switch (selection) {
            case "bean" -> "#bean:classifier";
            case "class" -> "#class:" + SemanticLanguageTest.LabelAdapter.class.getName();
            default -> SemanticLanguageTest.LabelAdapter.class.getName();
        };
        main.addProperty("camel.language.semantic.adapter", adapter);
        main.addProperty("camel.language.semantic.default-state", "${header.selected}");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            public void configure() {
                SemanticQuestions.get(getContext()).replace("test", Map.of("q",
                        SemanticLanguageTest.question(SemanticQuestion.Type.CHOICE, null, 0.5, 0,
                                SemanticQuestion.UncertaintyPolicy.FAIL)));
                from("direct:start").setHeader("answer").language("semantic", "ref:q");
            }
        });
        try {
            main.start();
            if (selection.equals("bean")) {
                assertThat(main.getCamelContext().getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
            } else {
                assertThat(main.getCamelContext().getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME))
                        .isInstanceOf(SemanticLanguageTest.LabelAdapter.class).isNotSameAs(bean);
            }
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

    @Test
    void classReferenceIsTypeCheckedBeforeConstruction() {
        Main main = new Main();
        WrongType.constructed.set(0);
        main.addProperty("camel.language.semantic.adapter", "#class:" + WrongType.class.getName());
        main.configure().addRoutesBuilder(new RouteBuilder() {
            public void configure() {
                SemanticQuestions.get(getContext()).replace("test", Map.of("q",
                        SemanticLanguageTest.question(SemanticQuestion.Type.CHOICE, null, 0.5, 0,
                                SemanticQuestion.UncertaintyPolicy.FAIL)));
                from("direct:start").setHeader("answer").language("semantic", "ref:q");
            }
        });
        try {
            assertThatThrownBy(main::start).hasStackTraceContaining("ClassCastException");
            assertThat(WrongType.constructed).hasValue(0);
            assertThat(main.getCamelContext().getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
        } finally {
            main.stop();
        }
    }

    public static class WrongType {
        static final AtomicInteger constructed = new AtomicInteger();

        public WrongType() {
            constructed.incrementAndGet();
        }
    }
}

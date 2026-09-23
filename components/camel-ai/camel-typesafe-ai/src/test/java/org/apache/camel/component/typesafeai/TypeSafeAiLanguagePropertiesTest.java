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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.language.typesafeai.TypeSafeAiLanguage;
import org.apache.camel.language.typesafeai.TypeSafeAiLanguage.UncertaintyPolicy;
import org.apache.camel.main.Main;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeAiLanguagePropertiesTest extends TypeSafeAiTestSupport {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = { "java", "xml" })
    void genericDslUsesLanguagePropertiesWithoutBeans(String dsl) throws Exception {
        Main main = new Main();
        main.addProperty("camel.component.typesafe-ai.api-key", "test-key");
        main.addProperty("camel.component.typesafe-ai.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        main.addProperty("camel.component.typesafe-ai.threshold", "0.2");
        main.addProperty("camel.component.typesafe-ai.uncertainty", "0");
        main.addProperty("camel.component.typesafe-ai.state", "${body}");
        main.addProperty("camel.language.typesafe-ai.endpoint", "typesafe-ai:refund");
        main.addProperty("camel.language.typesafe-ai.threshold", "0.8");
        main.addProperty("camel.language.typesafe-ai.uncertainty", "0.1");
        main.addProperty("camel.language.typesafe-ai.uncertainty-policy", "Fail");
        main.addProperty("camel.language.typesafe-ai.state", "${header.selected}");
        if ("java".equals(dsl)) {
            main.configure().addRoutesBuilder(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:filter").filter().language("typesafe-ai", "Refund requested?")
                            .setHeader("accepted", constant(true));
                }
            });
        } else {
            String route = """
                    <routes xmlns="http://camel.apache.org/schema/spring">
                      <route>
                        <from uri="direct:filter"/>
                        <filter>
                          <language language="typesafe-ai">Refund requested?</language>
                          <setHeader name="accepted"><constant>true</constant></setHeader>
                        </filter>
                      </route>
                    </routes>
                    """;
            Path file = directory.resolve("refund." + dsl);
            Files.writeString(file, route);
            main.configure().withRoutesIncludePattern("file:" + file);
        }
        try {
            main.start();
            assertThat(main.getCamelContext().getRegistry().findByType(Predicate.class)).isEmpty();
            try (var producer = main.getCamelContext().createProducerTemplate()) {
                for (double probability : new double[] { 0.6, 0.8, 0.95 }) {
                    respond = request -> noulResponse(probability);
                    Exchange result = producer.request("direct:filter", e -> {
                        e.getMessage().setBody("PRIVATE BODY");
                        e.getMessage().setHeader("selected", "Refund the payment");
                    });
                    assertThat(result.getMessage().getBody()).isEqualTo("PRIVATE BODY");
                    assertThat(result.getProperty(TypeSafeAiLanguage.RESULT)).isNotNull();
                    if (probability == 0.8) {
                        assertThat(result.getException()).hasCauseInstanceOf(TypeSafeAiUncertainResultException.class);
                    } else {
                        assertThat(result.getException()).isNull();
                    }
                    assertThat(result.getMessage().getHeader("accepted", Boolean.class))
                            .isEqualTo(probability == 0.95 ? true : null);
                }
            }
            assertThat(requests).hasSize(3).allSatisfy(request -> {
                assertThat(request.get("state")).isEqualTo("Refund the payment");
                assertThat(request.path("questions.predicate.instructions")).isEqualTo("Refund requested?");
                assertThat(request.toJson()).doesNotContain("PRIVATE");
            });
            assertThat(main.getCamelContext().hasEndpoint("typesafe-ai:refund")).isNotNull();
        } finally {
            main.stop();
        }
    }

    @Test
    void perUseOverridesLanguageWhichOverridesEndpointConfiguration() {
        TypeSafeAiConfiguration configuration
                = context.getComponent("typesafe-ai", TypeSafeAiComponent.class).getConfiguration();
        configuration.setThreshold(0.8);
        configuration.setUncertainty(0.1);
        configuration.setUncertaintyPolicy(UncertaintyPolicy.NonMatch);
        configuration.setState("${header.selected}");
        TypeSafeAiLanguage language = (TypeSafeAiLanguage) context.resolveLanguage("typesafe-ai");
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("BODY");
        exchange.getMessage().setHeader("selected", "SELECTED");
        respond = request -> noulResponse(0.75);
        assertThat(language.createPredicate("Refund?").matches(exchange)).isFalse();
        assertThat(requests.poll().get("state")).isEqualTo("SELECTED");

        language.setThreshold(0.7);
        language.setUncertainty(0.01);
        language.setState("${body}");
        assertThat(language.createPredicate("Refund?").matches(exchange)).isTrue();
        assertThat(requests.poll().get("state")).isEqualTo("BODY");

        String question = "typesafe-ai:question-is-not-an-endpoint?threshold=0";
        Predicate override = language.createPredicate(question,
                new Object[] { "typesafe-ai:other?threshold=0.6", 0.8, 0, "Fail", "${header.selected}" });
        assertThat(override.matches(exchange)).isFalse();
        var request = requests.poll();
        assertThat(request.get("state")).isEqualTo("SELECTED");
        assertThat(request.path("questions.predicate.instructions")).isEqualTo(question);
        assertThat(context.hasEndpoint("typesafe-ai:other?threshold=0.6")).isNotNull();
    }

    @Test
    void ordinaryExceptionClausesHandleUncertaintyAndTimeout() throws Exception {
        TypeSafeAiLanguage language = (TypeSafeAiLanguage) context.resolveLanguage("typesafe-ai");
        language.setEndpoint("typesafe-ai:errors?requestTimeout=200");
        language.setThreshold(0.5);
        language.setUncertainty(0.125);
        language.setUncertaintyPolicy(UncertaintyPolicy.Fail);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(TypeSafeAiUncertainResultException.class).handled(true).setHeader("handled", constant("uncertain"));
                onException(TimeoutException.class).handled(true).setHeader("handled", constant("timeout"));
                from("direct:errors").filter().language("typesafe-ai", "Refund?").setHeader("accepted", constant(true));
            }
        });
        respond = request -> noulResponse(0.5);
        Exchange uncertain = template.request("direct:errors", e -> e.getMessage().setBody("Refund"));
        assertThat(uncertain.getException()).isNull();
        assertThat(uncertain.getMessage().getHeader("handled")).isEqualTo("uncertain");
        holdHeaders = true;
        Exchange timeout = template.request("direct:errors", e -> e.getMessage().setBody("Refund"));
        assertThat(timeout.getException()).isNull();
        assertThat(timeout.getMessage().getHeader("handled")).isEqualTo("timeout");
        assertThat(timeout.getProperty(TypeSafeAiLanguage.RESULT)).isNull();
    }
}

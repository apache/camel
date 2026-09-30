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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeAiChoiceTest extends TypeSafeAiTestSupport {
    @BeforeEach
    void routes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());
                from("direct:classify")
                        .setProperty("original", body())
                        .process(exchange -> {
                            exchange.getMessage().setBody(Map.of("state", exchange.getMessage().getBody(),
                                    "questions", Map.of("department", Map.of("type", "choice",
                                            "instructions", "Which team should handle this message?", "criteria",
                                            Map.of("billing", "Payments and refunds", "technical", "Product failures",
                                                    "other", "Neither team applies")))));
                        })
                        .to("typesafe-ai:classifier?resultProperty=evaluation")
                        .setBody(exchangeProperty("original"))
                        .choice()
                        .when(exchange -> selects(exchange, "billing")).to("direct:billing")
                        .when(exchange -> selects(exchange, "technical")).to("direct:technical")
                        .otherwise().to("direct:manual");
                from("direct:billing").setHeader("branch", constant("billing"));
                from("direct:technical").setHeader("branch", constant("technical"));
                from("direct:manual").setHeader("branch", constant("manual"));
            }
        });
    }

    private static boolean selects(Exchange exchange, String label) {
        JsonObject response = exchange.getProperty("evaluation", JsonObject.class);
        JsonObject answer = (JsonObject) response.path("answers.department");
        Map<?, ?> probabilities = (Map<?, ?>) answer.get("probabilities");
        return label.equals(answer.get("choice")) && ((Number) probabilities.get(label)).doubleValue() >= 0.8
                && ((Number) answer.get("confidence")).doubleValue() >= 0.7;
    }

    @ParameterizedTest
    @CsvSource({
            "Refund my duplicate payment,billing,0.9,0.85,billing",
            "The export button fails,technical,0.9,0.85,technical",
            "Thanks for the newsletter,other,0.9,0.85,manual",
            "A refund or help fixing the export,billing,0.55,0.2,manual",
            "A payment question,billing,0.75,0.75,manual",
            "It fails sometimes,technical,0.9,0.4,manual"
    })
    void oneClassificationCallThenDeterministicBranchSelection(
            String message, String winner, double probability, double confidence, String expected) {
        Map<String, Double> probabilities = Map.of("billing", winner.equals("billing") ? probability : (1 - probability) / 2,
                "technical", winner.equals("technical") ? probability : (1 - probability) / 2,
                "other", winner.equals("other") ? probability : (1 - probability) / 2);
        respond = request -> result(Map.of("department", Map.of("type", "choice", "choice", winner,
                "confidence", confidence, "probabilities", probabilities)));
        Exchange exchange = template.request("direct:classify", e -> e.getMessage().setBody(message));
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader("branch")).isEqualTo(expected);
        assertThat(exchange.getMessage().getBody()).isEqualTo(message);
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().path("state")).isEqualTo(message);
    }

    @Test
    void serviceFailureDoesNotSelectOtherwise() {
        status = 503;
        Exchange exchange = template.request("direct:classify", e -> e.getMessage().setBody("Refund"));
        assertThat(exchange.getException()).isNotNull();
        assertThat(exchange.getMessage().getHeader("branch")).isNull();
        assertThat(requests).hasSize(1);
    }
}

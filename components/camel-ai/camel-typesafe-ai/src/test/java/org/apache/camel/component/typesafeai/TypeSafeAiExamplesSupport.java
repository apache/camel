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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.typesafeai.mock.TypeSafeAiService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Example routes shared by the mock test and the optional external API test. */
abstract class TypeSafeAiExamplesSupport extends CamelTestSupport {

    abstract TypeSafeAiService service();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        TypeSafeAiComponent component = new TypeSafeAiComponent();
        component.getConfiguration().setApiKey(service().getApiKey());
        component.getConfiguration().setBaseUrl(service().getBaseUrl());
        component.getConfiguration().setModel(service().getModel());
        component.getConfiguration().setResultProperty("evaluation");
        context.addComponent("typesafe-ai", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());
                from("direct:quickstart")
                        .to("typesafe-ai:quickstart?questionsResource=classpath:typesafe-ai/quickstart-questions.json")
                        .choice()
                        .when(simple("${exchangeProperty.evaluation[answers][department][confidence]} >= '0.6'"))
                        .setHeader("decision", constant("automatic"))
                        .otherwise()
                        .setHeader("decision", constant("review"));
                from("direct:triage")
                        .to("typesafe-ai:triage?questionsResource=classpath:typesafe-ai/triage-questions.json")
                        .setHeader("department", simple("${exchangeProperty.evaluation[answers][department][choice]}"));
                from("direct:cascade")
                        .to("typesafe-ai:cascade?questionsResource=classpath:typesafe-ai/cascade-questions.json");
                from("direct:tool-search")
                        .to("typesafe-ai:tool-search?questionsResource=classpath:typesafe-ai/tool-search-questions.json")
                        .choice()
                        .when(simple("${exchangeProperty.evaluation[answers][any_tool_applies][noul]} >= '0.7'"))
                        .setHeader("selectedTool",
                                simple("${exchangeProperty.evaluation[answers][selected_tool][choice]}"));
            }
        };
    }

    @Test
    void quickstartEvaluatesThreePrimitivesAndConfidenceGate() {
        String ticket = "Help! My payouts have been failing for 3 days.";
        Exchange exchange = template.request("direct:quickstart", e -> e.getMessage().setBody(ticket));
        JsonObject answers = answers(exchange, 3);
        assertThat(exchange.getMessage().getBody()).isEqualTo(ticket);
        assertThat(answers.getJsonObject("is_urgent").getDouble("noul")).isBetween(0.0, 1.0);
        assertThat(answers.getJsonObject("department").getString("choice"))
                .isIn("billing", "technical", "sales");
        assertThat(answers.getJsonObject("frustration").getDouble("score")).isBetween(0.0, 2.0);
        double confidence = answers.getJsonObject("department").getDouble("confidence");
        assertThat(exchange.getMessage().getHeader("decision")).as("confidence=%s", confidence)
                .isEqualTo(confidence >= 0.6 ? "automatic" : "review");
    }

    @Test
    void triageUsesTheFiveQuestionBatchFromTheSpringDemo() {
        for (String ticket : List.of(
                "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. Please help ASAP.",
                "You charged me twice for March. Please refund one of them.",
                "Just wanted to say the new dashboard is lovely. No issue, keep it up!")) {
            Exchange exchange = template.request("direct:triage", e -> e.getMessage().setBody(ticket));
            JsonObject answers = answers(exchange, 5);
            assertThat(exchange.getMessage().getBody()).isEqualTo(ticket);
            assertThat(exchange.getMessage().getHeader("department"))
                    .isEqualTo(answers.getJsonObject("department").getString("choice"));
            assertThat(answers).containsKeys("is_urgent", "frustration", "bug_severity", "refund_requested");
        }
    }

    @Test
    void cascadeVerifiesStructuredExtractionState() {
        Map<String, Object> state = Map.of(
                "source_text", "Invoice 4472 from Beaver Dam Builders. Materials 800.00, labour 450.00. Total due 1,250.00.",
                "extracted", Map.of("invoice", "4472", "vendor", "Beaver Dam Builders", "date", "2026-03-11",
                        "total", "1350.00"),
                "required_fields", List.of("invoice", "vendor", "date", "total"));
        Exchange exchange = template.request("direct:cascade", e -> e.getMessage().setBody(state));
        JsonObject answers = answers(exchange, 3);
        assertThat(exchange.getMessage().getBody()).isSameAs(state);
        assertThat(answers).containsKeys("invented_value", "wrong_total", "missing_field");
        assertThat(answers.getJsonObject("wrong_total").getDouble("noul")).isBetween(0.0, 1.0);
    }

    @Test
    void toolSearchSeparatesSelectionFromApplicability() {
        Map<String, Object> tools = Map.of(
                "currentWeather", "Returns current conditions for a named place",
                "sendEmail", "Sends an email message",
                "createInvoice", "Creates an invoice for a customer",
                "searchOrders", "Finds a customer's past orders",
                "bookMeeting", "Schedules a calendar meeting");
        for (String query : List.of("bill Acme Corp for last month", "what is the capital of Peru")) {
            Map<String, Object> state = Map.of("user_request", query, "available_tools", tools);
            Exchange exchange = template.request("direct:tool-search", e -> e.getMessage().setBody(state));
            JsonObject answers = answers(exchange, 2);
            assertThat(exchange.getMessage().getBody()).isSameAs(state);
            assertThat(answers.getJsonObject("selected_tool").getString("choice")).isIn(tools.keySet());
            double applicability = answers.getJsonObject("any_tool_applies").getDouble("noul");
            assertThat(applicability).isBetween(0.0, 1.0);
            assertThat(exchange.getMessage().getHeader("selectedTool")).as("applicability=%s", applicability)
                    .isEqualTo(applicability >= 0.7 ? answers.getJsonObject("selected_tool").getString("choice") : null);
        }
    }

    private JsonObject answers(Exchange exchange, int count) {
        assertThat(exchange.getException()).isNull();
        JsonObject evaluation = exchange.getProperty("evaluation", JsonObject.class);
        assertThat(evaluation).isNotNull();
        JsonObject answers = evaluation.getJsonObject("answers");
        assertThat(answers).hasSize(count);
        return answers;
    }
}

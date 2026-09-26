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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticEipTest extends CamelTestSupport {
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private volatile String completionState;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.getRegistry().bind("classifier", new SemanticAdapter() {
                    public void validate(SemanticQuestion question) {
                    }

                    public SemanticResult evaluate(SemanticQuestion question, Object state) {
                        String purpose = question.getInstructions();
                        calls.computeIfAbsent(purpose, k -> new AtomicInteger()).incrementAndGet();
                        String text = state.toString();
                        Object value = switch (purpose) {
                            case "department" -> text.contains("invoice") ? "billing"
                                    : text.contains("outage") ? "technical" : "general";
                            case "actionable" -> text.contains("please");
                            case "complete" -> {
                                completionState = text;
                                yield text.contains("DONE");
                            }
                            case "unfinished" -> Integer.parseInt(text) < 3;
                            case "urgency" -> text.contains("urgent") ? 2.0 : 0.0;
                            default -> throw new IllegalArgumentException("Unknown question");
                        };
                        return new SemanticResult(value, null, null, null, Map.of("provider", "fixture"));
                    }
                });
                ((SemanticLanguage) context.resolveLanguage("semantic")).setAdapter("classifier");
                SemanticQuestions.get(context).replace("test", Map.of(
                        "department", question("department", SemanticQuestion.Type.CHOICE),
                        "actionable", question("actionable", SemanticQuestion.Type.BOOLEAN),
                        "complete", question("complete", SemanticQuestion.Type.BOOLEAN),
                        "unfinished", question("unfinished", SemanticQuestion.Type.BOOLEAN),
                        "urgency", question("urgency", SemanticQuestion.Type.SCORE)));

                from("direct:choice").setProperty("department").language("semantic", "ref:department")
                        .choice()
                        .when(exchangeProperty("department").isEqualTo("billing")).to("mock:billing")
                        .when(exchangeProperty("department").isEqualTo("technical")).to("mock:technical")
                        .otherwise().to("mock:general");
                from("direct:repeatedChoice").loop(2)
                        .to("direct:choice")
                        .setBody(constant("invoice"))
                        .end();
                from("direct:filter").filter().language("semantic", "ref:actionable").to("mock:accepted");
                from("direct:validate").validate().language("semantic", "ref:actionable").to("mock:valid");
                from("direct:metadata").setHeader("department").language("semantic", "ref:department")
                        .setProperty("urgency").language("semantic", "ref:urgency")
                        .setHeader("reused", header("department"));
                from("direct:group").aggregate(semantic("department"), new GroupedBodyAggregationStrategy())
                        .completionSize(2).to("mock:grouped");
                from("direct:complete").aggregate(constant("case-1"), (oldExchange, incoming) -> {
                    if (oldExchange == null) {
                        return incoming;
                    }
                    oldExchange.getMessage().setBody(
                            oldExchange.getMessage().getBody(String.class) + ":" + incoming.getMessage().getBody(String.class));
                    return oldExchange;
                }).completionPredicate(context.resolveLanguage("semantic").createPredicate("ref:complete"))
                        .completionSize(10).to("mock:complete");
                from("direct:destinations").setProperty("department").language("semantic", "ref:department")
                        .process(exchange -> {
                            String category = exchange.getProperty("department", String.class);
                            exchange.getMessage().setHeader("recipients",
                                    Map.of("billing", "mock:billing,mock:audit", "technical", "mock:technical").get(category));
                            exchange.getMessage().setHeader("slip", Map
                                    .of("billing", "direct:first,direct:second", "technical", "direct:second").get(category));
                            exchange.getMessage()
                                    .setHeader("resource", Map
                                            .of("billing", "direct:billing-resource", "technical", "direct:technical-resource")
                                            .get(category));
                        })
                        .recipientList(header("recipients")).end()
                        .routingSlip(header("slip"))
                        .enrich().header("resource").aggregationStrategy((original, resource) -> {
                            original.getMessage().setHeader("knowledge", resource.getMessage().getBody());
                            return original;
                        });
                from("direct:first").setHeader("first", constant(true));
                from("direct:second").setHeader("second", constant(true));
                from("direct:billing-resource").setBody(constant("payment knowledge"));
                from("direct:technical-resource").setBody(constant("technical knowledge"));
                from("direct:loop").setHeader("iterations", constant(0))
                        .loopDoWhile(PredicateBuilder.and(context.resolveLanguage("semantic").createPredicate("ref:unfinished"),
                                exchange -> exchange.getMessage().getHeader("iterations", Integer.class) < 3))
                        .process(exchange -> {
                            int next = exchange.getMessage().getHeader("iterations", Integer.class) + 1;
                            exchange.getMessage().setHeader("iterations", next);
                            exchange.getMessage().setBody(Integer.toString(next));
                        }).end();
                from("direct:sort").split(body(), new GroupedBodyAggregationStrategy())
                    .setHeader("score").language("semantic", "ref:urgency")
                    .process(exchange -> exchange.getMessage().setBody(new Ticket(exchange.getMessage().getBody(String.class),
                            exchange.getMessage().getHeader("score", Double.class))))
                    .end().sort(body(), Comparator.comparingDouble(Ticket::score).reversed());
            }
        };
    }

    private static LanguageExpression semantic(String name) {
        return new LanguageExpression("semantic", "ref:" + name);
    }

    private static SemanticQuestion question(String name, SemanticQuestion.Type type) {
        return new SemanticQuestion(
                type, name, null,
                type == SemanticQuestion.Type.CHOICE
                        ? Map.of("billing", "Payments", "technical", "Bugs", "general", "Other requests") : null,
                type == SemanticQuestion.Type.SCORE ? List.of("low", "medium", "high") : null,
                0.5, 0, SemanticQuestion.UncertaintyPolicy.FAIL);
    }

    @Test
    void choiceFilterValidationAndStoredMetadata() throws Exception {
        getMockEndpoint("mock:technical").expectedMessageCount(1);
        getMockEndpoint("mock:accepted").expectedMessageCount(1);
        getMockEndpoint("mock:valid").expectedMessageCount(1);
        template.sendBody("direct:choice", "outage");
        template.sendBody("direct:filter", "please help");
        template.sendBody("direct:filter", "hello");
        template.sendBody("direct:validate", "please help");
        assertThatThrownBy(() -> template.sendBody("direct:validate", "hello")).hasStackTraceContaining("ValidationException");
        Exchange exchange = template.request("direct:metadata", e -> e.getMessage().setBody("urgent invoice"));
        assertThat(exchange.getMessage().getBody()).isEqualTo("urgent invoice");
        assertThat(exchange.getMessage().getHeader("reused")).isEqualTo("billing");
        assertThat(exchange.getProperty("urgency")).isEqualTo(2.0);
        assertThat(calls.get("department")).hasValue(2);
        assertThat(calls.get("urgency")).hasValue(1);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void storedChoiceDecisionEvaluatesOnceForEachBranchAndPreservesTheBody() throws Exception {
        getMockEndpoint("mock:billing").expectedBodiesReceived("invoice");
        getMockEndpoint("mock:technical").expectedBodiesReceived("outage");
        getMockEndpoint("mock:general").expectedBodiesReceived("hello");
        template.sendBody("direct:choice", "invoice");
        template.sendBody("direct:choice", "outage");
        template.sendBody("direct:choice", "hello");
        MockEndpoint.assertIsSatisfied(context);
        assertThat(calls.get("department")).hasValue(3);
    }

    @Test
    void storedChoiceDecisionIsRefreshedOnEachLoopIteration() throws Exception {
        getMockEndpoint("mock:technical").expectedBodiesReceived("outage");
        getMockEndpoint("mock:billing").expectedBodiesReceived("invoice");
        Exchange exchange = template.request("direct:repeatedChoice", e -> e.getMessage().setBody("outage"));
        MockEndpoint.assertIsSatisfied(context);
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getProperty("department")).isEqualTo("billing");
        assertThat(calls.get("department")).hasValue(2);
    }

    @Test
    void groupingAndCompletionUseTheRightState() throws Exception {
        getMockEndpoint("mock:grouped").expectedMessageCount(1);
        getMockEndpoint("mock:complete").expectedBodiesReceived("start:DONE");
        template.sendBody("direct:group", "invoice one");
        template.sendBody("direct:group", "invoice two");
        template.sendBody("direct:complete", "start");
        template.sendBody("direct:complete", "DONE");
        MockEndpoint.assertIsSatisfied(context);
        assertThat(completionState).isEqualTo("start:DONE");
        assertThat(getMockEndpoint("mock:grouped").getExchanges().get(0).getMessage().getBody(List.class))
                .containsExactly("invoice one", "invoice two");
        assertThat(calls.get("complete")).hasValue(2);
    }

    @Test
    void destinationsAreMappedFromOneStoredClassification() throws Exception {
        getMockEndpoint("mock:billing").expectedBodiesReceived("invoice");
        getMockEndpoint("mock:audit").expectedBodiesReceived("invoice");
        Exchange exchange = template.request("direct:destinations", e -> e.getMessage().setBody("invoice"));
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo("invoice");
        assertThat(exchange.getMessage().getHeader("first")).isEqualTo(true);
        assertThat(exchange.getMessage().getHeader("second")).isEqualTo(true);
        assertThat(exchange.getMessage().getHeader("knowledge")).isEqualTo("payment knowledge");
        assertThat(calls.get("department")).hasValue(1);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void boundedLoopReevaluatesChangedStateAndSortUsesPrecomputedScores() {
        assertThat(template.requestBody("direct:loop", "0", String.class)).isEqualTo("3");
        assertThat(calls.get("unfinished")).hasValue(4);
        List<?> sorted = template.requestBody("direct:sort", List.of("routine", "urgent", "another"), List.class);
        assertThat(sorted.get(0)).isEqualTo(new Ticket("urgent", 2));
        assertThat(calls.get("urgency")).hasValue(3);
    }

    private record Ticket(String text, double score) {
    }
}

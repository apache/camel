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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.ai.AiEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticEventTest {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void completeInterpretationReachesListenersIndependentlyOfAuditBudget(boolean capture) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var events = new Collector();
            context.getManagementStrategy().addEventNotifier(events);
            context.getRegistry().bind("expert", new ChoiceExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("test", new SemanticAuditConfiguration(capture, Map.of(), List.of("memory"), "memory", 10, 10));
            context.start();
            Map<String, String> criteria = new LinkedHashMap<>();
            for (int i = 0; i < 40; i++) {
                criteria.put("choice-" + i, "original meaning " + "x".repeat(500));
            }
            var evaluation = evaluation(criteria);
            var result = ((SemanticLanguage) context.resolveLanguage("semantic")).evaluate(evaluation, "private-input");
            criteria.put("choice-0", "changed after invocation");
            assertThat(result.getValue()).isEqualTo("choice-0");
            assertThat(events.values).extracting(SemanticEvent::getKind)
                    .containsExactly(SemanticEvent.Kind.EVALUATION_STARTED, SemanticEvent.Kind.EVALUATION_COMPLETED);
            assertThat(events.values.get(0).getEventId()).isEqualTo(events.values.get(1).getEventId());
            for (var event : events.values) {
                assertThat(event).isNotInstanceOf(AiEvent.ExchangeAiEvent.class);
                assertThat(event.getData()).doesNotContainKeys("input", "inputOmitted", "inputRedacted",
                        "interpretationOmitted");
                Map<?, ?> interpretation = (Map<?, ?>) event.getData().get("interpretation");
                Map<?, ?> labels = (Map<?, ?>) interpretation.get("choices");
                assertThat(labels).hasSize(40);
                assertThat(labels.get("choice-0")).isEqualTo("original meaning " + "x".repeat(500));
                assertThatThrownBy(labels::clear).isInstanceOf(UnsupportedOperationException.class);
                assertThat(event.toJSon(0)).contains("original meaning").doesNotContain("private-input");
                assertThat(event.toString()).doesNotContain("original meaning", "private-input");
            }
            assertThat(events.values.get(1).getData()).containsEntry("taskId", "support").containsEntry("taskVersion", "v1");
            assertThat(events.values.get(1).getData().get("findings")).isEqualTo(List.of(Map.of(
                    "code", "category_match", "meaning", "Matched the supplied category", "source", "choice-expert")));
            audit.stop();
            var retained = audit.getReader().getAll(List.of(events.values.get(1).getEventId()));
            if (capture) {
                assertThat(retained).hasSize(1);
                assertThat(retained.values().iterator().next().toMap())
                        .containsEntry("interpretationOmitted", "audit_snapshot_limit").doesNotContainKey("interpretation");
            } else {
                assertThat(retained).isEmpty();
            }
        }
    }

    @Test
    void capturesExecutingRouteAndKeepsInputPrivateWithMixedBackends() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var events = new Collector();
            context.getManagementStrategy().addEventNotifier(events);
            context.getRegistry().bind("expert", new ChoiceExpert());
            var legacy = new LegacyBackend();
            context.getRegistry().bind("legacy", legacy);
            var audit = SemanticAuditService.get(context);
            audit.configure("test", new SemanticAuditConfiguration(
                    true, Map.of("expert", true),
                    List.of("memory", "legacy"), "memory", 10, 10,
                    Map.of("expert", new SemanticAuditInputConfiguration(true, 100, null))));
            SemanticEvaluations.get(context).replace("definitions",
                    Map.of("classify", evaluation(Map.of("choice-0", "Billing"))));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:entry").routeId("entry-route").to("direct:assessment");
                    from("direct:assessment").routeId("assessment-route").setBody().language("semantic", "ref:classify");
                }
            });
            context.start();
            try (var template = context.createProducerTemplate()) {
                assertThat(template.requestBody("direct:entry", "private-input", String.class)).isEqualTo("choice-0");
            }
            context.getRouteController().stopRoute("assessment-route");
            context.removeRoute("assessment-route");
            audit.stop();
            var completed = events.values.get(1);
            assertThat(completed.getData()).containsEntry("routeId", "entry-route")
                    .containsEntry("executingRouteId", "assessment-route").doesNotContainKey("input");
            assertThat(audit.status()).as("audit delivery health").containsEntry("dropped", 0L)
                    .containsEntry("sinkErrors", Map.of("memory", 0L, "legacy", 0L));
            assertThat(audit.getReader().get(completed.getEventId())).as("retained event; health: %s", audit.status())
                    .isPresent();
            assertThat(audit.getReader().get(completed.getEventId()).orElseThrow().toMap())
                    .containsEntry("input", "private-input").containsEntry("executingRouteId", "assessment-route");
            assertThat(legacy.records).hasSize(1);
            assertThat(legacy.records.get(0).toMap()).containsEntry("input", "private-input")
                    .doesNotContainKeys("interpretation", "executingRouteId", "findings", "taskId", "taskVersion");
            assertThat(legacy.getSupportedExtensions()).isEmpty();
        }
    }

    @Test
    void listenerFailuresDoNotChangeResultsAndFilteringDoesNotDisableAudit() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getManagementStrategy().addEventNotifier(new Collector() {
                @Override
                public boolean isEnabled(CamelEvent event) {
                    if (event instanceof SemanticEvent) {
                        throw new IllegalStateException("Eligibility failure");
                    }
                    return false;
                }
            });
            context.getManagementStrategy().addEventNotifier(new Collector() {
                @Override
                public void notify(CamelEvent event) {
                    throw new IllegalStateException("Delivery failure");
                }
            });
            var events = new Collector();
            context.getManagementStrategy().addEventNotifier(events);
            context.getRegistry().bind("expert", new ChoiceExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("test", new SemanticAuditConfiguration(true, Map.of(), List.of("memory"), "memory", 10, 10));
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            var evaluation = evaluation(Map.of("choice-0", "Billing"));
            assertThat(language.evaluate(evaluation, "first").getValue()).isEqualTo("choice-0");
            assertThat(events.values).hasSize(2);
            context.getManagementStrategy().getEventNotifiers().forEach(notifier -> notifier.setIgnoreAiEvents(true));
            assertThat(language.evaluate(evaluation, "second").getValue()).isEqualTo("choice-0");
            audit.stop();
            assertThat(events.values).hasSize(2);
            assertThat(audit.getReader().query(new SemanticAuditQuery(Map.of(), null, null, 10)).getRecords()).hasSize(2);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "disabled", "ignored", "broken" })
    void disabledAuditSkipsOccurrenceConstructionWithoutAnEligibleConsumer(String mode) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.start();
            if (!"none".equals(mode)) {
                context.getManagementStrategy().addEventNotifier(new Collector() {
                    @Override
                    public boolean isDisabled() {
                        if ("broken".equals(mode)) {
                            throw new AssertionError("Listener eligibility failed");
                        }
                        return "disabled".equals(mode);
                    }

                    @Override
                    public boolean isIgnoreAiEvents() {
                        return "ignored".equals(mode);
                    }
                });
            }
            var invocation = SemanticAuditService.get(context).begin(
                    null, null, null, null, "expert", "test", null, null);
            assertThat(invocation.getEventId()).as("No occurrence is constructed for %s", mode).isNull();
            invocation.complete("success", "evaluation_completed", new SemanticResult(true, null, Map.of(), null, Map.of()));
            // Context lifecycle dispatch does not have the best-effort AI dispatch contract.
            List.copyOf(context.getManagementStrategy().getEventNotifiers())
                    .forEach(context.getManagementStrategy()::removeEventNotifier);
        }
    }

    @Test
    void failedEligibilityDoesNotPreventAnotherConsumerOrEvaluation() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.start();
            context.getManagementStrategy().addEventNotifier(new Collector() {
                @Override
                public boolean isIgnoreAiEvents() {
                    throw new AssertionError("Listener eligibility failed");
                }
            });
            var events = new Collector();
            context.getManagementStrategy().addEventNotifier(events);
            var invocation = SemanticAuditService.get(context).begin(
                    null, null, null, null, "expert", "test", null, null);
            assertThat(invocation.getEventId()).isNotNull();
            invocation.complete("success", "evaluation_completed", new SemanticResult(true, null, Map.of(), null, Map.of()));
            assertThat(events.values).extracting(SemanticEvent::getKind)
                    .containsExactly(SemanticEvent.Kind.EVALUATION_STARTED, SemanticEvent.Kind.EVALUATION_COMPLETED);
        }
    }

    private static SemanticEvaluation evaluation(Map<String, String> criteria) {
        return new SemanticEvaluation("choice", "expert", null, Map.of("criteria", criteria));
    }

    @Test
    void exchangeEventsExposeLiveContextButRetainFrozenOccurrenceData() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var events = new Collector();
            context.getManagementStrategy().addEventNotifier(events);
            var audit = SemanticAuditService.get(context);
            audit.configure("test", new SemanticAuditConfiguration(true, Map.of(), List.of("memory"), "memory", 10, 10));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.setExchangeId("original-exchange");
            exchange.getMessage().setHeader(Exchange.BREADCRUMB_ID, "original-breadcrumb");
            exchange.getMessage().setHeader("secret", "private-header");
            exchange.getMessage().setBody("private-body");
            var invocation = audit.begin(exchange, null, null, null, "expert", "test", null, null);
            exchange.setExchangeId("changed-exchange");
            exchange.getMessage().setHeader(Exchange.BREADCRUMB_ID, "changed-breadcrumb");
            invocation.complete("success", "evaluation_completed", new SemanticResult(true, null, Map.of(), null, Map.of()));
            assertThat(events.values).hasSize(2);
            for (var event : events.values) {
                assertThat(event).isInstanceOf(AiEvent.ExchangeAiEvent.class);
                assertThat(event).isInstanceOfSatisfying(AiEvent.ExchangeAiEvent.class,
                        occurrence -> assertThat(occurrence.getExchange()).isSameAs(exchange));
                assertThat(event.getSource()).isSameAs(exchange);
                assertThat(event.getData()).containsEntry("exchangeId", "original-exchange")
                        .containsEntry("breadcrumbId", "original-breadcrumb");
                assertThat(event.toJSon(0)).doesNotContain("private-body", "private-header", "changed-exchange");
                assertThat(event.toString()).doesNotContain("private-body", "private-header");
            }
            audit.stop();
            assertThat(audit.getReader().get(invocation.getEventId()).orElseThrow().toMap())
                    .containsEntry("exchangeId", "original-exchange").containsEntry("breadcrumbId", "original-breadcrumb")
                    .doesNotContainValue(exchange);
        }
    }

    static class Collector extends EventNotifierSupport {
        final List<SemanticEvent> values = new ArrayList<>();

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof SemanticEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            values.add((SemanticEvent) event);
        }
    }

    // An existing combined reader/sink needs no new methods to remain source-compatible.
    static class LegacyBackend extends ServiceSupport implements SemanticAuditSink, SemanticAuditReader {
        final List<SemanticAuditRecord> records = new ArrayList<>();

        @Override
        public void append(SemanticAuditRecord record) {
            records.add(record);
        }

        @Override
        public Optional<SemanticAuditRecord> get(String eventId) {
            return records.stream().filter(record -> eventId.equals(record.getEventId())).findFirst();
        }

        @Override
        public SemanticAuditPage query(SemanticAuditQuery query) {
            return new SemanticAuditPage(
                    records.stream().filter(query::matches).limit(query.getLimit()).toList(), null, 0, false);
        }
    }

    @SemanticExpert(name = "choice", description = "Fixed test classifier", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "choice", description = "Classify",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.CHOICE, resultMeaning = "Selected category",
                                                    choiceLabelsParameter = "criteria",
                                                    parameters = @SemanticParameter(name = "criteria",
                                                                                    description = "Categories",
                                                                                    type = Map.class, itemType = String.class,
                                                                                    required = true)))
    static class ChoiceExpert implements SemanticAdapter {
        @Override
        public void validate(SemanticEvaluation evaluation) {
            SemanticCapabilities.from(getClass()).validate(evaluation);
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            return new SemanticResult(
                    "choice-0", null, Map.of(), null, Map.of("taskId", "support", "taskVersion", "v1"),
                    List.of(new SemanticFinding(
                            "category_match", "Matched the supplied category", "choice-expert", null, null)));
        }
    }
}

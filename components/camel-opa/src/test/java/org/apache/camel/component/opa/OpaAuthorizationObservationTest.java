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
package org.apache.camel.component.opa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.security.AuthorizationDecisionEvent;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpaAuthorizationObservationTest {
    @Test
    void observesAllowDenyAndEvaluationErrorWithoutMessageData() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Evaluator evaluator = new Evaluator(false);
            Exchange exchange = fixture.exchange();
            evaluator.decision = true;
            assertThat(evaluator.evaluate(exchange)).isTrue();
            evaluator.decision = false;
            assertThat(evaluator.evaluate(exchange)).isFalse();
            evaluator.failure = new IllegalStateException("secret-error-text");
            assertThatThrownBy(() -> evaluator.evaluate(exchange)).isInstanceOf(OpaPolicyEvaluationException.class);
            assertThat(fixture.events).extracting(AuthorizationDecisionEvent::getOutcome)
                    .containsExactly(Outcome.ALLOW, Outcome.DENY, Outcome.ERROR);
            assertThat(fixture.events).allSatisfy(event -> {
                assertThat(event.getExchangeId()).isEqualTo(exchange.getExchangeId());
                assertThat(event.getRevision()).isNull();
                assertThat(event.getDurationNanos()).isNotNegative();
                assertThat(event.asJSon().toString()).doesNotContain("secret-body", "secret-token", "secret-error-text");
            });
        }
    }

    @Test
    void unavailableAndFailedOpenRemainDistinctFromAnAllow() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Evaluator closed = new Evaluator(false);
            closed.failure = new IOException("secret-network-error");
            assertThatThrownBy(() -> closed.evaluate(fixture.exchange())).isInstanceOf(OpaPolicyEvaluationException.class);
            Evaluator open = new Evaluator(true);
            open.failure = new IOException("secret-network-error");
            Exchange exchange = fixture.exchange();
            assertThat(open.evaluate(exchange)).isTrue();
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isEqualTo(true);
            assertThat(fixture.events).extracting(AuthorizationDecisionEvent::getOutcome)
                    .containsExactly(Outcome.UNAVAILABLE, Outcome.FAILED_OPEN);
        }
    }

    @Test
    void partialBatchPreservesTrueDecisionsFailuresAndFailedOpenCounts() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Evaluator evaluator = new Evaluator(true);
            evaluator.batch = Map.of("0", new OpaPolicyEvaluator.BatchElement(true, null),
                    "1", new OpaPolicyEvaluator.BatchElement(false, null),
                    "2", new OpaPolicyEvaluator.BatchElement(null, new IOException("secret")),
                    "3", new OpaPolicyEvaluator.BatchElement(null, new IllegalStateException("secret")));
            assertThat(evaluator.evaluateBatch(fixture.exchange(), List.of("a", "b", "c", "d")))
                    .containsExactly(true, false, true, false);
            assertThat(fixture.events).hasSize(1);
            assertThat(fixture.events.getFirst().getCounts()).isEqualTo(new AuthorizationDecisionEvent.Counts(1, 1, 1, 0, 1));
            assertThat(fixture.events.getFirst().getOperation()).isEqualTo(AuthorizationDecisionEvent.Operation.BATCH);
        }
    }

    @Test
    void wholeBatchUnavailableCountsAllElementsAndEmptyBatchHasNoDecisions() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Evaluator evaluator = new Evaluator(false);
            evaluator.failure = new IOException("offline");
            assertThatThrownBy(() -> evaluator.evaluateBatch(fixture.exchange(), List.of("a", "b")))
                    .isInstanceOf(OpaPolicyEvaluationException.class);
            assertThat(fixture.events.getFirst().getCounts().unavailable()).isEqualTo(2);
            assertThat(evaluator.evaluateBatch(fixture.exchange(), List.of())).isEmpty();
            assertThat(fixture.events.getLast().getCounts()).isEqualTo(new AuthorizationDecisionEvent.Counts(0, 0, 0, 0, 0));
        }
    }

    private static class Evaluator extends OpaPolicyEvaluator {
        Object decision;
        Exception failure;
        Map<String, BatchElement> batch;

        Evaluator(boolean failOpen) {
            super("policy", "allow", "*", null, true, failOpen);
        }

        @Override
        protected Object evaluateDecision(Map<String, Object> input) throws Exception {
            if (failure != null) {
                throw failure;
            }
            return decision;
        }

        @Override
        protected boolean isDecisionPointUnavailable(Exception failure) {
            return failure instanceof IOException;
        }

        @Override
        protected Map<String, BatchElement> evaluateBatchDecisions(Map<String, Map<String, Object>> inputs) throws Exception {
            if (failure != null) {
                throw failure;
            }
            return batch;
        }
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final List<AuthorizationDecisionEvent> events = new ArrayList<>();

        Fixture() throws Exception {
            context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
                @Override
                public boolean isEnabled(CamelEvent event) {
                    return event instanceof AuthorizationDecisionEvent;
                }

                @Override
                public void notify(CamelEvent event) {
                    events.add((AuthorizationDecisionEvent) event);
                }
            });
            context.start();
        }

        Exchange exchange() {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("secret-body");
            exchange.getMessage().setHeader("Authorization", "secret-token");
            return exchange;
        }

        @Override
        public void close() throws Exception {
            context.close();
        }
    }
}

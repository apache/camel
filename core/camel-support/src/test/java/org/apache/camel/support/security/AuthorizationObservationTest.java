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
package org.apache.camel.support.security;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Engine;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Operation;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationObservationTest {
    @Test
    void noStartedListenerUsesTheSharedDisabledObservation() {
        Exchange exchange = exchange(List.of());
        var first = AuthorizationObservation.begin(exchange, Engine.OPENFGA, Operation.BATCH, "model");
        var second = AuthorizationObservation.begin(exchange, Engine.OPA_REST, Operation.DECISION, null);
        assertThat(first).isSameAs(second);
        assertThat(first.isEnabled()).isFalse();
        first.item(Outcome.ERROR);
        first.completeBatch();
    }

    @Test
    void eventIsImmutableAndContainsOnlySafeDecisionMetadata() {
        Collector collector = new Collector();
        var observation
                = AuthorizationObservation.begin(exchange(List.of(collector)), Engine.OPENFGA, Operation.BATCH, "model-1");
        observation.item(Outcome.ALLOW);
        observation.item(Outcome.DENY);
        observation.item(Outcome.ERROR);
        observation.completeBatch();
        AuthorizationDecisionEvent event = collector.events.getFirst();
        assertThat(event.getType()).isEqualTo(CamelEvent.Type.Custom);
        assertThat(event.getSource()).isEqualTo(Engine.OPENFGA);
        assertThat(event.getExchangeId()).isEqualTo("exchange-1");
        assertThat(event.getRouteId()).isEqualTo("route-1");
        assertThat(event.getRevision()).isEqualTo("model-1");
        assertThat(event.getOutcome()).isEqualTo(Outcome.ERROR);
        assertThat(event.getCounts()).isEqualTo(new AuthorizationDecisionEvent.Counts(1, 1, 1, 0, 0));
        assertThat(event.getDurationNanos()).isNotNegative();
        assertThat(event.getPoolWaitNanos()).isEqualTo(-1);
        assertThat(event.asJSon().keySet()).doesNotContain("exchange", "principal", "resource", "input", "exception", "token");
        assertThatThrownBy(() -> event.setTimestamp(1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> event.asJSon().put("token", "secret")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void brokenListenersCannotChangeOutcomeOrStarveOtherListeners() {
        Collector collector = new Collector();
        EventNotifier brokenOptOut = new Collector() {
            @Override
            public boolean isDisabled() {
                throw new IllegalStateException("secret");
            }
        };
        EventNotifier brokenEnablement = new Collector() {
            @Override
            public boolean isEnabled(CamelEvent event) {
                throw new IllegalStateException("secret");
            }
        };
        EventNotifier brokenNotification = new Collector() {
            @Override
            public void notify(CamelEvent event) {
                throw new IllegalStateException("secret");
            }
        };
        var observation = AuthorizationObservation.begin(
                exchange(List.of(brokenOptOut, brokenEnablement, brokenNotification, collector)),
                Engine.SPIFFE, Operation.MTLS, null);
        observation.complete(Outcome.ALLOW, AuthorizationDecisionEvent.Failure.NONE);
        assertThat(collector.events).hasSize(1);
        assertThat(collector.events.getFirst().getOutcome()).isEqualTo(Outcome.ALLOW);
    }

    @Test
    void notifierOptOutAndEnablementAreHonored() {
        Collector disabled = new Collector() {
            @Override
            public boolean isDisabled() {
                return true;
            }
        };
        Collector ignored = new Collector();
        ignored.setIgnoreExchangeEvents(true);
        Collector filtered = new Collector() {
            @Override
            public boolean isEnabled(CamelEvent event) {
                return false;
            }
        };
        var observation = AuthorizationObservation.begin(exchange(List.of(disabled, ignored, filtered)), Engine.SPIFFE,
                Operation.MTLS, null);
        observation.complete(Outcome.DENY, AuthorizationDecisionEvent.Failure.NONE);
        assertThat(disabled.events).isEmpty();
        assertThat(ignored.events).isEmpty();
        assertThat(filtered.events).isEmpty();
    }

    // Proxies deliberately reject body/header/property access: constructing observations must not inspect message data.
    private static Exchange exchange(List<EventNotifier> notifiers) {
        ManagementStrategy management = proxy(ManagementStrategy.class, (method) -> {
            if (method.equals("getStartedEventNotifiers")) {
                return notifiers;
            }
            throw new UnsupportedOperationException(method);
        });
        CamelContext context = proxy(CamelContext.class, method -> {
            if (method.equals("getManagementStrategy")) {
                return management;
            }
            throw new UnsupportedOperationException(method);
        });
        return proxy(Exchange.class, method -> switch (method) {
            case "getContext" -> context;
            case "getExchangeId" -> "exchange-1";
            case "getFromRouteId" -> "route-1";
            default -> throw new UnsupportedOperationException(method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.Function<String, Object> invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (object, method, args) -> invocation.apply(method.getName()));
    }

    private static class Collector extends SimpleEventNotifierSupport {
        final List<AuthorizationDecisionEvent> events = new ArrayList<>();

        Collector() {
            setIgnoreExchangeEvents(false);
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof AuthorizationDecisionEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            events.add((AuthorizationDecisionEvent) event);
        }
    }
}

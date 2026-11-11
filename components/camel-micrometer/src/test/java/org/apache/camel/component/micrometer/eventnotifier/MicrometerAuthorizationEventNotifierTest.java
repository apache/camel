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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Engine;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Operation;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Outcome;
import org.apache.camel.support.security.AuthorizationObservation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrometerAuthorizationEventNotifierTest {
    @Test
    void revisionsAndCorrelationDoNotCreateAdditionalMetricSeries() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var context = new DefaultCamelContext()) {
            var notifier = new MicrometerAuthorizationEventNotifier();
            notifier.setCamelContext(context);
            notifier.setMeterRegistry(registry);
            context.getManagementStrategy().addEventNotifier(notifier);
            context.start();
            for (int index = 0; index < 2; index++) {
                var exchange = new DefaultExchange(context);
                exchange.getExchangeExtension().setFromRouteId("secret-route-" + index);
                exchange.getMessage().setHeader("Authorization", "secret-token");
                var observation = AuthorizationObservation.begin(exchange, Engine.OPA_WASM, Operation.BATCH,
                        "secret-revision-" + index);
                observation.item(Outcome.ALLOW);
                observation.item(Outcome.DENY);
                observation.item(Outcome.FAILED_OPEN);
                observation.poolWait(100);
                observation.completeBatch();
            }
            var authorizationMeters = registry.getMeters().stream()
                    .filter(meter -> meter.getId().getName().startsWith("camel.authorization.")).toList();
            assertEquals(5, authorizationMeters.size());
            assertEquals(2, registry.get("camel.authorization.duration").timer().count());
            assertEquals(2, registry.get("camel.authorization.pool.wait").timer().count());
            assertEquals(2.0, registry.get("camel.authorization.decisions").tag("outcome", "FAILED_OPEN").counter().count());
            authorizationMeters.forEach(meter -> {
                Set<String> keys = meter.getId().getTags().stream().map(io.micrometer.core.instrument.Tag::getKey)
                        .collect(Collectors.toSet());
                assertTrue(Set.of("engine", "operation", "outcome", "failure").containsAll(keys));
                assertFalse(meter.getId().toString().contains("secret"));
            });
        } finally {
            registry.close();
        }
    }
}

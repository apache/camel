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
package org.apache.camel.component.ai.observability;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-time operator diagnostics when GenAI observability silently falls back to a no-op.
 */
final class GenAiObservabilityDiagnostics {

    private static final Logger LOG = LoggerFactory.getLogger(GenAiObservabilityDiagnostics.class);
    private static final ConcurrentMap<CamelContext, EnumSet<Reason>> REPORTED = new ConcurrentHashMap<>();

    private GenAiObservabilityDiagnostics() {
    }

    static void warnMissingImplementation(CamelContext camelContext) {
        warnOnce(camelContext, Reason.MISSING_IMPLEMENTATION,
                "GenAI observability is enabled but camel-ai-observability is not on the classpath - "
                                                              + "spans and metrics will not be emitted. Add the camel-ai-observability dependency "
                                                              + "(or camel-ai-observability-starter plus the implementation module) to your application.");
    }

    static void warnObservationWithoutTracingHandler(CamelContext camelContext) {
        warnOnce(camelContext, Reason.OBSERVATION_WITHOUT_TRACING_HANDLER,
                "GenAI observations are recorded on an ObservationRegistry without a tracing handler - "
                                                                           + "gen_ai spans will not be exported. Add micrometer-tracing with an OpenTelemetry or Brave bridge, "
                                                                           + "or remove the ObservationRegistry bean so Camel can use camel-opentelemetry2 spans instead.");
    }

    static boolean isExplicitlyEnabled(CamelContext camelContext) {
        if (camelContext == null) {
            return false;
        }
        Optional<String> property
                = camelContext.getPropertiesComponent().resolveProperty(GenAiObservabilityProperties.ENABLED);
        return property.isPresent() && Boolean.parseBoolean(property.get().trim());
    }

    static void resetForTesting() {
        REPORTED.clear();
    }

    private static void warnOnce(CamelContext camelContext, Reason reason, String message) {
        if (camelContext == null) {
            return;
        }
        EnumSet<Reason> reasons = REPORTED.computeIfAbsent(camelContext, ignored -> EnumSet.noneOf(Reason.class));
        synchronized (reasons) {
            if (!reasons.add(reason)) {
                return;
            }
        }
        if (isExplicitlyEnabled(camelContext)) {
            LOG.warn(message);
        } else {
            LOG.info(message);
        }
    }

    private enum Reason {
        MISSING_IMPLEMENTATION,
        OBSERVATION_WITHOUT_TRACING_HANDLER
    }
}

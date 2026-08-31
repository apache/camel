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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Micrometer Observation-backed instrumentation. Loaded reflectively only when {@link ObservationRegistry} is on the
 * classpath.
 */
final class GenAiMicrometerObservationSupport implements GenAiMicrometerObservationBackend {

    private final ObservationRegistry observationRegistry;

    GenAiMicrometerObservationSupport(CamelContext camelContext) {
        ObservationRegistry registry = CamelContextHelper.findSingleByType(camelContext, ObservationRegistry.class);
        this.observationRegistry = isUsable(registry) ? registry : null;
    }

    @Override
    public boolean isAvailable() {
        return observationRegistry != null;
    }

    @Override
    public Handle start(GenAiObservationContext context) {
        if (observationRegistry == null) {
            return null;
        }
        Observation observation = Observation.createNotStarted(GenAiMetrics.CLIENT_OPERATION, observationRegistry);
        observation.contextualName(context.spanName());
        observation.lowCardinalityKeyValue(GenAiAttributes.OPERATION_NAME, context.operationName().value());
        observation.lowCardinalityKeyValue(GenAiAttributes.SYSTEM, nullToUnknown(context.system()));
        observation.lowCardinalityKeyValue(GenAiAttributes.REQUEST_MODEL, nullToUnknown(context.requestModel()));
        if (ObjectHelper.isNotEmpty(context.componentScheme())) {
            observation.lowCardinalityKeyValue(GenAiAttributes.CAMEL_COMPONENT, context.componentScheme());
        }
        observation.start();
        if (observation.isNoop()) {
            return null;
        }
        try {
            return new ObservationHandle(observation, observation.openScope());
        } catch (RuntimeException e) {
            observation.stop();
            throw e;
        }
    }

    private static boolean isUsable(ObservationRegistry registry) {
        return registry != null && registry != ObservationRegistry.NOOP;
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static final class ObservationHandle implements Handle {

        private final Observation observation;
        private final Observation.Scope scope;

        private ObservationHandle(Observation observation, Observation.Scope scope) {
            this.observation = observation;
            this.scope = scope;
        }

        @Override
        public void stop(Throwable error) {
            try {
                if (error != null) {
                    observation.lowCardinalityKeyValue(GenAiAttributes.ERROR_TYPE, error.getClass().getSimpleName());
                    observation.error(error);
                }
            } finally {
                try {
                    if (scope != null) {
                        scope.close();
                    }
                } finally {
                    observation.stop();
                }
            }
        }
    }
}

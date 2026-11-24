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

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Micrometer metrics backend that cannot be linked (such as a mismatched Micrometer jar) disables the metrics only:
 * the calls are still observed.
 */
class GenAiObservabilityMetricsLinkageErrorTest extends ExchangeTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        return new DefaultRegistry() {
            @Override
            public <T> Set<T> findByType(Class<T> type) {
                failForMeterRegistry(type);
                return super.findByType(type);
            }

            @Override
            public <T> T findSingleByType(Class<T> type) {
                failForMeterRegistry(type);
                return super.findSingleByType(type);
            }
        };
    }

    private static void failForMeterRegistry(Class<?> type) {
        if (type == MeterRegistry.class) {
            throw new NoClassDefFoundError("io/micrometer/core/instrument/Meter$Id");
        }
    }

    @Test
    void shouldKeepObservingWhenTheMetricsBackendCannotBeLoaded() {
        AtomicInteger stopped = new AtomicInteger();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStop(Observation.Context context) {
                stopped.incrementAndGet();
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        context.getRegistry().bind("observationRegistry", observationRegistry);

        GenAiObservation observation = GenAiObservability.start(new DefaultExchange(context),
                GenAiObservationContext.builder().operationName(GenAiOperationName.CHAT).requestModel("test-model").build());
        observation.recordSuccess(GenAiUsage.of(1, 1, "stop", "test-model"));
        observation.close();

        assertThat(stopped).hasValue(1);
    }
}

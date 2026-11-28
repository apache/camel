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
package org.apache.camel.component.pqc.metrics;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PQCMicrometerMetricsProviderTest {

    @Test
    void returnsNoopWhenCamelHasNoMeterRegistry() {
        CamelContext context = mock(CamelContext.class);
        Registry registry = mock(Registry.class);
        when(context.getRegistry()).thenReturn(registry);
        when(registry.findByType(MeterRegistry.class)).thenReturn(Set.of());

        assertSame(PQCMetrics.NOOP, new PQCMicrometerMetricsProvider().create(context));
    }

    @Test
    void recordsOperationsAndStatefulKeyGauge() {
        CamelContext context = mock(CamelContext.class);
        Registry registry = mock(Registry.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(context.getRegistry()).thenReturn(registry);
        when(registry.findByType(MeterRegistry.class)).thenReturn(Set.of(meterRegistry));

        PQCMetrics metrics = new PQCMicrometerMetricsProvider().create(context);
        metrics.recordOperation("sign", "ML-DSA", true);
        assertEquals(1.0, meterRegistry.get(PQCMicrometerMetrics.METRIC_OPERATIONS)
                .tag("operation", "sign").tag("algorithm", "ML-DSA").tag("outcome", "success")
                .counter().count());

        AtomicLong remaining = new AtomicLong(7);
        metrics.registerStatefulKeyGauge("XMSS", remaining::get);
        assertEquals(7.0, meterRegistry.get(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING)
                .tag("algorithm", "XMSS").gauge().value());
        remaining.set(6);
        assertEquals(6.0, meterRegistry.get(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING)
                .tag("algorithm", "XMSS").gauge().value());
        metrics.close();
        meterRegistry.close();
    }

    @Test
    void providerIsRegisteredForCamelFactoryFinderDiscovery() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Class<?> providerType = context.getCamelContextExtension()
                    .getFactoryFinder(PQCMetricsProvider.FACTORY_RESOURCE_PATH)
                    .findClass(PQCMetricsProvider.FACTORY_NAME)
                    .orElse(null);
            assertTrue(providerType != null, "camel-pqc must register its optional Micrometer provider");
            assertTrue(PQCMetricsProvider.class.isAssignableFrom(providerType));
        }
    }
}

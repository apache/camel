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

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Micrometer holds the state object of a gauge through a weak reference, so the remaining-signatures supplier has to be
 * kept reachable by this class or the gauge silently degrades to NaN.
 */
class PQCMicrometerMetricsTest {

    @Test
    void theStatefulGaugeKeepsReportingAfterTheSupplierBecomesUnreachable() {
        MeterRegistry registry = new SimpleMeterRegistry();
        PQCMicrometerMetrics metrics = new PQCMicrometerMetrics(registry);

        AtomicLong remaining = new AtomicLong(42);
        // the lambda is reachable only through the metrics instance from here on
        metrics.registerStatefulKeyGauge("LMS", remaining::get);

        forceGc();

        assertEquals(42.0,
                registry.get(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING).tag("algorithm", "LMS").gauge().value(),
                "the gauge must still read the supplier after a collection");

        remaining.set(7);
        assertEquals(7.0,
                registry.get(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING).tag("algorithm", "LMS").gauge().value(),
                "the gauge must track the live value, not a snapshot");
    }

    @Test
    void registeringASecondGaugeDoesNotOrphanTheFirst() {
        MeterRegistry registry = new SimpleMeterRegistry();
        PQCMicrometerMetrics metrics = new PQCMicrometerMetrics(registry);

        metrics.registerStatefulKeyGauge("LMS", () -> 10);
        metrics.registerStatefulKeyGauge("XMSS", () -> 20);

        assertEquals(10.0,
                registry.get(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING).tag("algorithm", "LMS").gauge().value());
        assertEquals(20.0,
                registry.get(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING).tag("algorithm", "XMSS").gauge().value());

        metrics.close();

        assertNull(registry.find(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING).tag("algorithm", "LMS").gauge(),
                "close() must remove every gauge it registered, not only the last one");
        assertNull(registry.find(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING).tag("algorithm", "XMSS").gauge());
    }

    @Test
    void operationCountersAreRecordedAndRemovedOnClose() {
        MeterRegistry registry = new SimpleMeterRegistry();
        PQCMicrometerMetrics metrics = new PQCMicrometerMetrics(registry);

        metrics.recordOperation("sign", "MLDSA", true);
        metrics.recordOperation("sign", "MLDSA", true);
        metrics.recordOperation("sign", "MLDSA", false);

        assertEquals(2.0, registry.get(PQCMicrometerMetrics.METRIC_OPERATIONS)
                .tag("operation", "sign").tag("algorithm", "MLDSA").tag("outcome", "success").counter().count());
        assertEquals(1.0, registry.get(PQCMicrometerMetrics.METRIC_OPERATIONS)
                .tag("operation", "sign").tag("algorithm", "MLDSA").tag("outcome", "failure").counter().count());

        metrics.close();

        assertNull(registry.find(PQCMicrometerMetrics.METRIC_OPERATIONS).counter());
    }

    @Test
    void noMeterRegistryYieldsTheNoopImplementation() {
        assertNotNull(PQCMetrics.NOOP);
        // the no-op must tolerate every call without a registry behind it
        PQCMetrics.NOOP.recordOperation("sign", "MLDSA", true);
        PQCMetrics.NOOP.registerStatefulKeyGauge("LMS", () -> 1);
        PQCMetrics.NOOP.close();
        assertFalse(PQCMetrics.NOOP instanceof PQCMicrometerMetrics);
    }

    /**
     * Collects until a canary weak reference is actually cleared, so the assertion that follows is about the gauge
     * rather than about whether a collection happened to run.
     */
    private static void forceGc() {
        WeakReference<Object> canary = new WeakReference<>(new Object());
        for (int i = 0; i < 100 && canary.get() != null; i++) {
            System.gc();
        }
        assertNull(canary.get(), "no garbage collection was observed, the test below would prove nothing");
    }
}

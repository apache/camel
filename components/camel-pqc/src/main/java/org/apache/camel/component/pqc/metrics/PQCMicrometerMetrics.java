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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.CamelContext;

/**
 * Micrometer-backed {@link PQCMetrics}. All {@code io.micrometer} references live in this class so that it is only
 * loaded when Micrometer is actually present on the classpath (see {@code PQCProducer#createMetrics}).
 * <p/>
 * Note on {@link #close()}: a {@code MeterRegistry} is shared across the JVM and identifies a meter by its id, so two
 * producers whose operation, algorithm and outcome tags coincide share one meter instance. Closing one therefore
 * de-registers a meter the other is still using. Giving each producer a distinguishing tag would fix that, but it
 * changes the published tag set, so it is left alone here.
 */
public class PQCMicrometerMetrics implements PQCMetrics {

    public static final String METRIC_OPERATIONS = "camel.pqc.operations";
    public static final String METRIC_STATEFUL_REMAINING = "camel.pqc.stateful.key.remaining";

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Gauge> statefulKeyGauges = new ConcurrentHashMap<>();
    // Micrometer holds the state object of a gauge through a WeakReference, so the suppliers have to be kept
    // reachable here. Without this the gauge reports NaN as soon as the lambda is collected - and this particular
    // gauge is the one that warns an operator a one-time-use key is running out of signatures.
    private final ConcurrentMap<String, LongSupplier> statefulKeySuppliers = new ConcurrentHashMap<>();

    public PQCMicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Creates a Micrometer-backed metrics instance if a {@code MeterRegistry} is available in the Camel registry,
     * otherwise returns {@link PQCMetrics#NOOP}.
     */
    public static PQCMetrics create(CamelContext camelContext) {
        Set<MeterRegistry> registries = camelContext.getRegistry().findByType(MeterRegistry.class);
        if (registries.isEmpty()) {
            return PQCMetrics.NOOP;
        }
        return new PQCMicrometerMetrics(registries.iterator().next());
    }

    @Override
    public void recordOperation(String operation, String algorithm, boolean success) {
        String outcome = success ? "success" : "failure";
        String key = operation + '|' + algorithm + '|' + outcome;
        counters.computeIfAbsent(key, k -> Counter.builder(METRIC_OPERATIONS)
                .tag("operation", operation)
                .tag("algorithm", algorithm)
                .tag("outcome", outcome)
                .description("Number of PQC operations performed")
                .register(registry))
                .increment();
    }

    @Override
    public void registerStatefulKeyGauge(String algorithm, LongSupplier remainingSupplier) {
        // Held for the lifetime of this instance so Micrometer's weak reference to it stays resolvable
        statefulKeySuppliers.put(algorithm, remainingSupplier);
        statefulKeyGauges.put(algorithm,
                Gauge.builder(METRIC_STATEFUL_REMAINING, remainingSupplier, LongSupplier::getAsLong)
                        .tag("algorithm", algorithm)
                        .description("Remaining signatures for the stateful PQC signature key")
                        .register(registry));
    }

    @Override
    public void close() {
        counters.values().forEach(registry::remove);
        counters.clear();
        statefulKeyGauges.values().forEach(registry::remove);
        statefulKeyGauges.clear();
        statefulKeySuppliers.clear();
    }
}

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

import java.util.function.LongSupplier;

/**
 * Abstraction over the optional Micrometer metrics emitted by the PQC producer.
 * <p/>
 * When the optional {@code camel-pqc-micrometer} integration is present, its implementation is discovered through the
 * Camel-owned {@link PQCMetricsProvider} SPI. This interface deliberately contains no Micrometer types, so the core
 * {@code camel-pqc} artifact remains independent of Micrometer.
 */
public interface PQCMetrics {

    /**
     * A no-op instance used when metrics are disabled.
     */
    PQCMetrics NOOP = new PQCMetrics() {
    };

    /**
     * Records that an operation was performed.
     *
     * @param operation the operation name (for example {@code sign}, {@code verify},
     *                  {@code generateSecretKeyEncapsulation})
     * @param algorithm the PQC algorithm in use
     * @param success   whether the operation completed successfully
     */
    default void recordOperation(String operation, String algorithm, boolean success) {
    }

    /**
     * Registers a gauge reporting the remaining signatures of a stateful signature key.
     *
     * @param algorithm         the stateful signature algorithm (XMSS, XMSSMT, LMS, HSS)
     * @param remainingSupplier supplies the current remaining-signature count (negative when unavailable)
     */
    default void registerStatefulKeyGauge(String algorithm, LongSupplier remainingSupplier) {
    }

    /**
     * Removes any meters registered by this instance.
     */
    default void close() {
    }
}

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
package org.apache.camel.component.pqc;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

/**
 * Health check that reports the state of stateful PQC signature keys (XMSS, XMSSMT, LMS/HSS). These hash-based
 * signature schemes have a finite number of signatures. This health check reports DOWN when a key is exhausted, UP with
 * a {@code warning=true} detail when remaining signatures fall below the warning threshold, and includes remaining
 * signature capacity as a detail.
 */
public class PQCStatefulKeyHealthCheck extends AbstractHealthCheck {

    private final PQCEndpoint endpoint;
    private final PQCProducer producer;

    public PQCStatefulKeyHealthCheck(PQCEndpoint endpoint, PQCProducer producer, String clientId) {
        super("camel", "producer:pqc-stateful-key-" + clientId);
        this.endpoint = endpoint;
        this.producer = producer;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        // Read key state from the producer's runtime keyPair, not from configuration,
        // to avoid stale state when the key has been rotated or updated at runtime
        KeyPair keyPair = producer.getRuntimeKeyPair();

        if (keyPair == null || keyPair.getPrivate() == null) {
            builder.detail("stateful_key", false);
            builder.up();
            return;
        }

        PrivateKey privateKey = keyPair.getPrivate();
        String algorithm = privateKey.getAlgorithm();
        long remaining = PQCProducer.getStatefulKeyRemaining(privateKey);
        long index = PQCProducer.getStatefulKeyIndex(privateKey);

        if (remaining < 0) {
            // Not a stateful key - always healthy
            builder.detail("stateful_key", false);
            builder.detail("algorithm", algorithm);
            builder.up();
            return;
        }

        long totalCapacity = index + remaining;

        builder.detail("stateful_key", true);
        builder.detail("algorithm", algorithm);
        builder.detail("remaining_signatures", remaining);
        builder.detail("signatures_used", index);
        builder.detail("total_capacity", totalCapacity);

        if (remaining == 0) {
            builder.message("Stateful key (" + algorithm + ") is exhausted with 0 remaining signatures");
            builder.down();
            return;
        }

        double threshold = endpoint.getConfiguration().getStatefulKeyWarningThreshold();
        if (threshold > 0 && totalCapacity > 0) {
            double fractionRemaining = (double) remaining / totalCapacity;
            builder.detail("fraction_remaining", String.format("%.4f", fractionRemaining));
            builder.detail("warning_threshold", String.valueOf(threshold));

            if (fractionRemaining <= threshold) {
                builder.message(
                        "Stateful key (" + algorithm + ") is approaching exhaustion: " + remaining
                                + " signatures remaining out of " + totalCapacity + " total ("
                                + String.format("%.1f%%", fractionRemaining * 100) + " remaining)");
                builder.detail("warning", true);
                builder.up();
                return;
            }
        }

        builder.up();
    }
}

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
import org.bouncycastle.pqc.jcajce.interfaces.LMSPrivateKey;
import org.bouncycastle.pqc.jcajce.interfaces.XMSSMTPrivateKey;
import org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey;

/**
 * Health check that reports the state of stateful PQC signature keys (XMSS, XMSSMT, LMS/HSS). These hash-based
 * signature schemes have a finite number of signatures. This health check reports DOWN when a key is exhausted and
 * includes remaining signature capacity as a detail.
 */
public class PQCStatefulKeyHealthCheck extends AbstractHealthCheck {

    private final PQCEndpoint endpoint;

    public PQCStatefulKeyHealthCheck(PQCEndpoint endpoint, String clientId) {
        super("camel", "producer:pqc-stateful-key-" + clientId);
        this.endpoint = endpoint;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        PQCConfiguration configuration = endpoint.getConfiguration();
        KeyPair keyPair = configuration.getKeyPair();

        if (keyPair == null || keyPair.getPrivate() == null) {
            builder.detail("stateful_key", false);
            builder.up();
            return;
        }

        PrivateKey privateKey = keyPair.getPrivate();
        long remaining = -1;
        long index = 0;
        String algorithm = privateKey.getAlgorithm();

        if (privateKey instanceof XMSSPrivateKey) {
            XMSSPrivateKey xmssKey = (XMSSPrivateKey) privateKey;
            remaining = xmssKey.getUsagesRemaining();
            index = xmssKey.getIndex();
        } else if (privateKey instanceof XMSSMTPrivateKey) {
            XMSSMTPrivateKey xmssmtKey = (XMSSMTPrivateKey) privateKey;
            remaining = xmssmtKey.getUsagesRemaining();
            index = xmssmtKey.getIndex();
        } else if (privateKey instanceof LMSPrivateKey) {
            LMSPrivateKey lmsKey = (LMSPrivateKey) privateKey;
            remaining = lmsKey.getUsagesRemaining();
            index = lmsKey.getIndex();
        }

        if (remaining < 0) {
            // Not a stateful key - always healthy
            builder.detail("stateful_key", false);
            builder.detail("algorithm", algorithm);
            builder.up();
            return;
        }

        builder.detail("stateful_key", true);
        builder.detail("algorithm", algorithm);
        builder.detail("remaining_signatures", remaining);
        builder.detail("signatures_used", index);
        builder.detail("total_capacity", index + remaining);

        if (remaining <= 0) {
            builder.message("Stateful key (" + algorithm + ") is exhausted with 0 remaining signatures");
            builder.down();
            return;
        }

        double threshold = configuration.getStatefulKeyWarningThreshold();
        long totalCapacity = index + remaining;
        if (threshold > 0 && totalCapacity > 0) {
            double fractionRemaining = (double) remaining / totalCapacity;
            builder.detail("fraction_remaining", String.format("%.4f", fractionRemaining));
            builder.detail("warning_threshold", String.valueOf(threshold));
        }

        builder.up();
    }
}

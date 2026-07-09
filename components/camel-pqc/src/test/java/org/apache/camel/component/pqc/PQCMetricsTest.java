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

import java.security.Security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.metrics.PQCMicrometerMetrics;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PQCMetricsTest extends CamelTestSupport {

    @BindToRegistry
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @EndpointInject("mock:sign")
    protected MockEndpoint resultSign;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    @BeforeAll
    public static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA")
                        .to("mock:sign")
                        .to("pqc:verify?operation=verify&signatureAlgorithm=MLDSA");
            }
        };
    }

    @Test
    void testProducerRecordsOperationMetrics() throws Exception {
        resultSign.expectedMessageCount(1);
        templateSign.sendBody("Hello PQC");
        resultSign.assertIsSatisfied();

        Counter sign = meterRegistry.find(PQCMicrometerMetrics.METRIC_OPERATIONS)
                .tag("operation", "sign").tag("algorithm", "MLDSA").tag("outcome", "success").counter();
        assertNotNull(sign, "sign success counter should be registered");
        assertTrue(sign.count() >= 1.0);

        Counter verify = meterRegistry.find(PQCMicrometerMetrics.METRIC_OPERATIONS)
                .tag("operation", "verify").tag("outcome", "success").counter();
        assertNotNull(verify, "verify success counter should be registered");
        assertTrue(verify.count() >= 1.0);
    }

    @Test
    void testMicrometerMetricsDirectly() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        PQCMicrometerMetrics m = new PQCMicrometerMetrics(reg);

        m.recordOperation("sign", "MLDSA", true);
        m.recordOperation("sign", "MLDSA", true);
        m.recordOperation("sign", "MLDSA", false);
        m.registerStatefulKeyGauge("XMSS", () -> 42L);

        Counter ok = reg.find(PQCMicrometerMetrics.METRIC_OPERATIONS)
                .tag("operation", "sign").tag("outcome", "success").counter();
        assertNotNull(ok);
        assertEquals(2.0, ok.count());

        Counter fail = reg.find(PQCMicrometerMetrics.METRIC_OPERATIONS)
                .tag("operation", "sign").tag("outcome", "failure").counter();
        assertNotNull(fail);
        assertEquals(1.0, fail.count());

        Gauge gauge = reg.find(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING).tag("algorithm", "XMSS").gauge();
        assertNotNull(gauge);
        assertEquals(42.0, gauge.value());

        // close() removes the meters it registered
        m.close();
        assertNull(reg.find(PQCMicrometerMetrics.METRIC_OPERATIONS).counter());
        assertNull(reg.find(PQCMicrometerMetrics.METRIC_STATEFUL_REMAINING).gauge());
    }
}

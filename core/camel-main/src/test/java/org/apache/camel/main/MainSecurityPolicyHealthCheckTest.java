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
package org.apache.camel.main;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.impl.health.SecurityPolicyHealthCheck;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainSecurityPolicyHealthCheckTest {

    @Test
    public void testHealthCheckUpWhenNoViolations() {
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "warn");
        main.start();
        try {
            CamelContext ctx = main.getCamelContext();
            SecurityPolicyHealthCheck hc = new SecurityPolicyHealthCheck();
            hc.setCamelContext(ctx);

            HealthCheck.Result result = hc.call();
            assertNotNull(result);
            assertEquals(HealthCheck.State.UP, result.getState());

            Map<String, Object> details = result.getDetails();
            assertEquals(0, details.get("security.violations.count"));
            assertEquals("clean", details.get("security.status"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testHealthCheckUpWithWarnViolations() {
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "warn");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");
        main.start();
        try {
            CamelContext ctx = main.getCamelContext();
            SecurityPolicyHealthCheck hc = new SecurityPolicyHealthCheck();
            hc.setCamelContext(ctx);

            HealthCheck.Result result = hc.call();
            assertNotNull(result);
            // warn-level violations → UP (not DOWN)
            assertEquals(HealthCheck.State.UP, result.getState());

            Map<String, Object> details = result.getDetails();
            assertTrue((int) details.get("security.violations.count") > 0);
            assertNotNull(details.get("security.violation.0.category"));
            assertNotNull(details.get("security.violation.0.property"));
            assertNotNull(details.get("security.violation.0.policy"));
            assertEquals("warn", details.get("security.violation.0.policy"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testHealthCheckDownWithFailViolations() {
        // Simulate fail-level violations by manually setting a SecurityPolicyResult
        // (can't use real fail policy as it throws at startup)
        Main main = new Main();
        main.start();
        try {
            CamelContext ctx = main.getCamelContext();

            // create result with a fail-level violation
            SecurityPolicyResult policyResult = new SecurityPolicyResult(
                    java.util.List.of(
                            new org.apache.camel.util.SecurityViolation(
                                    "insecure:ssl", "camel.ssl.trustAllCertificates",
                                    "Insecure SSL", "fail")));
            ctx.getCamelContextExtension().addContextPlugin(SecurityPolicyResult.class, policyResult);

            SecurityPolicyHealthCheck hc = new SecurityPolicyHealthCheck();
            hc.setCamelContext(ctx);

            HealthCheck.Result result = hc.call();
            assertNotNull(result);
            // fail-level violations → DOWN
            assertEquals(HealthCheck.State.DOWN, result.getState());

            Map<String, Object> details = result.getDetails();
            assertEquals(1, details.get("security.violations.count"));
            assertEquals("fail", details.get("security.violation.0.policy"));
            assertEquals("insecure:ssl", details.get("security.violation.0.category"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testHealthCheckUpWhenAllAllowed() {
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "allow");
        main.addInitialProperty("camel.ssl.keystorePassword", "plaintext-password");
        main.start();
        try {
            CamelContext ctx = main.getCamelContext();
            SecurityPolicyHealthCheck hc = new SecurityPolicyHealthCheck();
            hc.setCamelContext(ctx);

            HealthCheck.Result result = hc.call();
            assertNotNull(result);
            assertEquals(HealthCheck.State.UP, result.getState());

            Map<String, Object> details = result.getDetails();
            assertEquals(0, details.get("security.violations.count"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testHealthCheckWithNullContext() {
        SecurityPolicyHealthCheck hc = new SecurityPolicyHealthCheck();
        // no CamelContext set

        HealthCheck.Result result = hc.call();
        assertNotNull(result);
        assertEquals(HealthCheck.State.DOWN, result.getState());
    }

    @Test
    public void testHealthCheckReadinessOnly() {
        SecurityPolicyHealthCheck hc = new SecurityPolicyHealthCheck();
        assertTrue(hc.isReadiness());
        assertEquals(false, hc.isLiveness());
    }
}

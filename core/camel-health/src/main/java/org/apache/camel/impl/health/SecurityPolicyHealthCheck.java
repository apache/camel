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
package org.apache.camel.impl.health;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.SecurityViolation;

/**
 * {@link org.apache.camel.health.HealthCheck} that reports security policy violations detected at startup.
 * <p>
 * This health check reads the {@code SecurityPolicyResult} stored as a context plugin by the security policy
 * enforcement during startup. If violations were found, the health check reports DOWN with details about each
 * violation.
 * <p>
 * This is a readiness check only — security violations don't mean the application is dead (liveness), but they indicate
 * it may not be ready for production traffic.
 */
@org.apache.camel.spi.annotations.HealthCheck("security-policy-check")
public final class SecurityPolicyHealthCheck extends AbstractHealthCheck {

    private static final String RESULT_CLASS = "org.apache.camel.main.SecurityPolicyResult";

    public SecurityPolicyHealthCheck() {
        super("camel", "security-policy");
    }

    @Override
    public boolean isReadiness() {
        return true;
    }

    @Override
    public boolean isLiveness() {
        return false;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        builder.unknown();

        if (getCamelContext() == null) {
            builder.message("CamelContext not available");
            builder.down();
            return;
        }

        // use reflection to avoid a compile-time dependency on camel-main from camel-health
        Object result = null;
        try {
            Class<?> resultClass = getCamelContext().getClassResolver().resolveClass(RESULT_CLASS);
            if (resultClass != null) {
                result = getCamelContext().getCamelContextExtension().getContextPlugin(resultClass);
            }
        } catch (Exception e) {
            // class not available — camel-main not in use
        }

        if (result == null) {
            // no security policy result available — security enforcement not active
            builder.detail("security.enforcement", "not-active");
            builder.up();
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            List<SecurityViolation> violations
                    = (List<SecurityViolation>) result.getClass().getMethod("getViolations").invoke(result);

            builder.detail("security.violations.count", violations.size());

            if (violations.isEmpty()) {
                builder.detail("security.status", "clean");
                builder.up();
            } else {
                StringJoiner sj = new StringJoiner("; ");
                boolean hasFailures = false;
                int i = 0;
                for (SecurityViolation v : violations) {
                    String prefix = "security.violation." + i + ".";
                    builder.detail(prefix + "category", v.category());
                    builder.detail(prefix + "property", v.propertyKey());
                    builder.detail(prefix + "message", v.message());
                    builder.detail(prefix + "policy", v.policy());
                    sj.add(v.toString());
                    if ("fail".equals(v.policy())) {
                        hasFailures = true;
                    }
                    i++;
                }
                builder.message("Security policy violations detected: " + sj);
                // only report DOWN for fail-level violations; warn-level are informational
                if (hasFailures) {
                    builder.down();
                } else {
                    builder.up();
                }
            }
        } catch (Exception e) {
            builder.message("Error reading security policy result: " + e.getMessage());
            builder.error(e);
            builder.down();
        }
    }
}

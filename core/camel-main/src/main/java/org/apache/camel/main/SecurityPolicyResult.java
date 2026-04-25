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

import java.util.Collections;
import java.util.List;

import org.apache.camel.util.SecurityViolation;

/**
 * Holds the results of security policy evaluation at startup.
 * <p>
 * Registered as a context plugin so that health checks and other components can access the security policy violations
 * detected during startup.
 *
 * @since 4.19.0
 */
public final class SecurityPolicyResult {

    private final List<SecurityViolation> violations;

    public SecurityPolicyResult(List<SecurityViolation> violations) {
        this.violations = violations != null ? Collections.unmodifiableList(violations) : Collections.emptyList();
    }

    /**
     * All security violations detected at startup.
     */
    public List<SecurityViolation> getViolations() {
        return violations;
    }

    /**
     * Whether any violations were detected.
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    /**
     * Number of violations detected.
     */
    public int getViolationCount() {
        return violations.size();
    }
}

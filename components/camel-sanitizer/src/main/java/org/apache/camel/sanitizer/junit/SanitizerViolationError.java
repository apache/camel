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
package org.apache.camel.sanitizer.junit;

import java.util.List;

import org.apache.camel.sanitizer.SanitizerViolation;

/**
 * The failure of a test whose CamelContext broke a rule of the exchange sanitizer: its message has the first violation
 * and the reports of the others, up to ten violations in all.
 *
 * @since 4.24
 */
public class SanitizerViolationError extends AssertionError {

    private static final long serialVersionUID = 1L;

    private static final int MAX_IN_MESSAGE = 10;

    private final transient List<SanitizerViolation> violations;

    SanitizerViolationError(String test, List<SanitizerViolation> violations) {
        super(message(test, violations));
        this.violations = List.copyOf(violations);
    }

    /**
     * The violations charged to the test.
     */
    public List<SanitizerViolation> getViolations() {
        return violations;
    }

    private static String message(String test, List<SanitizerViolation> violations) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append(violations.size()).append(violations.size() == 1 ? " violation" : " violations")
                .append(" of the exchange sanitizer in ").append(test).append(". The first:\n");
        sb.append(violations.get(0).getText());
        for (int i = 1; i < violations.size() && i < MAX_IN_MESSAGE; i++) {
            sb.append("\n\n").append(violations.get(i).getText());
        }
        if (violations.size() > MAX_IN_MESSAGE) {
            sb.append("\n\n(").append(violations.size() - MAX_IN_MESSAGE).append(" more)");
        }
        return sb.toString();
    }
}

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
package org.apache.camel.component.cedar;

import java.util.List;
import java.util.Set;

/** Immutable, input-free authorization response. Error diagnostics omit engine text that may contain input values. */
public record CedarAuthorizationResult(Outcome outcome, Decision engineDecision, Set<String> policyIds,
        List<Diagnostic> diagnostics, String revision) {
    public enum Outcome {
        ALLOW,
        DENY,
        ERROR
    }

    public enum Decision {
        ALLOW,
        DENY,
        UNKNOWN
    }

    public record Diagnostic(String policyId, String code) {
    }

    public CedarAuthorizationResult {
        policyIds = Set.copyOf(policyIds);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean isAllowed() {
        return outcome == Outcome.ALLOW;
    }

    @Override
    public String toString() {
        return "CedarAuthorizationResult[outcome=" + outcome + ", engineDecision=" + engineDecision
               + ", diagnostics=" + diagnostics.size() + ", revision=" + revision + "]";
    }
}

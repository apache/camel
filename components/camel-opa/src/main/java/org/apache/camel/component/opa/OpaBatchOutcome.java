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
package org.apache.camel.component.opa;

/**
 * An immutable batch element outcome, distinguishing a policy decision from a failure to obtain one.
 * <p/>
 * The decision is the normalized Boolean policy verdict, not the raw decision document. Failure messages and exceptions
 * are deliberately omitted: they can contain request data or credentials.
 */
public final class OpaBatchOutcome {

    public enum FailureCategory {
        NONE,
        UNAVAILABLE,
        EVALUATION_ERROR,
        MISSING_RESULT,
        INTERRUPTED
    }

    private final boolean allowed;
    private final Boolean decision;
    private final FailureCategory failureCategory;
    private final boolean failedOpen;

    private OpaBatchOutcome(boolean allowed, Boolean decision, FailureCategory failureCategory, boolean failedOpen) {
        this.allowed = allowed;
        this.decision = decision;
        this.failureCategory = failureCategory;
        this.failedOpen = failedOpen;
    }

    static OpaBatchOutcome decision(boolean allowed) {
        return new OpaBatchOutcome(allowed, allowed, FailureCategory.NONE, false);
    }

    static OpaBatchOutcome failure(FailureCategory category, boolean failedOpen) {
        return new OpaBatchOutcome(failedOpen, null, category, failedOpen);
    }

    /** Whether this element is allowed to proceed, including an explicit fail-open result. */
    public boolean isAllowed() {
        return allowed;
    }

    /** The policy's normalized Boolean verdict, or null when no decision was obtained. */
    public Boolean getDecision() {
        return decision;
    }

    /** Why no decision was obtained, or NONE when a policy decision was obtained. */
    public FailureCategory getFailureCategory() {
        return failureCategory;
    }

    /** Whether the element proceeded only because failOpen was enabled and its decision point was unavailable. */
    public boolean isFailedOpen() {
        return failedOpen;
    }
}

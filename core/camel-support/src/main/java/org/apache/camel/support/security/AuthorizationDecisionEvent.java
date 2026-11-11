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
package org.apache.camel.support.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.spi.CamelEvent;

/**
 * Immutable authorization observation, without credentials, subjects, resources, inputs, exceptions or Exchange
 * references. Correlation and configured revision are event fields, never default metric labels. Unknown revisions are
 * null.
 */
public final class AuthorizationDecisionEvent implements CamelEvent {
    public enum Engine {
        OPA_REST,
        OPA_WASM,
        OPENFGA,
        SPIFFE
    }

    public enum Operation {
        DECISION,
        BATCH,
        MTLS,
        VALIDATE_JWT
    }

    public enum Outcome {
        ALLOW,
        DENY,
        ERROR,
        UNAVAILABLE,
        FAILED_OPEN
    }

    public enum Failure {
        NONE,
        EVALUATION,
        UNAVAILABLE,
        INTERRUPTED
    }

    /** Counts distinguish actual decisions from elements that never obtained a decision. */
    public static final class Counts {
        private final long allowed;
        private final long denied;
        private final long errors;
        private final long unavailable;
        private final long failedOpen;

        public Counts(long allowed, long denied, long errors, long unavailable, long failedOpen) {
            this.allowed = allowed;
            this.denied = denied;
            this.errors = errors;
            this.unavailable = unavailable;
            this.failedOpen = failedOpen;
        }

        public long allowed() {
            return allowed;
        }

        public long denied() {
            return denied;
        }

        public long errors() {
            return errors;
        }

        public long unavailable() {
            return unavailable;
        }

        public long failedOpen() {
            return failedOpen;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Counts counts)) {
                return false;
            }
            return allowed == counts.allowed && denied == counts.denied && errors == counts.errors
                    && unavailable == counts.unavailable && failedOpen == counts.failedOpen;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(allowed, denied, errors, unavailable, failedOpen);
        }

        @Override
        public String toString() {
            return "Counts[allowed=" + allowed + ", denied=" + denied + ", errors=" + errors
                   + ", unavailable=" + unavailable + ", failedOpen=" + failedOpen + "]";
        }
    }

    private final Engine engine;
    private final Operation operation;
    private final Outcome outcome;
    private final Failure failure;
    private final long durationNanos;
    private final long poolWaitNanos;
    private final Counts counts;
    private final String revision;
    private final String exchangeId;
    private final String routeId;
    private final long timestamp;

    AuthorizationDecisionEvent(Engine engine, Operation operation, Outcome outcome, Failure failure, long durationNanos,
                               long poolWaitNanos, Counts counts, String revision, String exchangeId, String routeId) {
        this.engine = engine;
        this.operation = operation;
        this.outcome = outcome;
        this.failure = failure;
        this.durationNanos = durationNanos;
        this.poolWaitNanos = poolWaitNanos;
        this.counts = counts;
        this.revision = revision;
        this.exchangeId = exchangeId;
        this.routeId = routeId;
        this.timestamp = System.currentTimeMillis();
    }

    public Engine getEngine() {
        return engine;
    }

    public Operation getOperation() {
        return operation;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Failure getFailure() {
        return failure;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    /** Borrow wait, including a failed borrow, or -1 when this operation does not use a WASM pool. */
    public long getPoolWaitNanos() {
        return poolWaitNanos;
    }

    public Counts getCounts() {
        return counts;
    }

    /** Configured model revision, or null when the actual revision is not known. */
    public String getRevision() {
        return revision;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public String getRouteId() {
        return routeId;
    }

    @Override
    public Type getType() {
        return Type.Custom;
    }

    @Override
    public Object getSource() {
        return engine;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        throw new UnsupportedOperationException("Immutable authorization event");
    }

    @Override
    public Map<String, Object> asJSon() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("engine", engine.name());
        values.put("operation", operation.name());
        values.put("outcome", outcome.name());
        values.put("failure", failure.name());
        values.put("durationNanos", durationNanos);
        values.put("poolWaitNanos", poolWaitNanos);
        values.put("counts", Map.of("allowed", counts.allowed(), "denied", counts.denied(), "errors", counts.errors(),
                "unavailable", counts.unavailable(), "failedOpen", counts.failedOpen()));
        values.put("revision", revision);
        values.put("exchangeId", exchangeId);
        values.put("routeId", routeId);
        values.put("timestamp", timestamp);
        return java.util.Collections.unmodifiableMap(values);
    }

    @Override
    public String toString() {
        return "AuthorizationDecisionEvent[engine=" + engine + ", operation=" + operation + ", outcome=" + outcome + "]";
    }
}

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
package org.apache.camel.semantic;

/**
 * Independent semantic lifecycle consumer, for telemetry or application observations. Registered observers run on the
 * invocation thread and must not block. Callbacks must restore any thread context before returning; an observation is
 * not an attached tracing scope. Batch members can overlap, so each span uses the supplied parent explicitly and
 * remains detached between callbacks. This also avoids putting unrelated batch members in a span tree. No OpenTelemetry
 * or provider classes are part of this contract. Audit filtering and trace sampling do not govern each other. All
 * callbacks receive the same bounded safe snapshots. Register beans before context startup: observers are discovered
 * once when the audit service starts, independently of whether audit capture is enabled.
 */
public interface SemanticObserver {
    /**
     * Capture an implementation-specific parent context before an executor handoff. Never put it in an audit record.
     */
    default Object captureContext() {
        return null;
    }

    /** Start a detached invocation observation using the supplied parent context. */
    Observation started(SemanticAuditRecord record, Object parentContext);

    default void decision(SemanticAuditRecord record) {
    }

    interface Observation {
        /** Finish on the invocation thread, including on failure. Restore temporary context before returning. */
        void completed(SemanticAuditRecord record);
    }
}

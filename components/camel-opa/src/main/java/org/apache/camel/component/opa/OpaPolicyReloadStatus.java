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

import java.time.Instant;

/**
 * An immutable view of the active WebAssembly revision, the last successful check of the configured resource and the
 * last failed reload. A later successful check does not clear the last failure, so it stays visible until another
 * reload fails.
 */
public final class OpaPolicyReloadStatus {

    private final String activeRevision;
    private final Instant lastSuccessfulCheck;
    private final Instant lastFailedReload;
    private final String lastReloadFailureType;

    OpaPolicyReloadStatus(String activeRevision, Instant lastSuccessfulCheck, Instant lastFailedReload,
                          String lastReloadFailureType) {
        this.activeRevision = activeRevision;
        this.lastSuccessfulCheck = lastSuccessfulCheck;
        this.lastFailedReload = lastFailedReload;
        this.lastReloadFailureType = lastReloadFailureType;
    }

    /** The SHA-256 hex digest of the active WASM module and its data document. */
    public String getActiveRevision() {
        return activeRevision;
    }

    /**
     * When the configured resource was last read and found valid: the initial load, a reload that published a new
     * revision, or a poll that found the content unchanged. Never null.
     */
    public Instant getLastSuccessfulCheck() {
        return lastSuccessfulCheck;
    }

    /** When a reload last failed, or {@code null} if none has failed. Kept after later successful checks. */
    public Instant getLastFailedReload() {
        return lastFailedReload;
    }

    /**
     * The class name of the exception of the last failed reload, or {@code null} if none has failed. Only the class:
     * messages and resource locations are not exposed through the status.
     */
    public String getLastReloadFailureType() {
        return lastReloadFailureType;
    }
}

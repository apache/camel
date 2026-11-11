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

/** An immutable view of the active WebAssembly revision and the last reload attempt. */
public final class OpaPolicyReloadStatus {

    private final String activeRevision;
    private final Instant lastSuccessfulReload;
    private final Instant lastFailedReload;
    private final String lastReloadFailureType;

    OpaPolicyReloadStatus(String activeRevision, Instant lastSuccessfulReload, Instant lastFailedReload,
                          String lastReloadFailureType) {
        this.activeRevision = activeRevision;
        this.lastSuccessfulReload = lastSuccessfulReload;
        this.lastFailedReload = lastFailedReload;
        this.lastReloadFailureType = lastReloadFailureType;
    }

    public String getActiveRevision() {
        return activeRevision;
    }

    public Instant getLastSuccessfulReload() {
        return lastSuccessfulReload;
    }

    public Instant getLastFailedReload() {
        return lastFailedReload;
    }

    /** Failure class only; messages and resource locations are not exposed through status. */
    public String getLastReloadFailureType() {
        return lastReloadFailureType;
    }
}

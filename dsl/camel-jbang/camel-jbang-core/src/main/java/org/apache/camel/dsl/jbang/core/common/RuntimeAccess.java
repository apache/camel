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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.util.List;

import org.apache.camel.util.json.JsonObject;

/**
 * Framework-neutral access to running Camel runtimes.
 * <p>
 * Implementations own the transport and the mapping from an opaque {@link RuntimeTarget} to that transport. The default
 * implementation is local file-based IPC; a remote implementation can use the same contract without making the JBang
 * core depend on MCP, CDI, Quarkus, Spring Boot, or a particular network protocol.
 *
 */
public interface RuntimeAccess {

    /** Discovers the runtimes visible in this access scope. */
    List<RuntimeTarget> discoverProcesses();

    /**
     * Finds one target by name, PID string, or implementation-specific stable identifier. Returns {@code null} when no
     * unique target matches.
     */
    RuntimeTarget findProcess(String selector);

    /** Resolves the one runtime suitable for a chaos tool, following the access implementation's selection rules. */
    RuntimeTarget findChaosProcess();

    /**
     * Creates/resolves a target from a PID for legacy callers. New code should keep and pass a {@link RuntimeTarget}
     * instead of reducing it to a PID.
     */
    RuntimeTarget targetForPid(long pid);

    /** Reads the latest status snapshot, or {@code null} when no snapshot is available. */
    JsonObject readStatus(RuntimeTarget target);

    /** Sends an action that is expected to be confirmed, even when it has no result payload. */
    ActionResult sendAction(RuntimeTarget target, JsonObject request, long timeoutMs) throws IOException;

    /** Runs an action and returns the raw textual response. Returns {@code null} on timeout. */
    String executeActionText(RuntimeTarget target, JsonObject request, long timeoutMs) throws IOException;

    /** Reads the most recently captured errors, or {@code null} when none are available. */
    JsonObject readErrorFile(RuntimeTarget target);

    /** Reads the message history, or {@code null} when none is available. */
    JsonObject readHistoryFile(RuntimeTarget target);

    /**
     * Captures an opaque receive-stream cursor. The token is meaningful only to the implementation that created it;
     * callers must not assume that it is a file offset or sequence number.
     */
    String receiveCursor(RuntimeTarget target);

    /** Reads receive messages published after the supplied opaque cursor. */
    List<JsonObject> readReceivedMessages(RuntimeTarget target, String cursor);

    /** Requests a graceful application shutdown. */
    String stopApplication(RuntimeTarget target);

    /** Sends one or more messages to an endpoint in the target runtime. */
    JsonObject sendMessage(
            RuntimeTarget target, String endpoint, String body, String headers, int repeat, String interval);

    /** Executes a SQL query in the target runtime. */
    JsonObject executeSqlQuery(
            RuntimeTarget target, String sql, String datasource, int maxRows, int queryTimeout);

    /** Returns the Camel version reported by the target, or {@code null} when it is unknown. */
    String runtimeVersion(RuntimeTarget target);

    enum ActionStatus {
        OK,
        ERROR,
        TIMEOUT,
        UNCONFIRMED
    }

    /** The transport-neutral outcome of a confirmed action. */
    record ActionResult(ActionStatus status, String error, String reason) {
        public boolean isOk() {
            return status == ActionStatus.OK || status == ActionStatus.UNCONFIRMED;
        }
    }

    /** Signals that the target stopped before it answered an action. */
    final class RuntimeStoppedException extends IOException {
        private static final long serialVersionUID = 1L;

        public RuntimeStoppedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

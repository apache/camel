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

/**
 * An opaque reference to a running Camel runtime.
 * <p>
 * The {@code id} is the identity used by a {@link RuntimeAccess} implementation. It is not a PID and must not be
 * derived from one by callers. A PID is optional metadata: it is available for local processes, but a remote runtime
 * need not expose one, and PIDs are not globally unique.
 *
 * @param id          stable identifier within the runtime access scope
 * @param pid         operating-system PID when the runtime is local or otherwise exposes one; may be {@code null}
 * @param name        integration name; may be {@code null}
 * @param contextName Camel context name; may be {@code null}
 * @param transport   access transport such as {@code file} or {@code websocket}; may be {@code null}
 */
public record RuntimeTarget(String id, Long pid, String name, String contextName, String transport) {

    public RuntimeTarget {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Runtime target id must not be blank");
        }
    }

    /** Creates a local file-IPC target for a PID that is already known by the caller. */
    public static RuntimeTarget local(long pid) {
        return new RuntimeTarget("file:" + pid, pid, null, null, "file");
    }

    /** Creates a local file-IPC target from process discovery information. */
    public static RuntimeTarget local(RuntimeHelper.ProcessInfo process) {
        return new RuntimeTarget("file:" + process.pid(), process.pid(), process.name(), process.contextName(), "file");
    }

    /** A short label that can be used in diagnostics and process lists. */
    public String displayName() {
        return name != null && !name.isBlank() ? name : id;
    }

    /** The identity to show to a user without implying that every runtime has a PID. */
    public String identityLabel() {
        return pid != null ? "PID " + pid : "ID " + id;
    }
}

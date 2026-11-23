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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/** Stable server-side identity and bounded last-known state for one remote integration. */
final class RemoteRuntimeEntry {

    private final String ownerId;
    private final String executionId;
    private final int maxSnapshotChars;
    private final int maxRetainedMessages;
    private final Map<String, JsonObject> snapshots = new LinkedHashMap<>();
    private final LinkedHashMap<Long, JsonObject> traces = new LinkedHashMap<>();
    private final LinkedHashMap<Long, JsonObject> received = new LinkedHashMap<>();

    private volatile JsonObject hello;
    private volatile RemoteRuntimeConnection connection;
    private volatile long lastSeen = System.currentTimeMillis();

    RemoteRuntimeEntry(String ownerId, String executionId, int maxSnapshotChars, int maxRetainedMessages) {
        this.ownerId = ownerId;
        this.executionId = executionId;
        this.maxSnapshotChars = maxSnapshotChars;
        this.maxRetainedMessages = maxRetainedMessages;
    }

    String ownerId() {
        return ownerId;
    }

    String executionId() {
        return executionId;
    }

    long lastSeen() {
        return lastSeen;
    }

    boolean isConnected() {
        RemoteRuntimeConnection current = connection;
        return current != null && current.isOpen();
    }

    boolean isReady() {
        RemoteRuntimeConnection current = connection;
        return current != null && current.isReady();
    }

    synchronized RemoteRuntimeConnection attach(RemoteRuntimeConnection next) {
        RemoteRuntimeConnection previous = connection;
        connection = next;
        touch();
        return previous;
    }

    synchronized boolean detach(RemoteRuntimeConnection expected) {
        if (connection != expected) {
            return false;
        }
        connection = null;
        touch();
        return true;
    }

    synchronized boolean isCurrent(RemoteRuntimeConnection candidate) {
        return connection == candidate;
    }

    synchronized RemoteRuntimeConnection expireConnectionIfIdle(long now, long timeoutMillis) {
        RemoteRuntimeConnection current = connection;
        if (current != null && now - lastSeen > timeoutMillis) {
            connection = null;
            lastSeen = now;
            return current;
        }
        return null;
    }

    synchronized void touch() {
        lastSeen = System.currentTimeMillis();
    }

    /** Updates integration metadata; each connection independently enforces one hello frame. */
    synchronized void updateHello(JsonObject frame) {
        Long previousPid = runtimePid(hello);
        Long newPid = runtimePid(frame);
        // executionId survives an integration restart, but the connector's per-process UIDs restart too.
        // Clear process-scoped snapshots only when the runtime PID proves a new process took over this identity.
        if (previousPid != null && newPid != null && !previousPid.equals(newPid)) {
            snapshots.clear();
            traces.clear();
            received.clear();
        }
        hello = new JsonObject(frame);
        touch();
    }

    private static Long runtimePid(JsonObject hello) {
        if (hello == null || !(hello.get("runtime") instanceof JsonObject runtime)) {
            return null;
        }
        Object pid = runtime.get("pid");
        return pid instanceof Number number ? number.longValue() : null;
    }

    synchronized void updateSnapshot(String kind, JsonObject data) {
        if (data == null) {
            throw new IllegalArgumentException("Snapshot data is required");
        }
        String collectionKey;
        LinkedHashMap<Long, JsonObject> retained;
        if ("trace".equals(kind)) {
            collectionKey = "traces";
            retained = traces;
        } else if ("receive".equals(kind)) {
            collectionKey = "messages";
            retained = received;
        } else {
            String serialized = data.toJson();
            if (serialized.length() > maxSnapshotChars) {
                throw new IllegalArgumentException("Snapshot exceeds the configured size limit");
            }
            snapshots.put(kind, new JsonObject(data));
            touch();
            return;
        }

        List<JsonObject> batch = data.getCollection(collectionKey);
        if (batch != null) {
            for (JsonObject message : batch) {
                Object uidValue = message.get("uid");
                if (!(uidValue instanceof Number uid)) {
                    throw new IllegalArgumentException("A " + kind + " message is missing its numeric uid");
                }
                retained.put(uid.longValue(), new JsonObject(message));
                while (retained.size() > maxRetainedMessages) {
                    retained.remove(retained.keySet().iterator().next());
                }
            }
        }

        JsonObject merged = new JsonObject(data);
        merged.put(collectionKey, new JsonArray(new ArrayList<>(retained.values())));
        while (!retained.isEmpty() && merged.toJson().length() > maxSnapshotChars) {
            retained.remove(retained.keySet().iterator().next());
            merged.put(collectionKey, new JsonArray(new ArrayList<>(retained.values())));
        }
        if (merged.toJson().length() > maxSnapshotChars) {
            throw new IllegalArgumentException("Snapshot metadata exceeds the configured size limit");
        }
        snapshots.put(kind, merged);
        touch();
    }

    synchronized JsonObject snapshot(String kind) {
        JsonObject value = snapshots.get(kind);
        return value != null ? new JsonObject(value) : null;
    }

    synchronized Map<String, JsonObject> snapshots() {
        Map<String, JsonObject> copy = new LinkedHashMap<>();
        snapshots.forEach((key, value) -> copy.put(key, new JsonObject(value)));
        return Map.copyOf(copy);
    }

    JsonObject hello() {
        JsonObject value = hello;
        return value != null ? new JsonObject(value) : null;
    }

    synchronized RemoteRuntimeConnection connectionForShutdown() {
        return connection;
    }

    JsonObject executeAction(JsonObject action, long timeoutMillis) throws java.io.IOException {
        RemoteRuntimeConnection current = connection;
        if (current == null || !current.isOpen()) {
            throw new java.io.IOException("Remote Camel integration is disconnected: " + executionId);
        }
        return current.executeAction(action, timeoutMillis);
    }
}

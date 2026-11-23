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

import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteRuntimeEntryTest {

    @Test
    void retainsHelloAndLatestStatus() {
        RemoteRuntimeEntry entry = new RemoteRuntimeEntry("owner", "orders-1", 4096, 10);
        entry.updateHello(hello(1234));

        JsonObject status = new JsonObject();
        status.put("state", "Started");
        entry.updateSnapshot("status", status);

        assertThat(entry.hello().getString("camelVersion")).isEqualTo("4.24.0-SNAPSHOT");
        assertThat(entry.snapshot("status").getString("state")).isEqualTo("Started");
    }

    @Test
    void preservesSnapshotsAcrossReconnectButClearsThemAfterProcessRestart() {
        RemoteRuntimeEntry entry = new RemoteRuntimeEntry("owner", "orders-1", 4096, 10);
        entry.updateHello(hello(1234));
        entry.updateSnapshot("receive", receive(1));

        // A network reconnect in the same process keeps the last-seen UID and deduplicates the replay.
        entry.updateHello(hello(1234));
        entry.updateSnapshot("receive", receive(1, 2));
        List<JsonObject> retainedAfterReconnect = entry.snapshot("receive").getCollection("messages");
        assertThat(retainedAfterReconnect)
                .extracting(message -> message.getLong("uid")).containsExactly(1L, 2L);

        // A process restart reuses executionId but restarts the connector's per-process UID sequence.
        entry.updateHello(hello(5678));
        assertThat(entry.snapshot("receive")).isNull();
        entry.updateSnapshot("receive", receive(1));
        List<JsonObject> retainedAfterRestart = entry.snapshot("receive").getCollection("messages");
        assertThat(retainedAfterRestart)
                .extracting(message -> message.getLong("uid")).containsExactly(1L);
    }

    @Test
    void deduplicatesReplayedReceiveMessagesAndBoundsTheBuffer() {
        RemoteRuntimeEntry entry = new RemoteRuntimeEntry("owner", "orders-1", 4096, 2);
        entry.updateSnapshot("receive", receive(1, 2));
        // Reconnects replay previously retained UIDs; the server must not duplicate them.
        entry.updateSnapshot("receive", receive(2, 3));

        List<JsonObject> messages = entry.snapshot("receive").getCollection("messages");
        assertThat(messages).extracting(m -> m.getLong("uid")).containsExactly(2L, 3L);
    }

    @Test
    void boundsTraceBufferByCount() {
        RemoteRuntimeEntry entry = new RemoteRuntimeEntry("owner", "orders-1", 4096, 2);
        entry.updateSnapshot("trace", traces(1, 2));
        entry.updateSnapshot("trace", traces(2, 3));

        List<JsonObject> retained = entry.snapshot("trace").getCollection("traces");
        assertThat(retained).extracting(message -> message.getLong("uid")).containsExactly(2L, 3L);
    }

    @Test
    void rejectsOversizedSnapshots() {
        RemoteRuntimeEntry entry = new RemoteRuntimeEntry("owner", "orders-1", 32, 10);
        JsonObject data = new JsonObject();
        data.put("payload", "x".repeat(100));
        assertThatThrownBy(() -> entry.updateSnapshot("status", data))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size limit");
    }

    private static JsonObject hello(long pid) {
        JsonObject hello = new JsonObject();
        hello.put("v", 1);
        hello.put("type", "hello");
        hello.put("camelVersion", "4.24.0-SNAPSHOT");
        hello.put("name", "orders");
        hello.put("transport", "jdk");
        hello.put("runtime", new JsonObject(java.util.Map.of("pid", pid)));
        return hello;
    }

    private static JsonObject traces(long... uids) {
        JsonObject data = new JsonObject();
        JsonArray traces = new JsonArray();
        for (long uid : uids) {
            JsonObject trace = new JsonObject();
            trace.put("uid", uid);
            trace.put("body", "trace-" + uid);
            traces.add(trace);
        }
        data.put("traces", traces);
        return data;
    }

    private static JsonObject receive(long... uids) {
        JsonObject data = new JsonObject();
        JsonArray messages = new JsonArray();
        for (long uid : uids) {
            JsonObject message = new JsonObject();
            message.put("uid", uid);
            message.put("body", "message-" + uid);
            messages.add(message);
        }
        data.put("messages", messages);
        return data;
    }
}

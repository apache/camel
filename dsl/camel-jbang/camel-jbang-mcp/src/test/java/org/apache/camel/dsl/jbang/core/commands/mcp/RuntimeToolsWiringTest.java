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

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The requests the MCP runtime tools send to a process, against a recorded runtime: what the tools ask the CLI
 * connector, and how they read its answers.
 */
class RuntimeToolsWiringTest {

    private static final long PID = 4711;

    /** Records the actions and answers them from {@link #answers}; the received messages come from {@link #inbox}. */
    static class RecordingRuntime extends RuntimeService {
        final List<JsonObject> actions = new ArrayList<>();
        final Map<String, JsonObject> answers = new HashMap<>();
        JsonObject receiveState = new JsonObject();
        final List<JsonObject> inbox = new ArrayList<>();
        RuntimeException stopFailure;

        @Override
        public ProcessInfo findSingleProcess(String nameOrPid) {
            return new ProcessInfo(PID, "shop", "shop");
        }

        @Override
        public JsonObject readStatusSection(long pid, String section) {
            return receiveState;
        }

        @Override
        public long receiveFileSize(long pid) {
            return 0;
        }

        @Override
        public List<JsonObject> readReceivedMessages(long pid, long offset) {
            return inbox;
        }

        @Override
        public JsonObject executeAction(long pid, String action, Consumer<JsonObject> configure) {
            JsonObject request = new JsonObject();
            request.put("action", action);
            if (configure != null) {
                configure.accept(request);
            }
            actions.add(request);
            if (configure == null && stopFailure != null) {
                throw stopFailure;
            }
            JsonObject answer = request.containsKey("endpoint") ? answers.get("receive-start") : null;
            return answer != null ? answer : new JsonObject();
        }
    }

    private final RecordingRuntime runtime = new RecordingRuntime();
    private final RuntimeTools tools = new RuntimeTools();

    RuntimeToolsWiringTest() {
        tools.runtimeService = runtime;
    }

    private void endpointMatches(String uri) {
        JsonObject started = new JsonObject();
        started.put("enabled", true);
        started.put("url", uri);
        runtime.answers.put("receive-start", started);
    }

    @Test
    void receiveReturnsTheMessageAndStopsTheConsumer() {
        endpointMatches("seda://orders");
        JsonObject message = new JsonObject();
        message.put("uid", 1L);
        message.put("endpointUri", "seda://orders");
        runtime.inbox.add(message);

        JsonObject answer = tools.receive(PID, "seda:orders", 5000, 1);

        assertThat(answer.getString("endpoint")).isEqualTo("seda://orders");
        assertThat(answer.getInteger("received")).isEqualTo(1);
        assertThat(answer.<List<Object>> getCollection("messages")).containsExactly(message);
        assertThat(runtime.actions).hasSize(2);
        assertThat(runtime.actions.get(0)).containsEntry("enabled", "true").containsEntry("endpoint", "seda:orders");
        assertThat(runtime.actions.get(1)).containsOnlyKeys("action").containsEntry("action", "receive");
    }

    @Test
    void receiveReturnsTheFirstMessagesUpToTheLimitAndCountsTheOthers() {
        endpointMatches("seda://orders");
        for (long uid = 1; uid <= 4; uid++) {
            runtime.inbox.add(message(uid));
        }

        JsonObject one = tools.receive(PID, "seda:orders", 5000, 1);
        assertThat(one.getInteger("received")).isEqualTo(4);
        assertThat(one.<List<JsonObject>> getCollection("messages")).extracting(m -> m.getLong("uid")).containsExactly(1L);
        assertThat(one.getInteger("notReturned")).isEqualTo(3);
        assertThat(one.getString("note")).contains("3 more", "taken from the endpoint");

        JsonObject all = tools.receive(PID, "seda:orders", 5000, 10);
        assertThat(all.<List<JsonObject>> getCollection("messages")).hasSize(4);
        assertThat(all).doesNotContainKeys("notReturned", "note");
    }

    @Test
    void receiveRefusesALimitBelowOne() {
        assertThatThrownBy(() -> tools.camel_runtime_receive(null, "seda:orders", null, 0))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("limit must be at least 1");
        assertThat(runtime.actions).isEmpty();
    }

    @Test
    void receiveReturnsTheMessageWithAWarningWhenTheConsumerCannotBeStopped() {
        endpointMatches("seda://orders");
        runtime.inbox.add(message(1));
        runtime.stopFailure = new ToolCallException("Timeout waiting for response", null);

        JsonObject answer = tools.receive(PID, "seda:orders", 5000, 1);

        assertThat(answer.<List<Object>> getCollection("messages")).hasSize(1);
        assertThat(answer.getString("warning")).contains("Timeout waiting", "camel cmd receive --action=stop");
    }

    @Test
    void receiveNamesTheStopCommandWhenItFailsAndCannotStopTheConsumer() {
        runtime.answers.put("receive-start", new JsonObject());
        runtime.stopFailure = new ToolCallException("Timeout waiting for response", null);

        assertThatThrownBy(() -> tools.receive(PID, "kafka*", 300, 1))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("No endpoint matches kafka*")
                .hasMessageContaining("camel cmd receive --action=stop");
    }

    private static JsonObject message(long uid) {
        JsonObject message = new JsonObject();
        message.put("uid", uid);
        return message;
    }

    @Test
    void receiveStopsTheConsumerWhenNoMessageArrives() {
        endpointMatches("seda://orders");

        JsonObject answer = tools.receive(PID, "seda:orders", 300, 1);

        assertThat(answer.getInteger("received")).isZero();
        assertThat(answer.getString("note")).contains("No message arrived");
        assertThat(runtime.actions).last().satisfies(r -> assertThat(r).doesNotContainKey("endpoint"));
    }

    @Test
    void receiveReportsAnEndpointNoneMatchesAndStops() {
        runtime.answers.put("receive-start", new JsonObject());

        assertThatThrownBy(() -> tools.receive(PID, "kafka*", 300, 1))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("No endpoint matches kafka*");
        assertThat(runtime.actions).hasSize(2);
    }

    @Test
    void receiveLeavesAReceiveModeStartedByTheCliAlone() {
        runtime.receiveState.put("enabled", true);
        runtime.receiveState.put("endpoints", new JsonArray(List.of(Map.of("uri", "seda://audit"))));

        assertThatThrownBy(() -> tools.receive(PID, "seda:orders", 300, 1))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("already receiving");
        assertThat(runtime.actions).isEmpty();
    }

    @Test
    void readReceivedMessagesReadsTheLinesAfterTheOffset(@TempDir Path home) throws Exception {
        Path previous = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        try {
            Path camelDir = CommandLineHelper.getCamelDir();
            Files.createDirectories(camelDir);
            Path file = camelDir.resolve(PID + "-receive.json");
            Files.writeString(file, "{\"messages\":[{\"uid\":1}]}\n");
            RuntimeService service = new RuntimeService();
            long offset = service.receiveFileSize(PID);

            Files.writeString(file, "{\"messages\":[{\"uid\":2},{\"uid\":3}]}\n{\"messages\":[{\"uid\":4}",
                    StandardOpenOption.APPEND);

            assertThat(service.readReceivedMessages(PID, offset)).extracting(m -> m.getLong("uid"))
                    .as("the half written last line is read on the next poll")
                    .containsExactly(2L, 3L);

            // camel cmd receive --action=clear rewrites the file shorter than the offset: read it from the start
            Files.writeString(file, "{\"messages\":[{\"uid\":5}]}\n");
            assertThat(service.readReceivedMessages(PID, offset + 100)).extracting(m -> m.getLong("uid"))
                    .containsExactly(5L);
        } finally {
            CommandLineHelper.useHomeDir(previous.toString());
        }
    }

    @Test
    void memoryLeakStartsOneRecordingByDefault() {
        tools.camel_runtime_memory_leak(null, "start", null, null, null, null);
        tools.camel_runtime_memory_leak(null, "compare", null, null, null, null);

        assertThat(runtime.actions).extracting(r -> r.getString("command")).containsExactly("start", "compare");
        assertThat(runtime.actions.get(0).getString("duration")).isEqualTo("60");
        assertThat(runtime.actions.get(1)).doesNotContainKey("duration");
    }

    @Test
    void memoryLeakRefusesABadDurationOrMode() {
        assertThatThrownBy(() -> tools.camel_runtime_memory_leak(null, "start", "1m", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("whole number of seconds");
        assertThatThrownBy(() -> tools.camel_runtime_memory_leak(null, "start", "30", "triple", null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("single or dual");
        assertThat(runtime.actions).isEmpty();
    }

    @Test
    void aiTraceReportsAFailedQueryAsAnError() {
        // no process selected: the registry refuses, which must not read as "no AI components"
        assertThatThrownBy(() -> new AiTraceTools().executeRegistryTool("get_history", new ToolContext(), Map.of()))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("get_history");
    }

    @Test
    void theHintsMatchWhatTheToolsDo() throws Exception {
        assertThat(hints("camel_runtime_receive", String.class, String.class, Integer.class, Integer.class))
                .as("receive takes the message from the routes").containsExactly(false, true);
        assertThat(hints("camel_runtime_send", String.class, String.class, String.class, String.class))
                .containsExactly(false, true);
        assertThat(hints("camel_runtime_trace_dump", String.class)).containsExactly(true, false);

        Method aiTrace = AiTraceTools.class.getDeclaredMethod("camel_runtime_ai_trace", String.class, String.class);
        assertThat(aiTrace.getParameters()[0].getAnnotation(ToolArg.class).required()).isFalse();
    }

    private static List<Boolean> hints(String tool, Class<?>... parameters) throws Exception {
        Tool.Annotations a = RuntimeTools.class.getDeclaredMethod(tool, parameters).getAnnotation(Tool.class).annotations();
        return List.of(a.readOnlyHint(), a.destructiveHint());
    }
}

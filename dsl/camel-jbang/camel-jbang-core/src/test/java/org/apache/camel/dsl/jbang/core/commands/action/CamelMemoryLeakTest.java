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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel cmd memory-leak: recording the old objects of an integration with JFR, and the samples of the recording.
 */
@ExtendWith(MockitoExtension.class)
class CamelMemoryLeakTest extends ActionCommandTestSupport {

    @Test
    void startARecording() throws Exception {
        JsonObject reply = reply("recording");
        reply.put("durationSeconds", 30);

        assertThat(callWithResponse(command("--start", "--mode=single", "--duration=30"), reply)).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("jfr-memory-leak");
        assertThat(action.getString("command")).isEqualTo("start");
        assertThat(action.getString("duration")).isEqualTo("30");
        assertThat(printer.getOutput()).contains("JFR recording started for PID: " + TEST_PID,
                "Duration: 30 seconds (auto-stop)");
    }

    @Test
    void statusOfARecordingInProgress() throws Exception {
        JsonObject reply = reply("recording");
        reply.put("durationSeconds", 60);
        reply.put("elapsedMs", 20000);
        reply.put("remainingMs", 40000);

        assertThat(callWithResponse(command("--status"), reply)).isZero();

        assertThat(readActionFile(TEST_PID).getString("command")).isEqualTo("status");
        assertThat(printer.getOutput()).contains("JFR recording in progress for PID: " + TEST_PID, "Elapsed: 20.0s",
                "Remaining: 40.0s").doesNotContain("started");
    }

    @Test
    void statusAfterTheRecording() throws Exception {
        JsonObject reply = reply("completed");
        reply.put("hasCachedResults", true);
        reply.put("hasComparisonData", false);
        reply.put("sampleCount", 12);

        assertThat(callWithResponse(command("--status"), reply)).isZero();

        assertThat(printer.getOutput()).contains("JFR recording completed. Use --query to view results.",
                "Cached samples: 12").doesNotContain("No old object samples");
    }

    @Test
    void statusWithoutARecording() throws Exception {
        assertThat(callWithResponse(command("--status"), reply("idle"))).isZero();

        assertThat(printer.getOutput()).contains("No active JFR recording for PID: " + TEST_PID);
    }

    @Test
    void stopShowsTheSamples() throws Exception {
        assertThat(callWithResponse(command("--stop"), samples())).isZero();

        assertThat(readActionFile(TEST_PID).getString("command")).isEqualTo("stop");
        assertThat(printer.getOutput()).contains("Samples: 2", "Recording duration: 1m 1s", "HashMap$Node", "2.0 KB",
                "Cache.map -> HashMap.table");
    }

    @Test
    void queryWithTheStackTraces() throws Exception {
        assertThat(callWithResponse(command("--query", "--stacktrace", "--min-size=1MB"), samples())).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("command")).isEqualTo("query");
        assertThat(action.getString("stacktrace")).isEqualTo("true");
        assertThat(action.getString("minSize")).isEqualTo(Long.toString(1024 * 1024));
        assertThat(printer.getOutput()).contains("1) java.util.HashMap$Node  count:3  sampled:2.0 KB  age:1.5s",
                "chain: Cache.map -> HashMap.table", "at org.example.Cache.put:42");
    }

    @Test
    void theTopSamplesOnly() throws Exception {
        assertThat(callWithResponse(command("--query", "--top=1"), samples())).isZero();

        assertThat(printer.getOutput()).contains("HashMap$Node").doesNotContain("byte[]");
    }

    @Test
    void anErrorOfTheRecordingIsAFailure() throws Exception {
        JsonObject reply = reply("error");
        reply.put("error", "A JFR recording is already active. Stop it first.");

        assertThat(callWithResponse(command("--start", "--mode=single"), reply)).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).contains("A JFR recording is already active. Stop it first.");
    }

    @Test
    void anActionTheIntegrationFailsIsAFailure() throws Exception {
        // the camel-cli-connector answers so when the action throws an exception
        JsonObject exception = new JsonObject(Map.of("type", "java.lang.IllegalStateException", "message", "JFR is off"));
        JsonObject reply = reply("failed");
        reply.put("exception", exception);

        assertThat(callWithResponse(command("--query"), reply)).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).contains("JFR is off");
    }

    @Test
    void anIntegrationWithoutTheConsoleIsAFailure() throws Exception {
        // the answer of the camel-cli-connector when the integration has no jfr-memory-leak dev console
        assertThat(callWithResponse(command("--query"), new JsonObject())).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).contains("cannot record memory leaks");
    }

    @Test
    void comparisonOfTwoRecordings() throws Exception {
        JsonObject comparison = new JsonObject();
        comparison.put("allocationClass", "java.util.HashMap$Node");
        comparison.put("baselineSampledSize", 1024);
        comparison.put("currentSampledSize", 4096);
        comparison.put("growthRatio", 4.0);
        comparison.put("trend", "growing");
        JsonObject reply = reply("compared");
        reply.put("durationRatio", 2.0);
        reply.put("baseline", new JsonObject(Map.of("recordingDurationMs", 60000)));
        reply.put("current", new JsonObject(Map.of("recordingDurationMs", 120000)));
        reply.put("comparisons", new JsonArray(List.of(comparison)));

        // the answer to the compare command, which ends a dual recording
        assertThat(callWithResponse(command("--query"), reply)).isZero();

        assertThat(printer.getOutput()).contains("Run 1: 1m 0s", "Run 2: 2m 0s", "Duration ratio: 2.0x",
                "HashMap$Node");
    }

    @Test
    void sizes() {
        assertThat(CamelMemoryLeak.parseSize("0")).isZero();
        assertThat(CamelMemoryLeak.parseSize("1024")).isEqualTo(1024);
        assertThat(CamelMemoryLeak.parseSize("10B")).isEqualTo(10);
        assertThat(CamelMemoryLeak.parseSize("10KB")).isEqualTo(10 * 1024);
        assertThat(CamelMemoryLeak.parseSize("10k")).isEqualTo(10 * 1024);
        assertThat(CamelMemoryLeak.parseSize("1.5MB")).isEqualTo(1536 * 1024);
        assertThat(CamelMemoryLeak.parseSize("2G")).isEqualTo(2L * 1024 * 1024 * 1024);
    }

    @Test
    void notASizeIsAUsageError() {
        assertThatThrownBy(() -> CamelMemoryLeak.parseSize("lots"))
                .isInstanceOf(CliException.class)
                .hasMessage("--min-size must be a size such as 1024, 10KB or 1MB, was: lots")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    private CamelMemoryLeak command(String... args) throws Exception {
        writeStatusFile(TEST_PID, "orders");
        CamelMemoryLeak command = new CamelMemoryLeak(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, Stream.concat(Stream.of("orders"), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    private static JsonObject reply(String status) {
        JsonObject reply = new JsonObject();
        reply.put("status", status);
        return reply;
    }

    private static JsonObject samples() {
        JsonObject node = new JsonObject();
        node.put("allocationClass", "java.util.HashMap$Node");
        node.put("count", 3);
        node.put("sampledSize", 2048);
        node.put("objectAge", 1500);
        node.put("referenceChain", new JsonArray(
                List.of(
                        new JsonObject(Map.of("type", "org.example.Cache", "field", "map")),
                        new JsonObject(Map.of("type", "java.util.HashMap", "field", "table")))));
        node.put("stackTrace", new JsonArray(List.of(new JsonObject(Map.of("method", "org.example.Cache.put", "line", 42)))));
        JsonObject bytes = new JsonObject();
        bytes.put("allocationClass", "byte[]");
        bytes.put("count", 1);
        bytes.put("sampledSize", 512);
        JsonObject reply = reply("completed");
        reply.put("sampleCount", 2);
        reply.put("recordingDurationMs", 61000);
        reply.put("samples", new JsonArray(List.of(node, bytes)));
        return reply;
    }
}

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
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel top source: the slowest processors of an integration with their source code, as the top dev console has them.
 */
@ExtendWith(MockitoExtension.class)
class CamelSourceTopTest extends ActionCommandTestSupport {

    @Test
    void testRequestsTopProcessorsAndRendersRows() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), processors(log(), to()))).isZero();

        assertThat(readActionFile(TEST_PID).getString("action")).isEqualTo("top-processors");
        assertThat(printer.getOutput())
                .contains("Route: orders\tSource: file:orders.yaml:12 Total: 5 Mean: 2 Max: 3 Min: 1 Last: 2");
    }

    @Test
    void theSlowestFirstWithTheirCode() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), processors(log(), to()))).isZero();

        String out = printer.getOutput();
        assertThat(out.indexOf("Mean: 30")).isLessThan(out.indexOf("Mean: 2 "));
        // the line of the processor is marked, and the code is unescaped
        assertThat(out).contains("  13:" + " ".repeat(9) + "- to:", "  14: -->" + " ".repeat(9) + "uri: \"kafka:out\"",
                "  15:" + " ".repeat(13) + "id: to1");
    }

    @Test
    void filterMeanShowsTheSlowerProcessors() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--filter-mean=10"), processors(log(), to()))).isZero();

        assertThat(printer.getOutput()).contains("Mean: 30").doesNotContain("Mean: 2 ");
    }

    @Test
    void limitShowsTheSlowest() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        // the integration lists the slowest first
        assertThat(callWithResponse(command("--limit=1"), processors(to(), log()))).isZero();

        assertThat(printer.getOutput()).contains("Mean: 30").doesNotContain("Mean: 2 ");
    }

    @Test
    void aProcessorWithoutExchangesYet() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        JsonObject idle = processor("idle1", "file:orders.yaml:20", -1, -1, List.of());

        assertThat(callWithResponse(command(), processors(idle))).isZero();

        assertThat(printer.getOutput()).contains("Total: 0 Mean:  Max: 0 Min: 0 Last:");
    }

    @Test
    void jsonHasTheNumbersAndTheCode() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--json"), processors(log(), to()))).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(rows).hasSize(2);
        JsonObject to = rows.getMap(0);
        assertThat(to.getString("id")).isEqualTo("to1");
        assertThat(to.getString("routeId")).isEqualTo("orders");
        assertThat(to.getString("location")).isEqualTo("file:orders.yaml:14");
        // numbers, not text
        assertThat(to.get("total")).isInstanceOf(Number.class);
        assertThat(to.get("mean")).isInstanceOf(Number.class);
        assertThat(to.getLong("total")).isEqualTo(5L);
        assertThat(to.getLong("mean")).isEqualTo(30L);
        assertThat(to.getLong("max")).isEqualTo(59L);
        List<JsonObject> code = to.getCollection("code");
        assertThat(code).hasSize(3);
        assertThat(code.get(1).getBoolean("match")).isTrue();
        assertThat(code.get(0).containsKey("match")).isFalse();
    }

    @Test
    void jsonOfAProcessorWithoutExchangesYet() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        JsonObject idle = processor("idle1", "file:orders.yaml:20", -1, -1, List.of());

        assertThat(callWithResponse(command("--json"), processors(idle))).isZero();

        JsonObject row = Jsoner.deserialize(printer.getOutput(), (JsonArray) null).getMap(0);
        assertThat(row.get("mean")).isNull();
        assertThat(row.get("last")).isNull();
        assertThat(row.getLong("total")).isZero();
    }

    @Test
    void anAnswerWithoutProcessorsIsAFailure() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelSourceTop command = command();

        assertThatThrownBy(() -> callWithResponse(command, new JsonObject()))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessage("Source top data not available from the running integration");
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelSourceTop command = new CamelSourceTop(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "doesNotExist");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No running Camel integration matches: doesNotExist");
        assertThat(printer.getOutput()).isEmpty();
    }

    private CamelSourceTop command(String... args) {
        CamelSourceTop command = new CamelSourceTop(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, Stream.concat(Stream.of("myApp"), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    // as the top dev console answers
    private static JsonObject processors(JsonObject... processors) {
        JsonObject answer = new JsonObject();
        answer.put("processors", new JsonArray(Arrays.asList(processors)));
        return answer;
    }

    private static JsonObject log() {
        return processor("log1", "file:orders.yaml:12", 2, 2, List.of());
    }

    private static JsonObject to() {
        return processor("to1", "file:orders.yaml:14", 30, 25, List.of(
                line(13, "    - to:", false),
                line(14, "        uri: \\\"kafka:out\\\"", true),
                line(15, "        id: to1", false)));
    }

    private static JsonObject processor(String id, String location, long mean, long last, List<JsonObject> code) {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", mean < 0 ? 0 : 5);
        stats.put("meanProcessingTime", mean);
        stats.put("maxProcessingTime", Math.max(0, mean * 2 - 1));
        stats.put("minProcessingTime", mean < 0 ? 0 : 1);
        stats.put("lastProcessingTime", last);
        JsonObject processor = new JsonObject();
        processor.put("routeId", "orders");
        processor.put("processorId", id);
        processor.put("location", location);
        processor.put("statistics", stats);
        if (!code.isEmpty()) {
            processor.put("code", new JsonArray(code));
        }
        return processor;
    }

    // the code is escaped as JSON text
    private static JsonObject line(int line, String code, boolean match) {
        JsonObject jo = new JsonObject(Map.of("line", line, "code", code));
        if (match) {
            jo.put("match", true);
        }
        return jo;
    }
}

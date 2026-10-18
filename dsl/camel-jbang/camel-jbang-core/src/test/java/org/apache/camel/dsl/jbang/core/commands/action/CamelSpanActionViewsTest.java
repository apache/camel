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
 * camel cmd span: the traces of the spans an integration captured, a flat list of the spans, and the waterfall of a
 * trace.
 */
@ExtendWith(MockitoExtension.class)
class CamelSpanActionViewsTest extends ActionCommandTestSupport {

    @Test
    void tracesWithTheirSpans() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), spans())).isZero();

        String out = printer.getOutput();
        assertThat(out).contains("TRACE-ID", "orders", "payments", "ERROR", "OK", "kafka");
    }

    @Test
    void filterTheTraces() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--filter=payments"), spans())).isZero();

        assertThat(printer.getOutput()).contains("payments").doesNotContain("orders");
    }

    @Test
    void sortTheTracesBySpans() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--sort=spans"), spans())).isZero();

        // the trace with the most spans first
        String out = printer.getOutput();
        assertThat(out.indexOf("orders")).isLessThan(out.indexOf("payments"));
    }

    @Test
    void unknownSortOfTheTracesIsAUsageError() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelSpanAction command = command("--sort=size");

        assertThatThrownBy(() -> callWithResponse(command, spans()))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessageStartingWith("--sort must be one of trace, route, from, spans, routes, status, duration");
    }

    @Test
    void flatListOfTheSpansByDuration() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--flat", "--sort=-duration"), spans())).isZero();

        String out = printer.getOutput();
        // the slowest span (the kafka call of 40ms) first
        assertThat(out).contains("kafka:billing", "timer://orders");
        assertThat(out.indexOf("kafka:billing")).isLessThan(out.indexOf("log:done"));
    }

    @Test
    void waterfallOfATrace() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--trace=aaaa"), spans())).isZero();

        assertThat(printer.getOutput()).contains("timer://orders", "kafka:billing", "log:done")
                .doesNotContain("timer://payments");
    }

    @Test
    void waterfallOfATraceThatDoesNotExistIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelSpanAction command = command("--trace=ffff");

        assertThatThrownBy(() -> callWithResponse(command, spans()))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No trace found matching: ffff");
    }

    @Test
    void jsonOfTheSpansOfATrace() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--json", "--trace=bbbb"), spans())).isZero();

        JsonArray spans = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(spans).hasSize(1);
        JsonObject span = spans.getMap(0);
        assertThat(span.getString("routeId")).isEqualTo("payments");
    }

    @Test
    void noSpansYet() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        JsonObject answer = new JsonObject(Map.of("enabled", true, "spans", new JsonArray()));

        assertThat(callWithResponse(command(), answer)).isZero();

        assertThat(printer.getStderr()).contains("No spans captured yet.");
    }

    @Test
    void anIntegrationWithoutTheExporterIsAFailure() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelSpanAction command = command();

        assertThatThrownBy(() -> callWithResponse(command, new JsonObject(Map.of("enabled", false))))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessageContaining("--observe");
    }

    private CamelSpanAction command(String... args) {
        CamelSpanAction command = new CamelSpanAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Stream.of("myApp", "--logging-color=false"), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    // two traces: orders (three spans, a kafka call that failed) and payments (one span)
    private static JsonObject spans() {
        JsonArray spans = new JsonArray(
                List.of(
                        span("aaaa1111", "s1", "", "timer://orders", "orders", "OK", 0, 50, null),
                        span("aaaa1111", "s2", "s1", "kafka:billing", "orders", "ERROR", 5, 45, "kafka"),
                        span("aaaa1111", "s3", "s1", "log:done", "orders", "OK", 46, 48, null),
                        span("bbbb2222", "s4", "", "timer://payments", "payments", "OK", 100, 101, null)));
        JsonObject answer = new JsonObject();
        answer.put("enabled", true);
        answer.put("spans", spans);
        return answer;
    }

    private static JsonObject span(
            String traceId, String spanId, String parentSpanId, String name, String routeId, String status, long startMs,
            long endMs, String scheme) {
        JsonObject attributes = new JsonObject();
        attributes.put("exchangeId", "E-" + traceId);
        if (scheme != null) {
            attributes.put("url.scheme", scheme);
        }
        JsonObject span = new JsonObject();
        span.put("traceId", traceId);
        span.put("spanId", spanId);
        span.put("parentSpanId", parentSpanId);
        span.put("name", name);
        span.put("kind", "INTERNAL");
        span.put("status", status);
        span.put("durationMs", endMs - startMs);
        span.put("routeId", routeId);
        span.put("startEpochNanos", startMs * 1_000_000L);
        span.put("endEpochNanos", endMs * 1_000_000L);
        span.put("attributes", attributes);
        return span;
    }
}

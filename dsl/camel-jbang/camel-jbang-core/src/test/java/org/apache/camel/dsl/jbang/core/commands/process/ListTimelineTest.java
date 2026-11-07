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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListTimelineTest extends ProcessCommandTestSupport {

    @Test
    void nothingWhenTheIntegrationHasNoTimeline() throws Exception {
        assertThat(run(command(null))).isEmpty();
    }

    @Test
    void aLinePerRouteWithTheTotalsOfTheSeconds() throws Exception {
        String out = run(command(timeline()));

        assertThat(out).contains("ROUTE", "RECOVERED", "EXCHANGES/S");
        String orders = out.lines().filter(l -> l.contains("orders")).findFirst().orElseThrow();
        // 1+2+3 completed, 1 recovered, 1 handled, 2 failed
        assertThat(orders).contains(" 10 ").contains("47ms").contains("900ms");
        assertThat(out.lines().filter(l -> l.contains("dlq")).findFirst().orElseThrow()).contains(" 1 ");
    }

    @Test
    void theRoutesAsJson() throws Exception {
        ListTimeline command = command(timeline());
        command.jsonOutput = true;

        JsonObject orders = Jsoner.deserialize(run(command), (JsonArray) null).getMap(0);
        assertThat(orders.getString("routeId")).isEqualTo("orders");
        assertThat(orders.getLong("total")).isEqualTo(10);
        assertThat(orders.getLong("recovered")).isEqualTo(1);
        assertThat(orders.getLong("handled")).isEqualTo(1);
        assertThat(orders.getLong("failed")).isEqualTo(2);
        assertThat(orders.getLong("redeliveryAttempts")).isEqualTo(4);
        assertThat(orders.getLong("maxMillis")).isEqualTo(900);
    }

    @Test
    void theExchangesKeptWithTheirOutcome() throws Exception {
        ListTimeline command = command(timeline());
        command.exchanges = true;

        String out = run(command);

        assertThat(out).contains("EX-1", "EX-2", "FAILED", "RECOVERED", "r1", "platform-http:/");
        assertThat(out).doesNotContain("EXCHANGES/S");
    }

    @Test
    void theExchangesSortedByElapsedAndLimited() throws Exception {
        ListTimeline command = command(timeline());
        command.exchanges = true;
        command.sort = "elapsed";
        command.limit = 1;

        String out = run(command);

        assertThat(out).contains("EX-2").doesNotContain("EX-1");
    }

    @Test
    void theErrorsByRouteAndException() throws Exception {
        ListTimeline command = command(timeline());
        command.errors = true;

        String out = run(command);

        String line = out.lines().filter(l -> l.contains("HttpOperationFailedException")).findFirst().orElseThrow();
        assertThat(line).contains("orders", "503", "r1", " 3 ");
    }

    @Test
    void aSparklineTakesTheMaxOfItsSeconds() {
        assertThat(ListTimeline.spark(new long[] { 0, 0, 4, 8 }, 4)).isEqualTo("  ▄█");
        assertThat(ListTimeline.spark(new long[] { 0, 8, 0, 0 }, 2)).isEqualTo("█ ");
        assertThat(ListTimeline.spark(new long[] { 0, 0 }, 10)).isEqualTo("  ");
    }

    private String run(ListTimeline command) throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            assertThat(command.doCall()).isZero();
            return printer.getOutput().trim();
        }
    }

    private ListTimeline command(JsonObject answer) {
        ListTimeline cmd = new ListTimeline(new CamelJBangMain().withPrinter(printer)) {
            @Override
            JsonObject loadTimeline(long pid, String routeId) {
                return answer;
            }
        };
        cmd.sort = "since";
        return cmd;
    }

    private static JsonObject timeline() {
        long now = System.currentTimeMillis();
        JsonObject orders = route("orders", List.of(1L, 2L, 3L), List.of(0L, 1L, 0L), List.of(0L, 0L, 1L),
                List.of(0L, 2L, 0L));
        orders.put("meanMillis", List.of(10L, 70L, 40L));
        orders.put("maxMillis", List.of(20L, 900L, 50L));
        orders.put("redeliveryAttempts", List.of(0L, 4L, 0L));
        orders.put("from", "platform-http:/orders");
        JsonObject dlq = route("dlq", List.of(0L, 0L, 1L), List.of(0L, 0L, 0L), List.of(0L, 0L, 0L),
                List.of(0L, 0L, 0L));

        JsonObject error = new JsonObject();
        error.put("routeId", "orders");
        error.put("exception", "org.apache.camel.http.base.HttpOperationFailedException");
        error.put("statusCode", 503);
        error.put("chaosRule", "r1");
        error.put("counts", List.of(0L, 3L, 0L));

        JsonObject ex1 = exchange("EX-1", now - 2000, 70, true, false);
        ex1.put("chaosRule", "r1");
        JsonObject ex2 = exchange("EX-2", now - 1000, 900, false, true);

        JsonObject root = new JsonObject();
        root.put("end", now / 1000 - 1);
        root.put("seconds", 3);
        root.put("routes", List.of(orders, dlq));
        root.put("errors", List.of(error));
        root.put("exchanges", List.of(ex1, ex2));
        // as the integration answers it: parsed JSON
        try {
            return (JsonObject) Jsoner.deserialize(root.toJson());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonObject route(
            String id, List<Long> completed, List<Long> recovered, List<Long> handled, List<Long> failed) {
        JsonObject r = new JsonObject();
        r.put("routeId", id);
        r.put("completed", completed);
        r.put("recovered", recovered);
        r.put("handled", handled);
        r.put("failed", failed);
        return r;
    }

    private static JsonObject exchange(String id, long timestamp, long elapsed, boolean failed, boolean redelivered) {
        JsonObject e = new JsonObject();
        e.put("exchangeId", id);
        e.put("routeId", "orders");
        e.put("fromEndpointUri", "platform-http:/orders");
        e.put("timestamp", timestamp);
        e.put("elapsed", elapsed);
        e.put("failed", failed);
        e.put("redelivered", redelivered);
        return e;
    }
}

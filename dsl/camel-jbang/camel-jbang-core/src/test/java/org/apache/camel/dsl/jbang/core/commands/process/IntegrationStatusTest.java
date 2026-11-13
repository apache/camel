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

import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * camel status: the state of one integration in one call (CAMEL-26941).
 */
@ExtendWith(MockitoExtension.class)
class IntegrationStatusTest extends ProcessCommandTestSupport {

    @Test
    void theStateOfTheIntegrationInOneDocument() throws Exception {
        writeIntegration();
        IntegrationStatus command = command();
        command.jsonOutput = true;

        assertThat(call(command)).isEqualTo(CliExitCode.OK);

        JsonObject status = json(printer.getStdout());
        assertThat(status.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(status.getString("name")).isEqualTo("orders");
        assertThat(status.getString("camelVersion")).isEqualTo("4.24.0");
        assertThat(status.getString("platform")).isEqualTo("JBang");
        assertThat(status.getString("state")).isEqualTo("Started");
        assertThat(status.getInteger("phase")).isEqualTo(5);
        assertThat(status.getLong("uptime")).isEqualTo(65000L);
        assertThat(status.getBoolean("devMode")).isTrue();
        JsonObject exchanges = status.getMap("exchanges");
        assertThat(exchanges.getLong("total")).isEqualTo(12L);
        assertThat(exchanges.getLong("failed")).isEqualTo(2L);

        JsonArray routes = status.getCollection("routes");
        assertThat(routes).hasSize(1);
        JsonObject route = (JsonObject) routes.get(0);
        assertThat(route.getString("routeId")).isEqualTo("orders");
        // as camel get route has it: a route with a last error is in error
        assertThat(route.getString("status")).isEqualTo("Error");
        assertThat(route.getLong("uptime")).isEqualTo(60000L);
        assertThat(route.getLong("failed")).isEqualTo(2L);
        JsonObject lastError = route.getMap("lastError");
        assertThat(lastError.getString("message")).isEqualTo("Boom");

        JsonObject health = status.getMap("health");
        assertThat(health.getBoolean("up")).isFalse();
        JsonArray down = health.getCollection("down");
        assertThat(down).hasSize(1);
        JsonObject kafka = (JsonObject) down.get(0);
        assertThat(kafka.getString("id")).isEqualTo("kafka");

        JsonArray http = status.getCollection("http");
        JsonObject endpoint = (JsonObject) http.get(0);
        assertThat(endpoint.getString("url")).isEqualTo("http://0.0.0.0:8080/orders");

        // the newest error first, without its message and history
        JsonArray errors = status.getCollection("errors");
        assertThat(errors).hasSize(2);
        JsonObject newest = (JsonObject) errors.get(0);
        assertThat(newest.getString("exchangeId")).isEqualTo("ex-2");
        JsonObject exception = newest.getMap("exception");
        assertThat(exception.getString("message")).isEqualTo("Boom");
        assertThat(newest.containsKey("messageHistory")).isFalse();

        JsonObject reload = status.getMap("reload");
        assertThat(reload.getLong("reloaded")).isEqualTo(3L);
        JsonObject last = reload.getMap("last");
        assertThat(last.getString("status")).isEqualTo("reloaded");

        // newest first, as camel_get_log has them
        JsonArray log = status.getCollection("log");
        assertThat(log).hasSize(4);
        JsonObject newestRecord = (JsonObject) log.get(0);
        assertThat(newestRecord.getString("message")).isEqualTo("Processing order 2");
    }

    @Test
    void theLimitsOfTheLogAndTheErrors() throws Exception {
        writeIntegration();
        IntegrationStatus command = command();
        command.jsonOutput = true;
        command.logLines = 1;
        command.errors = 1;

        assertThat(call(command)).isEqualTo(CliExitCode.OK);

        JsonObject status = json(printer.getStdout());
        JsonArray log = status.getCollection("log");
        assertThat(log).hasSize(1);
        JsonArray errors = status.getCollection("errors");
        assertThat(errors).hasSize(1);
        // the last reload is found in the log also when the record is not shown
        JsonObject reload = status.getMap("reload");
        JsonObject last = reload.getMap("last");
        assertThat(last.getString("status")).isEqualTo("reloaded");
    }

    @Test
    void textForAPerson() throws Exception {
        writeIntegration();

        assertThat(call(command())).isEqualTo(CliExitCode.OK);

        assertThat(printer.getStdout()).contains(
                "orders (pid 12345), Camel 4.24.0 (JBang): Started, up 1m5s, dev mode",
                "Exchanges: 12 total, 2 failed, 0 inflight",
                "  orders  Error  timer://tick  (12 total, 2 failed, 0 inflight)",
                "    last error (processing): Boom",
                "Health: DOWN, ready, live",
                "  DOWN kafka: Connection refused",
                "HTTP: GET http://0.0.0.0:8080/orders",
                "Reload: 3 reloaded, 0 failed; last: reloaded: Routes reloaded summary (total:1 started:1)",
                "Log (last 4 records):");
    }

    @Test
    void nothingRunning() {
        IntegrationStatus command = command();

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            assertThatThrownBy(command::doCall).isInstanceOf(CliException.class)
                    .hasMessage("No running Camel integrations")
                    .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        }
    }

    private IntegrationStatus command() {
        return new IntegrationStatus(new CamelJBangMain().withPrinter(printer));
    }

    private static int call(IntegrationStatus command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }

    private static void writeIntegration() throws Exception {
        writeStatusFile(TEST_PID, json("""
                {"runtime":{"pid":12345,"platform":"JBang"},
                 "context":{"name":"orders","version":"4.24.0","state":"Started","phase":5,"uptime":65000,"devMode":true,
                   "statistics":{"exchangesTotal":12,"exchangesFailed":2,"exchangesInflight":0,
                     "reload":{"reloaded":3,"failed":0}}},
                 "routes":[{"routeId":"orders","from":"timer://tick","state":"Started","uptime":"1m0s",
                   "statistics":{"exchangesTotal":12,"exchangesFailed":2,"exchangesInflight":0},
                   "lastError":{"phase":"processing","timestamp":1794600000000,"message":"Boom"}}],
                 "healthChecks":{"up":false,"ready":true,"live":true,"checks":[
                   {"id":"routes","state":"UP"},
                   {"id":"kafka","state":"DOWN","message":"Connection refused"}]},
                 "platform-http":{"server":"http://0.0.0.0:8080",
                   "endpoints":[{"url":"http://0.0.0.0:8080/orders","path":"/orders","verbs":"GET"}]}}
                """));
        Files.writeString(CommandLineHelper.getCamelDir().resolve(TEST_PID + "-error.json"), json("""
                {"errors":[
                  {"timestamp":1794600000000,"routeId":"orders","nodeId":"to1","exchangeId":"ex-1","handled":false,
                   "exception":{"type":"java.lang.IllegalStateException","message":"Old"},"messageHistory":["a"]},
                  {"timestamp":1794600005000,"routeId":"orders","nodeId":"to1","exchangeId":"ex-2","handled":false,
                   "exception":{"type":"java.lang.IllegalStateException","message":"Boom"},"messageHistory":["b"]}]}
                """).toJson());
        Files.write(CommandLineHelper.getCamelDir().resolve(TEST_PID + ".log"), List.of(
                "2026-11-13 20:48:00.001  INFO 12345 --- [main] org.apache.camel.main.MainSupport : Started orders",
                "2026-11-13 20:48:05.069  INFO 12345 --- [rReloadStrategy] .support.RouteWatcherReloadStrategy"
                                                                                                                     + " : Routes reloaded summary (total:1 started:1)",
                "2026-11-13 20:48:06.075  INFO 12345 --- [ - timer://tick] orders.camel.yaml:8 : Processing order 1",
                "2026-11-13 20:48:07.079  INFO 12345 --- [ - timer://tick] orders.camel.yaml:8 : Processing order 2"));
    }

    private static JsonObject json(String text) {
        return Jsoner.deserialize(text.trim(), (JsonObject) null);
    }
}

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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * The runtime tools of camel ask and the MCP servers against an integration that answers their actions: what they ask
 * the integration for, and what they answer.
 */
class RuntimeToolsTest {

    private static final long PID = 4712L;

    @TempDir
    Path home;

    private Path previousHome;
    private FakeIntegration integration;
    // the answer of the integration to each action
    private final Map<String, Object> answers = new ConcurrentHashMap<>();

    @BeforeEach
    void start() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        integration = new FakeIntegration(PID, action -> answers.get(action.getString("action")));
    }

    @AfterEach
    void stop() throws Exception {
        integration.close();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void theSourceOfTheRoutes() throws Exception {
        answers.put("source", Map.of("routes", List.of()));

        assertThat(call("get_route_source", Map.of())).isEqualTo("{\"routes\":[]}");
        assertThat(integration.lastAction().getString("filter")).isEqualTo("*");

        call("get_route_source", Map.of("filter", "orders*"));
        assertThat(integration.lastAction().getString("filter")).isEqualTo("orders*");
    }

    @Test
    void theDumpOfTheRoutes() throws Exception {
        answers.put("route-dump", Map.of("routes", List.of()));

        call("get_route_dump", Map.of());
        assertThat(integration.lastAction().getString("id")).isEqualTo("*");
        assertThat(integration.lastAction().getString("format")).isEqualTo("yaml");

        call("get_route_dump", Map.of("routeId", "orders", "format", "xml"));
        assertThat(integration.lastAction().getString("id")).isEqualTo("orders");
        assertThat(integration.lastAction().getString("format")).isEqualTo("xml");
    }

    @Test
    void theStructureAndTheTopProcessorsAndTheTopology() throws Exception {
        answers.put("route-structure", Map.of("routes", List.of()));
        answers.put("top-processors", Map.of("processors", List.of()));
        answers.put("route-topology", Map.of("nodes", List.of()));

        call("get_route_structure", Map.of());
        assertThat(integration.lastAction().getString("id")).isEqualTo("*");
        call("get_top_processors", Map.of());
        assertThat(integration.lastAction().getString("action")).isEqualTo("top-processors");
        call("get_route_topology", Map.of());
        assertThat(integration.lastAction().getString("metric")).isEqualTo("true");
        assertThat(integration.lastAction().getString("external")).isEqualTo("true");
        call("get_route_topology", Map.of("metric", "false", "external", "false"));
        assertThat(integration.lastAction().getString("metric")).isEqualTo("false");
    }

    @Test
    void theTracing() throws Exception {
        answers.put("trace", Map.of("enabled", true));

        call("trace_control", Map.of("action", "enable"));
        assertThat(integration.lastAction().getString("enabled")).isEqualTo("true");
        call("trace_control", Map.of("action", "DISABLE"));
        assertThat(integration.lastAction().getString("enabled")).isEqualTo("false");
        call("trace_control", Map.of("action", "dump"));
        assertThat(integration.lastAction().getString("dump")).isEqualTo("true");

        assertThatThrownBy(() -> call("trace_control", Map.of("action", "start")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Unknown trace action: start. Use 'enable', 'disable', or 'dump'.");
        assertThatThrownBy(() -> call("trace_control", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("action is required (enable, disable, dump)");
    }

    @Test
    void browsingAnEndpoint() throws Exception {
        answers.put("browse", Map.of("browse", List.of()));

        call("browse_endpoint", Map.of("endpoint", "seda:orders"));
        assertThat(integration.lastAction().getString("filter")).isEqualTo("seda:orders");
        assertThat(integration.lastAction().getInteger("limit")).isEqualTo(50);
        call("browse_endpoint", Map.of("endpoint", "seda:orders", "limit", "5"));
        assertThat(integration.lastAction().getInteger("limit")).isEqualTo(5);

        assertThatThrownBy(() -> call("browse_endpoint", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("endpoint is required");
    }

    @Test
    void theSpansAndTheStartupStepsAndTheThreads() throws Exception {
        answers.put("span", Map.of("spans", List.of()));
        answers.put("startup-recorder", Map.of("steps", List.of()));
        answers.put("thread-dump", Map.of("threads", List.of()));
        answers.put("heap-histogram", Map.of("classes", List.of()));

        call("get_spans", Map.of());
        assertThat(integration.lastAction().getString("limit")).isEqualTo("100");
        call("get_spans", Map.of("limit", "7"));
        assertThat(integration.lastAction().getString("limit")).isEqualTo("7");
        call("get_startup_steps", Map.of());
        assertThat(integration.lastAction().getString("action")).isEqualTo("startup-recorder");
        call("get_thread_dump", Map.of());
        assertThat(integration.lastAction().getString("action")).isEqualTo("thread-dump");
        call("get_heap_histogram", Map.of());
        assertThat(integration.lastAction().getString("action")).isEqualTo("heap-histogram");
    }

    @Test
    void aHeapDumpWithANameAndOnlyTheLiveObjects() throws Exception {
        answers.put("heap-dump", Map.of("file", "/tmp/orders.hprof"));

        call("write_heap_dump", Map.of());
        assertThat(integration.lastAction().containsKey("name")).isFalse();
        assertThat(integration.lastAction().containsKey("live")).isFalse();

        call("write_heap_dump", Map.of("name", "orders", "live", "false"));
        assertThat(integration.lastAction().getString("name")).isEqualTo("orders");
        assertThat(integration.lastAction().getString("live")).isEqualTo("false");
    }

    @Test
    void aMessageWithHeaders() throws Exception {
        answers.put("send", Map.of("status", "success", "elapsed", 5));

        Object answer = call("send_message", Map.of("endpoint", "direct:orders", "body", "hello",
                "headers", "id=1\norigin = web\n"));

        assertThat(answer.toString()).contains("\"status\":\"success\"");
        JsonObject action = integration.lastAction();
        assertThat(action.getString("endpoint")).isEqualTo("direct:orders");
        assertThat(action.getString("body")).isEqualTo("hello");
        // as the send action of the CLI connector reads them: a key and a value for each header
        List<JsonObject> headers = action.getCollection("headers");
        assertThat(headers).hasSize(2);
        assertThat(headers.get(0)).containsEntry("key", "id").containsEntry("value", "1");
        assertThat(headers.get(1)).containsEntry("key", "origin").containsEntry("value", " web");
    }

    @Test
    void aHeaderThatIsNotAKeyAndAValueIsAnError() {
        answers.put("send", Map.of("status", "success"));

        assertThatThrownBy(() -> call("send_message", Map.of("endpoint", "direct:orders", "headers", "id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Header must be in key=value format, was: id");
    }

    @Test
    void aSqlQuery() throws Exception {
        answers.put("sql-query", Map.of("columns", List.of("id"), "rows", List.of()));

        call("execute_sql", Map.of("query", "select id from orders"));
        JsonObject action = integration.lastAction();
        assertThat(action.getString("sql")).isEqualTo("select id from orders");
        assertThat(action.getInteger("maxRows")).isEqualTo(100);
        assertThat(action.getInteger("queryTimeout")).isEqualTo(30);
        assertThat(action.containsKey("datasource")).isFalse();

        call("execute_sql", Map.of("query", "select 1", "datasource", "ordersDS", "maxRows", "5"));
        assertThat(integration.lastAction().getString("datasource")).isEqualTo("ordersDS");
        assertThat(integration.lastAction().getInteger("maxRows")).isEqualTo(5);
    }

    @Test
    void controllingARoute() throws Exception {
        answers.put("route", Map.of("status", "success"));

        assertThat(call("stop_route", Map.of("routeId", "orders"))).isEqualTo("Route orders: stop done");
        assertThat(integration.lastAction().getString("id")).isEqualTo("orders");
        assertThat(integration.lastAction().getString("command")).isEqualTo("stop");

        answers.put("route", Map.of("status", "failed", "error", "No route with id: payments"));
        assertThatThrownBy(() -> call("start_route", Map.of("routeId", "payments")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No route with id: payments");
    }

    @Test
    void theCircuitBreakersOfEachKind() throws Exception {
        JsonObject resilience = new JsonObject(
                Map.of("routeId", "orders", "id", "cb1", "state", "OPEN",
                        "bufferedCalls", 10, "successfulCalls", 4, "failedCalls", 6, "notPermittedCalls", 2,
                        "failureRate", 60.0, "fallbackCalls", 8, "timedOutCalls", 1));
        JsonObject tolerance = new JsonObject(
                Map.of("routeId", "payments", "id", "cb2", "state", "CLOSED",
                        "successfulCalls", 3, "bulkheadRejectedCalls", 5));
        JsonObject core = new JsonObject(Map.of("routeId", "audit", "state", "HALF_OPEN"));
        JsonObject status = new JsonObject();
        status.put("context", new JsonObject(Map.of("name", "myApp")));
        status.put("resilience4j", new JsonObject(Map.of("circuitBreakers", new JsonArray(List.of(resilience)))));
        status.put("fault-tolerance", new JsonObject(Map.of("circuitBreakers", new JsonArray(List.of(tolerance)))));
        status.put("circuit-breaker", new JsonObject(Map.of("circuitBreakers", new JsonArray(List.of(core)))));
        writeStatus(status);

        JsonObject answer = (JsonObject) Jsoner.deserialize(call("get_circuit_breakers", Map.of()).toString());

        assertThat(answer.getInteger("count")).isEqualTo(3);
        List<JsonObject> breakers = answer.getCollection("circuitBreakers");
        assertThat(breakers.get(0)).containsEntry("component", "resilience4j").containsEntry("state", "OPEN");
        // the calls of the fallback, the timeouts and the full bulkhead, as camel get circuit-breaker shows them
        assertThat(breakers.get(0).getInteger("fallbackCalls")).isEqualTo(8);
        assertThat(breakers.get(0).getInteger("timedOutCalls")).isEqualTo(1);
        assertThat(breakers.get(1)).containsEntry("component", "fault-tolerance");
        assertThat(breakers.get(1).getInteger("bulkheadRejectedCalls")).isEqualTo(5);
        assertThat(breakers.get(2)).containsEntry("component", "core").containsEntry("state", "HALF_OPEN");
        assertThat(answer.containsKey("hint")).isFalse();
    }

    @Test
    void noCircuitBreakers() throws Exception {
        writeStatus(new JsonObject(Map.of("context", new JsonObject(Map.of("name", "myApp")))));

        JsonObject answer = (JsonObject) Jsoner.deserialize(call("get_circuit_breakers", Map.of()).toString());

        assertThat(answer.getInteger("count")).isZero();
        assertThat(answer.getString("hint")).startsWith("No circuit breakers found.");
    }

    @Test
    void theSectionsOfTheStatus() throws Exception {
        JsonObject status = new JsonObject();
        status.put("micrometer", new JsonObject(Map.of("counters", new JsonArray())));
        status.put("dataSources", new JsonObject(Map.of("dataSources", new JsonArray())));
        writeStatus(status);

        assertThat(call("get_metrics", Map.of())).isEqualTo("{\"counters\":[]}");
        assertThat(call("get_datasources", Map.of())).isEqualTo("{\"dataSources\":[]}");
    }

    @Test
    void theErrorsAndTheHistory() throws Exception {
        assertThat(call("get_errors", Map.of())).isEqualTo("No errors captured.");
        assertThat(call("get_history", Map.of())).isEqualTo("No message history available.");

        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-error.json"), "{\"errors\":[]}");
        assertThat(call("get_errors", Map.of())).isEqualTo("{\"errors\":[]}");
    }

    @Test
    void theAnalysisToolsHaveTheAnswersOfTheIntegrationAsJson() throws Exception {
        answers.put("route-structure", Map.of("routes", List.of(Map.of("routeId", "orders"))));
        answers.put("route-dump", Map.of("routes", List.of(Map.of("routeId", "orders"))));
        answers.put("top-processors", Map.of("processors", List.of()));
        answers.put("source", Map.of("routes", List.of()));

        JsonObject analysis = (JsonObject) Jsoner.deserialize(call("get_route_analysis", Map.of()).toString());
        assertThat(object(analysis, "routeStructure").get("routes")).asInstanceOf(LIST).hasSize(1);
        assertThat(object(analysis, "routeDump")).containsKey("routes");
        assertThat(object(analysis, "analysisHints")).containsKey("errorHandling");

        JsonObject stats = (JsonObject) Jsoner.deserialize(call("get_eip_stats", Map.of()).toString());
        assertThat(object(stats, "processorStats")).containsKey("processors");
        assertThat(object(stats, "routeStructures")).containsKey("routes");

        JsonObject drift = (JsonObject) Jsoner.deserialize(call("detect_config_drift", Map.of("format", "xml")).toString());
        assertThat(object(drift, "runningRoutes")).containsKey("routes");
        assertThat(object(drift, "sourceFiles")).containsKey("routes");
        assertThat(drift.getString("format")).isEqualTo("xml");
        // the dump in the format asked for
        assertThat(integration.actions().stream().filter(a -> "route-dump".equals(a.getString("action")))
                .map(a -> a.getString("format")).toList()).containsExactly("yaml", "xml");
    }

    private static Object call(String tool, Map<String, String> args) throws Exception {
        ToolContext ctx = new ToolContext();
        ctx.selectProcess(PID);
        return ToolRegistry.execute(tool, ctx, new HashMap<>(args));
    }

    // the value of the key, which must be JSON (not JSON text)
    private static JsonObject object(JsonObject json, String key) {
        assertThat(json.get(key)).isInstanceOf(JsonObject.class);
        return (JsonObject) json.get(key);
    }

    private static void writeStatus(JsonObject status) throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"), status.toJson());
    }
}

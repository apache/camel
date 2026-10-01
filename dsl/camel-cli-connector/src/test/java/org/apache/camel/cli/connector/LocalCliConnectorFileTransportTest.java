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
package org.apache.camel.cli.connector;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pins down the file protocol the Camel CLI relies on ({@code ~/.camel/{pid}-*.json}), so the connector can be
 * refactored without changing what is written to disk.
 * <p>
 * Isolated because it changes {@code user.home}, and every connector in this JVM writes files named after the same pid.
 */
@Isolated
class LocalCliConnectorFileTransportTest extends CamelTestSupport {

    private static final String PID = String.valueOf(ProcessHandle.current().pid());

    @TempDir
    Path home;

    private String oldHome;
    private File dir;
    private LocalCliConnector connector;
    private final AtomicInteger sigterms = new AtomicInteger();

    @Override
    protected boolean useJmx() {
        // status and route dumps are built from the JMX management layer
        return true;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setBacklogTracing(true);
        // camel-cli-connector is on the classpath, so the context would start its own connector writing the same
        // {pid} files as the connector under test
        DefaultCliConnectorFactory disabled = new DefaultCliConnectorFactory();
        disabled.setEnabled(false);
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:hello").routeId("hello").setBody(simple("Hello ${body}"));
                from("direct:other").routeId("other").to("mock:other");
            }
        };
    }

    @BeforeEach
    void useTempHome() {
        // the connector writes to ${user.home}/.camel, keep the tests away from the real one
        oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        dir = home.resolve(".camel").toFile();
    }

    @AfterEach
    void restoreHome() {
        if (connector != null) {
            connector.stop();
        }
        System.setProperty("user.home", oldHome);
    }

    @Test
    void createsTheFilesTheCliExpects() {
        startConnector();

        assertThat(dir).isDirectoryContaining(f -> f.getName().equals(PID));
        // {pid}-action.json is created too, but the first poll consumes it (it is empty)
        for (String suffix : List.of("status", "output", "trace", "history", "error", "debug", "receive",
                "activity")) {
            assertThat(file(PID + "-" + suffix + ".json")).exists();
        }
    }

    @Test
    void writesStatusSnapshot() {
        startConnector();

        JsonObject status = awaitJson(file(PID + "-status.json"));
        JsonObject runtime = status.getMap("runtime");
        JsonObject ctx = status.getMap("context");
        assertThat(runtime.getLong("pid")).isEqualTo(ProcessHandle.current().pid());
        assertThat(ctx.getString("name")).isEqualTo(context.getName());
        assertThat(routeState(status, "hello")).isEqualTo("Started");
    }

    @Test
    void routeActionStopsRouteWithoutWritingOutput() {
        startConnector();

        writeAction(action("route", "command", "stop", "id", "hello"), null);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(context.getRouteController().getRouteStatus("hello"))
                        .isEqualTo(ServiceStatus.Stopped));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(file(PID + "-action.json")).doesNotExist());
        assertThat(file(PID + "-output.json")).isEmpty();
    }

    @Test
    void sendActionWritesReplyToLegacyOutputFile() {
        startConnector();

        writeAction(action("send", "endpoint", "direct:hello", "body", "World", "exchangePattern", "InOut"),
                null);

        JsonObject out = awaitJson(file(PID + "-output.json"));
        assertThat(out.getString("status")).isEqualTo("success");
        assertThat(out.toJson()).contains("Hello World");
    }

    @Test
    void actionsWithRequestIdWriteToTheirOwnOutputFile() {
        startConnector();

        writeAction(action("route-dump", "filter", "hello", "format", "yaml"), "r1");
        writeAction(action("route-dump", "filter", "other", "format", "yaml"), "r2");

        List<JsonObject> routes1 = awaitJson(file(PID + "-output-r1.json")).getCollection("routes");
        assertThat(routes1).extracting(r -> r.getString("routeId")).containsExactly("hello");
        List<JsonObject> routes2 = awaitJson(file(PID + "-output-r2.json")).getCollection("routes");
        assertThat(routes2).extracting(r -> r.getString("routeId")).containsExactly("other");
        assertThat(file(PID + "-output.json")).isEmpty();
    }

    @Test
    void unknownActionIsIgnored() {
        startConnector();

        writeAction(action("does-not-exist"), "r1");

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(file(PID + "-action-r1.json")).doesNotExist());
        assertThat(file(PID + "-output-r1.json")).doesNotExist();
    }

    @Test
    void traceFileOnlyAppendsNewTraces() throws Exception {
        startConnector();

        template.sendBody("direct:other", "one");
        File traceFile = file(PID + "-trace.json");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(traceLines(traceFile)).hasSize(1));
        long firstMax = maxUid(traceLines(traceFile).get(0));

        template.sendBody("direct:other", "two");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(traceLines(traceFile)).hasSize(2));
        JsonArray second = traceLines(traceFile).get(1).getCollection("traces");
        assertThat(second).allSatisfy(t -> assertThat(((JsonObject) t).getLong("uid")).isGreaterThan(firstMax));
    }

    @Test
    void deletingTheLockFileTriggersSigtermOnce() throws Exception {
        startConnector();

        Files.delete(file(PID).toPath());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(sigterms).hasValue(1));
        // give the connector a few more polls, it must not terminate twice
        await().during(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(sigterms).hasValue(1));
    }

    @Test
    void stopDeletesTheFiles() {
        startConnector();
        awaitJson(file(PID + "-status.json"));

        connector.stop();
        connector = null;

        assertThat(dir.listFiles((d, name) -> name.startsWith(PID))).isEmpty();
    }

    private void startConnector() {
        connector = new LocalCliConnector(new DefaultCliConnectorFactory()) {
            @Override
            public void sigterm() {
                // do not stop the test context, only record the call
                sigterms.incrementAndGet();
            }
        };
        connector.setCamelContext(context);
        connector.start();
    }

    private File file(String name) {
        return new File(dir, name);
    }

    private static JsonObject action(String name, String... keyValues) {
        JsonObject jo = new JsonObject();
        jo.put("action", name);
        for (int i = 0; i < keyValues.length; i += 2) {
            jo.put(keyValues[i], keyValues[i + 1]);
        }
        return jo;
    }

    /**
     * Writes the action atomically (the connector polls, and would otherwise read a half-written file).
     */
    private void writeAction(JsonObject action, String requestId) {
        String name = requestId != null ? PID + "-action-" + requestId + ".json" : PID + "-action.json";
        if (requestId == null) {
            // the connector creates an empty legacy action file on start and deletes it on its first poll, which would
            // also delete an action written in between
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(file(name)).doesNotExist());
        }
        try {
            Path tmp = dir.toPath().resolve(UUID.randomUUID() + ".tmp");
            Files.writeString(tmp, action.toJson());
            Files.move(tmp, dir.toPath().resolve(name), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonObject awaitJson(File file) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(file).isNotEmpty());
        return await().atMost(10, TimeUnit.SECONDS).until(() -> readJson(file), jo -> jo != null);
    }

    private static JsonObject readJson(File file) {
        try {
            String text = Files.readString(file.toPath());
            return text.isBlank() ? null : (JsonObject) Jsoner.deserialize(text);
        } catch (Exception e) {
            // the file is rewritten while we read it
            return null;
        }
    }

    private static String routeState(JsonObject status, String routeId) {
        if (status == null) {
            return null;
        }
        List<JsonObject> routes = status.getCollection("routes");
        return routes.stream().filter(r -> routeId.equals(r.getString("routeId")))
                .map(r -> r.getString("state")).findFirst().orElse(null);
    }

    private static List<JsonObject> traceLines(File traceFile) throws Exception {
        List<JsonObject> answer = new ArrayList<>();
        for (String line : Files.readAllLines(traceFile.toPath())) {
            if (!line.isBlank()) {
                answer.add((JsonObject) Jsoner.deserialize(line));
            }
        }
        return answer;
    }

    private static long maxUid(JsonObject traceLine) {
        List<Map<String, Object>> traces = traceLine.getCollection("traces");
        return traces.stream().mapToLong(t -> ((Number) t.get("uid")).longValue()).max().orElseThrow();
    }
}

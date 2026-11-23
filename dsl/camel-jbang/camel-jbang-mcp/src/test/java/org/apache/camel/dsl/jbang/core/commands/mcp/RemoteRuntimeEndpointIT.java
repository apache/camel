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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.cli.connector.CliWebSocketClient;
import org.apache.camel.cli.connector.CliWebSocketHandshakeException;
import org.apache.camel.cli.connector.DefaultCliConnectorFactory;
import org.apache.camel.cli.connector.JdkCliWebSocketClient;
import org.apache.camel.cli.connector.LocalCliConnector;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Black-box test of the packaged Quarkus MCP server and the real Camel CLI WebSocket transport. The MCP server runs in
 * a Java container; the integration connects from the test JVM over TCP.
 */
@Testcontainers
class RemoteRuntimeEndpointIT {

    private static final int HTTP_PORT = 8080;
    private static final String SERVER_TOKEN = "phase2-it-token-not-for-production";

    @Container
    static final GenericContainer<?> SERVER = createServer();

    @Test
    void authenticatesAndAcceptsTheRealCamelConnectorProtocol() throws Exception {
        String rejectedTokenId = "phase2-rejected-token";
        try (ConnectorHarness rejected = ConnectorHarness.start(websocketUrl(rejectedTokenId), "wrong-token")) {
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                    () -> assertThat(rejected.client.rejectedStatusCodes).contains(401));
            assertThat(rejected.client.openConnections).hasValue(0);
        }

        String rejectedExecutionId = "phase2-missing-execution-id";
        try (ConnectorHarness rejected = ConnectorHarness.start(websocketUrlWithoutExecutionId(), SERVER_TOKEN)) {
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                    () -> assertThat(rejected.client.rejectedStatusCodes).contains(400));
            assertThat(rejected.client.openConnections).hasValue(0);
        }

        String executionId = "phase2-live-connector";
        try (ConnectorHarness accepted = ConnectorHarness.start(websocketUrl(executionId), SERVER_TOKEN)) {
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                    () -> assertThat(accepted.client.helloFrames).hasValue(1));
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                    () -> assertThat(accepted.client.statusSnapshots).hasValueGreaterThan(0));
            await().atMost(20, TimeUnit.SECONDS).until(
                    () -> SERVER.getLogs().contains("Remote Camel integration connected: " + executionId));

            // The server must keep the accepted session alive while the actual connector sends its snapshots
            // and heartbeat pings. An invalid protocol frame would close the socket and trigger a reconnect.
            await().during(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(accepted.client.closeEvents).hasValue(0);
                assertThat(accepted.client.connectionErrors).isEmpty();
            });
        }

        await().atMost(10, TimeUnit.SECONDS).until(
                () -> SERVER.getLogs().contains("Remote Camel integration disconnected: " + executionId));
        String logs = SERVER.getLogs();
        assertThat(logs).doesNotContain("Remote Camel integration connected: " + rejectedTokenId);
        assertThat(logs).doesNotContain("Remote Camel integration connected: " + rejectedExecutionId);
    }

    private static GenericContainer<?> createServer() {
        String jar = System.getProperty("camel.test.runner.jar");
        if (jar == null || jar.isBlank()) {
            throw new IllegalStateException("The Maven Failsafe property camel.test.runner.jar is not configured");
        }
        Path runnerJar = Path.of(jar).toAbsolutePath().normalize();
        if (!Files.isRegularFile(runnerJar)) {
            throw new IllegalStateException(
                    "The packaged camel-jbang-mcp runner jar does not exist: " + runnerJar
                                            + "; run the Maven package phase before this integration test");
        }

        return new GenericContainer<>(DockerImageName.parse("mirror.gcr.io/library/eclipse-temurin:21-jre-jammy"))
                .withExposedPorts(HTTP_PORT)
                // Stream server startup logs into the Failsafe output for actionable boot/extension failures.
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(RemoteRuntimeEndpointIT.class)))
                .withCopyFileToContainer(MountableFile.forHostPath(runnerJar.toString()),
                        "/opt/camel/camel-jbang-mcp-runner.jar")
                // Override the base image entrypoint explicitly, then invoke the real packaged application.
                // The stdio MCP transport is packaged alongside HTTP. Keep Docker stdin open so EOF does not
                // trigger Quarkus shutdown while this test exercises the HTTP/WebSocket endpoint.
                .withCreateContainerCmdModifier(command -> command.withEntrypoint("java").withStdinOpen(true))
                .withCommand(
                        "-Dquarkus.http.host-enabled=true",
                        "-Dquarkus.http.host=0.0.0.0",
                        "-Dquarkus.http.port=" + HTTP_PORT,
                        "-Dquarkus.log.level=INFO",
                        "-Dcamel.mcp.remote.enabled=true",
                        "-Dcamel.mcp.remote.token=" + SERVER_TOKEN,
                        "-jar", "/opt/camel/camel-jbang-mcp-runner.jar")
                .waitingFor(Wait.forHttp("/").forStatusCodeMatching(status -> status >= 200 && status < 500)
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }

    private static String websocketUrl(String executionId) {
        return "ws://" + SERVER.getHost() + ":" + SERVER.getMappedPort(HTTP_PORT)
               + "/connect?executionId=" + executionId;
    }

    private static String websocketUrlWithoutExecutionId() {
        return "ws://" + SERVER.getHost() + ":" + SERVER.getMappedPort(HTTP_PORT) + "/connect";
    }

    private static final class ConnectorHarness implements AutoCloseable {

        final DefaultCamelContext context;
        final LocalCliConnector connector;
        final RecordingClient client;

        private ConnectorHarness(DefaultCamelContext context, LocalCliConnector connector, RecordingClient client) {
            this.context = context;
            this.connector = connector;
            this.client = client;
        }

        static ConnectorHarness start(String url, String token) throws Exception {
            DefaultCamelContext context = new DefaultCamelContext();
            context.getPropertiesComponent().addOverrideProperty("camel.cli.transport", "websocket");
            context.getPropertiesComponent().addOverrideProperty("camel.cli.websocket.url", url);
            context.getPropertiesComponent().addOverrideProperty("camel.cli.websocket.token", token);
            // The test reaches a container through a mapped host port. The connection is isolated to this test network.
            context.getPropertiesComponent().addOverrideProperty("camel.cli.websocket.allow-insecure", "true");
            context.getPropertiesComponent().addOverrideProperty("camel.cli.websocket.snapshot-interval", "100");
            context.getPropertiesComponent().addOverrideProperty("camel.cli.websocket.reconnect-delay", "100");
            context.getPropertiesComponent().addOverrideProperty("camel.cli.websocket.reconnect-max-delay", "200");
            context.getPropertiesComponent().addOverrideProperty("camel.cli.websocket.heartbeat-interval", "250");

            RecordingClient client = new RecordingClient();
            context.getRegistry().bind("phase2RecordingWebSocketClient", client);

            // Suppress service-loaded auto-start: this test explicitly starts the real connector below.
            DefaultCliConnectorFactory disabled = new DefaultCliConnectorFactory();
            disabled.setEnabled(false);
            context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
            context.start();

            LocalCliConnector connector = new LocalCliConnector(new DefaultCliConnectorFactory());
            connector.setCamelContext(context);
            connector.start();
            return new ConnectorHarness(context, connector, client);
        }

        @Override
        public void close() throws Exception {
            try {
                connector.stop();
            } finally {
                context.stop();
            }
        }
    }

    /**
     * Observes frames from the real WebSocketCliConnectorTransport while delegating all socket I/O to the JDK client.
     */
    private static final class RecordingClient implements CliWebSocketClient {

        private final JdkCliWebSocketClient delegate = new JdkCliWebSocketClient();
        private final AtomicInteger openConnections = new AtomicInteger();
        private final AtomicInteger closeEvents = new AtomicInteger();
        private final AtomicInteger helloFrames = new AtomicInteger();
        private final AtomicInteger statusSnapshots = new AtomicInteger();
        private final List<Integer> rejectedStatusCodes = new CopyOnWriteArrayList<>();
        private final List<Throwable> connectionErrors = new CopyOnWriteArrayList<>();

        @Override
        public String getName() {
            return "phase2-test-jdk";
        }

        @Override
        public CompletionStage<Channel> connect(URI url, Map<String, String> headers, Listener listener) {
            return delegate.connect(url, headers, new Listener() {
                @Override
                public void onText(String text) {
                    listener.onText(text);
                }

                @Override
                public void onPong() {
                    listener.onPong();
                }

                @Override
                public void onClose(int statusCode, String reason) {
                    closeEvents.incrementAndGet();
                    listener.onClose(statusCode, reason);
                }

                @Override
                public void onError(Throwable error) {
                    connectionErrors.add(error);
                    listener.onError(error);
                }
            }).whenComplete((channel, failure) -> {
                if (failure == null) {
                    openConnections.incrementAndGet();
                } else {
                    Integer status = handshakeStatus(failure);
                    if (status != null) {
                        rejectedStatusCodes.add(status);
                    } else {
                        connectionErrors.add(failure);
                    }
                }
            }).thenApply(channel -> new Channel() {
                @Override
                public CompletionStage<?> sendText(String text) {
                    recordOutgoing(text);
                    return channel.sendText(text);
                }

                @Override
                public CompletionStage<?> sendPing() {
                    return channel.sendPing();
                }

                @Override
                public CompletionStage<?> close(int code, String reason) {
                    return channel.close(code, reason);
                }

                @Override
                public void abort() {
                    channel.abort();
                }
            });
        }

        private void recordOutgoing(String text) {
            try {
                Object value = Jsoner.deserialize(text);
                if (!(value instanceof JsonObject frame)) {
                    connectionErrors.add(new IllegalStateException("Connector sent a non-object JSON frame"));
                    return;
                }
                if ("hello".equals(frame.getString("type"))) {
                    helloFrames.incrementAndGet();
                } else if ("snapshot".equals(frame.getString("type"))
                        && "status".equals(frame.getString("kind"))) {
                    statusSnapshots.incrementAndGet();
                }
            } catch (Exception e) {
                connectionErrors.add(e);
            }
        }

        private static Integer handshakeStatus(Throwable failure) {
            Throwable current = failure;
            while (current != null) {
                if (current instanceof CliWebSocketHandshakeException handshake) {
                    return handshake.getStatusCode();
                }
                current = current.getCause();
            }
            return null;
        }
    }
}

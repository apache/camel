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
package org.apache.camel.component.debezium;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.debezium.config.Configuration;
import io.debezium.util.IoUtil;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.debezium.configuration.FileConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded engine runs on its own thread and reports a failure only through its completion callback. A failure
 * while the engine starts, here an offset store it cannot read, must fail the start of the route instead of leaving the
 * route started and silent.
 */
public class DebeziumConsumerEngineFailureTest extends CamelTestSupport {

    private static final String ROUTE_ID = "debezium-failing-engine";
    private static final Path TEST_FILE_PATH
            = Paths.get("target/data", "camel-debezium-engine-failure-input.txt").toAbsolutePath();
    private static final Path TEST_OFFSET_STORE_PATH
            = Paths.get("target/data", "camel-debezium-engine-failure-offset-store.txt").toAbsolutePath();

    @BeforeAll
    static void createFolder() throws IOException {
        // the folder of the offset file must exist when the endpoint is created, which is before beforeEach
        Files.createDirectories(TEST_OFFSET_STORE_PATH.getParent());
    }

    @BeforeEach
    public void beforeEach() throws IOException {
        IoUtil.createFile(TEST_FILE_PATH);
        // an offset store the engine cannot read, which is what a corrupted offset file looks like; the
        // content must be long enough not to be mistaken for an empty store
        Files.write(IoUtil.createFile(TEST_OFFSET_STORE_PATH).toPath(),
                "this is not a serialized offset store".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void afterEach() throws IOException {
        IoUtil.delete(TEST_FILE_PATH);
        IoUtil.delete(TEST_OFFSET_STORE_PATH);
    }

    @Test
    void engineFailureWhileStartingFailsTheStartOfTheRoute() throws Exception {
        final DebeziumConsumer consumer = (DebeziumConsumer) context.getRoute(ROUTE_ID).getConsumer();

        final CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();
        consumer.setExceptionHandler(exceptionHandler);

        final Exception e = assertThrows(Exception.class, () -> context.getRouteController().startRoute(ROUTE_ID));

        final Throwable failure = findStartFailure(e);
        assertNotNull(failure, "the start should fail with the failure of the engine, but got: " + e);
        assertTrue(failure.getMessage().contains("route: " + ROUTE_ID), failure.getMessage());
        assertNotNull(failure.getCause(), "the failure of the engine should be the cause");
        assertTrue(failure.getMessage().contains(failure.getCause().getMessage()),
                "the message should carry the failure of the engine: " + failure.getMessage());
        // the offset store cannot be read: the root cause from Kafka Connect is in the message
        assertTrue(failure.getMessage().contains("invalid stream header"), failure.getMessage());

        assertNotEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus(ROUTE_ID));
        assertNull(exceptionHandler.captured.get(),
                "a failure that fails the start should not also be handed to the exception handler");
        assertNotNull(consumer.getEngineFailure(), "the failure should be kept for the health check");
    }

    @Test
    void theStartWaitsAsLongAsTheEngineWaitsForItsTasks() {
        assertEquals(80000L, DebeziumConsumer.startTimeout(Configuration.empty()),
                "by default 40 seconds for the task and as much for the connector");
        assertEquals(4000L, DebeziumConsumer.startTimeout(Configuration.from(
                Map.of("internal.task.management.timeout.ms", "1000", "tasks.max", "3"))));
    }

    private static Throwable findStartFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().startsWith("Debezium engine failed to start")) {
                return t;
            }
        }
        return null;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        final DebeziumTestComponent component = new DebeziumTestComponent(context);

        component.setConfiguration(initConfiguration());
        context.addComponent("debezium", component);
        context.disableJMX();

        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // started by the test, so that the exception handler is in place before the engine runs
                from("debezium:dummy").routeId(ROUTE_ID).autoStartup(false)
                        .to("mock:result");
            }
        };
    }

    private FileConnectorEmbeddedDebeziumConfiguration initConfiguration() {
        final FileConnectorEmbeddedDebeziumConfiguration configuration = new FileConnectorEmbeddedDebeziumConfiguration();
        configuration.setName("engine_failure_dummy");
        configuration.setTopicConfig("engine_failure_topic");
        configuration.setTestFilePath(TEST_FILE_PATH);
        configuration.setOffsetStorageFileName(TEST_OFFSET_STORE_PATH.toString());
        configuration.setOffsetFlushIntervalMs(0);

        return configuration;
    }

    private static final class CapturingExceptionHandler implements ExceptionHandler {

        private final AtomicReference<Throwable> captured = new AtomicReference<>();

        @Override
        public void handleException(Throwable exception) {
            handleException(null, null, exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(message, null, exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            captured.compareAndSet(null, exception);
        }
    }
}

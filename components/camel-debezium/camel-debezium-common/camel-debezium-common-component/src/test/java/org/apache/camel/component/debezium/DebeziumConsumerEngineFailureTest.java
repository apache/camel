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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.debezium.util.IoUtil;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.debezium.configuration.FileConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded engine runs on its own thread and reports a failure only through its completion callback, so without
 * that callback a connector that cannot start leaves the route started, healthy and silent.
 */
public class DebeziumConsumerEngineFailureTest extends CamelTestSupport {

    private static final String ROUTE_ID = "debezium-failing-engine";
    private static final Path TEST_FILE_PATH
            = Paths.get("target/data", "camel-debezium-engine-failure-input.txt").toAbsolutePath();
    private static final Path TEST_OFFSET_STORE_PATH
            = Paths.get("target/data", "camel-debezium-engine-failure-offset-store.txt").toAbsolutePath();

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
    void engineFailureIsReportedToTheExceptionHandlerAndTheHealthCheck() throws Exception {
        final DebeziumConsumer consumer = (DebeziumConsumer) context.getRoute(ROUTE_ID).getConsumer();

        final CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();
        consumer.setExceptionHandler(exceptionHandler);

        context.getRouteController().startRoute(ROUTE_ID);

        assertTrue(exceptionHandler.latch.await(30, TimeUnit.SECONDS),
                "the engine failure should be handed to the consumer exception handler");
        assertNotNull(exceptionHandler.captured.get(), "the failure cause should be reported");

        final HealthCheck healthCheck = consumer.getHealthCheck();
        assertNotNull(healthCheck, "the consumer should expose a health check");
        assertEquals(HealthCheck.State.DOWN, healthCheck.call().getState(),
                "a consumer whose engine has died should not report as healthy");
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

        private final CountDownLatch latch = new CountDownLatch(1);
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
            latch.countDown();
        }
    }
}

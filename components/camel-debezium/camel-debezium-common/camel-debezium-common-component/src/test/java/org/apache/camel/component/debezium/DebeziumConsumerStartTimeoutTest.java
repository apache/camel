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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.debezium.configuration.FileConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.connect.file.FileStreamSourceConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * An engine that does not report within the time it gives itself to start its tasks must not hang the start of the
 * route: the consumer starts as it did before, and reports ready once the engine polls.
 */
class DebeziumConsumerStartTimeoutTest extends CamelTestSupport {

    private static final String ROUTE_ID = "debezium-slow-connector";
    private static volatile CountDownLatch release;

    private Path input;
    private Path offsets;

    @Test
    void theStartDoesNotWaitForeverForAnEngineThatDoesNotReport() throws Exception {
        release = new CountDownLatch(1);
        final DebeziumConsumer consumer = (DebeziumConsumer) context.getRoute(ROUTE_ID).getConsumer();

        // the connector does not start until released, and the start waits 2 x 500 ms
        context.getRouteController().startRoute(ROUTE_ID);
        try {
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus(ROUTE_ID));
            assertFalse(consumer.isEngineReady(), "the engine has not started its tasks yet");
            assertEquals(HealthCheck.State.DOWN, consumer.getHealthCheck().call().getState());

            release.countDown();
            await().atMost(30, TimeUnit.SECONDS).until(consumer::isEngineReady);
            assertEquals(HealthCheck.State.UP, consumer.getHealthCheck().call().getState());
        } finally {
            release.countDown();
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (release != null) {
            release.countDown();
        }
        context.stop();
        Files.deleteIfExists(input);
        Files.deleteIfExists(offsets);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        input = Paths.get("target/data", "camel-debezium-start-timeout-input.txt").toAbsolutePath();
        offsets = Paths.get("target/data", "camel-debezium-start-timeout-offset-store.dat").toAbsolutePath();
        Files.createDirectories(input.getParent());
        Files.deleteIfExists(input);
        Files.deleteIfExists(offsets);
        Files.createFile(input);

        final FileConnectorEmbeddedDebeziumConfiguration configuration = new FileConnectorEmbeddedDebeziumConfiguration() {
            @Override
            protected Class<?> configureConnectorClass() {
                return SlowConnector.class;
            }
        };
        configuration.setName("start_timeout");
        configuration.setTopicConfig("start_timeout");
        configuration.setTestFilePath(input);
        configuration.setOffsetStorageFileName(offsets.toString());
        configuration.setAdditionalProperties(Map.of("internal.task.management.timeout.ms", "500"));

        final DebeziumTestComponent component = new DebeziumTestComponent(context);
        component.setConfiguration(configuration);
        context.addComponent("debezium", component);
        context.disableJMX();
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("debezium:dummy").routeId(ROUTE_ID).autoStartup(false).to("mock:result");
            }
        };
    }

    public static class SlowConnector extends FileStreamSourceConnector {
        @Override
        public void start(Map<String, String> props) {
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release the connector startup");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            super.start(props);
        }
    }
}

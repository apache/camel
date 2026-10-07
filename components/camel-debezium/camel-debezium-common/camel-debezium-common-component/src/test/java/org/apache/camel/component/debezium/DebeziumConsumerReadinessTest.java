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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.debezium.configuration.FileConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.file.FileStreamSourceConnector;
import org.apache.kafka.connect.file.FileStreamSourceTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DebeziumConsumerReadinessTest extends CamelTestSupport {
    private static volatile CountDownLatch starting;
    private static volatile CountDownLatch release;
    private Path input;
    private Path offsets;
    private HealthCheckRegistry registry;

    @ParameterizedTest
    @EnumSource(HealthCheck.State.class)
    void readinessWaitsForPollingAndResetsOnRestart(HealthCheck.State initialState) throws Exception {
        when(registry.getInitialState()).thenReturn(initialState);
        DebeziumConsumer consumer = (DebeziumConsumer) context.getRoute("readiness").getConsumer();
        // Construct after setting the initial state, as with a consumer built during context startup.
        consumer.setHealthCheck(new DebeziumConsumerHealthCheck(consumer, "consumer:readiness"));
        HealthCheck check = consumer.getHealthCheck();
        for (int i = 0; i < 2; i++) {
            starting = new CountDownLatch(1);
            release = new CountDownLatch(1);
            consumer.start();
            try {
                assertTrue(starting.await(30, TimeUnit.SECONDS), "the source task should enter startup");
                assertEquals(initialState, check.call().getState());
                check.setEnabled(false);
                assertEquals(HealthCheck.State.UNKNOWN, check.call().getState());
                check.setEnabled(true);
                release.countDown();
                await().atMost(30, TimeUnit.SECONDS).until(consumer::isEngineReady);
                await().atMost(30, TimeUnit.SECONDS)
                        .untilAsserted(() -> assertEquals(HealthCheck.State.UP, check.call().getState()));
                assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(),
                        "readiness must not require a change event");
            } finally {
                release.countDown();
                consumer.stop();
            }
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
        CamelContext context = super.createCamelContext();
        registry = mock(HealthCheckRegistry.class);
        when(registry.getInitialState()).thenReturn(HealthCheck.State.DOWN);
        context.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);
        input = Files.createTempFile("debezium-readiness-input", ".txt");
        offsets = Files.createTempFile("debezium-readiness-offsets", ".dat");
        FileConnectorEmbeddedDebeziumConfiguration configuration = new FileConnectorEmbeddedDebeziumConfiguration() {
            @Override
            protected Class<?> configureConnectorClass() {
                return DelayedConnector.class;
            }
        };
        configuration.setName("readiness");
        configuration.setTopicConfig("readiness");
        configuration.setTestFilePath(input);
        configuration.setOffsetStorageFileName(offsets.toString());
        DebeziumTestComponent component = new DebeziumTestComponent(context);
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
                from("debezium:dummy").routeId("readiness").autoStartup(false).to("mock:result");
            }
        };
    }

    public static class DelayedConnector extends FileStreamSourceConnector {
        @Override
        public Class<? extends Task> taskClass() {
            return DelayedTask.class;
        }
    }

    public static class DelayedTask extends FileStreamSourceTask {
        @Override
        public void start(Map<String, String> props) {
            starting.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release source task startup");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            super.start(props);
        }
    }
}

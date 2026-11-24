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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.debezium.configuration.FileConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.file.FileStreamSourceConnector;
import org.apache.kafka.connect.file.FileStreamSourceTask;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failure of the engine after it has started cannot fail the start of the route any more, so it goes to the exception
 * handler of the consumer and turns the health check DOWN.
 */
class DebeziumConsumerEngineFailureAfterStartTest extends CamelTestSupport {

    private static final String ROUTE_ID = "debezium-failing-after-start";
    private static final String BRIDGED_ROUTE_ID = "debezium-failing-after-start-bridged";
    private static final Path TEST_FILE_PATH
            = Paths.get("target/data", "camel-debezium-failure-after-start-input.txt").toAbsolutePath();
    private static final Path TEST_OFFSET_STORE_PATH
            = Paths.get("target/data", "camel-debezium-failure-after-start-offset-store.dat").toAbsolutePath();

    @Test
    void engineFailureAfterTheStartIsReportedToTheExceptionHandlerAndTheHealthCheck() throws Exception {
        final DebeziumConsumer consumer = (DebeziumConsumer) context.getRoute(ROUTE_ID).getConsumer();

        final CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();
        consumer.setExceptionHandler(exceptionHandler);

        // the engine starts its task, so the start of the route succeeds; the task fails when it polls
        context.getRouteController().startRoute(ROUTE_ID);
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus(ROUTE_ID));

        assertTrue(exceptionHandler.latch.await(30, TimeUnit.SECONDS),
                "the engine failure should be handed to the consumer exception handler");
        assertTrue(exceptionHandler.message.get().startsWith("Debezium engine has failed and stopped"),
                exceptionHandler.message.get());

        final HealthCheck healthCheck = consumer.getHealthCheck();
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(HealthCheck.State.DOWN, healthCheck.call().getState(),
                        "a consumer whose engine has died should not report as healthy"));
        assertEquals(1, exceptionHandler.count.get(), "the failure should be reported once");
    }

    @Test
    void engineFailureAfterTheStartGoesToTheErrorHandlerOfTheRouteWithBridgeErrorHandler() throws Exception {
        final MockEndpoint failures = getMockEndpoint("mock:engineFailure");
        failures.expectedMessageCount(1);
        failures.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalStateException.class);

        context.getRouteController().startRoute(BRIDGED_ROUTE_ID);

        failures.assertIsSatisfied();
        assertTrue(failures.getExchanges().get(0).getMessage().getBody(String.class)
                .startsWith("Debezium engine has failed and stopped"));
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(TEST_FILE_PATH);
        Files.deleteIfExists(TEST_OFFSET_STORE_PATH);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        Files.createDirectories(TEST_FILE_PATH.getParent());
        Files.deleteIfExists(TEST_FILE_PATH);
        Files.deleteIfExists(TEST_OFFSET_STORE_PATH);
        Files.createFile(TEST_FILE_PATH);

        final FileConnectorEmbeddedDebeziumConfiguration configuration = new FileConnectorEmbeddedDebeziumConfiguration() {
            @Override
            protected Class<?> configureConnectorClass() {
                return FailingPollConnector.class;
            }
        };
        configuration.setName("failure_after_start");
        configuration.setTopicConfig("failure_after_start");
        configuration.setTestFilePath(TEST_FILE_PATH);
        configuration.setOffsetStorageFileName(TEST_OFFSET_STORE_PATH.toString());

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
                // started by the test, so that the exception handler is in place before the engine runs
                from("debezium:dummy").routeId(ROUTE_ID).autoStartup(false)
                        .to("mock:result");

                from("debezium:bridged?bridgeErrorHandler=true").routeId(BRIDGED_ROUTE_ID).autoStartup(false)
                        .onException(Exception.class).handled(true).to("mock:engineFailure").end()
                        .to("mock:result");
            }
        };
    }

    public static class FailingPollConnector extends FileStreamSourceConnector {
        @Override
        public Class<? extends Task> taskClass() {
            return FailingPollTask.class;
        }
    }

    public static class FailingPollTask extends FileStreamSourceTask {
        @Override
        public List<SourceRecord> poll() {
            throw new IllegalStateException("The source went away");
        }
    }

    private static final class CapturingExceptionHandler implements ExceptionHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<String> message = new AtomicReference<>();
        private final AtomicInteger count = new AtomicInteger();

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
            count.incrementAndGet();
            this.message.compareAndSet(null, message);
            latch.countDown();
        }
    }
}

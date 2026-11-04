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
package org.apache.camel.component.azure.cosmosdb;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.azure.cosmos.ChangeFeedProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.health.ConsumersHealthCheckRepository;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The health check of the consumer as camel-health reports it: the readiness check of the consumer of the route
 * ({@code consumer:<routeId>}) is DOWN, with the start failure, until the change feed processor has started, and is not
 * a liveness check.
 */
class CosmosDbConsumerHealthCheckRegistryTest extends CamelTestSupport {

    private final AtomicBoolean reachable = new AtomicBoolean();
    // completes the first attempt to start (with a failure) when the test lets it
    private final CompletableFuture<Void> firstAttempt = new CompletableFuture<>();
    private final AtomicInteger attempts = new AtomicInteger();

    private ChangeFeedProcessor processor() {
        ChangeFeedProcessor processor = mock(ChangeFeedProcessor.class);
        AtomicBoolean started = new AtomicBoolean();
        when(processor.isStarted()).thenAnswer(invocation -> started.get());
        when(processor.stop()).thenAnswer(invocation -> {
            if (!started.compareAndSet(true, false)) {
                throw new IllegalStateException("The ChangeFeedProcessor instance has not fully started");
            }
            return Mono.empty();
        });
        when(processor.start()).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                return Mono.fromFuture(firstAttempt);
            }
            if (!reachable.get()) {
                return Mono.error(new IllegalStateException("Resource Not Found: lease container (test)"));
            }
            started.set(true);
            return Mono.empty();
        });
        return processor;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry(context);
        context.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);
        registry.register(new ConsumersHealthCheckRepository());
        // an azure-cosmosdb endpoint whose consumer uses the change feed processor of the test
        context.addComponent("testcosmos", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
                parameters.clear();
                CosmosDbEndpoint endpoint = (CosmosDbEndpoint) getCamelContext().getComponent("azure-cosmosdb")
                        .createEndpoint(
                                "azure-cosmosdb:" + remaining + "?databaseEndpoint=https://test.com:443&accountKey=myKey");
                return new CosmosDbEndpoint(uri, endpoint.getComponent(), endpoint.getConfiguration()) {
                    @Override
                    public void doStart() {
                        // no Cosmos DB client: the consumer uses the change feed processor of the test
                    }

                    @Override
                    public void doStop() {
                    }

                    @Override
                    public Consumer createConsumer(Processor processor) throws Exception {
                        CosmosDbConsumer consumer = new CosmosDbConsumer(this, processor) {
                            @Override
                            ChangeFeedProcessor createChangeFeedProcessor() {
                                return processor();
                            }
                        };
                        configureConsumer(consumer);
                        return consumer;
                    }
                };
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("testcosmos:mydb/myContainer").routeId("cosmos").to("mock:result");
            }
        };
    }

    private static Optional<HealthCheck.Result> consumerCheck(Iterable<HealthCheck.Result> results) {
        for (HealthCheck.Result result : results) {
            if ("consumer:cosmos".equals(result.getCheck().getId())) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private CosmosDbConsumer startRoute(HealthCheck.State initialState) throws Exception {
        context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class).setInitialState(
                initialState != null ? initialState : HealthCheck.State.DOWN);
        context.addRoutes(createRouteBuilder());
        context.start();
        return (CosmosDbConsumer) context.getRoute("cosmos").getConsumer();
    }

    @ParameterizedTest
    @EnumSource(value = HealthCheck.State.class, names = { "DOWN", "UP" })
    void readinessIsTheInitialStateWhileTheFirstAttemptRuns(HealthCheck.State initialState) throws Exception {
        CosmosDbConsumer consumer = startRoute(initialState);
        await().atMost(20, TimeUnit.SECONDS).until(() -> attempts.get() == 1);

        // camel.health.initial-state, as for the Debezium consumer
        assertEquals(initialState, consumerCheck(HealthCheckHelper.invokeReadiness(context)).orElseThrow().getState());

        // the first attempt fails: DOWN
        firstAttempt.completeExceptionally(new IllegalStateException("Resource Not Found: lease container (test)"));
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getLastStartFailure() != null);
        assertEquals(HealthCheck.State.DOWN,
                consumerCheck(HealthCheckHelper.invokeReadiness(context)).orElseThrow().getState());
    }

    @Test
    void readinessIsDownUntilTheChangeFeedProcessorStarted() throws Exception {
        CosmosDbConsumer consumer = startRoute(null);
        firstAttempt.completeExceptionally(new IllegalStateException("Resource Not Found: lease container (test)"));
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getLastStartFailure() != null);

        HealthCheck.Result down = consumerCheck(HealthCheckHelper.invokeReadiness(context)).orElseThrow();
        assertEquals(HealthCheck.State.DOWN, down.getState());
        assertEquals("Resource Not Found: lease container (test)", down.getError().orElseThrow().getMessage());
        assertTrue(((Number) down.getDetails().get("failed.starts")).longValue() >= 1, down.getDetails().toString());
        // not a liveness check (camel-health only asks the consumer check for readiness)
        assertFalse(consumerCheck(HealthCheckHelper.invokeLiveness(context))
                .filter(result -> result.getState() == HealthCheck.State.DOWN).isPresent());

        reachable.set(true);
        await().atMost(20, TimeUnit.SECONDS).until(
                () -> consumerCheck(HealthCheckHelper.invokeReadiness(context)).orElseThrow().getState()
                      == HealthCheck.State.UP);
    }
}

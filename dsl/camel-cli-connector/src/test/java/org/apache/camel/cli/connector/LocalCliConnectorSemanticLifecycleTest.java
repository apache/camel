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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class LocalCliConnectorSemanticLifecycleTest {
    @Test
    void stopClosesAnExecutorWhoseCreationIsAlreadyInProgress() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var created = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var pool = new AtomicReference<ExecutorService>();
            context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
                @Override
                public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                    ExecutorService executor = super.newThreadPool(source, name, profile);
                    if ("CliSemanticEvaluation".equals(name)) {
                        pool.set(executor);
                        created.countDown();
                        try {
                            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    }
                    return executor;
                }
            });
            var connector = startConnector(context);
            FutureTask<CompletableFuture<Boolean>> dispatch = new FutureTask<>(
                    () -> connector.dispatchAsync(action(), ignored -> {
                    }));
            FutureTask<Void> stop = new FutureTask<>(() -> {
                connector.stop();
                return null;
            });
            Thread caller = new Thread(dispatch);
            Thread stopper = new Thread(stop);
            caller.start();
            try {
                assertThat(created.await(5, TimeUnit.SECONDS)).isTrue();
                stopper.start();
                // Stop must either finish or wait for the first executor to finish initializing.
                await().atMost(5, TimeUnit.SECONDS)
                        .until(() -> stop.isDone() || stopper.getState() == Thread.State.BLOCKED);
                release.countDown();
                stop.get(5, TimeUnit.SECONDS);
                dispatch.get(5, TimeUnit.SECONDS).cancel(true);
                assertThat(connector.isStopped()).isTrue();
                assertThat(pool.get().isShutdown()).isTrue();
            } finally {
                release.countDown();
                caller.join(5000);
                stopper.join(5000);
                connector.stop();
                if (pool.get() != null) {
                    pool.get().shutdownNow();
                }
            }
        }
    }

    @Test
    void evaluationCannotCreateAnExecutorAfterStop() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var connector = startConnector(context);
            connector.stop();
            CompletableFuture<Boolean> result = connector.dispatchAsync(action(), ignored -> {
            });
            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("CLI connector is stopping or stopped");
        }
    }

    private static JsonObject action() {
        return new JsonObject(Map.of("action", "semantic-evaluate"));
    }

    private static LocalCliConnector startConnector(DefaultCamelContext context) {
        var disabled = new DefaultCliConnectorFactory();
        disabled.setEnabled(false);
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        context.start();
        var connector = new LocalCliConnector(new DefaultCliConnectorFactory()) {
            @Override
            protected CliConnectorTransport createTransport(String name) {
                // As with WebSocket shutdown, stopping this transport does not join an in-flight dispatch.
                return new CliConnectorTransport() {
                    @Override
                    public void configure(
                            CamelContext camelContext, CliActionDispatcher dispatcher,
                            CliSnapshotProducer snapshots, Runnable shutdown) {
                    }

                    @Override
                    public void start() {
                    }

                    @Override
                    public void stop() {
                    }
                };
            }
        };
        connector.setCamelContext(context);
        connector.start();
        return connector;
    }
}

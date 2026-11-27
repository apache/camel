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
package org.apache.camel.processor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the in-memory saga service stops while a compensation still waits for a retry, the caller that started the
 * compensation and the caller that waits for its outcome both fail, instead of waiting for a retry that never runs.
 */
class SagaStopDuringFinalizationTest extends ContextTestSupport {

    private final CountDownLatch compensationFailed = new CountDownLatch(1);
    private final AtomicReference<CamelSagaCoordinator> coordinator = new AtomicReference<>();
    private InMemorySagaService sagaService;

    @Test
    void stopWhileTheRetryIsScheduledFailsTheCompensation() throws Exception {
        // the retry is still scheduled when the shutdown of the executor gives up waiting and drops it
        sagaService.setMaxRetryAttempts(3);
        sagaService.setRetryDelayInMilliseconds(60000);
        context.getExecutorServiceManager().setShutdownAwaitTermination(500);

        assertCallersFailWhenTheServiceStops();
    }

    @Test
    void stopBeforeTheRetryRunsFailsTheCompensation() throws Exception {
        // the retry runs while the executor shuts down, and the executor drops the attempt it submits
        sagaService.setMaxRetryAttempts(100);
        sagaService.setRetryDelayInMilliseconds(1000);
        context.getExecutorServiceManager().setShutdownAwaitTermination(20000);

        assertCallersFailWhenTheServiceStops();
    }

    private void assertCallersFailWhenTheServiceStops() throws Exception {
        template.sendBody("direct:manual", "hello");
        CamelSagaCoordinator saga = coordinator.get();
        assertNotNull(saga);

        CompletableFuture<Void> first = saga.compensate(context.getEndpoint("direct:first").createExchange());
        assertTrue(compensationFailed.await(10, TimeUnit.SECONDS), "the first attempt of the compensation failed");
        CompletableFuture<Void> second = saga.compensate(context.getEndpoint("direct:second").createExchange());
        await().during(300, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS).until(() -> !first.isDone() && !second.isDone());

        sagaService.stop();

        // before, both waited until the retry, which never runs
        await().atMost(20, TimeUnit.SECONDS).until(() -> first.isDone() && second.isDone());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> first.get(1, TimeUnit.SECONDS));
        assertInstanceOf(RejectedExecutionException.class, ex.getCause());
        ex = assertThrows(ExecutionException.class, () -> second.get(1, TimeUnit.SECONDS));
        assertInstanceOf(RejectedExecutionException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains(saga.getId()), ex.getCause().getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                sagaService = new InMemorySagaService();
                context.addService(sagaService);

                from("direct:manual")
                        .saga().completionMode(SagaCompletionMode.MANUAL)
                        .compensation("direct:compensation-fails")
                        .process(e -> captureCoordinator(e));

                from("direct:compensation-fails")
                        .process(e -> {
                            compensationFailed.countDown();
                            throw new IllegalStateException("compensation failure");
                        });
            }
        };
    }

    private void captureCoordinator(Exchange exchange) throws Exception {
        coordinator.set(sagaService.getSaga(exchange.getExchangeExtension().getSagaLongRunningAction()).get());
    }
}

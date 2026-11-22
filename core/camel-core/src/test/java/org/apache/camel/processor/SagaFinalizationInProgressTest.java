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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compensate() or complete() of the in-memory saga that finds the saga already being compensated or completed (by a
 * step timeout or another caller) gets the outcome of that compensation or completion, not a success before it is
 * known.
 */
public class SagaFinalizationInProgressTest extends ContextTestSupport {

    private final CountDownLatch finalizationStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFinalization = new CountDownLatch(1);
    private final AtomicReference<CamelSagaCoordinator> coordinator = new AtomicReference<>();
    private InMemorySagaService sagaService;

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        releaseFinalization.countDown();
        super.tearDown();
    }

    @Test
    void ownerThatLosesToTheTimeoutCompensationGetsItsFailure() throws Exception {
        CompletableFuture<Exchange> owner = template.asyncSend("direct:fails-after-timeout",
                e -> e.getMessage().setBody("hello"));
        assertTrue(finalizationStarted.await(10, TimeUnit.SECONDS), "the timeout started the compensation");

        // on main the exchange returned at once, as if the saga had been compensated
        await().during(300, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS).until(() -> !owner.isDone());
        releaseFinalization.countDown();

        Exchange result = owner.get(10, TimeUnit.SECONDS);
        Exception ex = result.getException();
        assertInstanceOf(IllegalArgumentException.class, ex);
        assertEquals(1, ex.getSuppressed().length, "the compensation failure should be attached to the exchange");
        assertInstanceOf(IllegalStateException.class, ex.getSuppressed()[0]);
        assertTrue(ex.getSuppressed()[0].getMessage().contains("status is FAILED"), ex.getSuppressed()[0].getMessage());
    }

    @Test
    void compensateWhileTheTimeoutCompensationRunsGetsItsFailure() throws Exception {
        template.sendBody("direct:manual-with-timeout", "hello");
        assertTrue(finalizationStarted.await(10, TimeUnit.SECONDS), "the timeout started the compensation");
        CamelSagaCoordinator saga = coordinator.get();
        assertNotNull(saga);

        // a second caller, as the saga:compensate endpoint
        CompletableFuture<Void> second = saga.compensate(context.getEndpoint("direct:other").createExchange());
        // on main it completed at once, successfully
        await().during(300, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS).until(() -> !second.isDone());
        releaseFinalization.countDown();

        ExecutionException ex = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("status is FAILED"), ex.getCause().getMessage());
    }

    @Test
    void completeWhileAnotherCompletionRunsGetsItsFailure() throws Exception {
        template.sendBody("direct:manual-completion-fails", "hello");
        CamelSagaCoordinator saga = coordinator.get();
        assertNotNull(saga);

        CompletableFuture<Void> first = saga.complete(context.getEndpoint("direct:first").createExchange());
        assertTrue(finalizationStarted.await(10, TimeUnit.SECONDS), "the first call started the completion");
        CompletableFuture<Void> second = saga.complete(context.getEndpoint("direct:second").createExchange());
        // on main it completed at once, successfully
        await().during(300, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS).until(() -> !second.isDone());
        releaseFinalization.countDown();

        assertThrows(ExecutionException.class, () -> first.get(10, TimeUnit.SECONDS));
        ExecutionException ex = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("status is FAILED"), ex.getCause().getMessage());
    }

    @Test
    void compensateWhileTheCompensationRunsGetsItsSuccess() throws Exception {
        template.sendBody("direct:manual-compensation-succeeds", "hello");
        CamelSagaCoordinator saga = coordinator.get();
        assertNotNull(saga);

        CompletableFuture<Void> first = saga.compensate(context.getEndpoint("direct:first").createExchange());
        assertTrue(finalizationStarted.await(10, TimeUnit.SECONDS), "the first call started the compensation");
        CompletableFuture<Void> second = saga.compensate(context.getEndpoint("direct:second").createExchange());
        releaseFinalization.countDown();

        assertNull(first.get(10, TimeUnit.SECONDS));
        assertNull(second.get(10, TimeUnit.SECONDS));
    }

    @Test
    void compensationThatCompensatesItsOwnSagaDoesNotWaitForItself() throws Exception {
        // control, as on main: the compensation route asks to compensate the saga it compensates, and waits for the
        // answer; it is answered at once instead of waiting for the compensation it is part of
        Exchange result = template.send("direct:compensation-compensates-its-saga", e -> e.getMessage().setBody("hello"));

        Exception ex = result.getException();
        assertInstanceOf(IllegalArgumentException.class, ex);
        assertEquals(0, ex.getSuppressed().length, "the compensation succeeded");
        CamelSagaCoordinator saga = coordinator.get();
        assertNull(saga.compensate(result).get(10, TimeUnit.SECONDS));
        assertNull(sagaService.getSaga(saga.getId()).get(), "the saga ended");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                sagaService = new InMemorySagaService();
                sagaService.setMaxRetryAttempts(1);
                context.addService(sagaService);

                // the body outlives the timeout of the step, waits until the compensation runs, and then fails
                from("direct:fails-after-timeout")
                        .saga().timeout(200, TimeUnit.MILLISECONDS).compensation("direct:blocks-then-fails")
                        .process(e -> {
                            assertTrue(finalizationStarted.await(10, TimeUnit.SECONDS));
                            throw new IllegalArgumentException("business failure");
                        });

                from("direct:manual-with-timeout")
                        .saga().completionMode(SagaCompletionMode.MANUAL)
                        .timeout(200, TimeUnit.MILLISECONDS).compensation("direct:blocks-then-fails")
                        .process(e -> captureCoordinator(e));

                from("direct:manual-completion-fails")
                        .saga().completionMode(SagaCompletionMode.MANUAL)
                        .completion("direct:blocks-then-fails")
                        .process(e -> captureCoordinator(e));

                from("direct:manual-compensation-succeeds")
                        .saga().completionMode(SagaCompletionMode.MANUAL)
                        .compensation("direct:blocks-then-succeeds")
                        .process(e -> captureCoordinator(e));

                from("direct:compensation-compensates-its-saga")
                        .saga().compensation("direct:compensates-its-saga")
                        .process(e -> captureCoordinator(e))
                        .process(e -> {
                            throw new IllegalArgumentException("business failure");
                        });

                from("direct:blocks-then-fails")
                        .process(e -> {
                            finalizationStarted.countDown();
                            assertTrue(releaseFinalization.await(10, TimeUnit.SECONDS));
                            throw new IllegalStateException("finalization failure");
                        });

                from("direct:blocks-then-succeeds")
                        .process(e -> {
                            finalizationStarted.countDown();
                            assertTrue(releaseFinalization.await(10, TimeUnit.SECONDS));
                        });

                from("direct:compensates-its-saga")
                        .process(e -> coordinator.get().compensate(e).get(5, TimeUnit.SECONDS));
            }
        };
    }

    private void captureCoordinator(Exchange exchange) throws Exception {
        coordinator.set(sagaService.getSaga(exchange.getExchangeExtension().getSagaLongRunningAction()).get());
    }
}

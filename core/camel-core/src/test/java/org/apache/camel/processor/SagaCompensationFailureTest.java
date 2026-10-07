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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.InMemorySagaService;
import org.apache.camel.spi.Registry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25419: what the saga EIP reports when a compensation cannot be done, and what the in-memory saga service logs
 * when it stops while a compensation is still being retried.
 */
public class SagaCompensationFailureTest extends ContextTestSupport {

    private static final String SAGA_SERVICE_LOGGER = InMemorySagaService.class.getName();

    private final AtomicReference<CamelSagaCoordinator> coordinator = new AtomicReference<>();
    private final AtomicReference<String> timeoutSagaId = new AtomicReference<>();
    private final AtomicInteger flakyCompensationCalls = new AtomicInteger();
    private final List<String> sagaServiceWarnings = new CopyOnWriteArrayList<>();

    @Test
    public void testFailedCompensationKeepsTheOriginalException() {
        Exchange result = template.send("direct:saga-fails", e -> e.getMessage().setBody("hello"));

        Exception ex = result.getException();
        assertNotNull(ex, "the failure should propagate to the exchange");
        assertTrue(chainContains(ex, "business failure"),
                "the exception that caused the compensation should not be lost, but got: " + ex);
    }

    @Test
    public void testFailedCompensationIsNotReportedAsCompensated() throws Exception {
        Exchange result = template.send("direct:saga-fails", e -> e.getMessage().setBody("hello"));
        assertNotNull(result.getException());

        CamelSagaCoordinator saga = coordinator.get();
        assertNotNull(saga);
        // the compensation could not be done: compensating the saga again must not report success
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> saga.compensate(result).get(5, TimeUnit.SECONDS),
                "a saga whose compensation failed is reported as successfully compensated");
        assertNotNull(ex.getCause());
    }

    @Test
    public void testCompensationRetryPendingWhenContextStopsIsLogged() throws Exception {
        // the saga times out, the first compensation attempt fails, and a retry is scheduled
        template.sendBody("direct:saga-timeout", "hello");
        await().atMost(5, TimeUnit.SECONDS).until(() -> flakyCompensationCalls.get() == 1);

        // the in-memory service does not persist sagas: stopping while the retry is pending abandons the saga,
        // which must be logged
        context.stop();

        assertEquals(1, flakyCompensationCalls.get());
        String sagaId = timeoutSagaId.get();
        assertNotNull(sagaId);
        assertTrue(sagaServiceWarnings.stream().anyMatch(m -> m.contains(sagaId)),
                "no warning names the abandoned saga " + sagaId + ": " + sagaServiceWarnings);
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        ConsumingAppender.newAppender(SAGA_SERVICE_LOGGER, "SagaCompensationFailureTest", Level.WARN,
                e -> sagaServiceWarnings.add(e.getMessage().getFormattedMessage()));
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(SAGA_SERVICE_LOGGER);
        ctx.updateLoggers();
    }

    private static boolean chainContains(Throwable t, String message) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(message)) {
                return true;
            }
            for (Throwable s : c.getSuppressed()) {
                if (chainContains(s, message)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                InMemorySagaService sagaService = new InMemorySagaService();
                sagaService.setMaxRetryAttempts(2);
                sagaService.setRetryDelayInMilliseconds(500);
                context.addService(sagaService);

                from("direct:saga-fails")
                        .saga().compensation("direct:compensation-fails")
                        .process(e -> coordinator.set(
                                sagaService.getSaga(e.getExchangeExtension().getSagaLongRunningAction()).get()))
                        .process(e -> {
                            throw new IllegalArgumentException("business failure");
                        });

                from("direct:compensation-fails")
                        .process(e -> {
                            throw new IllegalStateException("compensation failure");
                        });

                from("direct:saga-timeout")
                        .saga().completionMode(SagaCompletionMode.MANUAL).timeout(100, TimeUnit.MILLISECONDS)
                        .compensation("bean:flakyCompensation")
                        .process(e -> timeoutSagaId.set(e.getExchangeExtension().getSagaLongRunningAction()))
                        .to("mock:end");
            }
        };
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("flakyCompensation", new Object() {
            @SuppressWarnings("unused")
            public void compensate() {
                if (flakyCompensationCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("first compensation attempt fails");
                }
            }
        });
        return registry;
    }
}

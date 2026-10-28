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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.InMemorySagaService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25419: what the saga EIP reports when a compensation or a completion cannot be done, and what the in-memory
 * saga service logs when it stops with sagas that are not finalized.
 */
public class SagaCompensationFailureTest extends ContextTestSupport {

    private static final String SAGA_SERVICE_LOGGER = InMemorySagaService.class.getName();

    private final AtomicReference<CamelSagaCoordinator> coordinator = new AtomicReference<>();
    private final AtomicReference<String> manualSagaId = new AtomicReference<>();
    private final List<String> sagaServiceWarnings = new CopyOnWriteArrayList<>();

    @Test
    public void testFailedCompensationKeepsTheOriginalException() {
        Exchange result = template.send("direct:saga-fails", e -> e.getMessage().setBody("hello"));

        // the exception that caused the compensation stays on the exchange
        Exception ex = result.getException();
        assertInstanceOf(IllegalArgumentException.class, ex);
        assertEquals("business failure", ex.getMessage());

        // and the compensation failure is attached to it
        Throwable[] suppressed = ex.getSuppressed();
        assertEquals(1, suppressed.length, "the compensation failure should be attached as a suppressed exception");
        assertInstanceOf(RuntimeCamelException.class, suppressed[0]);
        assertTrue(suppressed[0].getMessage().contains("Unable to compensate all required steps of the saga"),
                suppressed[0].getMessage());
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
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("status is FAILED"), ex.getCause().getMessage());
    }

    @Test
    public void testFailedCompletionIsNotWrapped() {
        Exchange result = template.send("direct:saga-completion-fails", e -> e.getMessage().setBody("hello"));

        // the completion failure is on the exchange as is, not wrapped in a CompletionException
        Exception ex = result.getException();
        assertInstanceOf(RuntimeCamelException.class, ex);
        assertTrue(ex.getMessage().contains("Unable to complete all required steps of the saga"), ex.getMessage());
    }

    @Test
    public void testSagaNotFinalizedWhenContextStopsIsLogged() throws Exception {
        // a saga in MANUAL completion mode that is never completed nor compensated
        template.sendBody("direct:saga-manual", "hello");
        String sagaId = manualSagaId.get();
        assertNotNull(sagaId);

        // the in-memory service does not persist sagas: stopping abandons the saga, which must be logged
        context.stop();

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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                InMemorySagaService sagaService = new InMemorySagaService();
                sagaService.setMaxRetryAttempts(2);
                sagaService.setRetryDelayInMilliseconds(100);
                context.addService(sagaService);

                from("direct:saga-fails")
                        .saga().compensation("direct:finalization-fails")
                        .process(e -> coordinator.set(
                                sagaService.getSaga(e.getExchangeExtension().getSagaLongRunningAction()).get()))
                        .process(e -> {
                            throw new IllegalArgumentException("business failure");
                        });

                from("direct:saga-completion-fails")
                        .saga().completion("direct:finalization-fails")
                        .to("mock:end");

                from("direct:finalization-fails")
                        .process(e -> {
                            throw new IllegalStateException("finalization failure");
                        });

                from("direct:saga-manual")
                        .saga().completionMode(SagaCompletionMode.MANUAL)
                        .compensation("mock:compensation")
                        .process(e -> manualSagaId.set(e.getExchangeExtension().getSagaLongRunningAction()))
                        .to("mock:end");
            }
        };
    }
}

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
package org.apache.camel.jta;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.StepHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The step hook in a JTA transacted route. camel-jta turns off the inherited error handler for the {@code transacted}
 * step and its direct children: a failure of such a child rolls the transaction back and is handled (onException,
 * redelivery of the whole transaction) by the {@link JtaTransactionErrorHandler} that wraps the transaction. A step
 * nested in an EIP inside the transaction (here a {@code filter}) has the error handler of the route, which asks the
 * hook and redelivers the step inside the transaction. In both cases a failure from the hook takes the same path as a
 * failure of the processor. The {@code transacted} step itself is asked by its channel, before the transaction begins.
 */
class StepHookJtaTest {

    private CamelContext camelContext;
    private final AtomicInteger commits = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();
    // per node id: how many times the hook was asked, how many times the processor was called
    private final Map<String, AtomicInteger> asked = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    // per node id, how many more times the hook or the processor fails the step
    private final Map<String, AtomicInteger> hookFailuresLeft = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> processorFailuresLeft = new ConcurrentHashMap<>();

    enum Failure {
        HOOK,
        PROCESSOR
    }

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.getRegistry().bind("recordingPolicy", new JtaTransactionPolicy() {
            @Override
            public void run(Runnable runnable) throws Throwable {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    rollbacks.incrementAndGet();
                    throw t;
                }
                commits.incrementAndGet();
            }
        });
        camelContext.getCamelContextExtension().setStepHook(new FailingHook());
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);

                from("direct:start").routeId("tx-route")
                        .transacted("recordingPolicy").id("tx")
                        .process(new FailingProcessor("work")).id("work")
                        .filter(constant(true)).id("filter")
                            .process(new FailingProcessor("nested")).id("nested")
                        .end()
                        .setBody(constant("done")).id("reply");
            }
        });
        camelContext.start();
    }

    @AfterEach
    void tearDown() {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void failureOfAStepRollsBackAndTheTransactionIsRedelivered(Failure failure) {
        fail(failure, "work", 1);

        Exchange result = send();

        assertNull(result.getException());
        assertEquals("done", result.getMessage().getBody());
        assertEquals(2, count(asked, "work"), "The redelivery of the transaction asks the hook again");
        assertEquals(failure == Failure.HOOK ? 1 : 2, count(calls, "work"));
        assertEquals(1, count(asked, "nested"));
        assertEquals(1, result.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class));
        assertEquals(1, rollbacks.get(), "The failed attempt rolls its transaction back");
        assertEquals(1, commits.get());
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void failureOfEveryAttemptRollsEachTransactionBack(Failure failure) {
        fail(failure, "work", Integer.MAX_VALUE);

        Exchange result = send();

        assertInstanceOf(IllegalStateException.class, result.getException());
        assertEquals(failure + " failed work", result.getException().getMessage());
        assertEquals(3, count(asked, "work"), "The first attempt and two redeliveries ask the hook");
        assertEquals(failure == Failure.HOOK ? 0 : 3, count(calls, "work"));
        assertEquals(0, count(asked, "nested"));
        assertEquals(0, commits.get());
        assertEquals(3, rollbacks.get());
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void failureOfANestedStepIsRedeliveredInsideTheTransaction(Failure failure) {
        fail(failure, "nested", 1);

        Exchange result = send();

        assertNull(result.getException());
        assertEquals("done", result.getMessage().getBody());
        assertEquals(2, count(asked, "nested"), "The error handler of the nested step asks the hook again");
        assertEquals(failure == Failure.HOOK ? 1 : 2, count(calls, "nested"));
        assertEquals(1, count(asked, "work"), "The transaction is not redelivered");
        assertEquals(1, result.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class));
        assertEquals(0, rollbacks.get());
        assertEquals(1, commits.get());
    }

    @Test
    void failureFromTheHookAtTheTransactedStepFailsBeforeTheTransaction() {
        fail(Failure.HOOK, "tx", Integer.MAX_VALUE);

        Exchange result = send();

        assertInstanceOf(IllegalStateException.class, result.getException());
        assertEquals(1, count(asked, "tx"), "Asked by the channel of the transacted step, which does not redeliver");
        assertEquals(0, count(asked, "work"));
        assertEquals(0, count(calls, "work"));
        assertEquals(0, commits.get(), "The transaction does not begin");
        assertEquals(0, rollbacks.get(), "The transaction does not begin");
    }

    private void fail(Failure failure, String nodeId, int times) {
        (failure == Failure.HOOK ? hookFailuresLeft : processorFailuresLeft).put(nodeId, new AtomicInteger(times));
    }

    private Exchange send() {
        return camelContext.createProducerTemplate().send("direct:start", e -> e.getIn().setBody("Hello"));
    }

    private static int count(Map<String, AtomicInteger> counts, String nodeId) {
        AtomicInteger count = counts.get(nodeId);
        return count != null ? count.get() : 0;
    }

    private static boolean failsNow(Map<String, AtomicInteger> failuresLeft, String nodeId) {
        AtomicInteger left = failuresLeft.get(nodeId);
        return left != null && left.getAndDecrement() > 0;
    }

    private final class FailingProcessor implements Processor {
        private final String nodeId;

        FailingProcessor(String nodeId) {
            this.nodeId = nodeId;
        }

        @Override
        public void process(Exchange exchange) {
            calls.computeIfAbsent(nodeId, k -> new AtomicInteger()).incrementAndGet();
            if (failsNow(processorFailuresLeft, nodeId)) {
                throw new IllegalStateException(Failure.PROCESSOR + " failed " + nodeId);
            }
        }
    }

    private final class FailingHook implements StepHook {
        @Override
        public long beforeStep(Exchange exchange, String nodeId) {
            asked.computeIfAbsent(nodeId, k -> new AtomicInteger()).incrementAndGet();
            if (failsNow(hookFailuresLeft, nodeId)) {
                exchange.setException(new IllegalStateException(Failure.HOOK + " failed " + nodeId));
            }
            return PROCEED;
        }
    }
}

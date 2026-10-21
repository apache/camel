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
package org.apache.camel.component.resilience4j;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timeout fires while the task is copying its result into the exchange (it has already won the write guard). The
 * caller must not change the exchange at the same time (fallback or timeout exception): it waits until the copy is
 * done, then sets the timeout result or runs the fallback on the exchange, which then holds the result of the task (as
 * when the timeout fires just after the copy). The copy does not change the exchange after the caller took it over.
 * Sync and async ({@code asynchronous=true}) mode.
 * <p>
 * The task holds its copy at a gate inside {@code ExchangeHelper.copyResults} (the first read of the body of its
 * result). The gate is opened when the caller has returned (it did not wait for the copy), or when the caller has not
 * returned 2 seconds after the timeout (it waits for the copy).
 */
class ResilienceTimeoutDuringWriteBackTest extends CamelTestSupport {

    private static final int TIMEOUT = 500;

    private final Gate gate = new Gate();
    private final CountDownLatch taskDone = new CountDownLatch(1);

    @Test
    void testFallbackIsNotOverwrittenByWriteBackInProgress() throws Exception {
        assertFallbackResult(sendWhileTaskHoldsWriteBack("direct:fallback"));
    }

    @Test
    void testTimeoutExceptionIsNotClearedByWriteBackInProgress() throws Exception {
        assertTimeoutResult(sendWhileTaskHoldsWriteBack("direct:nofallback"));
    }

    @Test
    void testAsyncFallbackIsNotOverwrittenByWriteBackInProgress() throws Exception {
        assertFallbackResult(sendWhileTaskHoldsWriteBack("direct:asyncFallback"));
    }

    @Test
    void testAsyncTimeoutExceptionIsNotClearedByWriteBackInProgress() throws Exception {
        assertTimeoutResult(sendWhileTaskHoldsWriteBack("direct:asyncNofallback"));
    }

    private static void assertFallbackResult(Exchange exchange) {
        assertEquals("Fallback result", exchange.getMessage().getBody(String.class));
        // the fallback ran after the write-back of the task, on the exchange that holds the result of the task
        assertEquals("task", exchange.getMessage().getHeader("Written-By"));
        assertEquals(Boolean.TRUE, exchange.getProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK));
        assertEquals(Boolean.FALSE,
                exchange.getProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION));
    }

    private static void assertTimeoutResult(Exchange exchange) {
        assertInstanceOf(TimeoutException.class, exchange.getException());
        // the timeout was set after the write-back of the task, on the exchange that holds the result of the task
        assertEquals("task", exchange.getMessage().getHeader("Written-By"));
        assertEquals(Boolean.TRUE, exchange.getProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_TIMED_OUT));
        assertEquals(Boolean.FALSE,
                exchange.getProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION));
    }

    private Exchange sendWhileTaskHoldsWriteBack(String uri) throws Exception {
        Future<Exchange> reply = template.asyncSend(uri, e -> e.getIn().setBody("Hello"));

        assertTrue(gate.entered.await(10, TimeUnit.SECONDS), "the task should reach the write-back");
        StackTraceElement[] where = gate.where.get();
        assertTrue(Arrays.stream(where).anyMatch(f -> f.getMethodName().equals("doCopyResults")),
                "the gate should hold the task inside copyResults, not at " + Arrays.toString(where));

        // the caller times out while the task holds the gate: either it returns without waiting for the task, or it
        // waits for the task, then it has not returned 2 seconds after the timeout
        try {
            reply.get(TIMEOUT + 2000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // the caller waits for the write-back of the task
        }
        gate.open.countDown();

        Exchange exchange = reply.get(10, TimeUnit.SECONDS);
        assertNotNull(exchange);
        assertTrue(taskDone.await(10, TimeUnit.SECONDS), "the task should be done");
        return exchange;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fallback")
                        .circuitBreaker()
                            .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(TIMEOUT).end()
                            .process(e -> holdWriteBack(e))
                        .onFallback()
                            .setBody(constant("Fallback result"))
                        .end();

                from("direct:nofallback")
                        .circuitBreaker()
                            .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(TIMEOUT).end()
                            .process(e -> holdWriteBack(e))
                        .end();

                from("direct:asyncFallback")
                        .circuitBreaker()
                            .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration(TIMEOUT)
                            .end()
                            .process(e -> holdWriteBack(e))
                        .onFallback()
                            .setBody(constant("Fallback result"))
                        .end();

                from("direct:asyncNofallback")
                        .circuitBreaker()
                            .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration(TIMEOUT)
                            .end()
                            .process(e -> holdWriteBack(e))
                        .end();
            }
        };
    }

    private void holdWriteBack(Exchange copy) {
        GatedMessage result = new GatedMessage(copy, gate);
        result.setBody("Task result");
        result.setHeader("Written-By", "task");
        copy.setIn(result);
        // done when the task has finished with its copy of the exchange
        copy.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                taskDone.countDown();
            }
        });
        result.armed = true;
    }

    private static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch open = new CountDownLatch(1);
        final AtomicReference<StackTraceElement[]> where = new AtomicReference<>();

        void pass() {
            where.set(Thread.currentThread().getStackTrace());
            entered.countDown();
            // keep waiting if the task thread is interrupted, bounded, and keep the interrupt status
            boolean interrupted = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            try {
                while (true) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        return;
                    }
                    try {
                        if (open.await(left, TimeUnit.NANOSECONDS)) {
                            return;
                        }
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * The result message of the task: the first read of its body by {@code ExchangeHelper.copyResults} after the task
     * is done holds at the gate (other reads, such as the stream caching advice, pass).
     */
    private static final class GatedMessage extends DefaultMessage {
        private final Gate gate;
        volatile boolean armed;

        GatedMessage(Exchange exchange, Gate gate) {
            super(exchange);
            this.gate = gate;
        }

        @Override
        public Object getBody() {
            if (armed && calledByCopyResults()) {
                armed = false;
                gate.pass();
            }
            return super.getBody();
        }

        private static boolean calledByCopyResults() {
            return StackWalker.getInstance()
                    .walk(frames -> frames.anyMatch(f -> f.getClassName().equals(ExchangeHelper.class.getName())
                            && f.getMethodName().equals("doCopyResults")));
        }

        @Override
        public DefaultMessage newInstance() {
            return new DefaultMessage(getExchange());
        }
    }
}

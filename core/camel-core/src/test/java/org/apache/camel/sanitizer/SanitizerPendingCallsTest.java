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
package org.apache.camel.sanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table of the pending calls of the still pending warning of R1, on a clock the test moves: only the innermost
 * pending call of an exchange is reported, once, when nothing happened in the exchange (or in its parts) for the
 * timeout; a call that threw stays pending until the exchange is done; and the bound of the table.
 */
class SanitizerPendingCallsTest {

    private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(60);

    private final CamelContext context = new DefaultCamelContext();
    private final SanitizerTestSession session = SanitizerTestSession.open("pending");
    private final AtomicLong clock = new AtomicLong();
    // the split steps wait for their parts
    private final PendingCalls pending = new PendingCalls(TIMEOUT, clock::get, (route, step) -> step.startsWith("split"));
    private final Sanitizer sanitizer = new Sanitizer(context, 0, session, pending);
    private final SanitizerStepListener listener = new SanitizerStepListener(sanitizer);

    @AfterEach
    void close() {
        session.close();
    }

    @Test
    void onlyTheInnermostCallIsReportedOnceAfterTheTimeout() {
        Exchange exchange = new DefaultExchange(context);
        listener.onStepCall(exchange, "outer", "toInner");
        Object inner = listener.onStepCall(exchange, "inner", "lost");
        listener.onStepReturn(inner, false);

        clock.addAndGet(TIMEOUT - 1);
        assertTrue(pending.stalled().isEmpty());
        clock.addAndGet(1);
        List<PendingCalls.Pending> stalled = pending.stalled();
        assertEquals(1, stalled.size());
        assertEquals("lost", stalled.get(0).stepId);
        assertEquals(Boolean.FALSE, stalled.get(0).returned);
        assertEquals(List.of("route outer step toInner"), stalled.get(0).enclosing);
        // reported once
        clock.addAndGet(TIMEOUT);
        assertTrue(pending.stalled().isEmpty());
        // nothing is charged to the test: a warning, not a violation
        assertTrue(session.drain().isEmpty());
    }

    @Test
    void theTimeIsCountedFromTheLastCallOrCallbackOfTheExchange() {
        Exchange exchange = new DefaultExchange(context);
        listener.onStepCall(exchange, "r", "loop1");
        for (int i = 0; i < 5; i++) {
            clock.addAndGet(TIMEOUT / 2);
            Object step = listener.onStepCall(exchange, "r", "to" + i);
            clock.addAndGet(TIMEOUT / 2);
            listener.onStepCallback(step, false);
            assertTrue(pending.stalled().isEmpty());
        }
        clock.addAndGet(TIMEOUT);
        List<PendingCalls.Pending> stalled = pending.stalled();
        assertEquals(1, stalled.size());
        assertEquals("loop1", stalled.get(0).stepId);
    }

    @Test
    void aSplitThatWaitsForItsPartsIsNotReported() {
        Exchange exchange = new DefaultExchange(context);
        listener.onStepCall(exchange, "r", "split1");
        // the parts take three timeouts in all, each a step that completes
        for (int i = 0; i < 6; i++) {
            Exchange part = part(exchange);
            clock.addAndGet(TIMEOUT / 2);
            Object step = listener.onStepCall(part, "r", "toPart");
            clock.addAndGet(TIMEOUT / 2);
            assertTrue(pending.stalled().isEmpty());
            listener.onStepCallback(step, false);
        }
        // a part whose step does not call back: the step of the part is reported, not the split that waits for it
        Exchange stuck = part(exchange);
        Object step = listener.onStepCall(stuck, "r", "toPart");
        clock.addAndGet(TIMEOUT);
        List<PendingCalls.Pending> stalled = pending.stalled();
        assertEquals(1, stalled.size());
        assertEquals("toPart", stalled.get(0).stepId);
        assertEquals(stuck.getExchangeId(), stalled.get(0).exchangeId);
        // when the parts are done and the split does not call back, the split is reported
        listener.onStepCallback(step, false);
        clock.addAndGet(TIMEOUT);
        stalled = pending.stalled();
        assertEquals(1, stalled.size());
        assertEquals("split1", stalled.get(0).stepId);
        assertEquals(exchange.getExchangeId(), stalled.get(0).exchangeId);
    }

    @Test
    void aCallThatThrewIsReportedAsThrewUntilTheExchangeIsDone() {
        Exchange hung = new DefaultExchange(context);
        Object call = listener.onStepCall(hung, "r", "process1");
        listener.onStepThrow(call, new IllegalStateException("Forced"));
        Exchange handled = new DefaultExchange(context);
        Object other = listener.onStepCall(handled, "r", "process1");
        listener.onStepThrow(other, new IllegalStateException("Forced"));
        // Camel caught the throw and the exchange is done (its unit of work)
        sanitizer.exchangeDone(handled.getExchangeId());

        clock.addAndGet(TIMEOUT);
        List<PendingCalls.Pending> stalled = pending.stalled();
        assertEquals(1, stalled.size());
        assertEquals(hung.getExchangeId(), stalled.get(0).exchangeId);
        assertEquals("java.lang.IllegalStateException: Forced", stalled.get(0).threw);
        assertEquals(1, pending.size());
    }

    @Test
    void anExchangeThatIsDoneTakesItsPartsWithIt() {
        Exchange exchange = new DefaultExchange(context);
        listener.onStepCall(exchange, "r", "split1");
        Exchange part = part(exchange);
        listener.onStepCall(part, "r", "toPart");
        assertEquals(2, pending.size());
        sanitizer.exchangeDone(exchange.getExchangeId());
        assertEquals(0, pending.size());
        clock.addAndGet(TIMEOUT);
        assertTrue(pending.stalled().isEmpty());
    }

    @Test
    void aCallbackAfterTheWarningIsNoLongerPending() {
        Exchange exchange = new DefaultExchange(context);
        SanitizerStepListener.StepCall call
                = (SanitizerStepListener.StepCall) listener.onStepCall(exchange, "r", "slow");
        clock.addAndGet(TIMEOUT);
        assertEquals(1, pending.stalled().size());
        clock.addAndGet(5);
        assertEquals(TIMEOUT + 5, pending.callback(call));
        assertEquals(0, pending.size());
        // a second callback is R1's completed twice, and changes nothing here
        assertEquals(-1, pending.callback(call));
    }

    @Test
    void theOldestCallIsDroppedAtTheBoundAndCounted() {
        for (int i = 0; i < PendingCalls.MAX_PENDING + 3; i++) {
            listener.onStepCall(new DefaultExchange(context), "r", "lost");
        }
        assertEquals(PendingCalls.MAX_PENDING, pending.size());
        assertEquals(3, pending.getDropped());
        assertTrue(sanitizer.report().contains("checks incomplete: 3 pending calls dropped"), sanitizer.report());
    }

    @Test
    void theCallsStillPendingAreTakenWhenTheContextStops() {
        Exchange exchange = new DefaultExchange(context);
        listener.onStepCall(exchange, "outer", "toInner");
        listener.onStepCall(exchange, "inner", "lost");
        List<PendingCalls.Pending> all = pending.takeAll();
        assertEquals(2, all.size());
        assertEquals("lost", all.get(0).stepId);
        assertEquals("toInner", all.get(1).stepId);
        assertEquals(0, pending.size());
        clock.addAndGet(TIMEOUT);
        assertTrue(pending.stalled().isEmpty());
    }

    @Test
    void anExchangeThatNothingRefersToAnyMoreIsStillReported() {
        // a processor that drops the callback leaves nothing that refers to the exchange: the table keeps its calls
        // (not the exchange), so the warning still comes
        listener.onStepCall(new DefaultExchange(context), "r", "lost");
        System.gc();
        clock.addAndGet(TIMEOUT);
        List<PendingCalls.Pending> stalled = pending.stalled();
        assertEquals(1, stalled.size());
        assertEquals("lost", stalled.get(0).stepId);
    }

    @Test
    void theWarningNamesTheStepAndHowItWaits() {
        Sanitizer withStacks = new Sanitizer(context, 32, session, new PendingCalls(TIMEOUT, clock::get, (r, s) -> false));
        SanitizerStepListener l = new SanitizerStepListener(withStacks);
        Exchange exchange = new DefaultExchange(context);
        Object call = l.onStepCall(exchange, "r", "lost");
        l.onStepReturn(call, false);
        clock.addAndGet(TIMEOUT);
        withStacks.checkPending();
        assertEquals(1, withStacks.getStillPendingCount());
        String text = withStacks.getStillPendingReports().get(0);
        assertTrue(text.startsWith("Exchange sanitizer: callback still pending (R1, a warning)\n"), text);
        assertTrue(text.contains(", route r, step lost, processor (not found)\n"), text);
        assertTrue(text.contains("process returned false, and the callback has not been called"), text);
        assertTrue(text.contains("This is a warning, not a violation"), text);
        assertTrue(withStacks.report().contains("1 call reported still pending (warnings), no violations"),
                withStacks.report());
        assertTrue(session.drain().isEmpty());
    }

    @Test
    void aCheckThatFailsWithAnErrorDoesNotStopTheLaterChecks() {
        // an Error thrown out of a task of scheduleWithFixedDelay would cancel its later runs
        AtomicLong failures = new AtomicLong(2);
        PendingCalls failing = new PendingCalls(TIMEOUT, () -> {
            if (failures.getAndDecrement() > 0) {
                throw new AssertionError("Forced");
            }
            return clock.get();
        }, (route, step) -> false);
        Sanitizer s = new Sanitizer(context, 0, session, failing);
        s.checkPending();
        s.checkPending();
        assertEquals(0, s.getStillPendingCount());

        Object call = new SanitizerStepListener(s).onStepCall(new DefaultExchange(context), "r", "lost");
        assertNotNull(call);
        clock.addAndGet(TIMEOUT);
        s.checkPending();
        assertEquals(1, s.getStillPendingCount());
    }

    @Test
    void callsCallbacksAndDoneEventsOnManyThreadsLeaveNothingPending() throws Exception {
        // the events of the calls come on many threads at once: a part's callback on one thread while its exchange is
        // done on another; at the end nothing is pending and the counts agree
        int threads = 8;
        int rounds = 1000;
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Thread> workers = new ArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        for (int t = 0; t < threads; t++) {
            int seed = t;
            workers.add(new Thread(() -> {
                await(start);
                for (int i = 0; i < rounds; i++) {
                    Exchange exchange = new DefaultExchange(context);
                    Object outer = listener.onStepCall(exchange, "r", "split1");
                    Exchange part = part(exchange);
                    Object inner = listener.onStepCall(part, "r", "toPart");
                    Thread other = new Thread(() -> listener.onStepCallback(inner, false));
                    other.setUncaughtExceptionHandler((th, e) -> errors.add(e));
                    other.start();
                    if ((i + seed) % 3 == 0) {
                        // done while the callback of the part may still come
                        sanitizer.exchangeDone(exchange.getExchangeId());
                    } else {
                        join(other);
                        listener.onStepCallback(outer, false);
                    }
                    join(other);
                }
            }));
        }
        workers.forEach(w -> w.setUncaughtExceptionHandler((t, e) -> errors.add(e)));
        workers.forEach(Thread::start);
        for (Thread w : workers) {
            w.join(TimeUnit.SECONDS.toMillis(60));
        }
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(0, pending.size());
        clock.addAndGet(TIMEOUT);
        assertTrue(pending.stalled().isEmpty());
        assertEquals(0, pending.takeAll().size());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void aCorrelatedCopyThatIsNotAPartIsAnExchangeOfItsOwn() {
        // such as the copy that a seda producer queues: it carries the correlation id, but the innermost pending call of
        // the exchange it points to is not a step that waits for its copies
        Exchange exchange = new DefaultExchange(context);
        listener.onStepCall(exchange, "r", "lost");
        Exchange copy = exchange.copy();
        copy.setProperty(ExchangePropertyKey.CORRELATION_ID, exchange.getExchangeId());
        for (int i = 0; i < 3; i++) {
            clock.addAndGet(TIMEOUT / 2);
            Object step = listener.onStepCall(copy, "audit", "toAudit");
            listener.onStepCallback(step, false);
        }
        // the copy was busy all the time, and the exchange is reported
        List<PendingCalls.Pending> stalled = pending.stalled();
        assertEquals(1, stalled.size());
        assertEquals("lost", stalled.get(0).stepId);
    }

    @Test
    void aPartKeepsItsExchangeWhenItsCorrelationIdChanges() {
        Exchange exchange = new DefaultExchange(context);
        listener.onStepCall(exchange, "r", "split1");
        Exchange part = part(exchange);
        Object first = listener.onStepCall(part, "r", "toSeda");
        clock.addAndGet(TIMEOUT / 2);
        listener.onStepCallback(first, false);
        // the results of a seda request copied onto the part change its correlation id
        part.setProperty(ExchangePropertyKey.CORRELATION_ID, part.getExchangeId());
        clock.addAndGet(TIMEOUT / 2);
        Object second = listener.onStepCall(part, "r", "toNext");
        clock.addAndGet(TIMEOUT / 2);
        listener.onStepCallback(second, false);
        clock.addAndGet(TIMEOUT / 2);
        // the part was busy until half a timeout ago: the split is not reported yet
        assertTrue(pending.stalled().isEmpty());
        clock.addAndGet(TIMEOUT / 2);
        assertEquals("split1", pending.stalled().get(0).stepId);
    }

    private static Exchange part(Exchange exchange) {
        Exchange part = exchange.copy();
        part.setProperty(ExchangePropertyKey.CORRELATION_ID, exchange.getExchangeId());
        return part;
    }
}

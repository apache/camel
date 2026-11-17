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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The watch windows of rule R3 on their own: a thread opens a window at a point of an exchange and pauses in it, and
 * the test thread comes to a point of the same exchange while it is open (the test waits until it is), or does not. The
 * window is long, and the test ends it with an interrupt after its point, so the test does not depend on timing.
 */
class SanitizerOverlapsTest {

    // the window stays open until the test interrupts its thread
    private static final long WINDOW = TimeUnit.SECONDS.toNanos(30);
    // the identity hash of the exchange of the points
    private static final int EXCHANGE = 42;

    private final CamelContext context = new DefaultCamelContext();
    private final SanitizerTestSession session = SanitizerTestSession.open("overlaps");

    @AfterEach
    void close() {
        session.close();
    }

    @Test
    void aPointOfAnotherThreadDuringTheWindowIsReportedWithBothThreads() throws Exception {
        Overlaps overlaps = overlaps(1);
        Thread watcher = watch(overlaps, "ex-1", Overlaps.STEP_CALLBACK, "route1", "to1");
        overlaps.point("ex-1", EXCHANGE, Overlaps.STEP_CALL, "route2", "process2");
        close(watcher);

        SanitizerViolation v = only();
        assertEquals("ex-1", v.getExchangeId());
        // the step of the thread that watched
        assertEquals("route1", v.getRouteId());
        assertEquals("to1", v.getStepId());
        String text = v.getText();
        assertTrue(text.startsWith("Exchange sanitizer: exchange used by two threads at the same time (R3)\n"
                                   + "  exchange ex-1, route route1, step to1, processor (not found)\n"),
                text);
        assertTrue(text.contains("  watched while the callback of step to1 of route route1 continues the exchange: thread"
                                 + " watcher\n"),
                text);
        assertTrue(text.contains("  meanwhile step process2 of route route2 is called: thread "
                                 + Thread.currentThread().getName() + "\n"),
                text);
        // the stack of each thread, from where it came to its point
        assertTrue(text.contains("at " + SanitizerOverlapsTest.class.getName() + ".lambda$watch$"), text);
        assertTrue(text.contains("at " + SanitizerOverlapsTest.class.getName()
                                 + ".aPointOfAnotherThreadDuringTheWindowIsReportedWithBothThreads"),
                text);
        // the thread that came to the point while another watched opened no window of its own
        assertEquals(1, overlaps.getWindowCount());
    }

    @Test
    void onCompletionsAreAtNoStep() throws Exception {
        Overlaps overlaps = overlaps(1);
        Thread watcher = watch(overlaps, "ex-1", Overlaps.ON_COMPLETION, "route1", "com.acme.Commit");
        overlaps.point("ex-1", EXCHANGE, Overlaps.ON_COMPLETION, "route1", "com.acme.Audit");
        close(watcher);

        SanitizerViolation v = only();
        assertNull(v.getStepId());
        assertNull(v.getProcessor());
        assertTrue(v.getText().contains("  exchange ex-1, route route1\n"), v.getText());
        assertTrue(v.getText().contains("  watched while on completion com.acme.Commit runs: thread watcher\n"),
                v.getText());
        assertTrue(v.getText().contains("  meanwhile on completion com.acme.Audit runs: thread "), v.getText());
    }

    @Test
    void anOnCompletionWatchedNamesTheStepOfTheOtherThread() throws Exception {
        Overlaps overlaps = overlaps(1);
        Thread watcher = watch(overlaps, "ex-1", Overlaps.ON_COMPLETION, "route1", "com.acme.Commit");
        overlaps.point("ex-1", EXCHANGE, Overlaps.STEP_CALLBACK, "route2", "to2");
        close(watcher);

        SanitizerViolation v = only();
        assertEquals("route2", v.getRouteId());
        assertEquals("to2", v.getStepId());
    }

    @Test
    void otherExchangesAndTheSameThreadAreNotReported() throws Exception {
        Overlaps overlaps = overlaps(1);
        Thread watcher = watch(overlaps, "ex-1", Overlaps.STEP_CALL, "route1", "to1");
        // another exchange, while the window on ex-1 is open: it opens its own window on this thread, which the
        // interrupt ends at once
        Thread.currentThread().interrupt();
        overlaps.point("ex-2", EXCHANGE, Overlaps.STEP_CALL, "route1", "to1");
        assertTrue(Thread.interrupted(), "the thread stays interrupted");
        close(watcher);
        // one thread that comes to point after point of the same exchange
        Overlaps quick = overlaps(1, TimeUnit.MILLISECONDS.toNanos(1));
        for (int i = 0; i < 10; i++) {
            quick.point("ex-3", EXCHANGE, i % 2 == 0 ? Overlaps.STEP_CALL : Overlaps.STEP_CALLBACK, "route1", "to" + i);
        }
        assertEquals(List.of(), session.drain());
        assertEquals(2, overlaps.getWindowCount());
        assertEquals(10, quick.getWindowCount());
    }

    @Test
    void aCopyWithTheSameIdIsNotReported() throws Exception {
        Overlaps overlaps = overlaps(1);
        Thread watcher = watch(overlaps, "ex-1", Overlaps.STEP_CALL, "route1", "to1");
        // another exchange object with the same id, such as one that a transport in the same JVM rebuilt with the id of
        // the sender: it is not the exchange that is watched, and it opens no window, as one is open on the id
        overlaps.point("ex-1", EXCHANGE + 1, Overlaps.STEP_CALL, "route2", "from2");
        close(watcher);
        assertEquals(List.of(), session.drain());
        assertEquals(1, overlaps.getWindowCount());
    }

    @Test
    void aWindowIsOpenedAtEveryNthPoint() {
        Overlaps overlaps = overlaps(3, TimeUnit.MILLISECONDS.toNanos(1));
        for (int i = 0; i < 9; i++) {
            overlaps.point("ex-" + i, EXCHANGE, Overlaps.STEP_CALL, "route1", "to1");
        }
        assertEquals(9, overlaps.getPointCount());
        assertEquals(3, overlaps.getWindowCount());
    }

    @Test
    void anExchangeWithAViolationAlreadyIsNotReportedAgain() throws Exception {
        Sanitizer sanitizer = new Sanitizer(context, 32, session, true, false, true, 0, 1, WINDOW);
        Overlaps overlaps = sanitizer.getOverlaps();
        // such as a callback called twice: the two threads that continue the exchange overlap because of it
        sanitizer.record(Violation.COMPLETED_TWICE, "ex-1", "route1", "to1", "");
        Thread watcher = watch(overlaps, "ex-1", Overlaps.STEP_CALL, "route1", "to2");
        overlaps.point("ex-1", EXCHANGE, Overlaps.STEP_CALL, "route1", "to2");
        close(watcher);

        List<SanitizerViolation> charged = session.drain();
        assertEquals(1, charged.size(), charged::toString);
        assertEquals(Violation.COMPLETED_TWICE, charged.get(0).getKind());
    }

    @Test
    void anExchangeIsReportedOnce() throws Exception {
        Overlaps overlaps = overlaps(1);
        for (int i = 0; i < 2; i++) {
            Thread watcher = watch(overlaps, "ex-1", Overlaps.STEP_CALL, "route1", "to" + i);
            overlaps.point("ex-1", EXCHANGE, Overlaps.STEP_CALL, "route1", "process" + i);
            close(watcher);
        }
        only();
        assertEquals(2, overlaps.getWindowCount());
    }

    @Test
    void anInterruptEndsTheWindow() throws Exception {
        Overlaps overlaps = overlaps(1, TimeUnit.SECONDS.toNanos(30));
        long start = System.nanoTime();
        Thread watcher = watch(overlaps, "ex-1", Overlaps.STEP_CALL, "route1", "to1");
        close(watcher);
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(10), "the window ended with the interrupt");
        assertEquals(List.of(), session.drain());
    }

    private Overlaps overlaps(int sampling) {
        return overlaps(sampling, WINDOW);
    }

    private Overlaps overlaps(int sampling, long windowNanos) {
        return new Sanitizer(context, 32, session, false, false, true, 0, sampling, windowNanos).getOverlaps();
    }

    /**
     * Starts a thread that comes to a point of the exchange and opens a window there, and waits until it is open.
     */
    private static Thread watch(Overlaps overlaps, String exchangeId, byte kind, String routeId, String name) {
        Thread t = new Thread(() -> overlaps.point(exchangeId, EXCHANGE, kind, routeId, name), "watcher");
        t.start();
        await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(1))
                .until(() -> overlaps.isWatching(exchangeId));
        return t;
    }

    /**
     * Ends the window of the thread (with an interrupt) and waits until the thread is done.
     */
    private static void close(Thread watcher) throws InterruptedException {
        watcher.interrupt();
        watcher.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(watcher.isAlive(), "the window ended");
    }

    private SanitizerViolation only() {
        List<SanitizerViolation> charged = session.drain();
        assertEquals(1, charged.size(), charged::toString);
        assertEquals(Violation.USED_BY_TWO_THREADS, charged.get(0).getKind());
        return charged.get(0);
    }
}

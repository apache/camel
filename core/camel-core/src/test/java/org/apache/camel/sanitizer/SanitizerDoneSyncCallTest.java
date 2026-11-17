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

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record of a call of rule R3 in each order of its return and its first callback, on the calling thread and on
 * another one, and under threads that race. The call is created on the thread of the test, which is then the thread
 * that called process.
 */
class SanitizerDoneSyncCallTest {

    private final CamelContext context = new DefaultCamelContext();
    private final SanitizerTestSession session = SanitizerTestSession.open("doneSync");
    private final Sanitizer sanitizer = new Sanitizer(context, 32, session, false, false, true, 0);

    @AfterEach
    void close() {
        session.close();
    }

    @Test
    void theSameValueIsNotReported() throws Exception {
        // synchronous: the callback with true inside process, then true
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        call.callback(true);
        call.returned(true);
        // asynchronous: false, and the callback with false on another thread, before or after the return
        call = newCall();
        call.returned(false);
        onAnotherThread(call, false);
        call = newCall();
        onAnotherThread(call, false);
        call.returned(false);
        // asynchronous but quick: the callback with false on the calling thread before the return
        call = newCall();
        call.callback(false);
        call.returned(false);
        assertNoViolations();
    }

    @Test
    void theReactiveExecutorOnTheCallingThreadIsNotReported() {
        // a processor that hands its callback to the reactive executor returns the value of the processor it calls (true),
        // and the reactive executor calls the callback with false on the same thread, at once or after the return
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        call.callback(false);
        call.returned(true);
        call = newCall();
        call.returned(true);
        call.callback(false);
        assertNoViolations();
    }

    @Test
    void returnedTrueWhileTheCallbackCameWithFalseOnAnotherThread() throws Exception {
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        onAnotherThread(call, false);
        call.returned(true);
        SanitizerViolation v = only(Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD);
        assertTrue(v.getText().contains("the callback was called with false: thread other"), v.getText());
        // the stack of the other thread was kept from when the callback came
        assertTrue(v.getText().contains("at " + SanitizerDoneSyncCallTest.class.getName() + ".lambda$onAnotherThread$"),
                v.getText());
        assertTrue(v.getText().contains("then process returned true: thread " + Thread.currentThread().getName()),
                v.getText());
    }

    @Test
    void returnedTrueWhileTheCallbackCameWithTrueOnAnotherThread() throws Exception {
        // the values match, but true means the callback was called on the calling thread: here another thread
        // continued the exchange while process still ran (such as a processor that waits for its worker)
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        onAnotherThread(call, true);
        call.returned(true);
        SanitizerViolation v = only(Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD);
        assertTrue(v.getText().contains("the callback was called with true: thread other"), v.getText());
    }

    @Test
    void returnedTrueBeforeTheCallbackOnAnotherThread() throws Exception {
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        call.returned(true);
        onAnotherThread(call, false);
        SanitizerViolation v = only(Violation.RETURNED_TRUE_BEFORE_CALLBACK);
        assertTrue(v.getText().contains("  process returned true\n"), v.getText());
        assertTrue(v.getText().contains("then the callback was called with false: thread other"), v.getText());
    }

    @Test
    void returnedTrueBeforeTheCallbackWithTrue() {
        // the reactive executor calls the callback with false only: a later callback with true is not it
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        call.returned(true);
        call.callback(true);
        SanitizerViolation v = only(Violation.RETURNED_TRUE_BEFORE_CALLBACK);
        assertTrue(v.getText().contains("then the callback was called with true, on the thread that called process"),
                v.getText());
    }

    @Test
    void returnedFalseWhileTheCallbackCameWithTrue() throws Exception {
        // in each order, and on either thread
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        call.returned(false);
        onAnotherThread(call, true);
        call = newCall();
        onAnotherThread(call, true);
        call.returned(false);
        call = newCall();
        call.callback(true);
        call.returned(false);
        call = newCall();
        call.returned(false);
        call.callback(true);
        assertEquals(4, sanitizer.getViolationCount(Violation.RETURNED_FALSE_CALLBACK_TRUE));
        assertEquals(4, sanitizer.getViolations().size());
        assertEquals(4, session.drain().size());
    }

    @Test
    void onlyTheFirstCallbackIsCompared() throws Exception {
        // a second callback is rule R1; here with the other value on another thread
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        call.callback(true);
        call.returned(true);
        onAnotherThread(call, false);
        call = newCall();
        call.returned(false);
        onAnotherThread(call, false);
        onAnotherThread(call, true);
        assertNoViolations();
    }

    @Test
    void aCallThatThrewIsNotCompared() throws Exception {
        // rule R1 reports what process threw and a callback after it
        SanitizerDoneSyncListener.DoneSyncCall call = newCall();
        call.threw();
        onAnotherThread(call, true);
        call = newCall();
        onAnotherThread(call, false);
        call.threw();
        assertNoViolations();
    }

    @Test
    void aReturnAndACallbackThatRaceAreComparedOnce() throws Exception {
        // no stacks, so the two threads meet as often as they can
        Sanitizer racing = new Sanitizer(context, 0, session, false, false, true, 0);
        int rounds = 2000;
        for (int round = 0; round < rounds; round++) {
            // returned true, callback on another thread: a violation in either order and with either value, of one
            // kind or the other
            race(racing, true, false);
            race(racing, true, true);
            // returned false, callback with true on another thread: a violation in either order
            race(racing, false, true);
            // returned false, callback with false on another thread: never
            race(racing, false, false);
        }
        long reported = racing.getViolationCount(Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD)
                        + racing.getViolationCount(Violation.RETURNED_TRUE_BEFORE_CALLBACK);
        assertEquals(2L * rounds, reported);
        assertEquals(rounds, racing.getViolationCount(Violation.RETURNED_FALSE_CALLBACK_TRUE));
        // all charged, the first 1000 kept
        assertEquals(Sanitizer.MAX_KEPT, racing.getViolations().size());
        assertEquals(Sanitizer.MAX_KEPT, session.drain().size());
    }

    private static void race(Sanitizer racing, boolean returned, boolean callback) throws Exception {
        SanitizerDoneSyncListener.DoneSyncCall call = new SanitizerDoneSyncListener.DoneSyncCall(racing, "ex", "r", "s");
        CyclicBarrier start = new CyclicBarrier(2);
        Thread other = new Thread(() -> {
            await(start);
            call.callback(callback);
        }, "other");
        other.start();
        await(start);
        call.returned(returned);
        other.join(TimeUnit.SECONDS.toMillis(10));
    }

    private SanitizerDoneSyncListener.DoneSyncCall newCall() {
        return new SanitizerDoneSyncListener.DoneSyncCall(sanitizer, "ex", "r", "s");
    }

    private static void onAnotherThread(SanitizerDoneSyncListener.DoneSyncCall call, boolean doneSync) throws Exception {
        Thread other = new Thread(() -> call.callback(doneSync), "other");
        other.start();
        other.join(TimeUnit.SECONDS.toMillis(10));
    }

    private SanitizerViolation only(Violation kind) {
        List<SanitizerViolation> all = sanitizer.getViolations();
        assertEquals(1, all.size(), all::toString);
        assertEquals(kind, all.get(0).getKind());
        assertEquals("R3", kind.getRule());
        assertTrue(all.get(0).getText().startsWith("Exchange sanitizer: " + kind.getTitle() + " (R3)\n"),
                all.get(0).getText());
        assertEquals(1, session.drain().size());
        return all.get(0);
    }

    private void assertNoViolations() {
        assertEquals(List.of(), sanitizer.getViolations());
        assertEquals(List.of(), session.drain());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

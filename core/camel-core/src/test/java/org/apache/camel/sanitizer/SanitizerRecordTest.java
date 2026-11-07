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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record of a call of rule R1 under threads that race: two callbacks and a throw of the same call on three threads
 * at the same time, many times. Each violation is reported exactly once, whatever the order (the kind depends on the
 * order, as the TLA+ model of the design's validation shows). Also the bound of the violations kept.
 */
class SanitizerRecordTest {

    private final CamelContext context = new DefaultCamelContext();
    private final SanitizerTestSession session = SanitizerTestSession.open("record");

    @AfterEach
    void close() {
        session.close();
    }

    @Test
    void racingCallbacksAndThrowAreEachReportedOnce() throws Exception {
        int rounds = 200;
        Sanitizer sanitizer = new Sanitizer(context, 0, session);
        for (int round = 0; round < rounds; round++) {
            SanitizerStepListener.StepCall call = new SanitizerStepListener.StepCall(sanitizer, "ex" + round, "r", "s");
            CyclicBarrier start = new CyclicBarrier(3);
            List<Thread> threads = new ArrayList<>();
            threads.add(new Thread(() -> await(start, call::callback)));
            threads.add(new Thread(() -> await(start, call::callback)));
            threads.add(new Thread(() -> await(start, () -> call.threw(new IllegalStateException("Forced")))));
            threads.forEach(Thread::start);
            for (Thread t : threads) {
                t.join(TimeUnit.SECONDS.toMillis(10));
            }
            // per round: one completed twice; one throw (before or after the first callback); and either a callback
            // after the throw, or the throw after the callback
            assertEquals(round + 1, sanitizer.getViolationCount(Violation.COMPLETED_TWICE));
            long threw = sanitizer.getViolationCount(Violation.THREW_BEFORE_CALLBACK)
                         + sanitizer.getViolationCount(Violation.THREW_AFTER_CALLBACK);
            assertEquals(round + 1, threw);
            long order = sanitizer.getViolationCount(Violation.CALLBACK_AFTER_THROW)
                         + sanitizer.getViolationCount(Violation.THREW_AFTER_CALLBACK);
            assertEquals(round + 1, order);
        }
        // all reported violations are kept and charged (two per round when the throw comes after the first callback,
        // three when the first callback comes after the throw)
        long total = 0;
        for (Violation v : Violation.values()) {
            total += sanitizer.getViolationCount(v);
        }
        assertEquals(total, sanitizer.getViolations().size());
        assertEquals(total, session.drain().size());
    }

    @Test
    void aCorrectCallIsNotReported() {
        Sanitizer sanitizer = new Sanitizer(context, 32, session);
        SanitizerStepListener.StepCall call = new SanitizerStepListener.StepCall(sanitizer, "ex", "r", "s");
        call.callback();
        assertEquals(0, sanitizer.getViolations().size());
        assertTrue(sanitizer.report().endsWith("no violations"), sanitizer.report());
    }

    @Test
    void onlyTheFirstViolationsAreKeptAndAllAreCounted() {
        Sanitizer sanitizer = new Sanitizer(context, 0, session);
        int n = Sanitizer.MAX_KEPT + 5;
        for (int i = 0; i < n; i++) {
            SanitizerStepListener.StepCall call = new SanitizerStepListener.StepCall(sanitizer, "ex" + i, "r", "s");
            call.callback();
            call.callback();
        }
        assertEquals(n, sanitizer.getViolationCount(Violation.COMPLETED_TWICE));
        assertEquals(Sanitizer.MAX_KEPT, sanitizer.getViolations().size());
        assertEquals(Sanitizer.MAX_KEPT, session.drain().size());
        assertTrue(sanitizer.report().contains("(the first " + Sanitizer.MAX_KEPT + " are listed)"));
        // the summary of the shutdown: the counts and one line per violation, without the stacks
        String summary = sanitizer.summary();
        assertTrue(summary.contains("callback called twice: " + n), summary);
        assertTrue(summary.contains("callback called twice: exchange ex0, route r, step s"), summary);
    }

    private static void await(CyclicBarrier barrier, Runnable action) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        action.run();
    }
}

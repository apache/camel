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

import org.apache.camel.Exchange;
import org.apache.camel.spi.StepListener;

/**
 * Rule R3 of the exchange sanitizer, its first part: the contract of {@link org.apache.camel.AsyncProcessor} at the
 * call of each step, below the error handler of the step. The value the callback of a call is called with
 * ({@code doneSync}) must be the value {@code process(exchange, callback)} returns, and {@code true} means that the
 * callback was called already, on the calling thread:
 * <ul>
 * <li>{@code process} returned {@code true} while its callback had been called on another thread: with {@code false},
 * under a caller that uses the return value, the caller and that thread both continue the exchange; with {@code true},
 * that thread continued it while {@code process} still ran
 * ({@link Violation#RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD});</li>
 * <li>{@code process} returned {@code true} before its callback was called, and the callback then came on another
 * thread, or with {@code true}: the caller went on before the work of the processor was done
 * ({@link Violation#RETURNED_TRUE_BEFORE_CALLBACK});</li>
 * <li>{@code process} returned {@code false} and its callback was called with {@code true}: under a caller that goes on
 * only for an asynchronous callback, no thread continues the exchange
 * ({@link Violation#RETURNED_FALSE_CALLBACK_TRUE}).</li>
 * </ul>
 * The contract allows one case where the two differ, and it is not reported: a processor that hands its callback to the
 * {@link org.apache.camel.spi.ReactiveExecutor} returns the value of the processor it calls, and the reactive executor
 * runs the callback with {@code false} on the calling thread, at once or after the work that thread is running (such as
 * the channel of a step without an error handler, which a {@code choice} calls directly when its branch has one step).
 * So a callback with {@code false} on the calling thread is never reported, whatever {@code process} returns and
 * whether it comes before or after the return.
 * <p/>
 * Only the first call of the callback is compared (a second one is rule R1), and a call whose {@code process} threw is
 * not compared (rule R1 reports it). The record of a call ({@link DoneSyncCall}) is the state of the call that
 * camel-core keeps in the callback of the call, so it needs no table; it holds ids, names and stacks, never the
 * exchange or a thread.
 */
final class SanitizerDoneSyncListener implements StepListener {

    private final Sanitizer sanitizer;
    // whether this listener counts the calls of the steps (when the step listener of R1 and R2, which counts them, is
    // not installed)
    private final boolean countCalls;

    SanitizerDoneSyncListener(Sanitizer sanitizer, boolean countCalls) {
        this.sanitizer = sanitizer;
        this.countCalls = countCalls;
    }

    @Override
    public Object onStepCall(Exchange exchange, String routeId, String nodeId) {
        if (countCalls) {
            sanitizer.called();
        }
        // only here may the exchange be read: the other events can come on a thread that does not own it
        return new DoneSyncCall(sanitizer, exchange.getExchangeId(), routeId, nodeId);
    }

    @Override
    public void onStepReturn(Object state, boolean doneSync) {
        if (state instanceof DoneSyncCall call) {
            call.returned(doneSync);
        }
    }

    @Override
    public void onStepThrow(Object state, Throwable cause) {
        if (state instanceof DoneSyncCall call) {
            call.threw();
        }
    }

    @Override
    public void onStepCallback(Object state, boolean doneSync) {
        if (state instanceof DoneSyncCall call) {
            call.callback(doneSync);
        }
    }

    @Override
    public String toString() {
        return "ExchangeSanitizer[R3]";
    }

    /**
     * The record of one call of the processor of a step, for the comparison of its return value with the value of its
     * first callback. The return and the callback can come on different threads at the same time, so they are applied
     * one at a time under the lock of the record, and whichever of the two comes second compares them, once.
     * <p/>
     * A stack is the main cost, so the callback keeps one only when a report can need it later: a callback on a thread
     * other than the one that called {@code process}, before {@code process} returned. A callback on the calling thread
     * (the usual synchronous case, and the reactive executor) keeps only that it was there; a callback after the return
     * takes its stack only when it is reported.
     */
    static final class DoneSyncCall {

        static final byte NONE = 0;
        static final byte FALSE = 1;
        static final byte TRUE = 2;
        static final byte THREW = 3;

        private final Sanitizer sanitizer;
        private final String exchangeId;
        private final String routeId;
        private final String nodeId;
        // the thread that called process (its id: a Thread is not kept)
        private final long callerThread;

        // what process did: set once, from NONE, under the lock; volatile, so a callback can see without the lock that
        // process returned already and keep no stack
        private volatile byte returned = NONE;
        // guarded by this
        private byte callbackValue = NONE;
        private boolean callbackOnCaller;
        private Sanitizer.Trace callbackTrace;

        DoneSyncCall(Sanitizer sanitizer, String exchangeId, String routeId, String nodeId) {
            this.sanitizer = sanitizer;
            this.exchangeId = exchangeId;
            this.routeId = routeId;
            this.nodeId = nodeId;
            this.callerThread = Thread.currentThread().threadId();
        }

        void callback(boolean doneSync) {
            boolean onCaller = Thread.currentThread().threadId() == callerThread;
            // taken before the lock, as the return can still come with true and need it
            Sanitizer.Trace trace = !onCaller && returned == NONE ? sanitizer.trace() : null;
            byte r;
            synchronized (this) {
                if (callbackValue != NONE) {
                    // a second call of the callback: rule R1
                    return;
                }
                callbackValue = doneSync ? TRUE : FALSE;
                callbackOnCaller = onCaller;
                callbackTrace = trace;
                r = returned;
            }
            Violation violation;
            if (r == FALSE && doneSync) {
                violation = Violation.RETURNED_FALSE_CALLBACK_TRUE;
            } else if (r == TRUE && (doneSync || !onCaller)) {
                // after a return of true only the reactive executor may still call the callback: with false, on the
                // calling thread
                violation = Violation.RETURNED_TRUE_BEFORE_CALLBACK;
            } else {
                // not returned yet (the return compares), threw (rule R1), or the same value
                return;
            }
            StringBuilder sb = new StringBuilder(256);
            sb.append("  process returned ").append(r == TRUE).append('\n');
            (trace != null ? trace : sanitizer.trace())
                    .appendTo(sb, "then the callback was called with " + doneSync
                                  + (onCaller ? ", on the thread that called process" : ""));
            sanitizer.record(violation, exchangeId, routeId, nodeId, sb.toString());
        }

        void returned(boolean doneSync) {
            byte cb;
            boolean cbOnCaller;
            Sanitizer.Trace first;
            synchronized (this) {
                if (returned != NONE) {
                    return;
                }
                returned = doneSync ? TRUE : FALSE;
                cb = callbackValue;
                cbOnCaller = callbackOnCaller;
                first = callbackTrace;
            }
            Violation violation;
            if (cb == NONE) {
                // the callback compares when it comes
                return;
            } else if (doneSync && !cbOnCaller) {
                violation = Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD;
            } else if (!doneSync && cb == TRUE) {
                violation = Violation.RETURNED_FALSE_CALLBACK_TRUE;
            } else {
                // the same value on the calling thread, or a callback with false there (the reactive executor)
                return;
            }
            StringBuilder sb = new StringBuilder(256);
            String what = "the callback was called with " + (cb == TRUE);
            if (first != null) {
                first.appendTo(sb, what);
            } else {
                // a callback on the calling thread before the return keeps no stack: process was still running there
                sb.append("  ").append(what).append(": on the thread that called process, inside process (no stack is"
                                                    + " kept for it)\n");
            }
            sanitizer.trace().appendTo(sb, "then process returned " + doneSync);
            sanitizer.record(violation, exchangeId, routeId, nodeId, sb.toString());
        }

        void threw() {
            synchronized (this) {
                if (returned == NONE) {
                    // rule R1 reports it; a callback that comes later is not compared
                    returned = THREW;
                }
            }
        }
    }
}

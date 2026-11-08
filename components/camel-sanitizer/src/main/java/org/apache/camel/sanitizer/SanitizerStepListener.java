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
 * Rule R1 of the exchange sanitizer: the callback of each call of the processor of a step is called exactly once, and
 * {@code process(exchange, callback)} does not throw.
 * <p/>
 * The record of a call ({@link StepCall}) is the state of the call that camel-core keeps in the callback of the call,
 * so it lives as long as anything can still call that callback, and needs no table. It holds ids, names, threads and
 * stacks, never the exchange.
 */
final class SanitizerStepListener implements StepListener {

    private final Sanitizer sanitizer;

    SanitizerStepListener(Sanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public Object onStepCall(Exchange exchange, String routeId, String nodeId) {
        sanitizer.called();
        // only here may the exchange be read: the other events can come on a thread that does not own it
        return new StepCall(sanitizer, exchange.getExchangeId(), routeId, nodeId);
    }

    @Override
    public void onStepThrow(Object state, Throwable cause) {
        if (state instanceof StepCall call) {
            call.threw(cause);
        }
    }

    @Override
    public void onStepCallback(Object state, boolean doneSync) {
        if (state instanceof StepCall call) {
            call.callback();
        }
    }

    @Override
    public String toString() {
        return "ExchangeSanitizer[R1]";
    }

    /**
     * The record of one call of the processor of a step. The events of a call can come on different threads at the same
     * time (a callback on another thread while {@code process} throws), so they are applied one at a time under the
     * lock of the record; within one call, the order in which they are applied decides the kind of violation, not
     * whether one is reported. What a processor throws is reported once, at the innermost step: the same exception
     * thrown on through an enclosing step on the same thread is ignored by the call of the enclosing step, as if that
     * call had not thrown.
     */
    static final class StepCall {

        // the usual fix when a plain Processor threw an Error after Camel's adapter called the callback
        static final String SYNC_ADAPTER_FIX
                = "fix what threw the Error. The processor is a plain Processor, so the callback is not its own: Camel's"
                  + " adapter around it (such as DelegateSyncProcessor for process(...)) calls the callback in finally and"
                  + " then lets the Error through";

        private final Sanitizer sanitizer;
        private final String exchangeId;
        private final String routeId;
        private final String nodeId;

        // guarded by this; what was thrown is kept as its class and message (and its stack in throwTrace), never as the
        // Throwable, which can hold the exchange (such as CamelExchangeException) for as long as the callback lives
        private int callbacks;
        private Sanitizer.Trace firstCallback;
        private String thrown;
        private Sanitizer.Trace throwTrace;

        StepCall(Sanitizer sanitizer, String exchangeId, String routeId, String nodeId) {
            this.sanitizer = sanitizer;
            this.exchangeId = exchangeId;
            this.routeId = routeId;
            this.nodeId = nodeId;
        }

        void callback() {
            // the stack is taken before the lock: it is the main cost, and is kept for a second call that may come
            Sanitizer.Trace trace = sanitizer.trace();
            Violation violation;
            Sanitizer.Trace first;
            Sanitizer.Trace threw;
            String cause;
            synchronized (this) {
                callbacks++;
                if (callbacks == 1) {
                    firstCallback = trace;
                    if (thrown == null) {
                        return;
                    }
                    violation = Violation.CALLBACK_AFTER_THROW;
                } else {
                    violation = Violation.COMPLETED_TWICE;
                }
                first = firstCallback;
                threw = throwTrace;
                cause = thrown;
            }
            StringBuilder sb = new StringBuilder(256);
            if (violation == Violation.COMPLETED_TWICE) {
                first.appendTo(sb, "first call of the callback");
                trace.appendTo(sb, "second call of the callback");
            } else {
                threw.appendTo(sb, "process threw " + cause);
                trace.appendTo(sb, "then the callback was called");
            }
            sanitizer.record(violation, exchangeId, routeId, nodeId, sb.toString());
        }

        void threw(Throwable cause) {
            if (!sanitizer.firstReportOf(cause)) {
                // reported at the inner step that threw it, and thrown on through this one: this call ignores it
                return;
            }
            Sanitizer.Trace trace = sanitizer.trace(cause);
            String description = describe(cause);
            Sanitizer.Trace first;
            synchronized (this) {
                if (thrown != null) {
                    return;
                }
                thrown = description;
                throwTrace = trace;
                first = firstCallback;
            }
            StringBuilder sb = new StringBuilder(256);
            if (first == null) {
                trace.appendTo(sb, "process threw " + description);
                sanitizer.record(Violation.THREW_BEFORE_CALLBACK, exchangeId, routeId, nodeId, sb.toString());
            } else if (cause instanceof Error && first.calledBySyncAdapter()) {
                // a plain Processor threw an Error: Camel's adapter called the callback in finally and let the Error
                // through, so the adapter breaks the rule, not the processor
                first.appendTo(sb, "the callback was called by Camel's adapter for a plain Processor");
                trace.appendTo(sb, "then the Error of the processor went through the adapter: " + description);
                sanitizer.record(Violation.THREW_AFTER_CALLBACK, exchangeId, routeId, nodeId, sb.toString(),
                        SYNC_ADAPTER_FIX);
            } else {
                first.appendTo(sb, "the callback was called");
                trace.appendTo(sb, "then process threw " + description);
                sanitizer.record(Violation.THREW_AFTER_CALLBACK, exchangeId, routeId, nodeId, sb.toString());
            }
        }

        private static String describe(Throwable cause) {
            return cause.getClass().getName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
        }
    }
}

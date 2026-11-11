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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StepWithoutErrorHandlerProcessorThrowsTest extends StepWithoutErrorHandlerProcessorThrowsTestSupport {

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void doTry() throws Exception {
        assertSameAsSettingTheException(DO_TRY,
                "exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void doTryWithDoFinally() throws Exception {
        assertSameAsSettingTheException(DO_TRY_FINALLY,
                "exception=IllegalStateException:thrown caught=IllegalStateException:thrown result=0 caughtMock=0 finally=1 calls=1");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void doTryWithDoFinallyProcessorThrowsError() throws Exception {
        // an Error (such as NoClassDefFoundError) thrown before the callback fails the exchange as well, as in the
        // other fixes of this kind
        start(DO_TRY_FINALLY, new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                throw new NoClassDefFoundError("thrown");
            }
        });
        Exchange out = send();
        assertInstanceOf(NoClassDefFoundError.class, out.getException().getCause());
        assertEquals("thrown", out.getException().getCause().getMessage());
        assertEquals(1, calls.get(), "calls");
        assertEquals(0, getMockEndpoint("mock:start-result").getReceivedCounter(), "mock:result");
        assertEquals(1, getMockEndpoint("mock:start-finally").getReceivedCounter(), "mock:finally");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void doCatch() throws Exception {
        assertSameAsSettingTheException(DO_CATCH,
                "exception=IllegalStateException:thrown caught=IllegalStateException:thrown result=0 caughtMock=0"
                                                  + " finally=1 calls=1");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void doFinally() throws Exception {
        assertSameAsSettingTheException(DO_FINALLY,
                "exception=IllegalStateException:thrown caught=none result=0 caughtMock=0 finally=0 calls=1");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void onException() throws Exception {
        assertSameAsSettingTheException(ON_EXCEPTION,
                "exception=IllegalStateException:thrown caught=IllegalStateException:thrown result=0 caughtMock=0"
                                                      + " finally=0 calls=1");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void onExceptionNotHandled() throws Exception {
        assertSameAsSettingTheException(ON_EXCEPTION_NOT_HANDLED,
                "exception=IllegalStateException:thrown caught=IllegalStateException:thrown result=0 caughtMock=0"
                                                                  + " finally=0 calls=1");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void multicastOutput() throws Exception {
        // the failed output fails the exchange at the end of the multicast, and the next output still runs
        assertSameAsSettingTheException(MULTICAST,
                "exception=IllegalStateException:thrown caught=none result=0 caughtMock=0 finally=1 calls=1");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void multicastOutputRedelivered() throws Exception {
        // the output is redelivered as when it sets the exception, and succeeds on the second call
        assertSameAsSettingTheException(MULTICAST_REDELIVERY,
                "exception=none caught=none result=1 caughtMock=0 finally=1 calls=2");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void afterAnAsynchronousStep() throws Exception {
        // the step runs on the thread of the asynchronous delay, not on the caller's
        start((b, id, p) -> b
                .from("direct:" + id)
                .doTry()
                    .delay(10).asyncDelayed().process(p).to("mock:" + id + "-result")
                .endDoTry()
                .doCatch(IllegalStateException.class)
                    .to("mock:" + id + "-caught")
                .end(), new ThrowsFirst());

        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void withMdcLogging() throws Exception {
        // with MDC logging the unit of work wraps the callback of each step (the takeover runs its after processing
        // instead of that wrapper)
        context.setUseMDCLogging(true);
        start(DO_TRY, new ThrowsFirst());

        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void controlTransacted() throws Exception {
        // control: a transacted exchange calls the step synchronously, which catches the exception, on main too
        start(DO_TRY, new ThrowsFirst());

        Exchange out = template.asyncSend("direct:start", e -> {
            e.getMessage().setBody("Hello");
            e.getExchangeExtension().setTransacted(true);
        }).get(10, TimeUnit.SECONDS);
        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(out));
    }

    @Test
    void controlPlainProcessorThrows() throws Exception {
        // control, as before: the converter of a plain Processor to an AsyncProcessor catches the exception and sets it
        // on the exchange
        start(DO_TRY, exchange -> {
            calls.incrementAndGet();
            throw new IllegalStateException("thrown");
        });

        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_AFTER_CALLBACK)
    void controlCallbackCalledBeforeTheProcessorThrows() throws Exception {
        // control, as before: the processor calls the callback and then throws. The callback continued the exchange, so
        // the exception is left to the caller (the reactive executor, which logs it), and the exchange completes once
        start(DO_TRY, new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                callback.done(true);
                throw new IllegalStateException("after the callback");
            }
        });
        AtomicInteger done = countDoneExchanges();

        Exchange out = sendOnThisThread();
        assertEquals("exception=none caught=none result=1 caughtMock=0 finally=0 calls=1", outcome(out));
        assertEquals(1, done.get(), "the exchange was done once");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_AFTER_CALLBACK)
    void controlCallbackCalledBeforeTheProcessorThrowsReachesASynchronousCaller() throws Exception {
        // control, as before: a caller that calls the step through the synchronous API (as a circuit breaker does with
        // its steps) gets the exception that the processor throws after it called the callback, as the callback only
        // released that caller
        Processor processor = new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                callback.done(true);
                throw new IllegalStateException("after the callback");
            }
        };
        start(DO_TRY, processor);
        Channel channel = findChannel(context.getRoutes().get(0).navigate(), processor);
        assertNotNull(channel, "the channel of the step");

        Exchange exchange = context.getEndpoint("direct:start").createExchange();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> channel.process(exchange));
        assertEquals("after the callback", e.getMessage());
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    @ExpectSanitizerViolation({ Violation.THREW_BEFORE_CALLBACK, Violation.THREW_AFTER_CALLBACK })
    @ExpectSanitizerViolation({ Violation.CALLBACK_AFTER_THROW, Violation.THREW_AFTER_CALLBACK })
    void controlCallbackQueuedBeforeTheProcessorThrows() throws Exception {
        // control, as before: the processor queues the call of the callback on the reactive executor and then throws.
        // The thrown failure is taken over through the reactive executor, after the work already queued there, so the
        // queued callback comes first and continues the exchange
        start(DO_TRY, new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                exchange.getContext().getCamelContextExtension().getReactiveExecutor().schedule(() -> {
                    exchange.getMessage().setBody("queued");
                    callback.done(false);
                });
                throw new IllegalStateException("after the callback was queued");
            }
        });
        AtomicInteger done = countDoneExchanges();

        Exchange out = sendOnThisThread();
        assertEquals("exception=none caught=none result=1 caughtMock=0 finally=0 calls=1", outcome(out));
        assertEquals("queued", out.getMessage().getBody());
        assertEquals(1, done.get(), "the exchange was done once");
    }
}

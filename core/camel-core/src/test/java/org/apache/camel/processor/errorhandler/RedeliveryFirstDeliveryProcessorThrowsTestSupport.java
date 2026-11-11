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
package org.apache.camel.processor.errorhandler;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Shared by {@link RedeliveryFirstDeliveryProcessorThrowsTest} and
 * {@link RedeliveryFirstDeliveryProcessorThrowsPooledTest}: the tests that depend on whether the redelivery tasks are
 * pooled. The route has an onException, so its error handler runs the step with its redelivery task, and the tests
 * inherited from {@link SimpleTaskProcessorThrowsTestSupport} (the late callback with the next exchange, the double
 * callback) run on the first delivery of that task.
 */
abstract class RedeliveryFirstDeliveryProcessorThrowsTestSupport extends SimpleTaskProcessorThrowsTestSupport {

    @Override
    protected void start(Processor processor) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // not handled and no redelivery: the exchange fails with the thrown exception, as with the simple task
                onException(IllegalStateException.class).maximumRedeliveries(0);
                from("direct:start").process(processor).to("mock:result");
            }
        });
        context.start();
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_AFTER_CALLBACK)
    void callbackCalledBeforeTheProcessorThrowsContinuesTheExchangeOnce() throws Exception {
        // the processor calls the callback and then throws: the callback continued the exchange, so the exception is
        // only logged, and the exchange completes once (before, the catch of the task failed the exchange and completed
        // it a second time)
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        start(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                callback.done(true);
                throw new IllegalStateException("after the callback");
            }
        });
        AtomicInteger done = countDoneExchanges();

        Exchange out = sendOnThisThread();
        assertNull(out.getException());
        result.assertIsSatisfied();
        assertEquals(1, result.getReceivedCounter(), "mock:result");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(1, calls.get(), "calls");
    }
}

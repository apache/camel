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
package org.apache.camel.component.seda;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The new Async API version of doing async routing based on the old AsyncProcessor API In the old
 * SedaAsyncProcessorTest a seda endpoint was needed to really turn it into async. This is not needed by the new API so
 * we send it using direct instead.
 */
public class SedaAsyncProducerTest extends ContextTestSupport {

    /**
     * Thread-safe accumulator for the route execution order. Each step appends its token atomically using
     * AtomicReference.updateAndGet() to avoid the non-atomic read-modify-write of the plain String field that caused
     * the flaky ordering assertions.
     */
    private final AtomicReference<String> route = new AtomicReference<>("");

    /**
     * Latch used in testAsyncProducer to guarantee that the test thread records "send" before the route processor is
     * allowed to record "process". Without this, a heavily loaded CI machine can preempt the test thread for >100ms
     * after asyncRequestBody() returns, letting the async thread win the race even though a 100ms delay was inserted in
     * the route.
     */
    private final CountDownLatch sendLatch = new CountDownLatch(1);

    @Test
    public void testAsyncProducer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // using the new async API we can fire a real async message
        Future<String> future = template.asyncRequestBody("direct:start", "Hello World", String.class);

        // Record "send" atomically, then signal the route processor it may proceed.
        // The processor waits on sendLatch so ordering is deterministic regardless of CI load.
        route.updateAndGet(s -> s + "send");
        sendLatch.countDown();

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        assertEquals("sendprocess", route.get(), "Send should occur before processor");

        // and get the response with the future handle
        String response = future.get();
        assertEquals("Bye World", response);
    }

    @Test
    public void testAsyncProducerWait() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // using the new async API we can fire a real async message
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");
        exchange.setPattern(ExchangePattern.InOut);
        exchange.setProperty(Exchange.ASYNC_WAIT, WaitForTaskToComplete.IfReplyExpected);
        // WaitForTaskToComplete.IfReplyExpected on InOut blocks until processing is complete,
        // so the processor always records "process" before template.send() returns.
        // The latch is not used in this test; count it down immediately so the processor is not blocked.
        sendLatch.countDown();
        template.send("direct:start", exchange);

        // I should not happen before mock – processor already finished above
        route.updateAndGet(s -> s + "send");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        assertEquals("processsend", route.get(), "Send should occur after processor");

        String response = exchange.getMessage().getBody(String.class);
        assertEquals("Bye World", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());

                from("direct:start").delay(100).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // Wait until the test thread has recorded "send" (only relevant for
                        // testAsyncProducer; testAsyncProducerWait counts down the latch before send).
                        if (!sendLatch.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for sendLatch");
                        }
                        route.updateAndGet(s -> s + "process");
                        // set the response
                        exchange.getMessage().setBody("Bye World");
                    }
                }).to("mock:result");

            }
        };
    }
}

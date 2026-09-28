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
package org.apache.camel.component.disruptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * When a request/reply send to a disruptor endpoint times out, the consumer must ignore the timed out exchange if it
 * has not started it yet, and later sends of the caller's exchange (a redelivery, a fallback) must not be ignored.
 */
class DisruptorTimeoutIgnoreExchangeTest extends CamelTestSupport {

    private final CountDownLatch releaseSlow = new CountDownLatch(1);
    private final CountDownLatch releaseBusy = new CountDownLatch(1);
    private final AtomicInteger attempts = new AtomicInteger();

    @AfterEach
    void release() {
        releaseSlow.countDown();
        releaseBusy.countDown();
    }

    @Test
    void testRedeliveryAfterTimeoutIsProcessed() {
        // the first attempt times out, the redelivery must reach the consumer and get the reply
        Object reply = template.requestBody("direct:redeliver", "hello");
        assertEquals("reply to attempt 2", reply);
    }

    @Test
    void testInOnlyFallbackAfterTimeoutIsProcessed() throws Exception {
        getMockEndpoint("mock:fallback").expectedBodiesReceived("hello");

        template.requestBody("direct:fallback", "hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testTimedOutExchangeNotStartedIsIgnored() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:busy");
        mock.expectedBodiesReceived("A");
        // the timed out B must not be processed after A
        mock.setAssertPeriod(1000);

        // A keeps the (single) consumer busy
        template.sendBody("disruptor:busy", "A");
        // B waits in the ring buffer until it times out
        Exchange out = template.send("disruptor:busy?timeout=250", ExchangePattern.InOut,
                e -> e.getMessage().setBody("B"));
        assertInstanceOf(ExchangeTimedOutException.class, out.getException());
        releaseBusy.countDown();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testReplyDoesNotMarkCallerExchange() throws Exception {
        getMockEndpoint("mock:after").expectedBodiesReceived("echo hello");

        Exchange out = template.send("direct:twice", ExchangePattern.InOut, e -> e.getMessage().setBody("hello"));

        assertNull(out.getException());
        assertNull(out.getProperty(DisruptorEndpoint.DISRUPTOR_IGNORE_EXCHANGE));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:redeliver")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .to("disruptor:slow?timeout=250");

                from("disruptor:slow?concurrentConsumers=2")
                        .process(e -> {
                            int attempt = attempts.incrementAndGet();
                            if (attempt == 1) {
                                releaseSlow.await(10, TimeUnit.SECONDS);
                            }
                            e.getMessage().setBody("reply to attempt " + attempt);
                        });

                from("direct:fallback")
                        .doTry()
                            .to("disruptor:slow2?timeout=250")
                        .doCatch(ExchangeTimedOutException.class)
                            .to(ExchangePattern.InOnly, "disruptor:fallback")
                        .end();

                from("disruptor:slow2")
                        .process(e -> releaseSlow.await(10, TimeUnit.SECONDS));

                from("disruptor:fallback").to("mock:fallback");

                from("disruptor:busy")
                        .process(e -> releaseBusy.await(10, TimeUnit.SECONDS))
                        .to("mock:busy");

                from("direct:twice")
                        .to("disruptor:echo")
                        .to(ExchangePattern.InOnly, "disruptor:after");

                from("disruptor:echo").setBody(simple("echo ${body}"));

                from("disruptor:after").to("mock:after");
            }
        };
    }
}

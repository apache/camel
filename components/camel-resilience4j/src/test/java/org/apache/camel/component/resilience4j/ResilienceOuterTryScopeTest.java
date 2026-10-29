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
package org.apache.camel.component.resilience4j;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResilienceOuterTryScopeTest extends CamelTestSupport {
    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    void synchronousCircuitBreakerPreservesOuterTry() throws Exception {
        check("direct:sync");
    }

    @Test
    void asynchronousCircuitBreakerPreservesOuterTry() throws Exception {
        check("direct:async");
    }

    @Test
    void fallbackPreservesOuterTry() throws Exception {
        check("direct:fallback");
    }

    @Test
    void laterFailureAfterRecoveredFallbackIsCaught() throws Exception {
        getMockEndpoint("mock:caught").expectedMessageCount(1);
        Exchange out = template.send("direct:afterFallback", e -> e.getMessage().setBody("Hello"));
        MockEndpoint.assertIsSatisfied(context);
        assertNull(out.getException(), "the doCatch must catch the failure after the fallback");
    }

    @Test
    void circuitBreakerInDoCatchKeepsTheFailureHandled() throws Exception {
        // control: a later doCatch of the same doTry is still skipped after the doCatch that handled the failure
        getMockEndpoint("mock:first").expectedMessageCount(1);
        getMockEndpoint("mock:second").expectedMessageCount(0);
        Exchange out = template.send("direct:inCatch", e -> e.getMessage().setBody("Hello"));
        MockEndpoint.assertIsSatisfied(context);
        assertNull(out.getException());
    }

    @Test
    void outsideTryUsesGlobalErrorHandler() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:outside", "Hello");
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(3, attempts.get());
        assertEquals(0, getMockEndpoint("mock:caught").getReceivedCounter());
    }

    private void check(String uri) throws Exception {
        getMockEndpoint("mock:caught").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        template.sendBody(uri, "Hello");
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, attempts.get(), "The outer doTry owns the recipient list failure");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));
                from("direct:fail").errorHandler(noErrorHandler()).process(e -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("Failure after circuit breaker");
                });
                from("direct:outside").circuitBreaker().to("mock:inside").end()
                    .recipientList(constant("direct:fail"));
                from("direct:sync").doTry()
                    .circuitBreaker().to("mock:inside").end()
                    .recipientList(constant("direct:fail")).endDoTry()
                    .doCatch(IllegalStateException.class).to("mock:caught").end();
                from("direct:async").doTry()
                    .circuitBreaker().resilience4jConfiguration().asynchronous(true).end()
                        .to("mock:inside").end()
                    .recipientList(constant("direct:fail")).endDoTry()
                    .doCatch(IllegalStateException.class).to("mock:caught").end();
                from("direct:fallback").doTry()
                    .circuitBreaker().throwException(new IllegalArgumentException("Recover me"))
                        .onFallback().setBody(constant("Recovered")).end()
                    .recipientList(constant("direct:fail")).endDoTry()
                    .doCatch(IllegalStateException.class).to("mock:caught").end();
                from("direct:afterFallback").doTry()
                    .circuitBreaker().throwException(new IllegalArgumentException("Recover me"))
                        .onFallback().setBody(constant("Recovered")).end()
                    .throwException(new IllegalStateException("Failure after the fallback")).endDoTry()
                    .doCatch(IllegalStateException.class).to("mock:caught").end();
                from("direct:inCatch").doTry()
                    .throwException(new IllegalStateException("First failure"))
                    .doCatch(IllegalStateException.class).to("mock:first")
                        .circuitBreaker().to("mock:inside").end().endDoTry()
                    .doCatch(Exception.class).to("mock:second").end();
            }
        };
    }
}

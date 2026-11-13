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
package org.apache.camel.impl.engine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.InternalProcessor;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class CamelInternalProcessorAdviceTest extends ContextTestSupport {

    private final AtomicInteger stateAfter = new AtomicInteger();
    private final CountingPolicy beginCounter = new CountingPolicy();
    private final CountingPolicy doneCounter = new CountingPolicy();

    @Test
    public void testBeforeFailsRunsAfterOfEarlierAdvices() throws Exception {
        InternalProcessor ip = (InternalProcessor) context.getRoute("start").getProcessor();
        ip.addAdvice(new StatefulAdvice());
        ip.addAdvice(new CamelInternalProcessorAdvice<Object>() {
            @Override
            public Object before(Exchange exchange) {
                throw new IllegalStateException("Forced before");
            }

            @Override
            public void after(Exchange exchange, Object data) {
                throw new IllegalStateException("Should not run after when before failed");
            }

            @Override
            public boolean hasState() {
                return false;
            }
        });

        getMockEndpoint("mock:result").expectedMessageCount(0);

        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
            assertInstanceOf(IllegalStateException.class, out.getException());
            assertEquals("Forced before", out.getException().getMessage());
            assertEquals(0, out.getException().getSuppressed().length);
        }

        assertMockEndpointsSatisfied();
        // the exchanges must not be left inflight
        assertEquals(0, context.getInflightRepository().size("start"));
        assertEquals(0, context.getInflightRepository().size());
        // the stateful advice gets its state
        assertEquals(3, stateAfter.get());
    }

    @Test
    public void testAfterFailsKeepsRouteException() {
        InternalProcessor ip = (InternalProcessor) context.getRoute("fail").getProcessor();
        ip.addAdvice(new CamelInternalProcessorAdvice<Object>() {
            @Override
            public Object before(Exchange exchange) {
                return null;
            }

            @Override
            public void after(Exchange exchange, Object data) {
                throw new IllegalStateException("Forced after");
            }

            @Override
            public boolean hasState() {
                return false;
            }
        });

        Exchange out = template.send("direct:fail", e -> e.getMessage().setBody("Hello"));
        Exception cause = out.getException();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Forced route", cause.getMessage());
        assertEquals(1, cause.getSuppressed().length);
        assertEquals("Forced after", cause.getSuppressed()[0].getMessage());
    }

    @Test
    public void testAfterFailsWithoutRouteException() {
        InternalProcessor ip = (InternalProcessor) context.getRoute("start").getProcessor();
        IllegalStateException forced = new IllegalStateException("Forced after");
        ip.addAdvice(new CamelInternalProcessorAdvice<Object>() {
            @Override
            public Object before(Exchange exchange) {
                return null;
            }

            @Override
            public void after(Exchange exchange, Object data) throws Exception {
                throw forced;
            }

            @Override
            public boolean hasState() {
                return false;
            }
        });

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertSame(forced, out.getException());
    }

    @Test
    public void testBeforeThrowsErrorRunsAfterOfEarlierAdvices() throws Exception {
        AssertionError error = new AssertionError("Forced before");
        InternalProcessor ip = (InternalProcessor) context.getRoute("start").getProcessor();
        ip.addAdvice(new StatefulAdvice());
        ip.addAdvice(new CamelInternalProcessorAdvice<Object>() {
            @Override
            public Object before(Exchange exchange) {
                throw error;
            }

            @Override
            public void after(Exchange exchange, Object data) {
                throw new IllegalStateException("Should not run after when before failed");
            }

            @Override
            public boolean hasState() {
                return false;
            }
        });

        getMockEndpoint("mock:result").expectedMessageCount(0);

        // bounded: before, the Error escaped from the route, without the after of the earlier advices
        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("Hello")).get(10, TimeUnit.SECONDS);
        assertSame(error, assertInstanceOf(CamelExecutionException.class, out.getException()).getCause());

        assertMockEndpointsSatisfied();
        // the exchange must not be left inflight, and the stateful advice gets its state
        assertEquals(0, context.getInflightRepository().size());
        assertEquals(1, stateAfter.get());
    }

    @Test
    public void testAfterThrowsErrorRunsTheOtherAfters() throws Exception {
        AssertionError error = new AssertionError("Forced after");
        InternalProcessor ip = (InternalProcessor) context.getRoute("start").getProcessor();
        ip.addAdvice(new StatefulAdvice());
        ip.addAdvice(new ThrowingAfterAdvice(error));

        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("Hello")).get(10, TimeUnit.SECONDS);
        assertSame(error, assertInstanceOf(CamelExecutionException.class, out.getException()).getCause());

        // the after of the advices before it (run in reverse order) still run, such as the end of the unit of work
        assertEquals(1, stateAfter.get());
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    public void testAfterThrowsErrorKeepsRouteException() throws Exception {
        AssertionError error = new AssertionError("Forced after");
        InternalProcessor ip = (InternalProcessor) context.getRoute("fail").getProcessor();
        ip.addAdvice(new ThrowingAfterAdvice(error));

        Exchange out = template.asyncSend("direct:fail", e -> e.getMessage().setBody("Hello")).get(10, TimeUnit.SECONDS);
        Exception cause = out.getException();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Forced route", cause.getMessage());
        assertEquals(1, cause.getSuppressed().length);
        assertSame(error, cause.getSuppressed()[0]);
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    public void testRoutePolicyThrowsErrorOnBeginIsIgnored() throws Exception {
        // as an exception of a route policy: logged, and the other policies are still called
        getMockEndpoint("mock:policy").expectedMessageCount(1);

        Exchange out = template.asyncSend("direct:policyBegin", e -> e.getMessage().setBody("Hello"))
                .get(10, TimeUnit.SECONDS);

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(1, beginCounter.begins.get());
        assertEquals(1, beginCounter.dones.get());
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    public void testRoutePolicyThrowsErrorOnDoneIsIgnored() throws Exception {
        getMockEndpoint("mock:policy").expectedMessageCount(1);

        Exchange out = template.asyncSend("direct:policyDone", e -> e.getMessage().setBody("Hello"))
                .get(10, TimeUnit.SECONDS);

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(1, doneCounter.begins.get());
        assertEquals(1, doneCounter.dones.get());
        assertEquals(0, context.getInflightRepository().size());
    }

    private static class ThrowingAfterAdvice implements CamelInternalProcessorAdvice<Object> {
        private final Error error;

        ThrowingAfterAdvice(Error error) {
            this.error = error;
        }

        @Override
        public Object before(Exchange exchange) {
            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) {
            throw error;
        }

        @Override
        public boolean hasState() {
            return false;
        }
    }

    private static class ThrowingPolicy extends RoutePolicySupport {
        private final boolean onBegin;

        ThrowingPolicy(boolean onBegin) {
            this.onBegin = onBegin;
        }

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            if (onBegin) {
                throw new AssertionError("Forced onExchangeBegin");
            }
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            if (!onBegin) {
                throw new AssertionError("Forced onExchangeDone");
            }
        }
    }

    private static class CountingPolicy extends RoutePolicySupport {
        private final AtomicInteger begins = new AtomicInteger();
        private final AtomicInteger dones = new AtomicInteger();

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            begins.incrementAndGet();
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            dones.incrementAndGet();
        }
    }

    private class StatefulAdvice implements CamelInternalProcessorAdvice<String> {
        @Override
        public String before(Exchange exchange) {
            return "state";
        }

        @Override
        public void after(Exchange exchange, String data) {
            if ("state".equals(data)) {
                stateAfter.incrementAndGet();
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start")
                        .to("mock:result");

                from("direct:fail").routeId("fail")
                        .throwException(new IllegalArgumentException("Forced route"));

                from("direct:policyBegin").routeId("policyBegin")
                        .routePolicy(new ThrowingPolicy(true), beginCounter)
                        .to("mock:policy");

                from("direct:policyDone").routeId("policyDone")
                        .routePolicy(new ThrowingPolicy(false), doneCounter)
                        .to("mock:policy");
            }
        };
    }
}

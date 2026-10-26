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

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The failover load balancer fails over only on the exception the attempt fails with and its causes, not on its
 * suppressed exceptions (such as an exception thrown in a doFinally block, or the original exception when an
 * onException block or the onWhen of a doCatch fails), the same as onException and doCatch.
 */
class FailOverLoadBalanceSuppressedExceptionTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                failover("finally");
                from("direct:finally-attempt")
                        .doTry()
                            .throwException(new IOException("original"))
                        .doFinally()
                            .throwException(new IllegalArgumentException("cleanup"))
                        .end();

                failover("finallyRoute");
                from("direct:finallyRoute-attempt")
                        .doTry()
                            .throwException(new IOException("original"))
                        .doFinally()
                            .to("direct:cleanup")
                        .end();
                from("direct:cleanup")
                        .throwException(new IllegalArgumentException("cleanup"));

                failover("suppressed");
                from("direct:suppressed-attempt")
                        .process(e -> {
                            IOException original = new IOException("original");
                            original.addSuppressed(new IllegalArgumentException("cleanup"));
                            throw original;
                        });

                failover("cause");
                from("direct:cause-attempt")
                        .process(e -> {
                            throw new IOException("original", new IllegalArgumentException("cause"));
                        });

                failover("monitored");
                from("direct:monitored-attempt")
                        .throwException(new IllegalArgumentException("original"));

                failover("monitoredFinally");
                from("direct:monitoredFinally-attempt")
                        .doTry()
                            .throwException(new IllegalArgumentException("original"))
                        .doFinally()
                            .throwException(new IOException("cleanup"))
                        .end();

                failover("notMonitored");
                from("direct:notMonitored-attempt")
                        .throwException(new IOException("original"));

                failover("errorHandlerFails");
                from("direct:errorHandlerFails-attempt")
                        .onException(IllegalArgumentException.class).to("direct:audit").end()
                        .throwException(new IllegalArgumentException("original"));
                from("direct:audit")
                        .throwException(new IOException("audit"));

                failover("onWhenFails");
                from("direct:onWhenFails-attempt")
                        .doTry()
                        .throwException(new IllegalArgumentException("original"))
                        .doCatch(IllegalArgumentException.class).onWhen(e -> {
                            throw new IllegalStateException("onWhen");
                        })
                        .to("mock:caught")
                        .end();

                from("direct:inherit")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .loadBalance().failover(2, true, true, IllegalArgumentException.class)
                            .to("direct:inherit-attempt")
                            .to("mock:second")
                        .end()
                        .to("mock:after");
                from("direct:inherit-attempt")
                        .to("mock:attempt")
                        // new exceptions on each redelivery (a shared instance would collect a suppressed one per attempt)
                        .doTry()
                        .process(e -> {
                            throw new IOException("original");
                        })
                        .doFinally()
                        .process(e -> {
                            throw new IllegalArgumentException("cleanup");
                        })
                        .end();

                from("direct:onException")
                        .onException(IllegalArgumentException.class).handled(true).to("mock:onException").end()
                        .doTry()
                            .throwException(new IOException("original"))
                        .doFinally()
                            .throwException(new IllegalArgumentException("cleanup"))
                        .end();
            }

            private void failover(String name) {
                from("direct:" + name)
                        .loadBalance().failover(IllegalArgumentException.class)
                            .to("direct:" + name + "-attempt")
                            .to("mock:second")
                        .end()
                        .to("mock:after");
            }
        };
    }

    @Test
    void doFinallyThrowsMonitoredException() throws Exception {
        assertNoFailover("direct:finally");
    }

    @Test
    void doFinallyCallsRouteThatThrowsMonitoredException() throws Exception {
        assertNoFailover("direct:finallyRoute");
    }

    @Test
    void processorAddsMonitoredExceptionAsSuppressed() throws Exception {
        assertNoFailover("direct:suppressed");
    }

    @Test
    void monitoredExceptionAsCause() throws Exception {
        assertFailover("direct:cause");
    }

    @Test
    void monitoredExceptionThrown() throws Exception {
        assertFailover("direct:monitored");
    }

    @Test
    void monitoredExceptionThrownWhenDoFinallyThrowsAnother() throws Exception {
        assertFailover("direct:monitoredFinally");
    }

    @Test
    void errorHandlerFailsWhileHandlingMonitoredException() throws Exception {
        // the onException block fails, so the attempt fails with the new exception (the original is suppressed)
        assertNoFailover("direct:errorHandlerFails", IOException.class, "audit", IllegalArgumentException.class, "original");
    }

    @Test
    void doCatchOnWhenFailsForMonitoredException() throws Exception {
        getMockEndpoint("mock:caught").expectedMessageCount(0);

        // the onWhen fails, so the attempt fails with the exception of the predicate (the original is suppressed)
        assertNoFailover("direct:onWhenFails", IllegalStateException.class, "onWhen", IllegalArgumentException.class,
                "original");
    }

    @Test
    void doFinallyThrowsMonitoredExceptionWithInheritErrorHandlerAndRoundRobin() throws Exception {
        // the inherited error handler redelivers the attempt twice, then the load balancer does not fail over
        getMockEndpoint("mock:attempt").expectedMessageCount(3);

        assertNoFailover("direct:inherit");
    }

    @Test
    void notMonitoredException() throws Exception {
        getMockEndpoint("mock:second").expectedMessageCount(0);
        getMockEndpoint("mock:after").expectedMessageCount(0);

        Exchange out = template.send("direct:notMonitored", e -> e.getMessage().setBody("Hello"));

        assertInstanceOf(IOException.class, out.getException());
        assertEquals("original", out.getException().getMessage());
        assertMockEndpointsSatisfied();
    }

    @Test
    void onExceptionDoesNotMatchSuppressedException() throws Exception {
        getMockEndpoint("mock:onException").expectedMessageCount(0);

        Exchange out = template.send("direct:onException", e -> e.getMessage().setBody("Hello"));

        assertInstanceOf(IOException.class, out.getException());
        assertEquals("original", out.getException().getMessage());
        assertMockEndpointsSatisfied();
    }

    private void assertNoFailover(String uri) throws Exception {
        assertNoFailover(uri, IOException.class, "original", IllegalArgumentException.class, "cleanup");
    }

    private void assertNoFailover(
            String uri, Class<? extends Exception> type, String message,
            Class<? extends Exception> suppressedType, String suppressedMessage)
            throws Exception {
        getMockEndpoint("mock:second").expectedMessageCount(0);
        getMockEndpoint("mock:after").expectedMessageCount(0);

        Exchange out = template.send(uri, e -> e.getMessage().setBody("Hello"));

        // the exchange fails with the exception the attempt failed with, and the listed one is only suppressed
        assertInstanceOf(type, out.getException());
        assertEquals(message, out.getException().getMessage());
        assertEquals(1, out.getException().getSuppressed().length);
        assertInstanceOf(suppressedType, out.getException().getSuppressed()[0]);
        assertEquals(suppressedMessage, out.getException().getSuppressed()[0].getMessage());
        assertMockEndpointsSatisfied();
    }

    private void assertFailover(String uri) throws Exception {
        getMockEndpoint("mock:second").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:after").expectedBodiesReceived("Hello");

        Exchange out = template.send(uri, e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
    }
}

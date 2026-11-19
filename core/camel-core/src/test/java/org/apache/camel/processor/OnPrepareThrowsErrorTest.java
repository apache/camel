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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An Error that is not a VirtualMachineError, thrown by the onPrepare processor of a split, multicast or recipient
 * list, fails only the part it prepares, as an exception does: the other parts are still sent.
 */
class OnPrepareThrowsErrorTest extends ContextTestSupport {

    @Test
    void testSplit() throws Exception {
        assertOnlyThePartFails("direct:split", new AssertionError("Forced"));
    }

    @Test
    void testStreamingSplit() throws Exception {
        assertOnlyThePartFails("direct:streamingSplit", new AssertionError("Forced"));
    }

    @Test
    void testRecipientList() throws Exception {
        assertOnlyThePartFails("direct:recipientList", new AssertionError("Forced"));
    }

    @Test
    void testSplitWithStopOnException() throws Exception {
        // the part fails as for an exception: the split stops at it, and wraps it
        getMockEndpoint("mock:part").expectedBodiesReceived("a");
        AssertionError failure = new AssertionError("Forced");

        Exchange out = template.asyncSend("direct:stopOnException", e -> {
            e.getMessage().setBody("a,b,c");
            e.setProperty("failure", failure);
        }).get(10, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
        CamelExchangeException wrapper = assertInstanceOf(CamelExchangeException.class, out.getException());
        assertTrue(wrapper.getMessage().startsWith("Multicast processing failed for number 1"), wrapper.getMessage());
        assertTrue(causedBy(wrapper, failure), "Expected the AssertionError in the causes of " + wrapper);
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    void testSplitWithAnException() throws Exception {
        // control: an exception already failed only the part
        assertOnlyThePartFails("direct:split", new IllegalArgumentException("Forced"));
    }

    private void assertOnlyThePartFails(String uri, Throwable failure) throws Exception {
        getMockEndpoint("mock:part").expectedBodiesReceived("a", "c");

        Exchange out = template.asyncSend(uri, e -> {
            e.getMessage().setBody("a,b,c");
            e.setProperty("failure", failure);
        }).get(10, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
        // the failed part fails the exchange at the end, as for an exception
        assertNotNull(out.getException());
        assertTrue(causedBy(out.getException(), failure), "Expected " + failure + " in the causes of " + out.getException());
        assertEquals(0, context.getInflightRepository().size());
    }

    private static boolean causedBy(Throwable t, Throwable cause) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c == cause) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Processor failing = e -> {
                    if ("b".equals(e.getMessage().getBody(String.class))) {
                        Throwable t = e.getProperty("failure", Throwable.class);
                        if (t instanceof Error error) {
                            throw error;
                        }
                        throw (RuntimeException) t;
                    }
                };

                from("direct:split").split(body().tokenize(",")).onPrepare(failing).to("mock:part");
                from("direct:stopOnException").split(body().tokenize(",")).stopOnException().onPrepare(failing)
                        .to("mock:part");
                from("direct:streamingSplit").split(body().tokenize(",")).streaming().onPrepare(failing).to("mock:part");
                from("direct:recipientList")
                        .recipientList(constant("direct:a,direct:b,direct:c")).onPrepare(e -> {
                            // the part sent to direct:b fails
                            String to = e.getProperty(Exchange.TO_ENDPOINT, String.class);
                            e.getMessage().setBody(to.substring(to.lastIndexOf('/') + 1));
                            failing.process(e);
                        });
                from("direct:a").to("mock:part");
                from("direct:b").to("mock:part");
                from("direct:c").to("mock:part");
            }
        };
    }
}

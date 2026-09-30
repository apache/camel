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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.InternalProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class CamelInternalProcessorAdviceTest extends ContextTestSupport {

    private final AtomicInteger stateAfter = new AtomicInteger();

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
            }
        };
    }
}

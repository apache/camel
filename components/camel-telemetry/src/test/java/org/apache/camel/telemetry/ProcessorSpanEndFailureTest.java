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
package org.apache.camel.telemetry;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.telemetry.mock.MockTracer;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A failure to end the span of a step does not replace an exception that the step has already set on the exchange.
 */
class ProcessorSpanEndFailureTest extends ExchangeTestSupport {

    MockTracer mockTracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        this.mockTracer = new FailingEndTracer();
        CamelContextAware.trySetCamelContext(mockTracer, context);
        mockTracer.init(context);
        return context;
    }

    @Test
    void endFailureKeepsTheExceptionOfTheStep() {
        Exchange out = template.send("direct:fails", e -> e.getMessage().setBody("my-body"));
        Exception e = out.getException();
        assertInstanceOf(IllegalArgumentException.class, e, "Exception: " + e);
        assertEquals("business failure", e.getMessage());
    }

    @Test
    void endFailureIsSetWhenTheStepDidNotFail() {
        Exchange out = template.send("direct:ok", e -> e.getMessage().setBody("my-body"));
        Exception e = out.getException();
        assertInstanceOf(IllegalStateException.class, e, "Exception: " + e);
        assertEquals("Cannot end the span boomOk-setHeader", e.getMessage());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fails").routeId("fails")
                        .throwException(new IllegalArgumentException("business failure")).id("boomFails");

                from("direct:ok").routeId("ok")
                        .setHeader("a", constant("1")).id("boomOk");
            }
        };
    }

    /**
     * A tracer that ends the span of a step with an id starting with boom, then fails.
     */
    private static class FailingEndTracer extends MockTracer {
        @Override
        protected void endProcessorSpan(Exchange exchange, String processorName) throws Exception {
            super.endProcessorSpan(exchange, processorName);
            if (processorName.startsWith("boom")) {
                throw new IllegalStateException("Cannot end the span " + processorName);
            }
        }
    }
}

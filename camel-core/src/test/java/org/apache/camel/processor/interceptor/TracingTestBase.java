/**
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
package org.apache.camel.processor.interceptor;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public abstract class TracingTestBase extends ContextTestSupport {
    protected List<StringBuilder> tracedMessages;
    private TraceTestProcessor processor = new TraceTestProcessor();

    protected void prepareTestTracerInOnly() {
    }

    protected void prepareTestTracerInOut() {
    }

    protected void prepareTestTracerExceptionInOut() {
    }

    protected void validateTestTracerInOnly() {
        assertEquals(3, tracedMessages.size());
        for (StringBuilder tracedMessage : tracedMessages) {
            String message = tracedMessage.toString();
            assertTrue(message.startsWith("Complete:"));
        }
    }

    protected void validateTestTracerInOut() {
        assertEquals(3, tracedMessages.size());
        for (StringBuilder tracedMessage : tracedMessages) {
            String message = tracedMessage.toString();
            assertTrue(message.startsWith("In:"));
            assertTrue(message.contains("Out:"));
        }
    }

    protected void validateTestTracerExceptionInOut() {
        assertEquals(5, tracedMessages.size());
        for (StringBuilder tracedMessage : tracedMessages) {
            String message = tracedMessage.toString();
            assertTrue(message.startsWith("In:"));
            assertTrue(message.contains("Out:"));
        }
        assertTrue(tracedMessages.get(2).toString().contains("Ex:"));
    }

    protected int getMessageCount() {
        return tracedMessages.size();
    }

    public void testTracerInOnly() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        ((Tracer) context.getDefaultTracer()).setTraceOutExchanges(false);
        result.expectedMessageCount(3);
        prepareTestTracerInOnly();

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");
        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        validateTestTracerInOnly();
    }

    public void testTracerInOut() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        ((Tracer) context.getDefaultTracer()).setTraceOutExchanges(true);
        result.expectedMessageCount(3);
        prepareTestTracerInOut();

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");
        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        validateTestTracerInOut();
    }

    public void testTracerExceptionInOut() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        ((Tracer) context.getDefaultTracer()).setTraceOutExchanges(true);
        result.expectedMessageCount(2);
        prepareTestTracerExceptionInOut();

        template.sendBody("direct:start", "Hello World");
        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }
        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        validateTestTracerExceptionInOut();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                from("direct:start")
                        .tracing()
                        .process(processor)
                        .to("mock:result");
            }
        };

    }
}

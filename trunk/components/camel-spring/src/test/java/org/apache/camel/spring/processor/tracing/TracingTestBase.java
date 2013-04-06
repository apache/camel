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
package org.apache.camel.spring.processor.tracing;

import java.util.List;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.TraceHandlerTestHandler;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spring.SpringTestSupport;

public abstract class TracingTestBase extends SpringTestSupport {

    protected List<StringBuilder> getTracedMessages() {
        Tracer tracer = this.applicationContext.getBean("tracer", Tracer.class);
        TraceHandlerTestHandler handler = (TraceHandlerTestHandler) tracer.getTraceHandlers().get(0);
        return handler.getEventMessages();
    }

    protected void prepareTestTracerExceptionInOut() {
    }

    protected void validateTestTracerExceptionInOut() {
        List<StringBuilder> tracedMessages = getTracedMessages();
        assertEquals(7, tracedMessages.size());
        for (StringBuilder tracedMessage : tracedMessages) {
            String message = tracedMessage.toString();
            assertTrue(message.startsWith("In"));
            assertTrue(message.contains("Out:"));
        }
        assertTrue(tracedMessages.get(4).toString().contains("Ex:"));
    }

    protected int getMessageCount() {
        return getTracedMessages().size();
    }

    public void testTracerExceptionInOut() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        ((Tracer) context.getDefaultTracer()).setTraceOutExchanges(true);
        result.expectedMessageCount(3);
        prepareTestTracerExceptionInOut();

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");
        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }
        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        validateTestTracerExceptionInOut();
    }
}

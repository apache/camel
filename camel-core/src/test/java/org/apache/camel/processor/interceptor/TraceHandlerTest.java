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

import java.util.LinkedList;

import org.apache.camel.CamelContext;

public class TraceHandlerTest extends TracingTestBase {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext contextLocal = super.createCamelContext();

        tracedMessages = new LinkedList<StringBuilder>();

        Tracer tracer = (Tracer) contextLocal.getDefaultTracer();
        tracer.setEnabled(true);
        tracer.setTraceExceptions(true);
        tracer.getTraceHandlers().clear();
        tracer.getTraceHandlers().add(new TraceHandlerTestHandler(tracedMessages));

        return contextLocal;
    }

    protected void validateTestTracerInOnly() {
        assertEquals(6, tracedMessages.size());
        for (StringBuilder tracedMessage : tracedMessages) {
            String message = tracedMessage.toString();
            assertTrue(message.startsWith("Complete:"));
        }
    }

    protected void validateTestTracerInOut() {
        assertEquals(6, tracedMessages.size());
        for (StringBuilder tracedMessage : tracedMessages) {
            String message = tracedMessage.toString();
            assertTrue(message.startsWith("In:"));
            assertTrue(message.contains("Out:"));
        }
    }

    @Override
    protected void prepareTestTracerExceptionInOut() {
        ((TraceHandlerTestHandler) ((Tracer) context.getDefaultTracer()).getTraceHandlers().get(0)).setTraceAllNodes(true);
    }

}

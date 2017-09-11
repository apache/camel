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
package org.apache.camel.processor.async;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.interceptor.TraceEventHandler;
import org.apache.camel.processor.interceptor.TraceInterceptor;
import org.apache.camel.processor.interceptor.Tracer;
import org.junit.Ignore;

/**
 * @version 
 */
@Deprecated
@Ignore
public class AsyncTraceHandlerTest extends ContextTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;

    public void testAsyncTraceHandler() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        assertMockEndpointsSatisfied();

        assertFalse("Should use different threads", beforeThreadName.equalsIgnoreCase(afterThreadName));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext contextLocal = super.createCamelContext();

        Tracer tracer = (Tracer) contextLocal.getDefaultTracer();
        tracer.setEnabled(true);
        tracer.getTraceHandlers().clear();
        tracer.getTraceHandlers().add(new MyTraceHandler());
        tracer.setTraceOutExchanges(true);

        return contextLocal;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                        .tracing()
                        .to("log:before")
                        .to("async:bye:camel").id("async")
                        .to("log:after")
                        .to("mock:result");
            }
        };
    }

    private static class MyTraceHandler implements TraceEventHandler {

        @Override
        public void traceExchange(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
            // noop
        }

        @Override
        public Object traceExchangeIn(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
            if (node.getId().equals("async")) {
                beforeThreadName = Thread.currentThread().getName();
            }
            return null;
        }

        @Override
        public void traceExchangeOut(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange, Object traceState) throws Exception {
            if (node.getId().equals("async")) {
                afterThreadName = Thread.currentThread().getName();
            }
        }
    }
}
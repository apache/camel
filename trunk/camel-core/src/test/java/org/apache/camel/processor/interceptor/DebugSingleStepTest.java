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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.BreakpointSupport;
import org.apache.camel.impl.DefaultDebugger;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Breakpoint;

/**
 * @version 
 */
public class DebugSingleStepTest extends ContextTestSupport {

    private List<String> logs = new ArrayList<String>();
    private Breakpoint breakpoint;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        breakpoint = new BreakpointSupport() {
            public void beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
                String body = exchange.getIn().getBody(String.class);
                logs.add("Single stepping at " + definition.getLabel() + " with body: " + body);
            }
        };
    }

    public void testDebug() throws Exception {
        context.getDebugger().addSingleStepBreakpoint(breakpoint);

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello Camel");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        assertEquals(6, logs.size());
        assertEquals("Single stepping at log:foo with body: Hello World", logs.get(0));
        assertEquals("Single stepping at log:bar with body: Hello World", logs.get(1));
        assertEquals("Single stepping at mock:result with body: Hello World", logs.get(2));
        assertEquals("Single stepping at log:foo with body: Hello Camel", logs.get(3));
        assertEquals("Single stepping at log:bar with body: Hello Camel", logs.get(4));
        assertEquals("Single stepping at mock:result with body: Hello Camel", logs.get(5));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use debugger
                context.setDebugger(new DefaultDebugger());

                from("direct:start").to("log:foo").to("log:bar").to("mock:result");
            }
        };
    }

}
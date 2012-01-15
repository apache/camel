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
import org.apache.camel.impl.ConditionSupport;
import org.apache.camel.impl.DefaultDebugger;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.Condition;

/**
 * @version 
 */
public class DebugSingleStepConditionTest extends ContextTestSupport {

    private List<String> logs = new ArrayList<String>();
    private Breakpoint breakpoint;
    private Condition beerCondition;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        breakpoint = new BreakpointSupport() {
            public void beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
                String body = exchange.getIn().getBody(String.class);
                logs.add("Single stepping at " + definition.getLabel() + " with body: " + body);
            }
        };

        beerCondition = new ConditionSupport() {
            public boolean matchProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
                return "beer".equals(exchange.getFromRouteId());
            }
        };
    }

    public void testDebug() throws Exception {
        // we only want to single step the beer route
        context.getDebugger().addSingleStepBreakpoint(breakpoint, beerCondition);

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Carlsberg");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:beer", "Carlsberg");

        assertMockEndpointsSatisfied();

        assertEquals(2, logs.size());
        assertEquals("Single stepping at log:beer with body: Carlsberg", logs.get(0));
        assertEquals("Single stepping at mock:result with body: Carlsberg", logs.get(1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use debugger
                context.setDebugger(new DefaultDebugger());

                from("direct:start").routeId("foo").to("log:foo").to("log:bar").to("mock:result");

                from("direct:beer").routeId("beer").to("log:beer").to("mock:result");
            }
        };
    }

}
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
import java.util.EventObject;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.BreakpointSupport;
import org.apache.camel.impl.ConditionSupport;
import org.apache.camel.impl.DefaultDebugger;
import org.apache.camel.management.event.AbstractExchangeEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.Condition;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class DebugExceptionEventBreakpointTest extends ContextTestSupport {

    private List<String> logs = new ArrayList<>();
    private Condition exceptionCondition;
    private Breakpoint breakpoint;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        breakpoint = new BreakpointSupport() {
            public void onEvent(Exchange exchange, EventObject event, ProcessorDefinition<?> definition) {
                AbstractExchangeEvent aee = (AbstractExchangeEvent) event;
                Exception e = aee.getExchange().getException();
                logs.add("Breakpoint at " + definition + " caused by: " + e.getClass().getSimpleName() + "[" + e.getMessage() + "]");
            }
        };

        exceptionCondition = new ConditionSupport() {
            public boolean matchEvent(Exchange exchange, EventObject event) {
                return event instanceof ExchangeFailedEvent;
            }
        };
    }

    @Test
    public void testDebug() throws Exception {
        context.getDebugger().addBreakpoint(breakpoint, exceptionCondition);

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");
        try {
            template.sendBody("direct:start", "Hello Camel");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }

        assertMockEndpointsSatisfied();

        assertEquals(1, logs.size());
        assertEquals("Breakpoint at ThrowException[java.lang.IllegalArgumentException] caused by: IllegalArgumentException[Damn]", logs.get(0));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use debugger
                context.setDebugger(new DefaultDebugger());

                from("direct:start")
                    .to("log:foo")
                    .choice()
                        .when(body().contains("Camel")).throwException(new IllegalArgumentException("Damn"))
                    .end()
                    .to("mock:result");
            }
        };
    }

}
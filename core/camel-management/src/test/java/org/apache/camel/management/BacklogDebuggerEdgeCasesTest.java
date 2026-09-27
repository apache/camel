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
package org.apache.camel.management;

import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BacklogDebugger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class BacklogDebuggerEdgeCasesTest extends ManagementTestSupport {

    private BacklogDebugger debugger;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        debugger = context.hasService(BacklogDebugger.class);
        debugger.enableDebugger();
    }

    private void awaitSuspendedAt(String nodeId) {
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(debugger.getSuspendedBreakpointNodeIds().contains(nodeId)));
    }

    @Test
    public void testRemoveAllBreakpoints() {
        debugger.addBreakpoint("foo");
        debugger.addBreakpoint("bar");
        debugger.removeAllBreakpoints();
        assertTrue(debugger.getBreakpoints().isEmpty());
    }

    @Test
    public void testUpdateBreakpointToConditional() throws Exception {
        debugger.addBreakpoint("bar");
        debugger.addConditionalBreakpoint("bar", "simple", "${body} contains 'Camel'");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("seda:start", "Hello World");
        mock.assertIsSatisfied();
    }

    @Test
    public void testBreakpointConditionThatFailsDoesNotFailTheExchange() throws Exception {
        debugger.addConditionalBreakpoint("bar", "simple", "${body.noSuchMethod()}");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("seda:start", "Hello World");
        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getException());
    }

    @Test
    public void testStepOverWhenNotSingleStepping() throws Exception {
        debugger.addBreakpoint("foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("seda:start", "Hello World");
        awaitSuspendedAt("foo");
        debugger.stepOver();
        mock.assertIsSatisfied();

        // the breakpoint is still in use for the next message
        template.sendBody("seda:start", "Bye World");
        awaitSuspendedAt("foo");
        debugger.resumeBreakpoint("foo");
    }

    @Test
    public void testSetExchangePropertyKeepsItsType() throws Exception {
        debugger.addBreakpoint("foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("seda:start", "Hello World");
        awaitSuspendedAt("foo");
        debugger.setExchangePropertyOnBreakpoint("foo", "num", "2");
        debugger.resumeBreakpoint("foo");
        mock.assertIsSatisfied();

        Object num = mock.getReceivedExchanges().get(0).getProperty("num");
        assertInstanceOf(Integer.class, num);
        assertEquals(2, num);
    }

    @Test
    public void testSuspendModeWaitsForAttach() throws Exception {
        debugger.setSuspendMode(true);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(300);
        template.sendBody("seda:start", "Hello World");
        mock.assertIsSatisfied();

        resetMocks();
        mock.expectedMessageCount(1);
        debugger.attach();
        mock.assertIsSatisfied();
    }

    @Test
    public void testEvaluateExpressionWhenNotSuspended() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        Object answer = mbeanServer.invoke(on, "evaluateExpressionAtBreakpoint",
                new Object[] { "foo", "simple", "${body}" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.String" });
        assertNull(answer);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setDebugging(true);
                context.setMessageHistory(true);

                from("seda:start")
                        .setProperty("num", constant(1))
                        .to("log:foo").id("foo")
                        .to("log:bar").id("bar")
                        .to("mock:result");
            }
        };
    }
}

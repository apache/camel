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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.debugger.BacklogDebugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class BacklogDebuggerTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebugger() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[] { "bar", false },
                new String[] { "java.lang.String", "boolean" });
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("Hello World"), "Should contain our body");
        assertTrue(xml.contains("<toNode>bar</toNode>"), "Should contain bar node");

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerUpdateBodyExchangePropertyAndHeader() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        // update body and header
        mbeanServer.invoke(on, "setMessageBodyOnBreakpoint", new Object[] { "foo", "Changed body" },
                new String[] { "java.lang.String", "java.lang.Object" });
        mbeanServer.invoke(on, "setMessageHeaderOnBreakpoint", new Object[] { "foo", "beer", "Carlsberg" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.Object" });
        mbeanServer.invoke(on, "setExchangePropertyOnBreakpoint", new Object[] { "foo", "food", "Bratwurst" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.Object" });

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[] { "bar", true },
                new String[] { "java.lang.String", "boolean" });
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("Changed body"), "Should contain our body");
        assertTrue(xml.contains("<toNode>bar</toNode>"), "Should contain bar node");
        assertTrue(xml.contains("<header key=\"beer\" type=\"java.lang.String\">Carlsberg</header>"),
                "Should contain our added header");
        assertTrue(xml.contains("<exchangeProperty name=\"food\" type=\"java.lang.String\">Bratwurst</exchangeProperty>"),
                "Should contain our added exchange property");

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerUpdateBodyExchangePropertyAndHeaderType() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        // update body and header
        mbeanServer.invoke(on, "setMessageBodyOnBreakpoint", new Object[] { "foo", "444", "java.lang.Integer" },
                new String[] { "java.lang.String", "java.lang.Object", "java.lang.String" });
        mbeanServer.invoke(on, "setMessageHeaderOnBreakpoint", new Object[] { "foo", "beer", "123", "java.lang.Integer" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.Object", "java.lang.String" });
        mbeanServer.invoke(on, "setExchangePropertyOnBreakpoint", new Object[] { "foo", "food", "987", "java.lang.Integer" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.Object", "java.lang.String" });

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[] { "bar", true },
                new String[] { "java.lang.String", "boolean" });
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("444"), "Should contain our body");
        assertTrue(xml.contains("<toNode>bar</toNode>"), "Should contain bar node");
        assertTrue(xml.contains("<header key=\"beer\" type=\"java.lang.Integer\">123</header>"),
                "Should contain our added header");
        assertTrue(xml.contains("<exchangeProperty name=\"food\" type=\"java.lang.Integer\">987</exchangeProperty>"),
                "Should contain our added exchange property");

        // update body and header
        mbeanServer.invoke(on, "setMessageBodyOnBreakpoint", new Object[] { "bar", "555", "java.lang.Integer" },
                new String[] { "java.lang.String", "java.lang.Object", "java.lang.String" });
        mbeanServer.invoke(on, "setMessageHeaderOnBreakpoint", new Object[] { "bar", "wine", "456", "java.lang.Integer" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.Object", "java.lang.String" });
        mbeanServer.invoke(on, "setExchangePropertyOnBreakpoint", new Object[] { "bar", "drink", "798", "java.lang.Integer" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.Object", "java.lang.String" });

        // the message should be updated
        xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[] { "bar", true },
                new String[] { "java.lang.String", "boolean" });
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("555"), "Should contain our body");
        assertTrue(xml.contains("<toNode>bar</toNode>"), "Should contain bar node");
        assertTrue(xml.contains("<header key=\"wine\" type=\"java.lang.Integer\">456</header>"),
                "Should contain our added header");
        assertTrue(xml.contains("<exchangeProperty name=\"drink\" type=\"java.lang.Integer\">798</exchangeProperty>"),
                "Should contain our added exchange property");

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerRemoveBodyAndHeader() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        // update body and header
        mbeanServer.invoke(on, "removeMessageBodyOnBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });
        mbeanServer.invoke(on, "removeMessageHeaderOnBreakpoint", new Object[] { "foo", "beer" },
                new String[] { "java.lang.String", "java.lang.String" });
        mbeanServer.invoke(on, "removeExchangePropertyOnBreakpoint", new Object[] { "foo", "food" },
                new String[] { "java.lang.String", "java.lang.String" });

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[] { "bar", true },
                new String[] { "java.lang.String", "boolean" });
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<body>[Body is null]</body>"), "Should not contain our body");
        assertTrue(xml.contains("<toNode>bar</toNode>"), "Should contain bar node");
        assertFalse(xml.contains("<header"), "Should not contain any headers");
        assertFalse(xml.contains("<exchangeProperty name=\"food\""), "Should not contain exchange property 'food'");

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerSuspendOnlyOneAtBreakpoint() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        // only one of them is suspended
        template.sendBody("seda:start", "Hello World");
        template.sendBody("seda:start", "Hello Camel");
        template.sendBody("seda:start", "Hello Earth");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("bar", nodes.iterator().next());

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[] { "bar", false },
                new String[] { "java.lang.String", "boolean" });
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<toNode>bar</toNode>"), "Should contain bar node");

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerConditional() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // validate conditional breakpoint (mistake on purpose)
        Object out = mbeanServer.invoke(on, "validateConditionalBreakpoint",
                new Object[] { "unknown", "${body contains 'Camel'" }, new String[] { "java.lang.String", "java.lang.String" });
        assertEquals("No language could be found for: unknown", out);

        // validate conditional breakpoint (mistake on purpose)
        out = mbeanServer.invoke(on, "validateConditionalBreakpoint", new Object[] { "simple", "${body contains 'Camel'" },
                new String[] { "java.lang.String", "java.lang.String" });
        assertNotNull(out);
        assertTrue(out.toString().startsWith("Invalid syntax ${body contains 'Camel'"));

        // validate conditional breakpoint (is correct)
        out = mbeanServer.invoke(on, "validateConditionalBreakpoint", new Object[] { "simple", "${body} contains 'Camel'" },
                new String[] { "java.lang.String", "java.lang.String" });
        assertNull(out);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addConditionalBreakpoint", new Object[] { "bar", "simple", "${body} contains 'Camel'" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add not breakpoint at bar as condition did not match
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());

        resetMocks();

        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("bar", nodes.iterator().next());

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[] { "bar", false },
                new String[] { "java.lang.String", "boolean" });
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("Hello Camel"), "Should contain our body");
        assertTrue(xml.contains("<toNode>bar</toNode>"), "Should contain bar node");

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerStep() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        Boolean stepMode = (Boolean) mbeanServer.getAttribute(on, "SingleStepMode");
        assertEquals(Boolean.FALSE, stepMode, "Should not be in step mode");

        // step breakpoint
        mbeanServer.invoke(on, "stepBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });

        // then at bar now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "step", null, null);

        // then at transform now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("transform", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "step", null, null);

        // then at cheese now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("cheese", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "step", null, null);

        // then at result now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("result", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "step", null, null);

        // then the exchange is completed
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(0, suspended.size());
        });

        // should no longer be in step mode
        stepMode = (Boolean) mbeanServer.getAttribute(on, "SingleStepMode");
        assertEquals(Boolean.FALSE, stepMode, "Should not be in step mode");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerStepCurrentNode() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        Boolean stepMode = (Boolean) mbeanServer.getAttribute(on, "SingleStepMode");
        assertEquals(Boolean.FALSE, stepMode, "Should not be in step mode");

        // step breakpoint
        mbeanServer.invoke(on, "stepBreakpoint", new Object[] { "foo" }, new String[] { "java.lang.String" });

        // then at bar now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "stepBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        // then at transform now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("transform", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "stepBreakpoint", new Object[] { "transform" }, new String[] { "java.lang.String" });

        // then at cheese now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("cheese", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "stepBreakpoint", new Object[] { "cheese" }, new String[] { "java.lang.String" });

        // then at result now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("result", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "stepBreakpoint", new Object[] { "result" }, new String[] { "java.lang.String" });

        // then the exchange is completed
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(0, suspended.size());
        });

        // should no longer be in step mode
        stepMode = (Boolean) mbeanServer.getAttribute(on, "SingleStepMode");
        assertEquals(Boolean.FALSE, stepMode, "Should not be in step mode");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerExchangeProperties() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // there should be an exchange property 'myProperty'
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[] { "bar", true },
                new String[] { "java.lang.String", "boolean" });
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<exchangeProperty name=\"myProperty\" type=\"java.lang.String\">myValue</exchangeProperty>"),
                "Should contain myProperty");

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerEvaluateExpression() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // evaluate expression, should return true
        Object response = mbeanServer.invoke(on, "evaluateExpressionAtBreakpoint",
                new Object[] { "bar", "simple", "${body} contains 'Hello'", "java.lang.Boolean" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String" });

        assertNotNull(response);
        log.info(response.toString());

        assertTrue(response.getClass().isAssignableFrom(Boolean.class));
        assertTrue((Boolean) response);

        // evaluate another expression, should return value
        response = mbeanServer.invoke(on, "evaluateExpressionAtBreakpoint",
                new Object[] { "bar", "simple", "${exchangeProperty.myProperty}", "java.lang.String" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String" });

        assertNotNull(response);
        log.info(response.toString());

        assertTrue(response.getClass().isAssignableFrom(String.class));
        assertEquals("myValue", response);

        // same as before but assume string by default
        response = mbeanServer.invoke(on, "evaluateExpressionAtBreakpoint",
                new Object[] { "bar", "simple", "${exchangeProperty.myProperty}" },
                new String[] { "java.lang.String", "java.lang.String", "java.lang.String" });

        assertNotNull(response);
        log.info(response.toString());

        assertTrue(response.getClass().isAssignableFrom(String.class));
        assertEquals("myValue", response);

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogDebuggerMessageHistory() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.FALSE, enabled, "Should not be enabled");

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        Object response = mbeanServer.invoke(on, "messageHistoryOnBreakpointAsXml",
                new Object[] { "bar" },
                new String[] { "java.lang.String" });

        assertNotNull(response);
        assertTrue(response.getClass().isAssignableFrom(String.class));
        String history = (String) response;
        int count = (history.split("messageHistoryEntry", -1).length) - 1;
        assertEquals(4, count);
        assertTrue(history.contains("processor=\"from[seda://start?concurrentConsumers=2]\""));
        assertTrue(history.contains("routeId=\"route1\""));
        assertTrue(history.contains("processorId=\"route1\""));
        assertTrue(history.contains("location=\""));
        assertTrue(history.contains("elapsed=\""));

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[] { "bar" }, new String[] { "java.lang.String" });

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "suspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    /**
     * Ensure that the suspend mode works as expected when it is set using an environment variable.
     */
    @Test
    @SetEnvironmentVariable(key = BacklogDebugger.SUSPEND_MODE_ENV_VAR_NAME, value = "true")
    public void testSuspendModeConfiguredWithEnvVariable() throws Exception {
        testSuspendMode();
    }

    /**
     * Ensure that the suspend mode works as expected when it is set using a system property.
     */
    @Test
    @SetSystemProperty(key = BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME, value = "true")
    public void testSuspendModeConfiguredWithSystemProperty() throws Exception {
        testSuspendMode();
    }

    /**
     * Ensure that the suspend mode works as expected when it is configured by relying on the precedence of the env
     * variable over the system property.
     */
    @Test
    @SetEnvironmentVariable(key = BacklogDebugger.SUSPEND_MODE_ENV_VAR_NAME, value = "true")
    @SetSystemProperty(key = BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME, value = "false")
    public void testSuspendModeConfiguredWithBoth() throws Exception {
        testSuspendMode();
    }

    private void testSuspendMode() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");
        assertMockEndpointsSatisfied();

        resetMocks();

        // Attach debugger
        mbeanServer.invoke(on, "attach", null, null);

        mock.expectedMessageCount(1);

        resetMocks();

        // Detach debugger
        mbeanServer.invoke(on, "detach", null, null);

        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World 2");
        assertMockEndpointsSatisfied();

        resetMocks();

        // Attach debugger
        mbeanServer.invoke(on, "attach", null, null);

        mock.expectedMessageCount(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setUseBreadcrumb(false);
                context.setDebugging(true);
                context.setMessageHistory(true);

                from("seda:start?concurrentConsumers=2")
                        .setProperty("myProperty", constant("myValue")).id("setProp")
                        .to("log:foo").id("foo")
                        .to("log:bar").id("bar")
                        .transform().constant("Bye World").id("transform")
                        .to("log:cheese?showExchangeId=true").id("cheese")
                        .to("mock:result").id("result");
            }
        };
    }

}

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
package org.apache.camel.management;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.awaitility.Awaitility.await;

public class BacklogDebuggerTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    public void testBacklogDebugger() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should be enabled", Boolean.TRUE, enabled);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[]{"bar"}, new String[]{"java.lang.String"});
        assertNotNull(xml);
        log.info(xml);

        assertTrue("Should contain our body", xml.contains("Hello World"));
        assertTrue("Should contain bar node", xml.contains("<toNode>bar</toNode>"));

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    public void testBacklogDebuggerUpdateBodyAndHeader() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should be enabled", Boolean.TRUE, enabled);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        // update body and header
        mbeanServer.invoke(on, "setMessageBodyOnBreakpoint", new Object[]{"foo", "Changed body"}, new String[]{"java.lang.String", "java.lang.Object"});
        mbeanServer.invoke(on, "setMessageHeaderOnBreakpoint", new Object[]{"foo", "beer", "Carlsberg"}, new String[]{"java.lang.String", "java.lang.String", "java.lang.Object"});

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[]{"bar"}, new String[]{"java.lang.String"});
        assertNotNull(xml);
        log.info(xml);

        assertTrue("Should contain our body", xml.contains("Changed body"));
        assertTrue("Should contain bar node", xml.contains("<toNode>bar</toNode>"));
        assertTrue("Should contain our added header", xml.contains("<header key=\"beer\" type=\"java.lang.String\">Carlsberg</header>"));

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    public void testBacklogDebuggerUpdateBodyAndHeaderType() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should be enabled", Boolean.TRUE, enabled);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        // update body and header
        mbeanServer.invoke(on, "setMessageBodyOnBreakpoint", new Object[]{"foo", "444", "java.lang.Integer"},
                new String[]{"java.lang.String", "java.lang.Object", "java.lang.String"});
        mbeanServer.invoke(on, "setMessageHeaderOnBreakpoint", new Object[]{"foo", "beer", "123", "java.lang.Integer"},
                new String[]{"java.lang.String", "java.lang.String", "java.lang.Object", "java.lang.String"});

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[]{"bar"}, new String[]{"java.lang.String"});
        assertNotNull(xml);
        log.info(xml);

        assertTrue("Should contain our body", xml.contains("444"));
        assertTrue("Should contain bar node", xml.contains("<toNode>bar</toNode>"));
        assertTrue("Should contain our added header", xml.contains("<header key=\"beer\" type=\"java.lang.Integer\">123</header>"));

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    public void testBacklogDebuggerRemoveBodyAndHeader() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should be enabled", Boolean.TRUE, enabled);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        // update body and header
        mbeanServer.invoke(on, "removeMessageBodyOnBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});
        mbeanServer.invoke(on, "removeMessageHeaderOnBreakpoint", new Object[]{"foo", "beer"}, new String[]{"java.lang.String", "java.lang.String"});

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});

        // wait for breakpoint at bar
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[]{"bar"}, new String[]{"java.lang.String"});
        assertNotNull(xml);
        log.info(xml);

        assertTrue("Should not contain our body", xml.contains("<body>[Body is null]</body>"));
        assertTrue("Should contain bar node", xml.contains("<toNode>bar</toNode>"));
        assertFalse("Should not contain any headers", xml.contains("<header"));

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    public void testBacklogDebuggerSuspendOnlyOneAtBreakpoint() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should be enabled", Boolean.TRUE, enabled);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        // only one of them is suspended
        template.sendBody("seda:start", "Hello World");
        template.sendBody("seda:start", "Hello Camel");
        template.sendBody("seda:start", "Hello Earth");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("bar", nodes.iterator().next());

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[]{"bar"}, new String[]{"java.lang.String"});
        assertNotNull(xml);
        log.info(xml);

        assertTrue("Should contain bar node", xml.contains("<toNode>bar</toNode>"));

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    public void testBacklogDebuggerConditional() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should be enabled", Boolean.TRUE, enabled);

        // validate conditional breakpoint (mistake on purpose)
        Object out = mbeanServer.invoke(on, "validateConditionalBreakpoint", new Object[]{"unknown", "${body contains 'Camel'"}, new String[]{"java.lang.String", "java.lang.String"});
        assertEquals("No language could be found for: unknown", out);

        // validate conditional breakpoint (mistake on purpose)
        out = mbeanServer.invoke(on, "validateConditionalBreakpoint", new Object[]{"simple", "${body contains 'Camel'"}, new String[]{"java.lang.String", "java.lang.String"});
        assertNotNull(out);
        assertTrue(out.toString().startsWith("Invalid syntax ${body contains 'Camel'"));

        // validate conditional breakpoint (is correct)
        out = mbeanServer.invoke(on, "validateConditionalBreakpoint", new Object[]{"simple", "${body} contains 'Camel'"}, new String[]{"java.lang.String", "java.lang.String"});
        assertNull(out);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addConditionalBreakpoint", new Object[]{"bar", "simple", "${body} contains 'Camel'"}, new String[]{"java.lang.String", "java.lang.String", "java.lang.String"});

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add not breakpoint at bar as condition did not match
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());

        resetMocks();

        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("bar", nodes.iterator().next());

        // the message should be ours
        String xml = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml", new Object[]{"bar"}, new String[]{"java.lang.String"});
        assertNotNull(xml);
        log.info(xml);

        assertTrue("Should contain our body", xml.contains("Hello Camel"));
        assertTrue("Should contain bar node", xml.contains("<toNode>bar</toNode>"));

        resetMocks();
        mock.expectedMessageCount(1);

        // resume breakpoint
        mbeanServer.invoke(on, "resumeBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        assertMockEndpointsSatisfied();

        // and no suspended anymore
        nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(0, nodes.size());
    }

    @SuppressWarnings("unchecked")
    public void testBacklogDebuggerStep() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should be enabled", Boolean.TRUE, enabled);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        Boolean stepMode = (Boolean) mbeanServer.getAttribute(on, "SingleStepMode");
        assertEquals("Should not be in step mode", Boolean.FALSE, stepMode);

        // step breakpoint
        mbeanServer.invoke(on, "stepBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});

        // then at bar now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "step", null, null);

        // then at transform now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("transform", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "step", null, null);

        // then at cheese now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("cheese", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "step", null, null);

        // then at result now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("result", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "step", null, null);

        // then the exchange is completed
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(0, suspended.size());
        });

        // should no longer be in step mode
        stepMode = (Boolean) mbeanServer.getAttribute(on, "SingleStepMode");
        assertEquals("Should not be in step mode", Boolean.FALSE, stepMode);
    }

    @SuppressWarnings("unchecked")
    public void testBacklogDebuggerStepCurrentNode() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogDebugger");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        // enable debugger
        mbeanServer.invoke(on, "enableDebugger", null, null);

        enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should be enabled", Boolean.TRUE, enabled);

        // add breakpoint at bar
        mbeanServer.invoke(on, "addBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // add breakpoint at bar
        Set<String> nodes = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
        assertNotNull(nodes);
        assertEquals(1, nodes.size());
        assertEquals("foo", nodes.iterator().next());

        Boolean stepMode = (Boolean) mbeanServer.getAttribute(on, "SingleStepMode");
        assertEquals("Should not be in step mode", Boolean.FALSE, stepMode);

        // step breakpoint
        mbeanServer.invoke(on, "stepBreakpoint", new Object[]{"foo"}, new String[]{"java.lang.String"});

        // then at bar now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("bar", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "stepBreakpoint", new Object[]{"bar"}, new String[]{"java.lang.String"});

        // then at transform now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("transform", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "stepBreakpoint", new Object[]{"transform"}, new String[]{"java.lang.String"});

        // then at cheese now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("cheese", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "stepBreakpoint", new Object[]{"cheese"}, new String[]{"java.lang.String"});

        // then at result now
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(1, suspended.size());
            assertEquals("result", suspended.iterator().next());
        });

        // step
        mbeanServer.invoke(on, "stepBreakpoint", new Object[]{"result"}, new String[]{"java.lang.String"});

        // then the exchange is completed
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<String> suspended = (Set<String>) mbeanServer.invoke(on, "getSuspendedBreakpointNodeIds", null, null);
            assertNotNull(suspended);
            assertEquals(0, suspended.size());
        });

        // should no longer be in step mode
        stepMode = (Boolean) mbeanServer.getAttribute(on, "SingleStepMode");
        assertEquals("Should not be in step mode", Boolean.FALSE, stepMode);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setUseBreadcrumb(false);

                from("seda:start?concurrentConsumers=2")
                        .to("log:foo").id("foo")
                        .to("log:bar").id("bar")
                        .transform().constant("Bye World").id("transform")
                        .to("log:cheese?showExchangeId=true").id("cheese")
                        .to("mock:result").id("result");
            }
        };
    }

}

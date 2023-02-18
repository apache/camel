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

import java.util.List;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class BacklogTracerTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerEventMessage() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        Integer size = (Integer) mbeanServer.getAttribute(on, "BacklogSize");
        assertEquals(1000, size.intValue(), "Should be 1000");

        Boolean removeOnDump = (Boolean) mbeanServer.getAttribute(on, "RemoveOnDump");
        assertEquals(Boolean.TRUE, removeOnDump);

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = getMockEndpoint("mock:foo").getReceivedExchanges();

        List<BacklogTracerEventMessage> events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[] { "foo" }, new String[] { "java.lang.String" });

        assertNotNull(events);
        assertEquals(2, events.size());

        BacklogTracerEventMessage event1 = events.get(0);
        assertEquals("foo", event1.getToNode());
        assertEquals("    <message exchangeId=\"" + exchanges.get(0).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <body type=\"java.lang.String\">Hello World</body>\n"
                     + "    </message>",
                event1.getMessageAsXml());

        BacklogTracerEventMessage event2 = events.get(1);
        assertEquals("foo", event2.getToNode());
        assertEquals("    <message exchangeId=\"" + exchanges.get(1).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <body type=\"java.lang.String\">Bye World</body>\n"
                     + "    </message>",
                event2.getMessageAsXml());
    }

    @Test
    public void testBacklogTracerEventMessageAsXml() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        Integer size = (Integer) mbeanServer.getAttribute(on, "BacklogSize");
        assertEquals(1000, size.intValue(), "Should be 1000");

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        String events = (String) mbeanServer.invoke(on, "dumpTracedMessagesAsXml",
                new Object[] { "foo" }, new String[] { "java.lang.String" });

        assertNotNull(events);
        log.info(events);

        // should be valid XML
        Document dom = context.getTypeConverter().convertTo(Document.class, events);
        assertNotNull(dom);

        NodeList list = dom.getElementsByTagName("backlogTracerEventMessage");
        assertEquals(2, list.getLength());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerEventMessageDumpAll() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        List<Exchange> fooExchanges = getMockEndpoint("mock:foo").getReceivedExchanges();
        List<Exchange> barExchanges = getMockEndpoint("mock:bar").getReceivedExchanges();

        List<BacklogTracerEventMessage> events
                = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);

        assertNotNull(events);
        assertEquals(8, events.size());

        // first and last events
        assertTrue(events.get(0).isFirst());
        assertTrue(events.get(7).isLast());

        BacklogTracerEventMessage event0 = events.get(0);
        assertEquals("route1", event0.getRouteId());
        assertEquals(null, event0.getToNode());
        assertEquals("    <message exchangeId=\"" + fooExchanges.get(0).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <body type=\"java.lang.String\">Hello World</body>\n"
                     + "    </message>",
                event0.getMessageAsXml());

        BacklogTracerEventMessage event1 = events.get(1);
        assertEquals("route1", event1.getRouteId());
        assertEquals("foo", event1.getToNode());
        assertEquals("    <message exchangeId=\"" + fooExchanges.get(0).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <body type=\"java.lang.String\">Hello World</body>\n"
                     + "    </message>",
                event1.getMessageAsXml());

        BacklogTracerEventMessage event2 = events.get(2);
        assertEquals("route1", event2.getRouteId());
        assertEquals("bar", event2.getToNode());
        assertEquals("    <message exchangeId=\"" + barExchanges.get(0).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <body type=\"java.lang.String\">Hello World</body>\n"
                     + "    </message>",
                event2.getMessageAsXml());

        BacklogTracerEventMessage event3 = events.get(4);
        assertEquals("route1", event3.getRouteId());
        assertEquals(null, event3.getToNode());
        assertEquals("    <message exchangeId=\"" + fooExchanges.get(1).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <body type=\"java.lang.String\">Bye World</body>\n"
                     + "    </message>",
                event3.getMessageAsXml());

        BacklogTracerEventMessage event4 = events.get(5);
        assertEquals("route1", event4.getRouteId());
        assertEquals("foo", event4.getToNode());
        assertEquals("    <message exchangeId=\"" + fooExchanges.get(1).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <body type=\"java.lang.String\">Bye World</body>\n"
                     + "    </message>",
                event3.getMessageAsXml());

        BacklogTracerEventMessage event5 = events.get(6);
        assertEquals("route1", event5.getRouteId());
        assertEquals("bar", event5.getToNode());
        assertEquals("    <message exchangeId=\"" + barExchanges.get(1).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <body type=\"java.lang.String\">Bye World</body>\n"
                     + "    </message>",
                event4.getMessageAsXml());
    }

    @Test
    public void testBacklogTracerEventMessageDumpAllAsXml() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should not be enabled");

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        String events = (String) mbeanServer.invoke(on, "dumpAllTracedMessagesAsXml", null, null);

        assertNotNull(events);
        log.info(events);

        // should be valid XML
        Document dom = context.getTypeConverter().convertTo(Document.class, events);
        assertNotNull(dom);

        NodeList list = dom.getElementsByTagName("backlogTracerEventMessage");
        assertEquals(8, list.getLength());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerNotRemoveOnDump() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean removeOnDump = (Boolean) mbeanServer.getAttribute(on, "RemoveOnDump");
        assertEquals(Boolean.TRUE, removeOnDump);
        // disable it
        mbeanServer.setAttribute(on, new Attribute("RemoveOnDump", Boolean.FALSE));

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should not be enabled");

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        List<BacklogTracerEventMessage> events
                = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);

        assertNotNull(events);
        assertEquals(8, events.size());

        // and if we get again they are still there
        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);
        assertNotNull(events);
        assertEquals(8, events.size());

        // send in another message
        resetMocks();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hi World");

        assertMockEndpointsSatisfied();

        // and now we should have 4 more messages
        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);
        assertNotNull(events);
        assertEquals(12, events.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerNotRemoveOnDumpPattern() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean removeOnDump = (Boolean) mbeanServer.getAttribute(on, "RemoveOnDump");
        assertEquals(Boolean.TRUE, removeOnDump);
        // disable it
        mbeanServer.setAttribute(on, new Attribute("RemoveOnDump", Boolean.FALSE));

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        List<BacklogTracerEventMessage> events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[] { "foo" }, new String[] { "java.lang.String" });

        assertNotNull(events);
        assertEquals(2, events.size());

        // and if we get again they are still there
        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[] { "foo" }, new String[] { "java.lang.String" });
        assertEquals(2, events.size());

        // send in another message
        resetMocks();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hi World");

        assertMockEndpointsSatisfied();

        // and now we should have 3 more messages
        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[] { "foo" }, new String[] { "java.lang.String" });
        assertNotNull(events);
        assertEquals(3, events.size());

        // and bar should also have 3 traced messages
        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[] { "bar" }, new String[] { "java.lang.String" });
        assertNotNull(events);
        assertEquals(3, events.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerNotRemoveOverflow() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean removeOnDump = (Boolean) mbeanServer.getAttribute(on, "RemoveOnDump");
        assertEquals(Boolean.TRUE, removeOnDump);
        // disable it
        mbeanServer.setAttribute(on, new Attribute("RemoveOnDump", Boolean.FALSE));

        Integer size = (Integer) mbeanServer.getAttribute(on, "BacklogSize");
        assertEquals(1000, size.intValue(), "Should be 1000");
        // change size to 2 x 10 (as we need for first as well)
        mbeanServer.setAttribute(on, new Attribute("BacklogSize", 20));
        // set the pattern to match only foo
        mbeanServer.setAttribute(on, new Attribute("TracePattern", "foo"));

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should not be enabled");

        getMockEndpoint("mock:foo").expectedMessageCount(10);
        getMockEndpoint("mock:bar").expectedMessageCount(10);

        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "###" + i + "###");
        }

        assertMockEndpointsSatisfied();

        List<BacklogTracerEventMessage> events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[] { "foo" }, new String[] { "java.lang.String" });
        assertEquals(7, events.size());

        // the first should be 3 and the last 9
        String xml = events.get(0).getMessageAsXml();
        assertTrue(xml.contains("###3###"));
        xml = events.get(6).getMessageAsXml();
        assertTrue(xml.contains("###9###"));

        // send in another message
        template.sendBody("direct:start", "###" + 10 + "###");

        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[] { "foo" }, new String[] { "java.lang.String" });
        assertEquals(7, events.size());

        // and we are shifted one now
        xml = events.get(0).getMessageAsXml();
        assertTrue(xml.contains("###4###"));
        xml = events.get(6).getMessageAsXml();
        assertTrue(xml.contains("###10###"));

        // send in 4 messages
        template.sendBody("direct:start", "###" + 11 + "###");
        template.sendBody("direct:start", "###" + 12 + "###");
        template.sendBody("direct:start", "###" + 13 + "###");
        template.sendBody("direct:start", "###" + 14 + "###");

        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[] { "foo" }, new String[] { "java.lang.String" });
        assertEquals(7, events.size());

        // and we are shifted +4 now
        xml = events.get(0).getMessageAsXml();
        assertTrue(xml.contains("###8###"));
        xml = events.get(6).getMessageAsXml();
        assertTrue(xml.contains("###14###"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setUseBreadcrumb(false);
                context.setBacklogTracing(true);

                from("direct:start")
                        .to("mock:foo").id("foo")
                        .to("mock:bar").id("bar");

            }
        };
    }

}

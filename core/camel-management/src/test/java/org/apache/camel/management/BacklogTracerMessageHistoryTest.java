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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class BacklogTracerMessageHistoryTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerReplay() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        Integer size = (Integer) mbeanServer.getAttribute(on, "BacklogSize");
        assertEquals(100, size.intValue(), "Should be 100");

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
                     + "      <exchangeProperties>\n"
                     + "        <exchangeProperty key=\"CamelToEndpoint\" type=\"java.lang.String\">direct://start</exchangeProperty>\n"
                     + "      </exchangeProperties>\n"
                     + "      <body type=\"java.lang.String\">Hello World</body>\n"
                     + "    </message>",
                event1.getMessageAsXml());

        BacklogTracerEventMessage event2 = events.get(1);
        assertEquals("foo", event2.getToNode());
        assertEquals("    <message exchangeId=\"" + exchanges.get(1).getExchangeId()
                     + "\" exchangePattern=\"InOnly\" exchangeType=\"org.apache.camel.support.DefaultExchange\" messageType=\"org.apache.camel.support.DefaultMessage\">\n"
                     + "      <exchangeProperties>\n"
                     + "        <exchangeProperty key=\"CamelToEndpoint\" type=\"java.lang.String\">direct://start</exchangeProperty>\n"
                     + "      </exchangeProperties>\n"
                     + "      <body type=\"java.lang.String\">Bye World</body>\n"
                     + "    </message>",
                event2.getMessageAsXml());

        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpLatestMessageHistory", null, null);
        assertNotNull(events);
        assertEquals(8, events.size());

        assertTrue(events.get(0).isFirst());
        assertEquals("direct://start", events.get(0).getEndpointUri());
        assertEquals("from", events.get(0).getToNodeShortName());
        assertEquals("myRoute", events.get(0).getRouteId());
        assertEquals("myRoute", events.get(0).getFromRouteId());
        assertFalse(events.get(1).isFirst());
        assertFalse(events.get(1).isLast());
        assertEquals("foo", events.get(1).getToNode());
        assertEquals("to", events.get(1).getToNodeShortName());
        assertEquals("to[mock:foo]", events.get(1).getToNodeLabel());
        assertEquals("myRoute", events.get(1).getRouteId());
        assertEquals("myRoute", events.get(1).getFromRouteId());

        // sub-route
        assertTrue(events.get(3).isFirst());
        assertFalse(events.get(3).isLast());
        assertEquals("direct://sub", events.get(3).getEndpointUri());
        assertEquals("from", events.get(3).getToNodeShortName());
        assertEquals("mySub", events.get(3).getRouteId());
        assertEquals("myRoute", events.get(3).getFromRouteId());
        assertFalse(events.get(4).isFirst());
        assertFalse(events.get(4).isLast());
        assertEquals("sub", events.get(4).getToNode());
        assertEquals("to", events.get(4).getToNodeShortName());
        assertEquals("to[mock:sub]", events.get(4).getToNodeLabel());
        assertEquals("mySub", events.get(4).getRouteId());
        assertEquals("myRoute", events.get(4).getFromRouteId());
        assertFalse(events.get(5).isFirst());
        assertTrue(events.get(5).isLast());
        assertEquals("direct://sub", events.get(5).getEndpointUri());
        assertEquals("from", events.get(5).getToNodeShortName());
        assertEquals("mySub", events.get(5).getRouteId());
        assertEquals("myRoute", events.get(5).getFromRouteId());

        assertFalse(events.get(6).isFirst());
        assertFalse(events.get(6).isLast());
        assertEquals("bar", events.get(6).getToNode());
        assertEquals("to", events.get(6).getToNodeShortName());
        assertEquals("to[mock:bar]", events.get(6).getToNodeLabel());
        assertEquals("myRoute", events.get(6).getRouteId());
        assertEquals("myRoute", events.get(6).getFromRouteId());
        assertTrue(events.get(7).isLast());
        assertEquals("direct://start", events.get(7).getEndpointUri());
        assertEquals("from1", events.get(7).getToNode());
        assertEquals("myRoute", events.get(7).getRouteId());
        assertEquals("myRoute", events.get(7).getFromRouteId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(false);
                context.setBacklogTracing(true);
                context.setMessageHistory(true);

                from("direct:start").routeId("myRoute")
                        .to("mock:foo").id("foo")
                        .to("direct:sub")
                        .to("mock:bar").id("bar");

                from("direct:sub").routeId("mySub")
                        .to("mock:sub").id("sub");
            }
        };
    }

}

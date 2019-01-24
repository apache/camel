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

import java.util.List;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class BacklogTracerFilterTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerFilter() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogTracer");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        Integer size = (Integer) mbeanServer.getAttribute(on, "BacklogSize");
        assertEquals("Should be 1000", 1000, size.intValue());

        // set the filter to match only if header foo exists
        mbeanServer.setAttribute(on, new Attribute("TraceFilter", "${header.foo} != null"));

        // enable it
        mbeanServer.setAttribute(on, new Attribute("Enabled", Boolean.TRUE));

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 123);

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = getMockEndpoint("mock:foo").getReceivedExchanges();

        List<BacklogTracerEventMessage> events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);

        assertNotNull(events);
        assertEquals(3, events.size());

        BacklogTracerEventMessage event = events.get(0);
        assertEquals(null, event.getToNode());
        assertEquals("    <message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n"
                + "      <headers>\n"
                + "        <header key=\"foo\" type=\"java.lang.Integer\">123</header>\n"
                + "      </headers>\n"
                + "      <body type=\"java.lang.String\">Bye World</body>\n"
                + "    </message>", event.getMessageAsXml());

        BacklogTracerEventMessage event1 = events.get(1);
        assertEquals("foo", event1.getToNode());
        assertEquals("    <message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n"
                + "      <headers>\n"
                + "        <header key=\"foo\" type=\"java.lang.Integer\">123</header>\n"
                + "      </headers>\n"
                + "      <body type=\"java.lang.String\">Bye World</body>\n"
                + "    </message>", event1.getMessageAsXml());

        BacklogTracerEventMessage event2 = events.get(2);
        assertEquals("bar", event2.getToNode());
        assertEquals("    <message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n"
                + "      <headers>\n"
                + "        <header key=\"foo\" type=\"java.lang.Integer\">123</header>\n"
                + "      </headers>\n"
                + "      <body type=\"java.lang.String\">Bye World</body>\n"
                + "    </message>", event2.getMessageAsXml());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setUseBreadcrumb(false);

                from("direct:start")
                        .to("mock:foo").id("foo")
                        .to("mock:bar").id("bar");

            }
        };
    }

}

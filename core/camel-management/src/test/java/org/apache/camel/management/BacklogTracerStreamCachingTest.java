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

import java.io.ByteArrayInputStream;
import java.util.List;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class BacklogTracerStreamCachingTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerEventMessageStreamCaching() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=BacklogTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals("Should not be enabled", Boolean.FALSE, enabled);

        Integer size = (Integer) mbeanServer.getAttribute(on, "BacklogSize");
        assertEquals("Should be 1000", 1000, size.intValue());

        Boolean removeOnDump = (Boolean) mbeanServer.getAttribute(on, "RemoveOnDump");
        assertEquals(Boolean.TRUE, removeOnDump);

        // enable streams
        mbeanServer.setAttribute(on, new Attribute("BodyIncludeStreams", Boolean.TRUE));

        // enable it
        mbeanServer.setAttribute(on, new Attribute("Enabled", Boolean.TRUE));

        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = getMockEndpoint("mock:bar").getReceivedExchanges();

        List<BacklogTracerEventMessage> events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[]{"bar"}, new String[]{"java.lang.String"});

        assertNotNull(events);
        assertEquals(1, events.size());

        BacklogTracerEventMessage event1 = events.get(0);
        assertEquals("bar", event1.getToNode());
        assertEquals("    <message exchangeId=\"" + exchanges.get(0).getExchangeId() + "\">\n"
                + "      <body type=\"org.apache.camel.converter.stream.ByteArrayInputStreamCache\">Bye World</body>\n"
                + "    </message>", event1.getMessageAsXml());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setUseBreadcrumb(false);
                context.setBacklogTracing(true);

                from("direct:start").streamCaching()
                        .process(exchange -> {
                            ByteArrayInputStream is = new ByteArrayInputStream("Bye World".getBytes());
                            exchange.getIn().setBody(is);
                        })
                        .log("Got ${body}")
                        .to("mock:bar").id("bar");
            }
        };
    }

}

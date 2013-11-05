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

import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;
import org.apache.camel.builder.RouteBuilder;

public class BacklogTracerPatternRouteTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    public void testBacklogTracerPattern() throws Exception {
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

        // set the pattern to match only coolRoute
        mbeanServer.setAttribute(on, new Attribute("TracePattern", "coolRoute"));

        // enable it
        mbeanServer.setAttribute(on, new Attribute("Enabled", Boolean.TRUE));

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        List<BacklogTracerEventMessage> events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[]{"foo"}, new String[]{"java.lang.String"});

        assertNotNull(events);
        assertEquals(2, events.size());

        // there should also be messages on bar
        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[]{"bar"}, new String[]{"java.lang.String"});
        assertNotNull(events);
        assertEquals(2, events.size());

        // but not on beer
        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpTracedMessages",
                new Object[]{"beer"}, new String[]{"java.lang.String"});
        assertNotNull(events);
        assertEquals(0, events.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setUseBreadcrumb(false);

                from("direct:start").routeId("coolRoute")
                        .to("direct:beer")
                        .to("mock:foo").id("foo")
                        .to("mock:bar").id("bar");

                from("direct:beer").routeId("beerRoute")
                        .to("mock:beer").id("beer");

            }
        };
    }

}

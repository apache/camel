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

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.Tracer;

/**
 * @version 
 */
public class ManagedRouteContextTracerTest extends ManagementTestSupport {

    public void testRouteTracing() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on1 = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route1\"");
        ObjectName on2 = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route2\"");

        // with tracing
        MockEndpoint traced = getMockEndpoint("mock:traced");
        traced.setExpectedMessageCount(2);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedMessageCount(1);
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.setExpectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();

        // should be enabled for route 1
        Boolean tracing = (Boolean) mbeanServer.getAttribute(on1, "Tracing");
        assertEquals("Tracing should be enabled for route 1", true, tracing.booleanValue());

        // should be disabled for route 2
        Boolean tracing2 = (Boolean) mbeanServer.getAttribute(on2, "Tracing");
        assertEquals("Tracing should be disabled for route 2", false, tracing2.booleanValue());

        // now enable tracing on route 2
        mbeanServer.setAttribute(on2, new Attribute("Tracing", Boolean.TRUE));

        // with tracing
        traced.reset();
        traced.setExpectedMessageCount(1);
        result.reset();
        result.setExpectedMessageCount(0);
        foo.reset();
        foo.setExpectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Tracer tracer = new Tracer();
                tracer.setDestinationUri("mock:traced");
                tracer.setLogLevel(LoggingLevel.OFF);
                context.addInterceptStrategy(tracer);

                from("direct:start").to("log:foo").to("mock:result");

                from("direct:foo").noTracing().to("mock:foo");
            }
        };
    }

}

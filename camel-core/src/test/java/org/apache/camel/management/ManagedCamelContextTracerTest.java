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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.Tracer;

/**
 * @version $Revision$
 */
public class ManagedCamelContextTracerTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DefaultManagementNamingStrategy naming = (DefaultManagementNamingStrategy) context.getManagementStrategy().getManagementNamingStrategy();
        naming.setHostName("localhost");
        naming.setDomainName("org.apache.camel");
        return context;
    }

    @SuppressWarnings("unchecked")
    public void testCamelContextTracing() throws Exception {
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=localhost/camel-1,type=context,name=\"camel-1\"");

        // with tracing
        MockEndpoint traced = getMockEndpoint("mock:traced");
        traced.setExpectedMessageCount(2);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // should be enabled
        Boolean tracing = (Boolean) mbeanServer.getAttribute(on, "Tracing");
        assertEquals("Tracing should be enabled", true, tracing.booleanValue());

        // now disable tracing
        mbeanServer.setAttribute(on, new Attribute("Tracing", Boolean.FALSE));

        // without tracing
        traced.reset();
        traced.setExpectedMessageCount(0);
        result.reset();
        result.setExpectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

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
            }
        };
    }

}
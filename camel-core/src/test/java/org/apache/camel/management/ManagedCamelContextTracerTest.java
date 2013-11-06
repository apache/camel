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
public class ManagedCamelContextTracerTest extends ManagementTestSupport {

    public void testCamelContextTracing() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName camel = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");
        ObjectName on = new ObjectName("org.apache.camel:context=camel-1,type=tracer,name=Tracer");
        mbeanServer.isRegistered(camel);
        mbeanServer.isRegistered(on);

        // with tracing
        MockEndpoint traced = getMockEndpoint("mock:traced");
        traced.setExpectedMessageCount(2);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // should be enabled
        Boolean tracing = (Boolean) mbeanServer.getAttribute(camel, "Tracing");
        assertEquals("Tracing should be enabled", true, tracing.booleanValue());

        String destinationUri = (String) mbeanServer.getAttribute(on, "DestinationUri");
        assertEquals("mock:traced", destinationUri);

        String logLevel = (String) mbeanServer.getAttribute(on, "LogLevel");
        assertEquals(LoggingLevel.OFF.name(), logLevel);

        String logName = (String) mbeanServer.getAttribute(on, "LogName");
        assertNotNull(logName);

        Boolean logStackTrace = (Boolean) mbeanServer.getAttribute(on, "LogStackTrace");
        assertEquals(Boolean.FALSE, logStackTrace);

        Boolean traceInterceptors = (Boolean) mbeanServer.getAttribute(on, "TraceInterceptors");
        assertEquals(Boolean.FALSE, traceInterceptors);

        Boolean traceExceptions = (Boolean) mbeanServer.getAttribute(on, "TraceExceptions");
        assertEquals(Boolean.TRUE, traceExceptions);

        Boolean traceOutExchanges = (Boolean) mbeanServer.getAttribute(on, "TraceOutExchanges");
        assertEquals(Boolean.FALSE, traceOutExchanges);

        Boolean formatterShowBody = (Boolean) mbeanServer.getAttribute(on, "FormatterShowBody");
        assertEquals(Boolean.TRUE, formatterShowBody);

        Boolean formatterShowBodyType = (Boolean) mbeanServer.getAttribute(on, "FormatterShowBodyType");
        assertEquals(Boolean.TRUE, formatterShowBodyType);

        Boolean formatterShowOutBody = (Boolean) mbeanServer.getAttribute(on, "FormatterShowOutBody");
        assertEquals(Boolean.FALSE, formatterShowOutBody);

        Boolean formatterShowOutBodyType = (Boolean) mbeanServer.getAttribute(on, "FormatterShowOutBodyType");
        assertEquals(Boolean.FALSE, formatterShowOutBodyType);

        Boolean formatterShowBreadCrumb = (Boolean) mbeanServer.getAttribute(on, "FormatterShowBreadCrumb");
        assertEquals(Boolean.TRUE, formatterShowBreadCrumb);

        Boolean formatterShowExchangeId = (Boolean) mbeanServer.getAttribute(on, "FormatterShowExchangeId");
        assertEquals(Boolean.FALSE, formatterShowExchangeId);

        Boolean formatterShowHeaders = (Boolean) mbeanServer.getAttribute(on, "FormatterShowHeaders");
        assertEquals(Boolean.TRUE, formatterShowHeaders);

        Boolean formatterShowOutHeaders = (Boolean) mbeanServer.getAttribute(on, "FormatterShowOutHeaders");
        assertEquals(Boolean.FALSE, formatterShowOutHeaders);

        Boolean formatterShowProperties = (Boolean) mbeanServer.getAttribute(on, "FormatterShowProperties");
        assertEquals(Boolean.FALSE, formatterShowProperties);

        Boolean formatterShowNode = (Boolean) mbeanServer.getAttribute(on, "FormatterShowNode");
        assertEquals(Boolean.TRUE, formatterShowNode);

        Boolean formatterShowExchangePattern = (Boolean) mbeanServer.getAttribute(on, "FormatterShowExchangePattern");
        assertEquals(Boolean.TRUE, formatterShowExchangePattern);

        Boolean formatterShowException = (Boolean) mbeanServer.getAttribute(on, "FormatterShowException");
        assertEquals(Boolean.TRUE, formatterShowException);

        Boolean formatterShowShortExchangeId = (Boolean) mbeanServer.getAttribute(on, "FormatterShowShortExchangeId");
        assertEquals(Boolean.FALSE, formatterShowShortExchangeId);

        Integer formatterBreadCrumbLength = (Integer) mbeanServer.getAttribute(on, "FormatterBreadCrumbLength");
        assertEquals(0, formatterBreadCrumbLength.intValue());

        Integer formatterNodeLength = (Integer) mbeanServer.getAttribute(on, "FormatterNodeLength");
        assertEquals(0, formatterNodeLength.intValue());

        Integer formatterMaxChars = (Integer) mbeanServer.getAttribute(on, "FormatterMaxChars");
        assertEquals(10000, formatterMaxChars.intValue());

        // now disable tracing
        mbeanServer.setAttribute(camel, new Attribute("Tracing", Boolean.FALSE));

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
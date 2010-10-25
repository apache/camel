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
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class ManagedTracerOptionsTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DefaultManagementNamingStrategy naming = (DefaultManagementNamingStrategy) context.getManagementStrategy().getManagementNamingStrategy();
        naming.setHostName("localhost");
        naming.setDomainName("org.apache.camel");
        return context;
    }

    public void testManagedTracerOptions() throws Exception {
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=tracer,*"), null);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();

        mbeanServer.setAttribute(on, new Attribute("Enabled", Boolean.TRUE));
        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(true, enabled.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("DestinationUri", null));
        String duri = (String) mbeanServer.getAttribute(on, "DestinationUri");
        assertEquals(null, duri);

        mbeanServer.setAttribute(on, new Attribute("DestinationUri", "mock://traced"));
        duri = (String) mbeanServer.getAttribute(on, "DestinationUri");
        assertEquals("mock://traced", duri);

        Boolean useJpa = (Boolean) mbeanServer.getAttribute(on, "UseJpa");
        assertEquals(false, useJpa.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("LogName", "foo"));
        String ln = (String) mbeanServer.getAttribute(on, "LogName");
        assertEquals("foo", ln);

        mbeanServer.setAttribute(on, new Attribute("LogLevel", "WARN"));
        String ll = (String) mbeanServer.getAttribute(on, "LogLevel");
        assertEquals(LoggingLevel.WARN.name(), ll);

        mbeanServer.setAttribute(on, new Attribute("LogStackTrace", Boolean.TRUE));
        Boolean lst = (Boolean) mbeanServer.getAttribute(on, "LogStackTrace");
        assertEquals(true, lst.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("TraceInterceptors", Boolean.TRUE));
        Boolean ti = (Boolean) mbeanServer.getAttribute(on, "TraceInterceptors");
        assertEquals(true, ti.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("TraceExceptions", Boolean.TRUE));
        Boolean te = (Boolean) mbeanServer.getAttribute(on, "TraceExceptions");
        assertEquals(true, te.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("TraceOutExchanges", Boolean.TRUE));
        Boolean toe = (Boolean) mbeanServer.getAttribute(on, "TraceOutExchanges");
        assertEquals(true, toe.booleanValue());

        doAssertFormatter(mbeanServer, on);

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    private void doAssertFormatter(MBeanServer mbeanServer, ObjectName on) throws Exception {
        mbeanServer.setAttribute(on, new Attribute("FormatterShowBody", Boolean.TRUE));
        Boolean fsb = (Boolean) mbeanServer.getAttribute(on, "FormatterShowBody");
        assertEquals(true, fsb.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowBodyType", Boolean.TRUE));
        Boolean fsbt = (Boolean) mbeanServer.getAttribute(on, "FormatterShowBodyType");
        assertEquals(true, fsbt.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowOutBody", Boolean.TRUE));
        Boolean fsob = (Boolean) mbeanServer.getAttribute(on, "FormatterShowOutBody");
        assertEquals(true, fsob.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowOutBodyType", Boolean.TRUE));
        Boolean fsobt = (Boolean) mbeanServer.getAttribute(on, "FormatterShowOutBodyType");
        assertEquals(true, fsobt.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowBreadCrumb", Boolean.TRUE));
        Boolean fsbc = (Boolean) mbeanServer.getAttribute(on, "FormatterShowBreadCrumb");
        assertEquals(true, fsbc.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowExchangeId", Boolean.TRUE));
        Boolean fsei = (Boolean) mbeanServer.getAttribute(on, "FormatterShowExchangeId");
        assertEquals(true, fsei.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowShortExchangeId", Boolean.TRUE));
        Boolean fssei = (Boolean) mbeanServer.getAttribute(on, "FormatterShowShortExchangeId");
        assertEquals(true, fssei.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowHeaders", Boolean.TRUE));
        Boolean fsh = (Boolean) mbeanServer.getAttribute(on, "FormatterShowHeaders");
        assertEquals(true, fsh.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowOutHeaders", Boolean.TRUE));
        Boolean fsoh = (Boolean) mbeanServer.getAttribute(on, "FormatterShowOutHeaders");
        assertEquals(true, fsoh.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowProperties", Boolean.TRUE));
        Boolean fsp = (Boolean) mbeanServer.getAttribute(on, "FormatterShowProperties");
        assertEquals(true, fsp.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowNode", Boolean.TRUE));
        Boolean fsn = (Boolean) mbeanServer.getAttribute(on, "FormatterShowNode");
        assertEquals(true, fsn.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowRouteId", Boolean.FALSE));
        Boolean fsr = (Boolean) mbeanServer.getAttribute(on, "FormatterShowRouteId");
        assertEquals(false, fsr.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowExchangePattern", Boolean.TRUE));
        Boolean fsep = (Boolean) mbeanServer.getAttribute(on, "FormatterShowExchangePattern");
        assertEquals(true, fsep.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterShowException", Boolean.TRUE));
        Boolean fsex = (Boolean) mbeanServer.getAttribute(on, "FormatterShowException");
        assertEquals(true, fsex.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterBreadCrumbLength", 100));
        Integer fbcl = (Integer) mbeanServer.getAttribute(on, "FormatterBreadCrumbLength");
        assertEquals(100, fbcl.intValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterNodeLength", 50));
        Integer fnl = (Integer) mbeanServer.getAttribute(on, "FormatterNodeLength");
        assertEquals(50, fnl.intValue());

        mbeanServer.setAttribute(on, new Attribute("FormatterMaxChars", 250));
        Integer fmc = (Integer) mbeanServer.getAttribute(on, "FormatterMaxChars");
        assertEquals(250, fmc.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }

}
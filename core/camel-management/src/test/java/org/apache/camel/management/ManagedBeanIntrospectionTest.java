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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedBeanIntrospectionTest extends ManagementTestSupport {

    public String getDummy() {
        return "MyDummy";
    }

    @Test
    public void testManageBeanIntrospection() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // get the bean introspection for the route
        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=services,*"), null);
        List<ObjectName> list = new ArrayList<>(set);
        ObjectName on = null;
        for (ObjectName name : list) {
            if (name.getCanonicalName().contains("DefaultBeanIntrospection")) {
                on = name;
                break;
            }
        }

        assertNotNull("Should have found DefaultBeanIntrospection", on);

        // reset counter
        mbeanServer.invoke(on, "resetCounters", null, null);

        Long counter = (Long) mbeanServer.getAttribute(on, "InvokedCounter");
        assertEquals("Should not have been invoked", 0, counter.intValue());

        Object dummy = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getOrElseProperty(this, "dummy", null, false);
        assertEquals("MyDummy", dummy);

        counter = (Long) mbeanServer.getAttribute(on, "InvokedCounter");
        assertEquals("Should have been invoked", 1, counter.intValue());
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

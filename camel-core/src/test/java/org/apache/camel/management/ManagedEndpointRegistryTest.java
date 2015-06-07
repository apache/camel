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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedEndpointRegistryTest extends ManagementTestSupport {

    public void testManageEndpointRegistry() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // to create a dynamic endpoint
        template.sendBody("log:foo", "Hello World");

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=services,*"), null);
        List<ObjectName> list = new ArrayList<ObjectName>(set);
        ObjectName on = null;
        for (ObjectName name : list) {
            if (name.getCanonicalName().contains("DefaultEndpointRegistry")) {
                on = name;
                break;
            }
        }

        assertNotNull("Should have found EndpointRegistry", on);

        Integer max = (Integer) mbeanServer.getAttribute(on, "MaximumCacheSize");
        assertEquals(1000, max.intValue());

        Integer current = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(3, current.intValue());

        current = (Integer) mbeanServer.getAttribute(on, "StaticSize");
        assertEquals(2, current.intValue());

        current = (Integer) mbeanServer.getAttribute(on, "DynamicSize");
        assertEquals(1, current.intValue());

        String source = (String) mbeanServer.getAttribute(on, "Source");
        assertTrue(source.startsWith("EndpointRegistry"));
        assertTrue(source.endsWith("capacity: 1000"));

        TabularData data = (TabularData) mbeanServer.invoke(on, "listEndpoints", null, null);
        assertEquals(3, data.size());

        // purge
        mbeanServer.invoke(on, "purge", null, null);

        current = (Integer) mbeanServer.getAttribute(on, "DynamicSize");
        assertEquals(0, current.intValue());

        current = (Integer) mbeanServer.getAttribute(on, "StaticSize");
        assertEquals(2, current.intValue());

        current = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(2, current.intValue());

        data = (TabularData) mbeanServer.invoke(on, "listEndpoints", null, null);
        assertEquals(2, data.size());
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
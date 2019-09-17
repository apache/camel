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

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedRouteGetPropertiesTest extends ManagementTestSupport {

    @Test
    public void testGetProperties() throws Exception {
        // JMX tests don't work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        // should be started
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("myRoute", routeId);

        TabularData data = (TabularData) mbeanServer.invoke(on, "getRouteProperties", null, null);

        assertNotNull(data);
        assertTrue(data.containsKey(new Object[] {"key1" }));
        assertEquals("key1", data.get(new Object[] {"key1" }).get("key"));
        assertEquals("val1", data.get(new Object[] {"key1" }).get("value"));
        assertTrue(data.containsKey(new Object[] {"key2" }));
        assertEquals("key2", data.get(new Object[] {"key2" }).get("key"));
        assertEquals("val2", data.get(new Object[] {"key2" }).get("value"));
    }


    static ObjectName getRouteObjectName(MBeanServer mbeanServer) throws Exception {
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        return set.iterator().next();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .routeId("myRoute")
                    .routeProperty("key1", "val1")
                    .routeProperty("key2", "val2")
                    .to("mock:result");
            }
        };
    }
}

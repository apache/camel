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
package org.apache.camel.spring.management;

import java.util.Iterator;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class DualCamelContextManagedAutoAssignedNameTest extends DualCamelContextManagedTest {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/management/dualCamelContextManagedAutoAssignedNameTest.xml");
    }

    public void testDualCamelContextManaged() throws Exception {

        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(2, set.size());
        Iterator<ObjectName> it = set.iterator();

        ObjectName on1 = it.next();
        ObjectName on2 = it.next();

        assertTrue("Route 1 is missing", on1.getCanonicalName().contains("route1") || on2.getCanonicalName().contains("route1"));
        assertTrue("Route 2 is missing", on1.getCanonicalName().contains("route2") || on2.getCanonicalName().contains("route2"));

        set = mbeanServer.queryNames(new ObjectName("*:type=endpoints,*"), null);
        assertTrue("Size should be 4 or higher, was: " + set.size(), set.size() >= 4);

        for (ObjectName on : set) {
            String name = on.getCanonicalName();

            if (name.contains("mock://mock1")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                assertEquals("camel-1", id);
            } else if (name.contains("mock://mock2")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                assertEquals("camel-2", id);
            } else if (name.contains("file://target/route1")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                assertEquals("camel-1", id);
            } else if (name.contains("file://target/route2")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                assertEquals("camel-2", id);
            }
        }
    }

}

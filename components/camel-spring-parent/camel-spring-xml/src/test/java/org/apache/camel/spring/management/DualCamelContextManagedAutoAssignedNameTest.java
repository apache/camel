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
package org.apache.camel.spring.management;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class DualCamelContextManagedAutoAssignedNameTest extends DualCamelContextManagedTest {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/spring/management/dualCamelContextManagedAutoAssignedNameTest.xml");
    }

    @Override
    @Test
    public void testDualCamelContextManaged() throws Exception {

        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(2, set.size());
        Iterator<ObjectName> it = set.iterator();

        ObjectName on1 = it.next();
        ObjectName on2 = it.next();

        assertTrue(on1.getCanonicalName().contains("route1") || on2.getCanonicalName().contains("route1"),
                "Route 1 is missing");
        assertTrue(on1.getCanonicalName().contains("route2") || on2.getCanonicalName().contains("route2"),
                "Route 2 is missing");

        set = mbeanServer.queryNames(new ObjectName("*:type=endpoints,*"), null);
        assertTrue(set.size() >= 4, "Size should be 4 or higher, was: " + set.size());

        Set<String> ids1 = new TreeSet<>();
        Set<String> ids2 = new TreeSet<>();
        for (ObjectName on : set) {
            String name = on.getCanonicalName();

            log.info("ObjectName: {}", on);

            if (name.contains("mock://mock1")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                ids1.add(id);
            } else if (name.contains("mock://mock2")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                ids2.add(id);
            } else if (name.contains("file://target/data/DualCamelContextManagedAutoAssignedNameTest/route1")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                ids1.add(id);
            } else if (name.contains("file://target/data/DualCamelContextManagedAutoAssignedNameTest/route2")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                ids2.add(id);
            }
        }
        assertEquals(1, ids1.size());
        assertEquals(1, ids2.size());
        String camel1 = ids1.iterator().next();
        String camel2 = ids2.iterator().next();
        // should be different Camels
        assertNotEquals(camel1, camel2);
    }

}

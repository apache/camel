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

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class DualCamelContextManagedTest extends SpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/management/dualCamelContextManagedTest.xml");
    }

    @Test
    public void testDualCamelContextManaged() throws Exception {

        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        ObjectName on1 = null;
        ObjectName on2 = null;
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        // filter out only camel-A and camel-B
        for (ObjectName on : set) {
            if (on.getCanonicalName().contains("camel-A")) {
                on1 = on;
            } else if (on.getCanonicalName().contains("camel-B")) {
                on2 = on;
            }
        }
        assertNotNull(on1, "Should have found camel-A route");
        assertNotNull(on2, "Should have found camel-B route");

        assertTrue(on1.getCanonicalName().contains("route1"), "Route 1 is missing");
        assertTrue(on2.getCanonicalName().contains("route2"), "Route 2 is missing");

        set = mbeanServer.queryNames(new ObjectName("*:type=endpoints,*"), null);
        assertTrue(set.size() >= 4, "Should be at least 4 endpoints, was: " + set.size());

        for (ObjectName on : set) {
            String name = on.getCanonicalName();

            if (name.contains("mock://mock1")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                assertEquals("camel-A", id);
            } else if (name.contains("mock://mock2")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                assertEquals("camel-B", id);
            } else if (name.contains("file://target/data/DualCamelContextManagedTest/route1")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                assertEquals("camel-A", id);
            } else if (name.contains("file://target/data/DualCamelContextManagedTestroute2")) {
                String id = (String) mbeanServer.getAttribute(on, "CamelId");
                assertEquals("camel-B", id);
            }
        }
    }

}

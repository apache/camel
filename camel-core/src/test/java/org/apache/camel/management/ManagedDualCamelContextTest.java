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
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version 
 */
public class ManagedDualCamelContextTest extends TestSupport {

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(createRouteBuilder());
        return context;
    }
    
    public void testDualCamelContext() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        CamelContext camel1 = createCamelContext();
        camel1.start();

        CamelContext camel2 = createCamelContext();
        camel2.start();

        // Ensure JMX is enabled for this test so the ManagedManagementStrategy.class
        // If other tests cleaned up the environment properly the following assertions will be true with the default settings
        assertIsInstanceOf(ManagedManagementStrategy.class, camel1.getManagementStrategy());
        assertIsInstanceOf(ManagedManagementStrategy.class, camel2.getManagementStrategy());

        MBeanServer mbeanServer1 = camel1.getManagementStrategy().getManagementAgent().getMBeanServer();
        Set<ObjectName> set = mbeanServer1.queryNames(new ObjectName("*:context=camel-1,type=components,*"), null);
        assertEquals(2, set.size());
        ObjectName on = set.iterator().next();
        assertTrue("Should be registered", mbeanServer1.isRegistered(on));
        String state = (String) mbeanServer1.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);
        String id = (String) mbeanServer1.getAttribute(on, "CamelId");
        assertEquals("camel-1", id);

        MBeanServer mbeanServer2 = camel2.getManagementStrategy().getManagementAgent().getMBeanServer();
        set = mbeanServer1.queryNames(new ObjectName("*:context=camel-2,type=components,*"), null);
        assertEquals(2, set.size());
        on = set.iterator().next();
        assertTrue("Should be registered", mbeanServer2.isRegistered(on));
        state = (String) mbeanServer2.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);
        id = (String) mbeanServer2.getAttribute(on, "CamelId");
        assertEquals("camel-2", id);

        camel1.stop();
        camel2.stop();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }

}
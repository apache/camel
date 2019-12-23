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

import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ServiceStatus;
import org.junit.Test;

public class ManagedUnregisterCamelContextTest extends ManagementTestSupport {

    @Test
    public void testUnregisterCamelContext() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // The camel context already started by ContextTestSupport in the startup method
        
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");

        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        String name = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("camel-1", name);

        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);

        String version = (String) mbeanServer.getAttribute(on, "CamelVersion");
        assertNotNull(version);

        Map<?, ?> properties = (Map<?, ?>) mbeanServer.getAttribute(on, "GlobalOptions");
        assertNull(properties);

        Long num = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
        assertEquals(0L, num.longValue());

        context.stop();

        assertFalse("Should no longer be registered", mbeanServer.isRegistered(on));
    }

}

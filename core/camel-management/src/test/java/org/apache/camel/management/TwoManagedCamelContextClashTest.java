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

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisabledOnOs(OS.AIX)
public class TwoManagedCamelContextClashTest extends TestSupport {

    private CamelContext camel1;
    private CamelContext camel2;

    protected CamelContext createCamelContext(String name, String managementPattern) throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().setName(name);
        if (managementPattern != null) {
            context.getManagementNameStrategy().setNamePattern(managementPattern);
        }
        return context;
    }

    @Test
    public void testTwoManagedCamelContextNoClashDefault() throws Exception {
        camel1 = createCamelContext("foo", null);
        camel2 = createCamelContext("foo", null);

        camel1.start();
        assertTrue(camel1.getStatus().isStarted(), "Should be started");

        MBeanServer mbeanServer = camel1.getManagementStrategy().getManagementAgent().getMBeanServer();
        ObjectName on = getContextObjectName(camel1);
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        // the default name pattern will ensure the JMX names is unique
        camel2.start();
        ObjectName on2 = getContextObjectName(camel2);
        assertTrue(mbeanServer.isRegistered(on2), "Should be registered");

        assertTrue(mbeanServer.isRegistered(on), "Should still be registered after name clash");
        assertTrue(mbeanServer.isRegistered(on2), "Should still be registered after name clash");
    }

    @Test
    public void testTwoManagedCamelContextNoClashCustomPattern() throws Exception {
        camel1 = createCamelContext("foo", "killer-#counter#");
        camel2 = createCamelContext("foo", "killer-#counter#");

        camel1.start();
        assertTrue(camel1.getStatus().isStarted(), "Should be started");

        MBeanServer mbeanServer = camel1.getManagementStrategy().getManagementAgent().getMBeanServer();
        ObjectName on = getContextObjectName(camel1);
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        // the pattern has a counter so no clash
        camel2.start();
        ObjectName on2 = getContextObjectName(camel2);
        assertTrue(mbeanServer.isRegistered(on2), "Should be registered");

        assertTrue(mbeanServer.isRegistered(on), "Should still be registered after name clash");
        assertTrue(mbeanServer.isRegistered(on2), "Should still be registered after name clash");
    }

    @Test
    public void testTwoManagedCamelContextClash() throws Exception {
        camel1 = createCamelContext("foo", "myFoo");
        camel2 = createCamelContext("foo", "myFoo");

        camel1.start();
        assertTrue(camel1.getStatus().isStarted(), "Should be started");

        MBeanServer mbeanServer = camel1.getManagementStrategy().getManagementAgent().getMBeanServer();
        ObjectName on = getContextObjectName(camel1);
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        // we use fixed names, so we will get a clash
        try {
            camel2.start();
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertTrue(e.getCause().getMessage().contains("is already registered"));
        }
    }

    private static ObjectName getContextObjectName(CamelContext context) throws MalformedObjectNameException {
        return ObjectName
                .getInstance("org.apache.camel:context=" + context.getManagementName() + ",type=context,name=\""
                             + context.getName() + "\"");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (camel1 != null) {
            camel1.stop();
        }
        if (camel2 != null) {
            camel2.stop();
        }
        super.tearDown();
    }

}

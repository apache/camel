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

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ManagementAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Tests proper behavior of DefaultManagementAgent when {@link MBeanServer#registerMBean(Object, ObjectName)} returns an
 * {@link ObjectInstance} with a different ObjectName
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
@DisabledOnOs(OS.AIX)
public class DefaultManagementAgentMockTest {

    @Test
    public void testObjectNameModification() throws JMException {
        MBeanServer mbeanServer = mock(MBeanServer.class);
        ObjectInstance instance = mock(ObjectInstance.class);

        ManagementAgent agent = new DefaultManagementAgent();
        agent.setMBeanServer(mbeanServer);

        Object object = "object";
        ObjectName sourceObjectName = new ObjectName("domain", "key", "value");
        ObjectName registeredObjectName = new ObjectName("domain", "key", "otherValue");

        // Register MBean and return different ObjectName
        when(mbeanServer.isRegistered(sourceObjectName)).thenReturn(false);
        when(mbeanServer.registerMBean(object, sourceObjectName)).thenReturn(instance);
        when(instance.getObjectName()).thenReturn(registeredObjectName);
        when(mbeanServer.isRegistered(registeredObjectName)).thenReturn(true);

        agent.register(object, sourceObjectName);

        assertTrue(agent.isRegistered(sourceObjectName));
        reset(mbeanServer, instance);

        // ... and unregister it again
        when(mbeanServer.isRegistered(registeredObjectName)).thenReturn(true);
        mbeanServer.unregisterMBean(registeredObjectName);
        when(mbeanServer.isRegistered(sourceObjectName)).thenReturn(false);

        agent.unregister(sourceObjectName);

        assertFalse(agent.isRegistered(sourceObjectName));
    }

    @Test
    public void testShouldUseHostIPAddressWhenFlagIsTrue() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS, "true");
        try {
            CamelContext ctx = new DefaultCamelContext();

            ManagementAgent agent = new DefaultManagementAgent(ctx);
            agent.start();

            assertTrue(agent.getUseHostIPAddress());
        } finally {
            System.clearProperty(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS);
        }
    }

    @Test
    public void shouldUseHostNameWhenFlagIsFalse() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS, "false");
        try {
            CamelContext ctx = new DefaultCamelContext();

            ManagementAgent agent = new DefaultManagementAgent(ctx);
            agent.start();

            assertFalse(agent.getUseHostIPAddress());
        } finally {
            System.clearProperty(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS);
        }
    }

}

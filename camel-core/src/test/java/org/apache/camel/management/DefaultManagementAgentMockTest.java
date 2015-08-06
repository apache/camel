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

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ManagementAgent;
import org.junit.Test;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests proper behavior of DefaultManagementAgent when
 * {@link MBeanServer#registerMBean(Object, ObjectName)} returns an
 * {@link ObjectInstance} with a different ObjectName
 */
public class DefaultManagementAgentMockTest {

    @Test
    public void testObjectNameModification() throws JMException {
        MBeanServer mbeanServer = createStrictMock(MBeanServer.class);
        ObjectInstance instance = createStrictMock(ObjectInstance.class);

        ManagementAgent agent = new DefaultManagementAgent();
        agent.setMBeanServer(mbeanServer);

        Object object = "object";
        ObjectName sourceObjectName = new ObjectName("domain", "key", "value");
        ObjectName registeredObjectName = new ObjectName("domain", "key", "otherValue");

        // Register MBean and return different ObjectName
        expect(mbeanServer.isRegistered(sourceObjectName)).andReturn(false);
        expect(mbeanServer.registerMBean(object, sourceObjectName)).andReturn(instance);
        expect(instance.getObjectName()).andReturn(registeredObjectName);
        expect(mbeanServer.isRegistered(registeredObjectName)).andReturn(true);
        replay(mbeanServer, instance);

        agent.register(object, sourceObjectName);

        assertTrue(agent.isRegistered(sourceObjectName));
        verify(mbeanServer, instance);
        reset(mbeanServer, instance);

        // ... and unregister it again
        expect(mbeanServer.isRegistered(registeredObjectName)).andReturn(true);
        mbeanServer.unregisterMBean(registeredObjectName);
        expect(mbeanServer.isRegistered(sourceObjectName)).andReturn(false);
        replay(mbeanServer);

        agent.unregister(sourceObjectName);

        assertFalse(agent.isRegistered(sourceObjectName));
        verify(mbeanServer);
    }

    @Test
    public void testShouldUseHostIPAddressWhenFlagisTrue() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS, "true");
        System.setProperty(JmxSystemPropertyKeys.CREATE_CONNECTOR, "true");
        CamelContext ctx = new DefaultCamelContext();

        ManagementAgent agent = new DefaultManagementAgent(ctx);
        agent.start();

        assertTrue(agent.getUseHostIPAddress());
    }

    @Test
    public void shouldUseHostNameWhenFlagisFalse() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS, "false");
        System.setProperty(JmxSystemPropertyKeys.CREATE_CONNECTOR, "true");
        CamelContext ctx = new DefaultCamelContext();

        ManagementAgent agent = new DefaultManagementAgent(ctx);
        agent.start();

        assertFalse(agent.getUseHostIPAddress());
    }

}

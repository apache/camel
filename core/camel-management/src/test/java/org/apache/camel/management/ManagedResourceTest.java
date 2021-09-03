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
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ManagementAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedResourceTest extends ManagementTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ManagedResourceTest.class);

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .bean(MyManagedBean.class).id("myManagedBean")
                        .log("${body}")
                        .to("seda:foo")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testManagedResource() throws Exception {
        final ManagementAgent managementAgent = context.getManagementStrategy().getManagementAgent();
        assertNotNull(managementAgent);

        final MBeanServer mBeanServer = managementAgent.getMBeanServer();
        assertNotNull(mBeanServer);

        final String mBeanServerDefaultDomain = managementAgent.getMBeanServerDefaultDomain();
        assertEquals("org.apache.camel", mBeanServerDefaultDomain);

        final String managementName = context.getManagementName();
        assertNotNull(managementName, "CamelContext should have a management name if JMX is enabled");
        LOG.info("managementName = {}", managementName);

        // Get the Camel Context MBean
        ObjectName onContext = getContextObjectName();
        assertTrue(mBeanServer.isRegistered(onContext), "Should be registered");

        // Get myManagedBean
        ObjectName onManagedBean = getCamelObjectName(TYPE_PROCESSOR, "myManagedBean");
        LOG.info("Canonical Name = {}", onManagedBean.getCanonicalName());
        assertTrue(mBeanServer.isRegistered(onManagedBean), "Should be registered");

        // Send a couple of messages to get some route statistics
        template.sendBody("direct:start", "Hello Camel");
        template.sendBody("direct:start", "Camel Rocks!");

        // Get MBean attribute
        int camelsSeenCount = (Integer) mBeanServer.getAttribute(onManagedBean, "CamelsSeenCount");
        assertEquals(2, camelsSeenCount);

        // Stop the route via JMX
        mBeanServer.invoke(onManagedBean, "resetCamelsSeenCount", null, null);

        camelsSeenCount = (Integer) mBeanServer.getAttribute(onManagedBean, "CamelsSeenCount");
        assertEquals(0, camelsSeenCount);

        String camelId = (String) mBeanServer.getAttribute(onManagedBean, "CamelId");
        assertEquals(context.getName(), camelId);

        String state = (String) mBeanServer.getAttribute(onManagedBean, "State");
        assertEquals("Started", state);

        String fqn = (String) mBeanServer.getAttribute(onManagedBean, "BeanClassName");
        assertEquals(MyManagedBean.class.getCanonicalName(), fqn);
    }
}

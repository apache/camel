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
package org.apache.camel.component.zookeeper;

import java.util.ArrayList;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.jmx.support.JmxUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("all")
public class ZooKeeperManagedEndpointTest extends ZooKeeperTestSupport {
    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testEnpointConfigurationCanBeSetViaJMX() throws Exception {
        Set s = getMBeanServer().queryNames(new ObjectName("org.apache.camel:type=endpoints,name=\"zookeeper:*\",*"), null);
        assertEquals(1, s.size(), "Could not find zookeper endpoint: " + s);
        ObjectName zepName = new ArrayList<ObjectName>(s).get(0);

        verifyManagedAttribute(zepName, "Path", "/node");
        verifyManagedAttribute(zepName, "Create", false);
        verifyManagedAttribute(zepName, "Repeat", false);
        verifyManagedAttribute(zepName, "ListChildren", false);
        verifyManagedAttribute(zepName, "Timeout", 1000);
        verifyManagedAttribute(zepName, "Backoff", 2000L);

        getMBeanServer().invoke(zepName, "clearServers",
                null,
                JmxUtils.getMethodSignature(ZooKeeperEndpoint.class.getMethod("clearServers", null)));
        getMBeanServer().invoke(zepName, "addServer",
                new Object[] { "someserver:12345" },
                JmxUtils.getMethodSignature(ZooKeeperEndpoint.class.getMethod("addServer", new Class[] { String.class })));
    }

    private void verifyManagedAttribute(ObjectName zepName, String attributeName, String attributeValue) throws Exception {
        assertEquals(attributeValue, getMBeanServer().getAttribute(zepName, attributeName));
    }

    private void verifyManagedAttribute(ObjectName zepName, String attributeName, Integer attributeValue) throws Exception {
        assertEquals(attributeValue, getMBeanServer().getAttribute(zepName, attributeName));
    }

    private void verifyManagedAttribute(ObjectName zepName, String attributeName, Boolean attributeValue) throws Exception {
        assertEquals(attributeValue, getMBeanServer().getAttribute(zepName, attributeName));
    }

    private void verifyManagedAttribute(ObjectName zepName, String attributeName, Long attributeValue) throws Exception {
        assertEquals(attributeValue, getMBeanServer().getAttribute(zepName, attributeName));
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("zookeeper://{{zookeeper.connection.string}}/node?timeout=1000&backoff=2000")
                        .to("mock:test");
            }
        };
    }
}

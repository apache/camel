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
package org.apache.camel.component.zookeeper;

import java.util.ArrayList;
import java.util.Set;

import javax.management.Attribute;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.ZooKeeperTestSupport.TestZookeeperClient;
import org.apache.camel.component.zookeeper.ZooKeeperTestSupport.TestZookeeperServer;
import org.apache.camel.management.JmxInstrumentationUsingDefaultsTest;
import org.apache.zookeeper.ZooKeeper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.springframework.jmx.support.JmxUtils;

@SuppressWarnings("all")
public class ZooKeeperEndpointTest extends JmxInstrumentationUsingDefaultsTest {

    private static int teardownAfter;

    @Override
    protected void setUp() throws Exception {
        if (teardownAfter == 0) {
            ZooKeeperTestSupport.setupTestServer();
        }
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (++teardownAfter == 3) {
            ZooKeeperTestSupport.shutdownServer();
        }
    }

    public synchronized void testEnpointConfigurationCanBeSetViaJMX() throws Exception {
        Set s = mbsc.queryNames(new ObjectName(domainName + ":type=endpoints,*"), null);
        assertEquals("Could not find  endpoints: " + s, 2, s.size());
        ObjectName zepName = new ArrayList<ObjectName>(s).get(0);

        verifyManagedAttribute(zepName, "Path", "/someotherpath");
        verifyManagedAttribute(zepName, "Create", true);
        verifyManagedAttribute(zepName, "Repeat", true);
        verifyManagedAttribute(zepName, "ListChildren", true);
        verifyManagedAttribute(zepName, "AwaitExistence", true);
        verifyManagedAttribute(zepName, "Timeout", 12345);
        verifyManagedAttribute(zepName, "Backoff", 12345L);

        mbsc.invoke(zepName, "clearServers", null, JmxUtils.getMethodSignature(ZooKeeperEndpoint.class.getMethod("clearServers", null)));
        mbsc.invoke(zepName, "addServer", new Object[] {"someserver:12345"},
                    JmxUtils.getMethodSignature(ZooKeeperEndpoint.class.getMethod("addServer", new Class[] {String.class})));

    }

    private void verifyManagedAttribute(ObjectName zepName, String attributeName, String attributeValue) throws Exception {
        mbsc.setAttribute(zepName, new Attribute(attributeName, attributeValue));
        assertEquals(attributeValue, mbsc.getAttribute(zepName, attributeName));
    }

    private void verifyManagedAttribute(ObjectName zepName, String attributeName, Integer attributeValue) throws Exception {
        mbsc.setAttribute(zepName, new Attribute(attributeName, attributeValue));
        assertEquals(attributeValue, mbsc.getAttribute(zepName, attributeName));
    }

    private void verifyManagedAttribute(ObjectName zepName, String attributeName, Boolean attributeValue) throws Exception {
        mbsc.setAttribute(zepName, new Attribute(attributeName, attributeValue));
        assertEquals(attributeValue, mbsc.getAttribute(zepName, attributeName));
    }

    private void verifyManagedAttribute(ObjectName zepName, String attributeName, Long attributeValue) throws Exception {
        mbsc.setAttribute(zepName, new Attribute(attributeName, attributeValue));
        assertEquals(attributeValue, mbsc.getAttribute(zepName, attributeName));
    }

    @Override
    public void testCounters() throws Exception {
    }

    @Override
    public void testMBeansRegistered() throws Exception {
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("zoo://localhost:39913/node").to("mock:test");
            }
        };
    }
}

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

import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.management.ManagedCamelContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Does not run well on CI due test uses JMX mbeans")
public class SpringManagedCamelContextTest extends ManagedCamelContextTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/management/SpringManagedCamelContextTest.xml");
    }

    @Test
    public void testFindEipNames() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        assertEquals("19-" + context.getName(), context.getManagementName());

        ObjectName on = getContextObjectName();
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        @SuppressWarnings("unchecked")
        List<String> info = (List<String>) mbeanServer.invoke(on, "findEipNames", null, null);
        assertNotNull(info);

        assertTrue(info.size() > 150);
        assertTrue(info.contains("transform"));
        assertTrue(info.contains("split"));
        assertTrue(info.contains("from"));
    }

    @Test
    public void testFindEips() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        assertEquals("19-" + context.getName(), context.getManagementName());

        ObjectName on = getContextObjectName();
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        @SuppressWarnings("unchecked")
        Map<String, Properties> info = (Map<String, Properties>) mbeanServer.invoke(on, "findEips", null, null);
        assertNotNull(info);

        assertTrue(info.size() > 150);
        Properties prop = info.get("transform");
        assertNotNull(prop);
        assertEquals("transform", prop.get("name"));
        assertEquals("org.apache.camel.model.TransformDefinition", prop.get("class"));
    }

    @Test
    public void testListEips() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        assertEquals("19-" + context.getName(), context.getManagementName());

        ObjectName on = getContextObjectName();
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        TabularData data = (TabularData) mbeanServer.invoke(on, "listEips", null, null);
        assertNotNull(data);
        assertTrue(data.size() > 150);
    }

}

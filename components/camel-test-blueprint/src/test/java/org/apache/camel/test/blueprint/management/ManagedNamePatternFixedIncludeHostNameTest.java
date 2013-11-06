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
package org.apache.camel.test.blueprint.management;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class ManagedNamePatternFixedIncludeHostNameTest extends CamelBlueprintTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/management/managedNamePatternFixedIncludeHostNameTest.xml";
    }

    @Override
    protected String getBundleVersion() {
        return "1.2.3";
    }

    @Test
    public void testManagedNamePattern() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        assertEquals("cool-1.2.3", context.getManagementName());

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=localhost/" + context.getManagementName()
                + ",type=context,name=\"" + context.getName() + "\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(on));
    }

}

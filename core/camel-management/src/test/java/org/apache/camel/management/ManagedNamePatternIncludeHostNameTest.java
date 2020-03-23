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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedNamePatternIncludeHostNameTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().init();
        DefaultManagementObjectNameStrategy naming = (DefaultManagementObjectNameStrategy)context.getManagementStrategy().getManagementObjectNameStrategy();
        naming.setHostName("localhost");
        context.getManagementStrategy().getManagementAgent().setIncludeHostName(true);
        context.getManagementNameStrategy().setNamePattern("cool-#name#");
        return context;
    }

    @Test
    public void testManagedNamePattern() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        assertTrue(context.getManagementName().startsWith("cool"));

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=localhost/" + context.getManagementName() + ",type=context,name=\"camel-1\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(on));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }
}

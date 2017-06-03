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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class JmxRecipientListTest extends ManagementTestSupport {

    public void testJmxEndpointsAddedDynamicallyDefaultRegister() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived("answer");
        y.expectedBodiesReceived("answer");
        z.expectedBodiesReceived("answer");

        sendBody();

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        // this endpoint is part of the route and should be registered
        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"direct://a\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(name));

        // endpoints added after routes has been started is by default not registered
        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://x\"");
        assertFalse("Should not be registered", mbeanServer.isRegistered(name));

        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://y\"");
        assertFalse("Should not be registered", mbeanServer.isRegistered(name));

        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://z\"");
        assertFalse("Should not be registered", mbeanServer.isRegistered(name));

        // however components is always registered
        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=components,name=\"mock\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(name));
    }

    public void testJmxEndpointsAddedDynamicallyAlwaysRegister() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // enable always register
        context.getManagementStrategy().getManagementAgent().setRegisterAlways(true);

        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived("answer");
        y.expectedBodiesReceived("answer");
        z.expectedBodiesReceived("answer");

        sendBody();

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        // this endpoint is part of the route and should be registered
        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"direct://a\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(name));

        // endpoints added after routes has been started is now also registered
        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://x\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(name));

        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://y\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(name));

        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://z\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(name));

        // however components is always registered
        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=components,name=\"mock\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(name));
    }

    protected void sendBody() {
        template.sendBodyAndHeader("direct:a", "answer", "recipientListHeader",
                "mock:x,mock:y,mock:z");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").recipientList(
                        header("recipientListHeader").tokenize(","));
            }
        };

    }

}

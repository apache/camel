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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringJmxRecipientListRegisterAlwaysTest extends SpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/management/SpringJmxRecipientListTestRegisterAlways.xml");
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testJmxEndpointsAddedDynamicallyAlwaysRegister() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived("answer");
        y.expectedBodiesReceived("answer");
        z.expectedBodiesReceived("answer");

        template.sendBodyAndHeader("direct:a", "answer", "recipientListHeader", "mock:x,mock:y,mock:z");

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

}

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

import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_COMPONENT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class SpringJmxEndpointInjectBeanRefTwoTest extends SpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/spring/management/SpringJmxEndpointInjectBeanRefTwoTest.xml");
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testJmxEndpointInjectBean() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getCamelObjectName(TYPE_COMPONENT, "seda");
        assertTrue(mbeanServer.isRegistered(on));

        on = getCamelObjectName(TYPE_ENDPOINT, "seda://foo");
        assertTrue(mbeanServer.isRegistered(on));
        String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
        assertEquals("seda://foo", uri);

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

}

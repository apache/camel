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
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class SpringManagedSedaEndpointTest extends SpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/management/SpringManagedSedaEndpointTest.xml");
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testSedaEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("seda:start", "Hello World");
        template.sendBody("seda:start", "Bye World");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"seda://start\"");
        String uri = (String) mbeanServer.getAttribute(name, "EndpointUri");
        assertEquals("seda://start", uri);

        Long timeout = (Long) mbeanServer.getAttribute(name, "Timeout");
        assertEquals(30000, timeout.intValue());

        Integer size = (Integer) mbeanServer.getAttribute(name, "CurrentQueueSize");
        assertEquals(0, size.intValue());

        // stop route
        context.getRouteController().stopRoute("foo");

        // send a message to queue
        template.sendBody("seda:start", "Hi World");

        size = (Integer) mbeanServer.getAttribute(name, "CurrentQueueSize");
        assertEquals(1, size.intValue());

        Long size2 = (Long) mbeanServer.invoke(name, "queueSize", null, null);
        assertEquals(1, size2.longValue());

        String out = (String) mbeanServer.invoke(name, "browseExchange", new Object[]{0}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        // message body is not dumped when browsing exchange
        assertFalse(out.contains("Hi World"));

        out = (String) mbeanServer.invoke(name, "browseMessageBody", new Object[]{0}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        assertTrue(out.contains("Hi World"));

        mbeanServer.invoke(name, "purgeQueue", null, null);

        size = (Integer) mbeanServer.getAttribute(name, "CurrentQueueSize");
        assertEquals(0, size.intValue());
    }
}

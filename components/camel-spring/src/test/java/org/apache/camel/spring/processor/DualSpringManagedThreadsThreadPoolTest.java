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
package org.apache.camel.spring.processor;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DualSpringManagedThreadsThreadPoolTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/processor/SpringDualManagedThreadsThreadPoolTest.xml");
    }

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testDualManagedThreadPool() throws Exception {
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=threadpools,name=\"myPool\"");

        Integer corePoolSize = (Integer) mbeanServer.getAttribute(on, "CorePoolSize");
        assertEquals(2, corePoolSize.intValue());

        Integer maxPoolSize = (Integer) mbeanServer.getAttribute(on, "MaximumPoolSize");
        assertEquals(4, maxPoolSize.intValue());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:foo", "Bye World");
        assertMockEndpointsSatisfied();

        String id = (String) mbeanServer.getAttribute(on, "Id");
        assertEquals("myPool", id);

        // no source or route as its a shared thread pool
        String source = (String) mbeanServer.getAttribute(on, "SourceId");
        assertEquals(null, source);

        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals(null, routeId);

        on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=threadpools,name=\"myOtherPool\"");

        corePoolSize = (Integer) mbeanServer.getAttribute(on, "CorePoolSize");
        assertEquals(7, corePoolSize.intValue());

        maxPoolSize = (Integer) mbeanServer.getAttribute(on, "MaximumPoolSize");
        assertEquals(9, maxPoolSize.intValue());

        id = (String) mbeanServer.getAttribute(on, "Id");
        assertEquals("myOtherPool", id);

        // no source or route as its a shared thread pool
        source = (String) mbeanServer.getAttribute(on, "SourceId");
        assertEquals(null, source);

        routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals(null, routeId);
    }

}

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

/**
 * @version 
 */
public class ManagedThreadPoolWithIdTest extends ManagementTestSupport {

    public void testManagedThreadPool() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=threadpools,name=\"myThreads(threads)\"");

        Boolean shutdown = (Boolean) mbeanServer.getAttribute(on, "Shutdown");
        assertEquals(false, shutdown.booleanValue());

        Integer corePoolSize = (Integer) mbeanServer.getAttribute(on, "CorePoolSize");
        assertEquals(15, corePoolSize.intValue());

        Integer maxPoolSize = (Integer) mbeanServer.getAttribute(on, "MaximumPoolSize");
        assertEquals(30, maxPoolSize.intValue());

        Integer poolSize = (Integer) mbeanServer.getAttribute(on, "PoolSize");
        assertEquals(0, poolSize.intValue());

        Long keepAlive = (Long) mbeanServer.getAttribute(on, "KeepAliveTime");
        assertEquals(60, keepAlive.intValue());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        String id = (String) mbeanServer.getAttribute(on, "Id");
        assertEquals("myThreads", id);

        String source = (String) mbeanServer.getAttribute(on, "SourceId");
        assertEquals("threads", source);

        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("myRoute", routeId);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute").threads(15, 30).id("myThreads").to("mock:result");
            }
        };
    }

}
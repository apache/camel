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

import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * @version 
 */
public class ManagedThreadPoolProfileTest extends ManagementTestSupport {

    public void testManagedThreadPool() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=threadpools,name=\"threads1(threads)\"");

        Boolean shutdown = (Boolean) mbeanServer.getAttribute(on, "Shutdown");
        assertEquals(false, shutdown.booleanValue());

        Integer corePoolSize = (Integer) mbeanServer.getAttribute(on, "CorePoolSize");
        assertEquals(5, corePoolSize.intValue());

        Integer maxPoolSize = (Integer) mbeanServer.getAttribute(on, "MaximumPoolSize");
        assertEquals(15, maxPoolSize.intValue());

        Integer poolSize = (Integer) mbeanServer.getAttribute(on, "PoolSize");
        assertEquals(0, poolSize.intValue());

        Long keepAlive = (Long) mbeanServer.getAttribute(on, "KeepAliveTime");
        assertEquals(25, keepAlive.intValue());

        Boolean allow = (Boolean) mbeanServer.getAttribute(on, "AllowCoreThreadTimeout");
        assertEquals(true, allow.booleanValue());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        String id = (String) mbeanServer.getAttribute(on, "Id");
        assertEquals("threads1", id);

        String source = (String) mbeanServer.getAttribute(on, "SourceId");
        assertEquals("threads", source);

        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("route1", routeId);

        String profileId = (String) mbeanServer.getAttribute(on, "ThreadPoolProfileId");
        assertEquals("custom", profileId);

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ThreadPoolProfile profile = new ThreadPoolProfile("custom");
                profile.setPoolSize(5);
                profile.setMaxPoolSize(15);
                profile.setKeepAliveTime(25L);
                profile.setMaxQueueSize(250);
                profile.setAllowCoreThreadTimeOut(true);
                profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);

                context.getExecutorServiceManager().registerThreadPoolProfile(profile);

                from("direct:start").threads().executorServiceRef("custom").to("mock:result");
            }
        };
    }

}
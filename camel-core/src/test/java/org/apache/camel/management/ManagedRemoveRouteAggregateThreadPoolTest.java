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

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

/**
 * @version 
 */
public class ManagedRemoveRouteAggregateThreadPoolTest extends ManagementTestSupport {

    public void testRemove() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"foo\"");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:foo", "Hello World");
        assertMockEndpointsSatisfied();

        // remember number of thread pools before
        Set<ObjectName> before = mbeanServer.queryNames(new ObjectName("*:type=threadpools,*"), null);

        // stop and remove route
        mbeanServer.invoke(on, "stop", null, null);
        mbeanServer.invoke(on, "remove", null, null);

        // should not be registered anymore
        boolean registered = mbeanServer.isRegistered(on);
        assertFalse("Route mbean should have been unregistered", registered);

        // and no wire tap thread pool as we use an existing external pool
        Set<ObjectName> after = mbeanServer.queryNames(new ObjectName("*:type=threadpools,*"), null);

        // there should be 1 less thread pool
        assertTrue("There should be one less thread pool", before.size() - 1 == after.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo")
                    .aggregate(constant(true), new UseLatestAggregationStrategy()).completionTimeout(1000)
                        .to("mock:result");
            }
        };
    }

}

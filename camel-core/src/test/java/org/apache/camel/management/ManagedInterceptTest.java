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
public class ManagedInterceptTest extends ManagementTestSupport {

    public void testIntercept() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:intercept").expectedBodiesReceived("Hello World", "Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");
        Long queueSize = (Long) mbeanServer.invoke(name, "queueSize", null, null);
        assertEquals(1, queueSize.intValue());

        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://intercept\"");
        queueSize = (Long) mbeanServer.invoke(name, "queueSize", null, null);
        assertEquals(2, queueSize.intValue());

        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"log-foo\"");
        mbeanServer.isRegistered(name);
        
        Long total = (Long) mbeanServer.getAttribute(name, "ExchangesTotal");
        assertEquals(1, total.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                intercept().to("mock:intercept");

                from("direct:start").routeId("foo")
                    .to("log:foo").id("log-foo").to("mock:result");
            }
        };
    }

}

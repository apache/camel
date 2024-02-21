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

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementMBeansLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public abstract class ManagedMBeansLevelTestSupport extends ManagementTestSupport {

    private final ManagementMBeansLevel level;

    public ManagedMBeansLevelTestSupport(ManagementMBeansLevel level) {
        this.level = level;
    }

    abstract void assertResults(Set<ObjectName> contexts, Set<ObjectName> routes, Set<ObjectName> processors);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        //set level if provided
        if (level != null) {
            context.getManagementStrategy().getManagementAgent().setMBeansLevel(level);
        }
        return context;
    }

    @Test
    public void test() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        assertMBeans(mbeanServer);
    }

    void assertMBeans(MBeanServer mbeanServer) throws MalformedObjectNameException {
        Set<ObjectName> contexts = mbeanServer.queryNames(new ObjectName("*:type=context,*"), null);
        Set<ObjectName> routes = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        Set<ObjectName> processors = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);

        assertResults(contexts, routes, processors);
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

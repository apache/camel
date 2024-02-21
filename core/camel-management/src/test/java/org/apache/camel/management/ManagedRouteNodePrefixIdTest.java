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
import javax.management.ObjectName;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedLogMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.AIX)
public class ManagedRouteNodePrefixIdTest extends ManagementTestSupport {

    @Test
    public void testNodeIdPrefix() throws Exception {
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals(4, set.size());

        // hardcoded ids should also be prefixed
        ManagedProcessorMBean mb
                = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class)
                        .getManagedProcessor("aaamyMock");
        assertEquals("aaamyMock", mb.getProcessorId());
        assertEquals("foo", mb.getRouteId());
        assertEquals("aaa", mb.getNodePrefixId());
        mb = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class).getManagedProcessor("bbbmyMock");
        assertEquals("bbbmyMock", mb.getProcessorId());
        assertEquals("bar", mb.getRouteId());
        assertEquals("bbb", mb.getNodePrefixId());

        // auto assigned ids should be prefixed
        mb = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class).getManagedProcessor("aaalog2",
                ManagedLogMBean.class);
        assertEquals("aaalog2", mb.getProcessorId());
        assertEquals("foo", mb.getRouteId());
        assertEquals("aaa", mb.getNodePrefixId());
        mb = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class).getManagedProcessor("bbblog4",
                ManagedLogMBean.class);
        assertEquals("bbblog4", mb.getProcessorId());
        assertEquals("bar", mb.getRouteId());
        assertEquals("bbb", mb.getNodePrefixId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").nodePrefixId("aaa")
                        .to("mock:foo").id("myMock")
                        .log("Hello foo");

                from("direct:bar").nodePrefixId("bbb").routeId("bar")
                        .to("mock:bar").id("myMock")
                        .log("Hello bar");
            }
        };
    }

}

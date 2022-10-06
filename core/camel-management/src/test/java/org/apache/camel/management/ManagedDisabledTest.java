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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.AIX)
public class ManagedDisabledTest extends ManagementTestSupport {

    @Test
    public void testManageDisabled() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:baz").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");
        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "mychoice");
        String type = (String) mbeanServer.getAttribute(on, "NodeType");
        assertEquals("choice", type);
        on = getCamelObjectName(TYPE_PROCESSOR, "mybaz");
        type = (String) mbeanServer.getAttribute(on, "NodeType");
        assertEquals("to", type);
        on = getCamelObjectName(TYPE_PROCESSOR, "mylog");
        type = (String) mbeanServer.getAttribute(on, "NodeType");
        assertEquals("log", type);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:foo")
                    .choice().disabled().id("mychoice")
                        .when(xpath("/bar")).to("mock:bar")
                        .when(xpath("/baz")).to("mock:baz")
                    .end()
                    .to("mock:baz").disabled(true).id("mybaz")
                    .log("Hello World").disabled("true").id("mylog")
                    .to("mock:result");
            }
        };
    }

}

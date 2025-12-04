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

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

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

        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "foo");
        Boolean disabled = (Boolean) mbeanServer.getAttribute(on, "Disabled");
        assertFalse(disabled);

        on = getCamelObjectName(TYPE_PROCESSOR, "mychoice");
        disabled = (Boolean) mbeanServer.getAttribute(on, "Disabled");
        assertTrue(disabled);

        on = getCamelObjectName(TYPE_PROCESSOR, "mybaz");
        disabled = (Boolean) mbeanServer.getAttribute(on, "Disabled");
        assertTrue(disabled);

        on = getCamelObjectName(TYPE_PROCESSOR, "mylog");
        disabled = (Boolean) mbeanServer.getAttribute(on, "Disabled");
        assertTrue(disabled);

        on = getCamelObjectName(TYPE_PROCESSOR, "result");
        disabled = (Boolean) mbeanServer.getAttribute(on, "Disabled");
        assertFalse(disabled);
    }

    @Test
    public void testManageEnabled() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        // enable at runtime
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "mychoice");
        mbeanServer.invoke(on, "enable", null, null);
        on = getCamelObjectName(TYPE_PROCESSOR, "mylog");
        mbeanServer.invoke(on, "enable", null, null);

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:baz").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "<bar>Moes</bar>", "foo", "123");
        assertMockEndpointsSatisfied();

        on = getCamelObjectName(TYPE_PROCESSOR, "mychoice");
        Boolean disabled = (Boolean) mbeanServer.getAttribute(on, "Disabled");
        assertFalse(disabled);

        on = getCamelObjectName(TYPE_PROCESSOR, "mybaz");
        disabled = (Boolean) mbeanServer.getAttribute(on, "Disabled");
        assertTrue(disabled);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("mock:foo")
                        .id("foo")
                        .choice()
                        .disabled()
                        .id("mychoice")
                        .when(xpath("/bar"))
                        .to("mock:bar")
                        .when(xpath("/baz"))
                        .to("mock:baz")
                        .end()
                        .to("mock:baz")
                        .disabled(true)
                        .id("mybaz")
                        .log("Hello World")
                        .disabled("true")
                        .id("mylog")
                        .to("mock:result")
                        .id("result");
            }
        };
    }
}

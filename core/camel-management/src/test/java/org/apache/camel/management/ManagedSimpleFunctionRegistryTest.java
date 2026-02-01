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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SimpleFunctionRegistry;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_SERVICE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.AIX)
public class ManagedSimpleFunctionRegistryTest extends ManagementTestSupport {

    @Test
    public void testSimpleFunction() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bonjour World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName name = getCamelObjectName(TYPE_SERVICE, "DefaultSimpleFunctionRegistry");

        Integer size = (Integer) mbeanServer.getAttribute(name, "Size");
        assertEquals(1, size);

        Set<String> names = (Set) mbeanServer.getAttribute(name, "FunctionNames");
        assertEquals(1, names.size());
        assertEquals("hi", names.iterator().next());

        Boolean bool
                = (Boolean) mbeanServer.invoke(name, "hasFunction", new Object[] { "hi" }, new String[] { "java.lang.String" });
        assertEquals(Boolean.TRUE, bool);

        bool = (Boolean) mbeanServer.invoke(name, "hasFunction", new Object[] { "goodbye" },
                new String[] { "java.lang.String" });
        assertEquals(Boolean.FALSE, bool);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                SimpleFunctionRegistry r = PluginHelper.getSimpleFunctionRegistry(context);
                r.addFunction("hi", simple("Bonjour ${body}"));

                from("direct:start").setBody(simple("${hi}")).to("mock:result");
            }
        };
    }

}

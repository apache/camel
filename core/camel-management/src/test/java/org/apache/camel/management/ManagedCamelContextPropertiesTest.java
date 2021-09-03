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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.ExplicitCamelContextNameStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCamelContextPropertiesTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // to force a different management name than the camel id
        context.getManagementNameStrategy().setNamePattern("19-#name#");
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("my-camel-context"));
        return context;
    }

    @Test
    public void testGetSetProperties() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        assertEquals("19-my-camel-context", context.getManagementName());
        assertEquals("my-camel-context", context.getName());

        assertTrue(mbeanServer.isRegistered(on), "Should be registered");
        String name = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("my-camel-context", name);

        // invoke operations
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        mbeanServer.invoke(on, "setGlobalOption", new String[] { Exchange.LOG_DEBUG_BODY_MAX_CHARS, "-1" },
                new String[] { "java.lang.String", "java.lang.String" });
        mbeanServer.invoke(on, "setGlobalOption", new String[] { Exchange.LOG_DEBUG_BODY_STREAMS, "true" },
                new String[] { "java.lang.String", "java.lang.String" });

        Object invoke = mbeanServer.invoke(on, "getGlobalOption", new String[] { Exchange.LOG_DEBUG_BODY_MAX_CHARS },
                new String[] { "java.lang.String" });
        assertEquals("-1", invoke);

        invoke = mbeanServer.invoke(on, "getGlobalOption", new String[] { Exchange.LOG_DEBUG_BODY_STREAMS },
                new String[] { "java.lang.String" });
        assertEquals("true", invoke);
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

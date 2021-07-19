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

import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.AIX)
public class JmxInstrumentationOnlyRegisterProcessorWithCustomIdTest extends ManagementTestSupport {

    protected String domainName = DefaultManagementAgent.DEFAULT_DOMAIN;
    protected MBeanServer server;

    @Test
    public void testCustomId() throws Exception {
        Set<ObjectName> s = server.queryNames(new ObjectName(domainName + ":type=endpoints,*"), null);
        assertEquals(6, s.size(), "Could not find 2 endpoints: " + s);

        s = server.queryNames(new ObjectName(domainName + ":type=context,*"), null);
        assertEquals(1, s.size(), "Could not find 1 context: " + s);

        s = server.queryNames(new ObjectName(domainName + ":type=processors,*"), null);
        assertEquals(1, s.size(), "Could not find 1 processor: " + s);
        // should be mock foo
        ObjectName on = s.iterator().next();
        String id = (String) server.getAttribute(on, "ProcessorId");
        assertEquals("myfoo", id);

        s = server.queryNames(new ObjectName(domainName + ":type=routes,*"), null);
        assertEquals(2, s.size(), "Could not find 2 route: " + s);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setOnlyRegisterProcessorWithCustomId(true);
        return context;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        server = ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        // sets the id of the previous node, that is the mock:foo
                        .to("mock:foo").id("myfoo")
                        .delay(10)
                        .to("mock:result");

                from("direct:other")
                        .to("mock:bar")
                        .delay(10)
                        .to("mock:other");
            }
        };
    }
}

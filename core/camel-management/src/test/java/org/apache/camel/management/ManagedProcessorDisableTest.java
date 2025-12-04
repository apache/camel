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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public class ManagedProcessorDisableTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().setProfile("dev"); // only possible in dev mode
        return context;
    }

    @Test
    public void testDisable() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "myProcessor");

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();

        Long counter = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(1L, counter.longValue());

        ManagedProcessorMBean mb = context.getCamelContextExtension()
                .getContextPlugin(ManagedCamelContext.class)
                .getManagedProcessor("myProcessor");
        assertNotNull(mb);
        assertEquals(1L, mb.getExchangesCompleted());
        assertEquals(false, mb.getDisabled());
        mb.disable();
        assertEquals(true, mb.getDisabled());

        resetMocks();

        getMockEndpoint("mock:result").expectedBodiesReceived("Earth");
        template.sendBody("direct:start", "Earth");
        assertMockEndpointsSatisfied();

        assertEquals(1L, mb.getExchangesCompleted());
        assertEquals(true, mb.getDisabled());

        mb.enable();

        resetMocks();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Moon");
        template.sendBody("direct:start", "Moon");
        assertMockEndpointsSatisfied();

        assertEquals(2L, mb.getExchangesCompleted());
        assertEquals(false, mb.getDisabled());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("foo")
                        .setBody(simple("Hello ${body}"))
                        .id("myProcessor")
                        .to("mock:result");
            }
        };
    }
}

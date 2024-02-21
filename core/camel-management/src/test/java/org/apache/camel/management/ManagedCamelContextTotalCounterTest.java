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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.ExplicitCamelContextNameStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCamelContextTotalCounterTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // to force a different management name than the camel id
        context.getManagementNameStrategy().setNamePattern("20-#name#");
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("my-camel-context"));
        return context;
    }

    @Test
    public void testContextTotalCounter() throws Exception {
        template.sendBody("direct:a", "Hello World");

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getContextObjectName();

        assertTrue(mbeanServer.isRegistered(on), "Should be registered");
        String name = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("my-camel-context", name);

        String managementName = (String) mbeanServer.getAttribute(on, "ManagementName");
        assertEquals("20-my-camel-context", managementName);

        Integer total = (Integer) mbeanServer.getAttribute(on, "TotalRoutes");
        assertEquals(3, total.intValue());

        // 3 routes but only 1 exchange completed
        Long ec = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(1, ec.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                        .to("log:a")
                        .to("direct:b");

                from("direct:b")
                        .to("log:b")
                        .to("direct:c");

                from("direct:c")
                        .to("log:c");
            }
        };
    }

}

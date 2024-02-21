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

import java.io.File;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedRouteDumpStrategyTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        String dir = testDirectory().toString();

        CamelContext context = super.createCamelContext();
        context.setDumpRoutes("xml");

        DefaultDumpRoutesStrategy drd = new DefaultDumpRoutesStrategy();
        drd.setInclude("all");
        drd.setLog(false);
        drd.setOutput(dir);
        drd.setResolvePlaceholders(false);
        context.addService(drd);

        return context;
    }

    @Test
    public void testDumpStrategy() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        String mbeanName
                = String.format("org.apache.camel:context=" + context.getManagementName() + ",name=%s,type=services",
                        DefaultDumpRoutesStrategy.class.getSimpleName());
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName(mbeanName), null);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();
        assertTrue(mbeanServer.isRegistered(on));

        String include = (String) mbeanServer.getAttribute(on, "Include");
        assertEquals("all", include);
        Boolean log = (Boolean) mbeanServer.getAttribute(on, "Log");
        assertFalse(log);
        Boolean rp = (Boolean) mbeanServer.getAttribute(on, "ResolvePlaceholders");
        assertFalse(rp);

        // dump should pre-exist
        File dir = testDirectory().toFile();
        String[] files = dir.list();
        assertEquals(1, files.length);
        assertEquals("dump1.xml", files[0]);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute")
                        .log("Got ${body}")
                        .to("mock:result");
            }
        };
    }

}

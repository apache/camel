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
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CAMEL-25598: the route template id and the source of the templated route of a route are exposed in JMX.
 */
@DisabledOnOs(OS.AIX)
public class ManagedRouteTemplatedRouteSourceTest extends ManagementTestSupport {

    @Test
    public void testTemplatedRouteSource() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getCamelObjectName(TYPE_ROUTE, "greet");
        assertEquals("greeting", mbeanServer.getAttribute(on, "RouteTemplateId"));
        assertEquals("mem:my-routes.java", mbeanServer.getAttribute(on, "TemplatedRouteSource"));

        on = getCamelObjectName(TYPE_ROUTE, "plain");
        assertNull(mbeanServer.getAttribute(on, "RouteTemplateId"));
        assertNull(mbeanServer.getAttribute(on, "TemplatedRouteSource"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("greeting").templateParameter("greeting")
                        .from("direct:greet")
                        .setBody(constant("{{greeting}}"));

                templatedRoute("greeting").routeId("greet").parameter("greeting", "Hello");

                from("direct:plain").routeId("plain")
                        .to("mock:plain");
            }
        };
        builder.setResource(ResourceHelper.fromString("mem:my-routes.java", ""));
        return builder;
    }

}

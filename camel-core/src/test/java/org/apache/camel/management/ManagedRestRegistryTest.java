/**
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
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

public class ManagedRestRegistryTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("dummy-test", new DummyRestConsumerFactory());
        return new DefaultCamelContext(registry);
    }

    public void testRestRegistry() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of services
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        ObjectName name = null;
        for (ObjectName service : names) {
            if (service.toString().contains("DefaultRestRegistry")) {
                name = service;
                break;
            }
        }
        assertNotNull("Cannot find DefaultRestRegistry", name);
        assertTrue(mbeanServer.isRegistered(name));

        assertEquals(3, mbeanServer.getAttribute(name, "NumberOfRestServices"));

        TabularData data = (TabularData) mbeanServer.invoke(name, "listRestServices", null, null);
        assertEquals(3, data.size());

        // should not be enabled as api-doc is not enabled or camel-swagger-java is not on classpath
        String json = (String) mbeanServer.invoke(name, "apiDocAsJson", null, null);
        assertNull(json);

        // remove all routes
        for (Route route : context.getRoutes()) {
            context.stopRoute(route.getId());
            context.removeRoute(route.getId());
        }

        assertEquals(0, mbeanServer.getAttribute(name, "NumberOfRestServices"));
        data = (TabularData) mbeanServer.invoke(name, "listRestServices", null, null);
        assertEquals(0, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost");
                rest("/say/hello/{name}")
                    .get().to("direct:hello").description("Calling direct route");

                rest("/say/bye").description("the bye rest service")
                    .get().consumes("application/json").description("I am saying bye world")
                        .route().routeId("myRestRoute").transform().constant("Bye World").endRest()
                    .post()
                        .to("mock:update");

                from("direct:hello").description("The hello route")
                    .transform().simple("Hello ${header.name}");
            }
        };
    }
}

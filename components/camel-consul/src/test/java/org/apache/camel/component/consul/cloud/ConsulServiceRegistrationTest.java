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
package org.apache.camel.component.consul.cloud;

import java.util.List;

import com.orbitz.consul.CatalogClient;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.health.ServiceHealth;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.support.ConsulTestSupport;
import org.apache.camel.component.service.ServiceComponent;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.springframework.util.SocketUtils;

public class ConsulServiceRegistrationTest extends ConsulTestSupport {
    private final static String SERVICE_NAME = "my-service";
    private final static String SERVICE_HOST = "localhost";
    private final static int SERVICE_PORT = SocketUtils.findAvailableTcpPort();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("service", new ServiceComponent());

        return registry;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        ConsulServiceRegistry registry = new ConsulServiceRegistry();
        registry.setId(context.getUuidGenerator().generateUuid());
        registry.setCamelContext(context());
        registry.setUrl(consulUrl());
        registry.setServiceHost(SERVICE_HOST);
        registry.setOverrideServiceHost(true);

        context.addService(registry, true, false);

        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("service:%s:jetty:http://0.0.0.0:%d?serviceMeta.type=consul", SERVICE_NAME, SERVICE_PORT)
                    .routeId("exposed")
                    .noAutoStartup()
                    .to("log:service-registry?level=INFO");
            }
        };
    }

    @Test
    public void testRegistrationFromRoute() throws Exception {
        final CatalogClient catalog = getConsul().catalogClient();
        final HealthClient health = getConsul().healthClient();

        // the service should not be registered as the route is not running
        assertTrue(catalog.getService(SERVICE_NAME).getResponse().isEmpty());

        // let start the route
        context().startRoute("exposed");

        // check that service has been registered
        List<CatalogService> services = catalog.getService(SERVICE_NAME).getResponse();
        assertEquals(1, services.size());
        assertEquals(SERVICE_PORT, services.get(0).getServicePort());
        assertEquals("localhost", services.get(0).getServiceAddress());
        assertTrue(services.get(0).getServiceTags().contains("type=consul"));
        assertTrue(services.get(0).getServiceTags().contains("service.protocol=http"));
        assertTrue(services.get(0).getServiceTags().contains("service.path=/"));
        assertTrue(services.get(0).getServiceTags().contains("service.port=" + SERVICE_PORT));

        List<ServiceHealth> checks = health.getHealthyServiceInstances(SERVICE_NAME).getResponse();
        assertEquals(1, checks.size());
        assertEquals(SERVICE_PORT, checks.get(0).getService().getPort());
        assertEquals("localhost", checks.get(0).getService().getAddress());

        // let stop the route
        context().stopRoute("exposed");

        // the service should be removed once the route is stopped
        assertTrue(catalog.getService(SERVICE_NAME).getResponse().isEmpty());
    }
}

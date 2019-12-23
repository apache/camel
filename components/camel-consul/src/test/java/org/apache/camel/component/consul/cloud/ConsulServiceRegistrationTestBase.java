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
package org.apache.camel.component.consul.cloud;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbitz.consul.CatalogClient;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.health.ServiceHealth;
import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.consul.ConsulTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.util.SocketUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ConsulServiceRegistrationTestBase extends ConsulTestSupport {
    protected static final String SERVICE_ID = UUID.randomUUID().toString();
    protected static final String SERVICE_NAME = "my-service";
    protected static final String SERVICE_HOST = "localhost";
    protected static final int SERVICE_PORT = SocketUtils.findAvailableTcpPort();

    protected Map<String, String> getMetadata() {
        return Collections.emptyMap();
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

    @Test
    public void testRegistrationFromRoute() throws Exception {
        final CatalogClient catalog = getConsul().catalogClient();
        final HealthClient health = getConsul().healthClient();

        // the service should not be registered as the route is not running
        assertTrue(catalog.getService(SERVICE_NAME).getResponse().isEmpty());

        // let start the route
        context().getRouteController().startRoute(SERVICE_ID);

        // check that service has been registered
        List<CatalogService> services = catalog.getService(SERVICE_NAME).getResponse();
        assertEquals(1, services.size());
        assertEquals(SERVICE_PORT, services.get(0).getServicePort());
        assertEquals("localhost", services.get(0).getServiceAddress());
        assertTrue(services.get(0).getServiceTags().contains(ServiceDefinition.SERVICE_META_PROTOCOL + "=http"));
        assertTrue(services.get(0).getServiceTags().contains(ServiceDefinition.SERVICE_META_PATH + "=/service/endpoint"));

        getMetadata().forEach((k, v) -> {
            assertTrue(services.get(0).getServiceTags().contains(k + "=" + v));
        });

        List<ServiceHealth> checks = health.getHealthyServiceInstances(SERVICE_NAME).getResponse();
        assertEquals(1, checks.size());
        assertEquals(SERVICE_PORT, checks.get(0).getService().getPort());
        assertEquals("localhost", checks.get(0).getService().getAddress());

        // let stop the route
        context().getRouteController().stopRoute(SERVICE_ID);

        // the service should be removed once the route is stopped
        assertTrue(catalog.getService(SERVICE_NAME).getResponse().isEmpty());
    }
}

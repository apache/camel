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

import java.util.List;

import com.orbitz.consul.CatalogClient;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.health.ServiceHealth;
import org.apache.camel.component.consul.ConsulTestSupport;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConsulServiceRegistryTest extends ConsulTestSupport {
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSimpleServiceRegistration() {
        ConsulServiceRegistry registry = new ConsulServiceRegistry();
        registry.setCamelContext(context());
        registry.setUrl(consulUrl());
        registry.setServiceHost("service-host");
        registry.setOverrideServiceHost(true);
        registry.start();

        registry.register(DefaultServiceDefinition.builder().withId("my-id").withName("service-name").withHost("my-host").withPort(9091).build());

        final CatalogClient catalog = getConsul().catalogClient();
        final HealthClient health = getConsul().healthClient();

        // check that service has been registered
        List<CatalogService> services = catalog.getService("service-name").getResponse();
        assertEquals(1, services.size());
        assertEquals(9091, services.get(0).getServicePort());
        assertEquals("service-host", services.get(0).getServiceAddress());
        assertEquals("my-id", services.get(0).getServiceId());

        List<ServiceHealth> checks = health.getHealthyServiceInstances("service-name").getResponse();
        assertEquals(1, checks.size());
        assertEquals(9091, checks.get(0).getService().getPort());
        assertEquals("service-host", checks.get(0).getService().getAddress());
        assertEquals("my-id", checks.get(0).getService().getId());

        registry.stop();

        // check that service has been de registered on service registry
        // shutdown
        assertEquals(0, catalog.getService("service-name").getResponse().size());
        assertEquals(0, health.getServiceChecks("service-name").getResponse().size());
    }
}

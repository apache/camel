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
package org.apache.camel.component.rest;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DefaultRestRegistryTest {

    private DefaultRestRegistry registry;
    private CamelContext camelContext;

    @Mock
    private Consumer consumer1;

    @Mock
    private Consumer consumer2;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        registry = new DefaultRestRegistry();
        registry.setCamelContext(camelContext);
        registry.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        registry.stop();
        camelContext.stop();
    }

    @Test
    void testAddRestService() {
        registry.addRestService(consumer1, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                "application/json", "application/json", "User", "User",
                "route1", "Get all users");

        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void testAddMultipleRestServices() {
        registry.addRestService(consumer1, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                "application/json", "application/json", null, null,
                "route1", "Get all users");

        registry.addRestService(consumer2, false, "http://localhost:8080/api/orders",
                "http://localhost:8080", "/api", "/orders", "POST",
                "application/json", "application/json", "Order", "Order",
                "route2", "Create order");

        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void testAddMultipleServicesToSameConsumer() {
        registry.addRestService(consumer1, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                null, null, null, null, "route1", "Get users");

        registry.addRestService(consumer1, false, "http://localhost:8080/api/users/{id}",
                "http://localhost:8080", "/api", "/users/{id}", "GET",
                null, null, null, null, "route2", "Get user by id");

        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void testRemoveRestService() {
        registry.addRestService(consumer1, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                null, null, null, null, "route1", "Get users");

        assertThat(registry.size()).isEqualTo(1);

        registry.removeRestService(consumer1);

        assertThat(registry.size()).isEqualTo(0);
    }

    @Test
    void testRemoveNonExistentService() {
        registry.addRestService(consumer1, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                null, null, null, null, "route1", "Get users");

        registry.removeRestService(consumer2);

        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void testListAllRestServices() {
        registry.addRestService(consumer1, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                "application/json", "application/json", "User", "UserResponse",
                "route1", "Get all users");

        List<RestRegistry.RestService> services = registry.listAllRestServices();

        assertThat(services).hasSize(1);

        RestRegistry.RestService service = services.get(0);
        assertThat(service.getUrl()).isEqualTo("http://localhost:8080/api/users");
        assertThat(service.getBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(service.getBasePath()).isEqualTo("/api");
        assertThat(service.getUriTemplate()).isEqualTo("/users");
        assertThat(service.getMethod()).isEqualTo("GET");
        assertThat(service.getConsumes()).isEqualTo("application/json");
        assertThat(service.getProduces()).isEqualTo("application/json");
        assertThat(service.getInType()).isEqualTo("User");
        assertThat(service.getOutType()).isEqualTo("UserResponse");
        assertThat(service.getDescription()).isEqualTo("Get all users");
        assertThat(service.getConsumer()).isSameAs(consumer1);
        assertThat(service.isContractFirst()).isFalse();
    }

    @Test
    void testContractFirstService() {
        registry.addRestService(consumer1, true, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                null, null, null, null, "route1", "Get users");

        List<RestRegistry.RestService> services = registry.listAllRestServices();
        assertThat(services.get(0).isContractFirst()).isTrue();
    }

    @Test
    void testSizeWithEmptyRegistry() {
        assertThat(registry.size()).isEqualTo(0);
    }

    @Test
    void testListAllRestServicesEmpty() {
        List<RestRegistry.RestService> services = registry.listAllRestServices();
        assertThat(services).isEmpty();
    }

    @Test
    void testServiceState() {
        registry.addRestService(consumer1, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                null, null, null, null, "route1", "Get users");

        List<RestRegistry.RestService> services = registry.listAllRestServices();
        // Non-stateful consumer returns Stopped state
        assertThat(services.get(0).getState()).isEqualTo("Stopped");
    }

    @Test
    void testApiDocAsJsonWithNoEndpoints() {
        String apiDoc = registry.apiDocAsJson();
        assertThat(apiDoc).isNull();
    }

    @Test
    void testStopClearsRegistry() throws Exception {
        registry.addRestService(consumer1, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                null, null, null, null, "route1", "Get users");

        assertThat(registry.size()).isEqualTo(1);

        registry.stop();

        assertThat(registry.size()).isEqualTo(0);
    }
}

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
package org.apache.camel.impl.cloud;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.model.RouteDefinition;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ServiceRegistryTest extends ContextTestSupport {

    // *********************
    // Set up
    // *********************

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }


    // *********************
    // Tests
    // *********************

    @Test
    public void testServiceRegistrationWithRouteIdAndGroup() throws Exception {
        final String serviceName = UUID.randomUUID().toString();
        final String serviceId = UUID.randomUUID().toString();
        final int port = 9090;

        context.addRouteDefinition(
            new RouteDefinition()
                .from("direct:start")
                .routeGroup(serviceName)
                .routeId(serviceId)
                .routeProperty(ServiceDefinition.SERVICE_META_HOST, "localhost")
                .routeProperty(ServiceDefinition.SERVICE_META_PORT, "" + port)
                .routeProperty("service.meta1", "meta1")
                .routeProperty("meta2", "meta2")
                .routePolicy(new ServiceRegistrationRoutePolicy())
                .to("mock:end")
        );

        InMemoryServiceRegistry sr = new InMemoryServiceRegistry();

        context.addService(sr);
        context.start();

        final Map<String, ServiceDefinition> defs = sr.getDefinitions();

        Assertions.assertThat(defs).hasSize(1);

        // basic properties
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("name", serviceName);
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("id", serviceId);
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("host", "localhost");
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("port", port);

        // metadata
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_NAME, serviceName);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_ID, serviceId);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_HOST, "localhost");
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_PORT, "" + port);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry("service.meta1", "meta1");
        Assertions.assertThat(defs.get(serviceId).getMetadata()).doesNotContainKeys("meta2");
    }

    @Test
    public void testServiceRegistrationWithRouteIdAndGroupOverride() throws Exception {
        final String serviceName = UUID.randomUUID().toString();
        final String serviceId = UUID.randomUUID().toString();
        final int port = 9090;

        context.addRouteDefinition(
            new RouteDefinition()
                .from("direct:start")
                .routeGroup("service-name")
                .routeId("service-id")
                .routeProperty(ServiceDefinition.SERVICE_META_NAME, serviceName)
                .routeProperty(ServiceDefinition.SERVICE_META_ID, serviceId)
                .routeProperty(ServiceDefinition.SERVICE_META_HOST, "localhost")
                .routeProperty(ServiceDefinition.SERVICE_META_PORT, "" + port)
                .routeProperty("service.meta1", "meta1")
                .routeProperty("meta2", "meta2")
                .routePolicy(new ServiceRegistrationRoutePolicy())
                .to("mock:end")
        );

        InMemoryServiceRegistry sr = new InMemoryServiceRegistry();

        context.addService(sr);
        context.start();

        final Map<String, ServiceDefinition> defs = sr.getDefinitions();

        Assertions.assertThat(defs).hasSize(1);

        // basic properties
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("name", serviceName);
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("id", serviceId);
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("host", "localhost");
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("port", port);

        // metadata
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_NAME, serviceName);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_ID, serviceId);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_HOST, "localhost");
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_PORT, "" + port);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry("service.meta1", "meta1");
        Assertions.assertThat(defs.get(serviceId).getMetadata()).doesNotContainKeys("meta2");
    }

    // *********************
    // Tests
    // *********************

    @Test
    public void testServiceRegistrationWithRouteProperties() throws Exception {
        final String serviceName = UUID.randomUUID().toString();
        final String serviceId = UUID.randomUUID().toString();
        final int port = 9090;

        context.addRouteDefinition(
            new RouteDefinition()
                .from("direct:start")
                .routeProperty(ServiceDefinition.SERVICE_META_NAME, serviceName)
                .routeProperty(ServiceDefinition.SERVICE_META_ID, serviceId)
                .routeProperty(ServiceDefinition.SERVICE_META_HOST, "localhost")
                .routeProperty(ServiceDefinition.SERVICE_META_PORT, "" + port)
                .routeProperty("service.meta1", "meta1")
                .routeProperty("meta2", "meta2")
                .routePolicy(new ServiceRegistrationRoutePolicy())
                .to("mock:end")
        );

        InMemoryServiceRegistry sr = new InMemoryServiceRegistry();

        context.addService(sr);
        context.start();

        final Map<String, ServiceDefinition> defs = sr.getDefinitions();

        Assertions.assertThat(defs).hasSize(1);

        // basic properties
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("name", serviceName);
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("id", serviceId);
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("host", "localhost");
        Assertions.assertThat(defs.values()).first().hasFieldOrPropertyWithValue("port", port);

        // metadata
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_NAME, serviceName);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_ID, serviceId);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_HOST, "localhost");
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_PORT, "" + port);
        Assertions.assertThat(defs.get(serviceId).getMetadata()).containsEntry("service.meta1", "meta1");
        Assertions.assertThat(defs.get(serviceId).getMetadata()).doesNotContainKeys("meta2");
    }

    // *********************
    // Helpers
    // *********************

    private static class InMemoryServiceRegistry extends AbstractServiceRegistry {
        private final ConcurrentMap<String, ServiceDefinition> definitions;

        public InMemoryServiceRegistry() {
            super(UUID.randomUUID().toString());

            this.definitions = new ConcurrentHashMap<>();
        }

        @Override
        public void register(ServiceDefinition definition) {
            Objects.requireNonNull(definition.getId(), "ServiceDefinition ID");
            Objects.requireNonNull(definition.getName(), "ServiceDefinition Name");

            definitions.put(definition.getId(), definition);
        }

        @Override
        public void deregister(ServiceDefinition definition) {
            Objects.requireNonNull(definition.getId(), "ServiceDefinition ID");
            Objects.requireNonNull(definition.getName(), "ServiceDefinition Name");

            definitions.remove(definition.getId());
        }

        @Override
        protected void doStart() throws Exception {
        }

        @Override
        protected void doStop() throws Exception {
            definitions.clear();
        }

        Map<String, ServiceDefinition> getDefinitions() {
            return Collections.unmodifiableMap(definitions);
        }
    }
}

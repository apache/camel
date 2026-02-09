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
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.support.DefaultConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestRegistryStatefulTest {

    private DefaultRestRegistry registry;
    private CamelContext camelContext;

    @Mock
    private Endpoint mockEndpoint;

    @Mock
    private Processor mockProcessor;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();

        // Configure mock endpoint to return the CamelContext
        when(mockEndpoint.getCamelContext()).thenReturn(camelContext);

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
    void testServiceStateWithStatefulConsumer() throws Exception {
        // Create a real DefaultConsumer which is a StatefulService
        TestConsumer consumer = new TestConsumer(mockEndpoint, mockProcessor);

        registry.addRestService(consumer, false, "http://localhost:8080/api/users",
                "http://localhost:8080", "/api", "/users", "GET",
                "application/json", "application/json", null, null,
                "route1", "Get users");

        List<RestRegistry.RestService> services = registry.listAllRestServices();
        assertThat(services).hasSize(1);

        // Before starting, status should be Stopped
        assertThat(services.get(0).getState()).isEqualTo("Stopped");

        // Start the consumer
        consumer.start();

        // After starting, status should be Started
        assertThat(services.get(0).getState()).isEqualTo("Started");

        // Stop the consumer
        consumer.stop();

        // After stopping, status should be Stopped
        assertThat(services.get(0).getState()).isEqualTo("Stopped");
    }

    @Test
    void testServiceStateWithStartedStatefulConsumer() throws Exception {
        TestConsumer consumer = new TestConsumer(mockEndpoint, mockProcessor);
        consumer.start();

        registry.addRestService(consumer, false, "http://localhost:8080/api/orders",
                "http://localhost:8080", "/api", "/orders", "POST",
                null, null, null, null, "route2", "Create order");

        List<RestRegistry.RestService> services = registry.listAllRestServices();
        assertThat(services.get(0).getState()).isEqualTo("Started");

        consumer.stop();
    }

    @Test
    void testServiceStateWithSuspendedConsumer() throws Exception {
        TestConsumer consumer = new TestConsumer(mockEndpoint, mockProcessor);
        consumer.start();
        consumer.suspend();

        registry.addRestService(consumer, false, "http://localhost:8080/api/items",
                "http://localhost:8080", "/api", "/items", "DELETE",
                null, null, null, null, "route3", "Delete item");

        List<RestRegistry.RestService> services = registry.listAllRestServices();
        assertThat(services.get(0).getState()).isEqualTo("Suspended");

        consumer.resume();
        consumer.stop();
    }

    /**
     * Test consumer that extends DefaultConsumer for testing stateful service behavior
     */
    private static class TestConsumer extends DefaultConsumer {

        public TestConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected void doStart() throws Exception {
            // No-op for test
        }

        @Override
        protected void doStop() throws Exception {
            // No-op for test
        }
    }
}

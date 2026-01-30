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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestEndpointProducerConsumerTest {

    private CamelContext camelContext;
    private SimpleRegistry registry;
    private RestComponent component;

    @Mock
    private RestProducerFactory mockProducerFactory;

    @Mock
    private RestConsumerFactory mockConsumerFactory;

    @Mock
    private Producer mockProducer;

    @Mock
    private Consumer mockConsumer;

    @BeforeEach
    void setUp() throws Exception {
        registry = new SimpleRegistry();
        camelContext = new DefaultCamelContext(registry);
        component = new RestComponent();
        component.setCamelContext(camelContext);
        camelContext.addComponent("rest", component);
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
    }

    @Test
    void testCreateProducerWithRegisteredFactory() throws Exception {
        registry.bind("myProducerFactory", mockProducerFactory);

        when(mockProducerFactory.createProducer(
                any(CamelContext.class), eq("http://localhost:8080"), eq("get"),
                eq("users"), any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockProducer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost:8080&producerComponentName=myProducerFactory");

        Producer producer = endpoint.createProducer();

        assertThat(producer).isNotNull();
        assertThat(producer).isInstanceOf(RestProducer.class);
    }

    @Test
    void testCreateProducerWithNonRestProducerFactoryComponent() throws Exception {
        registry.bind("myComponent", new Object());

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost:8080&producerComponentName=myComponent");

        assertThatThrownBy(endpoint::createProducer)
                .hasMessageContaining("RestProducerFactory");
    }

    @Test
    void testCreateConsumerWithRegisteredFactory() throws Exception {
        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost:8080&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithNonRestConsumerFactoryComponent() throws Exception {
        registry.bind("myComponent", new Object());

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost:8080&consumerComponentName=myComponent");

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        }))
                .hasMessageContaining("RestConsumerFactory");
    }

    @Test
    void testProducerWithConsumerFallback() throws Exception {
        // Register a factory that implements both producer and consumer
        RestProducerFactory dualFactory = mock(RestProducerFactory.class);
        registry.bind("dualFactory", dualFactory);

        when(dualFactory.createProducer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockProducer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost:8080&consumerComponentName=dualFactory");

        // With no explicit producer component, it should fall back to consumer component
        Producer producer = endpoint.createProducer();

        assertThat(producer).isNotNull();
    }

    @Test
    void testCreateProducerWithEmptyHost() throws Exception {
        RestEndpoint endpoint = new RestEndpoint("rest:get:users", component);
        endpoint.setMethod("get");
        endpoint.setPath("users");
        endpoint.setHost("");
        endpoint.setParameters(new HashMap<>());

        assertThatThrownBy(endpoint::createProducer)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hostname must be configured");
    }

    @Test
    void testCreateConsumerWithRestConfiguration() throws Exception {
        RestConfiguration restConfig = new RestConfiguration();
        restConfig.setHost("localhost");
        restConfig.setPort(9090);
        restConfig.setScheme("https");
        restConfig.setContextPath("/api");
        camelContext.setRestConfiguration(restConfig);

        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithHostNameResolver() throws Exception {
        RestConfiguration restConfig = new RestConfiguration();
        restConfig.setHostNameResolver(RestConfiguration.RestHostNameResolver.localIp);
        camelContext.setRestConfiguration(restConfig);

        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithAllLocalIpResolver() throws Exception {
        RestConfiguration restConfig = new RestConfiguration();
        restConfig.setHostNameResolver(RestConfiguration.RestHostNameResolver.allLocalIp);
        camelContext.setRestConfiguration(restConfig);

        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithLocalHostNameResolver() throws Exception {
        RestConfiguration restConfig = new RestConfiguration();
        restConfig.setHostNameResolver(RestConfiguration.RestHostNameResolver.localHostName);
        camelContext.setRestConfiguration(restConfig);

        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithUriTemplateStartingWithSlash() throws Exception {
        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users:/{id}?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithPathNotStartingWithSlash() throws Exception {
        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithContextPathStartingWithSlash() throws Exception {
        RestConfiguration restConfig = new RestConfiguration();
        restConfig.setContextPath("/api/v1");
        camelContext.setRestConfiguration(restConfig);

        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithContextPathNotStartingWithSlash() throws Exception {
        RestConfiguration restConfig = new RestConfiguration();
        restConfig.setContextPath("api/v1");
        camelContext.setRestConfiguration(restConfig);

        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithNonDefaultPort() throws Exception {
        RestConfiguration restConfig = new RestConfiguration();
        restConfig.setPort(9090);
        camelContext.setRestConfiguration(restConfig);

        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
    }

    @Test
    void testCreateConsumerWithUriTemplate() throws Exception {
        registry.bind("myConsumerFactory", mockConsumerFactory);

        when(mockConsumerFactory.createConsumer(
                any(CamelContext.class), any(), any(),
                any(), any(), any(), any(),
                any(RestConfiguration.class), any(Map.class)))
                .thenReturn(mockConsumer);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint(
                "rest:get:users:{id}?host=localhost&consumerComponentName=myConsumerFactory");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertThat(consumer).isNotNull();
        assertThat(endpoint.getUriTemplate()).isEqualTo("{id}");
    }
}

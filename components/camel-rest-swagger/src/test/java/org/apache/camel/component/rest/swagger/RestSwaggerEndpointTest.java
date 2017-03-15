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
package org.apache.camel.component.rest.swagger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.util.Collections.singletonMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Producer;
import org.apache.camel.component.jetty9.JettyHttpComponent9;
import org.apache.camel.component.undertow.UndertowComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.LoadPropertiesException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestSwaggerEndpointTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldComplainForUnknownOperations() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.findComponents()).thenReturn(singletonMap("undertow", new Properties()));
        when(camelContext.getComponent("undertow", true, false)).thenReturn(new UndertowComponent());

        final RestSwaggerComponent component = new RestSwaggerComponent(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:unknown", "unknown", component);
        endpoint.setEndpoint("http://api.example.com");

        endpoint.createProducer();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldComplainIfNoEndpointPropertyIsGiven() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById",
            component);

        endpoint.determineEndpoint();
    }

    @Test
    public void shouldCreateProducers() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.findComponents()).thenReturn(singletonMap("undertow", new Properties()));
        when(camelContext.getComponent("undertow", true, false)).thenReturn(new UndertowComponent());

        final RestSwaggerComponent component = new RestSwaggerComponent(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById",
            component);
        endpoint.setEndpoint("http://api.example.com");

        final Producer producer = endpoint.createProducer();

        assertThat(producer).isNotNull();
    }

    @Test
    public void shouldDetermineRestProducerFactoryIfOnlyOneProducerIsAvailable() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);

        when(camelContext.findComponents()).thenReturn(singletonMap("undertow", new Properties()));
        when(camelContext.getComponent("undertow", true, false)).thenReturn(new UndertowComponent());

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:test", "test",
            new RestSwaggerComponent());
        endpoint.setCamelContext(camelContext);

        final RestProducerFactory restProducerFactory = endpoint.determineRestProducerFactory();

        assertThat(restProducerFactory).isInstanceOf(UndertowComponent.class);
    }

    @Test
    public void shouldDetermineRestProducerFactoryViaComponentName() throws Exception {
        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint();
        endpoint.setCamelContext(new DefaultCamelContext());
        endpoint.setDelegateName("undertow");

        final RestProducerFactory restProducerFactory = endpoint.determineRestProducerFactory();

        assertThat(restProducerFactory).isInstanceOf(UndertowComponent.class);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailFindingComponentToUseIfMoreThanOneIsAvailable() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);

        final Map<String, Properties> found = new HashMap<>();
        found.put("undertow", new Properties());
        found.put("jetty", new Properties());

        when(camelContext.findComponents()).thenReturn(found);
        when(camelContext.getComponent("undertow", true, false)).thenReturn(new UndertowComponent());
        when(camelContext.getComponent("jetty", true, false)).thenReturn(new JettyHttpComponent9());

        RestSwaggerEndpoint.tryFindingComponentToUse(camelContext);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailFindingComponentToUseIfNoneAreAvailable() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);

        when(camelContext.findComponents()).thenReturn(Collections.emptyMap());
        when(camelContext.getComponent("undertow", true, false)).thenReturn(new UndertowComponent());
        when(camelContext.getComponent("jetty", true, false)).thenReturn(new JettyHttpComponent9());

        RestSwaggerEndpoint.tryFindingComponentToUse(camelContext);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailUsingSpecifiedComponentIfItsNotAvaiable() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);

        when(camelContext.getComponent("my-component")).thenReturn(null);

        RestSwaggerEndpoint.tryUsingSpecifiedComponent(camelContext, "my-component");
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailUsingSpecifiedComponentIfThatComponentIsNotRestProducerFactory() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);

        when(camelContext.getComponent("my-component")).thenReturn(mock(Component.class));

        RestSwaggerEndpoint.tryUsingSpecifiedComponent(camelContext, "my-component");
    }

    @Test
    public void shouldHaveADefaultSpecificationPathProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById",
            component);

        assertThat(endpoint.getSpecificationPath()).isEqualTo("swagger.json");
    }

    @Test
    public void shouldHonourComponentEndpointProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setEndpoint("https://api.component.com");

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById",
            component);

        assertThat(endpoint.determineEndpoint()).isEqualTo("https://api.component.com");
    }

    @Test
    public void shouldHonourComponentSpecificationPathProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setSpecificationPath("component.json");

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById",
            component);

        assertThat(endpoint.getSpecificationPath()).isEqualTo("component.json");
    }

    @Test
    public void shouldHonourDelegateSearchPrecedence() throws LoadPropertiesException, IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getComponent("undertow")).thenReturn(new UndertowComponent());
        when(camelContext.getComponent("jetty")).thenReturn(new JettyHttpComponent9());

        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setDelegateName("undertow");

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:test", "test", component);
        endpoint.setCamelContext(camelContext);

        assertThat(endpoint.determineRestProducerFactory()).isInstanceOf(UndertowComponent.class);

        final UndertowComponent componentDelegate = new UndertowComponent();
        component.setDelegate(componentDelegate);

        assertThat(endpoint.determineRestProducerFactory()).isSameAs(componentDelegate);

        endpoint.setDelegateName("jetty");

        assertThat(endpoint.determineRestProducerFactory()).isInstanceOf(JettyHttpComponent9.class);

        final JettyHttpComponent9 endpointDelegate = new JettyHttpComponent9();
        endpoint.setDelegate(endpointDelegate);

        assertThat(endpoint.determineRestProducerFactory()).isSameAs(endpointDelegate);
    }

    @Test
    public void shouldHonourEndpointsEndpointOverComponentsEndpointProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setEndpoint("https://api.component.com");

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById",
            component);
        endpoint.setEndpoint("https://api.endpoint.com");

        assertThat(endpoint.determineEndpoint()).isEqualTo("https://api.endpoint.com");
    }

    @Test
    public void shouldHonourEndpointUriPathSpecificationPathProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setSpecificationPath("component.json");

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById:endpoint.json",
            "getPetById:endpoint.json", component);

        assertThat(endpoint.getSpecificationPath()).isEqualTo("endpoint.json");
    }

    @Test
    public void shouldLoadSwaggerSpecifications() throws IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getApplicationContextClassLoader()).thenReturn(RestSwaggerEndpoint.class.getClassLoader());

        assertThat(RestSwaggerEndpoint.loadSpecificationFrom(camelContext, "swagger.json")).isNotNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRaiseExceptionsForMissingSpecifications() throws IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getApplicationContextClassLoader()).thenReturn(RestSwaggerEndpoint.class.getClassLoader());

        RestSwaggerEndpoint.loadSpecificationFrom(camelContext, "non-existant.json");
    }

    @Test
    public void shouldTryFindingComponentToUse() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);

        when(camelContext.findComponents()).thenReturn(singletonMap("undertow", new Properties()));
        when(camelContext.getComponent("undertow", true, false)).thenReturn(new UndertowComponent());

        final RestProducerFactory producer = RestSwaggerEndpoint.tryFindingComponentToUse(camelContext);

        assertThat(producer).isNotNull();
    }
}

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
package org.apache.camel.impl;

import java.util.Map;
import java.util.SortedMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.InvalidPropertyException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.TestSupport;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.component.seda.SedaEndpoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the use of {@link ComponentConfiguration} both from the URI string approach or for creating and mutating Endpoint instances
 * with both regular Components/Endpoints and {@link UriEndpointComponent} instances which have Endpoints annotated
 * with {@link org.apache.camel.spi.UriEndpoint}, {@link org.apache.camel.spi.UriParam} and {@link org.apache.camel.spi.UriParams}
 */
public class ComponentConfigurationTest {
    private static final Logger LOG = LoggerFactory.getLogger(ComponentConfigurationTest.class);

    private CamelContext context;

    @Before
    public void createContext() throws Exception {
        context = new DefaultCamelContext();
        context.addComponent("cheese", new NonUriComponent());
        context.start(); // so that TypeConverters are available
    }

    @After
    public void destroyContext() throws Exception {
        context.stop();
        context = null;
    }

    /**
     * Show we can create a URI from the base URI and the underlying query parameter values
     */
    @Test
    public void testCreateUriStringFromParameters() throws Exception {
        Component component = context.getComponent("seda");
        ComponentConfiguration configuration = component.createComponentConfiguration();
        assertNotNull("Should have created a ComponentConfiguration for component " + component,
                configuration);

        // configure the base URI properties
        configuration.setBaseUri("foo");

        // lets try set and get a valid parameter
        configuration.setParameter("concurrentConsumers", 5);
        configuration.setParameter("size", 1000);

        String uriString = configuration.getUriString();
        assertEquals("uriString", "foo?concurrentConsumers=5&size=1000", uriString);
    }

    /**
     * Show we can create a URI from the base URI and the underlying query parameter values
     * on any endpoint (even if its not a {@link UriEndpointComponent})
     */
    @Test
    public void testCreateUriStringFromParametersOnDefaultComponent() throws Exception {
        Component component = context.getComponent("cheese");
        ComponentConfiguration configuration = component.createComponentConfiguration();
        assertNotNull("Should have created a ComponentConfiguration for component " + component,
                configuration);

        // configure the base URI properties
        configuration.setBaseUri("somePath");

        // lets try set and get a valid parameter
        configuration.setParameter("foo", "something");
        configuration.setParameter("bar", 123);

        String uriString = configuration.getUriString();
        assertEquals("uriString", "somePath?bar=123&foo=something", uriString);
    }

    /**
     * Test that parameters are strictly typed on {@link UriEndpointComponent}s
     */
    @Test
    public void testSetParametersFromUriString() throws Exception {
        Component component = context.getComponent("seda");
        ComponentConfiguration configuration = component.createComponentConfiguration();
        assertNotNull("Should have created a ComponentConfiguration for component " + component,
                configuration);

        // configure the uri and query parameters
        configuration.setUriString("foo?concurrentConsumers=5&size=1000");

        // notice the parameters are all correctly typed due to the use of a UriEndpointComponent
        // and the associated @UriEndpoint / @UriParam annotations
        assertEquals("concurrentConsumers", 5, configuration.getParameter("concurrentConsumers"));
        assertEquals("size", 1000, configuration.getParameter("size"));

        configuration.setUriString("foo?concurrentConsumers=9&size=2000");

        assertEquals("concurrentConsumers", 9, configuration.getParameter("concurrentConsumers"));
        assertEquals("size", 2000, configuration.getParameter("size"));
    }

    /**
     * Tests that parameters can be used on non-{@link UriEndpointComponent} implementations
     * but that their types tend to be String until we try to create an Endpoint
     */
    @Test
    public void testSetParametersFromUriStringOnDefaultComponent() throws Exception {
        Component component = context.getComponent("cheese");
        ComponentConfiguration configuration = component.createComponentConfiguration();
        assertNotNull("Should have created a ComponentConfiguration for component " + component,
                configuration);

        // configure the uri and query parameters
        configuration.setUriString("somePath?foo=something&bar=123");

        // notice the parameters are all Strings since we don't use UriEndpointComponent
        assertEquals("foo", "something", configuration.getParameter("foo"));
        assertEquals("bar", "123", configuration.getParameter("bar"));

        configuration.setUriString("somePath?foo=another&bar=456");

        assertEquals("foo", "another", configuration.getParameter("foo"));
        assertEquals("bar", "456", configuration.getParameter("bar"));
    }

    /**
     * Use the {@link ComponentConfiguration}, set some parameters then lets turn it into an endpoint
     */
    @Test
    public void testCreateNewSedaUriEndpoint() throws Exception {
        Component component = context.getComponent("seda");
        ComponentConfiguration configuration = component.createComponentConfiguration();
        assertNotNull("Should have created a ComponentConfiguration for component " + component,
                configuration);

        // configure the base URI properties
        configuration.setBaseUri("foo");

        // lets try set and get a valid parameter
        configuration.setParameter("concurrentConsumers", 5);
        configuration.setParameter("size", 1000);

        // lets try set an invalid parameter
        try {
            configuration.setParameter("doesNotExist", 1000);
            fail("Should have got InvalidPropertyException thrown!");
        } catch (InvalidPropertyException e) {
            LOG.info("Got expected exception: " + e);
        }

        SedaEndpoint endpoint = TestSupport
                .assertIsInstanceOf(SedaEndpoint.class, configuration.createEndpoint());
        assertEquals("concurrentConsumers", 5, endpoint.getConcurrentConsumers());
        assertEquals("size", 1000, endpoint.getSize());

        assertEquals("endpoint uri", "foo?concurrentConsumers=5&size=1000", endpoint.getEndpointUri());

        // lets try configure a parameter
        configuration.setEndpointParameter(endpoint, "concurrentConsumers", 6);
        assertEquals("concurrentConsumers", 6, endpoint.getConcurrentConsumers());

        // lets try set an invalid parameter
        try {
            configuration.setEndpointParameter(endpoint, "doesNotExist", 1000);
            fail("Should have got InvalidPropertyException thrown!");
        } catch (InvalidPropertyException e) {
            LOG.info("Got expected exception: " + e);
        }
    }

    @Test
    public void testCreateNewDefaultComponentEndpoint() throws Exception {
        Component component = context.getComponent("cheese");
        ComponentConfiguration configuration = component.createComponentConfiguration();
        assertNotNull("Should have created a ComponentConfiguration for component " + component,
                configuration);

        // configure the base URI properties
        configuration.setBaseUri("something");

        // lets try set and get a valid parameter
        configuration.setParameter("foo", "xyz");
        configuration.setParameter("bar", 5);

        NonUriEndpoint endpoint = TestSupport
                .assertIsInstanceOf(NonUriEndpoint.class, configuration.createEndpoint());
        assertEquals("foo", "xyz", endpoint.getFoo());
        assertEquals("bar", 5, endpoint.getBar());

        LOG.info("Created endpoint {} on URI {}", endpoint, endpoint.getEndpointUri());

        // lets try configure a parameter
        configuration.setEndpointParameter(endpoint, "bar", 6);
        assertEquals("bar", 6, endpoint.getBar());

        // lets try configure an invalid parameter
        try {
            configuration.setEndpointParameter(endpoint, "doesNotExist", 1000);
            fail("Should have got InvalidPropertyException thrown!");
        } catch (InvalidPropertyException e) {
            LOG.info("Got expected exception: " + e);
        }

        ComponentConfiguration badConfiguration = component.createComponentConfiguration();
        badConfiguration.setBaseUri(configuration.getBaseUri());
        badConfiguration.setParameters(configuration.getParameters());

        // lets try set an invalid parameter on a configuration
        // there is no way to validate on non UriEndpoint unless the endpoint
        // creates its own configuration object so this always works...
        badConfiguration.setParameter("doesNotExist", 1000);

        // however it fails if we now try create an
        try {
            badConfiguration.createEndpoint();
            fail("Should have got ResolveEndpointFailedException thrown!");
        } catch (ResolveEndpointFailedException e) {
            LOG.info("Got expected exception: " + e);
        }
    }

    /**
     * Shows we can introspect a {@link UriEndpointComponent} and find all the available parameters
     * along with their types and {@link ParameterConfiguration}
     */
    @Test
    public void testIntrospectSedaEndpointParameters() throws Exception {
        Component component = context.getComponent("seda");
        ComponentConfiguration configuration = component.createComponentConfiguration();
        assertNotNull("Should have created a ComponentConfiguration for component " + component,
                configuration);

        SortedMap<String, ParameterConfiguration> parameterMap = configuration.getParameterConfigurationMap();
        assertTrue("getParameterConfigurationMap() should not be empty!", !parameterMap.isEmpty());

        ParameterConfiguration concurrentConsumersConfig = parameterMap.get("concurrentConsumers");
        assertNotNull("parameterMap[concurrentConsumers] should not be null!", concurrentConsumersConfig);
        assertEquals("concurrentConsumersConfig.getName()", "concurrentConsumers",
                concurrentConsumersConfig.getName());
        assertEquals("concurrentConsumersConfig.getParameterType()", int.class,
                concurrentConsumersConfig.getParameterType());

        LOG.info("{} has has configuration properties {}", component, parameterMap.keySet());
    }

    /**
     * Shows we can introspect the parameters of a DefaultComponent (i.e. a non {@link UriEndpointComponent})
     * though we only get to introspect the parameter values from teh current configuration
     */
    @Test
    public void testIntrospectDefaultComponentParameters() throws Exception {
        Component component = context.getComponent("cheese");
        ComponentConfiguration configuration = component.createComponentConfiguration();
        assertNotNull("Should have created a ComponentConfiguration for component " + component,
                configuration);

        SortedMap<String, ParameterConfiguration> parameterMap = configuration.getParameterConfigurationMap();
        assertTrue("getParameterConfigurationMap() should be empty as we have no parameters yet",
                parameterMap.isEmpty());

        // configure the uri and query parameters
        configuration.setUriString("somePath?foo=something&bar=123");

        parameterMap = configuration.getParameterConfigurationMap();
        assertEquals("getParameterConfigurationMap() size", 2, parameterMap.size());
        ParameterConfiguration barConfiguration = configuration.getParameterConfiguration("bar");
        assertNotNull("should hav a configuration for 'bar'", barConfiguration);
        assertEquals("barConfiguration.getName()", "bar", barConfiguration.getName());
        assertEquals("barConfiguration.getParameterType()", String.class,
                barConfiguration.getParameterType());
    }

    /**
     * Shows how we can use the configuration to get and set parameters directly on the endpoint
     * for a {@link UriEndpointComponent}
     */
    @Test
    public void testConfigureAnExistingSedaEndpoint() throws Exception {
        SedaEndpoint endpoint = context.getEndpoint("seda:cheese?concurrentConsumers=5", SedaEndpoint.class);
        SedaComponent component = endpoint.getComponent();
        ComponentConfiguration configuration = component.createComponentConfiguration();

        assertEquals("concurrentConsumers", 5, endpoint.getConcurrentConsumers());
        assertEquals("concurrentConsumers", 5,
                configuration.getEndpointParameter(endpoint, "concurrentConsumers"));

        // lets try set and get some valid parameters
        configuration.setEndpointParameter(endpoint, "concurrentConsumers", 10);
        Object concurrentConsumers = configuration.getEndpointParameter(endpoint, "concurrentConsumers");
        assertEquals("endpoint.concurrentConsumers", 10, concurrentConsumers);

        configuration.setEndpointParameter(endpoint, "size", 1000);
        Object size = configuration.getEndpointParameter(endpoint, "size");
        assertEquals("endpoint.size", 1000, size);

        // lets try set an invalid parameter
        try {
            configuration.setEndpointParameter(endpoint, "doesNotExist", 1000);
            fail("Should have got InvalidPropertyException thrown!");
        } catch (InvalidPropertyException e) {
            LOG.info("Got expected exception: " + e);
        }
    }


    /**
     * Shows how we can use the configuration to get and set parameters directly on the endpoint
     * which is typesafe and performs validation even if the component is not a {@link UriEndpointComponent}
     */
    @Test
    public void testConfigureAnExistingDefaultEndpoint() throws Exception {
        NonUriEndpoint endpoint = context
                .getEndpoint("cheese:somePath?bar=123&foo=something", NonUriEndpoint.class);
        Component component = endpoint.getComponent();
        ComponentConfiguration configuration = component.createComponentConfiguration();

        assertEquals("bar", 123, endpoint.getBar());
        assertEquals("bar", 123, configuration.getEndpointParameter(endpoint, "bar"));

        // lets try set and get some valid parameters
        configuration.setEndpointParameter(endpoint, "bar", 10);
        Object bar = configuration.getEndpointParameter(endpoint, "bar");
        assertEquals("endpoint.bar", 10, bar);

        configuration.setEndpointParameter(endpoint, "foo", "anotherThing");
        Object foo = configuration.getEndpointParameter(endpoint, "foo");
        assertEquals("endpoint.foo", "anotherThing", foo);

        // lets try set an invalid parameter
        try {
            configuration.setEndpointParameter(endpoint, "doesNotExist", 1000);
            fail("Should have got InvalidPropertyException thrown!");
        } catch (InvalidPropertyException e) {
            LOG.info("Got expected exception: " + e);
        }
    }

    public static class NonUriComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new NonUriEndpoint(uri, this);
        }

    }

    public static class NonUriEndpoint extends DefaultEndpoint {
        private String foo;
        private int bar;

        public NonUriEndpoint(String uri, NonUriComponent component) {
            super(uri, component);
        }

        public int getBar() {
            return bar;
        }

        public void setBar(int bar) {
            this.bar = bar;
        }

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        @Override
        public Producer createProducer() throws Exception {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return false;
        }
    }
}

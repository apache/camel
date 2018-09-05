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
package org.apache.camel.component.kura;

import java.nio.charset.StandardCharsets;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;   
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

public class KuraRouterTest extends Assert {

    TestKuraRouter router = new TestKuraRouter();

    BundleContext bundleContext = mock(BundleContext.class, RETURNS_DEEP_STUBS);

    ConfigurationAdmin configurationAdmin = mock(ConfigurationAdmin.class);

    Configuration configuration = mock(Configuration.class);

    @Before
    public void before() throws Exception {
        given(bundleContext.getBundle().getVersion().toString()).willReturn("version");
        given(bundleContext.getBundle().getSymbolicName()).willReturn("symbolic_name");
        given(bundleContext.getService(any(ServiceReference.class))).willReturn(configurationAdmin);

        router.start(bundleContext);
    }

    @After
    public void after() throws Exception {
        router.stop(bundleContext);
    }

    @Test
    public void shouldCloseCamelContext() throws Exception {
        // When
        router.stop(bundleContext);

        // Then
        Assert.assertEquals(ServiceStatus.Stopped, router.camelContext.getStatus());
    }

    @Test
    public void shouldStartCamelContext() throws Exception {
        // Given
        String message = "foo";
        MockEndpoint mockEndpoint = router.camelContext.getEndpoint("mock:test", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived(message);

        // When
        router.producerTemplate.sendBody("direct:start", message);

        // Then
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void shouldCreateConsumerTemplate() throws Exception {
        assertNotNull(router.consumerTemplate);
    }

    @Test
    public void shouldReturnNoService() {
        given(bundleContext.getServiceReference(any(String.class))).willReturn(null);
        assertNull(router.service(ConfigurationAdmin.class));
    }

    @Test
    public void shouldReturnService() {
        assertNotNull(router.service(ConfigurationAdmin.class));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldValidateLackOfService() {
        given(bundleContext.getServiceReference(any(String.class))).willReturn(null);
        router.requiredService(ConfigurationAdmin.class);
    }

    @Test
    public void shouldLoadXmlRoutes() throws Exception {
        // Given
        given(configurationAdmin.getConfiguration(anyString())).willReturn(configuration);
        Dictionary<String, Object> properties = new Hashtable<>();
        String routeDefinition = IOUtils.toString(getClass().getResource("/route.xml"), StandardCharsets.UTF_8);
        properties.put("kura.camel.symbolic_name.route", routeDefinition);
        given(configuration.getProperties()).willReturn(properties);

        // When
        router.start(router.bundleContext);

        // Then
        assertNotNull(router.camelContext.getRouteDefinition("loaded"));
    }

    static class TestKuraRouter extends KuraRouter {

        @Override
        public void configure() throws Exception {
            from("direct:start").to("mock:test");
        }

        @Override
        protected CamelContext createCamelContext() {
            return new DefaultCamelContext();
        }

    }

}


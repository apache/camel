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
package org.apache.camel.component.smpp;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppComponent</code>
 * 
 * @version 
 */
public class SmppComponentTest {
    
    private SmppComponent component;
    private DefaultCamelContext context;

    @Before
    public void setUp() {
        context = new DefaultCamelContext();
        component = new SmppComponent(context);
    }

    @Test
    public void constructorSmppConfigurationShouldSetTheConfiguration() {
        SmppConfiguration configuration = new SmppConfiguration();
        component = new SmppComponent(configuration);
        
        assertSame(configuration, component.getConfiguration());
    }

    @Test
    public void constructorCamelContextShouldSetTheContext() {
        CamelContext context = new DefaultCamelContext();
        component = new SmppComponent(context);
        
        assertSame(context, component.getCamelContext());
    }

    @Test
    public void createEndpointStringStringMapShouldReturnASmppEndpoint() throws Exception {
        CamelContext context = new DefaultCamelContext();
        component = new SmppComponent(context);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("password", "secret");
        Endpoint endpoint = component.createEndpoint("smpp://smppclient@localhost:2775", "?password=secret", parameters);
        SmppEndpoint smppEndpoint = (SmppEndpoint) endpoint;
        
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getEndpointUri());
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getEndpointKey());
        assertSame(component, smppEndpoint.getComponent());
        assertNotNull(smppEndpoint.getConfiguration());
        assertEquals("secret", smppEndpoint.getConfiguration().getPassword());
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getConnectionString());
        assertEquals(ExchangePattern.InOnly, smppEndpoint.getExchangePattern());
        assertTrue(smppEndpoint.getBinding() instanceof SmppBinding);
        assertNotNull(smppEndpoint.getCamelContext());
    }

    @Test
    public void createEndpointStringStringMapShouldReturnASmppsEndpoint() throws Exception {
        CamelContext context = new DefaultCamelContext();
        component = new SmppComponent(context);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("password", "secret");
        Endpoint endpoint = component.createEndpoint("smpps://smppclient@localhost:2775", "?password=secret", parameters);
        SmppEndpoint smppEndpoint = (SmppEndpoint) endpoint;

        assertEquals("smpps://smppclient@localhost:2775", smppEndpoint.getEndpointUri());
        assertEquals("smpps://smppclient@localhost:2775", smppEndpoint.getEndpointKey());
        assertSame(component, smppEndpoint.getComponent());
        assertNotNull(smppEndpoint.getConfiguration());
        assertEquals("secret", smppEndpoint.getConfiguration().getPassword());
        assertEquals("smpps://smppclient@localhost:2775", smppEndpoint.getConnectionString());
        assertEquals(ExchangePattern.InOnly, smppEndpoint.getExchangePattern());
        assertTrue(smppEndpoint.getBinding() instanceof SmppBinding);
        assertNotNull(smppEndpoint.getCamelContext());
    }
    
    @Test
    public void createEndpointStringStringMapWithoutACamelContextShouldReturnASmppEndpoint() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("password", "secret");
        Endpoint endpoint = component.createEndpoint("smpp://smppclient@localhost:2775", "?password=secret", parameters);
        SmppEndpoint smppEndpoint = (SmppEndpoint) endpoint;
        
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getEndpointUri());
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getEndpointKey());
        assertSame(component, smppEndpoint.getComponent());
        assertNotNull(smppEndpoint.getConfiguration());
        assertEquals("secret", smppEndpoint.getConfiguration().getPassword());
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getConnectionString());
        assertEquals(ExchangePattern.InOnly, smppEndpoint.getExchangePattern());
        assertTrue(smppEndpoint.getBinding() instanceof SmppBinding);
        assertNotNull(smppEndpoint.getCamelContext());
    }

    @Test
    public void allowEmptySystemTypeAndServiceTypeOption() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("systemType", null);
        parameters.put("serviceType", null);
        Endpoint endpoint = component.createEndpoint("smpp://smppclient@localhost:2775", "?systemType=&serviceType=", parameters);
        SmppEndpoint smppEndpoint = (SmppEndpoint) endpoint;

        assertEquals("", smppEndpoint.getConfiguration().getSystemType());
        assertEquals("", smppEndpoint.getConfiguration().getServiceType());
    }

    @Test
    public void createEndpointSmppConfigurationShouldReturnASmppEndpoint() throws Exception {
        SmppConfiguration configuration = new SmppConfiguration();
        Endpoint endpoint = component.createEndpoint(configuration);
        SmppEndpoint smppEndpoint = (SmppEndpoint) endpoint;
        
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getEndpointUri());
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getEndpointKey());
        assertSame(component, smppEndpoint.getComponent());
        assertSame(configuration, smppEndpoint.getConfiguration());
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getConnectionString());
        assertEquals(ExchangePattern.InOnly, smppEndpoint.getExchangePattern());
        assertTrue(smppEndpoint.getBinding() instanceof SmppBinding);
        assertNotNull(smppEndpoint.getCamelContext());
    }

    @Test
    public void createEndpointStringSmppConfigurationShouldReturnASmppEndpoint() throws Exception {
        SmppConfiguration configuration = new SmppConfiguration();
        Endpoint endpoint = component.createEndpoint("smpp://smppclient@localhost:2775?password=password", configuration);
        SmppEndpoint smppEndpoint = (SmppEndpoint) endpoint;
        
        assertEquals("smpp://smppclient@localhost:2775?password=password", smppEndpoint.getEndpointUri());
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getEndpointKey());
        assertSame(component, smppEndpoint.getComponent());
        assertSame(configuration, smppEndpoint.getConfiguration());
        assertEquals("smpp://smppclient@localhost:2775", smppEndpoint.getConnectionString());
        assertEquals(ExchangePattern.InOnly, smppEndpoint.getExchangePattern());
        assertTrue(smppEndpoint.getBinding() instanceof SmppBinding);
        assertNotNull(smppEndpoint.getCamelContext());
    }

    @Test
    public void getterShouldReturnTheSetValues() {
        SmppConfiguration configuration = new SmppConfiguration();
        component.setConfiguration(configuration);
        
        assertSame(configuration, component.getConfiguration());
    }
    
    @Test
    public void createEndpointWithSessionStateListener() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("sessionStateListener", new SessionStateListener() {
            @Override
            public void onStateChange(SessionState arg0, SessionState arg1, Session arg2) {
            }
        });
        context.setRegistry(registry);
        component = new SmppComponent(context);
        SmppEndpoint endpoint = (SmppEndpoint) component.createEndpoint("smpp://smppclient@localhost:2775?password=password&sessionStateListener=#sessionStateListener");
        
        assertNotNull(endpoint.getConfiguration().getSessionStateListener());
    }
}

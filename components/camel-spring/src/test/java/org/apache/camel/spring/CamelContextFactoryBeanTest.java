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
package org.apache.camel.spring;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;
import org.apache.camel.impl.DefaultModelJAXBContextFactory;
import org.apache.camel.impl.DefaultUuidGenerator;
import org.apache.camel.impl.SimpleUuidGenerator;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.UuidGenerator;
import org.springframework.context.support.StaticApplicationContext;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

/**
 * @version 
 */
public class CamelContextFactoryBeanTest extends TestCase {
    
    private CamelContextFactoryBean factory;

    protected void setUp() throws Exception {
        super.setUp();
        
        factory = new CamelContextFactoryBean();
        factory.setId("camelContext");
    }

    public void testGetDefaultUuidGenerator() throws Exception {
        factory.setApplicationContext(new StaticApplicationContext());
        factory.afterPropertiesSet();
        
        UuidGenerator uuidGenerator = factory.getContext().getUuidGenerator();
        
        assertTrue(uuidGenerator instanceof DefaultUuidGenerator);
    }
    
    public void testGetCustomUuidGenerator() throws Exception {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("uuidGenerator", SimpleUuidGenerator.class);
        factory.setApplicationContext(applicationContext);
        factory.afterPropertiesSet();
        
        UuidGenerator uuidGenerator = factory.getContext().getUuidGenerator();
        
        assertTrue(uuidGenerator instanceof SimpleUuidGenerator);
    }

    public void testSetEndpoints() throws Exception {
        // Create a new Camel context and add an endpoint
        CamelContextFactoryBean camelContext = new CamelContextFactoryBean();
        List<CamelEndpointFactoryBean> endpoints = new LinkedList<CamelEndpointFactoryBean>();
        CamelEndpointFactoryBean endpoint = new CamelEndpointFactoryBean();
        endpoint.setId("endpoint1");
        endpoint.setUri("mock:end");
        endpoints.add(endpoint);
        camelContext.setEndpoints(endpoints);

        // Compare the new context with our reference context
        URL expectedContext = getClass().getResource("/org/apache/camel/spring/context-with-endpoint.xml");
        Diff diff = DiffBuilder.compare(expectedContext).withTest(Input.fromJaxb(camelContext))
                .ignoreWhitespace().ignoreComments().checkForSimilar().build();
        assertFalse("Expected context and actual context differ:\n" + diff.toString(), diff.hasDifferences());
    }

    public void testCustomModelJAXBContextFactory() throws Exception {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("customModelJAXBContextFactory", CustomModelJAXBContextFactory.class);
        factory.setApplicationContext(applicationContext);
        factory.afterPropertiesSet();

        ModelJAXBContextFactory modelJAXBContextFactory = factory.getContext().getModelJAXBContextFactory();

        assertTrue(modelJAXBContextFactory instanceof CustomModelJAXBContextFactory);
    }

    private static class CustomModelJAXBContextFactory extends DefaultModelJAXBContextFactory {
        // Do nothing here
    }
}
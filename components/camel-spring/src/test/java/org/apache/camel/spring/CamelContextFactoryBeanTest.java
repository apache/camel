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

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.JAXBContext;

import junit.framework.TestCase;
import org.apache.camel.impl.ActiveMQUuidGenerator;
import org.apache.camel.impl.DefaultModelJAXBContextFactory;
import org.apache.camel.impl.SimpleUuidGenerator;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.IOHelper;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.springframework.context.support.StaticApplicationContext;

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
        
        assertTrue(uuidGenerator instanceof ActiveMQUuidGenerator);
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
        Reader expectedContext = null;
        try {
            expectedContext = new InputStreamReader(getClass().getResourceAsStream("/org/apache/camel/spring/context-with-endpoint.xml"));
            String createdContext = contextAsString(camelContext);
            XMLUnit.setIgnoreWhitespace(true);
            XMLAssert.assertXMLEqual(expectedContext, new StringReader(createdContext));
        } finally {
            IOHelper.close(expectedContext);
        }
    }

    private String contextAsString(CamelContextFactoryBean context) throws Exception {
        StringWriter stringOut = new StringWriter();
        JAXBContext jaxb = JAXBContext.newInstance(CamelContextFactoryBean.class);
        jaxb.createMarshaller().marshal(context, stringOut);
        return stringOut.toString();
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
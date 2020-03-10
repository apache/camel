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
package org.apache.camel.spring.config;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;

public class CamelContextFactoryBeanTest extends XmlConfigTestSupport {

    private AbstractApplicationContext applicationContext;

    @Override
    @Before
    public void setUp() throws Exception {
        // disable JMX
        System.setProperty(JmxSystemPropertyKeys.DISABLED, "true");
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        // enable JMX
        System.clearProperty(JmxSystemPropertyKeys.DISABLED);

        // we're done so let's properly close the application context
        IOHelper.close(applicationContext);
    }

    @Test
    public void testClassPathRouteLoading() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextFactoryBean.xml");

        CamelContext context = (CamelContext) applicationContext.getBean("camel");
        assertValidContext(context);
    }

    @Test
    public void testClassPathRouteLoadingUsingNamespaces() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextFactoryBean.xml");

        CamelContext context = applicationContext.getBean("camel3", CamelContext.class);
        assertValidContext(context);
    }

    @Test
    public void testGenericApplicationContextUsingNamespaces() throws Exception {
        applicationContext = new GenericApplicationContext();
        XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader((BeanDefinitionRegistry) applicationContext);
        xmlReader.loadBeanDefinitions(new ClassPathResource("org/apache/camel/spring/camelContextFactoryBean.xml"));

        // lets refresh to inject the applicationContext into beans
        applicationContext.refresh();

        CamelContext context = applicationContext.getBean("camel3", CamelContext.class);
        assertValidContext(context);
    }    
    
    @Test
    public void testXMLRouteLoading() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextFactoryBean.xml");

        CamelContext context = applicationContext.getBean("camel2", CamelContext.class);
        assertNotNull("No context found!", context);

        List<Route> routes = context.getRoutes();
        LOG.debug("Found routes: " + routes);

        assertNotNull("Should have found some routes", routes);
        assertEquals("One Route should be found", 1, routes.size());

        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
            Processor processor = consumerRoute.getProcessor();
            assertNotNull(processor);

            assertEndpointUri(key, "seda://test.c");
        }
    }
    
    @Test
    public void testRouteBuilderRef() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextRouteBuilderRef.xml");

        CamelContext context = applicationContext.getBean("camel5", CamelContext.class);
        assertNotNull("No context found!", context);
        
        assertValidContext(context);
    }

    @Test
    public void testAutoStartup() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextFactoryBean.xml");

        SpringCamelContext context = applicationContext.getBean("camel4", SpringCamelContext.class);
        assertFalse(context.isAutoStartup());
        // there is 1 route but its not started
        assertEquals(1, context.getRoutes().size());

        context = applicationContext.getBean("camel3", SpringCamelContext.class);
        assertTrue(context.isAutoStartup());
        // there is 1 route but and its started
        assertEquals(1, context.getRoutes().size());
    }

}

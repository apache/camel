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
package org.apache.camel.spring.config;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;

/**
 * @version $Revision$
 */
public class CamelContextFactoryBeanTest extends XmlConfigTestSupport {
    public void testClassPathRouteLoading() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextFactoryBean.xml");

        CamelContext context = (CamelContext) applicationContext.getBean("camel");
        assertValidContext(context);
    }

    public void testClassPathRouteLoadingUsingNamespaces() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextFactoryBean.xml");

        CamelContext context = (CamelContext) applicationContext.getBean("camel3");
        assertValidContext(context);
    }

    public void testGenericApplicationContextUsingNamespaces() throws Exception {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(applicationContext);
        xmlReader.loadBeanDefinitions(new ClassPathResource("org/apache/camel/spring/camelContextFactoryBean.xml"));

        // lets refresh to inject the applicationContext into beans
        applicationContext.refresh();

        CamelContext context = (CamelContext) applicationContext.getBean("camel3");
        assertValidContext(context);
    }

    public void testXMLRouteLoading() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextFactoryBean.xml");

        CamelContext context = (CamelContext) applicationContext.getBean("camel2");
        assertNotNull("No context found!", context);

        List<Route> routes = context.getRoutes();
        LOG.debug("Found routes: " + routes);

        assertNotNull("Should have found some routes", routes);
        assertEquals("One Route should be found", 1, routes.size());

        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = consumerRoute.getProcessor();
            assertNotNull(processor);

            assertEndpointUri(key, "seda:test.c");
        }
    }

    public void testShouldStartContext() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelContextFactoryBean.xml");

        SpringCamelContext context = (SpringCamelContext) applicationContext.getBean("camel4");
        assertNotNull("No context found!", context);        
        assertFalse("The context should not start yet", context.getShouldStartContext());
        assertEquals("There should have not route", context.getRoutes().size(), 0);
        context = (SpringCamelContext) applicationContext.getBean("camel3");
        assertTrue("The context should started",  context.getShouldStartContext());
        assertEquals("There should have one route", context.getRoutes().size(), 1);
    }

}

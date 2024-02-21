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
package org.apache.camel.dsl.xml.jaxb.spring;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Resource;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelLoadRoutesFromXMLTest extends ContextTestSupport {

    @Test
    public void testLoadRoutes() throws Exception {
        AbstractXmlApplicationContext applicationContext
                = new ClassPathXmlApplicationContext("org/apache/camel/spring/camelLoadRoutesFromXMLTest.xml");
        SpringCamelContext camel = applicationContext.getBean(SpringCamelContext.class);
        assertEquals(0, camel.getRoutes().size());
        assertTrue(camel.getStatus().isStarted());

        // load routes from xml file
        Resource resource = PluginHelper.getResourceLoader(camel).resolveResource("org/apache/camel/spring/myRoutes.xml");
        PluginHelper.getRoutesLoader(camel).loadRoutes(resource);

        assertEquals(2, camel.getRoutes().size());

        // they should be started
        assertTrue(camel.getRouteController().getRouteStatus("foo").isStarted());
        assertTrue(camel.getRouteController().getRouteStatus("bar").isStarted());

        // and they should work
        MockEndpoint foo = camel.getEndpoint("mock:foo", MockEndpoint.class);
        foo.expectedBodiesReceived("Hello World");

        MockEndpoint bar = camel.getEndpoint("mock:bar", MockEndpoint.class);
        bar.expectedBodiesReceived("Bye World");

        ProducerTemplate producer = camel.createProducerTemplate();
        producer.sendBody("direct:foo", "Hello World");
        producer.sendBody("direct:bar", "Bye World");

        MockEndpoint.assertIsSatisfied(foo, bar);

        // remove the routes
        camel.removeAllRoutes();

        // they should be removed
        assertNull(camel.getRouteController().getRouteStatus("foo"));
        assertNull(camel.getRouteController().getRouteStatus("bar"));

        // you can also do this manually via their route ids
        //camel.getRouteController().stopRoute("foo");
        //camel.getRouteController().removeRoute("foo");
        //camel.getRouteController().stopRoute("bar");
        //camel.getRouteController().removeRoute("bar");

        // load updated xml
        resource = PluginHelper.getResourceLoader(camel).resolveResource("org/apache/camel/spring/myUpdatedRoutes.xml");
        PluginHelper.getRoutesLoader(camel).loadRoutes(resource);

        assertEquals(2, camel.getRoutes().size());

        // they should be started
        assertTrue(camel.getRouteController().getRouteStatus("foo").isStarted());
        assertTrue(camel.getRouteController().getRouteStatus("bar").isStarted());

        // and they should work again (need to get new mock endpoint as the old is gone)
        foo = camel.getEndpoint("mock:foo", MockEndpoint.class);
        foo.expectedBodiesReceived("Updated: Hello World");

        bar = camel.getEndpoint("mock:bar", MockEndpoint.class);
        bar.expectedBodiesReceived("Updated: Bye World");

        producer.sendBody("direct:foo", "Hello World");
        producer.sendBody("direct:bar", "Bye World");

        MockEndpoint.assertIsSatisfied(foo, bar);
    }

}

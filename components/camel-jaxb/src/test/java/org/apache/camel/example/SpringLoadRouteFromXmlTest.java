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
package org.apache.camel.example;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.camel.xml.jaxb.DefaultModelJAXBContextFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpringLoadRouteFromXmlTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/example/SpringLoadRouteFromXmlTest.xml");
    }

    @Test
    void testLoadRouteFromXml() throws Exception {
        assertNotNull(context.getRoute("foo"), "Existing foo route should be there");
        assertEquals(1, context.getRoutes().size());

        // test that existing route works
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Hello World");
        template.sendBody("direct:foo", "Hello World");
        foo.assertIsSatisfied();

        // load bar route from classpath using JAXB
        JAXBContext jaxb = new DefaultModelJAXBContextFactory().newJAXBContext();
        Unmarshaller unmarshaller = jaxb.createUnmarshaller();

        Resource rs = new ClassPathResource("org/apache/camel/example/BarRoute.xml");
        Object value = unmarshaller.unmarshal(rs.getInputStream());

        // it should be a RoutesDefinition (we can have multiple routes in the same XML file)
        RoutesDefinition routes = (RoutesDefinition) value;
        assertNotNull(routes, "Should load routes from XML");
        assertEquals(1, routes.getRoutes().size());

        // add the routes to existing CamelContext
        context.addRouteDefinitions(routes.getRoutes());

        assertNotNull(context.getRoute("bar"), "Loaded bar route should be there");
        assertEquals(2, context.getRoutes().size());

        // test that loaded route works
        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedBodiesReceived("Bye World");
        template.sendBody("direct:bar", "Bye World");
        bar.assertIsSatisfied();
    }

}

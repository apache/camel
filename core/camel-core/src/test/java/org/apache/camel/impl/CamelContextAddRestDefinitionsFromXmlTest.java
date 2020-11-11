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
package org.apache.camel.impl;

import java.net.URL;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.component.rest.DummyRestProcessorFactory;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CamelContextAddRestDefinitionsFromXmlTest extends ContextTestSupport {

    protected JAXBContext jaxbContext;

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("dummy-rest", new DummyRestConsumerFactory());
        registry.bind("dummy-rest-api", new DummyRestProcessorFactory());
        return registry;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        jaxbContext = (JAXBContext) context.adapt(ExtendedCamelContext.class).getModelJAXBContextFactory().newJAXBContext();
    }

    protected Object parseUri(String uri) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        URL resource = getClass().getResource(uri);
        assertNotNull(resource, "Cannot find resource on the classpath: " + uri);
        Object value = unmarshaller.unmarshal(resource);
        return value;
    }

    protected RestDefinition loadRest(String uri) throws Exception {
        Object rest = parseUri(uri);
        return assertIsInstanceOf(RestDefinition.class, rest);
    }

    @Test
    public void testAddRestDefinitionsFromXml() throws Exception {
        RestDefinition rest = loadRest("rest1.xml");
        assertNotNull(rest);

        assertEquals("foo", rest.getId());
        assertEquals(0, context.getRestDefinitions().size());

        context.getRestDefinitions().add(rest);
        assertEquals(1, context.getRestDefinitions().size());

        final List<RouteDefinition> routeDefinitions = rest.asRouteDefinition(context);

        for (final RouteDefinition routeDefinition : routeDefinitions) {
            context.addRouteDefinition(routeDefinition);
        }

        assertEquals(2, context.getRoutes().size());

        assertTrue(context.getRouteController().getRouteStatus("route1").isStarted(), "Route should be started");

        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        template.sendBody("seda:get-say-hello-bar", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost").component("dummy-rest").apiContextPath("/api-docs");
            }
        };
    }

}

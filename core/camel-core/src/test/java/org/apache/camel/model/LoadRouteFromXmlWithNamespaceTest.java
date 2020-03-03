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
package org.apache.camel.model;

import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class LoadRouteFromXmlWithNamespaceTest extends ContextTestSupport {

    @Test
    public void testLoadRouteWithNamespaceFromXml() throws Exception {
        InputStream is = getClass().getResourceAsStream("routeWithNamespace.xml");
        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        RoutesDefinition routes = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(ecc, is);
        context.addRouteDefinitions(routes.getRoutes());
        context.start();

        Route routeWithNamespace = context.getRoute("routeWithNamespace");
        assertNotNull("Expected to find route with id: routeWithNamespace", routeWithNamespace);

        MockEndpoint bar = context.getEndpoint("mock:bar", MockEndpoint.class);
        bar.expectedBodiesReceived("Hello from foo");

        // Make sure loaded route can process a XML payload with namespaces
        // attached
        context.createProducerTemplate().sendBody("direct:foo", "<?xml version='1.0'?><foo xmlns='http://foo'><bar>cheese</bar></foo>");

        bar.assertIsSatisfied();
    }
}

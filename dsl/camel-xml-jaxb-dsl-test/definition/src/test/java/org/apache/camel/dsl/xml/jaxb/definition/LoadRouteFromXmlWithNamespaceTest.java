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
package org.apache.camel.dsl.xml.jaxb.definition;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LoadRouteFromXmlWithNamespaceTest extends ContextTestSupport {

    @Test
    public void testLoadRouteWithNamespaceFromXml() throws Exception {
        Resource resource
                = PluginHelper.getResourceLoader(context)
                        .resolveResource("org/apache/camel/dsl/xml/jaxb/definition/routeWithNamespace.xml");
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);
        context.start();

        Route routeWithNamespace = context.getRoute("routeWithNamespace");
        assertNotNull(routeWithNamespace, "Expected to find route with id: routeWithNamespace");

        MockEndpoint bar = context.getEndpoint("mock:bar", MockEndpoint.class);
        bar.expectedBodiesReceived("Hello from foo");

        // Make sure loaded route can process a XML payload with namespaces
        // attached
        context.createProducerTemplate().sendBody("direct:foo",
                "<?xml version='1.0'?><foo xmlns='http://foo'><bar>cheese</bar></foo>");

        bar.assertIsSatisfied();
    }
}

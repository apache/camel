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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LoadRouteFromXmlTest extends ContextTestSupport {

    @Test
    public void testLoadRouteFromXml() throws Exception {
        assertNotNull(context.getRoute("foo"), "Existing foo route should be there");
        assertEquals(1, context.getRoutes().size());

        // test that existing route works
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Hello World");
        template.sendBody("direct:foo", "Hello World");
        foo.assertIsSatisfied();

        // START SNIPPET: e1
        // load route from XML and add them to the existing camel context
        InputStream is = getClass().getResourceAsStream("barRoute.xml");
        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        RoutesDefinition routes = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(ecc, is);
        context.addRouteDefinitions(routes.getRoutes());
        // END SNIPPET: e1

        assertNotNull(context.getRoute("bar"), "Loaded bar route should be there");
        assertEquals(2, context.getRoutes().size());

        // test that loaded route works
        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedBodiesReceived("Bye World");
        template.sendBody("direct:bar", "Bye World");
        bar.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").to("mock:foo");
            }
        };
    }
}

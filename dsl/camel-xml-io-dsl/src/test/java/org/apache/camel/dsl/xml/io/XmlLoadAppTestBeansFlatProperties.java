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
package org.apache.camel.dsl.xml.io;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class XmlLoadAppTestBeansFlatProperties {
    @RegisterExtension
    private static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private CamelContext context;

    @BeforeEach
    void setupTest() throws Exception {
        context = camelContextExtension.getContext();

        // camel-app4 registers one bean, where its dependency is created from the flattened properties
        // and using org.apache.camel.spi.Injector.newInstance()

        Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                "/org/apache/camel/dsl/xml/io/camel-app4.xml");

        RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
        routesLoader.preParseRoute(resource, false);
        routesLoader.loadRoutes(resource);
    }

    @Test
    public void testLoadCamelAppWithBeansAndFlattenedProperties() throws Exception {
        assertNotNull(context.getRoute("r4"), "Loaded r4 route should be there");
        assertEquals(1, context.getRoutes().size());

        // test that loaded route works
        MockEndpoint y4 = context.getEndpoint("mock:y4", MockEndpoint.class);
        y4.expectedBodiesReceived("Hello World");
        context.createProducerTemplate().sendBody("direct:x4", "I'm World");
        y4.assertIsSatisfied();
    }

}

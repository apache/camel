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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.DIRegistry;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class XmlLoadAppTest {

    @Test
    public void testLoadCamelAppFromXml() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getCamelContextExtension().setRegistry(new DefaultRegistry(new DIRegistry()));
            context.start();

            // load route from XML and add them to the existing camel context
            String[] contexts = new String[] {
                    "camel-app1.xml",
                    "camel-app2.xml"
            };
            for (String r : contexts) {
                Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                        "/org/apache/camel/dsl/xml/io/" + r);

                PluginHelper.getRoutesLoader(context).loadRoutes(resource);
            }

            assertNotNull(context.getRoute("r1"), "Loaded r1 route should be there");
            assertNotNull(context.getRoute("r2"), "Loaded r2 route should be there");
            assertEquals(2, context.getRoutes().size());

            // test that loaded route works
            MockEndpoint y1 = context.getEndpoint("mock:y1", MockEndpoint.class);
            y1.expectedBodiesReceived("Hello World");
            context.createProducerTemplate().sendBody("direct:x1", "I'm World");
            y1.assertIsSatisfied();

            MockEndpoint y2 = context.getEndpoint("mock:y2", MockEndpoint.class);
            y2.expectedBodiesReceived("Hello World");
            context.createProducerTemplate().sendBody("direct:x2", "I'm World");
            y2.assertIsSatisfied();
        }
    }

}

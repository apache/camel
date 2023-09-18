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
package org.apache.camel.dsl.xml.io.configuration;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConfigurationTest {

    @Test
    public void testLoadRoutesBuilderFromXmlNoNamespace() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            // load route from XML and add them to the existing camel context
            Resource resource1 = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/configuration/conf1.xml");
            Resource resource2 = PluginHelper.getResourceLoader(context).resolveResource(
                    "/org/apache/camel/configuration/routeConfigurations1.xml");

            PluginHelper.getRoutesLoader(context).loadRoutes(resource1, resource2);

            assertNotNull(context.getRoute("conf1"), "Loaded conf1 route should be there");
            assertEquals(1, context.getRoutes().size());

            // test that loaded route works
            Object result = context.createProducerTemplate().requestBody("direct:conf1", "Hi World");
            Assertions.assertEquals("onException has been triggered in xmlRouteConfiguration1", result);
        }
    }
}

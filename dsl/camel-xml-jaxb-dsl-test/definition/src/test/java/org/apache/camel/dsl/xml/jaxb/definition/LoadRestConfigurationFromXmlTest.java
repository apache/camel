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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.component.rest.DummyRestProcessorFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

public class LoadRestConfigurationFromXmlTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        jndi.bind("dummy-rest-api", new DummyRestProcessorFactory());
        return jndi;
    }

    @Test
    public void testLoadRestsDefinitionFromXml() throws Exception {
        // load rest configuration from XML and add it to the existing camel context
        Resource resource = PluginHelper.getResourceLoader(context)
                .resolveResource("org/apache/camel/dsl/xml/jaxb/definition/restConfiguration.xml");
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        RestConfiguration restConfiguration = context.getRestConfiguration();
        assertNotNull(restConfiguration, "There should be a rest configuration");
        assertEquals("dummy-rest", restConfiguration.getApiComponent());
        assertEquals("dummy-rest", restConfiguration.getComponent());
        assertEquals("api", restConfiguration.getContextPath());
        assertEquals("api-doc", restConfiguration.getApiContextPath());

        Map<String, Object> apiProperties = restConfiguration.getApiProperties();
        assertEquals("test", apiProperties.get("api.title"));
        assertEquals("3.0", apiProperties.get("openapi.version"));
    }
}

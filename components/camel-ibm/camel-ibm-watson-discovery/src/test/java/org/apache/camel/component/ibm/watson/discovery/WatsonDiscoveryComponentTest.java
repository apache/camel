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
package org.apache.camel.component.ibm.watson.discovery;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WatsonDiscoveryComponentTest extends CamelTestSupport {

    @Test
    public void testComponentCreation() {
        WatsonDiscoveryComponent component = new WatsonDiscoveryComponent();
        assertNotNull(component);
    }

    @Test
    public void testEndpointCreation() throws Exception {
        WatsonDiscoveryComponent component = context.getComponent("ibm-watson-discovery", WatsonDiscoveryComponent.class);
        assertNotNull(component);

        WatsonDiscoveryConfiguration config = new WatsonDiscoveryConfiguration();
        config.setApiKey("test-api-key");
        config.setProjectId("test-project-id");
        component.setConfiguration(config);

        WatsonDiscoveryEndpoint endpoint
                = (WatsonDiscoveryEndpoint) component.createEndpoint("ibm-watson-discovery://default");
        assertNotNull(endpoint);
        assertEquals("test-api-key", endpoint.getConfiguration().getApiKey());
        assertEquals("test-project-id", endpoint.getConfiguration().getProjectId());
    }

    @Test
    public void testConfigurationDefaults() {
        WatsonDiscoveryConfiguration config = new WatsonDiscoveryConfiguration();
        assertEquals("2023-03-31", config.getVersion());
    }

    @Test
    public void testConfigurationCopy() {
        WatsonDiscoveryConfiguration config = new WatsonDiscoveryConfiguration();
        config.setApiKey("test-key");
        config.setServiceUrl("https://test.url");
        config.setProjectId("test-project");
        config.setVersion("2023-01-01");

        WatsonDiscoveryConfiguration copy = config.copy();
        assertNotNull(copy);
        assertEquals("test-key", copy.getApiKey());
        assertEquals("https://test.url", copy.getServiceUrl());
        assertEquals("test-project", copy.getProjectId());
        assertEquals("2023-01-01", copy.getVersion());
    }
}

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
package org.apache.camel.component.ibm.watson.language;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WatsonLanguageComponentTest extends CamelTestSupport {

    @Test
    public void testComponentCreation() {
        WatsonLanguageComponent component = new WatsonLanguageComponent();
        assertNotNull(component);
    }

    @Test
    public void testEndpointCreation() throws Exception {
        WatsonLanguageComponent component = context.getComponent("ibm-watson-language", WatsonLanguageComponent.class);
        assertNotNull(component);

        WatsonLanguageConfiguration config = new WatsonLanguageConfiguration();
        config.setApiKey("test-api-key");
        component.setConfiguration(config);

        WatsonLanguageEndpoint endpoint
                = (WatsonLanguageEndpoint) component.createEndpoint("ibm-watson-language://default");
        assertNotNull(endpoint);
        assertEquals("test-api-key", endpoint.getConfiguration().getApiKey());
    }

    @Test
    public void testConfigurationDefaults() {
        WatsonLanguageConfiguration config = new WatsonLanguageConfiguration();
        assertEquals(true, config.isAnalyzeSentiment());
        assertEquals(true, config.isAnalyzeEntities());
        assertEquals(true, config.isAnalyzeKeywords());
        assertEquals(false, config.isAnalyzeEmotion());
        assertEquals(false, config.isAnalyzeConcepts());
        assertEquals(false, config.isAnalyzeCategories());
    }

    @Test
    public void testConfigurationCopy() {
        WatsonLanguageConfiguration config = new WatsonLanguageConfiguration();
        config.setApiKey("test-key");
        config.setServiceUrl("https://test.url");
        config.setAnalyzeSentiment(false);

        WatsonLanguageConfiguration copy = config.copy();
        assertNotNull(copy);
        assertEquals("test-key", copy.getApiKey());
        assertEquals("https://test.url", copy.getServiceUrl());
        assertEquals(false, copy.isAnalyzeSentiment());
    }
}

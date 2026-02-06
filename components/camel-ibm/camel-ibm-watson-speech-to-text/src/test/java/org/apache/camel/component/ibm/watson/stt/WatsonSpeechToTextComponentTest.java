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
package org.apache.camel.component.ibm.watson.stt;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WatsonSpeechToTextComponentTest extends CamelTestSupport {

    @Test
    public void testComponentCreation() {
        WatsonSpeechToTextComponent component = new WatsonSpeechToTextComponent();
        assertNotNull(component);
    }

    @Test
    public void testEndpointCreation() throws Exception {
        WatsonSpeechToTextComponent component
                = context.getComponent("ibm-watson-speech-to-text", WatsonSpeechToTextComponent.class);
        assertNotNull(component);

        WatsonSpeechToTextConfiguration config = new WatsonSpeechToTextConfiguration();
        config.setApiKey("test-api-key");
        component.setConfiguration(config);

        WatsonSpeechToTextEndpoint endpoint
                = (WatsonSpeechToTextEndpoint) component.createEndpoint("ibm-watson-speech-to-text://default");
        assertNotNull(endpoint);
        assertEquals("test-api-key", endpoint.getConfiguration().getApiKey());
    }

    @Test
    public void testConfigurationDefaults() {
        WatsonSpeechToTextConfiguration config = new WatsonSpeechToTextConfiguration();
        assertEquals("en-US_BroadbandModel", config.getModel());
        assertEquals("audio/wav", config.getContentType());
        assertFalse(config.isTimestamps());
        assertFalse(config.isWordConfidence());
        assertFalse(config.isSpeakerLabels());
    }

    @Test
    public void testConfigurationCopy() {
        WatsonSpeechToTextConfiguration config = new WatsonSpeechToTextConfiguration();
        config.setApiKey("test-key");
        config.setServiceUrl("https://test.url");
        config.setModel("en-GB_BroadbandModel");
        config.setContentType("audio/mp3");
        config.setTimestamps(true);
        config.setWordConfidence(true);
        config.setSpeakerLabels(true);

        WatsonSpeechToTextConfiguration copy = config.copy();
        assertNotNull(copy);
        assertEquals("test-key", copy.getApiKey());
        assertEquals("https://test.url", copy.getServiceUrl());
        assertEquals("en-GB_BroadbandModel", copy.getModel());
        assertEquals("audio/mp3", copy.getContentType());
        assertEquals(true, copy.isTimestamps());
        assertEquals(true, copy.isWordConfidence());
        assertEquals(true, copy.isSpeakerLabels());
    }

    @Test
    public void testOperationsEnum() {
        assertNotNull(WatsonSpeechToTextOperations.recognize);
        assertNotNull(WatsonSpeechToTextOperations.listModels);
        assertNotNull(WatsonSpeechToTextOperations.getModel);
        assertNotNull(WatsonSpeechToTextOperations.listCustomModels);
        assertNotNull(WatsonSpeechToTextOperations.getCustomModel);
    }
}

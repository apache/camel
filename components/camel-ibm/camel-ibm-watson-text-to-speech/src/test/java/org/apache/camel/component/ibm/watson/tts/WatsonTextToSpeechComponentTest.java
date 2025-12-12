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
package org.apache.camel.component.ibm.watson.tts;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WatsonTextToSpeechComponentTest extends CamelTestSupport {

    @Test
    public void testComponentCreation() {
        WatsonTextToSpeechComponent component = new WatsonTextToSpeechComponent();
        assertNotNull(component);
    }

    @Test
    public void testEndpointCreation() throws Exception {
        WatsonTextToSpeechComponent component
                = context.getComponent("ibm-watson-text-to-speech", WatsonTextToSpeechComponent.class);
        assertNotNull(component);

        WatsonTextToSpeechConfiguration config = new WatsonTextToSpeechConfiguration();
        config.setApiKey("test-api-key");
        component.setConfiguration(config);

        WatsonTextToSpeechEndpoint endpoint
                = (WatsonTextToSpeechEndpoint) component.createEndpoint("ibm-watson-text-to-speech://default");
        assertNotNull(endpoint);
        assertEquals("test-api-key", endpoint.getConfiguration().getApiKey());
    }

    @Test
    public void testConfigurationDefaults() {
        WatsonTextToSpeechConfiguration config = new WatsonTextToSpeechConfiguration();
        assertEquals("en-US_MichaelV3Voice", config.getVoice());
        assertEquals("audio/wav", config.getAccept());
    }

    @Test
    public void testConfigurationCopy() {
        WatsonTextToSpeechConfiguration config = new WatsonTextToSpeechConfiguration();
        config.setApiKey("test-key");
        config.setServiceUrl("https://test.url");
        config.setVoice("en-US_AllisonV3Voice");
        config.setAccept("audio/mp3");

        WatsonTextToSpeechConfiguration copy = config.copy();
        assertNotNull(copy);
        assertEquals("test-key", copy.getApiKey());
        assertEquals("https://test.url", copy.getServiceUrl());
        assertEquals("en-US_AllisonV3Voice", copy.getVoice());
        assertEquals("audio/mp3", copy.getAccept());
    }

    @Test
    public void testOperationsEnum() {
        assertNotNull(WatsonTextToSpeechOperations.synthesize);
        assertNotNull(WatsonTextToSpeechOperations.listVoices);
        assertNotNull(WatsonTextToSpeechOperations.getVoice);
        assertNotNull(WatsonTextToSpeechOperations.listCustomModels);
        assertNotNull(WatsonTextToSpeechOperations.getCustomModel);
        assertNotNull(WatsonTextToSpeechOperations.getPronunciation);
    }
}

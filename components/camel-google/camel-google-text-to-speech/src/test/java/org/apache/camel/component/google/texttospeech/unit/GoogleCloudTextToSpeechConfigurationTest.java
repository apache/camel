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
package org.apache.camel.component.google.texttospeech.unit;

import org.apache.camel.component.google.texttospeech.GoogleCloudTextToSpeechConfiguration;
import org.apache.camel.component.google.texttospeech.GoogleCloudTextToSpeechOperations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class GoogleCloudTextToSpeechConfigurationTest {

    @Test
    public void testConfigurationDefaults() {
        GoogleCloudTextToSpeechConfiguration config = new GoogleCloudTextToSpeechConfiguration();
        assertFalse(config.isPojoRequest());
        assertEquals("en-US", config.getLanguageCode());
        assertEquals("MP3", config.getAudioEncoding());
    }

    @Test
    public void testConfigurationCopy() {
        GoogleCloudTextToSpeechConfiguration config = new GoogleCloudTextToSpeechConfiguration();
        config.setOperation("synthesize");
        config.setServiceAccountKey("test-key.json");
        config.setLanguageCode("fr-FR");
        config.setVoiceName("fr-FR-Wavenet-A");
        config.setAudioEncoding("OGG_OPUS");
        config.setSpeakingRate(1.5);
        config.setPitch(-2.0);
        config.setPojoRequest(true);

        GoogleCloudTextToSpeechConfiguration copy = config.copy();
        assertNotSame(config, copy);
        assertEquals("synthesize", copy.getOperation());
        assertEquals("test-key.json", copy.getServiceAccountKey());
        assertEquals("fr-FR", copy.getLanguageCode());
        assertEquals("fr-FR-Wavenet-A", copy.getVoiceName());
        assertEquals("OGG_OPUS", copy.getAudioEncoding());
        assertEquals(1.5, copy.getSpeakingRate());
        assertEquals(-2.0, copy.getPitch());
    }

    @Test
    public void testOperationsEnum() {
        assertNotNull(GoogleCloudTextToSpeechOperations.valueOf("synthesize"));
        assertNotNull(GoogleCloudTextToSpeechOperations.valueOf("listVoices"));
    }
}

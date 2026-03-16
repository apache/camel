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
package org.apache.camel.component.google.speechtotext.unit;

import org.apache.camel.component.google.speechtotext.GoogleCloudSpeechToTextConfiguration;
import org.apache.camel.component.google.speechtotext.GoogleCloudSpeechToTextOperations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class GoogleCloudSpeechToTextConfigurationTest {

    @Test
    public void testConfigurationDefaults() {
        GoogleCloudSpeechToTextConfiguration config = new GoogleCloudSpeechToTextConfiguration();
        assertFalse(config.isPojoRequest());
        assertEquals("en-US", config.getLanguageCode());
        assertEquals("LINEAR16", config.getEncoding());
    }

    @Test
    public void testConfigurationCopy() {
        GoogleCloudSpeechToTextConfiguration config = new GoogleCloudSpeechToTextConfiguration();
        config.setOperation("recognize");
        config.setServiceAccountKey("test-key.json");
        config.setEncoding("FLAC");
        config.setSampleRateHertz(16000);
        config.setLanguageCode("fr-FR");
        config.setPojoRequest(true);

        GoogleCloudSpeechToTextConfiguration copy = config.copy();
        assertNotSame(config, copy);
        assertEquals("recognize", copy.getOperation());
        assertEquals("test-key.json", copy.getServiceAccountKey());
        assertEquals("FLAC", copy.getEncoding());
        assertEquals(16000, copy.getSampleRateHertz());
        assertEquals("fr-FR", copy.getLanguageCode());
    }

    @Test
    public void testOperationsEnum() {
        assertNotNull(GoogleCloudSpeechToTextOperations.valueOf("recognize"));
    }
}

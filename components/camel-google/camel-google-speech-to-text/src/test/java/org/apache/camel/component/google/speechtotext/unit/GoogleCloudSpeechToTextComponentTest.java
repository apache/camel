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

import org.apache.camel.component.google.speechtotext.GoogleCloudSpeechToTextComponent;
import org.apache.camel.component.google.speechtotext.GoogleCloudSpeechToTextEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GoogleCloudSpeechToTextComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpoint() throws Exception {
        GoogleCloudSpeechToTextComponent component
                = context.getComponent("google-speech-to-text", GoogleCloudSpeechToTextComponent.class);
        assertNotNull(component);

        GoogleCloudSpeechToTextEndpoint endpoint = (GoogleCloudSpeechToTextEndpoint) component
                .createEndpoint("google-speech-to-text:recognize?serviceAccountKey=test.json");
        assertNotNull(endpoint);
        assertNotNull(endpoint.getConfiguration());
        assertEquals("recognize", endpoint.getConfiguration().getOperation());
    }

    @Test
    public void testCreateEndpointWithoutOperation() {
        GoogleCloudSpeechToTextComponent component
                = context.getComponent("google-speech-to-text", GoogleCloudSpeechToTextComponent.class);
        assertNotNull(component);

        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("google-speech-to-text:?serviceAccountKey=test.json"));
    }
}

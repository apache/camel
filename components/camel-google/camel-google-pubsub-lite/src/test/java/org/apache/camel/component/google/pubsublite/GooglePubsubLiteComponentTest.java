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
package org.apache.camel.component.google.pubsublite;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GooglePubsubLiteComponentTest extends CamelTestSupport {

    private GooglePubsubLiteComponent googlePubsubLiteComponent;

    @Override
    public void doPreSetup() {
        googlePubsubLiteComponent = new GooglePubsubLiteComponent();
    }

    @Test
    public void testCreateEndpointAllFields() throws Exception {
        String uri = "google-pubsub-lite:123456789012:europe-west3:test";
        String remaining = "123456789012:europe-west3:test";
        Map<String, Object> parameters = new HashMap<>();
        Endpoint endpoint = googlePubsubLiteComponent.createEndpoint(uri, remaining, parameters);

        assertTrue(endpoint instanceof GooglePubsubLiteEndpoint, "Should return instance of GooglePubsubLiteEndpoint");
        GooglePubsubLiteEndpoint googlePubsubLiteEndpoint = (GooglePubsubLiteEndpoint) endpoint;

        assertEquals(123456789012L, googlePubsubLiteEndpoint.getProjectId());
        assertEquals("europe-west3", googlePubsubLiteEndpoint.getLocation());
        assertEquals("test", googlePubsubLiteEndpoint.getDestinationName());
    }

    @Test
    public void testCreateEndpointMissingFields() {
        String uri = "google-pubsub-lite:123456789012:europe-west3";
        String remaining = "123456789012:europe-west3";
        Map<String, Object> parameters = new HashMap<>();

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> googlePubsubLiteComponent.createEndpoint(uri, remaining, parameters));

        String expectedMessage = "Google PubSub Lite Endpoint format \"projectId:location:destinationName[:subscriptionName]\"";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }
}

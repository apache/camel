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
package org.apache.camel.component.google.vision.unit;

import org.apache.camel.component.google.vision.GoogleCloudVisionComponent;
import org.apache.camel.component.google.vision.GoogleCloudVisionEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GoogleCloudVisionComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpoint() throws Exception {
        GoogleCloudVisionComponent component = context.getComponent("google-vision", GoogleCloudVisionComponent.class);
        assertNotNull(component);

        GoogleCloudVisionEndpoint endpoint = (GoogleCloudVisionEndpoint) component
                .createEndpoint("google-vision:labelDetection?serviceAccountKey=test.json");
        assertNotNull(endpoint);
        assertNotNull(endpoint.getConfiguration());
        assertEquals("labelDetection", endpoint.getConfiguration().getOperation());
    }

    @Test
    public void testCreateEndpointWithoutOperation() {
        GoogleCloudVisionComponent component = context.getComponent("google-vision", GoogleCloudVisionComponent.class);
        assertNotNull(component);

        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("google-vision:?serviceAccountKey=test.json"));
    }
}

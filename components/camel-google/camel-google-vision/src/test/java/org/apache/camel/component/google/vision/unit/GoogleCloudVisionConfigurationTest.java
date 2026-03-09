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

import org.apache.camel.component.google.vision.GoogleCloudVisionConfiguration;
import org.apache.camel.component.google.vision.GoogleCloudVisionOperations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class GoogleCloudVisionConfigurationTest {

    @Test
    public void testConfigurationDefaults() {
        GoogleCloudVisionConfiguration config = new GoogleCloudVisionConfiguration();
        assertFalse(config.isPojoRequest());
    }

    @Test
    public void testConfigurationCopy() {
        GoogleCloudVisionConfiguration config = new GoogleCloudVisionConfiguration();
        config.setOperation("labelDetection");
        config.setServiceAccountKey("test-key.json");
        config.setMaxResults(10);
        config.setPojoRequest(true);

        GoogleCloudVisionConfiguration copy = config.copy();
        assertNotSame(config, copy);
        assertEquals("labelDetection", copy.getOperation());
        assertEquals("test-key.json", copy.getServiceAccountKey());
        assertEquals(10, copy.getMaxResults());
    }

    @Test
    public void testOperationsEnum() {
        assertNotNull(GoogleCloudVisionOperations.valueOf("labelDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("textDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("faceDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("landmarkDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("logoDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("safeSearchDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("imagePropertiesDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("webDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("objectLocalization"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("cropHintsDetection"));
        assertNotNull(GoogleCloudVisionOperations.valueOf("documentTextDetection"));
    }
}
